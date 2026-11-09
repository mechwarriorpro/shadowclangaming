package com.shadowclangaming.launcher;

/**
 * Upgrade Kits: the only way to raise a launcher part's tier.
 *
 * One tier at a time, in order, and never skipping. A tier 4 kit on a tier 2 pad says "you need the
 * tier 3 kit first" rather than silently wasting the kit - the point of the ladder is that each step
 * costs something.
 */
public final class UpgradeKit {

    public static final int MIN_TIER = 1;

    /** Result of trying to apply a kit. */
    public enum Result {
        UPGRADED,
        ALREADY_MAX,
        WRONG_KIT,
        OK
    }

    private UpgradeKit() {
    }

    /**
     * @param currentTier the block's tier
     * @param kitTier     the kit's own tier
     */
    public static Result apply(int currentTier, int kitTier) {
        if (currentTier >= LauncherLevel.MAX) {
            return Result.ALREADY_MAX;
        }
        if (kitTier != currentTier + 1) {
            return Result.WRONG_KIT;
        }
        return Result.UPGRADED;
    }

    public static int nextTier(int currentTier, int kitTier) {
        return apply(currentTier, kitTier) == Result.UPGRADED ? kitTier : currentTier;
    }
}
