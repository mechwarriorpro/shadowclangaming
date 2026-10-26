package com.shadowclangaming.perf;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Budgeted explosions with parallel geometry planning.
 *
 * ──────────────────────────── WHAT THIS ACTUALLY DOES ────────────────────────────
 *
 * 1. PLANNING IS MULTITHREADED (safe, and it is where the CPU time goes).
 *    "Which blocks are inside this sphere, grouped into 16^3 sections" is pure maths on immutable
 *    values. No world access happens on the worker threads, so there is no thread-safety risk.
 *
 * 2. BLOCK REMOVAL STAYS ON THE SERVER THREAD (this cannot be multithreaded).
 *    Minecraft's world is single-threaded by design: chunk sections, the light engine, heightmaps,
 *    block entities and the packet queue all assume one thread. Writing blocks from a worker thread
 *    corrupts chunks and crashes the server. So we spread the work over ticks instead.
 *
 * 3. A WALL-CLOCK BUDGET PER TICK.
 *    Instead of "N sections per tick", it applies sections until a millisecond budget is used up
 *    (ServerProfile.applyBudgetMillis: 3ms dedicated, 2ms when the server is hosted on a player's
 *    machine). That keeps the tick cheap on slow hardware and lets fast hardware finish a blast in
 *    fewer ticks. This is the knob that protects both TPS and the host player's FPS.
 *
 * 4. WHOLE SECTIONS AT A TIME.
 *    Sections entirely inside the sphere skip all distance checks. Neighbour updates are suppressed
 *    (Block.UPDATE_CLIENTS), which is the single biggest saving: vanilla's explosion does neighbour
 *    updates per block.
 *
 * ──────────────────────────── WHAT IT DELIBERATELY DOESN'T DO ────────────────────────────
 *
 * There is no GPU path, because there cannot be one. A dedicated server has no GPU, and a client's
 * GPU cannot write block state back into the simulation. See docs/PERFORMANCE.md.
 */
public final class BlastScheduler {

    public static final BlastScheduler INSTANCE = new BlastScheduler();

    /** Never let one blast take longer than this many ticks (5 seconds). */
    private static final int MAX_TICKS_PER_BLAST = 100;

    /** Refuse to plan blasts bigger than this many blocks (memory guard). */
    public static final int MAX_BLOCKS = 3_000_000;

    /** Blasts at or below this radius use vanilla's explosion: it is already fast and looks right. */
    public static final int VANILLA_THRESHOLD = 12;

    private volatile ExecutorService planner = newPlanner();

    private static ExecutorService newPlanner() {
        return Executors.newFixedThreadPool(
            Math.max(1, Runtime.getRuntime().availableProcessors() / 2),
            new ThreadFactory() {
                private final AtomicInteger counter = new AtomicInteger();

                @Override
                public Thread newThread(Runnable runnable) {
                    Thread thread = new Thread(runnable, "voltz-blast-planner-" + counter.incrementAndGet());
                    thread.setDaemon(true);
                    thread.setPriority(Thread.NORM_PRIORITY - 1);
                    return thread;
                }
            });
    }

    /** Called at each server start; integrated servers may open several worlds in one JVM. */
    public synchronized void prepare() {
        if (planner.isShutdown()) {
            planner = newPlanner();
        }
    }

    private final Deque<BlastJob> queue = new ArrayDeque<>();

    private BlastScheduler() {
    }

    public int queuedBlasts() {
        return queue.size();
    }

