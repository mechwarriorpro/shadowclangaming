package com.shadowclangaming.launcher;

import java.util.ArrayList;
import java.util.List;

/**
 * The silo structure: what a launch pad actually needs around it before it will fire.
 *
 * WHY. A single block that launches intercontinental ballistic missiles is not a silo, it is a vending
 * machine. The reference build has a framed pit with bracing rods and a control position, so that is what
 * this enforces: **eight support rods** in a ring around the pad, **one control panel** on the edge, and a
 * **clear shaft** above the pad for the missile to leave through. Anything missing and the pad refuses to
 * launch, and it says which piece is missing.
 *
 * WHY THIS CLASS HAS NO MINECRAFT IMPORTS. The pattern is data, so the harness can prove the shape, the
 * symmetry and the refusal messages without a world. The block-reading half lives in
 * {@code LaunchPadBlockEntity}, which supplies a {@link Probe}.
 *
 * The pattern is deliberately small (5x5, 8 rods) rather than elaborate: a structure gate should be a
 * design constraint you satisfy once, not a puzzle. It also means a silo can be built into a wall, inside
 * a mountain, or on the deck of a Sable subgrid without fighting the geometry.
 */
public final class SiloStructure {

    /** Radius of the framing ring, in blocks, from the pad. */
    public static final int FRAME_RADIUS = 2;

    /** How many blocks of clear headroom the missile needs above the pad. */
    public static final int SHAFT_HEIGHT = 8;

    /** The parts a silo is made of. */
    public enum Part {
        /** The launch pad itself: the controller of the whole thing. */
        PAD,
        /** A support rod in the framing ring. */
        ROD,
        /** A control panel, anywhere on the ring's outside edge. */
        PANEL
    }

    /** One required block, relative to the pad. */
    public record Offset(int x, int y, int z, Part part) {
    }

    /**
     * The eight rods: every position on the 5x5 ring except the four corners, plus the corners.
     *
     * That is all eight perimeter positions of a 3x3 ring at radius 2 - a square frame with the pad in
     * the middle and nothing in between, so a silo is a hole with a cage around it.
     */
    public static final List<Offset> PATTERN = buildPattern();

    /** The four places a control panel may sit: the middle of each outside edge, one block further out. */
    public static final List<int[]> PANEL_SLOTS = List.of(
            new int[]{0, 0, -(FRAME_RADIUS + 1)},
            new int[]{0, 0, FRAME_RADIUS + 1},
            new int[]{-(FRAME_RADIUS + 1), 0, 0},
            new int[]{FRAME_RADIUS + 1, 0, 0});

    private static List<Offset> buildPattern() {
        List<Offset> pattern = new ArrayList<>(9);
        pattern.add(new Offset(0, 0, 0, Part.PAD));
        // The four corners. Written as an explicit pair of extremes rather than a range loop: a
        // `for (x = -R; x <= R; x += R)` walks -2, 0, 2 and quietly puts a rod requirement on the pad's own
        // position, which would make every silo permanently incomplete.
        int[] extremes = {-FRAME_RADIUS, FRAME_RADIUS};
        for (int x : extremes) {
            for (int z : extremes) {
                pattern.add(new Offset(x, 0, z, Part.ROD));
            }
        }
        // The four edge midpoints, so the frame is a square cage and not four lonely posts.
        for (int side = -1; side <= 1; side += 2) {
            pattern.add(new Offset(side * FRAME_RADIUS, 0, 0, Part.ROD));
            pattern.add(new Offset(0, 0, side * FRAME_RADIUS, Part.ROD));
        }
        return List.copyOf(pattern);
    }

    /** The number of rods the pattern requires, for the tooltip and the tests. */
    public static int requiredRods() {
        int count = 0;
        for (Offset offset : PATTERN) {
            if (offset.part() == Part.ROD) {
                count++;
            }
        }
        return count;
    }

    /**
     * What the world looks like around a pad. Implemented by the block entity, faked by the tests.
     */
    public interface Probe {
        /** Is the block at this offset the required part? */
        boolean matches(int dx, int dy, int dz, Part part);

        /** Is this offset inside the shaft above the pad, and free of anything a missile would hit? */
        boolean shaftClear(int dx, int dy, int dz);
    }

    /** The answer: complete, or exactly what is wrong. */
    public record Report(boolean complete, List<String> problems) {

        public static Report ok() {
            return new Report(true, List.of());
        }

        /** One line for a chat message or a GUI status field. */
        public String summary() {
            if (complete) {
                return "silo complete";
            }
            return problems.isEmpty() ? "silo incomplete" : String.join(", ", problems);
        }
    }

    private SiloStructure() {
    }

    /**
     * Check a pad's surroundings.
     *
     * The order of the checks is the order a player would fix them in: rods first (the visible part), then
     * the panel (the part they always forget), then the shaft (the part that ruins their roof).
     */
    public static Report inspect(Probe probe) {
        List<String> problems = new ArrayList<>();

        int rodsMissing = 0;
        for (Offset offset : PATTERN) {
            if (offset.part() == Part.ROD
                    && !probe.matches(offset.x(), offset.y(), offset.z(), Part.ROD)) {
                rodsMissing++;
            }
        }
        if (rodsMissing > 0) {
            problems.add(rodsMissing + "/" + requiredRods() + " support rods");
        }

        boolean panel = false;
        for (int[] slot : PANEL_SLOTS) {
            if (probe.matches(slot[0], slot[1], slot[2], Part.PANEL)) {
                panel = true;
                break;
            }
        }
        if (!panel) {
            problems.add("no control panel");
        }

        int blocked = 0;
        for (int dy = 1; dy <= SHAFT_HEIGHT; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (!probe.shaftClear(dx, dy, dz)) {
                        blocked++;
                    }
                }
            }
        }
        if (blocked > 0) {
            problems.add(blocked + " blocks in the shaft");
        }

        return new Report(problems.isEmpty(), List.copyOf(problems));
    }

    /**
     * The state a designator or a terminal cares about: can this pad shoot?
     *
     * Kept as a method rather than a boolean on the report because the message and the answer are used in
     * different places - the GUI wants the text, the launch path wants the flag.
     */
    public static boolean launchable(Report report) {
        return report.complete();
    }

    /** The shaft volume in blocks, for the docs and for a rough "is this thing huge" estimate. */
    public static int shaftVolume() {
        return 9 * SHAFT_HEIGHT;
    }
}
