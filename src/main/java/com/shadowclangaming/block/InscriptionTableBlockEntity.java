package com.shadowclangaming.block;

import com.shadowclangaming.registry.SCGBlockEntities;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.spell.SpellComponent;
import com.shadowclangaming.spell.SpellModifier;
import com.shadowclangaming.spell.SpellShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The draft page of the inscription table.
 *
 * ──────────────────── WHAT THE DRAFT IS NOW ────────────────────
 *
 * A SEQUENCE of steps. Each step is a shape (self / touch / beam / projectile / aura / zone / rune) with
 * its own list of components. That is what makes "projectile, then a zone, then a rune" possible, and it
 * is why this class no longer has a single {@code shape} field.
 *
 * The draft is mirrored to the client through {@link ContainerData}, encoded into a flat int array:
 *
 *     slot 0                 number of steps
 *     slot 1                 index of the step being edited
 *     slots 2..2+n           one "header" per step: (components << 8) | shapeOrdinal
 *     slots after that       the component ordinals, step by step
 *
 * Flat ints, not an object graph, because menu data sync is a plain int channel - and one header int per
 * step keeps the array short enough that the whole thing can be resent every time it changes.
 */
public class InscriptionTableBlockEntity extends BlockEntity {

    /** Most steps a spell may have. Matches {@link Spell#MAX_STEPS}. */
    public static final int MAX_STEPS = Spell.MAX_STEPS;
    /** Most components in one step. The total spell size is unbounded across steps. */
    public static final int MAX_PER_STEP = Spell.MAX_PER_STEP;

    /** slot 0 = step count, slot 1 = selected step. */
    public static final int HEADER_SLOTS = 2;
    /** Then MAX_STEPS headers, then MAX_STEPS modifier masks, then the component slots. */
    public static final int DATA_SLOTS = HEADER_SLOTS + MAX_STEPS + MAX_STEPS + MAX_STEPS * MAX_PER_STEP;

    /** Offsets inside the sync array, spelled out so the screen and the menu cannot disagree. */
    public static final int STEP_COUNT_SLOT = 0;
    public static final int SELECTED_SLOT = 1;
    public static final int STEP_HEADER_BASE = HEADER_SLOTS;
    /** One modifier bitmask per step, right after the headers. */
    public static final int MODIFIER_BASE_SLOT = HEADER_SLOTS + MAX_STEPS;
    public static final int COMPONENT_BASE_SLOT = HEADER_SLOTS + MAX_STEPS + MAX_STEPS;

    private final List<Spell.Step> steps = new ArrayList<>();
    private int selected;
    /** The name typed into the table's name box. Goes onto the spell when it is inscribed. */
    private String draftName = "";

