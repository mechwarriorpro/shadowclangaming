package com.shadowclangaming.launcher;

/**
 * Missile module tiers.
 *
 * "Tier 1 through 5 missile modules — warheads can be added to any missile module, however max range is
 * determined by module tier, with t5 having infinite range and t1 having a range of 10k blocks."
 *
 * So the module is the AIRFRAME and the warhead is the CARGO: any warhead fits any frame, and the only
 * thing the frame decides is how far it can fly. That is a much better shape than per-warhead missiles,
 * because it turns range into a build decision instead of a shopping decision.
 *
 * The ladder is deliberately not linear - range doubles then jumps, so a tier-5 module is a genuinely
 * different object (a strategic weapon) rather than "tier 4 with more numbers":
 *
 *   T1   10,000 blocks   ~625 chunks   tactical
 *   T2   25,000 blocks   ~1,560 chunks
 *   T3   50,000 blocks   ~3,125 chunks  regional
 *   T4  150,000 blocks   ~9,375 chunks
 *   T5  infinite                       strategic: anywhere in the dimension
 *
 * Pure data with no Minecraft imports, so the harness can check the ladder and the range gate.
 */
public enum ModuleTier {
    T1(1, 10_000L),
    T2(2, 25_000L),
    T3(3, 50_000L),
    T4(4, 150_000L),
    /** Infinite: any target in the same dimension, and (with a level-5 launcher) across dimensions. */
    T5(5, Long.MAX_VALUE);

    /** What an ordinary missile (a plain MissileItem, no module) is treated as. Unchanged behaviour. */
    public static final ModuleTier DEFAULT = T5;

    private final int tier;
    private final long range;

    ModuleTier(int tier, long range) {
        this.tier = tier;
        this.range = range;
    }

    public int tier() {
        return tier;
    }

    /** Maximum launch distance in blocks. {@link Long#MAX_VALUE} for T5. */
    public long range() {
        return range;
    }

    public boolean isInfinite() {
        return range == Long.MAX_VALUE;
    }

    /** "10,000 blocks" / "unlimited". */
    public String rangeLabel() {
        return isInfinite() ? "unlimited" : String.format("%,d blocks", range);
    }

    /**
     * Can a module of this tier reach a target {@code distanceSqr} away?
     *
     * Compared in squared space so a 150,000-block shot does not round badly. An infinite tier skips the
     * test entirely, which also keeps the maths away from Long overflow.
     */
    public boolean canReach(double distanceSqr) {
        if (isInfinite()) {
            return true;
        }
        if (distanceSqr < 0.0D || Double.isNaN(distanceSqr)) {
            return false;
        }
        double range = this.range;
        return distanceSqr <= range * range;
    }

    /** How much further a target would have to be to be out of reach, for the refusal message. */
    public long overBy(double distanceSqr) {
        if (isInfinite() || canReach(distanceSqr)) {
            return 0L;
        }
        return Math.max(0L, (long) Math.ceil(Math.sqrt(distanceSqr)) - range);
    }

    /** Tier by its number; anything out of range falls back to T1 rather than throwing. */
    public static ModuleTier of(int tier) {
        for (ModuleTier value : values()) {
            if (value.tier == tier) {
                return value;
            }
        }
        return T1;
    }

    /** The tier below this one, or itself at T1. Used by the upgrade ladder. */
    public ModuleTier lower() {
        return this == T1 ? T1 : of(tier - 1);
    }
}
