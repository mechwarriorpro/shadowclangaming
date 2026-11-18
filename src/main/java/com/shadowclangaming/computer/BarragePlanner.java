package com.shadowclangaming.computer;

import com.shadowclangaming.warhead.BarragePlan;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Turns "blanket that area" into one aim point per launcher.
 *
 * This is the brain behind the terminal's {@code barrage} command (and behind the example program that
 * boots with every terminal). Pure Java, no Minecraft imports: the interesting part is the arithmetic -
 * how far apart do neighbouring shots go so the craters just touch - and that is far easier to prove in a
 * harness than by standing in a field counting craters.
 *
 * THE MODEL. Each launcher fires ONE missile: a pad holds a single round and reloads by hand or by
 * automation. So a carpet is built out of launchers, not out of shots. Given N launchers the planner lays
 * out an N-cell grid (the largest square grid that fits, scratch cells dropped), spaced so that adjacent
 * blast circles overlap by {@link BarragePlan#OVERLAP} of their radius, and points every launcher at its
 * own cell. Add launchers and the covered square grows; that is the intended progression.
 *
 * SPACING COMES FROM THE SMALLEST WARHEAD in the group, never the average: a launcher carrying a 10-block
 * warhead next to one carrying a 40-block warhead must not be given a 25-block spacing, or the small
 * warhead leaves a gap exactly where it fired.
 */
public final class BarragePlanner {

    private BarragePlanner() {
    }

    /**
     * One launcher as the planner sees it.
     *
     * @param id          anything stable - a block position string - so the caller can map shots back
     * @param x           launcher X (reporting only)
     * @param z           launcher Z (reporting only)
     * @param blastRadius blast radius of the warhead it is loaded with, in blocks
     * @param ready       false = skip it entirely (empty pad, broken silo, disarmed panel)
     */
    public record Launcher(String id, int x, int z, int blastRadius, boolean ready) {
    }

    /** One launcher's orders: a single aim point. */
    public record Assignment(Launcher launcher, BarragePlan.Shot shot) {
    }

    /**
     * The result of planning.
     *
     * @param assignments   launcher/aim-point pairs, centred-first
     * @param spacing       block spacing used between neighbouring shots
     * @param coveredExtent how far from the centre the carpet actually reaches
     * @param skipped       launchers that could not be used (not ready, or no usable radius)
     * @param surplus       usable launchers with no cell (more launchers than the requested area needs)
     * @param shortfall     true when the requested extent needs a bigger grid than the launchers allow
     */
    public record Plan(List<Assignment> assignments, int spacing, int coveredExtent, int skipped,
                       int surplus, boolean shortfall) {
        public int shots() {
            return assignments.size();
        }
    }

    /**
     * Plan a carpet centred on {@code centreX, centreZ}.
     *
     * @param extent block radius the player asked for; 0 or less = "as much as you can cover"
     */
    public static Plan plan(int centreX, int centreZ, int extent, List<Launcher> launchers) {
        List<Launcher> usable = new ArrayList<>();
        int skipped = 0;
        int smallest = Integer.MAX_VALUE;
        for (Launcher launcher : launchers) {
            if (!launcher.ready() || launcher.blastRadius() <= 0) {
                skipped++;
                continue;
            }
            usable.add(launcher);
            smallest = Math.min(smallest, launcher.blastRadius());
        }
        if (usable.isEmpty()) {
            return new Plan(List.of(), 0, 0, skipped, 0, extent > 0);
        }
        int spacing = Math.max(1, (int) Math.round(BarragePlan.spacing(smallest)));

        // Largest square grid that always fits: 1, 4, 9, 16 ... cells. Spare launchers are reported, not
        // given fractional cells - two launchers aiming at the same crater is a wasted missile.
        int side = Math.max(1, (int) Math.floor(Math.sqrt(usable.size())));

        // Offsets are symmetric about the centre: -k .. +k for odd sides, and half a step off-centre for
        // even ones, so an even grid still has no cell sitting exactly on the middle line twice.
        int low = -((side - 1) / 2);
        List<int[]> offsets = new ArrayList<>();
        for (int i = 0; i < side; i++) {
            for (int j = 0; j < side; j++) {
                offsets.add(new int[]{low + i, low + j});
            }
        }
        // Centre-first: if the area is clipped by the requested extent, the surviving cells are the ones
        // nearest the middle rather than an arbitrary corner.
        offsets.sort(Comparator.comparingInt(offset -> offset[0] * offset[0] + offset[1] * offset[1]));

        List<BarragePlan.Shot> shots = new ArrayList<>();
        for (int[] offset : offsets) {
            int x = centreX + offset[0] * spacing;
            int z = centreZ + offset[1] * spacing;
            if (extent > 0 && (Math.abs(x - centreX) > extent || Math.abs(z - centreZ) > extent)) {
                continue;
            }
            shots.add(new BarragePlan.Shot(x, z));
        }

        int maxOffset = (side - 1) / 2;
        int coveredExtent = maxOffset * spacing;
        boolean shortfall = extent > 0 && coveredExtent < extent;
        int surplus = Math.max(0, usable.size() - shots.size());

        List<Assignment> assignments = new ArrayList<>();
        for (int i = 0; i < shots.size(); i++) {
            assignments.add(new Assignment(usable.get(i), shots.get(i)));
        }
        return new Plan(assignments, spacing, coveredExtent, skipped, surplus, shortfall);
    }

    /**
     * How many launchers are needed to carpet an area of {@code extent} blocks, given a warhead of
     * {@code blastRadius}. Used by the shell to say "you need nine silos for that" instead of silently
     * covering a quarter of it.
     */
    public static int launchersFor(int extent, int blastRadius) {
        if (extent <= 0 || blastRadius <= 0) {
            return 1;
        }
        int spacing = Math.max(1, (int) Math.round(BarragePlan.spacing(blastRadius)));
        int perAxis = 2 * (int) Math.floor(extent / (double) spacing) + 1;
        return perAxis * perAxis;
    }
}
