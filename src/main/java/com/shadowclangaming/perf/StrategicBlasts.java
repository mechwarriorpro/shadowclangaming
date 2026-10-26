package com.shadowclangaming.perf;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * "Strategic" (continent-scale) blasts: radius 256 to 4096.
 *
 * ──────────────────── THE HONEST DESIGN ────────────────────
 *
 * At radius 4096 the numbers stop being "an explosion" and start being "a world format operation":
 *
 *   radius   blocks (height-clamped)   chunk columns   region files
 *      96            3.7 million                 169             1
 *     256           79.1 million               1,089             4
 *    1024            1.26 BILLION             16,641            25
 *    4096           20.2 BILLION            263,169           289
 *
 * 20 billion blocks at ~5M blocks/sec of scrubbing is over an hour of solid work, and it needs 263k
 * chunk columns which no server can hold in memory at once. Trying to do it in one go is not "slow",
 * it is impossible.
 *
 * So instead of one huge operation, ShadowClanGaming records a SCAR: a persistent sphere in the world's
 * saved data. Then:
 *
 *   1. Currently loaded chunks inside the scar are scrubbed immediately (budgeted per tick).
 *   2. ANY chunk that loads later, anywhere in the world, is wiped the moment it comes into memory.
 *   3. The result the player sees is a clean crater of the right size, and the server never blocks
 *      for more than its per-tick budget.
 *
 * This is how a "destroy everything" weapon should work at planetary scale: it doesn't brute-force
 * the world, it changes what the world *means* from that point on.
 *
 * If you genuinely want the region files wiped and regenerated (instant, but destroys everything in
 * them including builds you wanted to keep), that's `SteelRain` territory - see docs/PERFORMANCE.md.
 */
public final class StrategicBlasts {

    /** Radius at which we stop enumerating blocks and start recording scars. */
    public static final int SCAR_THRESHOLD = 64;

    /** Radius beyond which the scar is treated as a cylinder down to bedrock. */
    public static final int MONSTER_THRESHOLD = 256;

    private StrategicBlasts() {
    }

    /** Persistent scar data, stored in the overworld's data storage. */
    public static final class Scars extends SavedData {
        public record Scar(Vec3 centre, double radius, long createdAt) {
        }

        private final List<Scar> scars = new ArrayList<>();

        public static Scars get(ServerLevel level) {
            ServerLevel storageLevel = level.getServer().overworld();
            return storageLevel.getDataStorage().computeIfAbsent(
                    new SavedData.Factory<>(Scars::new, Scars::load),
                    "shadowclangaming_scars");
        }

        public static Scars load(CompoundTag tag, HolderLookup.Provider registries) {
            Scars data = new Scars();
            ListTag list = tag.getList("Scars", Tag.TAG_COMPOUND);
            for (int i = 0; i < list.size(); i++) {
                CompoundTag entry = list.getCompound(i);
                data.scars.add(new Scar(
                        new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")),
                        entry.getDouble("Radius"),
                        entry.getLong("Created")));
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
            ListTag list = new ListTag();
            for (Scar scar : scars) {
                CompoundTag entry = new CompoundTag();
                entry.putDouble("X", scar.centre().x);
                entry.putDouble("Y", scar.centre().y);
                entry.putDouble("Z", scar.centre().z);
                entry.putDouble("Radius", scar.radius());
                entry.putLong("Created", scar.createdAt());
                list.add(entry);
            }
            tag.put("Scars", list);
            return tag;
        }

        public void add(Scar scar) {
            scars.add(scar);
            setDirty();
        }

        public List<Scar> all() {
            return scars;
        }

        public void clear() {
            scars.clear();
            setDirty();
        }

        /** @return true if a scar existed at that index. */
        public boolean remove(int index) {
            if (index < 0 || index >= scars.size()) {
                return false;
            }
            scars.remove(index);
            setDirty();
            return true;
        }

        public boolean isEmpty() {
            return scars.isEmpty();
        }
    }

    /**
     * Adds a scar and scrubs the chunks that are actually loaded right now.
     *
     * There is NO radius cap: the scar stores whatever radius it is given. The immediate pass is
     * deliberately bounded by "chunks near online players", not by the sphere's bounding box, because
     * a radius-30,000,000 bounding box is 13 billion chunk positions - and every one of them would be
     * unloaded anyway. Unloaded chunks are handled lazily by the chunk-load hook.
     */
    public static long detonate(ServerLevel level, Vec3 centre, int radius) {
        Scars scars = Scars.get(level);
        scars.add(new Scars.Scar(centre, radius, level.getGameTime()));

        // The immediate pass is QUEUED, not done inline. A radius-4096 scar covers tens of thousands of
        // blocks per loaded chunk, and doing that inside the warhead's tick would freeze the server for
        // seconds. The queue is drained a few milliseconds at a time by tick(); each chunk appears to the
        // players as it is carved, nearest first.
        for (LevelChunk chunk : loadedChunksIn(level, centre, radius)) {
            QUEUE.addLast(new Pending(level.dimension(), chunk.getPos().toLong(), centre, radius));
        }

        long columns = (long) (Math.PI * (double) radius * radius) / 256L;
        if (radius > MONSTER_THRESHOLD) {
            ShadowClanGaming.LOGGER.warn("!! STRATEGIC BLAST radius {} at {} - this scar will erase ~{} chunk columns "
                    + "as they load, and cannot be undone. Back up the world.", radius, centre, columns);
        }
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.strategic_blast", radius, columns), false);
        ShadowClanGaming.LOGGER.info("Strategic blast: radius {} at {} - scar recorded, {} chunk columns affected",
                radius, centre, columns);
        return columns;
    }

