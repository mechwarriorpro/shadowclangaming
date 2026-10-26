package com.shadowclangaming.magic;

import com.shadowclangaming.registry.SCGItems;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerTrades;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;

import java.util.List;

/**
 * Holy Water is not craftable. It is a cleric trade, which makes the cure a thing you have to go and
 * find a village for - and makes the cleric the one villager worth protecting when a plague is
 * running around.
 *
 * Two trades, at profession levels 2 and 4: the first is expensive but always available, the second is
 * bulk and cheaper per bottle so a server full of cursed players does not bottleneck on one priest.
 *
 * Note on 1.21.1: the trade lists are {@code List<VillagerTrades.ItemListing>} - factories, not built
 * offers - so a trade is described rather than constructed.
 */
public final class ClericTrades {

    public static void onVillagerTrades(VillagerTradesEvent event) {
        if (event.getType() != VillagerProfession.CLERIC) {
            return;
        }
        addTrade(event.getTrades().get(2), 12, 1);
        addTrade(event.getTrades().get(4), 40, 4);
    }

    /** One offer: {@code emeralds} emeralds for {@code bottles} bottles of Holy Water. */
    private static void addTrade(List<VillagerTrades.ItemListing> offers, int emeralds, int bottles) {
        if (offers == null) {
            return; // that profession level has no vanilla list on this version; skip quietly
        }
        offers.add(new VillagerTrades.ItemsForEmeralds(
                SCGItems.HOLY_WATER.get(), emeralds, bottles, 8, 10));
    }

    private ClericTrades() {
    }
}