    /**
     * Queue a blast. Small blasts are handed straight to vanilla.
     *
     * MUST be called from the server thread.
     */
    public void submit(ServerLevel level, Vec3 centre, int radius, float power) {
        if (radius <= VANILLA_THRESHOLD) {
            level.explode(null, centre.x, centre.y, centre.z, power, Level.ExplosionInteraction.TNT);
            return;
        }

        // ServerStoppingEvent can close this process-wide worker before a late staged apex tick gets here.
        // Never let a closed executor turn that shutdown race into a server crash.
        if (level.getServer() == null || level.getServer().isStopped()) {
            return;
        }
        if (planner.isShutdown()) {
            ShadowClanGaming.LOGGER.warn("Blast planner is shutting down; using vanilla explosion for radius {}", radius);
            level.explode(null, centre.x, centre.y, centre.z, power, Level.ExplosionInteraction.TNT);
            return;
        }

        final int clamped = Math.max(1, radius);
        final Vec3 origin = new Vec3(Math.floor(centre.x) + 0.5, Math.floor(centre.y) + 0.5,
                Math.floor(centre.z) + 0.5);

        ExecutorService worker = planner;
        BlastJob job = new BlastJob(level, origin, clamped, power);
        queue.addLast(job);
        ShadowClanGaming.LOGGER.debug("Blast plan submitted: radius={} power={} queued={}",
                clamped, power, queue.size());
        com.shadowclangaming.debug.ShadowDebug.log("BLAST_PLAN", "submitted radius={} power={} queued={}",
                clamped, power, queue.size());

        try {
            CompletableFuture
                    .supplyAsync(() -> plan(origin, clamped), worker)
                    .whenComplete((planned, throwable) -> {
                        // Runs on a worker thread: only touches the job's volatile fields.
                        if (throwable != null || planned == null) {
                            job.fail();
                        } else {
                            job.completePlanning(planned);
                        }
                    });
        } catch (java.util.concurrent.RejectedExecutionException shuttingDown) {
            // Shutdown can win between isShutdown() above and supplyAsync(). Remove the queued job before
            // falling back, otherwise the next server session can inherit an unplanned head-of-line job.
            queue.remove(job);
            if (level.getServer() != null && !level.getServer().isStopped()) {
                ShadowClanGaming.LOGGER.warn("Blast planning rejected during shutdown; using vanilla explosion");
                level.explode(null, centre.x, centre.y, centre.z, power, Level.ExplosionInteraction.TNT);
            }
        }
    }

    /**
     * PURE MATHS. No world access, therefore safe on a worker thread.
     * Groups the sphere into 16^3 sections and flags the fully-interior ones.
     */
    private static List<BlastJob.SectionWork> plan(Vec3 centre, int radius) {
        long estimated = (long) (Math.pow(radius * 2.0 + 1.0, 3.0) * 0.52);
        if (estimated > MAX_BLOCKS) {
            return null;
        }

        int cx = (int) Math.floor(centre.x);
        int cy = (int) Math.floor(centre.y);
        int cz = (int) Math.floor(centre.z);
        double radiusSq = (double) radius * radius;

        Map<Long, LongList> buckets = new HashMap<>();

        for (int x = cx - radius; x <= cx + radius; x++) {
            double dx = x + 0.5 - centre.x;
            double dxSq = dx * dx;
            if (dxSq > radiusSq) {
                continue;
            }
            for (int y = cy - radius; y <= cy + radius; y++) {
                double dy = y + 0.5 - centre.y;
                double dxySq = dxSq + dy * dy;
                if (dxySq > radiusSq) {
                    continue;
                }
                for (int z = cz - radius; z <= cz + radius; z++) {
                    double dz = z + 0.5 - centre.z;
                    if (dxySq + dz * dz > radiusSq) {
                        continue;
                    }
                    long section = SectionPos.asLong(x >> 4, y >> 4, z >> 4);
                    buckets.computeIfAbsent(section, key -> new LongList()).add(BlockPos.asLong(x, y, z));
                }
            }
        }

        List<BlastJob.SectionWork> sections = new ArrayList<>(buckets.size());
        for (Map.Entry<Long, LongList> entry : buckets.entrySet()) {
            long packedSection = entry.getKey();
            int sx = SectionPos.x(packedSection);
            int sy = SectionPos.y(packedSection);
            int sz = SectionPos.z(packedSection);

            boolean interior = true;
            for (int corner = 0; corner < 8 && interior; corner++) {
                double x = ((corner & 1) == 0 ? (sx << 4) : (sx << 4) + 16) - centre.x;
                double y = ((corner & 2) == 0 ? (sy << 4) : (sy << 4) + 16) - centre.y;
                double z = ((corner & 4) == 0 ? (sz << 4) : (sz << 4) + 16) - centre.z;
                if (x * x + y * y + z * z > radiusSq) {
                    interior = false;
                }
            }

            sections.add(new BlastJob.SectionWork(
                    new BlockPos(sx << 4, sy << 4, sz << 4),
                    entry.getValue().toArray(),
                    interior));
        }

        // Nearest first, so the hole grows outward. Avoids one giant packet burst at the end.
        sections.sort(Comparator.comparingDouble(work -> {
            BlockPos origin = work.sectionOrigin();
            double dx = (origin.getX() + 8) - centre.x;
            double dy = (origin.getY() + 8) - centre.y;
            double dz = (origin.getZ() + 8) - centre.z;
            return dx * dx + dy * dy + dz * dz;
        }));

        return sections;
    }

