package com.shadowclangaming.economy;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * How many currency units each player has ever converted out of FORGE ENERGY.
 *
 * This is the anti-exploit core of the Auto-Trader: the curve in {@link PowerExchange} is keyed to
 * the player, not to the block. Ten Auto-Traders do not give you ten cheap curves, they give you one
 * curve that ten blocks now have to share - which is exactly right, because the money all lands in
 * the same bank account.
 *
 * Stored in the overworld's saved data so it survives death, dimension changes and block breaking.
 * Item sales are NOT tracked here: they are limited by what the player can actually farm, so the
 * cube would just be punishing honest trading.
 */
public final class ExchangeLedger extends SavedData {

    private static final String FILE_ID = "shadowclangaming_exchange";

    private final Map<UUID, Long> powerUnits = new HashMap<>();

    public static ExchangeLedger get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ExchangeLedger::new, ExchangeLedger::load),
                FILE_ID);
    }

    public static ExchangeLedger load(CompoundTag tag, HolderLookup.Provider registries) {
        ExchangeLedger data = new ExchangeLedger();
        CompoundTag units = tag.getCompound("PowerUnits");
        for (String key : units.getAllKeys()) {
            try {
                data.powerUnits.put(UUID.fromString(key), units.getLong(key));
            } catch (IllegalArgumentException ignored) {
                // A corrupt or hand-edited entry must never stop the world from loading.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        CompoundTag units = new CompoundTag();
        powerUnits.forEach((player, count) -> units.putLong(player.toString(), count));
        tag.put("PowerUnits", units);
        return tag;
    }

    public long unitsCredited(UUID player) {
        return powerUnits.getOrDefault(player, 0L);
    }

    public void addUnits(UUID player, long units) {
        if (units <= 0L) {
            return;
        }
        powerUnits.merge(player, units, (old, extra) -> {
            long sum = old + extra;
            return sum < 0L ? Long.MAX_VALUE : sum;
        });
        setDirty();
    }

    /** @return true if the player had any recorded conversions. */
    public boolean reset(UUID player) {
        boolean removed = powerUnits.remove(player) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public int resetAll() {
        int count = powerUnits.size();
        powerUnits.clear();
        setDirty();
        return count;
    }
}
