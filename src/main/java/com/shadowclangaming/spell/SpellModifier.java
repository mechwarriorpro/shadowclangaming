package com.shadowclangaming.spell;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Spell MODIFIERS: the second dial on a step, on top of its components.
 *
 * A component says *what* happens. A modifier says *how* it happens - louder, wider, twice, from your
 * victim's blood. This is the old Ars Magica 2 modifier row, and it is deliberately a separate axis so
 * the two combine instead of multiplying the component list into thousands of near-identical entries.
 *
 * ──────────────────── THE RULE ────────────────────
 *
 * Modifiers are per STEP and stack multiplicatively into that step's cost:
 *
 *     step cost = (shape base + Σ component costs) × (1 + Σ modifier percentages)
 *
 * so a fire-damage bolt with EMPOWERED and TWINNED costs 60 + 25 × 2.60 = 221 mana and does 1.5x
 * damage twice. Nothing here is free and nothing here is capped; the gate is still mana capacity.
 *
 * Each modifier is stored as a bit in a single int on the step, which is what keeps the whole thing
 * (codec, network, the synced GUI array) cheap.
 */
public enum SpellModifier implements StringRepresentable {

    /** +60 %: damage and healing from this step are ×1.5. */
    EMPOWERED("empowered", 60),
    /**
     * +70 %: areas reach ×1.6 - aura radius, zone radius, firestorm footprint, chain range - and a
     * projectile hits everything within 2.5 blocks of its impact instead of one thing.
     */
    WIDE("wide", 70),
    /** +45 %: damage ignores armour and resistance and clears the victim's damage cooldown. */
    PIERCING("piercing", 45),
    /** +80 %: the caster heals 35 % of all damage this step deals. */
    VAMPIRIC("vampiric", 80),
    /** +100 %: the step resolves twice. Zones leave two zones; bolts fire two bolts. */
    TWINNED("twinned", 100),
    /** +25 %: projectiles fly at double speed, which makes them much harder to dodge. */
    SWIFT("swift", 25),
    /** +65 %: zones and runes last 50 % longer. Nothing else in the step changes. */
    LASTING("lasting", 65),
    /** +10 %: no sounds and only a quarter of the particles. Quiet magic out of sight. */
    SILENT("silent", 10),

    // ---- the YGGDRASIL metamagic, from Overlord ------------------------------------------------
    /**
     * Maximize Magic: damage and healing are taken at their top roll instead of varying. In this mod
     * that is simply a flat multiplier, because nothing here rolls dice - it is the expensive,
     * reliable version of a spell.
     */
    MAXIMIZE("maximize", 90),
    /** Triplet Magic: the step resolves three times. Overrides Twinned when both are on. */
    TRIPLET("triplet", 180),
    /** Boost Magic: a quarter more damage and a quarter more duration on anything that persists. */
    BOOST("boost", 50),
    /** Delay Magic: the step lands three seconds after you cast it. Traps, ambushes, artillery. */
    DELAY("delay", 40),
    /**
     * Long Chant: 25 % off the price for a two-second cast time. The Megumin trade - and unlike her,
     * this one is repeatable.
     */
    CHANT("chant", -25),

    // ════════════════ THE DEEP ARCANA ════════════════
    //
    // Fifteen more. These are the expensive half of the system: the cheap modifiers are about making a
    // spell bigger, and these are about changing WHAT IT IS. A step carrying three of them costs more
    // than most players will ever have capacity for, which is the point - they are build goals.

    /** +200 %: bolts steer. A bolt with Seeker turns toward the nearest living thing within 24 blocks. */
    SEEKER("seeker", 200),
    /**
     * +160 %: the step happens TWICE. Two seconds after it resolves, a half-power copy of it fires from
     * the same place. Unlike Twinned (which is simultaneous) this one is a second event, so it beats
     * invulnerability - the first hit's cooldown has long expired by the time the echo lands.
     */
    ECHO("echo", 160),
    /** +170 %: every point of damage dealt hands the caster 6 mana back. Mana is power, and so is this. */
    SIPHON("siphon", 170),
    /** +190 %: the caster gains absorption worth half the damage the step deals. */
    BASTION("bastion", 190),
    /**
     * +240 %: x1.6 on everything, and x2.25 against anything already under 35 % health. A finisher -
     * the same grammar as Grasp Heart, applied to whatever component you like.
     */
    EXECUTE("execute", 240),
    /** +400 %: x2.0 damage and x1.5 area at once. The single loudest thing on this list. */
    CATACLYSM("cataclysm", 400),
    /** +300 %: zones and runes last three times as long. A 90-second zone, or a 15-minute rune. */
    ETERNAL("eternal", 300),
    /** +450 %: the step resolves FOUR times. Beats Triplet. Volleys of four, four zones, four touches. */
    LEGION("legion", 450),
    /** +220 %: area x2.2, beating Wide. A 13-block zone, a 19-block aura. */
    EPICENTRE("epicentre", 220),
    /** +210 %: victims are left weak, slow and unable to dig for 8 seconds. */
    CRIPPLE("cripple", 210),
    /** +130 %: every beneficial effect on the victim is stripped BEFORE the damage lands. */
    DISPEL("dispel", 130),
    /** +330 %: the step detonates. A block-safe blast, radius 2 + 2 per point of power, plus knockback. */
    NOVA("nova", 330),
    /** +240 %: a random heavy curse - wither, poison, blindness, nausea or levitation. */
    HEX("hex", 240),
    /** +300 %: victims are rooted for 3 seconds: no walking, no jumping. */
    BIND("bind", 300),
    /**
     * -70 %: the step is paid for in BLOOD. The caster loses 25 % of maximum health when it resolves.
     * The only modifier with a negative price, and the only one that can kill you to cast.
     */
    BLOOD_PRICE("blood_price", -70);

