package com.shadowclangaming.compat.economy;

import com.shadowclangaming.compat.numismatics.NumismaticsBank;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.UUID;

/**
 * One place that answers "whose money is this, and how much of it is there?"
 *
 * THE BUG THIS REPLACES. The Bank Card used to call EconomyCraft directly and print "EconomyCraft API
 * not available" whenever EconomyCraft was absent - which is exactly what happens on a Numismatics
 * server. The currency mod a player is actually running is not something we get to pick, so the card
 * now asks this class instead, and this class asks every backend it can find:
 *
 *   1. EconomyCraft  - a virtual balance per player UUID
 *   2. Numismatics   - a bank account per player, plus card-bound accounts (Blaze Banker, shared bases)
 *
 * Coins in the inventory are the third path and are unchanged: they never needed an API.
 *
 * Everything here is safe to call when neither mod is installed; it answers "none" and the caller
 * falls back to item loading.
 */
public final class Money {

    /** Which wallet answered. */
    public enum Backend {
        ECONOMYCRAFT("EconomyCraft"),
        NUMISMATICS("Create: Numismatics"),
        NONE("none");

        public final String label;

        Backend(String label) {
            this.label = label;
        }
    }

    private Money() {
    }

    /** The backend that will be used for this player, in priority order. */
    public static Backend backend() {
        if (EconomyCraftBalance.isAvailable()) {
            return Backend.ECONOMYCRAFT;
        }
        if (NumismaticsBank.isAvailable()) {
            return Backend.NUMISMATICS;
        }
        return Backend.NONE;
    }

    public static boolean available() {
        return backend() != Backend.NONE;
    }

    /**
     * The player's spendable balance.
     *
     * With a Numismatics card in {@code held}, this is the account the CARD is bound to - so a shared
     * Blaze Banker account pays, not the holder's pocket.
     *
     * @return the balance, or -1 when no backend is available
     */
    public static long balance(MinecraftServer server, Player player, ItemStack held) {
        return switch (backend()) {
            case ECONOMYCRAFT -> EconomyCraftBalance.getBalance(server, player.getUUID());
            case NUMISMATICS -> {
                UUID account = NumismaticsBank.accountIdFor(player, held);
                yield NumismaticsBank.cardAccountOf(held) != null
                        ? NumismaticsBank.balanceOf(account)
                        : NumismaticsBank.balanceOf(player);
            }
            case NONE -> -1L;
        };
    }

    /** Same as {@link #balance} for code that has no item in hand. */
    public static long balance(MinecraftServer server, Player player) {
        return balance(server, player, player.getMainHandItem());
    }

    // ------------------------------------------------------------------ by account UUID
    //
    // The Auto-Trader acts on behalf of a card's OWNER, who may be offline, so it needs the raw
    // account path. Both backends are UUID-addressed, so this stays simple.

    public static long balance(MinecraftServer server, UUID accountId) {
        return switch (backend()) {
            case ECONOMYCRAFT -> EconomyCraftBalance.getBalance(server, accountId);
            case NUMISMATICS -> NumismaticsBank.balanceOf(accountId);
            case NONE -> -1L;
        };
    }

    public static boolean spend(MinecraftServer server, UUID accountId, long amount) {
        if (amount <= 0L) {
            return false;
        }
        return switch (backend()) {
            case ECONOMYCRAFT -> EconomyCraftBalance.spend(server, accountId, amount).ok();
            case NUMISMATICS -> NumismaticsBank.spend(accountId, amount);
            case NONE -> false;
        };
    }

    public static boolean credit(MinecraftServer server, UUID accountId, long amount) {
        if (amount <= 0L) {
            return false;
        }
        return switch (backend()) {
            case ECONOMYCRAFT -> EconomyCraftBalance.credit(server, accountId, amount).ok();
            case NUMISMATICS -> NumismaticsBank.credit(accountId, amount);
            case NONE -> false;
        };
    }

    /**
     * Spend {@code amount}, never partially.
     *
     * @return true only when the whole amount left the account
     */
    public static boolean spend(MinecraftServer server, Player player, ItemStack held, long amount) {
        if (amount <= 0L) {
            return false;
        }
        return switch (backend()) {
            case ECONOMYCRAFT -> EconomyCraftBalance.spend(server, player.getUUID(), amount).ok();
            case NUMISMATICS -> NumismaticsBank.spend(NumismaticsBank.accountIdFor(player, held), amount);
            case NONE -> false;
        };
    }

    /** Pay money in. Used by the Auto-Trader. */
    public static boolean credit(MinecraftServer server, Player player, ItemStack held, long amount) {
        if (amount <= 0L) {
            return false;
        }
        return switch (backend()) {
            case ECONOMYCRAFT -> EconomyCraftBalance.credit(server, player.getUUID(), amount).ok();
            case NUMISMATICS -> NumismaticsBank.credit(NumismaticsBank.accountIdFor(player, held), amount);
            case NONE -> false;
        };
    }

    /** Pretty-print an amount. Falls back to plain thousands separators. */
    public static String format(MinecraftServer server, long amount) {
        if (backend() == Backend.ECONOMYCRAFT) {
            return EconomyCraftBalance.format(server, amount);
        }
        return String.format("%,d", amount);
    }

    /** A short line naming the backend, for tooltips and chat. */
    public static String label() {
        return backend().label;
    }
}
