package com.shadowclangaming.launcher;

/**
 * What a missile module DOES, as opposed to how far it goes.
 *
 * {@link ModuleTier} answers "how far can this airframe fly". A {@code ModuleKind} answers "what is this
 * airframe for" - and the two are orthogonal, because a homing frame is not a longer or shorter missile,
 * it is a different tool. Keeping the kind as data (this enum) rather than as behaviour spread through
 * three classes means the flight code asks one object one question, and the numbers stay in one file.
 *
 * WHY THIS CLASS HAS NO MINECRAFT IMPORTS. The tuning is data: multipliers, radii, sub-munition counts.
 * The harness proves the balance relationships (a high-velocity frame really is faster than a standard
 * one, a cluster really splits, a transport frame really kills the warhead) without a world.
 *
 * Every kind exists at all five tiers (T1 10,000 blocks ... T5 unlimited), because "what it does" and
 * "how far it goes" are independent decisions: a T5 homing frame is a strategic weapon that follows you,
 * a T1 homing frame is a short-range one that does the same thing badly. Twenty-five modules in total.
 *
 * The five frames:
 * <ul>
 *   <li><b>STANDARD</b> - the plain airframe. T1 through T5 are all this.</li>
 *   <li><b>HOMING</b> - acquires and follows a moving target instead of flying to a remembered block.</li>
 *   <li><b>CLUSTER</b> - breaks into sub-munitions before impact, so one silo covers an area.</li>
 *   <li><b>HIGH_VELOCITY</b> - no subtlety: 2.4x the speed, so point defence has less time to answer.</li>
 *   <li><b>TRANSPORT</b> - delivers crew, not a warhead. No blast, and nobody takes fall damage.</li>
 * </ul>
 */
public enum ModuleKind {

    /** The default frame: fly to the target point and detonate. */
    STANDARD("standard", 1.0D, false, 0, false),

    /**
     * Seeker frame.
     *
     * Re-aims at a living target while in flight. Without a Tracker lock it acquires for itself: the
     * nearest hostile within {@link #HOMING_RANGE}, re-checked every {@link #HOMING_RETARGET} ticks.
     */
    HOMING("homing", 1.05D, true, 0, false),

    /**
     * Area frame.
     *
     * Splits into {@link #SUBMUNITIONS} smaller missiles at {@link #CLUSTER_TRIGGER} blocks out, each aimed
     * at its own point on a ring of {@link #CLUSTER_SPREAD} blocks around the target. One warhead becomes
     * a pattern.
     */
    CLUSTER("cluster", 1.0D, false, 6, false),

    /**
     * Sprint frame.
     *
     * {@link #HIGH_VELOCITY_MULTIPLIER}x the cruise and terminal speed. Costs nothing but range-per-fuel;
     * the point is that the other side has less warning, which is what a first strike is for.
     */
    HIGH_VELOCITY("high_velocity", 2.4D, false, 0, false),

    /**
     * Personnel frame.
     *
     * A missile you can ride down. It does not detonate: on impact it puts its passengers on the ground
     * with their fall distance cleared, drops whatever was in the hold, and goes quiet.
     */
    TRANSPORT("transport", 1.25D, false, 0, true);

    /** Extra speed for a high-velocity frame, applied to both cruise and terminal speed. */
    public static final double HIGH_VELOCITY_MULTIPLIER = 2.4D;

    /** How far a homing frame will go looking for something to chase, in blocks. */
    public static final double HOMING_RANGE = 96.0D;

    /** Ticks between homing re-acquisitions. Ten is a smooth enough pursuit at cruise speed. */
    public static final int HOMING_RETARGET = 10;

    /** Blocks between sub-munition target points, so a cluster leaves overlapping craters. */
    public static final double CLUSTER_SPREAD = 14.0D;

    /** How close to the target a cluster frame breaks up. */
    public static final double CLUSTER_TRIGGER = 56.0D;

    /** Fraction of the parent's power each sub-munition carries. Sums to {@link #CLUSTER_POWER_SCALE}. */
    public static final double CLUSTER_POWER_SCALE = 0.6D;

