package com.shadowclangaming.client;

import com.shadowclangaming.config.SCGConfig;
import com.shadowclangaming.warhead.BlastMotif;
import com.shadowclangaming.warhead.BlastStyle;

import java.util.ArrayList;
import java.util.List;

/**
 * Detonations waiting to be drawn.
 *
 * Same shape as {@link ClientApexQueue}, for the same reason: the network thread appends, the render thread
 * reads, and the only shared state is a list of plain records. Lifetime is counted in TICKS REMAINING so a
 * lost packet can only ever make an effect too short, never leave the screen graded forever.
 *
 * BOUNDED. A carpet barrage of 4096 shots would otherwise queue 4096 full-screen effects. The queue keeps the
 * newest {@link #MAX_ENTRIES} and, because the renderer only ever draws the strongest one, dropping the oldest
 * costs nothing visually.
 */
public final class ClientBlastQueue {

    public static final int MAX_ENTRIES = 48;

    /** One live effect. */
    public static final class Entry {
        public final BlastStyle style;
        /**
         * Which warhead this is, not just which style. The style is nine palettes; the cinematic geometry
         * is per-warhead ({@code warhead/BlastMotif}), so the renderer needs the id to know what to draw.
         */
        public final String warheadId;
        public final double x;
        public final double y;
        public final double z;
        public final int radius;
        /** Ticks since this half of the effect began. */
        public int age;
        public final int lifetime;
        /**
         * Which half of the weapon's life this entry is: the anticipation playing out before the blast,
         * or the detonation itself. A charge is drawn from the motif's charge layers and brightens as it
         * goes; an impact flashes and decays.
         */
        public final BlastMotif.Phase phase;

        Entry(BlastStyle style, String warheadId, double x, double y, double z, int radius, int lifetime,
              BlastMotif.Phase phase) {
            this.style = style;
            this.warheadId = warheadId == null || warheadId.isBlank() ? style.id() : warheadId;
            this.x = x;
            this.y = y;
            this.z = z;
            this.radius = radius;
            this.lifetime = Math.max(1, lifetime);
            this.phase = phase == null ? BlastMotif.Phase.IMPACT : phase;
        }

        public boolean charging() {
            return phase == BlastMotif.Phase.CHARGE;
        }

        public boolean expired() {
            return age >= lifetime;
        }

        /** 0 at the flash, 1 when the effect is over. */
        public float progress() {
            return Math.min(1.0F, age / (float) lifetime);
        }

        /**
         * How hard this effect pushes right now: a fast attack (three ticks) and an exponential decay.
         *
         * The attack matters: a flash that ramps up over half its lifetime reads as a fade-in, which looks
         * like a mistake rather than an explosion.
         */
        public float intensity() {
            float t = progress();
            if (charging()) {
                // Anticipation: it builds. An exponential decay here would make a thirty-second charge
                // invisible for twenty-nine of its seconds, which is exactly the read that got the
                // previous version rejected.
                return Math.max(0.0F, Math.min(1.0F, 0.3F + 0.7F * t));
            }
            float attack = Math.min(1.0F, age / 3.0F);
            float decay = (float) Math.exp(-3.0D * t);
            return Math.max(0.0F, Math.min(1.0F, attack * decay));
        }
    }

    private static final List<Entry> PENDING = new ArrayList<>();

    private ClientBlastQueue() {
    }

    /** Called from the network handler. */
    public static void accept(String styleId, String warheadId, double x, double y, double z, int radius,
                              int ticks, BlastMotif.Phase phase) {
        if (!SCGConfig.shadersEnabled() && !SCGConfig.flashEnabled() && !SCGConfig.screenShake()) {
            // Everything visual is switched off: do not even grow the list.
            return;
        }
        PENDING.add(new Entry(BlastStyle.byId(styleId), warheadId, x, y, z, radius, ticks, phase));
        while (PENDING.size() > MAX_ENTRIES) {
            PENDING.remove(0);
        }
    }

    /** One client tick. */
    public static void tick() {
        if (PENDING.isEmpty()) {
            return;
        }
        for (Entry entry : PENDING) {
            entry.age++;
        }
        PENDING.removeIf(Entry::expired);
    }

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
