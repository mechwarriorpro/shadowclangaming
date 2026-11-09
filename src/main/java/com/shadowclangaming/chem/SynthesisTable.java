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

        // Chemical Compounds from classic MineChem:
        add(synthesis(60, com.shadowclangaming.item.CompoundVialItem.create(Compound.SULFURIC_ACID, 1),
                Element.H, 2, Element.S, 1, Element.O, 4));
        add(synthesis(40, com.shadowclangaming.item.CompoundVialItem.create(Compound.HYDROCHLORIC_ACID, 1),
                Element.H, 1, Element.CL, 1));
        add(synthesis(60, com.shadowclangaming.item.CompoundVialItem.create(Compound.CYANIDE, 1),
                Element.K, 1, Element.C, 1, Element.N, 1));
        add(synthesis(40, com.shadowclangaming.item.CompoundVialItem.create(Compound.AMMONIA, 1),
                Element.N, 1, Element.H, 3));
        add(synthesis(60, com.shadowclangaming.item.CompoundVialItem.create(Compound.PHOSGENE, 1),
                Element.C, 1, Element.O, 1, Element.CL, 2));
        add(synthesis(80, com.shadowclangaming.item.CompoundVialItem.create(Compound.SARIN, 1),
                Element.C, 4, Element.H, 10, Element.F, 1, Element.O, 2, Element.P, 1));
        add(synthesis(80, com.shadowclangaming.item.CompoundVialItem.create(Compound.PENICILLIN, 1),
                Element.C, 8, Element.H, 9, Element.N, 1, Element.O, 2, Element.S, 1));
        add(synthesis(60, com.shadowclangaming.item.CompoundVialItem.create(Compound.ASPIRIN, 1),
                Element.C, 9, Element.H, 8, Element.O, 4));
        add(synthesis(60, com.shadowclangaming.item.CompoundVialItem.create(Compound.CAFFEINE, 1),
                Element.C, 8, Element.H, 10, Element.N, 4, Element.O, 2));
        add(synthesis(50, com.shadowclangaming.item.CompoundVialItem.create(Compound.ETHANOL, 1),
                Element.C, 2, Element.H, 6, Element.O, 1));
        add(synthesis(40, com.shadowclangaming.item.CompoundVialItem.create(Compound.SALT, 1),
                Element.NA, 1, Element.CL, 1));
        add(synthesis(80, com.shadowclangaming.item.CompoundVialItem.create(Compound.TETRODOTOXIN, 1),
                Element.C, 11, Element.H, 17, Element.N, 3, Element.O, 8));
        // Propellants & oxidisers (hydrazine / N2O4 / RP-1 / LOX equivalents for Create thrusters)
        add(synthesis(100, com.shadowclangaming.item.CompoundVialItem.create(Compound.HYDRAZINE, 1),
                Element.N, 2, Element.H, 4));
        add(synthesis(110, com.shadowclangaming.item.CompoundVialItem.create(Compound.MMH, 1),
                Element.C, 1, Element.H, 6, Element.N, 2));
        add(synthesis(120, com.shadowclangaming.item.CompoundVialItem.create(Compound.UDMH, 1),
                Element.C, 2, Element.H, 8, Element.N, 2));
        add(synthesis(90, com.shadowclangaming.item.CompoundVialItem.create(Compound.NITROGEN_TETROXIDE, 1),
                Element.N, 2, Element.O, 4));
        add(synthesis(70, com.shadowclangaming.item.CompoundVialItem.create(Compound.NITRIC_ACID, 1),
                Element.H, 1, Element.N, 1, Element.O, 3));
        add(synthesis(70, com.shadowclangaming.item.CompoundVialItem.create(Compound.HYDROGEN_PEROXIDE_CONC, 1),
                Element.H, 2, Element.O, 2));
        add(synthesis(80, com.shadowclangaming.item.CompoundVialItem.create(Compound.KEROSENE, 1),
                Element.C, 12, Element.H, 26));
        add(synthesis(80, com.shadowclangaming.item.CompoundVialItem.create(Compound.RP1, 1),
                Element.C, 12, Element.H, 24));
        add(synthesis(40, com.shadowclangaming.item.CompoundVialItem.create(Compound.LIQUID_OXYGEN, 1),
                Element.O, 2));
        add(synthesis(100, com.shadowclangaming.item.CompoundVialItem.create(Compound.AMMONIUM_PERCHLORATE, 1),
                Element.N, 1, Element.H, 4, Element.CL, 1, Element.O, 4));
        add(synthesis(90, com.shadowclangaming.item.CompoundVialItem.create(Compound.NITROMETHANE, 1),
                Element.C, 1, Element.H, 3, Element.N, 1, Element.O, 2));
        add(synthesis(110, com.shadowclangaming.item.CompoundVialItem.create(Compound.NITROGLYCERIN, 1),
                Element.C, 3, Element.H, 5, Element.N, 3, Element.O, 9));
        // Medications (pharmacology effects)
        add(synthesis(80, com.shadowclangaming.item.CompoundVialItem.create(Compound.IBUPROFEN, 1),
                Element.C, 13, Element.H, 18, Element.O, 2));
        add(synthesis(100, com.shadowclangaming.item.CompoundVialItem.create(Compound.MORPHINE, 1),
                Element.C, 17, Element.H, 19, Element.N, 1, Element.O, 3));
        add(synthesis(80, com.shadowclangaming.item.CompoundVialItem.create(Compound.EPINEPHRINE, 1),
                Element.C, 9, Element.H, 13, Element.N, 1, Element.O, 3));
        add(synthesis(90, com.shadowclangaming.item.CompoundVialItem.create(Compound.ANTIDOTE_KIT, 1),
                Element.C, 6, Element.H, 12, Element.O, 6));
        add(synthesis(100, com.shadowclangaming.item.CompoundVialItem.create(Compound.BROAD_SPECTRUM_ANTIBIOTIC, 1),
                Element.C, 16, Element.H, 18, Element.N, 2, Element.O, 4));
        add(synthesis(100, com.shadowclangaming.item.CompoundVialItem.create(Compound.COAGULANT, 1),
                Element.C, 20, Element.H, 22, Element.N, 2, Element.O, 4));
        add(synthesis(90, com.shadowclangaming.item.CompoundVialItem.create(Compound.STIMULANT_MIX, 1),
                Element.C, 10, Element.H, 15, Element.N, 1, Element.O, 1));
        // Extra explosives & poisons
        add(synthesis(120, com.shadowclangaming.item.CompoundVialItem.create(Compound.RDX, 1),
                Element.C, 3, Element.H, 6, Element.N, 6, Element.O, 6));
        add(synthesis(140, com.shadowclangaming.item.CompoundVialItem.create(Compound.HMX, 1),
                Element.C, 4, Element.H, 8, Element.N, 8, Element.O, 8));
        add(synthesis(130, com.shadowclangaming.item.CompoundVialItem.create(Compound.PETN, 1),
                Element.C, 5, Element.H, 8, Element.N, 4, Element.O, 12));
        add(synthesis(110, com.shadowclangaming.item.CompoundVialItem.create(Compound.TATP, 1),
                Element.C, 9, Element.H, 18, Element.O, 6));
        add(synthesis(100, com.shadowclangaming.item.CompoundVialItem.create(Compound.VX_GAS, 1),
                Element.C, 11, Element.H, 26, Element.N, 1, Element.O, 2, Element.P, 1, Element.S, 1));
        add(synthesis(90, com.shadowclangaming.item.CompoundVialItem.create(Compound.MUSTARD_GAS, 1),
                Element.C, 4, Element.H, 8, Element.CL, 2, Element.S, 1));
        add(synthesis(80, com.shadowclangaming.item.CompoundVialItem.create(Compound.ARSENIC_TRIOXIDE, 1),
                Element.AS, 2, Element.O, 3));
        add(synthesis(160, com.shadowclangaming.item.CompoundVialItem.create(Compound.RICIN, 1),
                Element.C, 20, Element.H, 20, Element.N, 2, Element.O, 6));
        add(synthesis(200, com.shadowclangaming.item.CompoundVialItem.create(Compound.BOTULINUM, 1),
                Element.C, 67, Element.H, 104, Element.N, 17, Element.O, 20, Element.S, 1));
        // Sci-fi compounds — expensive (high tick cost + rare elements)
        add(synthesis(400, com.shadowclangaming.item.CompoundVialItem.create(Compound.NANITE_SERUM, 1),
                Element.C, 40, Element.H, 60, Element.N, 10, Element.O, 10, Element.TI, 2));
        add(synthesis(500, com.shadowclangaming.item.CompoundVialItem.create(Compound.VIBRANIUM_CATALYST, 1),
                Element.TI, 4, Element.V, 2, Element.C, 10, Element.O, 4));
        add(synthesis(600, com.shadowclangaming.item.CompoundVialItem.create(Compound.NAQUADAH_COMPOUND, 1),
                Element.U238, 2, Element.O, 4, Element.FE, 1));
        add(synthesis(800, com.shadowclangaming.item.CompoundVialItem.create(Compound.E115_ALLOTROPE, 1),
                Element.MC, 1, Element.PB, 2, Element.O, 2));
        add(synthesis(700, com.shadowclangaming.item.CompoundVialItem.create(Compound.ADAMANTINE_RESIN, 1),
                Element.TI, 6, Element.C, 12, Element.N, 2));
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
