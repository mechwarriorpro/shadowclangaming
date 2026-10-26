package com.shadowclangaming.warhead.apex;

/**
 * The shape of an apex detonation over time, as pure numbers.
 *
 * One function decides where the destruction front is at any moment ({@link #frontRadius}), and both the
 * server (which blocks to remove) and the client (where to draw the ring) call it. That is deliberate: if
 * the client had its own copy of the curve, the ring and the crater would drift apart, and a shockwave
 * that visibly passes over intact ground is worse than no shockwave at all.
 *
 * An apex runs {@code chargeTicks} of visible wind-up, then {@code stages} destructive stages spaced
 * {@code intervalTicks} apart. Between stages the front does not jump: it sweeps, because a front that
 * teleports is a flash, not a wave.
 *
 * No Minecraft imports - the harness checks every curve in here.
 */
public final class ApexTimeline {

    /** Minimum radius, so maths on tiny inputs cannot divide by zero. */
    public static final double MIN_RADIUS = 1.0D;

    private ApexTimeline() {
    }

    /** Absolute tick at which {@code stage} (0-based) happens. Stage 0 is the end of the charge. */
    public static int stageTick(Apex apex, int stage) {
        int clamped = Math.max(0, Math.min(stage, apex.stages() - 1));
        return apex.chargeTicks() + clamped * apex.intervalTicks();
    }

    /** Tick at which the whole thing is over. */
    public static int endTick(Apex apex) {
        return stageTick(apex, apex.stages() - 1);
    }

    /**
     * How far out the destructive front is at absolute {@code tick}, in blocks.
     *
     * OUTWARD  starts at 0 and reaches {@code radius} at the last stage.
     * INWARD   starts at {@code radius} and shrinks to 0 at the last stage (a black hole closing), and the
     *          final stage then flips to the full radius - the release.
     * STATIC   is at {@code radius} the whole time: the marked volume is the weapon.
     */
    public static double frontRadius(Apex apex, int tick, int radius) {
        double r = Math.max(MIN_RADIUS, radius);
        switch (apex.front()) {
            case STATIC -> {
                return r;
            }
            case OUTWARD -> {
                return r * fraction(apex, tick);
            }
            case INWARD -> {
                double f = fraction(apex, tick);
                // Closes over the run, then the last stage is the release.
                if (tick >= endTick(apex)) {
                    return r;
                }
                return r * (1.0D - f);
            }
            default -> {
                return r;
            }
        }
    }

    /**
     * Progress through the destructive run: 0 at the end of the charge, 1 at the last stage.
     * Uses ticks, not stages, so a client rendering between stages sees the front move smoothly.
     */
    public static double fraction(Apex apex, int tick) {
        int start = apex.chargeTicks();
        int end = endTick(apex);
        if (tick <= start || end <= start) {
            return tick >= end ? 1.0D : 0.0D;
        }
        return Math.min(1.0D, (tick - start) / (double) (end - start));
    }

    /** 0..1 how bright the core is: it swells during the charge and flares on release. */
    public static double coreIntensity(Apex apex, int tick) {
        if (tick < 0) {
            return 0.0D;
        }
        int charge = apex.chargeTicks();
        if (charge > 0 && tick < charge) {
            return 0.25D + 0.6D * (tick / (double) charge);
        }
        int end = endTick(apex);
        if (tick > end) {
            // Afterglow falls away over one interval.
            double fade = 1.0D - ((tick - end) / (double) Math.max(1, apex.intervalTicks()));
            return clamp(fade);
        }
        double f = fraction(apex, tick);
        // Flare at the release point, dip in the middle of the sweep.
        return clamp(0.75D + 0.25D * Math.cos(f * Math.PI * 2.0D) * 0.5D + 0.25D * f);
    }

    /** Should this tick spawn the visual bursts (rings, cracks, tendrils)? Every other tick is enough. */
    public static boolean isFxTick(int tick) {
        return tick % 2 == 0;
    }

    /** How many shockwave rings an apex draws. Hammer and Omega get the full ladder; others get two. */
    public static int ringCount(Apex apex) {
        return switch (apex) {
            case SPACETIME_HAMMER -> 5;
            case OMEGA -> 4;
            case ABSOLUTE_ANNIHILATION -> 7;
            case WORLD_EATER, UNIVERSAL_IMPLODER -> 3;
            default -> 2;
        };
    }

    /** Ring {@code index} of {@code total}, as a 0..1 fraction of the radius, staggered for the effect. */
    public static double ringFraction(int index, int total) {
        if (total <= 1) {
            return 1.0D;
        }
        return (index + 1) * (1.0D / total);
    }

    /** Meteors for Ragnarok: enough to read as a bombardment, scaled by the radius. */
    public static int meteors(Apex apex, int radius) {
        if (apex.style() != Apex.Style.APOCALYPSE) {
            return 0;
        }
        return Math.max(8, Math.min(64, radius / 3));
    }

    /** Tendrils for the Abyssal Covenant: one per ~24 blocks of radius, hard-capped for sanity. */
    public static int tendrils(int radius) {
        return Math.max(12, Math.min(160, radius * 2));
    }

    /** Portals for the Abyssal Covenant. "Hundreds" - measured, not metaphorical. */
    public static int portals(int radius) {
        return Math.max(32, Math.min(240, radius * 3));
    }

    /** Rune strokes for the World Rune: one per 4 blocks of radius, so the glyph always fits. */
    public static int runeStrokes(int radius) {
        return Math.max(8, Math.min(96, radius / 4));
    }

    /** Length of the Quasar Lance's jet, in blocks: four radii, capped so it stays a carve not a wipe. */
    public static int lanceLength(int radius) {
        return Math.max(32, Math.min(512, radius * 4));
    }

    /** How many blocks the Quasar Lance jet is wide. */
    public static int lanceWidth(int radius) {
        return Math.max(3, Math.min(16, radius / 8));
    }

    /** Soul Furnace: souls consumed raise the final blast; this is how much each is worth. */
    public static float soulValue(int souls) {
        return Math.min(24.0F, souls * 0.6F);
    }

    private static double clamp(double value) {
        return Math.max(0.0D, Math.min(1.0D, value));
    }
}
