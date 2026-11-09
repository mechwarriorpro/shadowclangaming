package com.shadowclangaming.compat.economy;

/**
 * Hand-set prices for items where a keyword guess would be wrong.
 *
 * EconomyCraft's own defaults use a consistent pattern worth matching:
 *   unit_buy ~= 3 x unit_sell   (iron ingot 15/5, diamond 280/84, nether star 3000/900)
 *
 * Currency items are the exception: they are FLAT (buy == sell) so nobody can arbitrage coins into
 * money. See docs/ECONOMY.md.
 */
public final class AuthoritativePrices {

    /** category, unit_buy, unit_sell */
    public record Price(String category, int buy, int sell) {
    }

    public static Price forId(String id) {
        return switch (id) {
            // ---------------------------------------------------------- currency (flat)
            case "shadowclangaming:war_bond" -> new Price("currency", 100, 100);

            // Create: Numismatics denominations - 1/8/16/64/512/4096
            case "numismatics:spur" -> new Price("currency", 1, 1);
            case "numismatics:bevel" -> new Price("currency", 8, 8);
            case "numismatics:sprocket" -> new Price("currency", 16, 16);
            case "numismatics:cog" -> new Price("currency", 64, 64);
            case "numismatics:crown" -> new Price("currency", 512, 512);
            case "numismatics:sun" -> new Price("currency", 4096, 4096);

            // ---------------------------------------------------------- ShadowClanGaming: chemistry
            case "shadowclangaming:uranium_ore" -> new Price("shadowclangaming.chemistry", 180, 60);
            case "shadowclangaming:raw_uranium" -> new Price("shadowclangaming.chemistry", 75, 25);
            case "shadowclangaming:enriched_uranium" -> new Price("shadowclangaming.chemistry", 1200, 400);
            case "shadowclangaming:tritium_cell" -> new Price("shadowclangaming.chemistry", 2700, 900);
            case "shadowclangaming:element_vial" -> new Price("shadowclangaming.chemistry", 18, 6);
            case "shadowclangaming:decomposer" -> new Price("shadowclangaming.machines", 3600, 1200);
            case "shadowclangaming:synthesizer" -> new Price("shadowclangaming.machines", 3600, 1200);

            // ---------------------------------------------------------- ShadowClanGaming: fire control
            case "shadowclangaming:launch_pad" -> new Price("shadowclangaming.military", 2400, 800);
            case "shadowclangaming:target_designator" -> new Price("shadowclangaming.military", 4500, 1500);
            case "shadowclangaming:rtg_pellet" -> new Price("shadowclangaming.military", 1050, 350);

            // ---------------------------------------------------------- ShadowClanGaming: warheads
            // Missiles are expensive on purpose: a missile is a *strategic* decision, not a purchase.
            case "shadowclangaming:decoy_missile" -> new Price("shadowclangaming.warheads", 360, 120);
            case "shadowclangaming:srbm" -> new Price("shadowclangaming.warheads", 600, 200);
            case "shadowclangaming:mrbm" -> new Price("shadowclangaming.warheads", 1800, 600);
            case "shadowclangaming:icbm" -> new Price("shadowclangaming.warheads", 4500, 1500);
            case "shadowclangaming:incendiary_missile" -> new Price("shadowclangaming.warheads", 1500, 500);
            case "shadowclangaming:cluster_missile" -> new Price("shadowclangaming.warheads", 2700, 900);
            case "shadowclangaming:emp_missile" -> new Price("shadowclangaming.warheads", 3600, 1200);
            case "shadowclangaming:fission_missile" -> new Price("shadowclangaming.warheads", 12000, 4000);
            case "shadowclangaming:fusion_missile" -> new Price("shadowclangaming.warheads", 36000, 12000);
            case "shadowclangaming:numismatic_missile" -> new Price("shadowclangaming.warheads", 2400, 800);
            case "shadowclangaming:antimatter_missile" -> new Price("shadowclangaming.warheads", 180000, 60000);
            // The Bank Card is a tool, not a consumable: it is meant to be kept, so it resells low.
            case "shadowclangaming:bank_card" -> new Price("shadowclangaming.tools", 3000, 900);

            // ---------------------------------------------------------- common nuclear mod items
            // These exist in NTM:DE, Ballistix etc. under different ids; keyword fallback covers the
            // rest, but the big ones deserve a real number.
            case "hbm:uranium_ingot", "hbm:ingot_uranium" -> new Price("ores", 60, 20);
            case "hbm:plutonium_ingot", "hbm:ingot_plutonium" -> new Price("ores", 600, 200);
            default -> null;
        };
    }

    private AuthoritativePrices() {
    }
}
