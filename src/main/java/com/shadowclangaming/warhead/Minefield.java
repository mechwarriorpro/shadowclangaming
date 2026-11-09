package com.shadowclangaming.warhead;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The mine layer's arithmetic and placement. Kept free of game objects except the level it writes to,
 * because the two numbers players argue about - how far the field reaches, and how many mines it
 * leaves - are pure functions of the funding.
 *
 * THE RULE, as specified:
 *   base radius 32 blocks
 *   +1 block of radius for every 250 currency units loaded
 *   every block in the field has a 10% chance of getting a mine
 *
 * "Every block" is read as every ground COLUMN, not every cubic block: a 32-block sphere contains
 * 137,000 positions and a 10% chance there would be 13,000 mines. Columns give 4,096 positions at
 * radius 32 - about 410 mines - which is a minefield a server can survive and a player can cross.
 */
public final class Minefield {

    /** Radius with no funding at all. */
    public static final int BASE_RADIUS = 32;
    /** Currency units per extra block of radius. */
    public static final long FUNDING_PER_BLOCK = 250L;
    /** Chance a candidate column gets a mine. */
    public static final float MINE_CHANCE = 0.10F;
    /** Hard ceiling on mines per warhead, regardless of funding. */
    public static final int MAX_MINES = 2048;
    /** How far below/above the impact point a column may put its mine. */
    public static final int VERTICAL_SCAN = 6;

    private Minefield() {
    }

    /** Radius in blocks for a given funding. Uncapped in principle; MAX_MINES is the real limit. */
    public static int radiusForFunding(long funding) {
        if (funding <= 0L) {
            return BASE_RADIUS;
        }
        return BASE_RADIUS + (int) Math.min(Integer.MAX_VALUE - BASE_RADIUS, funding / FUNDING_PER_BLOCK);
    }

    /** Funding needed to reach a radius. */
    public static long fundingForRadius(int radius) {
        return Math.max(0L, (long) (radius - BASE_RADIUS) * FUNDING_PER_BLOCK);
    }

    /** How many columns a radius produces - the number of 10% dice that get rolled. */
    public static long candidateColumns(int radius) {
        long side = radius * 2L + 1L;
        return side * side;
    }

    /** Expected mines for a radius, before the MAX_MINES cut. */
    public static long expectedMines(int radius) {
        return (long) (candidateColumns(radius) * MINE_CHANCE);
    }

    /** Actual cap: never more than MAX_MINES. */
    public static int mineBudget(int radius) {
        return (int) Math.min(MAX_MINES, Math.max(0L, expectedMines(radius)));
    }

    /**
     * Place the field. Each column in the square radius rolls 10% independently, and the first solid
     * ground at or below the impact height gets the mine. Columns with no ground (open air, ocean
     * surface) are skipped rather than mined mid-air.
     *
     * @return how many mines were actually placed
     */
    public static int deploy(ServerLevel level, BlockPos impact, int radius) {
        RandomSource random = level.getRandom();
        List<BlockPos> placed = new ArrayList<>();
        int budget = mineBudget(radius);

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (placed.size() >= budget) {
                    break;
                }
                if (dx * dx + dz * dz > radius * radius) {
                    continue; // square dice, round field
                }
                if (random.nextFloat() >= MINE_CHANCE) {
                    continue;
                }
                BlockPos ground = findGround(level, impact.getX() + dx, impact.getZ() + dz, impact.getY());
                if (ground != null) {
                    placed.add(ground);
                }
            }
        }

        for (BlockPos pos : placed) {
            // The mine REPLACES the ground block and remembers it, so it is hidden in the terrain
            // instead of sitting on top of it as an obvious metal disc.
            BlockState replaced = level.getBlockState(pos);
            level.setBlockAndUpdate(pos,
                    com.shadowclangaming.registry.SCGBlocks.MINE.get().defaultBlockState());
            if (level.getBlockEntity(pos) instanceof com.shadowclangaming.block.MineBlockEntity mine) {
                mine.setMimic(replaced);
            }
        }
        return placed.size();
    }

    /**
     * The first mineable ground block at or near the impact column, scanning down then up.
     *
     * The position returned is the GROUND block itself - that is the block the mine replaces and then
     * imitates. Bedrock and other unbreakable blocks are skipped: a mine there would be indestructible.
     */
    private static BlockPos findGround(ServerLevel level, int x, int z, int y) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos(x, y, z);
        for (int down = 0; down <= VERTICAL_SCAN; down++) {
            cursor.set(x, y - down, z);
            if (isMineableGround(level, cursor)) {
                return cursor.immutable();
            }
        }
        // Nothing underneath the impact - try just above it, in case the missile came in low.
        for (int up = 1; up <= VERTICAL_SCAN; up++) {
            cursor.set(x, y + up, z);
            if (isMineableGround(level, cursor)) {
                return cursor.immutable();
            }
        }
        return null;
    }

    private static boolean isMineableGround(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.isAir() || !state.isSolidRender(level, pos)) {
            return false;
        }
        // Unbreakable blocks (bedrock, barriers, portal frames) cannot host a mine.
        if (state.getDestroySpeed(level, pos) < 0.0F) {
            return false;
        }
        // Do not bury a mine under another solid block: it has to be reachable by stepping on it.
        return level.getBlockState(pos.above()).isAir();
    }
}
