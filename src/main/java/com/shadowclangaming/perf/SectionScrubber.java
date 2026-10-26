package com.shadowclangaming.perf;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Bulk block clearing - a real sphere, not a box.
 *
 * ──────────────────── WHAT WAS WRONG ────────────────────
 *
 * The first version cleared "a slab of one chunk": every section that overlapped the sphere's y range was
 * emptied corner to corner. That is why a strategic blast came out SQUARE, with ragged edges and intact
 * islands of terrain around the rim - the shape was being decided by chunk boundaries, not by the blast
 * radius. Worse, above radius 256 it deliberately cleared full-height cylinders, so big warheads carved
 * literal columns.
 *
 * ──────────────────── WHAT IT DOES NOW ────────────────────
 *
 * The blast is a sphere, tested per COLUMN and then per block:
 *
 *   for every one of the chunk's 256 columns:
 *       h2 = dx^2 + dz^2                      (horizontal distance from the centre)
 *       if h2 > r^2: the column is outside the sphere, skip it entirely
 *       half = sqrt(r^2 - h2)                 (exact half-height of the sphere at this column)
 *       clear y from centre.y-half to centre.y+half
 *
 * That is the analytic intersection of a sphere with a vertical column, so the result is a true sphere at
 * any radius: the rim curves, single blocks survive just outside it, and nothing is decided by where a
 * chunk happens to end. Columns fully outside cost one comparison; columns inside cost only the blocks
 * they actually contain.
 *
 * Everything else that made the old path fast is kept: writes go straight into the section palette
 * (no per-block neighbour updates), each touched section is relit ONCE, heightmaps are primed once per
 * chunk, block entities inside the hole are removed, and the chunk is queued for a client resend.
 */
public final class SectionScrubber {

    /**
     * Clears the part of one chunk that lies inside the sphere.
     *
     * @return the number of blocks actually changed (already-air blocks are not counted)
     */
    public static int scrubSphere(ServerLevel level, LevelChunk chunk, Vec3 centre, double radius) {
        if (radius <= 0.0D) {
            return 0;
        }
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight() - 1;
        int baseX = chunk.getPos().getMinBlockX();
        int baseZ = chunk.getPos().getMinBlockZ();
        double radiusSq = radius * radius;

        BlockState air = Blocks.AIR.defaultBlockState();
        Set<Integer> touchedSections = new LinkedHashSet<>();
        int written = 0;

        for (int localX = 0; localX < 16; localX++) {
            double dx = baseX + localX + 0.5D - centre.x;
            if (dx * dx > radiusSq) {
                continue; // whole column of 16 is outside the sphere
            }
            for (int localZ = 0; localZ < 16; localZ++) {
                double dz = baseZ + localZ + 0.5D - centre.z;
                double horizontalSq = dx * dx + dz * dz;
                if (horizontalSq > radiusSq) {
                    continue;
                }
                double halfHeight = Math.sqrt(radiusSq - horizontalSq);
                int fromY = (int) Math.floor(centre.y - halfHeight);
                int toY = (int) Math.ceil(centre.y + halfHeight);
                if (toY < minY || fromY > maxY) {
                    continue;
                }
                fromY = Math.max(fromY, minY);
                toY = Math.min(toY, maxY);

                for (int y = fromY; y <= toY; y++) {
                    int sectionIndex = level.getSectionIndex(y);
                    LevelChunkSection section = chunk.getSection(sectionIndex);
                    if (section == null || section.hasOnlyAir()) {
                        continue;
                    }
                    int localY = y - level.getSectionYFromSectionIndex(sectionIndex) * 16;
                    if (!section.getBlockState(localX, localY, localZ).isAir()) {
                        section.setBlockState(localX, localY, localZ, air);
                        written++;
                        touchedSections.add(sectionIndex);
                    }
                }
            }
        }

        if (written > 0) {
            for (int sectionIndex : touchedSections) {
                // ONE relight per section instead of one per block - the single biggest saving here.
                level.getLightEngine().updateSectionStatus(
                        SectionPos.of(chunk.getPos().x, level.getSectionYFromSectionIndex(sectionIndex),
                                chunk.getPos().z),
                        false);
            }
            // Heightmaps still describe the pre-blast terrain until they are primed again.
            ClientSync.primeHeightmaps(chunk);
            // Block entities (chests, machines) must go with their blocks, or they survive as invisible
            // interactable ghosts inside the crater.
            removeBlockEntitiesInside(chunk, centre, radius);
            chunk.setUnsaved(true);
            // And the client has to be told, or the world turns into ghost blocks.
            ClientSync.markDirty(level, chunk.getPos());
        }
        return written;
    }

    /** Drops block entities whose position lies inside the sphere. */
    private static void removeBlockEntitiesInside(LevelChunk chunk, Vec3 centre, double radius) {
        double radiusSq = radius * radius;
        java.util.List<BlockPos> doomed = new java.util.ArrayList<>();
        for (BlockPos pos : chunk.getBlockEntities().keySet()) {
            double dx = pos.getX() + 0.5D - centre.x;
            double dy = pos.getY() + 0.5D - centre.y;
            double dz = pos.getZ() + 0.5D - centre.z;
            if (dx * dx + dy * dy + dz * dz <= radiusSq) {
                doomed.add(pos);
            }
        }
        for (BlockPos pos : doomed) {
            chunk.removeBlockEntity(pos);
        }
    }

    /** Returns true if the chunk has nothing solid left in the given y range. */
    public static boolean isEmpty(ServerLevel level, LevelChunk chunk, int fromY, int toY) {
        int minSectionIndex = level.getSectionIndex(Math.max(fromY, level.getMinBuildHeight()));
        int maxSectionIndex = level.getSectionIndex(Math.min(toY, level.getMaxBuildHeight() - 1));
        for (int index = minSectionIndex; index <= maxSectionIndex; index++) {
            LevelChunkSection section = chunk.getSection(index);
            if (section != null && !section.hasOnlyAir()) {
                return false;
            }
        }
        return true;
    }

    /**
     * Does the sphere reach into this chunk at all?
     *
     * The nearest point of the chunk's 16x16 footprint to the centre decides it: a chunk whose nearest
     * corner is further away than the radius cannot contain a single block of the sphere.
     */
    public static boolean chunkOverlapsSphere(LevelChunk chunk, Vec3 centre, double radius) {
        int minX = chunk.getPos().getMinBlockX();
        int minZ = chunk.getPos().getMinBlockZ();
        double nearestX = Math.max(minX, Math.min(minX + 16.0D, centre.x));
        double nearestZ = Math.max(minZ, Math.min(minZ + 16.0D, centre.z));
        double dx = nearestX - centre.x;
        double dz = nearestZ - centre.z;
        return dx * dx + dz * dz <= radius * radius;
    }

    private SectionScrubber() {
    }
}
