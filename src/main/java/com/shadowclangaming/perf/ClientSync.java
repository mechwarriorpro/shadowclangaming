package com.shadowclangaming.perf;

import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Sends bulk terrain edits to the client.
 *
 * ──────────────────── WHY THIS CLASS EXISTS ────────────────────
 *
 * {@link SectionScrubber} writes straight into a chunk's palette instead of calling
 * {@code Level.setBlock}, because that is the only way a continent-scale crater is possible at all. The
 * price is that nothing tells the client: vanilla only ships a block change when {@code setBlock} says
 * so. The result is the classic "ghost block" world - the server knows the ground is gone, the player
 * still stands on it and sees it, and only a relog fixes it.
 *
 * The fix is not "call setBlock for 20 billion blocks". It is to resend the whole CHUNK to the players
 * who can see it, once per changed chunk. One ~30 KB packet replaces 4,096 block-change packets, and the
 * client's own rendering picks up the new palette, the new light and the new heightmaps in one go.
 *
 * Resends are queued, not sent inline: the light engine recalculates a section's light at the end of the
 * tick, so a packet built in the same tick carries stale light (a crater that is still bright). One tick
 * of latency is invisible and gets correct lighting.
 */
public final class ClientSync {

    /**
     * Chunks waiting to be resent, with the number of ticks they have waited.
     *
     * The one-tick delay is not padding: the light engine recalculates a section's light during the
     * chunk tick, so a packet built in the same tick as the edit would arrive with the crater still
     * lit. One tick later the light is right and the player sees a dark hole instead of a glowing one.
     */
    private static final java.util.Map<Key, Integer> PENDING = new java.util.LinkedHashMap<>();
    private static final int MIN_AGE_TICKS = 1;

    private record Key(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                       long packed) {
    }

    private ClientSync() {
    }

    /** Queue a chunk for a full resend. Cheap: the packet is built on the next tick. */
    public static void markDirty(ServerLevel level, ChunkPos pos) {
        PENDING.putIfAbsent(new Key(level.dimension(), pos.toLong()), 0);
    }

    /**
     * Recompute the heightmaps a crater invalidates. Without this, mobs spawn in mid-air over the hole
     * and rain never falls into it, because the client-sided and server-sided heightmaps still describe
     * the pre-blast terrain.
     */
    public static void primeHeightmaps(LevelChunk chunk) {
        Heightmap.primeHeightmaps(chunk, EnumSet.of(
                Heightmap.Types.WORLD_SURFACE,
                Heightmap.Types.MOTION_BLOCKING,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                Heightmap.Types.OCEAN_FLOOR));
    }

    /** Called once per server tick from the mod's tick listener. Sends everything queued. */
    public static void flush(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        java.util.List<Key> ready = new java.util.ArrayList<>();
        var iterator = PENDING.entrySet().iterator();
        while (iterator.hasNext()) {
            var entry = iterator.next();
            int age = entry.getValue() + 1;
            if (age >= MIN_AGE_TICKS) {
                ready.add(entry.getKey());
                iterator.remove();
            } else {
                entry.setValue(age);
            }
        }
        for (Key key : ready) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            ChunkPos pos = new ChunkPos(key.packed());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                continue; // unloaded before we got to it - it will be scrubbed again when it reloads
            }
            ClientboundLevelChunkWithLightPacket packet = new ClientboundLevelChunkWithLightPacket(
                    chunk, level.getLightEngine(), null, null);
            for (ServerPlayer player : level.getChunkSource().chunkMap.getPlayers(pos, false)) {
                player.connection.send(packet);
            }
        }
    }

    /** Drops any queued work for a level that is going away (server stop, dimension unload). */
    public static void clear() {
        PENDING.clear();
    }

    /** @return how many chunks are waiting, for the perf log. */
    public static int pending() {
        return PENDING.size();
    }
}
