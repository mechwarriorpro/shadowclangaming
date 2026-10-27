package com.shadowclangaming.warhead.apex;

/**
 * The Soul Furnace's own clock, as pure numbers.
 *
 * <p>WHAT MAKES IT DIFFERENT FROM EVERY OTHER APEX WEAPON. The other sixteen run a fixed timeline: a
 * charge, a fixed number of stages, a release, done. The Soul Furnace cannot work like that, because its
 * length is a property of the battlefield it lands in. It eats the living; while there is anything left to
 * eat it keeps growing; and it only ends when a look around finds nothing more to consume. A furnace fired
 * into an empty field burns for a few seconds. A furnace dropped on a raid burns for as long as the raid
 * keeps supplying it.
 *
 * <p>THE RULE, exactly as specified:
 * <ol>
 *   <li>On ignition it consumes everything in range immediately.</li>
 *   <li>About every five seconds ({@link #CHECK_INTERVAL_TICKS}) it looks for more.</li>
 *   <li>Mobs found: consume them, grow the sphere, extend the effect, and go back to step 2.</li>
 *   <li>No mobs found: terminal blast, scaled by everything it ate.</li>
 * </ol>
 *
 * <p>WHY THERE IS A CEILING. {@link #MAX_CHECKS} is not a design flourish, it is the difference between a
 * weapon and a server process: a mob farm inside the radius would otherwise keep a detonation alive
 * forever, one entity-scan every five seconds, until somebody restarted the world. Past the ceiling the
 * furnace releases what it has, which is both honest and survivable. It is far above any real battle.
 *
 * <p>No Minecraft imports, so {@code tools/run_logic_harness.sh} proves the whole schedule.
 */
public final class SoulFurnacePlan {

    /** How often it looks for more to eat: 100 ticks, five seconds. */
    public static final int CHECK_INTERVAL_TICKS = 100;

    /** A short beat after ignition before the first availability check, so the sphere is on screen first. */
    public static final int FIRST_CHECK_TICKS = 20;

    /**
     * Checks that must have happened before "nothing left to eat" is allowed to end the weapon. Without
     * this a furnace that lands on bare rock would detonate on the first look-around and never show the
     * growth the weapon is built around.
     */
    public static final int MIN_CHECKS = 2;

    /** The safety ceiling: 40 checks, two hundred seconds. See the class comment. */
    public static final int MAX_CHECKS = 40;

    /** How much the sphere grows per successful consumption check, as a fraction of the current size. */
    public static final float GROWTH_PER_CHECK = 0.18F;

    /** The sphere never grows past three times the radius it was fired with. */
    public static final float MAX_GROWTH = 3.0F;

    /**
     * How far out the furnace stops new mobs from spawning, as a multiple of its own current radius.
     *
     * <p>Without this the furnace cannot finish. It only releases when an availability check comes back
     * empty, and in any chunk with a normal spawn rate something walks back into range before the next
     * check lands - so every check finds food, it grows to the ceiling, and it sits there for the full
     * safety maximum. The furnace was reported doing exactly this. The margin is a fifth of the radius
     * rather than none because the check consumes within {@code radius} and a mob spawning exactly on
     * that boundary is consumed on the next check anyway; the extra ring is the one that matters, since
     * it is where a spawn would land and then walk in.
     */
    public static final float SUPPRESS_MULTIPLIER = 1.2F;

    /** Effect lifetime before any souls are counted. */
    public static final int BASE_EFFECT_TICKS = 240;

    /** Every soul consumed adds this much effect lifetime. */
    public static final int TICKS_PER_SOUL = 8;

    /** Hard ceiling on the visual, so a farm cannot leave geometry in the world forever. */
    public static final int MAX_EFFECT_TICKS = 1800;

    /** Every soul adds this much blast power, as a fraction of the apex's own power. */
    public static final float POWER_PER_SOUL = 0.02F;

    /** Hard ceiling on the terminal blast multiplier. */
    public static final float MAX_POWER_MULTIPLIER = 2.5F;

    /** Minimum sky circles; the giant red sigils are part of the weapon's identity even at zero souls. */
    public static final int BASE_SKY_CIRCLES = 3;

    /** Maximum sky circles, so the sky does not become a wall of rings. */
    public static final int MAX_SKY_CIRCLES = 24;

    private SoulFurnacePlan() {
    }

    /**
     * Absolute tick of the {@code index}-th availability check (0-based). Check 0 is the beat after
     * ignition; the rest are five seconds apart.
     */
    public static int checkTick(int index) {
        int clamped = Math.max(0, Math.min(index, MAX_CHECKS));
        return FIRST_CHECK_TICKS + clamped * CHECK_INTERVAL_TICKS;
    }

    /**
     * Does this check end the weapon?
     *
     * @param mobsRemaining true when the look-around found something still to consume
     * @param checksDone    how many checks have already run, this one included
     */
    public static boolean terminal(boolean mobsRemaining, int checksDone) {
        if (checksDone >= MAX_CHECKS) {
            return true;
        }
        return !mobsRemaining && checksDone >= MIN_CHECKS;
    }

    /**
     * Sphere size relative to the radius it was fired with, after {@code checks} successful consumptions.
     * Monotonic, and capped at {@link #MAX_GROWTH}.
     */
    public static float growth(int checks) {
        int clamped = Math.max(0, checks);
        return Math.min(MAX_GROWTH, 1.0F + GROWTH_PER_CHECK * clamped);
    }

    /** Radius in blocks after {@code checks} consumptions, from the radius the weapon was fired with. */
    public static int radius(int baseRadius, int checks) {
        return Math.max(4, Math.round(Math.max(1, baseRadius) * growth(checks)));
    }

    /**
     * The radius inside which the furnace vetoes new spawns, in blocks.
     *
     * <p>Grows with the furnace, so a furnace that has eaten three times as much holds back three times
     * the area - which is the point, since that is also three times the area something could walk in from.
     */
    public static double suppressionRadius(int baseRadius, int checks) {
        return radius(baseRadius, checks) * (double) SUPPRESS_MULTIPLIER;
    }

    /** Effect lifetime in ticks for a furnace that has eaten {@code souls}. */
    public static int lifetimeTicks(int souls) {
        int clamped = Math.max(0, souls);
        return Math.min(MAX_EFFECT_TICKS, BASE_EFFECT_TICKS + clamped * TICKS_PER_SOUL);
    }

    /** Terminal blast power: the apex's own power, scaled by everything the furnace ate. */
    public static float terminalPower(float apexPower, int souls) {
        int clamped = Math.max(0, souls);
        float multiplier = Math.min(MAX_POWER_MULTIPLIER, 1.0F + POWER_PER_SOUL * clamped);
        return Math.max(0.0F, apexPower * multiplier);
    }

    /** How many giant red circles hang in the sky for a furnace that has eaten {@code souls}. */
    public static int skyCircles(int souls) {
        int clamped = Math.max(0, souls);
        return Math.min(MAX_SKY_CIRCLES, BASE_SKY_CIRCLES + clamped / 8);
    }

    /**
     * Total ticks this furnace can possibly run for, including its terminal blast: the safety ceiling's
     * own schedule plus one interval of slack. Used to bound the client effect and the perf read-out.
     */
    public static int maximumTicks() {
        return checkTick(MAX_CHECKS) + CHECK_INTERVAL_TICKS;
    }

    /** The id of the apex this plan belongs to, for logs. */
    public static String id() {
        return "soul_furnace";
    }
}
