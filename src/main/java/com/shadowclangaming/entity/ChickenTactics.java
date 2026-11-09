package com.shadowclangaming.entity;

/**
 * How a turret chicken chooses to behave, as numbers.
 *
 * The AI itself lives in {@link TurretChickenEntity}; this is the decision table it reads. Pulling it out
 * has two payoffs: the tuning is in one place instead of smeared across four goal classes, and the rules
 * can be asserted in the harness without a world (a chicken must never chase from further than it can
 * see, must stop before it is standing on its target, and must keep staring at something it cannot reach).
 *
 * THE SHAPE OF THE FIGHT
 *   stare   - always, at whatever it has decided is its enemy
 *   shoot   - inside {@link #FIRE_RANGE}
 *   chase   - between {@link #KEEP_DISTANCE} and {@link #SIGHT_RANGE}, so it closes but never crowds
 *   hold    - a target is kept for {@link #TARGET_MEMORY} ticks after it was last seen, so a chicken that
 *             loses sight behind a wall does not instantly forget and wander off
 */
public final class ChickenTactics {

    /** How far it will shoot. Matches {@code TurretChickenEntity.RANGE}. */
    public static final double FIRE_RANGE = 16.0D;

    /**
     * How far it notices an enemy.
     *
     * Half again the firing range on purpose: a chicken that only reacted inside 16 blocks would be
     * outrun. This is the range at which it starts walking.
     */
    public static final double SIGHT_RANGE = 24.0D;

    /**
     * The distance it tries to hold once it has closed.
     *
     * Chickens are small and slow; standing on top of a player is a good way to be killed by the swing
     * they were making anyway. Five blocks is inside its own firing range and outside a sword's reach.
     */
    public static final double KEEP_DISTANCE = 5.0D;

    /** How fast it walks when chasing, as a path-speed multiplier. A chicken walks at 0.25. */
    public static final double CHASE_SPEED = 1.15D;

    /** Ticks a target stays remembered once acquired (5 s). */
    public static final int TARGET_MEMORY = 100;

    /** Ticks between path recalculations while chasing. Re-pathing every tick is how mobs get stuck. */
    public static final int REPATH_INTERVAL = 15;

    /** Ticks between target scans. */
    public static final int SCAN_INTERVAL = 10;

    /** How far the head may turn per tick while staring, in degrees. Swift, but not a snap. */
    public static final float STARE_TURN_SPEED = 35.0F;

    private ChickenTactics() {
    }

    /** Close the distance? */
    public static boolean shouldChase(double distance) {
        return distance > KEEP_DISTANCE && distance <= SIGHT_RANGE;
    }

    /** Fire? Slightly inside the sight range so a chicken that just noticed you has to step in first. */
    public static boolean shouldShoot(double distance) {
        return distance <= FIRE_RANGE;
    }

    /**
     * Hold still and stare?
     *
     * Anything it can see, at any range it can see it. A chicken staring at you from the far edge of its
     * sight range before it starts walking is the whole "stare down" behaviour: the threat arrives before
     * the bird does.
     */
    public static boolean shouldStare(double distance, boolean hasTarget) {
        return hasTarget && distance <= SIGHT_RANGE * 1.5D;
    }

    /** Is a remembered target still worth keeping, given the tick it was last seen? */
    public static boolean rememberTarget(int now, int lastSeen) {
        return now - lastSeen <= TARGET_MEMORY;
    }
}