    public static final Codec<SpellModifier> CODEC = StringRepresentable.fromEnum(SpellModifier::values);

    private final String id;
    private final int manaPercent;

    SpellModifier(String id, int manaPercent) {
        this.id = id;
        this.manaPercent = manaPercent;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String id() {
        return id;
    }

    /** Extra percent of the step's cost this modifier adds. */
    public int manaPercent() {
        return manaPercent;
    }

    public int bit() {
        return 1 << ordinal();
    }

    /** Is {@code mask} carrying this modifier? */
    public boolean in(int mask) {
        return (mask & bit()) != 0;
    }

    public static boolean has(int mask, SpellModifier modifier) {
        return modifier != null && (mask & modifier.bit()) != 0;
    }

    /** Toggle one modifier in a mask. */
    public static int toggle(int mask, SpellModifier modifier) {
        return mask ^ modifier.bit();
    }

    /** The multiplicity for a mask: cost multiplier, e.g. 2.60 for EMPOWERED + TWINNED. */
    public static double costMultiplier(int mask) {
        int percent = 0;
        for (SpellModifier modifier : values()) {
            if (modifier.in(mask)) {
                percent += modifier.manaPercent();
            }
        }
        return 1.0D + percent / 100.0D;
    }

    /** Damage / healing multiplier from a mask. */
    public static double powerMultiplier(int mask) {
        double multiplier = 1.0D;
        if (EMPOWERED.in(mask)) {
            multiplier *= 1.5D;
        }
        if (MAXIMIZE.in(mask)) {
            multiplier *= 1.35D;
        }
        if (BOOST.in(mask)) {
            multiplier *= 1.25D;
        }
        if (EXECUTE.in(mask)) {
            multiplier *= 1.6D;
        }
        if (CATACLYSM.in(mask)) {
            multiplier *= 2.0D;
        }
        return multiplier;
    }

    /** Extra damage against a target already below 35 % health. 1.0 without EXECUTE. */
    public static double executeMultiplier(int mask) {
        return EXECUTE.in(mask) ? 2.25D : 1.0D;
    }

    /** Does this step charge the caster's health rather than (only) their mana? */
    public static boolean bloodPrice(int mask) {
        return BLOOD_PRICE.in(mask);
    }

    /** Area multiplier from a mask. */
    public static double areaMultiplier(int mask) {
        double multiplier = 1.0D;
        if (WIDE.in(mask)) {
            multiplier = Math.max(multiplier, 1.6D);
        }
        if (CATACLYSM.in(mask)) {
            multiplier = Math.max(multiplier, 1.5D);
        }
        // EPICENTRE is the largest single area modifier, so it wins outright when stacked.
        if (EPICENTRE.in(mask)) {
            multiplier = Math.max(multiplier, 2.2D);
        }
        return multiplier;
    }

    /** How many times the step resolves. Triplet beats Twinned. */
    public static int repeats(int mask) {
        if (LEGION.in(mask)) {
            return 4;
        }
        if (TRIPLET.in(mask)) {
            return 3;
        }
        return TWINNED.in(mask) ? 2 : 1;
    }

    /**
     * How many ticks late the step lands, or 0 for "now".
     *
     * DELAY is the expensive, long one; CHANT is the cheap, short one. They do not stack into an
     * eight-second cast - the longer wins.
     */
    public static int delayTicks(int mask) {
        if (DELAY.in(mask)) {
            return 60;
        }
        if (CHANT.in(mask)) {
            return 40;
        }
        return 0;
    }

    /** Lifetime multiplier for anything that persists. */
    public static double durationMultiplier(int mask) {
        double multiplier = LASTING.in(mask) ? 1.5D : 1.0D;
        if (BOOST.in(mask)) {
            multiplier *= 1.25D;
        }
        if (ETERNAL.in(mask)) {
            multiplier *= 3.0D;
        }
        return multiplier;
    }

    /** One mask bit per modifier, for the codec: a missing field means "no modifiers". */
    public static int maskOf(Iterable<SpellModifier> modifiers) {
        int mask = 0;
        for (SpellModifier modifier : modifiers) {
            mask |= modifier.bit();
        }
        return mask;
    }

    public static java.util.List<SpellModifier> unpack(int mask) {
        java.util.List<SpellModifier> list = new java.util.ArrayList<>();
        for (SpellModifier modifier : values()) {
            if (modifier.in(mask)) {
                list.add(modifier);
            }
        }
        return list;
    }

    public String translationKey() {
        return "modifier.shadowclangaming." + id;
    }

    public static SpellModifier byId(String id) {
        for (SpellModifier modifier : values()) {
            if (modifier.id.equalsIgnoreCase(id)) {
                return modifier;
            }
        }
        return EMPOWERED;
    }
}
