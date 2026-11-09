package com.shadowclangaming.smeltery;

import java.util.*;

/**
 * Smeltery alloying: mixing fluids in correct ratios produces new fluid.
 * Classic Tinkers alloy logic reimplemented clean-room:
 * smeltery controller checks tank contents against alloy recipes each tick,
 * if ratios match, consumes inputs and produces output.
 */
public final class SmelteryAlloyRecipe {
    public record Alloy(Map<SmelteryFluid, Integer> inputs, SmelteryFluid output, int outputAmountMb, int temp) {}

    private static final List<Alloy> RECIPES = new ArrayList<>();

    static {
        // Bronze: Copper 3 + Tin 1 => Bronze 4 (classic)
        add(Map.of(SmelteryFluid.MOLTEN_COPPER, 3, SmelteryFluid.MOLTEN_TIN, 1), SmelteryFluid.MOLTEN_BRONZE, 576, 500);
        // Manyullyn: Cobalt 1 + Ardite 1 => Manyullyn 2
        add(Map.of(SmelteryFluid.MOLTEN_COBALT, 1, SmelteryFluid.MOLTEN_ARDITE, 1), SmelteryFluid.MOLTEN_MANYULLYN, 288, 850);
        // Aluminium Brass: Aluminium 3 + Copper 1 (for casting)
        // Steel: Iron + coal via melting already handled, but alloy: Iron 1 + charcoal?
        // Unobtainium: 1 Nether Star + 8 Yellorium => 9 Unobtainium (500x power, 100x lifetime)
        add(Map.of(SmelteryFluid.MOLTEN_NETHER_STAR, 1, SmelteryFluid.MOLTEN_YELLORIUM, 8), SmelteryFluid.MOLTEN_UNOBTAINIUM, 1296, 1200);
    }

    private static void add(Map<SmelteryFluid, Integer> inputs, SmelteryFluid output, int outputMb, int temp) {
        RECIPES.add(new Alloy(inputs, output, outputMb, temp));
    }

    public static List<Alloy> all() { return Collections.unmodifiableList(RECIPES); }

    /**
     * Try to alloy from current tank. Returns output alloy if possible.
     * Input: map fluid -> amountMb in tank. Logic: if all inputs present at ratio, produce.
     */
    public static Alloy match(Map<SmelteryFluid, Integer> tank) {
        for (var recipe : RECIPES) {
            boolean matches = true;
            int minBatches = Integer.MAX_VALUE;
            for (var e : recipe.inputs().entrySet()) {
                int have = tank.getOrDefault(e.getKey(), 0);
                int needUnits = e.getValue() * 144; // 1 unit = 1 ingot =144mb
                if (have < needUnits) { matches = false; break; }
                minBatches = Math.min(minBatches, have / needUnits);
            }
            if (matches && minBatches > 0) return recipe;
        }
        return null;
    }

    public static int batchesAvailable(Map<SmelteryFluid, Integer> tank, Alloy recipe) {
        int batches = Integer.MAX_VALUE;
        for (var e : recipe.inputs().entrySet()) {
            int have = tank.getOrDefault(e.getKey(), 0);
            batches = Math.min(batches, have / (e.getValue()*144));
        }
        return batches;
    }
}
