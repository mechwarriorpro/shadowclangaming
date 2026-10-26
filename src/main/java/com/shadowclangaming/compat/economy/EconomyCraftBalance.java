package com.shadowclangaming.compat.economy;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.server.MinecraftServer;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * Talks to EconomyCraft's balance API without linking against its jar.
 *
 * WHY REFLECTION AND NOT A COMPILE DEPENDENCY
 * EconomyCraft is GPL-3.0. Linking our (differently licensed) mod against it at compile time is a
 * grey area at best. Reflection means:
 *   - we ship no reference to their classes, only their published method names
 *   - our mod still builds and runs with EconomyCraft absent
 *   - if their API moves, we log one warning and fall back to coin loading
 *
 * Their API surface (v1, stable): com.reazip.economycraft.api.v1.EconomyCraftApi
 *   static EconomyCraftApi get(MinecraftServer)
 *   BalanceApi balances()
 *     long getBalance(UUID lead)
 *     BalanceMutationResult removeMoney(UUID lead, long amount)
 *   String formatMoney(long)
 *
 * IMPORTANT: their implementation calls requireServerThread(), so every call here must happen on the
 * server thread. Item interactions are, so this is fine - just never call it from a worker thread.
 */
public final class EconomyCraftBalance {

    public enum Status {
        OK,
        INSUFFICIENT_FUNDS,
        UNAVAILABLE,
        REJECTED
    }

    public record Result(Status status, long balanceAfter) {
        public boolean ok() {
            return status == Status.OK;
        }
    }

    private static final String API_CLASS = "com.reazip.economycraft.api.v1.EconomyCraftApi";

    private static boolean attempted = false;
    private static MethodHandle getApi;
    private static MethodHandle balances;
    private static MethodHandle getBalance;
    private static MethodHandle removeMoney;
    private static MethodHandle formatMoney;
    private static MethodHandle addMoney;
    private static volatile boolean available = false;

    private static synchronized void resolve() {
        if (attempted) {
            return;
        }
        attempted = true;

        if (!ModList.get().isLoaded(EconomyBridge.MODID)) {
            return;
        }
        try {
            Class<?> apiClass = Class.forName(API_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            getApi = lookup.findStatic(apiClass, "get",
                    MethodType.methodType(apiClass, MinecraftServer.class));

            Class<?> balanceApiClass = Class.forName("com.reazip.economycraft.api.v1.BalanceApi");
            balances = lookup.findVirtual(apiClass, "balances",
                    MethodType.methodType(balanceApiClass));
            getBalance = lookup.findVirtual(balanceApiClass, "getBalance",
                    MethodType.methodType(long.class, UUID.class));
            removeMoney = lookup.findVirtual(balanceApiClass, "removeMoney",
                    MethodType.methodType(
                            Class.forName("com.reazip.economycraft.api.v1.BalanceMutationResult"),
                            UUID.class, long.class));
            formatMoney = lookup.findVirtual(apiClass, "formatMoney",
                    MethodType.methodType(String.class, long.class));
            // Same BalanceMutationResult type as removeMoney, so no extra class lookup needed.
            addMoney = lookup.findVirtual(balanceApiClass, "addMoney",
                    MethodType.methodType(
                            Class.forName("com.reazip.economycraft.api.v1.BalanceMutationResult"),
                            UUID.class, long.class));

            available = true;
            ShadowClanGaming.LOGGER.info("EconomyCraft API found - Bank Cards will draw from player balances.");
        } catch (Throwable throwable) {
            available = false;
            ShadowClanGaming.LOGGER.warn("EconomyCraft API not usable ({}). Bank Cards disabled; coins still work.",
                    throwable.toString());
        }
    }

    public static boolean isAvailable() {
        resolve();
        return available;
    }

    private static Object api(MinecraftServer server) throws Throwable {
        return getApi.invoke(server);
    }

    /** @return the player's balance, or -1 if the API is unavailable. */
    public static long getBalance(MinecraftServer server, UUID player) {
        resolve();
        if (!available) {
            return -1L;
        }
        try {
            Object apiInstance = api(server);
            Object balanceApi = balances.invoke(apiInstance);
            return (long) getBalance.invoke(balanceApi, player);
        } catch (Throwable throwable) {
            ShadowClanGaming.LOGGER.warn("EconomyCraft getBalance failed: {}", throwable.toString());
            return -1L;
        }
    }

    /**
     * Spends money if the player can afford it. Never partially succeeds.
     */
    public static Result spend(MinecraftServer server, UUID player, long amount) {
        resolve();
        if (!available) {
            return new Result(Status.UNAVAILABLE, -1L);
        }
        if (amount <= 0L) {
            return new Result(Status.REJECTED, getBalance(server, player));
        }
        try {
            Object apiInstance = api(server);
            Object balanceApi = balances.invoke(apiInstance);

            long before = (long) getBalance.invoke(balanceApi, player);
            if (before < amount) {
                return new Result(Status.INSUFFICIENT_FUNDS, before);
            }

            Object result = removeMoney.invoke(balanceApi, player, amount);
            boolean success = (boolean) result.getClass().getMethod("successful").invoke(result);
            if (!success) {
                return new Result(Status.REJECTED, before);
            }
            long after = (long) getBalance.invoke(balanceApi, player);
            return new Result(Status.OK, after);
        } catch (Throwable throwable) {
            ShadowClanGaming.LOGGER.warn("EconomyCraft removeMoney failed: {}", throwable.toString());
            return new Result(Status.UNAVAILABLE, -1L);
        }
    }

    /**
     * Pays money INTO a player's balance. Used by the Auto-Trader when it sells items or converts FE.
     * Returns UNAVAILABLE when EconomyCraft is missing, REJECTED when the balance is already at
     * EconomyCraft's own maximum (their cap, not ours - we never impose one).
     */
    public static Result credit(MinecraftServer server, UUID player, long amount) {
        resolve();
        if (!available) {
            return new Result(Status.UNAVAILABLE, -1L);
        }
        if (amount <= 0L) {
            return new Result(Status.REJECTED, getBalance(server, player));
        }
        try {
            Object apiInstance = api(server);
            Object balanceApi = balances.invoke(apiInstance);
            Object result = addMoney.invoke(balanceApi, player, amount);
            boolean success = (boolean) result.getClass().getMethod("successful").invoke(result);
            long after = (long) getBalance.invoke(balanceApi, player);
            return new Result(success ? Status.OK : Status.REJECTED, after);
        } catch (Throwable throwable) {
            ShadowClanGaming.LOGGER.warn("EconomyCraft addMoney failed: {}", throwable.toString());
            return new Result(Status.UNAVAILABLE, -1L);
        }
    }

    /** Pretty-prints an amount using EconomyCraft's own formatting ($1.234 etc). */
    public static String format(MinecraftServer server, long amount) {
        resolve();
        if (!available) {
            return String.format("%,d", amount);
        }
        try {
            return (String) formatMoney.invoke(api(server), amount);
        } catch (Throwable throwable) {
            return String.format("%,d", amount);
        }
    }

    private EconomyCraftBalance() {
    }
}
