package com.shadowclangaming.client;

import com.shadowclangaming.warhead.apex.Apex;

import java.util.ArrayList;
import java.util.List;

/**
 * Where an incoming apex detonation waits until the renderer can draw it.
 *
 * Same split as {@link ClientConsoleQueue}: this half is touched by the network handler and imports
 * nothing client-only, so a dedicated server can load {@code network/ScgNet} without dragging in the
 * renderer. {@link ApexVisuals} drains it.
 *
 * LIFETIME IS COUNTED IN TICKS REMAINING, not in absolute game time, because the packet arrives on the
 * network thread where reading the level would be a race. The renderer ticks the queue once per client
 * tick and retires anything that runs out - so a weapon whose cancel packet is lost still stops.
 */
public final class ClientApexQueue {

    /** One running effect, and how long it has left. */
    public static final class Entry {
        public final Apex apex;
        public final double x;
        public final double y;
        public final double z;
        /**
         * Current radius in blocks. Mutable because one weapon changes size while it runs: the Soul
         * Furnace grows every time it eats, and re-announcing it is cheaper - and smoother - than ending
         * one effect and starting another.
         */
        public int radius;
        /** Ticks since the detonation began. */
        public int age;
        /**
         * The furthest through its life this effect has been seen. Re-announcing a weapon with a longer
         * lifetime (a Soul Furnace that just ate) would otherwise pull its progress backwards, and a
         * sphere that shrinks when it grows is exactly the wrong read.
         */
        public float visualProgress;
        /** How long the whole thing lasts; the client reads its own timeline against this. */
        public int lifetime;

        Entry(Apex apex, double x, double y, double z, int radius, int lifetime) {
            this.apex = apex;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.lifetime = lifetime;
        }

        public boolean expired() {
            return age >= lifetime;
        }
    }

    private static final List<Entry> PENDING = new ArrayList<>();

    private ClientApexQueue() {
    }

    /** @param ticks lifetime in ticks, or -1 to cancel the effects of this apex at this position */
    public static void accept(String apexId, double x, double y, double z, int radius, int ticks) {
        Apex apex = Apex.byId(apexId);
        if (apex == null) {
            return;
        }
        if (ticks < 0) {
            PENDING.removeIf(entry -> entry.apex == apex
                    && Math.abs(entry.x - x) < 1.0D
                    && Math.abs(entry.z - z) < 1.0D);
            return;
        }
        // A re-announcement of a weapon that is already running (the Soul Furnace growing) updates the
        // existing entry rather than stacking a second effect on the same spot: same size, longer life,
        // no visible restart.
        for (Entry entry : PENDING) {
            if (entry.apex == apex
                    && Math.abs(entry.x - x) < 1.0D
                    && Math.abs(entry.z - z) < 1.0D) {
                entry.radius = Math.max(entry.radius, Math.max(1, radius));
                entry.lifetime = Math.max(entry.lifetime, entry.age + Math.max(1, ticks));
                return;
            }
        }
        PENDING.add(new Entry(apex, x, y, z, radius, Math.max(1, ticks)));
    }

    /** Advances every effect by one tick and drops the finished ones. Called once per client tick. */
    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Entry entry : PENDING) {
            entry.age++;
        }
        PENDING.removeIf(Entry::expired);
    }

    /** Live entries, for the renderer. */
    public static List<Entry> active() {
        return List.copyOf(PENDING);
    }

    public static int size() {
        return PENDING.size();
    }

    public static void clear() {
        PENDING.clear();
    }
}
