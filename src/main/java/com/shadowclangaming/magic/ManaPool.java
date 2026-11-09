package com.shadowclangaming.magic;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Every player's mana, in one place.
 *
 * PARTS OF THE MAXIMUM:
 *   base            100            everyone starts here
 *   + growth        permanent      earned by casting (see ManaApi.onSpellCast)
 *   + enchantment   dynamic        the Mana Capacity enchantment, recomputed every read
 *
 * Only the first two are stored here. The enchantment bonus is deliberately NOT stored: it is derived
 * from what the player is wearing right now, so taking the boots off immediately costs you the mana
 * capacity they gave you. Storing it would let players wear the boots once and keep the bonus.
 *
 * THERE IS NO NATURAL REGENERATION. Mana comes back from exactly three places (see ManaSources and
 * ChalkCircleBlockEntity):
 *   1. standing near amethyst            - very slow, free, always available
 *   2. standing in the middle of a chalk circle - scales with the circle's size
 *   3. a blood pact                      - a huge supply anywhere in the world, but it costs
 *                                          sacrifices to keep alive, and it permanently locks the
 *                                          player out of chalk circles
 *
 * This account also carries the pact state and the ritual progress, because both are per player and
 * both need to survive a restart.
 */
public final class ManaPool extends SavedData {

    private static final String FILE_ID = "shadowclangaming_mana";

    /** Mana every player has before anything else. */
    public static final long BASE_CAPACITY = 100L;

    /** One account. Mutable on purpose: reading a player's mana updates it. */
    public static final class Account {
        private long mana;
        private long bonusCapacity;
        private long lastRegenTick;
        private long casts;

        /** Chosen the bloody path. Permanent; chalk circles will never work for this player again. */
        private boolean evil;
        /** Game tick the blood pact runs out. Mana flows from it anywhere in the world until then. */
        private long pactExpiresAt;
        /** Which of the three ritual victims have been sacrificed this full moon (see Sacrifices). */
        private int ritualMask;
        /** Game tick the ritual progress goes stale. */
        private long ritualExpiresAt;

        /** Creeping Evil level. Starts at 1 the moment the pact is formed, and only ever goes up. */
        private int evilLevel;
        /** Ticks left in the current Creeping Evil level (6 hours each). */
        private long evilTicks;

        public long mana() {
            return mana;
        }

        public long bonusCapacity() {
            return bonusCapacity;
        }

        public long casts() {
            return casts;
        }

        public long lastRegenTick() {
            return lastRegenTick;
        }

        public boolean evil() {
            return evil;
        }

        public long pactExpiresAt() {
            return pactExpiresAt;
        }

        public int ritualMask() {
            return ritualMask;
        }

        public long ritualExpiresAt() {
            return ritualExpiresAt;
        }

        public int evilLevel() {
            return evilLevel;
        }

        public long evilTicks() {
            return evilTicks;
        }
    }

    private final Map<UUID, Account> accounts = new HashMap<>();

    public static ManaPool get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ManaPool::new, ManaPool::load),
                FILE_ID);
    }

    public static ManaPool load(CompoundTag tag, HolderLookup.Provider registries) {
        ManaPool data = new ManaPool();
        CompoundTag accountsTag = tag.getCompound("Accounts");
        for (String key : accountsTag.getAllKeys()) {
            try {
                UUID id = UUID.fromString(key);
                CompoundTag entry = accountsTag.getCompound(key);
                Account account = new Account();
                account.mana = entry.getLong("Mana");
                account.bonusCapacity = entry.getLong("BonusCapacity");
                account.lastRegenTick = entry.getLong("LastRegen");
                account.casts = entry.getLong("Casts");
                account.evil = entry.getBoolean("Evil");
                account.pactExpiresAt = entry.getLong("PactExpires");
                account.ritualMask = entry.getInt("RitualMask");
                account.ritualExpiresAt = entry.getLong("RitualExpires");
                account.evilLevel = entry.getInt("EvilLevel");
                account.evilTicks = entry.getLong("EvilTicks");
                data.accounts.put(id, account);
            } catch (IllegalArgumentException ignored) {
                // corrupt or hand-edited entry: skip, never refuse to load the world
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag accountsTag = new CompoundTag();
        accounts.forEach((id, account) -> {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Mana", account.mana);
            entry.putLong("BonusCapacity", account.bonusCapacity);
            entry.putLong("LastRegen", account.lastRegenTick);
            entry.putLong("Casts", account.casts);
            entry.putBoolean("Evil", account.evil);
            entry.putLong("PactExpires", account.pactExpiresAt);
            entry.putInt("RitualMask", account.ritualMask);
            entry.putLong("RitualExpires", account.ritualExpiresAt);
            entry.putInt("EvilLevel", account.evilLevel);
            entry.putLong("EvilTicks", account.evilTicks);
            accountsTag.put(id.toString(), entry);
        });
        tag.put("Accounts", accountsTag);
        return tag;
    }

    public Account account(UUID player) {
        return accounts.computeIfAbsent(player, id -> new Account());
    }

    public boolean hasAccount(UUID player) {
        return accounts.containsKey(player);
    }

    public int size() {
        return accounts.size();
    }

    public void markUsed(UUID player) {
        account(player);
        setDirty();
    }

    public boolean reset(UUID player) {
        boolean removed = accounts.remove(player) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    // ------------------------------------------------------------------ helpers used by ManaApi

    void setMana(UUID player, long mana) {
        account(player).mana = Math.max(0L, mana);
        setDirty();
    }

    void setBonusCapacity(UUID player, long bonus) {
        account(player).bonusCapacity = Math.max(0L, bonus);
        setDirty();
    }

    void setLastRegenTick(UUID player, long tick) {
        account(player).lastRegenTick = tick;
        setDirty();
    }

    void setEvil(UUID player, boolean evil) {
        account(player).evil = evil;
        setDirty();
    }

    void setPactExpiresAt(UUID player, long tick) {
        account(player).pactExpiresAt = tick;
        setDirty();
    }

    void setCurse(UUID player, int level, long ticks) {
        Account account = account(player);
        account.evilLevel = Math.max(0, level);
        account.evilTicks = Math.max(0L, ticks);
        setDirty();
    }

    void setRitual(UUID player, int mask, long expiresAt) {
        Account account = account(player);
        account.ritualMask = mask;
        account.ritualExpiresAt = expiresAt;
        setDirty();
    }

    void recordCast(UUID player) {
        account(player).casts++;
        setDirty();
    }
}
