package com.shadowclangaming.chem;

import com.shadowclangaming.item.ElementVialItem;
import com.shadowclangaming.registry.SCGItems;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What the Chemical Synthesizer builds out of element vials.
 *
 * Decomposition is "one item in, random elements out". Synthesis is the reverse: feed N vials of
 * specific elements, get one useful item. The machine buffers elements, so a player can feed it
 * U-238 vials one at a time and it fires the instant a recipe is satisfied.
 *
 * This chain is the "Nuclear Science" spine of the pack:
 *   ore -> Decomposer -> U-238 vials -> Synthesizer -> Enriched Uranium -> warhead
 *                      -> Li + H vials -> Tritium Cell -> fusion warhead
 *                      -> U-238 + U-235 -> Plutonium-239 vials -> (M1) implosion warheads
 */
public final class SynthesisTable {

    /**
     * @param inputs element -> how many vials are consumed
     * @param result the item produced
     * @param ticks  how long the machine takes once the requirements are met
     */
    public record Synthesis(Map<Element, Integer> inputs, ItemStack result, int ticks) {
    }

    private static final List<Synthesis> RECIPES = new ArrayList<>();

    static {
        // Enrichment: lots of U-238 in, a little reactor/weapons fuel out.
        add(synthesis(200, new ItemStack(SCGItems.ENRICHED_URANIUM.get()),
                Element.U238, 4));

        // Fusion fuel: lithium + hydrogen -> tritium.
        add(synthesis(160, new ItemStack(SCGItems.TRITIUM_CELL.get()),
                Element.LI, 2, Element.H, 1));

        // Breeder reaction: U-238 captures a neutron from U-235 and becomes plutonium.
        add(synthesis(240, ElementVialItem.create(Element.PU239, 1),
                Element.U238, 2, Element.U235, 1));

        // Basic chemistry: gunpowder from its elements. Cheap, always useful, teaches the machine.
        add(synthesis(80, new ItemStack(Items.GUNPOWDER, 2),
                Element.S, 1, Element.N, 2, Element.K, 1, Element.C, 1));

        // Mineral synthesis: reconstruct valuable minerals from pure elements.
        add(synthesis(120, new ItemStack(Items.DIAMOND, 1),
                Element.C, 8));
        add(synthesis(100, new ItemStack(Items.EMERALD, 1),
                Element.BE, 1, Element.AL, 1, Element.SI, 2, Element.O, 3));
        add(synthesis(60, new ItemStack(Items.QUARTZ, 2),
                Element.SI, 3, Element.O, 6));
        add(synthesis(80, new ItemStack(Items.GLOWSTONE_DUST, 2),
                Element.P, 2, Element.S, 1, Element.HE, 1));
        add(synthesis(80, new ItemStack(Items.REDSTONE, 2),
                Element.FE, 1, Element.CU, 1, Element.SI, 1));
        add(synthesis(90, new ItemStack(Items.TNT, 1),
                Element.C, 3, Element.H, 2, Element.N, 3, Element.O, 4));
        add(synthesis(140, new ItemStack(Items.ENDER_PEARL, 1),
                Element.SI, 2, Element.C, 1, Element.N, 2, Element.TI, 1));
        add(synthesis(180, new ItemStack(Items.NETHERITE_SCRAP, 1),
                Element.AU, 2, Element.TI, 2, Element.W, 1, Element.C, 1));
    }

    private static Synthesis synthesis(int ticks, ItemStack result, Object... elementCountPairs) {
        Map<Element, Integer> inputs = new LinkedHashMap<>();
        for (int i = 0; i < elementCountPairs.length; i += 2) {
            inputs.put((Element) elementCountPairs[i], (Integer) elementCountPairs[i + 1]);
        }
        return new Synthesis(inputs, result, ticks);
    }

    private static void add(Synthesis synthesis) {
        RECIPES.add(synthesis);
    }

    public static List<Synthesis> all() {
        return RECIPES;
    }

    /**
     * Finds the first recipe the buffer can satisfy.
     *
     * @return the matching recipe, or {@code null} if the buffer is missing something.
     */
    public static Synthesis findMatch(Map<Element, Integer> buffer) {
        for (Synthesis synthesis : RECIPES) {
            boolean satisfied = true;
            for (Map.Entry<Element, Integer> requirement : synthesis.inputs().entrySet()) {
                if (buffer.getOrDefault(requirement.getKey(), 0) < requirement.getValue()) {
                    satisfied = false;
                    break;
                }
            }
            if (satisfied) {
                return synthesis;
            }
        }
        return null;
    }

    private SynthesisTable() {
    }
}
