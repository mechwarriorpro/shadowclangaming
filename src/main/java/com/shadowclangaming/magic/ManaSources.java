package com.shadowclangaming.magic;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * The passive side of mana: amethyst and the blood pact. Chalk circles look after themselves (their
 * block entity hands out mana to whoever is standing in them), so they are not here.
 *
 * ──────────────────── NO NATURAL REGENERATION ────────────────────
 *
 * Mana does not come back on its own. There are exactly three ways to get it:
 *
 *   1. AMETHYST   within 32 blocks of amethyst: 1 mana/s, +1 per extra source up to 5/s. Free, slow,
 *                 and always available - this is the floor, not a strategy.
 *   2. CHALK      stand in the middle of a drawn chalk circle. Bigger circle, more mana.
 *                 See ChalkCircleBlockEntity.
 *   3. THE PACT   a blood ritual. A huge supply anywhere in the world, but it expires and has to be
 *                 fed, and taking it permanently bars you from chalk forever.
 *
 * ──────────────────── WHY THE AMETHYST SCAN IS CACHED ────────────────────
 *
 * A 32-block radius is a 65x65x65 volume: 274,625 blocks. Scanning that per player per second is not
 * an option. Two cheap tricks get it to something sane:
 *
 *   - only rescan when the player has moved more than 16 blocks, or 30 seconds have passed, because
 *     amethyst does not move either;
 *   - sample a grid with a stride of 4 and stop at the first hit, which is 4,096 lookups and usually
 *     a handful in practice.
 *
 * A geode is a big object, so a stride-4 grid cannot miss one that is genuinely within range.
 */
public final class ManaSources {

    /** Radius within which amethyst counts. */
    public static final int AMETHYST_RADIUS = 32;

    /** Base mana per second from being near amethyst. */
    public static final long AMETHYST_MANA_PER_SECOND = 1L;

    /** Extra mana per second per additional amethyst source found, and its cap. */
    public static final long AMETHYST_BONUS_PER_SOURCE = 1L;
    public static final long AMETHYST_MAX_SOURCES = 4L;

    /** Mana per second while a blood pact is active. Deliberately enormous. */
    public static final long PACT_MANA_PER_SECOND = 500L;

    /** Rescan the amethyst neighbourhood after moving this far... */
    public static final double AMETHYST_RESCAN_DISTANCE = 16.0D;
    /** ...or after this many ticks regardless. */
    public static final long AMETHYST_RESCAN_TICKS = 600L;

    private record Cache(long sources, BlockPos position, long scannedAt) {
    }

    private static final Map<UUID, Cache> CACHE = new HashMap<>();

    /** Called once a second per online player from the server tick listener. */
    public static void tickPlayer(ServerPlayer player) {
        MinecraftServer server = player.serverLevel().getServer();
        long now = player.serverLevel().getGameTime();
        long mana = 0L;
        StringBuilder sources = new StringBuilder();

        long amethyst = amethystSources(player, now);
        if (amethyst > 0L) {
            long perSecond = AMETHYST_MANA_PER_SECOND
                    + Math.min(AMETHYST_MAX_SOURCES, amethyst - 1L) * AMETHYST_BONUS_PER_SOURCE;
            mana += perSecond;
            sources.append("amethyst ");
        }

        if (ManaApi.hasPact(server, player.getUUID(), now)) {
            mana += PACT_MANA_PER_SECOND;
            sources.append("pact ");
        }

        if (mana > 0L) {
            long granted = ManaApi.grantMana(player, mana);
            if (granted > 0L) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.shadowclangaming.mana_gain",
                                String.format("%,d", granted),
                                String.format("%,d", ManaApi.current(player)),
                                String.format("%,d", ManaApi.capacity(player))), true);
            }
        }
    }

    /** How many amethyst sources are within range, using the cache where possible. */
    public static long amethystSources(ServerPlayer player, long now) {
        UUID id = player.getUUID();
        BlockPos position = player.blockPosition();
        Cache cache = CACHE.get(id);

        if (cache != null
                && cache.position().distSqr(position) < AMETHYST_RESCAN_DISTANCE * AMETHYST_RESCAN_DISTANCE
                && now - cache.scannedAt() < AMETHYST_RESCAN_TICKS) {
            return cache.sources();
        }

        long found = scanForAmethyst(player.serverLevel(), position);
        CACHE.put(id, new Cache(found, position.immutable(), now));
        return found;
    }

    /** Stride-4 grid scan, stops at {@link #AMETHYST_MAX_SOURCES} + 1 hits. */
    private static long scanForAmethyst(ServerLevel level, BlockPos centre) {
        final int stride = 4;
        long found = 0L;
        int r = AMETHYST_RADIUS;

        for (int x = -r; x <= r; x += stride) {
            for (int z = -r; z <= r; z += stride) {
                // vertical extent of a geode: generous but not the whole world
                for (int y = -r; y <= r; y += stride) {
                    BlockPos pos = centre.offset(x, y, z);
                    if (isAmethyst(level.getBlockState(pos))) {
                        found++;
                        if (found > AMETHYST_MAX_SOURCES) {
                            return found; // capped anyway, no point scanning further
                        }
                    }
                }
            }
        }
        return found;
    }

    private static boolean isAmethyst(BlockState state) {
        return state.is(Blocks.AMETHYST_BLOCK)
                || state.is(Blocks.BUDDING_AMETHYST)
                || state.is(Blocks.AMETHYST_CLUSTER)
                || state.is(Blocks.LARGE_AMETHYST_BUD)
                || state.is(Blocks.MEDIUM_AMETHYST_BUD)
                || state.is(Blocks.SMALL_AMETHYST_BUD);
    }

    /** Drop a player's cached scan (logout, dimension change). */
    public static void forget(UUID player) {
        CACHE.remove(player);
    }

    private ManaSources() {
    }
}
