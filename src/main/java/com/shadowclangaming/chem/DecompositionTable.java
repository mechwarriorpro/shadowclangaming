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
        add(Items.GUNPOWDER,       y(Element.K, 1, 1), y(Element.N, 1, 2), y(Element.O, 2, 3), y(Element.C, 1, 2), y(Element.S, 1, 2));
        add(Items.TNT,             y(Element.C, 3, 4), y(Element.H, 2, 3), y(Element.N, 2, 3), y(Element.O, 3, 4));
        add(Items.SUGAR,           y(Element.C, 4, 4), y(Element.H, 5, 6), y(Element.O, 4, 4));
        add(Items.BONE,            y(Element.CA, 3, 4), y(Element.P, 2, 2), y(Element.O, 4, 5));
        add(Items.BONE_MEAL,       y(Element.CA, 2, 3), y(Element.P, 1, 2), y(Element.O, 3, 4), y(Element.C, 1, 1));
        add(Items.SLIME_BALL,      y(Element.C, 2, 3), y(Element.H, 4, 5), y(Element.O, 2, 3), y(Element.N, 1, 1));
        add(Items.ROTTEN_FLESH,    y(Element.C, 2, 3), y(Element.H, 3, 4), y(Element.N, 1, 2), y(Element.O, 1, 2), y(Element.S, 0, 1));
        add(Items.SPIDER_EYE,      y(Element.C, 2, 3), y(Element.H, 3, 4), y(Element.N, 1, 2), y(Element.O, 1, 1));
        add(Items.WHEAT,           y(Element.C, 3, 4), y(Element.H, 4, 5), y(Element.O, 3, 4));
        add(Items.BREAD,           y(Element.C, 4, 5), y(Element.H, 6, 7), y(Element.O, 4, 5));
        add(Items.APPLE,           y(Element.C, 3, 4), y(Element.H, 4, 5), y(Element.O, 3, 4), y(Element.K, 0, 1));
        add(Items.DRIED_KELP,      y(Element.NA, 1, 2), y(Element.CL, 1, 2), y(Element.I, 0, 1), y(Element.C, 1, 2));
        add(Items.OAK_LOG,         y(Element.C, 4, 6), y(Element.H, 5, 7), y(Element.O, 4, 5));
        add(Items.OAK_LEAVES,      y(Element.C, 2, 3), y(Element.H, 3, 4), y(Element.O, 2, 3), y(Element.MG, 1, 1));

        // --- vanilla metals / minerals ---
        add(Items.RAW_IRON,        y(Element.FE, 2, 4));
        add(Items.IRON_INGOT,      y(Element.FE, 3, 3));
        add(Items.RAW_COPPER,      y(Element.CU, 2, 4));
        add(Items.COPPER_INGOT,    y(Element.CU, 3, 3));
        add(Items.RAW_GOLD,        y(Element.AU, 2, 3));
        add(Items.GOLD_INGOT,      y(Element.AU, 3, 3));
        add(Items.REDSTONE,        y(Element.FE, 1, 2), y(Element.CU, 1, 1), y(Element.SI, 1, 1));
        add(Items.LAPIS_LAZULI,    y(Element.AL, 1, 2), y(Element.SI, 1, 2), y(Element.S, 1, 2), y(Element.NA, 1, 1));
        add(Items.DIAMOND,         y(Element.C, 8, 10));
        add(Items.EMERALD,         y(Element.BE, 1, 2), y(Element.AL, 1, 2), y(Element.SI, 2, 3), y(Element.O, 3, 4));
        add(Items.AMETHYST_SHARD,  y(Element.SI, 2, 3), y(Element.O, 4, 5), y(Element.FE, 0, 1));
        add(Items.SAND,            y(Element.SI, 3, 4), y(Element.O, 4, 5));
        add(Items.GLASS,           y(Element.SI, 3, 4), y(Element.O, 4, 5));
        add(Items.CLAY_BALL,       y(Element.SI, 2, 2), y(Element.AL, 1, 1), y(Element.O, 3, 3));
        add(Items.QUARTZ,          y(Element.SI, 3, 3), y(Element.O, 6, 6));
        add(Items.STONE,           y(Element.SI, 2, 3), y(Element.O, 3, 4), y(Element.AL, 1, 1), y(Element.CA, 0, 1));
        add(Items.COBBLESTONE,     y(Element.SI, 2, 3), y(Element.O, 3, 4), y(Element.AL, 1, 1), y(Element.FE, 0, 1));
        add(Items.GRANITE,         y(Element.SI, 3, 4), y(Element.O, 4, 5), y(Element.AL, 1, 2), y(Element.K, 1, 1));
        add(Items.DIORITE,         y(Element.SI, 2, 3), y(Element.O, 3, 4), y(Element.AL, 1, 2), y(Element.NA, 1, 1), y(Element.CA, 1, 1));
        add(Items.ANDESITE,        y(Element.SI, 2, 3), y(Element.O, 3, 4), y(Element.MG, 1, 1), y(Element.FE, 1, 1));
        add(Items.BASALT,          y(Element.SI, 2, 3), y(Element.O, 3, 4), y(Element.MG, 1, 2), y(Element.CA, 1, 1), y(Element.FE, 1, 1));
        add(Items.OBSIDIAN,        y(Element.SI, 4, 6), y(Element.O, 6, 8), y(Element.FE, 1, 2), y(Element.MG, 1, 1));
        add(Items.WATER_BUCKET,    y(Element.H, 4, 4), y(Element.O, 2, 2));

        // --- nether & end ---
        add(Items.NETHERRACK,      y(Element.SI, 1, 2), y(Element.O, 2, 3), y(Element.S, 1, 2), y(Element.FE, 1, 1));
        add(Items.SOUL_SAND,       y(Element.C, 2, 3), y(Element.SI, 2, 3), y(Element.O, 3, 4), y(Element.P, 1, 1));
        add(Items.GLOWSTONE_DUST,  y(Element.P, 2, 3), y(Element.S, 1, 2), y(Element.HE, 1, 1), y(Element.NE, 0, 1));
        add(Items.BLAZE_POWDER,    y(Element.S, 2, 3), y(Element.P, 1, 2), y(Element.C, 1, 1), y(Element.FE, 0, 1));
        add(Items.ENDER_PEARL,     y(Element.SI, 2, 2), y(Element.C, 1, 2), y(Element.N, 1, 2), y(Element.TI, 1, 1));
        add(Items.NETHERITE_SCRAP, y(Element.AU, 2, 3), y(Element.TI, 2, 3), y(Element.W, 1, 2), y(Element.C, 1, 1));
        add(Items.NETHERITE_INGOT, y(Element.AU, 4, 4), y(Element.TI, 3, 4), y(Element.W, 2, 3), y(Element.C, 2, 2));

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
        if (stack.isEmpty()) return false;
        if (stack.is(SCGItems.CHEMICAL_BUCKET.get())) return true;
        return TABLE.containsKey(stack.getItem());
    }

    /**
     * Returns the yield definitions for an item without rolling random amounts.
     * Used by the Microscope and Chemist's Journal to display formulas.
     */
    public static List<Yield> yieldsFor(ItemStack stack) {
        if (stack.isEmpty()) {
            return List.of();
        }
        if (stack.is(SCGItems.CHEMICAL_BUCKET.get())) {
            Element e = com.shadowclangaming.item.ChemicalBucketItem.elementOf(stack);
            return List.of(new Yield(e, 8, 8));
        }
        List<Yield> yields = TABLE.get(stack.getItem());
        return yields == null ? List.of() : yields;
    }

    public static Map<Item, List<Yield>> allDecompositions() {
        return java.util.Collections.unmodifiableMap(TABLE);
    }

    /**
     * Rolls the table for one item and returns the vials that should pop out.
     * A "vial" is one unit of one element - see {@link com.shadowclangaming.item.ElementVialItem}.
     */
    public static List<ItemStack> decompose(ItemStack stack, net.minecraft.util.RandomSource random) {
        if (stack.is(SCGItems.CHEMICAL_BUCKET.get())) {
            Element e = com.shadowclangaming.item.ChemicalBucketItem.elementOf(stack);
            List<ItemStack> out = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                out.add(com.shadowclangaming.item.ElementVialItem.create(e, 1));
            }
            out.add(new ItemStack(Items.BUCKET));
            return out;
        }

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
