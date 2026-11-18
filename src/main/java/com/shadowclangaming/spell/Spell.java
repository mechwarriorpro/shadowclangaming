package com.shadowclangaming.spell;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * A finished spell: an ordered SEQUENCE of steps, each step a shape plus the components that fire with it.
 *
 * ──────────────────── WHY A SEQUENCE ────────────────────
 *
 * The first version of this class was "one shape + a pile of components", which could only ever describe
 * a single delivery. A spell that is a projectile that leaves a burning zone that drops a rune is three
 * different deliveries in a row, so a spell is now a list:
 *
 *     [ PROJECTILE : fire damage ]  ->  [ ZONE : lingering_zone ]  ->  [ RUNE : ignite ]
 *
 * Casting walks the list. Each step does its thing and then hands the REST of the list to the next step -
 * the projectile carries it to whatever it hits, the zone re-runs it every 5 seconds for 30 seconds, the
 * rune runs it once when triggered. See {@link SpellCasting#run}.
 *
 * Immutable. Anything that edits a spell builds a new one, which keeps it safe to store in a data
 * component and to hand to another thread.
 *
 * All the arithmetic lives here: cost is a pure function of the spell, so the inscription table, the
 * rifle, the warhead and the tooltip all quote exactly the same number.
 */
public record Spell(List<Step> steps, String name) {

    /** One step: a shape, and the components that fire with it. */
    /**
     * One step: a shape, the components that fire with it, and its MODIFIERS.
     *
     * {@code modifiers} is a bitmask of {@link SpellModifier} - one int, so the codec, the network form
     * and the synced GUI array all stay small. A step with no modifiers is 0, which is also what a step
     * saved by an older build decodes to.
     */
    public record Step(SpellShape shape, List<SpellComponent> components, int modifiers) {
        public static final Codec<Step> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                SpellShape.CODEC.fieldOf("shape").forGetter(Step::shape),
                SpellComponent.CODEC.listOf().fieldOf("components").forGetter(Step::components),
                Codec.INT.optionalFieldOf("modifiers", 0).forGetter(Step::modifiers)
        ).apply(instance, Step::new));

        /** Network form. Strings, not ordinals: an ordinal breaks the first time the enum is reordered. */
        public static final StreamCodec<RegistryFriendlyByteBuf, Step> STREAM_CODEC =
                StreamCodec.composite(
                        ByteBufCodecs.STRING_UTF8, step -> step.shape().id(),
                        ByteCodecs.COMPONENT_ID_LIST, step -> {
                            List<String> ids = new ArrayList<>(step.components().size());
                            for (SpellComponent component : step.components()) {
                                ids.add(component.id());
                            }
                            return ids;
                        },
                        ByteBufCodecs.VAR_INT, Step::modifiers,
                        (shapeId, componentIds, modifiers) -> new Step(SpellShape.byId(shapeId),
                                ByteCodecs.parseComponents(componentIds), modifiers));

        public Step(SpellShape shape, List<SpellComponent> components) {
            this(shape, components, 0);
        }

        public Step {
            components = List.copyOf(components);
        }

        /** The step's raw cost, before modifiers: shape base plus every component in it. */
        public long baseManaCost() {
            long total = shape.baseCost();
            for (SpellComponent component : components) {
                total += component.manaCost();
            }
            return total;
        }

        /** What the step actually costs, modifiers included. */
        public long manaCost() {
            double multiplier = SpellModifier.costMultiplier(modifiers);
            return Math.max(1L, Math.round(baseManaCost() * multiplier));
        }

        public Step plus(SpellComponent component) {
            List<SpellComponent> next = new ArrayList<>(components);
            next.add(component);
            return new Step(shape, next, modifiers);
        }

        public Step minusLast() {
            if (components.isEmpty()) {
                return this;
            }
            return new Step(shape, components.subList(0, components.size() - 1), modifiers);
        }

        /** Flip one modifier on this step. */
        public Step toggled(SpellModifier modifier) {
            return new Step(shape, components, SpellModifier.toggle(modifiers, modifier));
        }

        public Step withModifiers(int mask) {
            return new Step(shape, components, mask);
        }

        public boolean has(SpellModifier modifier) {
            return SpellModifier.has(modifiers, modifier);
        }

        public List<SpellModifier> modifierList() {
            return SpellModifier.unpack(modifiers);
        }
    }

    /**
     * How many SHAPES a spell may contain.
     *
     * Not a design cap in the old sense - there is no "pick one of eight slots" any more. A spell is a
     * flat list of parts (see {@link Step}); a shape part starts a new delivery and the components
     * after it belong to that delivery. Sixteen is simply the size of the synced array the GUI
     * mirrors, and no spell a mana pool can pay for reaches it.
     */
    public static final int MAX_STEPS = 16;
    /**
     * A memory guard, NOT a design cap.
     *
     * The user's rule: nothing limits how many components a step may carry - mana does. This number
     * exists only because the menu syncs its draft through a flat int array and the save file has to
     * have some length to allocate. At 256 components a single step costs tens of thousands of mana
     * before it is half full, so no spell can reach this limit through play; see {@link #rawManaCost()}.
     */
    public static final int MAX_PER_STEP = 256;

    /** The longest name the table will store. Long names are the player's problem, not the codec's. */
    public static final int MAX_NAME_LENGTH = 48;

    public static final Codec<Spell> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Step.CODEC.listOf().fieldOf("steps").forGetter(Spell::steps),
            // Optional, so every spell written by an older build still decodes - as an unnamed spell.
            Codec.STRING.optionalFieldOf("name", "").forGetter(Spell::name)
    ).apply(instance, Spell::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, Spell> STREAM_CODEC =
            StreamCodec.composite(
                    Step.STREAM_CODEC.apply(ByteBufCodecs.list(MAX_STEPS)), Spell::steps,
                    ByteBufCodecs.stringUtf8(MAX_NAME_LENGTH), Spell::name,
                    Spell::new);

    public Spell {
        steps = List.copyOf(steps);
        name = name == null ? "" : name.length() > MAX_NAME_LENGTH
                ? name.substring(0, MAX_NAME_LENGTH) : name;
    }

    /** A spell with no name. The constructor every older call site uses. */
    public Spell(List<Step> steps) {
        this(steps, "");
    }

    /** The same spell, renamed. Used by the table's name box. */
    public Spell withName(String newName) {
        return new Spell(steps, newName);
    }

    /** The name if there is one, otherwise a generated label like "projectile x2". */
    public String displayName() {
        if (!name.isBlank()) {
            return name;
        }
        return steps.isEmpty() ? "empty spell" : steps.get(0).shape().id() + " x" + steps.size();
    }

    // ------------------------------------------------------------------ construction

    /** A single-step spell - the shape the rest of the codebase still thinks in. */
    public static Spell of(SpellShape shape, List<SpellComponent> components) {
        return new Spell(List.of(new Step(shape, components)));
    }

    /** A spell with one empty step. Legal to build, useless to cast. */
    public static Spell blank(SpellShape shape) {
        return new Spell(List.of(new Step(shape, List.of())));
    }

    public static Spell empty() {
        return new Spell(List.of());
    }

    // ------------------------------------------------------------------ read access

    /** The first step's shape, or SELF for an empty spell. Kept for display code. */
    public SpellShape shape() {
        return steps.isEmpty() ? SpellShape.SELF : steps.get(0).shape();
    }

    /** Every component in the spell, flattened. */
    public List<SpellComponent> components() {
        List<SpellComponent> all = new ArrayList<>();
        for (Step step : steps) {
            all.addAll(step.components());
        }
        return List.copyOf(all);
    }

    public int componentCount() {
        int count = 0;
        for (Step step : steps) {
            count += step.components().size();
        }
        return count;
    }

    public int stepCount() {
        return steps.size();
    }

    /** A spell needs at least one step with at least one component to be worth casting. */
    public boolean isEmpty() {
        if (steps.isEmpty()) {
            return true;
        }
        for (Step step : steps) {
            if (!step.components().isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Highest component tier used. Now only a display/rarity figure - see {@link #capacityDemand()}. */
    public int highestTier() {
        int highest = 0;
        for (Step step : steps) {
            for (SpellComponent component : step.components()) {
                highest = Math.max(highest, component.tier());
            }
        }
        return highest;
    }

    /** Raw cost: every step's shape base plus every component at its tier price. */
    public long rawManaCost() {
        long total = 0L;
        for (Step step : steps) {
            total += step.manaCost();
        }
        return total;
    }

    /**
     * Mana CAPACITY this spell demands.
     *
     * This replaces the old "spell level vs rifle tier" gate. The rule the user asked for: you can write
     * any spell your own mana pool is big enough to hold, so the price of a big spell is growing your
     * capacity (the Mana Capacity enchantment), not grinding levels. A spell whose cost exceeds your
     * capacity simply cannot be inscribed or fired.
     */
    public long capacityDemand() {
        return rawManaCost();
    }

    /**
     * @param efficiencyPercent what the gun is worth: 100 = pay full price, 60 = pay 60%.
     *                          Comes from the block-built rifle's materials (see BlueprintMath).
     */
    public long manaCost(int efficiencyPercent) {
        long raw = rawManaCost();
        int clamped = Math.max(10, Math.min(100, efficiencyPercent));
        return Math.max(1L, raw * clamped / 100L);
    }

    /** Fee to copy this spell into a warhead, in currency units. Quadratic in size. */
    public long copyFee() {
        long n = Math.max(1, componentCount());
        return 200L * n * n;
    }

    /** Total damage components in the spell - used by the spell warhead to size its blast. */
    public int damageComponentCount() {
        int count = 0;
        for (SpellComponent component : components()) {
            if (component.category() == SpellComponent.Category.DAMAGE) {
                count++;
            }
        }
        return count;
    }

    /** "projectile: fire damage > zone: lingering zone > rune: ignite" - for tooltips and commands. */
    public String summary() {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < steps.size(); i++) {
            if (i > 0) {
                out.append(" > ");
            }
            Step step = steps.get(i);
            out.append(step.shape().id()).append(": ");
            if (step.components().isEmpty()) {
                out.append("(empty)");
            }
            for (int c = 0; c < step.components().size(); c++) {
                if (c > 0) {
                    out.append(", ");
                }
                out.append(step.components().get(c).id());
            }
        }
        return out.toString();
    }

    /** Does this spell contain a zone or rune step? Asked by the tooltip and by the warhead. */
    public boolean isPersistent() {
        for (Step step : steps) {
            if (step.shape().isPersistent()) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ editing

    /** Same spell with one more component on the end of the LAST step. */
    public Spell plus(SpellComponent component) {
        if (steps.isEmpty()) {
            return Spell.of(SpellShape.PROJECTILE, List.of(component));
        }
        List<Step> next = new ArrayList<>(steps);
        int last = next.size() - 1;
        next.set(last, next.get(last).plus(component));
        return new Spell(next, name);
    }

    /** Same spell without its last component (no-op on an empty spell). */
    public Spell minusLast() {
        if (steps.isEmpty()) {
            return this;
        }
        List<Step> next = new ArrayList<>(steps);
        int last = next.size() - 1;
        Step trimmed = next.get(last).minusLast();
        next.set(last, trimmed);
        return new Spell(next, name);
    }

    /** Replaces the whole step list (used by the table when a step is removed or reordered). */
    public Spell withSteps(List<Step> newSteps) {
        return new Spell(newSteps, name);
    }

    /** A single-step spell of this shape, keeping the old behaviour of "change the shape". */
    public Spell withShape(SpellShape newShape) {
        if (steps.isEmpty()) {
            return Spell.blank(newShape);
        }
        List<Step> next = new ArrayList<>(steps);
        next.set(0, new Step(newShape, next.get(0).components()));
        return new Spell(next, name);
    }

    /** Small shared string-list helpers, kept here so both codecs read the same way. */
    private static final class ByteCodecs {
        static final StreamCodec<RegistryFriendlyByteBuf, List<String>> COMPONENT_ID_LIST =
                ByteBufCodecs.collection(ArrayList::new, ByteBufCodecs.STRING_UTF8);

        static List<SpellComponent> parseComponents(List<String> ids) {
            List<SpellComponent> parsed = new ArrayList<>(ids.size());
            for (String id : ids) {
                parsed.add(SpellComponent.byId(id));
            }
            return List.copyOf(parsed);
        }

        private ByteCodecs() {
        }
    }
}
