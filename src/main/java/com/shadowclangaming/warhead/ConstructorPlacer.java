package com.shadowclangaming.warhead;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Places the constructor warhead's sphere, a budgeted slice per tick.
 *
 * WHY NOT JUST LOOP. A radius-32 sphere is 137,000 blocks; doing that in one tick is a frozen server,
 * and a radius-256 sphere is 70 million. So a build is a JOB: it owns a cursor into the sphere and
 * places at most {@link #BLOCKS_PER_TICK} blocks each server tick, resuming where it stopped. The world
 * fills in visibly from the impact point outwards, which is also the nicest way to watch it happen.
 *
 * WHAT IT REPLACES. Only air, fluids and plants are replaced; anything solid is left alone. That is the
 * "fill holes, do not bulldoze the base" reading of a constructor warhead, and it also means a
 * misplaced sphere cannot shred a player's build.
 *
 * A cell in the payload stops the whole thing: the user's rule is "if you load an AE2 cell, the cell is
 * dropped instead of the items", so a job never unpacks a cell as blocks.
 */
public final class ConstructorPlacer {

    /** Blocks placed per tick. 512 is invisible in a tick budget and still fills fast. */
    public static final int BLOCKS_PER_TICK = 512;

    /** Hard ceiling on queued work, so a coordinate-glitched radius cannot queue forever. */
    public static final int MAX_PENDING = 4096;

    private static final List<Job> JOBS = new ArrayList<>();

    private ConstructorPlacer() {
    }

    /**
     * Plan the build and queue it.
     *
     * @param requestedRadius the slider/typed radius, or -1 to derive it from the payload
     * @return a report for chat: what was built, what was short, what was dropped
     */
    public static Report build(ServerLevel level, BlockPos center, List<ItemStack> payload,
                               int requestedRadius, long seed) {
        if (payload == null || payload.isEmpty()) {
            return Report.failed("no blocks loaded");
        }
        if (Ae2Cells.containsCell(payload)) {
            // The cell travels instead of its contents (see the CARGO/constructor rules in WarheadType).
            return Report.cell();
        }

        // One entry per distinct block, with how many of it there are.
        List<Block> blocks = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        List<ItemStack> leftovers = new ArrayList<>();
        long totalBlocks = 0L;
        for (ItemStack stack : payload) {
            if (stack.isEmpty()) {
                continue;
            }
            boolean isBlock = stack.getItem() instanceof BlockItem;
            if (!isBlock) {
                leftovers.add(stack.copy());
                continue;
            }
            Block block = ((BlockItem) stack.getItem()).getBlock();
            int index = blocks.indexOf(block);
            if (index < 0) {
                blocks.add(block);
                weights.add(stack.getCount());
            } else {
                weights.set(index, weights.get(index) + stack.getCount());
            }
            totalBlocks += stack.getCount();
        }
        if (blocks.isEmpty()) {
            return Report.failed("nothing block-shaped loaded");
        }

        int radius = requestedRadius >= 0 ? requestedRadius : ConstructorBlast.radiusForBlocks(totalBlocks);
        long needed = ConstructorBlast.costForRadius(radius);
        if (totalBlocks < needed) {
            return Report.shortBy(radius, needed - totalBlocks);
        }
        long excess = ConstructorBlast.excessFor(totalBlocks, radius);

        int[] weightArray = new int[weights.size()];
        for (int i = 0; i < weights.size(); i++) {
            weightArray[i] = weights.get(i);
        }

        Job job = new Job(level, center, radius, blocks, weightArray, seed);
        synchronized (JOBS) {
            if (JOBS.size() >= MAX_PENDING) {
                return Report.failed("too many builds in progress");
            }
            JOBS.add(job);
        }
        return new Report(true, radius, needed, excess, leftovers, job.totalCells(), null);
    }

    /** Called once per server tick. */
    public static void tick(MinecraftServer server) {
        synchronized (JOBS) {
            if (JOBS.isEmpty()) {
                return;
            }
            for (Iterator<Job> iterator = JOBS.iterator(); iterator.hasNext(); ) {
                Job job = iterator.next();
                if (!job.step()) {
                    iterator.remove();
                }
            }
        }
    }

    /** How many builds are still filling in, for /scg status. */
    public static int activeJobs() {
        synchronized (JOBS) {
            return JOBS.size();
        }
    }

    /** One sphere being filled. */
    private static final class Job {
        private final ServerLevel level;
        private final BlockPos center;
        private final int radius;
        private final List<Block> blocks;
        private final int[] weights;
        private final long seed;
        private final int total;

        private int x;
        private int y;
        private int z;
        private boolean done;

        Job(ServerLevel level, BlockPos center, int radius, List<Block> blocks, int[] weights, long seed) {
            this.level = level;
            this.center = center;
            this.radius = radius;
            this.blocks = blocks;
            this.weights = weights;
            this.seed = seed;
            this.x = -radius;
            this.y = -radius;
            this.z = -radius;
            this.total = ConstructorBlast.blocksInSphere(radius);
        }

        int totalCells() {
            return total;
        }

        /** @return false when the job is finished (or the world went away) */
        boolean step() {
            if (done || level.getServer() == null) {
                return false;
            }
            long squared = (long) radius * radius;
            int placed = 0;
            while (x <= radius) {
                // Walk in the same order as forEachCell: x, then a disc of y, then z.
                int yLimit = limitFor(squared, x);
                while (y <= yLimit) {
                    long rem = squared - (long) x * x - (long) y * y;
                    if (rem < 0L) {
                        y++;
                        continue;
                    }
                    int zLimit = (int) Math.sqrt((double) rem);
                    while (z <= zLimit) {
                        place(x, y, z);
                        placed++;
                        z++;
                        if (placed >= BLOCKS_PER_TICK) {
                            return true;
                        }
                    }
                    z = -zLimit;
                    y++;
                }
                x++;
                if (x <= radius) {
                    int next = limitFor(squared, x);
                    y = -next;
                    z = -next;
                }
            }
            done = true;
            return false;
        }

        private static int limitFor(long squared, int x) {
            long remX = squared - (long) x * x;
            return remX < 0L ? -1 : (int) Math.sqrt((double) remX);
        }

        private void place(int dx, int dy, int dz) {
            BlockPos target = center.offset(dx, dy, dz);
            if (!level.isLoaded(target)) {
                return;
            }
            BlockState state = level.getBlockState(target);
            if (!replaceable(state)) {
                return;
            }
            int pick = ConstructorBlast.pickBlended(weights, seed, dx, dy, dz);
            if (pick < 0) {
                return;
            }
            level.setBlock(target, blocks.get(pick).defaultBlockState(), Block.UPDATE_CLIENTS);
        }

        /** Anything you could walk through and that is not already a build. */
        private static boolean replaceable(BlockState state) {
            if (state.isAir()) {
                return true;
            }
            if (!state.getFluidState().isEmpty()) {
                return true;
            }
            Block block = state.getBlock();
            return block == Blocks.SHORT_GRASS || block == Blocks.TALL_GRASS || block == Blocks.FERN
                    || block == Blocks.LARGE_FERN || block == Blocks.DEAD_BUSH || block == Blocks.SNOW
                    || block == Blocks.VINE || block == Blocks.SEAGRASS;
        }
    }

    /** What the player is told after firing one. */
    public record Report(boolean ok, int radius, long blocksPlaced, long excessDropped,
                         List<ItemStack> nonBlockItems, int totalCells, String problem) {

        static Report failed(String problem) {
            return new Report(false, 0, 0L, 0L, List.of(), 0, problem);
        }

        static Report shortBy(int radius, long missing) {
            return new Report(false, radius, 0L, 0L, List.of(), 0,
                    "not enough blocks: radius " + radius + " needs " + missing + " more");
        }

        static Report cell() {
            return new Report(true, 0, 0L, 0L, List.of(), 0, "cell");
        }

        /** True when the payload was a cell and the cell should simply be dropped. */
        public boolean isCell() {
            return "cell".equals(problem);
        }
    }
}
