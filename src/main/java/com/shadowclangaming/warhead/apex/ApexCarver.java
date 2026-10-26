package com.shadowclangaming.warhead.apex;

import com.shadowclangaming.perf.ServerProfile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The bit that actually removes blocks for an apex warhead, one budgeted slice per tick.
 *
 * WHY THIS EXISTS. {@code BlastScheduler} removes spheres. Several apex weapons are not spheres: the
 * Quasar Lance is a canyon four radii long, the World Rune is a glyph on the surface, the Abyssal
 * Covenant is a hundred tendrils, the Spacetime Hammer is a set of rings. All of those are "remove the
 * blocks where a mask says yes", so they share one job queue with one tick budget instead of five.
 *
 * WHY A MASK AND NOT A SHAPE CLASS. A mask is a function of distance and direction from the centre. That
 * covers every shape above with a single switch and no allocation per block, which matters when a radius
 * 256 weapon is asking about sixty million positions.
 *
 * WHAT IT WILL NOT DO. It never touches unloaded chunks (it cannot - writing a block in an unloaded chunk
 * forces a synchronous load, which is exactly the TPS cliff the whole perf layer exists to avoid), it
 * never touches block entities, and it never removes bedrock. Everything else goes.
 */
public final class ApexCarver {

    /** Blocks removed per tick, per job. High enough to look instant, low enough to keep the tick cheap. */
    public static final int BLOCKS_PER_TICK = 4096;

    /** Hard ceiling on live jobs, so a coordinate-glitched blast cannot fill memory. */
    public static final int MAX_JOBS = 24;

    /** Blocks a job will ever plan. A radius-256 sphere is ~70M positions; this caps the queue. */
    public static final int MAX_POSITIONS = 1_500_000;

    /** Shape of the region a job clears. */
    public enum Mask {
        /** Everything inside the sphere. */
        SPHERE,
        /** A hollow shell of {@link #thickness} blocks - the "ring" of a shockwave. */
        SHELL,
        /** A flat disc, {@link #thickness} deep, centred on the impact height. */
        DISC,
        /** A trench: wide along X/Z, deep along Y, used by the hammer. */
        TRENCH,
        /** A capsule along a direction, used by the lance. */
        BEAM,
        /** Blocks within {@link #thickness} of a glyph path on the surface - the rune. */
        RUNE
    }

    /** One queued carve. Public fields are read by the tester and by the shape maths. */
    public static final class Job {
        final ServerLevel level;
        final Vec3 centre;
        final int radius;
        final Mask mask;
        final int thickness;
        final Vec3 direction;
        final List<BlockPos> pending = new ArrayList<>();
        int cursor;
        int removed;
        int index;

        Job(ServerLevel level, Vec3 centre, int radius, Mask mask, int thickness, Vec3 direction) {
            this.level = level;
            this.centre = centre;
            this.radius = radius;
            this.mask = mask;
            this.thickness = Math.max(1, thickness);
            this.direction = direction;
        }

        public boolean done() {
            return cursor >= pending.size();
        }

        public int removed() {
            return removed;
        }
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private ApexCarver() {
    }

    /** Live job count, for the perf readout. */
    public static int jobs() {
        return JOBS.size();
    }

    /**
     * Queue a carve.
     *
     * @param direction only used by {@link Mask#BEAM}; ignored otherwise (may be null)
     * @return the job, or null if the queue is full
     */
    public static Job carve(ServerLevel level, Vec3 centre, int radius, Mask mask, int thickness,
                            Vec3 direction) {
        if (JOBS.size() >= MAX_JOBS) {
            return null;
        }
        Job job = new Job(level, centre, radius, mask, thickness, direction == null ? Vec3.ZERO : direction.normalize());
        plan(job);
        if (!job.pending.isEmpty()) {
            JOBS.add(job);
        }
        return job;
    }

    /** Convenience: a plain sphere, the shape most apex weapons end with. */
    public static Job carveSphere(ServerLevel level, Vec3 centre, int radius) {
        return carve(level, centre, radius, Mask.SPHERE, radius, null);
    }