    /** One chunk waiting to be carved. */
    private record Pending(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                           long chunkPos,
                           Vec3 centre, double radius) {
    }

    private static final java.util.ArrayDeque<Pending> QUEUE = new java.util.ArrayDeque<>();

    /** Milliseconds of carving the server may spend per tick on queued scar work. */
    public static final long CARVE_BUDGET_MILLIS = 6L;

    /**
     * Drains the carve queue. Called once per server tick, before the client resend flush, so a chunk is
     * carved and then shipped to the players in the same pass.
     */
    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) {
            return;
        }
        long budget = CARVE_BUDGET_MILLIS * 1_000_000L;
        long started = System.nanoTime();
        while (!QUEUE.isEmpty() && System.nanoTime() - started < budget) {
            Pending pending = QUEUE.pollFirst();
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null) {
                continue;
            }
            ChunkPos pos = new ChunkPos(pending.chunkPos());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.x, pos.z);
            if (chunk == null) {
                continue; // unloaded again; the chunk-load hook will catch it when it returns
            }
            scrubChunk(level, chunk, pending.centre(), pending.radius());
        }
    }

    /** How many chunks are still waiting - used by /shadowclangaming scar to report progress. */
    public static int queued() {
        return QUEUE.size();
    }

    /**
     * Chunks to scrub immediately: everything loaded around online players, filtered to the scar.
     *
     * Cost is O(players x view-area), not O(radius^2), so an uncapped radius cannot hang the server.
     * This is also complete in practice: a chunk is only loaded because a player (or a forced ticket)
     * is near it.
     */
    private static List<LevelChunk> loadedChunksIn(ServerLevel level, Vec3 centre, double radius) {
        List<LevelChunk> chunks = new ArrayList<>();
        int view = level.getServer().getPlayerList().getViewDistance() + 2;

        java.util.LinkedHashSet<Long> seen = new java.util.LinkedHashSet<>();
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }
            ChunkPos playerChunk = player.chunkPosition();
            for (int dx = -view; dx <= view; dx++) {
                for (int dz = -view; dz <= view; dz++) {
                    int cx = playerChunk.x + dx;
                    int cz = playerChunk.z + dz;
                    long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                    if (!seen.add(key)) {
                        continue;
                    }
                    LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                    if (chunk != null) {
                        chunks.add(chunk);
                    }
                }
            }
        }
        // Nearest first so the centre of the crater clears before the rim.
        chunks.sort(Comparator.comparingDouble(chunk -> {
            double dx = (chunk.getPos().x << 4) + 8 - centre.x;
            double dz = (chunk.getPos().z << 4) + 8 - centre.z;
            return dx * dx + dz * dz;
        }));
        return chunks;
    }

    /**
     * Scrub one chunk against one scar.
     *
     * This is now a true sphere intersection (see {@link SectionScrubber#scrubSphere}): the shape of the
     * crater is decided by the radius, not by chunk boundaries, so the rim curves and the "square with
     * intact edges" look is gone at every size, including above 256 where the old code deliberately
     * carved full-height columns.
     */
    public static void scrubChunk(ServerLevel level, LevelChunk chunk, Vec3 centre, double radius) {
        if (!SectionScrubber.chunkOverlapsSphere(chunk, centre, radius)) {
            return;
        }
        SectionScrubber.scrubSphere(level, chunk, centre, radius);
    }

    /**
     * Called when a chunk loads: if it sits inside any recorded scar, wipe it.
     * This is what makes a 4096-radius blast possible at all - the world pays the cost lazily,
     * chunk by chunk, exactly when that chunk enters memory.
     */
    public static void onChunkLoad(ServerLevel level, LevelChunk chunk) {
        Scars scars = Scars.get(level);
        if (scars.isEmpty()) {
            return;
        }
        for (Scars.Scar scar : scars.all()) {
            if (SectionScrubber.chunkOverlapsSphere(chunk, scar.centre(), scar.radius())) {
                scrubChunk(level, chunk, scar.centre(), scar.radius());
            }
        }
    }

    /** Used by the tick listener to log progress without spamming. */
    public static int scarCount(MinecraftServer server) {
        return Scars.get(server.overworld()).all().size();
    }
}
