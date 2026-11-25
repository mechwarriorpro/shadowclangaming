package com.shadowclangaming.cannon;

/**
 * The aiming solution for a big gun, in plain trigonometry.
 *
 * A cannon does not point at a target, it points above one. At 6 blocks a tick and a hundred blocks of
 * range a shell is in the air for about seventeen ticks, and gravity pulls it down roughly seven blocks
 * in that time - so the barrel has to be lifted, and the amount it has to be lifted is the whole of this
 * class.
 *
 * The model is the same first-order one the ballistic calculator uses, deliberately: a player who has
 * watched a shell land short once should be able to work out why, and the number the computer prints
 * should match the number a person would scribble on paper.
 *
 *   time    t = horizontal / velocity            (blocks, blocks per tick)
 *   drop    d = 0.5 * gravity * t²               (blocks)
 *   pitch   θ = atan2(height + drop, horizontal)
 *
 * Everything is pure Java so the harness can assert it; there is no Level anywhere near it.
 */
public final class CannonMath {

    /** Blocks per tick down. Vanilla arrows are 0.05; a heavy shell is a little lazier. */
    public static final double GRAVITY = 0.05D;
    /** A Create Big Cannons propellant charge throws a shell at roughly this speed. */
    public static final double DEFAULT_VELOCITY = 6.0D;
    public static final double MIN_VELOCITY = 1.0D;
    public static final double MAX_VELOCITY = 32.0D;
    /** What CBC will actually allow the barrel to do. Clamped here so the aim is never refused. */
    public static final float MAX_ELEVATION = 45.0F;
    public static final float MAX_DEPRESSION = -10.0F;
    public static final double MAX_RANGE = 2048.0D;

    private CannonMath() {
    }

    /** Where to point the barrel, and why. */
    public record Aim(float yaw, float pitch, double distance, double flightTicks, double drop) {
        public String describe() {
            return String.format(java.util.Locale.ROOT,
                    "yaw %.1f pitch %.1f (range %.0f, %.1f ticks, drop %.1f)",
                    yaw, pitch, distance, flightTicks, drop);
        }
    }

    /**
     * Minecraft yaw convention, which is also CBC's: 0 = south (+Z), 90 = west (-X), 180 = north (-Z),
     * 270 = east (+X). The sign on dx is the thing people get wrong.
     */
    public static double yawDegrees(double dx, double dz) {
        double degrees = Math.toDegrees(Math.atan2(-dx, dz));
        return ((degrees % 360.0D) + 360.0D) % 360.0D;
    }

    /** How far the shell falls in the time it takes to cross {@code horizontal}. */
    public static double drop(double horizontal, double velocity, double gravity) {
        if (velocity <= 0.0D) {
            return 0.0D;
        }
        double ticks = horizontal / velocity;
        return 0.5D * gravity * ticks * ticks;
    }

    public static double pitchDegrees(double horizontal, double height, double velocity) {
        double drop = drop(horizontal, velocity, GRAVITY);
        return Math.toDegrees(Math.atan2(height + drop, Math.max(0.0001D, horizontal)));
    }

    public static double pitchDegrees(double horizontal, double height, double velocity, double gravity) {
        double drop = drop(horizontal, velocity, gravity);
        return Math.toDegrees(Math.atan2(height + drop, Math.max(0.0001D, horizontal)));
    }

    /** Clamp into the barrel's real travel. */
    public static float clampPitch(double pitch) {
        return (float) Math.max(MAX_DEPRESSION, Math.min(MAX_ELEVATION, pitch));
    }

    /**
     * Full solution from an origin to a target.
     *
     * @param velocity blocks per tick, from the propellant; see {@link #DEFAULT_VELOCITY}
     */
    public static Aim solve(double dx, double dy, double dz, double velocity) {
        double v = Math.max(MIN_VELOCITY, Math.min(MAX_VELOCITY, velocity));
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double drop = drop(horizontal, v, GRAVITY);
        double pitch = pitchDegrees(horizontal, dy, v);
        return new Aim((float) yawDegrees(dx, dz), clampPitch(pitch), horizontal, horizontal / v, drop);
    }

    /** How long a shell takes to arrive, in ticks. Used to lead a moving target. */
    public static double flightTicks(double horizontal, double velocity) {
        return horizontal / Math.max(MIN_VELOCITY, Math.min(MAX_VELOCITY, velocity));
    }

    /** Lead a contact that is moving: where it will be when the shell gets there. */
    public static double[] lead(double x, double y, double z, double vx, double vy, double vz,
                                double fromX, double fromY, double fromZ, double velocity) {
        double horizontal = Math.sqrt((x - fromX) * (x - fromX) + (z - fromZ) * (z - fromZ));
        double ticks = flightTicks(horizontal, velocity);
        return new double[]{x + vx * ticks, y + vy * ticks, z + vz * ticks};
    }

    public static double clampVelocity(double velocity) {
        return Math.max(MIN_VELOCITY, Math.min(MAX_VELOCITY, velocity));
    }
}