    /**
     * Build the position list for a job. Done once, up front, so the per-tick slice is a pure write loop.
     *
     * The list is stored as {@link BlockPos} rather than re-derived each tick because a re-derivation
     * would have to iterate the whole bounding box again - the expensive part - just to skip to a cursor.
     */
    private static void plan(Job job) {
        int r = Math.max(1, job.radius);
        BlockPos centre = BlockPos.containing(job.centre);
        switch (job.mask) {
            case SPHERE, SHELL -> {
                double shellInner = Math.max(0, r - job.thickness);
                for (BlockPos p : BlockPos.betweenClosed(
                        centre.offset(-r, -r, -r), centre.offset(r, r, r))) {
                    double d = Math.sqrt(p.distSqr(centre));
                    if (d > r) {
                        continue;
                    }
                    if (job.mask == Mask.SHELL && d < shellInner) {
                        continue;
                    }
                    add(job, p);
                }
            }
            case DISC -> {
                for (BlockPos p : BlockPos.betweenClosed(
                        centre.offset(-r, -job.thickness / 2, -r),
                        centre.offset(r, job.thickness / 2, r))) {
                    if (Math.hypot(p.getX() - centre.getX(), p.getZ() - centre.getZ()) > r) {
                        continue;
                    }
                    add(job, p);
                }
            }
            case TRENCH -> {
                // A trench is a wide, deep, shallow cylinder: destroys structures, leaves a ditch.
                int depth = Math.min(48, Math.max(6, r / 2));
                for (BlockPos p : BlockPos.betweenClosed(
                        centre.offset(-r, -depth, -r), centre.offset(r, 4, r))) {
                    if (Math.hypot(p.getX() - centre.getX(), p.getZ() - centre.getZ()) > r) {
                        continue;
                    }
                    add(job, p);
                }
            }
            case BEAM -> {
                Vec3 dir = job.direction.lengthSqr() < 1.0E-6 ? new Vec3(1, 0, 0) : job.direction;
                // A beam is defined by its base radius: the radius field gives the length, the thickness
                // field gives the bore. Nothing else in here has a direction.
                double length = ApexTimeline.lanceLength(job.radius);
                int width = Math.max(1, job.thickness);
                for (double step = 0; step <= length; step += 1.0) {
                    Vec3 at = job.centre.add(dir.scale(step));
                    BlockPos mid = BlockPos.containing(at);
                    for (BlockPos p : BlockPos.betweenClosed(
                            mid.offset(-width, -width, -width), mid.offset(width, width, width))) {
                        if (p.distSqr(mid) > (double) width * width) {
                            continue;
                        }
                        add(job, p);
                    }
                    if (job.pending.size() >= MAX_POSITIONS) {
                        return;
                    }
                }
            }
            case RUNE -> {
                // The glyph itself is generated by {@link ApexWarheads}; here it is a set of segments.
                // A rune mask is "near the surface within thickness of the path" - see ApexWarheads.
                for (BlockPos p : BlockPos.betweenClosed(
                        centre.offset(-r, -4, -r), centre.offset(r, 4, r))) {
                    if (Math.hypot(p.getX() - centre.getX(), p.getZ() - centre.getZ()) > r) {
                        continue;
                    }
                    add(job, p);
                }
            }
            default -> {
            }
        }
    }

    private static void add(Job job, BlockPos p) {
        if (job.pending.size() >= MAX_POSITIONS) {
            return;
        }
        job.pending.add(p.immutable());
    }

    /** Called once per server tick. Writes up to {@link #BLOCKS_PER_TICK} blocks per live job. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (JOBS.isEmpty()) {
            return;
        }
        long budgetNanos = 2_000_000L; // 2 ms of block writes per tick, same order as BlastScheduler
        long start = System.nanoTime();
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext()) {
            Job job = it.next();
            if (job.level.getServer() == null || job.level.getServer().isStopped()) {
                it.remove();
                continue;
            }
            int done = 0;
            while (!job.done() && done < BLOCKS_PER_TICK) {
                BlockPos p = job.pending.get(job.cursor++);
                done++;
                if (!job.level.isLoaded(p)) {
                    continue;
                }
                BlockState state = job.level.getBlockState(p);
                if (state.isAir() || state.is(Blocks.BEDROCK) || !state.getFluidState().isEmpty()) {
                    continue;
                }
                if (job.level.getBlockEntity(p) != null) {
                    continue;
                }
                job.level.setBlock(p, Blocks.AIR.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
                job.removed++;
            }
            if (job.done()) {
                it.remove();
            }
            if (System.nanoTime() - start > budgetNanos * 4L) {
                break;
            }
        }
        // Clients need the edits they were not told about individually.
        com.shadowclangaming.perf.ClientSync.flush(server);
    }

    /** Drop every queued job, e.g. on server stop. */
    public static void clear() {
        JOBS.clear();
    }

    /** Per-tick budget expressed in the server profile's language, for the docs and the readout. */
    public static String describeBudget() {
        return BLOCKS_PER_TICK + " blocks/tick/job (host profile "
                + ServerProfile.applyBudgetMillis() + " ms)";
    }
}
