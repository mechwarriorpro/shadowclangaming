package com.shadowclangaming.blueprint;

import java.util.Map;

/**
 * The arithmetic behind the blueprint table, kept deliberately free of Minecraft classes so it can be
 * unit-tested without a game running (see docs/BLUEPRINT.md).
 *
 * THE IDEA (the user's design):
 *   1. mark out a box with the blueprint table
 *   2. build the rifle out of real blocks inside that box, with an Emitter as the muzzle
 *   3. the table eats the blocks and hands you one rifle item
 *
 * So a rifle is literally worth what you built it from. A dirt-box pop gun pays full mana cost; a
 * netherite frame with a diamond core is a precision instrument that cuts the price of every spell you
 * fire. That is the whole progression: you do not craft a better gun, you build one.
 *
 * Two numbers come out of a scan:
 *   TIER       1..5   - how exotic a spell the frame can hold (components above the tier overheat)
 *   EFFICIENCY 60..95 - what percentage of a spell's mana cost you actually pay
 */
public final class BlueprintMath {

    /** Hard volume cap: 32 x 32 x 32. Big enough for real builds, small enough to scan on right-click. */
    public static final int MAX_SIDE = 32;
    public static final int MAX_VOLUME = MAX_SIDE * MAX_SIDE * MAX_SIDE;

    /** A rifle has to be more than a single block and an emitter. */
    public static final int MIN_BLOCKS = 8;

    /** One of these must be inside the marked volume - it is the muzzle. More than one is a minigun. */
    public static final String EMITTER_PATH = "emitter";

    public static final int MIN_TIER = 1;
    public static final int MAX_TIER = 5;
    public static final int MIN_EFFICIENCY = 60;
    public static final int MAX_EFFICIENCY = 95;

    /** Score per unit of "worth" - tune here, not in the block entity. */
    public static final long SCORE_PER_TIER = 60L;
    public static final long SCORE_PER_EFFICIENCY = 8L;

    /**
     * What one block is worth when it is part of a rifle frame. Keys are block registry paths, so
     * anything not listed (and any modded block) is worth the default.
     *
     * This is intentionally not a "rarity" table: it is roughly what the block is made of, so a block
     * of iron is worth eight blocks of stone and a block of netherite is worth five blocks of iron.
     */
    private static final Map<String, Integer> VALUES = Map.ofEntries(
            Map.entry("stone", 1), Map.entry("cobblestone", 1), Map.entry("deepslate", 1),
            Map.entry("dirt", 1), Map.entry("planks", 1), Map.entry("oak_planks", 1),
            Map.entry("oak_log", 1), Map.entry("glass", 2), Map.entry("bricks", 2),
            Map.entry("stone_bricks", 2), Map.entry("smooth_stone", 2),
            Map.entry("copper_block", 5), Map.entry("cut_copper", 5),
            Map.entry("redstone_block", 4), Map.entry("lapis_block", 6), Map.entry("quartz_block", 3),
            Map.entry("iron_block", 8), Map.entry("amethyst_block", 8),
            Map.entry("gold_block", 10), Map.entry("emerald_block", 18),
            Map.entry("diamond_block", 20), Map.entry("obsidian", 6), Map.entry("crying_obsidian", 12),
            Map.entry("netherite_block", 40), Map.entry("ancient_debris", 20),
            Map.entry("sculk", 4), Map.entry("sculk_catalyst", 10)
    );

    /** Value of one block, by registry path. Unknown solid blocks are worth 1. */
    public static int materialValue(String path) {
        return VALUES.getOrDefault(path, 1);
    }

    /** Result of scanning a finished build. */
    public record RifleSpec(int tier, int efficiencyPercent, int blocks, long score) {
    }

    /** Thrown when a marked volume cannot become a rifle. The message key is what the player sees. */
    public enum Rejection {
        TOO_LARGE("message.shadowclangaming.blueprint_too_large"),
        TOO_SMALL("message.shadowclangaming.blueprint_too_small"),
        NO_EMITTER("message.shadowclangaming.blueprint_no_emitter"),
        MANY_EMITTERS("message.shadowclangaming.blueprint_many_emitters"),
        NO_CORNERS("message.shadowclangaming.blueprint_no_corners"),
        OK("message.shadowclangaming.blueprint_ok");

        private final String translationKey;

        Rejection(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        public boolean ok() {
            return this == OK;
        }
    }

