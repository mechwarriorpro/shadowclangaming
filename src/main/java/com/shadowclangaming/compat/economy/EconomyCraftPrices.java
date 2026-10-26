package com.shadowclangaming.compat.economy;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Optional;
import java.util.OptionalLong;

/**
 * Reads EconomyCraft's own shop prices, so the Auto-Trader sells at exactly the price the shop would
 * pay and buys at exactly the price the shop would charge.
 *
 * This matters: EconomyCraft is the authority on prices, and our generated table
 * ({@link EconomyBridge} + {@link PriceHeuristic}) is what feeds it. Asking the plugin instead of
 * duplicating the logic means a server owner who hand-edits prices.json gets a trader that respects
 * their edit immediately.
 *
 * Reflection for the same reason as {@link EconomyCraftBalance}: no GPL linkage.
 *
 * Their surface used here:
 *   api.prices()                  -> PriceApi
 *   priceApi.resolve(ItemStack)   -> Optional<ItemPrice>
 *   itemPrice.unitSellPrice()     -> OptionalLong
 *   itemPrice.unitBuyPrice()      -> OptionalLong
 *
 * Must be called on the server thread (their impl asserts it).
 */
public final class EconomyCraftPrices {

    private static final String API_CLASS = "com.reazip.economycraft.api.v1.EconomyCraftApi";

    private static boolean attempted = false;
    private static MethodHandle getApi;
    private static MethodHandle prices;
    private static MethodHandle resolve;
    private static MethodHandle unitSell;
    private static MethodHandle unitBuy;
    private static volatile boolean available = false;

    private static synchronized void resolveHandles() {
        if (attempted) {
            return;
        }
        attempted = true;
        if (!ModList.get().isLoaded(EconomyBridge.MODID)) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            Class<?> priceApiClass = Class.forName("com.reazip.economycraft.api.v1.PriceApi");
            Class<?> itemPriceClass = Class.forName("com.reazip.economycraft.api.v1.ItemPrice");
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            getApi = lookup.findStatic(apiClass, "get",
                    MethodType.methodType(apiClass, MinecraftServer.class));
            prices = lookup.findVirtual(apiClass, "prices", MethodType.methodType(priceApiClass));
            resolve = lookup.findVirtual(priceApiClass, "resolve",
                    MethodType.methodType(Optional.class, ItemStack.class));
            unitSell = lookup.findVirtual(itemPriceClass, "unitSellPrice",
                    MethodType.methodType(OptionalLong.class));
            unitBuy = lookup.findVirtual(itemPriceClass, "unitBuyPrice",
                    MethodType.methodType(OptionalLong.class));

            available = true;
        } catch (Throwable throwable) {
            available = false;
            ShadowClanGaming.LOGGER.warn("EconomyCraft price API not usable ({}). Auto-Trader will idle.",
                    throwable.toString());
        }
    }

    public static boolean isAvailable() {
        resolveHandles();
        return available;
    }

    /**
     * @return what the shop pays for ONE of this item, or -1 if it is unsellable / API unavailable.
     */
    public static long sellValueOf(MinecraftServer server, ItemStack stack) {
        return lookup(server, stack, true);
    }

    /**
     * @return what the shop charges for ONE of this item, or -1 if it is unbuyable / API unavailable.
     */
    public static long buyValueOf(MinecraftServer server, ItemStack stack) {
        return lookup(server, stack, false);
    }

    private static long lookup(MinecraftServer server, ItemStack stack, boolean sellSide) {
        resolveHandles();
        if (!available || stack.isEmpty() || server == null) {
            return -1L;
        }
        try {
            Object apiInstance = getApi.invoke(server);
            Object priceApi = prices.invoke(apiInstance);
            @SuppressWarnings("unchecked")
            Optional<Object> resolved = (Optional<Object>) resolve.invoke(priceApi, stack);
            if (resolved == null || resolved.isEmpty()) {
                return -1L;
            }
            Object price = resolved.get();
            OptionalLong value = (OptionalLong) (sellSide ? unitSell : unitBuy).invoke(price);
            return value.isPresent() ? value.getAsLong() : -1L;
        } catch (Throwable throwable) {
            ShadowClanGaming.LOGGER.warn("EconomyCraft price lookup failed: {}", throwable.toString());
            return -1L;
        }
    }

    private EconomyCraftPrices() {
    }
}
