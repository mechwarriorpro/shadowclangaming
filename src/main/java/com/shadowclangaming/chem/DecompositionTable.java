package com.shadowclangaming.chem;

import com.shadowclangaming.registry.SCGItems;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "What is this item made of?" - the heart of the chemistry loop.
 *
 * A Yield is a range, so results are a little random (like the classic decomposer machines):
 * a coal chunk always gives carbon, but the exact amount varies.
 *
 * Everything here is a plain Java table for now. Converting it to JSON (a real recipe type in
 * data/shadowclangaming/recipe/decomposition/) is milestone M2 - at that point server owners can add
 * their own breakdowns without touching code.
 */
public final class DecompositionTable {

    /** element, minimum amount, maximum amount (inclusive). */
    public record Yield(Element element, int min, int max) {
    }

    private static final Map<Item, List<Yield>> TABLE = new HashMap<>();

    static {
        // --- vanilla organics / fuels ---
        add(Items.COAL,            y(Element.C, 4, 6), y(Element.S, 0, 1));
        add(Items.CHARCOAL,        y(Element.C, 3, 5));
        add(Items.GUNPOWDER,       y(Element.S, 1, 2), y(Element.N, 1, 2), y(Element.K, 0, 1), y(Element.C, 0, 1));
        add(Items.SUGAR,           y(Element.C, 4, 4), y(Element.H, 5, 6), y(Element.O, 4, 4));
        add(Items.BONE_MEAL,       y(Element.C, 1, 2), y(Element.O, 2, 3), y(Element.N, 0, 1));

        // --- vanilla metals / minerals ---
        add(Items.RAW_IRON,        y(Element.FE, 2, 4));
        add(Items.IRON_INGOT,      y(Element.FE, 3, 3));
        add(Items.RAW_COPPER,      y(Element.CU, 2, 4));
        add(Items.COPPER_INGOT,    y(Element.CU, 3, 3));
        add(Items.RAW_GOLD,        y(Element.AU, 2, 3));
        add(Items.GOLD_INGOT,      y(Element.AU, 3, 3));
        add(Items.REDSTONE,        y(Element.FE, 1, 2), y(Element.CU, 0, 1));
        add(Items.LAPIS_LAZULI,    y(Element.AL, 1, 2), y(Element.SI, 1, 2), y(Element.S, 1, 2));
        add(Items.DIAMOND,         y(Element.C, 8, 10));
        add(Items.EMERALD,         y(Element.SI, 2, 3), y(Element.AL, 1, 2), y(Element.O, 2, 3));
        add(Items.SAND,            y(Element.SI, 3, 4), y(Element.O, 4, 5));
        add(Items.GLASS,           y(Element.SI, 3, 4), y(Element.O, 4, 5));
        add(Items.CLAY_BALL,       y(Element.SI, 2, 2), y(Element.AL, 1, 1), y(Element.O, 3, 3));
        add(Items.QUARTZ,          y(Element.SI, 3, 3), y(Element.O, 6, 6));

        // --- our own materials: this is where the nuclear chain starts ---
        add(SCGItems.RAW_URANIUM.get(), y(Element.U238, 2, 3), y(Element.U235, 0, 1), y(Element.PB, 0, 1));
        add(SCGItems.ENRICHED_URANIUM.get(), y(Element.U235, 2, 3), y(Element.U238, 0, 1));
        add(SCGItems.TRITIUM_CELL.get(), y(Element.T, 3, 4), y(Element.H, 0, 1));
    }

    private static Yield y(Element element, int min, int max) {
        return new Yield(element, min, max);
    }

    private static void add(Item item, Yield... yields) {
        TABLE.put(item, List.of(yields));
    }

    public static boolean isDecomposable(ItemStack stack) {
        return !stack.isEmpty() && TABLE.containsKey(stack.getItem());
    }

    /**
     * Rolls the table for one item and returns the vials that should pop out.
     * A "vial" is one unit of one element - see {@link com.shadowclangaming.item.ElementVialItem}.
     */
    public static List<ItemStack> decompose(ItemStack stack, net.minecraft.util.RandomSource random) {
        List<Yield> yields = TABLE.get(stack.getItem());
        List<ItemStack> out = new ArrayList<>();
        if (yields == null) {
            return out;
        }
        for (Yield yield : yields) {
            int amount = yield.min() == yield.max()
                    ? yield.min()
                    : yield.min() + random.nextInt(yield.max() - yield.min() + 1);
            for (int i = 0; i < amount; i++) {
                out.add(com.shadowclangaming.item.ElementVialItem.create(yield.element(), 1));
            }
        }
        return out;
    }

    private DecompositionTable() {
    }
}