    /** Volume of the box described by two corners, inclusive. */
    public static long volume(int x1, int y1, int z1, int x2, int y2, int z2) {
        long dx = Math.abs((long) x2 - x1) + 1L;
        long dy = Math.abs((long) y2 - y1) + 1L;
        long dz = Math.abs((long) z2 - z1) + 1L;
        return dx * dy * dz;
    }

    /** Dimensions in order x, y, z. */
    public static int[] sizes(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new int[]{
                Math.abs(x2 - x1) + 1,
                Math.abs(y2 - y1) + 1,
                Math.abs(z2 - z1) + 1
        };
    }

    /**
     * Check a scan before anything is consumed.
     *
     * @param blocks  number of non-air blocks inside the box
     * @param emitters how many Emitter blocks were found inside the box
     * @param volume  the box volume
     */
    public static Rejection validate(int blocks, int emitters, long volume) {
        if (volume > MAX_VOLUME) {
            return Rejection.TOO_LARGE;
        }
        if (blocks < MIN_BLOCKS) {
            return Rejection.TOO_SMALL;
        }
        if (emitters <= 0) {
            return Rejection.NO_EMITTER;
        }
        // Multiple emitters are legal and useful: they cycle as barrels, each one shortening the
        // cooldown by EMITTER_COOLDOWN_STEP and raising the mana cost by EMITTER_MANA_STEP.
        return Rejection.OK;
    }

    /** Turn a scan into rifle stats. Score is the summed material value of everything consumed. */
    public static RifleSpec spec(int blocks, long score) {
        int tier = (int) Math.max(MIN_TIER, Math.min(MAX_TIER, MIN_TIER + score / SCORE_PER_TIER));
        int efficiency = (int) Math.max(MIN_EFFICIENCY,
                Math.min(MAX_EFFICIENCY, MIN_EFFICIENCY + score / SCORE_PER_EFFICIENCY));
        return new RifleSpec(tier, efficiency, blocks, score);
    }

    // ---- multi-emitter ("minigun") tuning ------------------------------------------------------

    /** Each extra emitter takes this fraction off the cooldown. */
    public static final double EMITTER_COOLDOWN_STEP = 0.05D;
    /** Each extra emitter adds this fraction to the mana cost. */
    public static final double EMITTER_MANA_STEP = 0.025D;
    /** Cooldown can never drop below this, however many emitters are bolted on. */
    public static final int MIN_COOLDOWN_TICKS = 2;
    /** Barrels top out at 128 - the user's number - and the mana bill is the real limit. */
    public static final int MAX_EMITTERS = 128;
    /** Barrel dial ceiling, mirrored by the bench's UI. */
    public static final int MAX_BARRELS = 128;
    /** Strength dial ceiling. */
    public static final int MAX_STRENGTH = 128;

    // ---- the two bench dials ------------------------------------------------------------------
    //
    // BARRELS   more muzzles: faster, hungrier, slightly harder-hitting.
    // STRENGTH  heavier barrels: much harder-hitting, a little slower and much more expensive.
    //
    // Both are linear, not exponential, so 128 is a big number rather than an infinity. At 128/128 the
    // gun fires for 17x power and costs about 80x mana a shot.

    /** +2% power per barrel above the first. */
    public static final double BARREL_POWER_STEP = 0.02D;
    /** +3% power per point of strength above the first. */
    public static final double STRENGTH_POWER_STEP = 0.03D;
    /** +2% mana per point of strength. */
    public static final double STRENGTH_MANA_STEP = 0.02D;
    /** +1% cooldown per point of strength: heavy barrels are slow barrels. */
    public static final double STRENGTH_COOLDOWN_STEP = 0.01D;

    /** Power multiplier from the barrel count: 1.00 at one barrel, 3.54 at 128. */
    public static double barrelPower(int barrels) {
        int count = Math.max(1, Math.min(MAX_BARRELS, barrels));
        return 1.0D + BARREL_POWER_STEP * (count - 1);
    }

    /** Power multiplier from barrel strength: 1.00 at one, 4.81 at 128. */
    public static double strengthPower(int strength) {
        int value = Math.max(1, Math.min(MAX_STRENGTH, strength));
        return 1.0D + STRENGTH_POWER_STEP * (value - 1);
    }

    /** Mana multiplier from barrel strength: 1.00 at one, 3.54 at 128. */
    public static double strengthMana(int strength) {
        int value = Math.max(1, Math.min(MAX_STRENGTH, strength));
        return 1.0D + STRENGTH_MANA_STEP * (value - 1);
    }

