package com.shadowclangaming.smeltery;

import java.util.*;

/**
 * Simple internal fluid tank for smeltery multiblock.
 * Not NeoForge FluidStack - internal representation to avoid requiring fluid registration
 * for basic operation. Handles alloying logic.
 */
public final class SmelteryTank {
    private final Map<SmelteryFluid, Integer> fluids = new LinkedHashMap<>();
    private int capacity = 4 * 1296; // 4 blocks default, expands with size
    private int temperature = 0; // current heat

    public int capacity() { return capacity; }
    public void setCapacity(int mb) { capacity = Math.max(144, mb); }

    public int totalAmount() { return fluids.values().stream().mapToInt(i->i).sum(); }
    public int space() { return capacity - totalAmount(); }
    public Map<SmelteryFluid, Integer> view() { return Collections.unmodifiableMap(fluids); }

    public int get(SmelteryFluid fluid) { return fluids.getOrDefault(fluid, 0); }

    public int fill(SmelteryFluid fluid, int amount) {
        int canFill = Math.min(space(), amount);
        if (canFill <= 0) return 0;
        fluids.merge(fluid, canFill, Integer::sum);
        return canFill;
    }

    public int drain(SmelteryFluid fluid, int amount) {
        int have = fluids.getOrDefault(fluid, 0);
        int toDrain = Math.min(have, amount);
        if (toDrain <= 0) return 0;
        if (have == toDrain) fluids.remove(fluid);
        else fluids.put(fluid, have - toDrain);
        return toDrain;
    }

    public void setTemperature(int temp) { temperature = temp; }
    public int temperature() { return temperature; }

    /** Try alloying - called each tick by controller */
    public boolean tryAlloy() {
        if (fluids.size() < 2) return false;
        var match = SmelteryAlloyRecipe.match(fluids);
        if (match == null) return false;
        int batches = SmelteryAlloyRecipe.batchesAvailable(fluids, match);
        if (batches <= 0) return false;
        // Need temp
        if (temperature < match.temp()) return false;
        // Consume 1 batch and produce output
        for (var e : match.inputs().entrySet()) {
            drain(e.getKey(), e.getValue()*144);
        }
        fill(match.output(), match.outputAmountMb());
        return true;
    }

    public List<String> debugStrings() {
        List<String> out = new ArrayList<>();
        for (var e : fluids.entrySet()) out.add(e.getKey().id() + ": " + e.getValue() + "mB");
        return out;
    }
}
