package com.shadowclangaming.warhead;

/**
 * The price of a Numismatic warhead.
 *
 * DESIGN TARGETS (this is the whole spec, expressed as two constants):
 *   - radius 4 blocks costs 100 units
 *   - radius 4096 blocks costs 256 Suns = 1,048,576 units
 *
 * A power law through two points is unique, so the exponent is DERIVED, not hand-picked:
 *
 *   cost = 100 * (radius / 4) ^ k        where k = ln(1048576 / 100) / ln(4096 / 4)
 *                                             = 1.335614
 *
 * Which means every DOUBLING of radius costs 2^k = 2.524x more:
 *
 *   radius      4 ->        100   (0.02 Suns)
 *   radius      8 ->        252   (0.06 Suns)
 *   radius     16 ->        637   (0.16 Suns)
 *   radius     32 ->      1,608   (0.39 Suns)
 *   radius     64 ->      4,057   (0.99 Suns)
 *   radius    128 ->     10,240   (2.50 Suns)
 *   radius    512 ->     65,226   (15.92 Suns)
 *   radius   1024 ->    164,619   (40.19 Suns)
 *   radius   4096 ->  1,048,576   (256.00 Suns)
 *
 * Note this replaces the earlier "1.25x per doubling" rule. Those two rules are mathematically
 * incompatible: 1.25x per doubling would make radius 4096 cost 932 units, not 1,048,576. The
 * 2.524x curve is what makes big warheads a genuine late-game investment instead of pocket change.
 *
 * THERE IS NO ECONOMY CAP ON RADIUS. You may load any amount of currency; the only limits are
 * physical:
 *   - the vanilla world border (29,999,984 blocks from the centre of the world)
 *   - 64-bit integer limits on the cost values
 * Anything past the world border is pointless (there are no blocks out there), so the math saturates
 * there rather than wrapping around.
 */
public final class Funding {

    /** Radius (in blocks) you get for BASE_COST. */
    public static final long BASE_RADIUS = 4L;

    /** Price of a BASE_RADIUS warhead, in the smallest currency unit (1 = 1 spur / 1 war bond unit). */
    public static final long BASE_COST = 100L;

    /** Reference radius used by the price target below. */
    public static final long TARGET_RADIUS = 4096L;

    /** Value of one Sun coin in the smallest unit. */
    public static final long SUN_VALUE = 4096L;

    /** Price of a TARGET_RADIUS warhead: 256 Suns. */
    public static final long TARGET_COST = 256L * SUN_VALUE;

    /**
     * Derived exponent: 1.335614.
     * Every doubling of radius multiplies the price by 2^1.335614 = 2.524.
     */
    public static final double COST_EXPONENT =
            Math.log((double) TARGET_COST / BASE_COST) / Math.log((double) TARGET_RADIUS / BASE_RADIUS);

    /**
     * Physical limit, NOT an economy cap: the world border sits 29,999,984 blocks from the origin,
     * so a radius beyond that has no blocks left to destroy. Saturating here also keeps the maths
     * inside 64-bit and int ranges.
     */
    public static final long WORLD_EDGE = 29_999_984L;

    /** Cost to reach a given blast radius, in currency units. */
    public static long costForRadius(long radius) {
        long clamped = Math.max(1L, Math.min(WORLD_EDGE, radius));
        double value = BASE_COST * Math.pow((double) clamped / BASE_RADIUS, COST_EXPONENT);
        if (value >= (double) Long.MAX_VALUE) {
            return Long.MAX_VALUE;
        }
        return (long) Math.ceil(value);
    }

    /** Blast radius bought by a given amount of funding. Uncapped apart from the world edge. */
    public static int radiusForFunding(long funding) {
        if (funding <= 0L) {
            return 0;
        }
        double radius = BASE_RADIUS
                * Math.pow((double) funding / BASE_COST, 1.0 / COST_EXPONENT);
        if (radius >= (double) WORLD_EDGE) {
            return (int) WORLD_EDGE;
        }
        return (int) Math.max(1.0, Math.floor(radius));
    }

    /**
     * Converts a blast radius into the vanilla "explosion power" number.
     * TNT is 4 and destroys roughly a 5-block sphere, so power is a bit under the radius.
     */
    public static float powerForRadius(int radius) {
        return Math.max(0.0F, radius * 0.8F);
    }

    /** Convenience: funding straight to explosion power. */
    public static float powerForFunding(long funding) {
        return powerForRadius(radiusForFunding(funding));
    }

    /** How many Sun coins a price is, for tooltips. */
    public static double sunsFor(long cost) {
        return (double) cost / SUN_VALUE;
    }

    private Funding() {
    }
}
