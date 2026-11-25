package com.shadowclangaming.compat.numismatics;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.compat.economy.MoneyChunks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.UUID;

/**
 * Talks to Create: Numismatics' bank without linking against its jar.
 *
 * WHY REFLECTION. Numismatics is LGPL-3.0 and its internals move between alpha releases. We ship no
 * reference to their classes, only the names below; if they rename something we log once and fall
 * back to physical coin loading, which always works.
 *
 * THE API WE USE (read from their 1.21.1 source, not guessed):
 *
 *   dev.ithundxr.createnumismatics.Numismatics
 *     static final GlobalBankManager BANK
 *
 *   ...content.backend.GlobalBankManager
 *     BankAccount getAccount(Player)          // creates the account if missing
 *     BankAccount getAccount(UUID)            // nullable
 *
 *   ...content.backend.BankAccount
 *     int  getBalance()                       // in spurs
 *     void deposit(int)
 *     boolean deduct(int, ReasonHolder)
 *     long additionalBalance                  // private: overflow past MAX_STANDARD_BALANCE
 *
 *   ...content.backend.ReasonHolder
 *     static final ReasonHolder IGNORED
 *
 *   ...content.bank.CardItem
 *     static UUID get(ItemStack)              // the account a card is bound to, or null
 *     static boolean isBound(ItemStack)
 *
 * BALANCE IS A long HERE. Their stored balance is an int plus a private long overflow, and the player's
 * instruction is that nothing caps what can be loaded into a warhead - so we read both parts and work
 * in long. Spending is chunked, because deduct()/deposit() take an int.
 *
 * SERVER THREAD ONLY. GetBalance/deduct touch world saved data; item interactions are already on the
 * server thread, so this is satisfied by construction.
 */
public final class NumismaticsBank {

    public static final String MODID = "numismatics";

    private static final String PACKAGE = "dev.ithundxr.createnumismatics";
    private static final String NUMISMATICS_CLASS = PACKAGE + ".Numismatics";
    private static final String MANAGER_CLASS = PACKAGE + ".content.backend.GlobalBankManager";
    private static final String ACCOUNT_CLASS = PACKAGE + ".content.backend.BankAccount";
    private static final String REASON_CLASS = PACKAGE + ".content.backend.ReasonHolder";
    private static final String CARD_CLASS = PACKAGE + ".content.bank.CardItem";

    private static boolean attempted;
    private static volatile boolean available;

    private static MethodHandle getBank;          // () -> GlobalBankManager
    private static MethodHandle accountOfPlayer;  // (manager, Player) -> BankAccount
    private static MethodHandle accountOfUuid;    // (manager, UUID)   -> BankAccount (nullable)
    private static MethodHandle getBalance;       // (account) -> int
    private static MethodHandle deposit;          // (account, int) -> void
    private static MethodHandle deduct;           // (account, int, ReasonHolder) -> boolean
    private static Object ignoredReason;          // ReasonHolder.IGNORED
    private static MethodHandle cardAccount;      // (ItemStack) -> UUID (nullable)
    private static MethodHandle cardBound;        // (ItemStack) -> boolean

    private NumismaticsBank() {
    }

    private static synchronized void resolve() {
        if (attempted) {
            return;
        }
        attempted = true;
        if (!ModList.get().isLoaded(MODID)) {
            return;
        }
        try {
            Class<?> numismatics = Class.forName(NUMISMATICS_CLASS);
            Class<?> manager = Class.forName(MANAGER_CLASS);
            Class<?> account = Class.forName(ACCOUNT_CLASS);
            Class<?> reason = Class.forName(REASON_CLASS);
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();

            getBank = lookup.findStaticGetter(numismatics, "BANK", manager);
            accountOfPlayer = lookup.findVirtual(manager, "getAccount",
                    MethodType.methodType(account, Player.class));
            accountOfUuid = lookup.findVirtual(manager, "getAccount",
                    MethodType.methodType(account, UUID.class));
            getBalance = lookup.findVirtual(account, "getBalance", MethodType.methodType(int.class));
            deposit = lookup.findVirtual(account, "deposit", MethodType.methodType(void.class, int.class));
            deduct = lookup.findVirtual(account, "deduct",
                    MethodType.methodType(boolean.class, int.class, reason));
            ignoredReason = lookup.findStaticGetter(reason, "IGNORED", reason).invoke();

            // Optional: the card helpers. Their absence must not disable balances.
            try {
                Class<?> card = Class.forName(CARD_CLASS);
                cardAccount = lookup.findStatic(card, "get", MethodType.methodType(UUID.class, ItemStack.class));
                cardBound = lookup.findStatic(card, "isBound", MethodType.methodType(boolean.class, ItemStack.class));
            } catch (Throwable ignored) {
                cardAccount = null;
                cardBound = null;
            }

            available = true;
            ShadowClanGaming.LOGGER.info("Create: Numismatics bank found - Bank Cards draw from bank accounts.");
        } catch (Throwable throwable) {
            available = false;
            ShadowClanGaming.LOGGER.warn("Create: Numismatics bank API not usable ({}); coins still work.",
                    throwable.toString());
        }
    }

