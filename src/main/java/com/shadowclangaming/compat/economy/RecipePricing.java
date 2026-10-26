package com.shadowclangaming.compat.economy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Prices items from their RECIPES, recursively, down to things that have no recipe.
 *
 * THE BUG THIS REPLACES
 * The old table priced by keyword: anything named "ingot" is 5, anything named "ore" is 7, and anything
 * that matched no keyword at all fell through to a flat 7 (x3 buy = $21). On a modded server most items
 * are machines and components whose names match nothing, so 90% of the "modded" shop tab cost $21 - a
 * price that says nothing about what the item is made of and makes arbitrage trivial (buy the $21
 * machine, sell its $300 of ingots back).
 *
 * THE RULE NOW
 *   * no recipe            -> the keyword price (unchanged; that is what the keyword table is for)
 *   * one or more recipes  -> cost of the CHEAPEST way to make it, per unit produced, plus labour
 *
 * "Cheapest way" is the meaningful definition for a shop: a player will always take the cheapest route,
 * so the shop must not pay more than the ingredients are worth by that route, or it prints money.
 *
 * WHY IT IS MINECRAFT-FREE
 * The graph is built by {@link RecipePricer} from the running server; this class only does the maths.
 * That means the resolution rules - cheapest option, per-unit division, cycles, depth - are covered by
 * the logic harness instead of by hoping.
 */
public final class RecipePricing {

    /** How deep the recursion goes before it gives up and uses the base price. */
    public static final int MAX_DEPTH = 24;

    /** Labour multipliers per recipe kind. A craft is not free; a smelt costs fuel. */
    public static final double LABOUR_CRAFTING = 1.25D;
    public static final double LABOUR_SMELTING = 1.40D;
    public static final double LABOUR_STONECUTTING = 1.20D;
    public static final double LABOUR_SMITHING = 1.50D;
    public static final double LABOUR_MACHINE = 1.60D;

    /** One way of making something. */
    public record Craft(int output, List<Options> ingredients, double labour) {
    }

    /** "One of these, {@code count} times" - the cheapest option is the one that gets charged. */
    public record Options(int count, List<String> options) {
    }

    /** The graph plus the base prices, and the memo for the answers. */
    public static final class Table {
        private final Map<String, List<Craft>> crafts = new HashMap<>();
        private final Map<String, Integer> basePrices = new HashMap<>();
        private final Map<String, Integer> resolved = new HashMap<>();
        private final Set<String> inProgress = new LinkedHashSet<>();
        private final Map<String, Integer> fallbacks = new HashMap<>();

        /** A price for something that cannot be crafted. This is the floor the recursion bottoms out on. */
        public void setBasePrice(String id, int sell) {
            basePrices.put(id, Math.max(1, sell));
        }

        public void addCraft(String outputId, Craft craft) {
            crafts.computeIfAbsent(outputId, key -> new ArrayList<>()).add(craft);
        }

        public boolean hasCrafts(String id) {
            return crafts.containsKey(id);
        }

        public int basePriceOf(String id) {
            return basePrices.getOrDefault(id, 1);
        }

        /** How many crafts are known for this id (diagnostics only). */
        public int craftCount(String id) {
            return crafts.getOrDefault(id, List.of()).size();
        }

        /**
         * The sell price of {@code id}, in whole currency units.
         *
         * Memoised, cycle-safe and depth-limited. An item whose recipes all depend on themselves
         * (a mod with a 1:1 conversion loop, which does happen) falls back to its base price rather
         * than to zero.
         */
        public int sellPrice(String id) {
            Integer known = resolved.get(id);
            if (known != null) {
                return known;
            }
            if (!inProgress.add(id)) {
                // Cycle: this id is already being priced higher up the stack.
                return basePriceOf(id);
            }
            int price;
            try {
                price = computeSell(id, 0);
            } finally {
                inProgress.remove(id);
            }
            price = Math.max(1, price);
            resolved.put(id, price);
            return price;
        }

        /** The buy price: EconomyCraft's own 3:1 ratio (iron 15/5, diamond 280/84). */
        public int buyPrice(String id) {
            return sellPrice(id) * 3;
        }

