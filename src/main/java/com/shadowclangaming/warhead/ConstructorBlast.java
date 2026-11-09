package com.shadowclangaming.warhead;

/**
 * The constructor warhead's arithmetic: what a bag of blocks becomes.
 *
 * THE RULE THE PLAYER ASKED FOR
 *   "spherical blast based on number of items loaded to the nearest perfect sphere and drop excess"
 *
 * So: count the blocks, find the largest ball of whole blocks whose sphere you can fill COMPLETELY,
 * build that, and hand the leftover back rather than smearing it over the rim.
 *
 * WHY IT IS ITS OWN CLASS. This is all integer and double maths with no Minecraft types in it, so the
 * logic harness can check it - and a sphere that is off by one shell is a build a player notices
 * immediately (a hole in the ground under their base).
 *
 * A ball of radius r in a block grid contains every cell (x,y,z) with x^2+y^2+z^2 <= r^2.
 */
public final class ConstructorBlast {

    /** Radii below this are memoised: the GUI slider asks for the same counts over and over. */
    private static final int MEMO_LIMIT = 512;

    /** -1 = not computed yet. Filled on demand, because most sessions never ask for a big sphere. */
    private static final int[] MEMO = new int[MEMO_LIMIT + 1];

    static {
        java.util.Arrays.fill(MEMO, -1);
        MEMO[0] = 1;
    }

    private ConstructorBlast() {
    }

    /**
     * Number of blocks in a solid ball of radius {@code radius}.
     *
     * radius 0 is one block (the centre), radius 1 is 7, radius 2 is 33, radius 3 is 123.
     *
     * The count is a sum of horizontal disc widths, O(r^2): radius 64 is about 16k steps, radius 512
     * about a million, so a slider drag stays cheap and the answer is memoised after the first ask.
     */
    public static int blocksInSphere(int radius) {
        if (radius < 0) {
            return 0;
        }
        if (radius <= MEMO_LIMIT && MEMO[radius] >= 0) {
            return MEMO[radius];
        }
        long total = 0L;
        long squared = (long) radius * radius;
        for (int x = -radius; x <= radius; x++) {
            long remX = squared - (long) x * x;
            if (remX < 0L) {
                continue;
            }
            int limit = (int) Math.sqrt((double) remX);
            for (int y = -limit; y <= limit; y++) {
                long rem = remX - (long) y * y;
                if (rem < 0L) {
                    continue;
                }
                total += 2L * (int) Math.sqrt((double) rem) + 1L;
            }
        }
        int value = (int) Math.min(Integer.MAX_VALUE, total);
        if (radius <= MEMO_LIMIT) {
            MEMO[radius] = value;
        }
        return value;
    }

    /**
     * The same count as {@link #blocksInSphere(int)} but as a {@code long}.
     *
     * Needed because a radius-4096 sphere is about 2.9e11 blocks: the int version clamps, which is right
     * for "can the payload fill this" and wrong for a GUI that wants to print the real number. One loop
     * serves both, so there is no second definition of a sphere in the mod.
     */
    public static long blocksInSphereLong(int radius) {
        if (radius < 0) {
            return 0L;
        }
        long total = 0L;
        long squared = (long) radius * radius;
        for (int x = -radius; x <= radius; x++) {
            long remX = squared - (long) x * x;
            if (remX < 0L) {
                continue;
            }
            int limit = (int) Math.sqrt((double) remX);
            for (int y = -limit; y <= limit; y++) {
                long rem = remX - (long) y * y;
                if (rem < 0L) {
                    continue;
                }
                total += 2L * (int) Math.sqrt((double) rem) + 1L;
            }
        }
        return total;
    }

    /**
     * The largest radius whose whole sphere fits in {@code blocks} blocks.
     *
     * This is the "nearest perfect sphere": radius 2 needs 33 blocks, so 32 blocks gets you radius 1.
     */
    public static int radiusForBlocks(long blocks) {
        if (blocks <= 0L) {
            return 0;
        }
        int radius = 0;
        while (radius < 4096 && blocksInSphere(radius + 1) <= blocks) {
            radius++;
        }
        return radius;
    }

