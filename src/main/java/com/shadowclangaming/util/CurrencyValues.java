package com.shadowclangaming.util;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * "How much money is this item worth?" - used by the Numismatic Warhead.
 *
 * Deliberately data-light so the mod works with OR without Create: Numismatics:
 *   - our own War Bond is a fixed value and always exists
 *   - if Create: Numismatics is installed, its coins are recognised by item id
 *   - anything else is worth nothing
 *
 * Numismatics denominations (their published exchange rates):
 *   spur 1 | bevel 8 | sprocket 16 | cog 64 | crown 512 | sun 4096
 *
 * If a future Numismatics version renames or rebalances coins, just edit the table below - nothing else
 * in the mod cares.
 */
public final class CurrencyValues {

    /** Value of one War Bond (our fallback currency, works with no other mods installed). */
    public static final long WAR_BOND_VALUE = 100L;

    private static final Map<String, Long> VALUES = new HashMap<>();

    static {
        VALUES.put("shadowclangaming:war_bond", WAR_BOND_VALUE);
        VALUES.put("numismatics:spur", 1L);
        VALUES.put("numismatics:bevel", 8L);
        VALUES.put("numismatics:sprocket", 16L);
        VALUES.put("numismatics:cog", 64L);
        VALUES.put("numismatics:crown", 512L);
        VALUES.put("numismatics:sun", 4096L);
    }

    /** @return the value of one item, or 0 if it is not currency we understand. */
    public static long valueOf(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return VALUES.getOrDefault(id.toString(), 0L);
    }

    public static boolean isCurrency(ItemStack stack) {
        return valueOf(stack) > 0;
    }

    /** Lets you add your own currency items at runtime (e.g. from a config file, later). */
    public static void register(String itemId, long value) {
        VALUES.put(itemId, value);
    }

    private CurrencyValues() {
    }
}