        /** Why a price came out the way it did, for `/scg economy explain <item>`. */
        public String explain(String id) {
            int price = sellPrice(id);
            List<Craft> list = crafts.get(id);
            if (list == null || list.isEmpty()) {
                return id + ": no recipe, base price " + basePriceOf(id) + " (sell " + price + ")";
            }
            StringBuilder text = new StringBuilder(id + ": sell " + price + " from " + list.size()
                    + " recipe(s), cheapest of:");
            Craft best = cheapestCraft(id, list);
            if (best != null) {
                text.append(' ').append(best.output()).append("x for");
                for (Options options : best.ingredients()) {
                    text.append(' ').append(options.count()).append('x')
                            .append(cheapestOption(options)).append(',');
                }
            }
            return text.toString();
        }

        // ------------------------------------------------------------------ the maths

        private int computeSell(String id, int depth) {
            List<Craft> list = crafts.get(id);
            if (list == null || list.isEmpty() || depth >= MAX_DEPTH) {
                return basePriceOf(id);
            }
            Craft best = cheapestCraft(id, list);
            if (best == null) {
                return basePriceOf(id);
            }

            double ingredientCost = 0.0D;
            for (Options options : best.ingredients()) {
                String option = cheapestOption(options);
                if (option == null) {
                    // An ingredient we cannot value at all (a tag of items with no price, an empty
                    // slot). Charge it as one unit of the base and keep going.
                    ingredientCost += options.count();
                    continue;
                }
                ingredientCost += options.count() * (double) priceOf(option, depth + 1);
            }

            int output = Math.max(1, best.output());
            double perUnit = ingredientCost / output * best.labour();
            // "Nearest value": round to the nearest whole unit, never below 1.
            return Math.max(1, (int) Math.round(perUnit));
        }

        private int priceOf(String id, int depth) {
            Integer known = resolved.get(id);
            if (known != null) {
                return known;
            }
            if (inProgress.contains(id)) {
                return basePriceOf(id);
            }
            List<Craft> list = crafts.get(id);
            if (list == null || list.isEmpty() || depth >= MAX_DEPTH) {
                return basePriceOf(id);
            }
            inProgress.add(id);
            int price;
            try {
                price = computeSell(id, depth);
            } finally {
                inProgress.remove(id);
            }
            price = Math.max(1, price);
            // Memoise everything that was NOT truncated by the depth limit: a truncated answer is the
            // base floor, and caching that would poison a later, deeper query. Without this memo the
            // resolution is exponential in chain length (every diamond sword would re-price planks).
            if (depth < MAX_DEPTH) {
                resolved.put(id, price);
            }
            return price;
        }

        /** The recipe that makes this item for the least money. */
        private Craft cheapestCraft(String id, List<Craft> list) {
            Craft best = null;
            double bestCost = Double.MAX_VALUE;
            for (Craft craft : list) {
                double cost = 0.0D;
                boolean usable = true;
                for (Options options : craft.ingredients()) {
                    String option = cheapestOption(options);
                    if (option == null) {
                        cost += options.count();
                        continue;
                    }
                    Integer price = resolved.get(option);
                    // Use the base price as a stand-in during selection so this pass cannot recurse;
                    // computeSell does the accurate pass for the chosen recipe.
                    cost += options.count() * (price != null ? price : basePriceOf(option));
                    if (inProgress.contains(option)) {
                        usable = false;
                    }
                }
                if (!usable) {
                    continue;
                }
                double perUnit = cost / Math.max(1, craft.output()) * craft.labour();
                if (perUnit < bestCost) {
                    bestCost = perUnit;
                    best = craft;
                }
            }
            return best != null ? best : (list.isEmpty() ? null : list.get(0));
        }

        /** The cheapest option in an ingredient list: the one a player would actually use. */
        private String cheapestOption(Options options) {
            String best = null;
            int bestPrice = Integer.MAX_VALUE;
            for (String candidate : options.options()) {
                Integer price = resolved.get(candidate);
                int value = price != null ? price : basePriceOf(candidate);
                if (value < bestPrice) {
                    bestPrice = value;
                    best = candidate;
                }
            }
            return best;
        }

        /** Everything priced so far, for reports. */
        public int resolvedCount() {
            return resolved.size();
        }

        /** Fill the fallback map so repeated base lookups are cheap, and reports can name the floor. */
        public void noteFallback(String id, int sell) {
            fallbacks.put(id, sell);
            setBasePrice(id, sell);
        }

        public Map<String, Integer> fallbacks() {
            return Map.copyOf(fallbacks);
        }
    }

    private RecipePricing() {
    }
}