    public InscriptionTableBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.INSCRIPTION_TABLE.get(), pos, state);
        // A table starts on a projectile step so there is always something to add components to.
        steps.add(new Spell.Step(SpellShape.PROJECTILE, List.of()));
    }

    // ------------------------------------------------------------------ sync

    /** Header encoding for one step: component count in the high bits, shape ordinal in the low byte. */
    public static int header(int componentCount, SpellShape shape) {
        return (Math.min(componentCount, MAX_PER_STEP) << 8) | (shape.ordinal() & 0xFF);
    }

    public static int headerShape(int header) {
        return Math.floorMod(header & 0xFF, SpellShape.values().length);
    }

    public static int headerCount(int header) {
        return Math.min(MAX_PER_STEP, header >>> 8);
    }

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            if (index == STEP_COUNT_SLOT) {
                return steps.size();
            }
            if (index == SELECTED_SLOT) {
                return selected;
            }
            if (index >= STEP_HEADER_BASE && index < COMPONENT_BASE_SLOT) {
                int stepIndex = index - STEP_HEADER_BASE;
                if (stepIndex >= steps.size()) {
                    return 0;
                }
                Spell.Step step = steps.get(stepIndex);
                return header(step.components().size(), step.shape());
            }
            if (index >= MODIFIER_BASE_SLOT && index < COMPONENT_BASE_SLOT) {
                int stepIndex = index - MODIFIER_BASE_SLOT;
                return stepIndex < steps.size() ? steps.get(stepIndex).modifiers() : 0;
            }
            int flat = index - COMPONENT_BASE_SLOT;
            if (flat < 0) {
                return 0;
            }
            int stepIndex = flat / MAX_PER_STEP;
            int within = flat % MAX_PER_STEP;
            if (stepIndex >= steps.size()) {
                return 0;
            }
            List<SpellComponent> components = steps.get(stepIndex).components();
            return within < components.size() ? components.get(within).ordinal() : 0;
        }

        @Override
        public void set(int index, int value) {
            // Read-only on the server: every change comes from a button click, which runs here.
        }

        @Override
        public int getCount() {
            return DATA_SLOTS;
        }
    };

    public ContainerData data() {
        return data;
    }

    // ------------------------------------------------------------------ reading

    public List<Spell.Step> steps() {
        return List.copyOf(steps);
    }

    public int selectedStep() {
        return Math.min(selected, Math.max(0, steps.size() - 1));
    }

    public Spell toSpell() {
        return new Spell(steps, draftName);
    }

    /** The draft's name, or "" for an unnamed spell. */
    public String draftName() {
        return draftName;
    }

    /** Rename the draft. Clamped here rather than at the codec so the field cannot grow past the sync. */
    public void setDraftName(String name) {
        String next = name == null ? "" : name.strip();
        if (next.length() > Spell.MAX_NAME_LENGTH) {
            next = next.substring(0, Spell.MAX_NAME_LENGTH);
        }
        this.draftName = next;
        setChanged();
    }

    // ------------------------------------------------------------------ editing

    /** Adds a new step of this shape and selects it, so the next component click lands in it. */
    public boolean addStep(SpellShape shape) {
        if (steps.size() >= MAX_STEPS) {
            return false;
        }
        steps.add(new Spell.Step(shape, List.of(), 0));
        selected = steps.size() - 1;
        setChanged();
        return true;
    }

    /** Removes a step (and everything in it). A table always keeps at least one step. */
    public boolean removeStep(int index) {
        if (steps.size() <= 1 || index < 0 || index >= steps.size()) {
            return false;
        }
        steps.remove(index);
        selected = Math.max(0, Math.min(selected, steps.size() - 1));
        setChanged();
        return true;
    }

    public void select(int index) {
        if (index >= 0 && index < steps.size()) {
            this.selected = index;
            setChanged();
        }
    }

    /** Adds a component to the SELECTED step. */
    public boolean add(SpellComponent component) {
        if (steps.isEmpty()) {
            steps.add(new Spell.Step(SpellShape.PROJECTILE, List.of(), 0));
            selected = 0;
        }
        int index = selectedStep();
        Spell.Step step = steps.get(index);
        if (step.components().size() >= MAX_PER_STEP) {
            return false;
        }
        steps.set(index, step.plus(component));
        setChanged();
        return true;
    }

    /**
     * Insert a component into a step at a given position.
     *
     * This is what the drag-and-drop screen calls: the palette row that was dragged, the card it was
     * dropped on, and how far along that card it landed. Order matters - a spell resolves its
     * components in list order - so "where" is as much a part of the edit as "what".
     */
    public boolean insert(SpellComponent component, int stepIndex, int at) {
        if (component == null || steps.isEmpty()) {
            return false;
        }
        int index = Math.max(0, Math.min(steps.size() - 1, stepIndex));
        Spell.Step step = steps.get(index);
        if (step.components().size() >= MAX_PER_STEP) {
            return false;
        }
        List<SpellComponent> components = new ArrayList<>(step.components());
        components.add(Math.max(0, Math.min(components.size(), at)), component);
        steps.set(index, new Spell.Step(step.shape(), components, step.modifiers()));
        selected = index;
        setChanged();
        return true;
    }

    /** Remove one component by position. The screen's right-click. */
    public boolean removeAt(int stepIndex, int at) {
        if (steps.isEmpty()) {
            return false;
        }
        int index = Math.max(0, Math.min(steps.size() - 1, stepIndex));
        Spell.Step step = steps.get(index);
        if (at < 0 || at >= step.components().size()) {
            return false;
        }
        List<SpellComponent> components = new ArrayList<>(step.components());
        components.remove(at);
        steps.set(index, new Spell.Step(step.shape(), components, step.modifiers()));
        setChanged();
        return true;
    }

    /**
     * Move one component to another place in the draft - possibly into another step.
     *
     * Removing and inserting as two separate clicks would let a server tick (or another player at the
     * same table) land between them, which turns a move into a duplicate or a loss. This does it in a
     * single edit, and adjusts the destination when the source was earlier in the same list.
     */
    public boolean move(int fromStep, int fromIndex, int toStep, int toIndex) {
        if (steps.isEmpty()) {
            return false;
        }
        int from = Math.max(0, Math.min(steps.size() - 1, fromStep));
        int to = Math.max(0, Math.min(steps.size() - 1, toStep));
        Spell.Step source = steps.get(from);
        if (fromIndex < 0 || fromIndex >= source.components().size()) {
            return false;
        }
        SpellComponent moved = source.components().get(fromIndex);
        if (from == to) {
            List<SpellComponent> components = new ArrayList<>(source.components());
            components.remove(fromIndex);
            int target = Math.max(0, Math.min(components.size(), toIndex));
            components.add(target, moved);
            steps.set(from, new Spell.Step(source.shape(), components, source.modifiers()));
            selected = from;
            setChanged();
            return true;
        }
        List<SpellComponent> sourceList = new ArrayList<>(source.components());
        sourceList.remove(fromIndex);
        steps.set(from, new Spell.Step(source.shape(), sourceList, source.modifiers()));
        Spell.Step destination = steps.get(to);
        if (destination.components().size() >= MAX_PER_STEP) {
            // The destination is full: put it back rather than silently deleting the component.
            steps.set(from, source);
            return false;
        }
        List<SpellComponent> destinationList = new ArrayList<>(destination.components());
        destinationList.add(Math.max(0, Math.min(destinationList.size(), toIndex)), moved);
        steps.set(to, new Spell.Step(destination.shape(), destinationList, destination.modifiers()));
        selected = to;
        setChanged();
        return true;
    }

    /** Change one step's shape without touching its components (the shape tags on the cards). */
    public boolean setShape(int stepIndex, SpellShape shape) {
        if (steps.isEmpty() || shape == null) {
            return false;
        }
        int index = Math.max(0, Math.min(steps.size() - 1, stepIndex));
        Spell.Step step = steps.get(index);
        steps.set(index, new Spell.Step(shape, step.components(), step.modifiers()));
        selected = index;
        setChanged();
        return true;
    }

    /**
     * Insert a SHAPE as a brand new delivery at {@code at}.
     *
     * This is what makes the model flat: a spell is a list of parts, and a shape part can be dropped
     * anywhere in that list. Everything after the inserted shape now belongs to it - which is exactly
     * how "projectile, then a zone that keeps re-casting" is expressed.
     */
    public boolean insertStepAt(int at, SpellShape shape) {
        if (shape == null) {
            return false;
        }
        if (steps.isEmpty()) {
            steps.add(new Spell.Step(shape, List.of(), 0));
            selected = 0;
            setChanged();
            return true;
        }
        if (steps.size() >= MAX_STEPS) {
            return false;
        }
        steps.add(Math.max(0, Math.min(steps.size(), at)), new Spell.Step(shape, List.of(), 0));
        selected = Math.max(0, Math.min(steps.size() - 1, at));
        setChanged();
        return true;
    }

    /** Move a whole delivery (its shape and every component in it) to another position. */
    public boolean moveStep(int from, int to) {
        if (from < 0 || from >= steps.size() || to < 0 || to >= steps.size() || from == to) {
            return false;
        }
        Spell.Step step = steps.remove(from);
        steps.add(Math.max(0, Math.min(steps.size(), to)), step);
        selected = Math.max(0, Math.min(steps.size() - 1, to));
        setChanged();
        return true;
    }

    /** Removes the last component of the selected step. */
    public void pop() {
        if (steps.isEmpty()) {
            return;
        }
        int index = selectedStep();
        List<SpellComponent> components = new ArrayList<>(steps.get(index).components());
        if (!components.isEmpty()) {
            components.remove(components.size() - 1);
            steps.set(index, new Spell.Step(steps.get(index).shape(), components,
                    steps.get(index).modifiers()));
            setChanged();
        }
    }

    /** Flip one modifier on the SELECTED step. This is the whole modifier UI: one call. */
    public boolean toggleModifier(SpellModifier modifier) {
        if (steps.isEmpty() || modifier == null) {
            return false;
        }
        int index = selectedStep();
        steps.set(index, steps.get(index).toggled(modifier));
        setChanged();
        return true;
    }

    /** Clears every modifier on the selected step. */
    public boolean clearModifiers() {
        if (steps.isEmpty()) {
            return false;
        }
        int index = selectedStep();
        steps.set(index, steps.get(index).withModifiers(0));
        setChanged();
        return true;
    }

    /** Empties every step's components but keeps the step sequence - you built that deliberately. */
    public void clear() {
        boolean changed = false;
        for (int i = 0; i < steps.size(); i++) {
            if (!steps.get(i).components().isEmpty()) {
                steps.set(i, new Spell.Step(steps.get(i).shape(), List.of(), 0));
                changed = true;
            }
        }
        if (changed) {
            setChanged();
        }
    }

    /** Starts over: one empty projectile step. */
    public void reset() {
        steps.clear();
        steps.add(new Spell.Step(SpellShape.PROJECTILE, List.of(), 0));
        selected = 0;
        setChanged();
    }

    // ------------------------------------------------------------------ save / load

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Selected", selected);
        tag.putString("DraftName", draftName);
        ListTag stepList = new ListTag();
        for (Spell.Step step : steps) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Shape", step.shape().id());
            ListTag components = new ListTag();
            for (SpellComponent component : step.components()) {
                components.add(StringTag.valueOf(component.id()));
            }
            entry.put("Components", components);
            if (step.modifiers() != 0) {
                entry.putInt("Modifiers", step.modifiers());
            }
            stepList.add(entry);
        }
        tag.put("Steps", stepList);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        selected = Math.max(0, tag.getInt("Selected"));
        draftName = tag.getString("DraftName");
        steps.clear();
        ListTag stepList = tag.getList("Steps", Tag.TAG_COMPOUND);
        for (int i = 0; i < stepList.size() && steps.size() < MAX_STEPS; i++) {
            CompoundTag entry = stepList.getCompound(i);
            SpellShape shape = SpellShape.byId(entry.getString("Shape"));
            List<SpellComponent> components = new ArrayList<>();
            ListTag componentList = entry.getList("Components", Tag.TAG_STRING);
            for (int c = 0; c < componentList.size() && components.size() < MAX_PER_STEP; c++) {
                components.add(SpellComponent.byId(componentList.getString(c)));
            }
            steps.add(new Spell.Step(shape, components,
                    entry.contains("Modifiers") ? entry.getInt("Modifiers") : 0));
        }
        if (steps.isEmpty()) {
            steps.add(new Spell.Step(SpellShape.PROJECTILE, List.of(), 0));
        }
        selected = Math.min(selected, steps.size() - 1);
    }
}
