package com.shadowclangaming.compat.economy;

/**
 * Deterministic price guess for an item nobody has priced.
 *
 * RULES ARE ORDERED, MOST SPECIFIC FIRST. That ordering matters more than the numbers:
 * "diamond_sword" must hit the sword rule before the gem rule, and "diamond_block" must hit the
 * storage-block rule before the gem rule. (Both were real bugs caught by testing.)
 *
 * Buy is always 3x sell, matching EconomyCraft's own ratios (iron 15/5, diamond 280/84,
 * nether star 3000/900), so generated entries sit naturally beside vanilla ones.
 *
 * Coverage over perfection: every item in every mod gets a sane, non-arbitrageable price, and the
 * ones that matter are hand-set in {@link AuthoritativePrices}. Everything is one file to edit.
 */
public final class PriceHeuristic {

    public record Price(String category, int buy, int sell) {
    }

    /** A rule: if the item path contains any keyword, use this base price and category. */
    private record Rule(String[] keywords, String category, int baseSell, boolean useMaterialMultiplier) {
    }

    private static final Rule[] RULES = {
            // ---- gear first: "diamond_sword" is a sword, not a gem ----
            new Rule(new String[]{"helmet", "chestplate", "chestpiece", "leggings", "boots", "gauntlet",
                    "grieves", "tunic", "robe", "hood", "shield"}, "armor", 26, true),
            new Rule(new String[]{"sword", "pickaxe", "shovel", "hoe", "shears", "dagger", "spear",
                    "battleaxe", "warhammer", "cutlass"}, "tools", 20, true),
            new Rule(new String[]{"_axe", "axe_"}, "tools", 20, true),

            // ---- machines and components ----
            new Rule(new String[]{"machine", "engine", "motor", "generator", "reactor", "turbine",
                    "centrifuge", "assembler", "fabricator", "smelter", "crusher", "boiler",
                    "compressor", "refinery", "enricher", "separator", "condenser"}, "machines", 130, false),
            new Rule(new String[]{"mechanical_", "press", "mixer", "millstone", "saw", "drill",
                    "deployer", "funnel", "chute", "shaft", "bearing", "piston_", "valve", "pump",
                    "conveyor", "belt", "assembler", "mechanism", "crushing", "_wheel", "fan",
                    "nozzle", "turret", "hopper_", "arm_"}, "machines", 45, false),
            new Rule(new String[]{"circuit", "processor", "chip", "board", "controller", "computer",
                    "wafer", "capacitor", "transistor", "logic", "terminal", "monitor", "display",
                    "interface", "pattern_provider"}, "machines", 40, false),

            new Rule(new String[]{"pellet", "fuel_rod", "fuel_cell", "fuel_assembly"}, "utility", 12, false),
            // Nuclearcraft / Mekanism name fuels as "uranium_235_fuel" - suffix, not prefix.
            new Rule(new String[]{"_fuel", "fuel_"}, "utility", 10, true),

            // ---- exotic matter: ProjectE, Draconic, Mekanism processing goods ----
            // Awakened/chaos gear is a tier above its base material, so it gets its own rule.
            new Rule(new String[]{"awakened", "chaos_"}, "utility", 40, true),
            new Rule(new String[]{"dark_matter", "red_matter", "_matter"}, "utility", 300, false),
            new Rule(new String[]{"enriched", "fissile", "breeder", "fissile_fuel"}, "utility", 8, true),
            // Mystical Agriculture essences: cheap, plentiful, infinite - deliberately low.
            new Rule(new String[]{"essence", "prosperity_shard", "inferium"}, "drops", 8, false),

            // ---- ordnance ----
            new Rule(new String[]{"missile", "warhead", "bomb", "grenade", "shell", "bullet",
                    "cartridge", "rocket", "artillery", "ammo", "torpedo", "mine_", "_mine",
                    "explosive", "propellant", "casing", "thermobaric", "incendiary", "shrapnel",
                    "breaching", "exothermic", "endothermic", "chemical", "toxic", "sonic",
                    "debris", "fragmentation"}, "military", 240, false),

            // ---- storage blocks before raw materials ----
            new Rule(new String[]{"_block", "block_of", "_bars"}, "blocks", 45, true),

            // ---- raw materials ----
            new Rule(new String[]{"nugget"}, "ores", 2, false),
            new Rule(new String[]{"raw_", "_raw", "chunk", "ore", "scrap", "cluster"}, "ores", 7, false),
            new Rule(new String[]{"ingot", "billet"}, "ores", 5, true),
            new Rule(new String[]{"dust", "powder", "grit", "flour"}, "drops", 4, false),
            new Rule(new String[]{"plate", "sheet", "foil"}, "ores", 6, true),
            new Rule(new String[]{"rod", "wire", "bolt", "screw", "spring", "coil"}, "ores", 4, false),
            new Rule(new String[]{"gear", "cogwheel", "sprocket"}, "utility", 18, false),
            new Rule(new String[]{"gem", "crystal", "shard", "diamond", "emerald", "quartz"}, "ores", 40, false),

            // ---- misc world items ----
            new Rule(new String[]{"log", "wood", "plank", "slab", "stairs", "fence", "door"}, "blocks.earth", 2, false),
            new Rule(new String[]{"seed", "sapling", "leaves"}, "drops", 1, false),
            new Rule(new String[]{"tank", "pipe", "bucket", "barrel", "drum"}, "utility", 10, false),
            new Rule(new String[]{"bread", "cake", "cooked", "stew", "soup", "juice", "jerky", "pie"}, "food", 5, false),
            new Rule(new String[]{"apple", "carrot", "potato", "berry", "meat", "fish", "cheese"}, "food", 3, false),
    };

