package com.shadowclangaming.spell;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * One STEP of a spell: how that step is delivered. A spell is a SEQUENCE of these, which is what makes
 * "projectile, then a zone, then a rune" possible:
 *
 *   PROJECTILE  flies, and on impact the spell continues from where it landed
 *   ZONE        leaves a 30-second area that re-runs the rest of the spell every 5 seconds
 *   RUNE        leaves a trap that runs the rest of the spell once, when something walks over it
 *   SELF / TOUCH / BEAM / AURA   resolve immediately and pass control to the next step
 *
 * SHAPES ARE NOT FREE. Each has its own base mana cost, so a ten-component spell costs more with a
 * zone step than with a self step. There are no reagents - the table, mana and mana capacity are the
 * only prices, which is the rule the user set.
 */
public enum SpellShape implements StringRepresentable {
    /** Lands on the caster. Buffs, heals, blinks. 10 mana. */
    SELF("self", 10L, 0.0D),
    /** Short-range reach, up to 4 blocks. 30 mana. */
    TOUCH("touch", 30L, 4.0D),
    /** A bolt that flies and lands where it hits, up to 48 blocks. 60 mana. */
    PROJECTILE("projectile", 60L, 48.0D),
    /** Instant line of effect up to 32 blocks. 100 mana. */
    BEAM("beam", 100L, 32.0D),
    /** Everything within 6 blocks of the caster, friend and foe alike. 150 mana. */
    AURA("aura", 150L, 6.0D),
    /**
     * A lingering area. 250 mana, 6-block radius, lasts {@link #ZONE_SECONDS} and re-runs the rest of
     * the spell from here every {@link #ZONE_PULSE_SECONDS}.
     */
    ZONE("zone", 250L, 6.0D),
    /**
     * A trap: 120 mana, triggers once when a living thing walks within 1.5 blocks, lasts
     * {@link #RUNE_SECONDS} untriggered.
     */
    RUNE("rune", 120L, 1.5D);

    public static final Codec<SpellShape> CODEC = StringRepresentable.fromEnum(SpellShape::values);

    /** How long a ZONE lasts at power x1.0, in seconds. */
    public static final int ZONE_SECONDS = 30;
    /** How often a ZONE re-runs the rest of its spell at power x1.0, in seconds. 30 / 5 = six pulses. */
    public static final int ZONE_PULSE_SECONDS = 5;
    /** How long an untriggered RUNE waits before fading, in seconds. */
    public static final int RUNE_SECONDS = 300;

    // ---- how a zone scales with POWER ------------------------------------------------------------
    //
    // The rule, as specified: more power means a zone lasts LONGER and re-casts FASTER.
    //
    //   duration  = 30 s + 1 s per level
    //   recast    =  5 s - 0.25 s per level      (never faster than 1 s)
    //
    // Levels are counted ABOVE the baseline, exactly as the emitter assembly counts them: the first
    // level of assembly is x1.00 power and therefore level 0. An 8-emitter assembly (x1.95) is zone
    // level 7 - a 37-second zone pulsing every 3.25 s, eleven pulses instead of six, each one already
    // 1.95x damage. The two effects multiply, and that is the point: power is not a number on a
    // tooltip, it is how much of the world a zone can hold down.

    /** One second of extra zone lifetime per power level. */
    public static final int ZONE_SECONDS_PER_LEVEL = 1;
    /** A quarter second less between pulses, per power level. */
    public static final double ZONE_PULSE_REDUCTION_PER_LEVEL = 0.25D;
    /** The recast floor. Below a second it stops being a zone and becomes a damage-over-time bug. */
    public static final double ZONE_MIN_PULSE_SECONDS = 1.0D;
    /** What one power level is worth: the same +10% an emitter assembly gives. */
    public static final double POWER_PER_LEVEL = 1.10D;

    /**
     * The power LEVEL behind a multiplier: x1.0 is level 0, x1.10 is level 1, x1.21 is level 2.
     *
     * This is the inverse of {@code BlueprintMath.clusterPower}, so a zone scaled by a 27-emitter
     * assembly (level 27, x11.9) is exactly as scaled as the damage the same gun does.
     */
    public static int powerLevel(float power) {
        if (!(power > 1.0F)) {
            return 0;
        }
        int level = (int) Math.round(Math.log(power) / Math.log(POWER_PER_LEVEL));
        return Math.max(0, Math.min(64, level));
    }

    /** How long a zone cast at {@code power} lasts, in seconds. */
    public static int zoneSeconds(float power) {
        return ZONE_SECONDS + powerLevel(power) * ZONE_SECONDS_PER_LEVEL;
    }

    /** How many ticks between pulses for a zone cast at {@code power}. */
    public static int zonePulseTicks(float power) {
        double seconds = ZONE_PULSE_SECONDS - powerLevel(power) * ZONE_PULSE_REDUCTION_PER_LEVEL;
        seconds = Math.max(ZONE_MIN_PULSE_SECONDS, seconds);
        return (int) Math.round(seconds * 20.0D);
    }

    /** How many times a zone cast at {@code power} will pulse in its lifetime. */
    public static int zonePulses(float power) {
        return zoneSeconds(power) * 20 / Math.max(1, zonePulseTicks(power));
    }

    /** "38 s, recast 3.0 s (12 pulses)" - used by the shape tooltip. */
    public static String zoneSummary(float power) {
        return zoneSeconds(power) + " s, recast "
                + String.format("%.2f", zonePulseTicks(power) / 20.0D) + " s ("
                + zonePulses(power) + " pulses)";
    }

    /** Does this step hand control to something persistent (a zone or a rune) rather than continuing? */
    public boolean isPersistent() {
        return this == ZONE || this == RUNE;
    }

    private final String id;
    private final long baseCost;
    private final double range;

    SpellShape(String id, long baseCost, double range) {
        this.id = id;
        this.baseCost = baseCost;
        this.range = range;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String id() {
        return id;
    }

    /** Mana for the shape alone, before any component is added. */
    public long baseCost() {
        return baseCost;
    }

    /** Reach in blocks: touch/beam length, projectile range or aura radius. */
    public double range() {
        return range;
    }

    public String translationKey() {
        return "shape.shadowclangaming." + id;
    }

    public static SpellShape byId(String id) {
        for (SpellShape shape : values()) {
            if (shape.id.equalsIgnoreCase(id)) {
                return shape;
            }
        }
        return SELF;
    }
}
