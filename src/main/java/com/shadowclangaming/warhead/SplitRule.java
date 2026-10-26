package com.shadowclangaming.warhead;

/**
 * When a cluster warhead opens up.
 *
 * Pulled out of {@code MissileEntity} so the rule can be proved on a bare JVM: the interesting part is a
 * comparison, not a world. Two modes:
 *
 *   ALTITUDE   the player set a Y level. Split when the missile is descending at or below it. The descent
 *              check is not decoration - a silo in the mountains starts BELOW its own split altitude, and
 *              without "descending" the missile would break up in the tube.
 *   PROXIMITY  nobody set an altitude, so the frame's own rule applies: split when the aim point is closer
 *              than {@code trigger} blocks.
 */
public final class SplitRule {

    /**
     * "No altitude set". Deliberately the same value {@link MissilePayload#NO_SPLIT_ALTITUDE} uses - that
     * constant now reads from this one, so the two can never drift.
     */
    public static final int AUTO = Integer.MIN_VALUE;

    private SplitRule() {
    }

    /**
     * @param y             the missile's current Y
     * @param verticalSpeed its current vertical velocity (negative = falling)
     * @param altitude      the player-set split altitude, or {@link #AUTO}
     * @param distance      distance to the aim point, in blocks
     * @param trigger       the frame's proximity trigger, in blocks
     */
    public static boolean shouldSplit(double y, double verticalSpeed, int altitude, double distance,
                                      double trigger) {
        if (altitude != AUTO) {
            if (verticalSpeed > 0.0D) {
                return false;
            }
            return y <= altitude;
        }
        return distance < trigger;
    }
}
