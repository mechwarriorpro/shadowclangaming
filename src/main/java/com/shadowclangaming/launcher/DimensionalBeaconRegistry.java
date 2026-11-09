package com.shadowclangaming.launcher;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

import java.util.*;

/**
 * All placed Dimensional Beacons, keyed by dimension. Used for control panel's `beacons` command
 * and for missile fallback targeting when coordinates are vague. Persistent via saved data on server start
 * is not needed — beacons re-register on chunk load via block's onPlace/onLoad.
 */
public final class DimensionalBeaconRegistry {
    private static final Map<ResourceKey<Level>, Set<GlobalPos>> BEACONS = new HashMap<>();

    private DimensionalBeaconRegistry() {}

    public static void register(ServerLevel level, BlockPos pos) {
        BEACONS.computeIfAbsent(level.dimension(), k -> new HashSet<>())
                .add(GlobalPos.of(level.dimension(), pos));
    }
    public static void unregister(ServerLevel level, BlockPos pos) {
        Set<GlobalPos> s = BEACONS.get(level.dimension());
        if (s != null) s.remove(GlobalPos.of(level.dimension(), pos));
    }
    public static List<GlobalPos> all(MinecraftServer server) {
        List<GlobalPos> out = new ArrayList<>();
        for (var e : BEACONS.values()) out.addAll(e);
        return out;
    }
    public static List<GlobalPos> in(ResourceKey<Level> dim) {
        return List.copyOf(BEACONS.getOrDefault(dim, Set.of()));
    }
    public static Optional<GlobalPos> nearest(ServerLevel origin, ResourceKey<Level> dim, BlockPos near) {
        var list = in(dim);
        if (list.isEmpty()) return Optional.empty();
        return list.stream().min(Comparator.comparingDouble(p -> p.pos().distSqr(near)));
    }
    public static void clear() { BEACONS.clear(); }
}
