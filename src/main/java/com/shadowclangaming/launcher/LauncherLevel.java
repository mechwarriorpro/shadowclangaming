package com.shadowclangaming.launcher;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * What level a launcher actually is.
 *
 * A pad's own tier is only one of the three things that matter, and the other two are physical:
 *
 *   pad tier          the launch pad itself (upgraded with Upgrade Kits)
 *   control panel     a panel within 6 blocks, tier >= the pad's
 *   support rods      at least two rods within 6 blocks, both tier >= the pad's
 *
 * So a "level 5 launcher" is not a number in a save file - it is a structure somebody had to build and
 * pay for, and knocking out the rods downgrades it immediately. The interdimensional module checks
 * exactly this, which is why you cannot bolt it onto a level 1 pad.
 */
public final class LauncherLevel {

    public static final int MIN = 1;
    public static final int MAX = 5;
    /** How far from the pad the panel and rods may sit. */
    public static final int STRUCTURE_RADIUS = 6;
    /** How many support rods a full launcher needs. */
    public static final int REQUIRED_RODS = 2;

    /** Everything the caller needs to know, in one value. */
    public record Report(int padTier, int panelTier, int rods, int rodTier, int effectiveLevel,
                         boolean complete) {

        public String summary() {
            return "pad " + padTier + ", panel " + panelTier + ", rods " + rods + "x" + rodTier
                    + " -> level " + effectiveLevel + (complete ? "" : " (incomplete)");
        }
    }

    private LauncherLevel() {
    }

    /**
     * Scan the structure around a pad.
     *
     * @param padTier the pad's own upgrade level
     */
    public static Report scan(ServerLevel level, BlockPos padPos, int padTier) {
        int panelTier = 0;
        int rods = 0;
        int lowestRodTier = Integer.MAX_VALUE;

        for (BlockPos cursor : BlockPos.betweenClosed(
                padPos.offset(-STRUCTURE_RADIUS, -STRUCTURE_RADIUS, -STRUCTURE_RADIUS),
                padPos.offset(STRUCTURE_RADIUS, STRUCTURE_RADIUS, STRUCTURE_RADIUS))) {
            BlockEntity blockEntity = level.getBlockEntity(cursor);
            if (blockEntity instanceof com.shadowclangaming.block.ControlPanelBlockEntity panel) {
                panelTier = Math.max(panelTier, panel.tier());
            } else if (blockEntity instanceof com.shadowclangaming.block.SupportRodBlockEntity rod) {
                rods++;
                lowestRodTier = Math.min(lowestRodTier, rod.tier());
            }
        }
        if (rods == 0) {
            lowestRodTier = 0;
        }

        // The launcher is only as good as its weakest part.
        int rodComponent = rods >= REQUIRED_RODS ? lowestRodTier : 0;
        int effective = Math.min(padTier, Math.min(panelTier, rodComponent));
        boolean complete = effective >= MIN && padTier == panelTier
                && rods >= REQUIRED_RODS && lowestRodTier >= padTier;

        return new Report(padTier, panelTier, rods, rods == 0 ? 0 : lowestRodTier,
                Math.max(0, effective), complete);
    }

    /** Does this structure meet a required level on every part? */
    public static boolean meets(Report report, int requiredLevel) {
        return report.padTier() >= requiredLevel
                && report.panelTier() >= requiredLevel
                && report.rods() >= REQUIRED_RODS
                && report.rodTier() >= requiredLevel;
    }

    public static boolean isValidTier(int tier) {
        return tier >= MIN && tier <= MAX;
    }
}
