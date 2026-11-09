package com.shadowclangaming.warhead;

import java.util.Random;

/**
 * The numbers behind the kamikaze vest and the hand launcher.
 *
 * Both are "one person, one missile" weapons, so they share a file: what a hand launcher may fire, how far
 * a dying vest hurls things, and how far out a dead man's missiles are aimed. Keeping it as data with no
 * Minecraft imports means the harness can prove the awkward parts - the tier gate, the scatter being
 * actually scattered, the salvo cap - without a world.
 */
public final class KamikazePlan {

    /** A hand launcher fires T1 and T2. Anything longer is a silo weapon. */
    public static final int HAND_LAUNCHER_MAX_TIER = 2;

    /** How far the hand launcher will look for something to hit, in blocks. */
    public static final double HAND_LAUNCHER_RANGE = 256.0D;

    /** Ticks between shots. A shoulder tube is not a machine gun. */
    public static final int HAND_LAUNCHER_COOLDOWN = 30;

    /** Blast power of the vest itself, on the warhead scale (an HE warhead is ~8-16). */
    public static final float VEST_POWER = 30.0F;

    /**
     * How many missiles a dying vest actually launches.
     *
     * Uncapped would be "however many the player could carry", which is a mob-farm sized lag bomb. Twelve
     * is enough that a prepared bomber is terrifying and few enough that the server survives it.
     */
    public static final int MAX_SALVO = 12;

    /** Distance range a death-launched missile is aimed at. */
    public static final double SALVO_MIN_RANGE = 48.0D;
    public static final double SALVO_MAX_RANGE = 176.0D;

    /** The vest clears this radius, in blocks, to make room before the missiles go. */
    public static final double VEST_CLEARING = 6.0D;

    /** Ticks of fuse on a piece of TNT thrown clear by a dying vest. */
    public static final int TNT_FUSE_MIN = 30;
    public static final int TNT_FUSE_MAX = 70;

    /** Speed an explosive is thrown away at, blocks/tick. */
    public static final double THROW_SPEED = 0.9D;

    private KamikazePlan() {
    }

    /** May a hand launcher fire a missile of this tier? */
    public static boolean handLaunchable(int tier) {
        return tier >= 1 && tier <= HAND_LAUNCHER_MAX_TIER;
    }

    /**
     * Where a death-launched missile is aimed: a random bearing, a random distance, a random pitch.
     *
     * Deliberately NOT centred on anything. A kamikaze vest is not a targeting system, it is a tantrum:
     * the missiles go everywhere, some harmlessly into the sky, some into whatever is nearest. That is the
     * requested behaviour and it is also the funny one.
     *
     * @return {x, y, z} of the aim point
     */
    public static double[] salvoTarget(double x, double y, double z, Random random) {
        double bearing = random.nextDouble() * Math.PI * 2.0D;
        double distance = SALVO_MIN_RANGE + random.nextDouble() * (SALVO_MAX_RANGE - SALVO_MIN_RANGE);
        // Mostly flat, occasionally thrown high or slammed low.
        double pitch = (random.nextDouble() - 0.45D) * 0.6D;
        double flat = Math.cos(pitch) * distance;
        return new double[]{
                x + Math.cos(bearing) * flat,
                y + Math.sin(pitch) * distance,
                z + Math.sin(bearing) * flat};
    }

    /** Velocity an explosive is thrown at when the vest goes off: outward, with a kick upward. */
    public static double[] throwVelocity(double dirX, double dirZ, Random random) {
        double speed = THROW_SPEED * (0.6D + random.nextDouble() * 0.8D);
        return new double[]{dirX * speed, 0.35D + random.nextDouble() * 0.45D, dirZ * speed};
    }

    /** Fuse length for a thrown piece of TNT. */
    public static int tntFuse(Random random) {
        return TNT_FUSE_MIN + random.nextInt(TNT_FUSE_MAX - TNT_FUSE_MIN + 1);
    }

    /**
     * How many missiles out of a carried pile actually launch.
     *
     * @param carried how many the player is holding
     */
    public static int salvoSize(int carried) {
        return Math.max(0, Math.min(carried, MAX_SALVO));
    }
}