    /** Called once per server tick from the tick listener. */
    public void tick(MinecraftServer server) {
        if (queue.isEmpty()) {
            return;
        }

        // Head-of-line job only: keeps blasts in order and bounds memory.
        BlastJob job = queue.peekFirst();
        if (!job.isPlanned()) {
            return; // still being planned on a worker thread
        }
        if (job.hasFailed()) {
            // Planning failed (too big / exception): fall back to a vanilla explosion so the warhead
            // still does *something*, on the server thread where it belongs.
            ShadowClanGaming.LOGGER.warn("Blast planning failed at {} - using vanilla explosion", job.centre);
            job.level.explode(null, job.centre.x, job.centre.y, job.centre.z, job.power,
                    Level.ExplosionInteraction.TNT);
            queue.pollFirst();
            return;
        }

        List<BlastJob.SectionWork> sections = job.sections();
        int start = job.appliedSections();
        if (start >= sections.size()) {
            queue.pollFirst();
            return;
        }

        // Guarantee the blast finishes eventually, however slow the machine.
        int required = Math.max(1, (int) Math.ceil(sections.size() / (double) MAX_TICKS_PER_BLAST));

        // ...but the real governor is wall-clock time: keep the tick cheap.
        // This is the setting that protects FPS when the server runs on the player's own machine.
        long budgetNanos = ServerProfile.applyBudgetMillis() * 1_000_000L;
        long started = System.nanoTime();

        int applied = 0;
        for (int i = start; i < sections.size(); i++) {
            applySection(job.level, sections.get(i));
            applied++;

            boolean overBudget = (System.nanoTime() - started) > budgetNanos;
            if (applied >= required && overBudget) {
                break;
            }
            // Hard ceiling so a pathological case can't hog the whole tick.
            if (applied >= required * 8) {
                break;
            }
        }
        job.markApplied(applied);

        if (job.isFinished()) {
            queue.pollFirst();
        }
    }

    /** Applies one 16^3 section. Server thread only. */
    private void applySection(ServerLevel level, BlastJob.SectionWork work) {
        BlockState air = Blocks.AIR.defaultBlockState();

        if (work.fullyInterior()) {
            for (long packed : work.positions()) {
                level.setBlock(BlockPos.of(packed), air, Block.UPDATE_CLIENTS);
            }
        } else {
            for (long packed : work.positions()) {
                BlockPos pos = BlockPos.of(packed);
                if (level.getBlockState(pos).isAir()) {
                    continue; // most of a big shell is already air - skip the write
                }
                level.setBlock(pos, air, Block.UPDATE_CLIENTS);
            }
        }
    }

    public void shutdown() {
        planner.shutdownNow();
        queue.clear();
    }

    /** Growable primitive long array: avoids boxing millions of BlockPos objects while planning. */
    private static final class LongList {
        private long[] data = new long[64];
        private int size = 0;

        void add(long value) {
            if (size == data.length) {
                data = java.util.Arrays.copyOf(data, size * 2);
            }
            data[size++] = value;
        }

        long[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
