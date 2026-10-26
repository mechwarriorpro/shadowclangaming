package com.shadowclangaming.compat.economy;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds the price graph from the recipes the server actually loaded.
 *
 * Uses the GENERIC recipe interface on purpose. {@code Recipe#getIngredients()} and
 * {@code Recipe#getResultItem(registries)} are implemented by every recipe type - vanilla shaped and
 * shapeless, smelting, blasting, smoking, stonecutting, smithing, and any mod's custom machine recipe
 * that gives sensible answers - so one loop prices the whole pack. There is no per-recipe-type switch
 * to forget when a mod adds a new machine.
 *
 * The labour multiplier is chosen from the recipe TYPE's id, so modded machine recipes get the machine
 * multiplier without being listed anywhere.
 */
public final class RecipePricer {

    private RecipePricer() {
    }

    /**
     * Assemble the table for every item in the registry.
     *
     * @param server the running server, for its recipe manager and registries
     */
    public static RecipePricing.Table build(MinecraftServer server) {
        RecipePricing.Table table = new RecipePricing.Table();

        // 1. The floor: what every item costs when it cannot be crafted. Authoritative prices win,
        //    then the keyword table - which is now only a base-material table in practice.
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            if (key == null || key.getPath().equals("air")) {
                continue;
            }
            String id = key.toString();
            table.setBasePrice(id, PriceHeuristic.priceFor(id, item.getDefaultMaxStackSize()).sell());
        }

        // 2. Every recipe, as "output <- ingredients".
        int counted = 0;
        int skipped = 0;
        for (net.minecraft.world.item.crafting.RecipeHolder<?> holder
                : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            if (recipe.isSpecial() || recipe.isIncomplete()) {
                // Dynamic recipes (dye a leather jacket, banner patterns) have no meaningful cost.
                skipped++;
                continue;
            }
            ItemStack result;
            try {
                result = recipe.getResultItem(server.registryAccess());
            } catch (Throwable throwable) {
                skipped++;
                continue;
            }
            if (result == null || result.isEmpty()) {
                skipped++;
                continue;
            }
            ResourceLocation resultKey = BuiltInRegistries.ITEM.getKey(result.getItem());
            if (resultKey == null) {
                skipped++;
                continue;
            }

            List<RecipePricing.Options> ingredients = new ArrayList<>();
            boolean usable = true;
            for (Ingredient ingredient : recipe.getIngredients()) {
                if (ingredient == null || ingredient.isEmpty()) {
                    // An empty slot in a shaped recipe is just padding: it costs nothing.
                    continue;
                }
                ItemStack[] options = ingredient.getItems();
                if (options.length == 0) {
                    usable = false;
                    break;
                }
                List<String> ids = new ArrayList<>(Math.min(options.length, 256));
                for (int i = 0; i < options.length && i < 256; i++) {
                    ResourceLocation optionKey = BuiltInRegistries.ITEM.getKey(options[i].getItem());
                    if (optionKey != null) {
                        ids.add(optionKey.toString());
                    }
                }
                if (ids.isEmpty()) {
                    usable = false;
                    break;
                }
                ingredients.add(new RecipePricing.Options(1, List.copyOf(ids)));
            }
            if (!usable || ingredients.isEmpty()) {
                skipped++;
                continue;
            }

            table.addCraft(resultKey.toString(), new RecipePricing.Craft(
                    Math.max(1, result.getCount()),
                    List.copyOf(ingredients),
                    labourFor(recipe)));
            counted++;
        }

        ShadowClanGaming.LOGGER.info(
                "Price graph: {} recipes indexed, {} skipped (special/empty), {} items with a base price",
                counted, skipped, BuiltInRegistries.ITEM.size());
        return table;
    }

    /** Labour by recipe type id, so a modded machine recipe is costed as machine work. */
    private static double labourFor(Recipe<?> recipe) {
        ResourceLocation type;
        try {
            type = BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
        } catch (Throwable throwable) {
            return RecipePricing.LABOUR_CRAFTING;
        }
        String path = type == null ? "crafting" : type.getPath();
        return switch (path) {
            case "crafting", "crafting_shaped", "crafting_shapeless", "crafting_special_repairitem" ->
                    RecipePricing.LABOUR_CRAFTING;
            case "smelting", "blasting", "smoking", "campfire_cooking" -> RecipePricing.LABOUR_SMELTING;
            case "stonecutting" -> RecipePricing.LABOUR_STONECUTTING;
            case "smithing", "smithing_transform", "smithing_trim" -> RecipePricing.LABOUR_SMITHING;
            default -> RecipePricing.LABOUR_MACHINE;
        };
    }
}