    /** Cooldown multiplier from barrel strength: 1.00 at one, 2.27 at 128. */
    public static double strengthCooldown(int strength) {
        int value = Math.max(1, Math.min(MAX_STRENGTH, strength));
        return 1.0D + STRENGTH_COOLDOWN_STEP * (value - 1);
    }

    /** Cooldown in ticks for a barrel count, given a base that may already include strength. */
    public static int barrelCooldownTicks(int baseCooldown, int barrels) {
        return cooldownTicks(baseCooldown, barrels);
    }

    /**
     * Ticks between shots for a rifle with {@code emitters} muzzles.
     *
     * Every emitter knocks 5% off: 12 ticks -> 11.4 -> 10.83 -> ... with a floor of 2 ticks. The floor
     * is not decoration - without it a 96-block build would fire tens of times per second and the mana
     * bill (which grows at 2.5% per emitter) would be the only thing stopping it.
     */
    public static int cooldownTicks(int baseCooldown, int emitters) {
        int count = Math.max(1, Math.min(MAX_EMITTERS, emitters));
        double ticks = baseCooldown * Math.pow(1.0D - EMITTER_COOLDOWN_STEP, count - 1);
        return (int) Math.max(MIN_COOLDOWN_TICKS, Math.round(ticks));
    }

    /**
     * Mana multiplier for a rifle with {@code emitters} muzzles: 1 + (n-1) steps of +2.5%.
     *
     * A single emitter is free (multiplier 1.0). Two barrels cost 2.5% more per shot, ten cost 22.5%
     * more - so a minigun trades mana efficiency for rate of fire, exactly as asked.
     */
    public static double manaMultiplier(int emitters) {
        int count = Math.max(1, Math.min(MAX_EMITTERS, emitters));
        return Math.pow(1.0D + EMITTER_MANA_STEP, count - 1);
    }

    // ---- touching-emitter clusters ("assembly level") ------------------------------------------

    /** Every extra emitter TOUCHING this one adds this fraction of power to the shot. */
    public static final double CLUSTER_POWER_STEP = 0.10D;
    /** ...and this fraction to the mana cost. */
    public static final double CLUSTER_MANA_STEP = 0.05D;
    /** A cluster never counts for more than this, so one absurd build cannot overflow the maths. */
    public static final int MAX_CLUSTER_LEVEL = 64;

    /**
     * The "level" of an emitter assembly: how many emitters are touching each other in it.
     *
     *   1 emitter          -> 1
     *   3x3 plate (9)      -> 9
     *   3x3x3 cube (27)    -> 27
     */
    public static int level(int emittersInCluster) {
        return Math.max(1, Math.min(MAX_CLUSTER_LEVEL, emittersInCluster));
    }

    /** Power multiplier for a cluster: 1.10^(level-1). A 27-emitter cube hits for 11.9x. */
    public static double clusterPower(int emittersInCluster) {
        return Math.pow(1.0D + CLUSTER_POWER_STEP, level(emittersInCluster) - 1);
    }

    /** Mana multiplier for a cluster: 1.05^(level-1). The same 27-emitter cube costs 3.56x. */
    public static double clusterMana(int emittersInCluster) {
        return Math.pow(1.0D + CLUSTER_MANA_STEP, level(emittersInCluster) - 1);
    }

    /**
     * What one shot costs, all three multipliers together: frame efficiency, the rifle-wide barrel
     * surcharge, and the firing cluster's own surcharge.
     */
    public static long shotCostForCluster(long rawCost, int efficiencyPercent, int emitters,
                                          int clusterLevel) {
        double cost = discounted(rawCost, efficiencyPercent)
                * manaMultiplier(emitters)
                * clusterMana(clusterLevel);
        return Math.max(1L, Math.round(cost));
    }

    /** The mana a shot actually costs, with efficiency and the emitter surcharge applied. */
    public static long shotCost(long rawCost, int efficiencyPercent, int emitters) {
        double cost = discounted(rawCost, efficiencyPercent) * manaMultiplier(emitters);
        return Math.max(1L, Math.round(cost));
    }

    /** Mana cost of a spell when fired from a rifle with this efficiency. Delegates to Spell. */
    public static long discounted(long rawCost, int efficiencyPercent) {
        int clamped = Math.max(10, Math.min(100, efficiencyPercent));
        return Math.max(1L, rawCost * clamped / 100L);
    }

    private BlueprintMath() {
    }
}
