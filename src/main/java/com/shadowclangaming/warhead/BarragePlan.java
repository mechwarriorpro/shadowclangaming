package com.shadowclangaming.warhead;

import java.util.ArrayList;
import java.util.List;

/**
 * The blanket barrage: turn N launchers and a blast radius into a grid of aim points.
 *
 * THE PROBLEM. A launcher fires at a point. A warhead covers a circle. Blanketing an area therefore is not
 * "fire everywhere" - it is choosing a grid whose cells are just smaller than the blast circles, so
 * neighbouring craters overlap a little and nothing survives in the gaps. Too wide and you leave standing
 * columns between craters; too tight and you waste launchers re-glassing ground you already flattened.
 *
 * THE NUMBER THAT MATTERS. For circles of radius R on a square grid, the worst case is the centre of a
 * cell, whose distance to the nearest crater centre is R*sqrt(2)/2 ≈ 0.7071R. So a spacing of R*sqrt(2)
 * EXACTLY covers the cell corners and touches circles with zero overlap - which is why the default is
 * slightly tighter than that: {@link #OVERLAP} pulls the spacing in until the circles cut each other by a
 * visible margin.
 *
 * Spacing = R * sqrt(2) * (1 - OVERLAP). With OVERLAP = 0.25 a radius-10 warhead is fired every 10.6
 * blocks instead of 14.1, so the circles overlap by about a quarter of a radius everywhere.
 *
 * Pure Java, no Minecraft imports - the harness proves the coverage and the ring-to-grid conversion.
 */
public final class BarragePlan {

    /** How much the grid is tightened below the exact-cover spacing. 0.25 = a quarter of a radius of overlap. */
    public static final double OVERLAP = 0.25D;

    /** sqrt(2): the diagonal of a unit cell. A circle of radius R exactly covers a cell of side R*sqrt(2). */
    public static final double ROOT_TWO = Math.sqrt(2.0D);

    /** No barrage is allowed to plan more shots than this, however generous the area. */
    public static final int MAX_SHOTS = 4096;

    /** One aim point. */
    public record Shot(int x, int z) {
    }

    private BarragePlan() {
    }

    /** Grid spacing for a warhead of this radius: circles overlap by {@link #OVERLAP} of a radius. */
    public static double spacing(double radius) {
        return Math.max(1.0D, radius * ROOT_TWO * (1.0D - OVERLAP));
    }

    /**
     * Plan a blanket over a square area.
     *
     * @param centreX  centre of the area
     * @param centreZ  centre of the area
     * @param radius   the warhead's blast radius in blocks
     * @param extent   half-width of the area to cover, in blocks
     * @return aim points, centre-first so the middle of the target goes first
     */
    public static List<Shot> plan(int centreX, int centreZ, double radius, int extent) {
        List<Shot> shots = new ArrayList<>();
        if (radius <= 0.0D || extent <= 0) {
            shots.add(new Shot(centreX, centreZ));
            return shots;
        }
        double spacing = spacing(radius);
        int steps = (int) Math.floor(extent / spacing);
        if (steps > 64) {
            steps = 64; // a hard geometric cap; MAX_SHOTS catches the rest
        }

        // Rings outward from the centre: the middle of the target is what the commander is watching, so it
        // goes first. Chebyshev rings (max(|i|,|j|) == ring) walk a square grid without duplicates, which is
        // fiddly to do by hand and trivial to do by checking the ring membership directly.
        int step = (int) Math.round(spacing);
        for (int ring = 0; ring <= steps; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    shots.add(new Shot(centreX + i * step, centreZ + j * step));
                    if (shots.size() >= MAX_SHOTS) {
                        return shots.subList(0, MAX_SHOTS);
                    }
                }
            }
        }
        return shots;
    }

    /** How many aim points a barrage needs, without building the list (for a "is this sane?" check). */
    public static int shotCount(double radius, int extent) {
        if (radius <= 0.0D || extent <= 0) {
            return 1;
        }
        double spacing = spacing(radius);
        int perAxis = 2 * (int) Math.floor(extent / spacing) + 1;
        long total = (long) perAxis * perAxis;
        return (int) Math.min(MAX_SHOTS, total);
    }

    /**
     * Split the plan across the launchers that are available.
     *
     * Round-robin, so every tube gets a similar share and the barrage arrives as a wave rather than one
     * silo emptying itself while the rest watch. The launchers also have their own reload timers, which is
     * exactly why the plan is handed out evenly instead of in order.
     */
    public static List<List<Shot>> distribute(List<Shot> plan, int launchers) {
        List<List<Shot>> out = new ArrayList<>();
        for (int i = 0; i < Math.max(1, launchers); i++) {
            out.add(new ArrayList<>());
        }
        for (int i = 0; i < plan.size(); i++) {
            out.get(i % out.size()).add(plan.get(i));
        }
        return out;
    }
}