    /** Fall-damage grace, in ticks, applied to anything a transport frame sets down. */
    public static final int TRANSPORT_GRACE = 100;

    /**
     * The one warhead id that is frame-sensitive.
     *
     * A Cargo warhead delivers the missile's hold to wherever it lands. On a CLUSTER frame that means the
     * hold would be delivered several times over - six sub-munitions, six copies of the same crate - which
     * is item duplication with extra steps. So cargo is legal on the frames that arrive ONCE (standard,
     * homing, high velocity) and refused on the frame that multiplies. Compared by id rather than by the
     * enum because {@code WarheadType} needs a live server to detonate and this class must stay testable
     * without one.
     */
    public static final String CARGO_WARHEAD_ID = "cargo";

    private final String id;
    private final double speedMultiplier;
    private final boolean homing;
    private final int subMunitions;
    private final boolean transport;

    ModuleKind(String id, double speedMultiplier, boolean homing, int subMunitions, boolean transport) {
        this.id = id;
        this.speedMultiplier = speedMultiplier;
        this.homing = homing;
        this.subMunitions = subMunitions;
        this.transport = transport;
    }

    /** Stable id for NBT and for the item's translation key. */
    public String id() {
        return id;
    }

    public String translationKey() {
        return "module.shadowclangaming." + id;
    }

    /** Multiplier applied to the frame's cruise speed. */
    public double speedMultiplier() {
        return speedMultiplier;
    }

    /** Does this frame chase a moving target? */
    public boolean homing() {
        return homing;
    }

    /** How many sub-munitions a cluster frame splits into (0 when it is not a cluster frame). */
    public int subMunitions() {
        return subMunitions;
    }

    /**
     * Does this frame deliver people instead of a warhead?
     *
     * Transport frames ignore the warhead slot entirely - fitting one is refused rather than silently
     * wasted, because a player who fits an antimatter warhead and then boards has misunderstood something
     * and should be told so at the bench, not at the impact.
     */
    public boolean transport() {
        return transport;
    }

    /**
     * May this frame carry that warhead?
     *
     * <ul>
     *   <li><b>Transport</b> carries none: it is a personnel frame, and the warhead slot is sealed.</li>
     *   <li><b>Cargo</b> is limited to the frames that deliver exactly once (standard, homing, high
     *       velocity). Cluster is refused because splitting would duplicate the hold.</li>
     *   <li>Everything else fits everywhere.</li>
     * </ul>
     */
    public boolean allowsWarhead(String warheadId) {
        if (transport) {
            return false;
        }
        if (CARGO_WARHEAD_ID.equals(warheadId)) {
            return this == STANDARD || this == HOMING || this == HIGH_VELOCITY;
        }
        return true;
    }

    /** Convenience: does this frame deliver cargo at all? */
    public boolean carriesCargo() {
        return allowsWarhead(CARGO_WARHEAD_ID);
    }

    /** True when this frame does something other than fly to a point. */
    public boolean special() {
        return this != STANDARD;
    }

    /** Speed to fly at, given the frame's nominal cruise speed. */
    public double speedFor(double cruise) {
        return Math.max(0.35D, cruise * speedMultiplier);
    }

    /** Power for one sub-munition, given the parent's power. */
    public float subMunitionPower(float parentPower) {
        if (subMunitions <= 0) {
            return parentPower;
        }
        return Math.max(1.0F, parentPower * (float) CLUSTER_POWER_SCALE / subMunitions);
    }

    /** Where sub-munition {@code index} should aim, as an offset from the parent's target. */
    public double[] subMunitionOffset(int index, int total) {
        double angle = (Math.PI * 2.0D) * index / Math.max(1, total);
        double radius = CLUSTER_SPREAD * (index % 2 == 0 ? 1.0D : 0.6D);
        return new double[]{Math.cos(angle) * radius, Math.sin(angle) * radius};
    }

    public static ModuleKind byId(String id) {
        if (id == null) {
            return STANDARD;
        }
        for (ModuleKind kind : values()) {
            if (kind.id.equals(id)) {
                return kind;
            }
        }
        return STANDARD;
    }
}
