package com.shadowclangaming.warhead;

/**
 * The three ways a warhead can be delivered without a missile.
 *
 * A warhead used to exist only as the payload of an airframe. That is a fine strategic weapon and a poor
 * infantry one: you cannot throw a silo at a pillbox. So every warhead also exists as
 *
 *   BLOCK     - a block you place and ignite. Fuses, chains, and can be wired to redstone.
 *   GRENADE   - something you throw. Fuse on impact, small radius.
 *   MINECART  - a minecart that detonates on impact or on a redstone signal. Rails are the delivery
 *               system: roll it into a base and let it cook.
 *
 * The three share one rule set (this class) and differ in radius, fuse and trigger, so the items stay
 * thin and the balance numbers live in one place. No Minecraft imports: the harness proves the scaling and
 * the id scheme without a world.
 */
public final class ExplosivePackaging {

    /** Delivery form of a warhead. */
    public enum Form {
        /**
         * A placed charge. Its fuse IS the warhead's own effect length - see {@link #fuseTicks}. The
         * number here is only the floor for a warhead whose effect is shorter than a charge deserves.
         */
        BLOCK("explosive_", 1.0D, 80, false, 1.00D, 40, 900),
        /** A thrown warhead: a third of the effect length, so you can throw it and still get clear. */
        GRENADE("grenade_", 0.35D, 40, false, 0.35D, 15, 320),
        /** A rail-delivered warhead: impact lights it, then it cooks for half the effect length. */
        MINECART("minecart_", 1.15D, 0, true, 0.50D, 20, 450);

        private final String prefix;
        private final double radiusScale;
        private final int fuse;
        private final boolean impact;
        private final double fuseShare;
        private final int minFuse;
        private final int maxFuse;

        Form(String prefix, double radiusScale, int fuse, boolean impact,
             double fuseShare, int minFuse, int maxFuse) {
            this.prefix = prefix;
            this.radiusScale = radiusScale;
            this.fuse = fuse;
            this.impact = impact;
            this.fuseShare = fuseShare;
            this.minFuse = minFuse;
            this.maxFuse = maxFuse;
        }

        public String prefix() {
            return prefix;
        }

        /** Multiplier applied to the warhead's own radius. */
        public double radiusScale() {
            return radiusScale;
        }

        /**
         * Legacy fixed fuse in ticks, kept as this form's floor. Nothing schedules this number any more:
         * every fuse is {@link ExplosivePackaging#fuseTicks}, which is per-warhead.
         */
        public int fuse() {
            return fuse;
        }

        /** Lights on impact rather than on placement? The detonation is still timer-driven. */
        public boolean impact() {
            return impact;
        }

        /** Fraction of the warhead's effect length this form burns for. */
        public double fuseShare() {
            return fuseShare;
        }

        public int minFuse() {
            return minFuse;
        }

        public int maxFuse() {
            return maxFuse;
        }
    }

    /**
     * Which warheads get packaged forms. All of them, including cargo, spell and constructor - the ones
     * that need a payload simply arrive with an empty one.
     */
    public static boolean packable(String warheadId) {
        // DUD is included on purpose: a "dud charge" that goes off and does nothing is a legitimate item
        // (a training prop, a prop for a raid, a cheap way to test a launcher), and "every warhead has all
        // three forms" is a much easier rule to remember than "every warhead except one".
        return warheadId != null && !warheadId.isBlank();
    }

    /** {@code explosive_he}, {@code grenade_he}, {@code minecart_he}. */
    public static String id(Form form, String warheadId) {
        return form.prefix() + warheadId;
    }

    /**
     * Radius for a packaged warhead.
     *
     * A warhead's own number is not a radius - {@link WarheadType} carries a power, and the warhead turns
     * that into a radius itself. So packaging scales the POWER it is handed, which every warhead already
     * understands. A grenade is a third of a block's power; a minecart carries slightly more than a
     * stationary charge because it arrives with momentum.
     */
    public static float power(float warheadPower, Form form) {
        return Math.max(0.5F, (float) (warheadPower * form.radiusScale()));
    }

    /**
     * How long this warhead burns for in this delivery form, in ticks.
     *
     * <p>THE FUSE IS THE EFFECT. A charge used to burn for 80 ticks whatever it was carrying, a grenade for
     * 40, and a minecart for none at all - so a Big Bang Seed charge and a dud charge were indistinguishable
     * until the moment they went off. Now the fuse is derived from the warhead's own cinematic duration
     * ({@link BlastMotif#durationTicks}) at the power this form actually delivers: the effect and the fuse
     * come from one number, so a warhead with a long, slow, cinematic build genuinely gives you time to
     * watch it, and a fast one does not make you wait.
     *
     * <p>Each form takes a share of that duration - a block the whole thing, a grenade a third, a minecart
     * half - clamped to its own floor and ceiling so no warhead can produce an unusable charge.
     *
     * @param warheadId    the warhead's id, e.g. {@code he} or {@code big_bang_seed}
     * @param warheadPower the warhead's own base power, before the form's scale is applied
     */
    public static int fuseTicks(String warheadId, float warheadPower, Form form) {
        float effective = power(warheadPower, form);
        int effect = BlastMotif.durationTicks(warheadId, effective);
        int fuse = (int) Math.round(effect * form.fuseShare());
        return Math.max(form.minFuse(), Math.min(form.maxFuse(), fuse));
    }

    /** The same fuse, in seconds, for tooltips and read-outs. */
    public static double fuseSeconds(String warheadId, float warheadPower, Form form) {
        return fuseTicks(warheadId, warheadPower, form) / 20.0D;
    }

    private ExplosivePackaging() {
    }
}
