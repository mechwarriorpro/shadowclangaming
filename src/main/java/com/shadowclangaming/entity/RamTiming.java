package com.shadowclangaming.entity;

/**
 * Beef Supreme's ram, as arithmetic.
 *
 * "It deals 30 damage if it hits you with a ram attack, however before it rams it will slow down for 5
 * seconds and have dirt and flame particles under its feet."
 *
 * The entity drives the states; this class owns the numbers, which is what lets the logic harness check
 * that the telegraph is really five seconds, that the charge really is a dash rather than a walk, and
 * that a charge cannot connect twice.
 */
public final class RamTiming {

    /** Idle: walking about, being a cow. */
    public static final int IDLE = 0;
    /** Telegraphed: slowed down, particles on the ground. */
    public static final int WINDUP = 1;
    /** Barking: charging in a straight line. */
    public static final int CHARGE = 2;

    public static final int WINDUP_TICKS = 100;
    public static final int CHARGE_TICKS = 30;
    public static final int RAM_COOLDOWN = 80;
    public static final double CHARGE_SPEED = 1.15D;
    public static final double WINDUP_SLOWDOWN = 0.02D;
    public static final double RAM_RANGE = 18.0D;
    public static final double HIT_RANGE = 2.2D;
    public static final float RAM_DAMAGE = 30.0F;

    // ------------------------------------------------------------------ step-up
    //
    // "Needs step up when charging, max step height of 2.5 blocks." A charge that dies against a
    // one-block lip is a boss you beat with a fence, so the ram climbs. 2.5 is above a full block and
    // below a two-block wall, which is the line the player asked for.

    /** The highest lip a charging Beef Supreme will climb, in blocks. */
    public static final double MAX_STEP_HEIGHT = 2.5D;

    /** Will it climb a lip this tall while charging? */
    public static boolean canStepUp(double height) {
        return height > 0.0D && height <= MAX_STEP_HEIGHT;
    }

    // ------------------------------------------------------------------ the ending
    //
    // "Upon death it explodes leaving a few stacks of steak behind."

    /** How many stacks of steak it leaves. */
    public static final int STEAK_STACKS = 3;
    /** Items per stack. */
    public static final int STEAK_PER_STACK = 64;
    /** Blast power of the death explosion. Big enough to matter, not enough to level a base. */
    public static final float DEATH_EXPLOSION_POWER = 6.0F;

    /** Total steak dropped on death. */
    public static int steakTotal() {
        return STEAK_STACKS * STEAK_PER_STACK;
    }

    private RamTiming() {
    }

    /** Is a target close enough to start a ram? */
    public static boolean inRange(double distanceSqr) {
        return distanceSqr <= RAM_RANGE * RAM_RANGE;
    }

    /** Can a new ram start, given the current state and cooldown? */
    public static boolean canStart(int state, int cooldown, boolean hasTarget, double distanceSqr) {
        return state == IDLE && cooldown <= 0 && hasTarget && inRange(distanceSqr);
    }

    /**
     * The state after {@code ticks} in the current one.
     *
     * @param hit true when the charge connected, which ends it immediately
     */
    public static int nextState(int state, int ticks, boolean hit, boolean blocked) {
        return switch (state) {
            case WINDUP -> ticks >= WINDUP_TICKS ? CHARGE : WINDUP;
            case CHARGE -> hit || blocked || ticks >= CHARGE_TICKS ? IDLE : CHARGE;
            default -> IDLE;
        };
    }

    /** Movement speed multiplier for the current state. The wind-up is the "slowing down". */
    public static double speedMultiplier(int state) {
        return state == WINDUP ? WINDUP_SLOWDOWN : 1.0D;
    }

    /** Seconds of telegraph, for the docs and for the boss bar tooltip. */
    public static double windupSeconds() {
        return WINDUP_TICKS / 20.0D;
    }

    /** Damage per ram at a given difficulty-ish scale; 1.0 is "as designed". */
    public static float ramDamage(double scale) {
        return (float) (RAM_DAMAGE * scale);
    }
}
