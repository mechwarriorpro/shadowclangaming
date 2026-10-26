package com.shadowclangaming.computer;

import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Every launch pad that has been given a frequency, and which frequency it is on.
 *
 * A terminal does not scan the world for pads - that would mean chunk-loading half a base to answer
 * "what is on channel 7". Instead a pad registers itself here when a Frequency Chip is used on it, and
 * the terminal looks up the channel. Lookups are O(1) and the result is stable across restarts.
 *
 * Dead pads are pruned on read: if the block there is no longer a pad, it is dropped from the channel.
 */
public final class FrequencyRegistry extends SavedData {

    private static final String FILE_ID = "shadowclangaming_frequencies";

    /** Frequency -> the pads listening on it. */
    private final Map<Integer, Set<GlobalPos>> channels = new HashMap<>();

    public static FrequencyRegistry get(MinecraftServer server) {
        ServerLevel storage = server.overworld();
        return storage.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(FrequencyRegistry::new, FrequencyRegistry::load),
                FILE_ID);
    }

    public void bind(int frequency, GlobalPos pos) {
        // A pad can only be on one channel: remove it from any other first.
        for (Set<GlobalPos> pads : channels.values()) {
            pads.remove(pos);
        }
        channels.computeIfAbsent(frequency, key -> new HashSet<>()).add(pos);
        setDirty();
    }

    public void unbind(GlobalPos pos) {
        for (Set<GlobalPos> pads : channels.values()) {
            pads.remove(pos);
        }
        setDirty();
    }

    /** Live pads on a frequency, in the given dimension only. */
    public Set<GlobalPos> pads(int frequency, net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension) {
        Set<GlobalPos> all = channels.getOrDefault(frequency, Set.of());
        Set<GlobalPos> live = new HashSet<>();
        for (GlobalPos pos : all) {
            if (!pos.dimension().equals(dimension)) {
                continue;
            }
            live.add(pos);
        }
        return live;
    }

    public int frequencyOf(GlobalPos pos) {
        for (Map.Entry<Integer, Set<GlobalPos>> entry : channels.entrySet()) {
            if (entry.getValue().contains(pos)) {
                return entry.getKey();
            }
        }
        return -1;
    }

    // ------------------------------------------------------------------ persistence

    public static FrequencyRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        FrequencyRegistry registry = new FrequencyRegistry();
        ListTag channels = tag.getList("Channels", Tag.TAG_COMPOUND);
        for (int i = 0; i < channels.size(); i++) {
            CompoundTag entry = channels.getCompound(i);
            int frequency = entry.getInt("Frequency");
            ListTag pads = entry.getList("Pads", Tag.TAG_COMPOUND);
            Set<GlobalPos> set = new HashSet<>();
            for (int p = 0; p < pads.size(); p++) {
                CompoundTag pad = pads.getCompound(p);
                set.add(GlobalPos.of(
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION,
                                net.minecraft.resources.ResourceLocation.parse(pad.getString("Dimension"))),
                        net.minecraft.core.BlockPos.of(pad.getLong("Pos"))));
            }
            registry.channels.put(frequency, set);
        }
        return registry;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag channelList = new ListTag();
        for (Map.Entry<Integer, Set<GlobalPos>> entry : channels.entrySet()) {
            if (entry.getValue().isEmpty()) {
                continue;
            }
            CompoundTag channel = new CompoundTag();
            channel.putInt("Frequency", entry.getKey());
            ListTag pads = new ListTag();
            for (GlobalPos pos : entry.getValue()) {
                CompoundTag pad = new CompoundTag();
                pad.putString("Dimension", pos.dimension().location().toString());
                pad.putLong("Pos", pos.pos().asLong());
                pads.add(pad);
            }
            channel.put("Pads", pads);
            channelList.add(channel);
        }
        tag.put("Channels", channelList);
        return tag;
    }
}
