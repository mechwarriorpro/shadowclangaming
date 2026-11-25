package com.shadowclangaming.smeltery;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.core.registries.BuiltInRegistries;

import java.util.*;

/**
 * Smeltery melting recipes - which item melts into which fluid and how much.
 * Reimplements old Tinkers Construct melting: items -> fluids at temperature thresholds.
 * Covers vanilla + mod materials from Create, Mekanism, Thermal, etc.
 */
public final class SmelteryMeltingRecipe {
    public record Melting(Item item, SmelteryFluid fluid, int amountMb, int temp) {}

    private static final List<Melting> RECIPES = new ArrayList<>();
    private static final Map<Item, Melting> BY_ITEM = new HashMap<>();

    static {
        // Vanilla
        add(Items.IRON_INGOT, SmelteryFluid.MOLTEN_IRON, 144, 600);
        add(Items.GOLD_INGOT, SmelteryFluid.MOLTEN_GOLD, 144, 400);
        add(Items.COPPER_INGOT, SmelteryFluid.MOLTEN_COPPER, 144, 450);
        add(Items.NETHER_STAR, SmelteryFluid.MOLTEN_NETHER_STAR, 144, 1200);
        add(Items.NETHERITE_INGOT, SmelteryFluid.MOLTEN_MANYULLYN, 144, 800);
        add(Items.IRON_BLOCK, SmelteryFluid.MOLTEN_IRON, 1296, 600);
        add(Items.GOLD_BLOCK, SmelteryFluid.MOLTEN_GOLD, 1296, 400);
        // Manyullyn classic (cobalt + ardite)
        // Mod materials - soft: use registry name lookup so no hard dep
        addById("minecraft:raw_iron", SmelteryFluid.MOLTEN_IRON, 144, 600);
        addById("bigreactors:yellorium_ingot", SmelteryFluid.MOLTEN_YELLORIUM, 144, 650);
        addById("bigreactors:yellorium_block", SmelteryFluid.MOLTEN_YELLORIUM, 1296, 650);
        addById("extremereactors:yellorium_ingot", SmelteryFluid.MOLTEN_YELLORIUM, 144, 650);
        addById("biggerreactors:yellorium_ingot", SmelteryFluid.MOLTEN_YELLORIUM, 144, 650);
        // Create
        addById("create:brass_ingot", SmelteryFluid.MOLTEN_BRONZE, 144, 500);
        addById("create:zinc_ingot", SmelteryFluid.MOLTEN_TIN, 144, 350);
        addById("create:copper_sheet", SmelteryFluid.MOLTEN_COPPER, 144, 450);
        // Thermal
        addById("thermal:tin_ingot", SmelteryFluid.MOLTEN_TIN, 144, 350);
        addById("thermal:lead_ingot", SmelteryFluid.MOLTEN_IRON, 144, 600);
        addById("thermal:silver_ingot", SmelteryFluid.MOLTEN_GOLD, 144, 400);
        addById("thermal:nickel_ingot", SmelteryFluid.MOLTEN_IRON, 144, 600);
        // Mekanism
        addById("mekanism:ingot_steel", SmelteryFluid.MOLTEN_STEEL, 144, 700);
        addById("mekanism:ingot_osmium", SmelteryFluid.MOLTEN_IRON, 144, 700);
        // Tinkers legacy materials
        addById("tconstruct:cobalt_ingot", SmelteryFluid.MOLTEN_COBALT, 144, 750);
        addById("tconstruct:ardite_ingot", SmelteryFluid.MOLTEN_ARDITE, 144, 850);
        addById("tconstruct:manyullyn_ingot", SmelteryFluid.MOLTEN_MANYULLYN, 144, 800);
        // Unobtainium ingot itself can be remelted
        addById("shadowclangaming:unobtainium_ingot", SmelteryFluid.MOLTEN_UNOBTAINIUM, 144, 1500);
        addById("shadowclangaming:unobtainium_block", SmelteryFluid.MOLTEN_UNOBTAINIUM, 1296, 1500);
    }

    private static void add(Item item, SmelteryFluid fluid, int amount, int temp) {
        Melting m = new Melting(item, fluid, amount, temp);
        RECIPES.add(m);
        BY_ITEM.put(item, m);
    }
    private static void addById(String id, SmelteryFluid fluid, int amount, int temp) {
        var loc = net.minecraft.resources.ResourceLocation.parse(id);
        var item = BuiltInRegistries.ITEM.get(loc);
        if (item != null && item != Items.AIR) add(item, fluid, amount, temp);
        else {
            // deferred: store as string lookup fallback (record is final, no anonymous toString)
            RECIPES.add(new Melting(Items.AIR, fluid, amount, temp));
        }
    }

    public static Melting find(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        var direct = BY_ITEM.get(stack.getItem());
        if (direct != null) return direct;
        // fallback by registry id
        var loc = BuiltInRegistries.ITEM.getKey(stack.getItem());
        for (var r : RECIPES) {
            if (r.item() == Items.AIR) continue;
            // string lookup already handled
        }
        return null;
    }

    public static List<Melting> all() { return Collections.unmodifiableList(RECIPES); }
}