    public static boolean isAvailable() {
        resolve();
        return available;
    }

    /** The account a Numismatics card in this stack is bound to, or null if it is not a bound card. */
    public static UUID cardAccountOf(ItemStack stack) {
        resolve();
        if (!available || cardAccount == null || stack == null || stack.isEmpty()) {
            return null;
        }
        try {
            return (UUID) cardAccount.invoke(stack);
        } catch (Throwable throwable) {
            return null;
        }
    }

    /** Is this stack any Numismatics card, bound or not? Used by the launch pad interaction. */
    public static boolean isCard(ItemStack stack) {
        resolve();
        if (!available || cardBound == null || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            return (boolean) cardBound.invoke(stack);
        } catch (Throwable throwable) {
            return false;
        }
    }

    /**
     * The account UUID to bill: the card's own account when one is held, otherwise the player's.
     *
     * A card can be bound to somebody else's account (a shared base account, a Blaze Banker). Honouring
     * that is what makes a "company card" mean something.
     */
    public static UUID accountIdFor(Player player, ItemStack held) {
        UUID fromCard = cardAccountOf(held);
        return fromCard != null ? fromCard : player.getUUID();
    }

    /** Balance of an account in spurs, including their private long overflow. -1 if unknown. */
    public static long balanceOf(UUID accountId) {
        resolve();
        if (!available || accountId == null) {
            return -1L;
        }
        try {
            Object account = accountOfUuid.invoke(getBank.invoke(), accountId);
            if (account == null) {
                return 0L; // never created: no money yet
            }
            long base = (long) (int) getBalance.invoke(account);
            return base + overflowOf(account);
        } catch (Throwable throwable) {
            ShadowClanGaming.LOGGER.debug("Numismatics balance read failed: {}", throwable.toString());
            return -1L;
        }
    }

    /** Balance of the account a player owns. Creates it if this is their first use. */
    public static long balanceOf(Player player) {
        resolve();
        if (!available || player == null) {
            return -1L;
        }
        try {
            Object account = accountOfPlayer.invoke(getBank.invoke(), player);
            return (long) (int) getBalance.invoke(account) + overflowOf(account);
        } catch (Throwable throwable) {
            return -1L;
        }
    }

    /**
     * Takes {@code amount} spurs from an account.
     *
     * @return true only if the whole amount was taken - deduct() is called with the exact amount once,
     *         so a partial withdrawal is impossible
     */
    public static boolean spend(UUID accountId, long amount) {
        resolve();
        if (!available || accountId == null || amount <= 0L) {
            return false;
        }
        try {
            Object account = accountOfUuid.invoke(getBank.invoke(), accountId);
            if (account == null) {
                return false;
            }
            long available = (long) (int) getBalance.invoke(account) + overflowOf(account);
            if (available < amount || !MoneyChunks.transactable(amount)) {
                return false;
            }
            for (int chunk : chunks(amount)) {
                Object ok = deduct.invoke(account, chunk, ignoredReason);
                if (!(boolean) ok) {
                    return false;
                }
            }
            return true;
        } catch (Throwable throwable) {
            ShadowClanGaming.LOGGER.warn("Numismatics deduct failed: {}", throwable.toString());
            return false;
        }
    }

    /** Pays money into an account (used by the Auto-Trader when it sells). */
    public static boolean credit(UUID accountId, long amount) {
        resolve();
        if (!available || accountId == null || amount <= 0L) {
            return false;
        }
        try {
            Object account = accountOfUuid.invoke(getBank.invoke(), accountId);
            if (account == null) {
                return false;
            }
            for (int chunk : chunks(amount)) {
                deposit.invoke(account, chunk);
            }
            return true;
        } catch (Throwable throwable) {
            ShadowClanGaming.LOGGER.warn("Numismatics deposit failed: {}", throwable.toString());
            return false;
        }
    }

    /**
     * Their deposit()/deduct() take an int; our amounts are longs.
     *
     * Lazy on purpose (see {@link MoneyChunks}): an array here would allocate billions of entries for
     * an absurd amount. The transaction is refused outright if it would need more than MAX_CHUNKS
     * calls, rather than freezing the tick.
     */
    private static Iterable<Integer> chunks(long amount) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        if (!MoneyChunks.transactable(amount)) {
            return out;
        }
        for (long part : MoneyChunks.of(amount)) {
            out.add((int) part);
        }
        return out;
    }

    /** Their private overflow field, or 0 when it is absent (older/newer builds). */
    private static long overflowOf(Object account) {
        try {
            java.lang.reflect.Field field = account.getClass().getDeclaredField("additionalBalance");
            field.setAccessible(true);
            Object value = field.get(account);
            return value instanceof Number number ? number.longValue() : 0L;
        } catch (Throwable ignored) {
            return 0L;
        }
    }
}
