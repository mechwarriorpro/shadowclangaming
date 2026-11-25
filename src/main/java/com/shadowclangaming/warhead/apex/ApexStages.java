package com.shadowclangaming.warhead.apex;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The clock behind every apex warhead.
 *
 * A staged weapon cannot be a single method call: "the front closes over four seconds and then lets go"
 * needs something to come back four seconds later. Sleeping on the server thread is not an option (it
 * stalls the whole tick), so each detonation books a row here and the row is ticked once per server tick.
 *
 * Deliberately NOT saved data. A pending detonation is a few seconds of animation; if the server stops in
 * the middle of one, the honest outcome is that it stops. Persisting it would mean a warhead resuming
 * after a restart, mid-front, on a chunk that may have been rebuilt - that is worse than losing the tail
 * of an explosion.
 *
 * A global cap keeps a griefer with a chest of missiles from queueing a thousand staged weapons: past
 * {@link #MAX_PENDING} the oldest is finished off immediately rather than queued forever.
 */
public final class ApexStages {

    /** Most detonations running at once across the whole server. */
    public static final int MAX_PENDING = 64;

    private static final List<Pending> PENDING = new ArrayList<>();

    private ApexStages() {
    }

    /** One running detonation. */
    private static final class Pending {
        final ServerLevel level;
        final Vec3 pos;
        final Apex apex;
        final int radius;
        final long seed;
        int stage;
        int age;

        Pending(ServerLevel level, Vec3 pos, Apex apex, int radius, long seed) {
            this.level = level;
            this.pos = pos;
            this.apex = apex;
            this.radius = radius;
            this.seed = seed;
        }
    }

    /**
     * Book a detonation and run its first stage once the charge has elapsed.
     *
     * @return false if the queue was full (the caller has already run stage 0 in that case)
     */
    public static boolean schedule(ServerLevel level, Vec3 pos, Apex apex, int radius, long seed) {
        if (PENDING.size() >= MAX_PENDING) {
            // Finish the oldest now instead of dropping it: a weapon that silently does nothing is worse
            // than a weapon that goes off early.
            Pending oldest = PENDING.remove(0);
            ApexWarheads.finish(oldest.level, oldest.pos, oldest.apex, oldest.radius, oldest.seed);
            return false;
        }
        PENDING.add(new Pending(level, pos, apex, radius, seed));
        return true;
    }

    /** Live count, for the perf readout. */
    public static int pending() {
        return PENDING.size();
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.level.getServer() == null || p.level.getServer().isStopped()) {
                it.remove();
                continue;
            }
            p.age++;
            int due = ApexTimeline.stageTick(p.apex, p.stage);
            while (p.stage < p.apex.stages() && p.age >= due) {
                ApexWarheads.stage(p.level, p.pos, p.apex, p.radius, p.stage, p.seed);
                p.stage++;
                due = ApexTimeline.stageTick(p.apex, p.stage);
            }
            if (p.stage >= p.apex.stages()) {
                ApexWarheads.finish(p.level, p.pos, p.apex, p.radius, p.seed);
                it.remove();
            }
        }
    }

    public static void clear() {
        PENDING.clear();
    }
}
