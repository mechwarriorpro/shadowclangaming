package com.shadowclangaming.magic;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

import java.util.UUID;

/**
 * The one door to mana. Spells, rifles, the inscription table and the Auto-Trader all go through
 * here rather than poking {@link ManaPool} directly, so that the three sources of maximum mana are
 * applied consistently in one place:
 *
 *      maximum = 100 base + permanent growth from casting + (Mana Capacity enchantment level x 25)
 *
 * Everything is long. A player who has been casting for a month can hold mana values that would
 * overflow an int, and there is no balance reason to stop them - the cost of a spell scales with its
 * component count, so the only thing a huge pool buys is a huge spell.
 */
public final class ManaApi {

    public static final ResourceKey<Enchantment> MANA_CAPACITY = ResourceKey.create(
            Registries.ENCHANTMENT,
            ResourceLocation.fromNamespaceAndPath("shadowclangaming", "mana_capacity"));

    /** Extra maximum mana per level of the Mana Capacity enchantment. */
    public static final long CAPACITY_PER_ENCHANT_LEVEL = 25L;

    /** Casting growth: a flat point per cast, plus more the more complex the spell was. */
    public static final long GROWTH_PER_CAST = 1L;
    public static final long GROWTH_PER_COMPLEXITY = 1L;
    public static final long GROWTH_COMPLEXITY_CAP = 9L;

    // ------------------------------------------------------------------ reads

    /** Maximum mana, ignoring the enchantment. Safe to call from inside settle(). */
    private static long rawCapacity(ServerLevel level, UUID player) {
        ManaPool pool = ManaPool.get(level);
        return ManaPool.BASE_CAPACITY + pool.account(player).bonusCapacity();
    }

    /** Bonus from the Mana Capacity enchantment, summed over everything the player is wearing. */
    public static long enchantBonus(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        Holder<Enchantment> holder = level.registryAccess()
                .registryOrThrow(Registries.ENCHANTMENT)
                .getHolder(MANA_CAPACITY)
                .orElse(null);
        if (holder == null) {
            return 0L;
        }
        int enchantLevel = EnchantmentHelper.getEnchantmentLevel(holder, player);
        return enchantLevel * CAPACITY_PER_ENCHANT_LEVEL;
    }

    public static long capacity(ServerPlayer player) {
        settle(player);
        return rawCapacity(player.serverLevel(), player.getUUID()) + enchantBonus(player);
    }

    public static long current(ServerPlayer player) {
        settle(player);
        return ManaPool.get(player.serverLevel()).account(player.getUUID()).mana();
    }

    // ------------------------------------------------------------------ writes