    /**
     * Munitions some mods name by effect alone ("ballistix:emp", "ballistix:antimatter"). Substring
     * matching cannot catch these without false hits ("emp" is inside "temperature"), so they are
     * matched on the whole path instead.
     */
    private static final java.util.Set<String> BARE_ORDNANCE = java.util.Set.of(
            "emp", "antimatter", "nuclear", "cluster", "incendiary", "sonic", "laser", "plasma",
            "breaching", "attractive", "repulsive", "contagious", "fragmentation", "thermobaric",
            "darkmatter", "redmatter");

    /** Multipliers for materials that should cost more than generic iron. */
    private static final String[][] RARITY = {
            {"enriched", "plutonium", "plasma", "antimatter", "schrabidium", "draconium",
                    "awakened", "chaos", "californium", "americium", "curium"},   // x12
            {"uranium", "thorium", "titanium", "tungsten", "iridium", "osmium"},
            {"brass", "bronze", "steel", "electrum", "invar", "signalum", "nethersteel"},
    };

    private static final double[] RARITY_MULTIPLIER = {12.0, 3.0, 2.0};

    public static Price priceFor(String id, int maxStackSize) {
        AuthoritativePrices.Price exact = AuthoritativePrices.forId(id);
        if (exact != null) {
            return new Price(exact.category(), exact.buy(), exact.sell());
        }

        String namespace = id.substring(0, id.indexOf(':'));
        String path = id.substring(id.indexOf(':') + 1);

        if (BARE_ORDNANCE.contains(path)) {
            int sell = Math.max(1, (int) Math.round(240 * rarityMultiplier(path)));
            return new Price("military", sell * 3, sell);
        }

        for (Rule rule : RULES) {
            if (!containsAny(path, rule.keywords())) {
                continue;
            }
            double sell = rule.baseSell();
            // Material tier (iron vs diamond) AND rarity (uranium vs iron) both matter. Applying only
            // one made plutonium ingots cost the same as iron ones - caught by testing.
            sell *= materialMultiplier(path);
            sell *= rarityMultiplier(path);
            int rounded = Math.max(1, (int) Math.round(sell));
            return new Price(rule.category(), rounded * 3, rounded);
        }

        int base = (int) Math.round(7 * rarityMultiplier(path));
        return new Price("modded." + namespace, base * 3, base);
    }

    private static boolean containsAny(String path, String[] keywords) {
        for (String keyword : keywords) {
            if (path.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private static double materialMultiplier(String path) {
        if (path.contains("netherite")) {
            return 6.0;
        }
        if (path.contains("diamond")) {
            return 3.0;
        }
        if (path.contains("golden") || path.contains("gold_")) {
            return 1.5;
        }
        if (path.contains("iron") || path.contains("steel") || path.contains("brass")) {
            return 1.0;
        }
        if (path.contains("stone") || path.contains("flint") || path.contains("copper")) {
            return 0.5;
        }
        if (path.contains("wooden") || path.contains("wood_")) {
            return 0.3;
        }
        return 1.0;
    }

    private static double rarityMultiplier(String path) {
        for (int i = 0; i < RARITY.length; i++) {
            if (containsAny(path, RARITY[i])) {
                return RARITY_MULTIPLIER[i];
            }
        }
        return 1.0;
    }

    private PriceHeuristic() {
    }
}