    /** How many blocks are needed to fill a sphere of this radius. */
    public static int costForRadius(int radius) {
        return blocksInSphere(Math.max(0, radius));
    }

    /** Blocks left over after filling the largest sphere the payload can afford - dropped, not placed. */
    public static long excessFor(long blocks, int radius) {
        return Math.max(0L, blocks - blocksInSphere(Math.max(0, radius)));
    }

    /**
     * Can the payload fill this exact radius?
     *
     * The player may override the radius with the slider. If they ask for more than they loaded the
     * sphere is not built at all - a half-built shell is worse than the sphere they can afford - and
     * the caller reports the shortfall.
     */
    public static boolean affordable(long blocks, int radius) {
        return blocks >= blocksInSphere(Math.max(0, radius));
    }

    /** How many more blocks a requested radius needs. Zero when it is already affordable. */
    public static long shortfall(long blocks, int radius) {
        return Math.max(0L, blocksInSphere(Math.max(0, radius)) - blocks);
    }

    /**
     * Which payload entry to place at a given cell.
     *
     * "If multiple items used, blend block placement using random noise style." The blend is a
     * deterministic hash of the cell position and a seed, weighted by how many of each block were
     * loaded - so a 50/50 mix of stone and cobble looks like static rather than a checkerboard, and
     * the same missile always builds the same way.
     *
     * @param weights how many of each payload entry are loaded (proportional mixing)
     * @return the index into {@code weights}, or -1 when there is nothing to place
     */
    public static int pickBlended(int[] weights, long seed, int x, int y, int z) {
        int total = 0;
        for (int weight : weights) {
            total += Math.max(0, weight);
        }
        if (total <= 0) {
            return -1;
        }
        int roll = (int) Math.floorMod(hash(seed, x, y, z), (long) total);
        int cursor = 0;
        for (int i = 0; i < weights.length; i++) {
            cursor += Math.max(0, weights[i]);
            if (roll < cursor) {
                return i;
            }
        }
        return weights.length - 1;
    }

    /**
     * Position hash for the blend.
     *
     * A splitmix-style finaliser over the cell and seed. Not cryptographic: it just has to look random
     * at block scale and be stable across runs, which is what "noise style" means.
     */
    public static long hash(long seed, int x, int y, int z) {
        long h = seed;
        h ^= x * 0x9E3779B97F4A7C15L;
        h = mix(h);
        h ^= y * 0xC2B2AE3D27D4EB4FL;
        h = mix(h);
        h ^= z * 0x165667B19E3779F9L;
        return mix(h);
    }

    private static long mix(long value) {
        long z = value;
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    /** Called once per cell of the sphere. Returning false stops the walk early. */
    public interface CellVisitor {
        boolean visit(int dx, int dy, int dz);
    }

    /**
     * Walk every cell of a solid ball without building a list.
     *
     * A radius-128 sphere is 8.8 million blocks; materialising that as a List would be about a
     * gigabyte, so the placer visits cells instead.
     */
    public static void forEachCell(int radius, CellVisitor visitor) {
        long squared = (long) radius * radius;
        for (int x = -radius; x <= radius; x++) {
            long remX = squared - (long) x * x;
            if (remX < 0L) {
                continue;
            }
            int limit = (int) Math.sqrt((double) remX);
            for (int y = -limit; y <= limit; y++) {
                long rem = remX - (long) y * y;
                if (rem < 0L) {
                    continue;
                }
                int zLimit = (int) Math.sqrt((double) rem);
                for (int z = -zLimit; z <= zLimit; z++) {
                    if (!visitor.visit(x, y, z)) {
                        return;
                    }
                }
            }
        }
    }
}