    /** @return true if the player could pay. Never partially spends. */
    public static boolean spend(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return true;
        }
        settle(player);
        ServerLevel level = player.serverLevel();
        ManaPool pool = ManaPool.get(level);
        UUID id = player.getUUID();
        if (pool.account(id).mana() < amount) {
            return false;
        }
        pool.setMana(id, pool.account(id).mana() - amount);
        return true;
    }

    /** Refills to maximum. Used by the trader when the player's mana is not full. */
    public static long refill(ServerPlayer player) {
        settle(player);
        long capacity = capacity(player);
        ServerLevel level = player.serverLevel();
        ManaPool pool = ManaPool.get(level);
        long before = pool.account(player.getUUID()).mana();
        pool.setMana(player.getUUID(), capacity);
        return Math.max(0L, capacity - before);
    }

    /**
     * Adds up to {@code amount} mana, capped at the player's maximum.
     *
     * @return how much mana was LEFT OVER because the pool was full. This is what the Auto-Trader
     *         turns into permanent capacity growth, which is exactly the "increase max mana if full,
     *         refill it if not" rule.
     */
    public static long addMana(ServerPlayer player, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        settle(player);
        ServerLevel level = player.serverLevel();
        ManaPool pool = ManaPool.get(level);
        UUID id = player.getUUID();
        long capacity = rawCapacity(level, id) + enchantBonus(player);
        long room = Math.max(0L, capacity - pool.account(id).mana());
        long accepted = Math.min(room, amount);
        if (accepted > 0L) {
            pool.setMana(id, pool.account(id).mana() + accepted);
        }
        return amount - accepted;
    }

    /** Permanent maximum-mana growth. Works for offline players too. */
    public static void addCapacity(MinecraftServer server, UUID player, long amount) {
        if (amount <= 0L) {
            return;
        }
        ServerLevel level = server.overworld();
        ManaPool pool = ManaPool.get(level);
        pool.setBonusCapacity(player, pool.account(player).bonusCapacity() + amount);
    }

    // ------------------------------------------------------------------ admin: maximum manipulation
    //
    // Everything below exists for the /shadowclangaming mana add|remove|set|reset commands. The rule
    // they all follow: the number an admin types is MAXIMUM MANA - the same number the HUD and the
    // inscription table show - not "bonus growth". An admin who sets 5000 and then finds the player
    // capped at 5025 because of a necklace would rightly call that a bug, so the enchantment bonus is
    // folded into the arithmetic here and nowhere else.

    /** The permanent-growth part of a player's maximum: everything except the 100 base and gear. */
    public static long growth(MinecraftServer server, UUID player) {
        return ManaPool.get(server.overworld()).account(player).bonusCapacity();
    }

    /**
     * The growth figure that produces {@code maximum} for this player right now.
     *
     * Clamped at zero: a player wearing enough Mana Capacity gear already exceeds a small requested
     * maximum, and negative growth is not a thing the pool can store.
     */
    public static long growthFor(ServerPlayer player, long maximum) {
        long target = maximum - ManaPool.BASE_CAPACITY - enchantBonus(player);
        return Math.max(0L, target);
    }

    /** Set a player's growth directly. Does not touch what is currently in the pool. */
    public static void setGrowth(MinecraftServer server, UUID player, long growth) {
        ManaPool pool = ManaPool.get(server.overworld());
        pool.setBonusCapacity(player, Math.max(0L, growth));
    }

    /**
     * Bring an online player in line with their (possibly shrunken) maximum. {@code settle} already
     * does the clamping - this is just the public door to it, so a command can lower a maximum
     * without leaving a pool holding more mana than the player can have.
     */
    public static void clampToMaximum(ServerPlayer player) {
        settle(player);
    }

    /** Saturating add, so a careless amount cannot wrap a long into a negative maximum. */
    public static long addGrowthSaturating(long current, long amount) {
        if (amount <= 0L) {
            return current;
        }
        if (current > Long.MAX_VALUE - amount) {
            return Long.MAX_VALUE;
        }
        return current + amount;
    }

    /**
     * UUID-only variant used by machinery (the Auto-Trader) whose owner may be offline. Enchantment
     * bonuses are skipped because there is no player object to inspect, which is the correct
     * behaviour: an offline player's gear is not contributing to anything.
     */
    public static long addManaFor(MinecraftServer server, UUID player, long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        ServerLevel level = server.overworld();
        ManaPool pool = ManaPool.get(level);
        long capacity = ManaPool.BASE_CAPACITY + pool.account(player).bonusCapacity();
        long room = Math.max(0L, capacity - pool.account(player).mana());
        long accepted = Math.min(room, amount);
        if (accepted > 0L) {
            pool.setMana(player, pool.account(player).mana() + accepted);
        }
        return amount - accepted;
    }

    public static long currentFor(MinecraftServer server, UUID player) {
        ServerLevel level = server.overworld();
        return ManaPool.get(level).account(player).mana();
    }

    public static long capacityFor(MinecraftServer server, UUID player) {
        ServerLevel level = server.overworld();
        ManaPool pool = ManaPool.get(level);
        return ManaPool.BASE_CAPACITY + pool.account(player).bonusCapacity();
    }

    /**
     * Called after a spell actually goes off. Casting is how a wizard grows: every cast adds a little
     * permanent maximum mana, and complex spells add more, so the ceiling a player reaches is a
     * record of what they have actually cast rather than what they have farmed.
     */
    public static void onSpellCast(ServerPlayer player, int complexity) {
        long growth = GROWTH_PER_CAST
                + Math.min(GROWTH_COMPLEXITY_CAP, Math.max(0, complexity) * GROWTH_PER_COMPLEXITY);
        ManaPool.get(player.serverLevel()).recordCast(player.getUUID());
        addCapacity(player.serverLevel().getServer(), player.getUUID(), growth);
    }

    // ------------------------------------------------------------------ lazy regeneration

    /**
     * Brings an account up to date. NOT regeneration - mana does not come back on its own any more.
     * This only initialises a brand new account (full pool) and clamps a pool that has shrunk because
     * the Mana Capacity gear came off.
     */
    private static void settle(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        ManaPool pool = ManaPool.get(level);
        UUID id = player.getUUID();
        ManaPool.Account account = pool.account(id);

        long capacity = Math.max(0L, rawCapacity(level, id) + enchantBonus(player));

        if (account.lastRegenTick() <= 0L) {
            // First time we have ever seen this player: start full.
            pool.setMana(id, capacity);
            pool.setLastRegenTick(id, level.getGameTime());
            return;
        }
        if (account.mana() > capacity) {
            pool.setMana(id, capacity);
        }
    }

    // ------------------------------------------------------------------ the blood pact and the chalk ban

    /** @return true if this player took the sacrifice route. Permanent - it is the price of the pact. */
    public static boolean isEvil(MinecraftServer server, UUID player) {
        return ManaPool.get(server.overworld()).account(player).evil();
    }

    public static void markEvil(MinecraftServer server, UUID player) {
        markEvil(server, player, true);
    }

    public static void markEvil(MinecraftServer server, UUID player, boolean evil) {
        ManaPool.get(server.overworld()).setEvil(player, evil);
    }

    public static long pactExpiresAt(MinecraftServer server, UUID player) {
        return ManaPool.get(server.overworld()).account(player).pactExpiresAt();
    }

    public static boolean hasPact(MinecraftServer server, UUID player, long now) {
        return pactExpiresAt(server, player) > now;
    }

    public static void extendPact(MinecraftServer server, UUID player, long ticks, long now) {
        ManaPool pool = ManaPool.get(server.overworld());
        long until = Math.max(pool.account(player).pactExpiresAt(), now);
        pool.setPactExpiresAt(player, until + ticks);
    }

    /** Clears the pact without touching the evil flag: the player is still barred from chalk. */
    public static void endPact(MinecraftServer server, UUID player) {
        ManaPool.get(server.overworld()).setPactExpiresAt(player, 0L);
    }

    // ---- Creeping Evil state (see CreepingEvil) ----

    public static int evilLevel(MinecraftServer server, UUID player) {
        return ManaPool.get(server.overworld()).account(player).evilLevel();
    }

    public static long evilTicks(MinecraftServer server, UUID player) {
        return ManaPool.get(server.overworld()).account(player).evilTicks();
    }

    public static void setCurse(MinecraftServer server, UUID player, int level, long ticks) {
        ManaPool.get(server.overworld()).setCurse(player, level, ticks);
    }

    public static int ritualMask(MinecraftServer server, UUID player) {
        return ManaPool.get(server.overworld()).account(player).ritualMask();
    }

    public static long ritualExpiresAt(MinecraftServer server, UUID player) {
        return ManaPool.get(server.overworld()).account(player).ritualExpiresAt();
    }

    public static void setRitual(MinecraftServer server, UUID player, int mask, long expiresAt) {
        ManaPool.get(server.overworld()).setRitual(player, mask, expiresAt);
    }

    /**
     * Adds mana from an external source, capped at capacity, discarding any overflow.
     *
     * This is the choke point for every passive regen source (amethyst, chalk circles, the pact), which
     * is why the Holy Necklace is checked here: carrying it blocks regeneration completely. Mana that
     * arrives from machinery (the Auto-Trader) does not come through this method - see
     * AutoTraderBlockEntity for the equivalent check.
     */
    public static long grantMana(ServerPlayer player, long amount) {
        if (CreepingEvil.carriesNecklace(player)) {
            return 0L;
        }
        return amount - addMana(player, amount);
    }

    private ManaApi() {
    }
}
