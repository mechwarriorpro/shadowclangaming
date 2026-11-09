package com.shadowclangaming.radar;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

import java.util.ArrayList;
import java.util.List;

/**
 * Optional: read the Create: Radars bearings around our antenna.
 *
 * WHY THIS IS REFLECTIVE. Create: Radars (mod id {@code create_radar}) is a third-party mod with a
 * custom licence and an API that has already moved twice in a year - the 0.4.x line replaced its
 * bearing linking with a Network Controller and a Data Link item. Compiling against it would mean our
 * mod fails to load whenever they rename a method, so we never link against it. Instead:
 *
 *   1. Ask whether the mod is loaded at all.
 *   2. Look for block entities near the antenna whose class name contains "Radar".
 *   3. Let {@link TrackScanner} find the track list by SHAPE rather than by name, so a rename costs
 *      us nothing.
 *   4. If none of that answers, still report that the dish is there and how many we found, because
 *      "linked, no readable track list" is a useful answer and a crash is not.
 *
 * Their radar stays their radar. Ours is the one with the long range and the track list.
 */
public final class CreateRadarBridge {

    public static final String MODID = "create_radar";
    /** How far from our antenna a Create: Radars dish may sit and still be counted as linked. */
    public static final int LINK_RANGE = 8;

    private static boolean attempted;
    private static boolean available;

    private CreateRadarBridge() {
    }

    /** Is the mod on this installation at all? Cheap and cached. */
    public static boolean installed() {
        if (!attempted) {
            attempted = true;
            available = ModList.get().isLoaded(MODID);
            if (!available) {
                ShadowClanGaming.LOGGER.info("Create: Radars is not installed - radar integration stays local.");
            }
        }
        return available;
    }

    /** Block entities near {@code near} that look like a Create: Radars part. */
    public static List<BlockEntity> radarsNear(ServerLevel level, BlockPos near) {
        List<BlockEntity> found = new ArrayList<>();
        for (BlockPos cursor : BlockPos.betweenClosed(near.offset(-LINK_RANGE, -6, -LINK_RANGE),
                near.offset(LINK_RANGE, 6, LINK_RANGE))) {
            if (!level.isLoaded(cursor)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(cursor);
            if (blockEntity == null) {
                continue;
            }
            String name = blockEntity.getClass().getName();
            if (name.contains("Radar") || name.contains("radar")) {
                found.add(blockEntity);
            }
        }
        return found;
    }

    public static boolean linked(ServerLevel level, BlockPos near) {
        return installed() && !radarsNear(level, near).isEmpty();
    }

    public static int bearingCount(ServerLevel level, BlockPos near) {
        return installed() ? radarsNear(level, near).size() : 0;
    }

    /**
     * Ask the nearby dishes what they can see, if this build of Create: Radars lets us.
     *
     * The guessing lives in {@link TrackScanner}: any no-arg accessor or field whose name looks like a
     * track list is read, and their wrapper types are drilled into rather than assumed. Saved NBT is
     * the last resort, because a dish that publishes nothing still knows where it saw things.
     *
     * @return positions, never null, possibly empty
     */
    public static List<Vec3> readTracks(ServerLevel level, BlockPos near) {
        List<Vec3> points = new ArrayList<>();
        if (!installed()) {
            return points;
        }
        for (BlockEntity radar : radarsNear(level, near)) {
            for (double[] triple : TrackScanner.scan(radar)) {
                points.add(new Vec3(triple[0], triple[1], triple[2]));
            }
            if (points.isEmpty()) {
                readSavedTag(radar, points);
            }
        }
        return points;
    }

    /** Whatever the dish saved to disk still contains positions. Never throws. */
    private static void readSavedTag(BlockEntity radar, List<Vec3> points) {
        try {
            CompoundTag tag = radar.saveWithoutMetadata(radar.getLevel().registryAccess());
            List<double[]> found = new ArrayList<>();
            TrackScanner.collect(tagPositions(tag), found, 0);
            for (double[] triple : found) {
                points.add(new Vec3(triple[0], triple[1], triple[2]));
            }
        } catch (Throwable ignored) {
            // Older/newer signatures differ; ignore. A missing track list is not an error.
        }
    }

    /** Flattens a saved tag's "Pos" lists into plain double arrays the scanner understands. */
    private static List<double[]> tagPositions(CompoundTag tag) {
        List<double[]> out = new ArrayList<>();
        if (tag.contains("Pos")) {
            var list = tag.getList("Pos", 6);
            if (list.size() >= 3) {
                out.add(new double[]{list.getDouble(0), list.getDouble(1), list.getDouble(2)});
            }
        }
        for (String key : tag.getAllKeys()) {
            if (tag.get(key) instanceof CompoundTag child) {
                out.addAll(tagPositions(child));
            }
        }
        return out;
    }

    /**
     * One display line for the radar block and /term radar, saying whether the integration answered
     * and, when it did not, that our own sweep is still doing the work.
     */
    public static String describe(ServerLevel level, BlockPos near) {
        if (!installed()) {
            return "create_radar: not installed - this radar uses its own sweep";
        }
        int bearings = bearingCount(level, near);
        if (bearings == 0) {
            return "create_radar: installed, no dish within " + LINK_RANGE + " blocks";
        }
        int tracks = readTracks(level, near).size();
        if (tracks > 0) {
            return "create_radar: " + bearings + " dish(es), " + tracks + " track(s) readable";
        }
        return "create_radar: " + bearings + " dish(es) linked; their build publishes no readable"
                + " track list, so their tracks are ignored and ours are used";
    }

    /** Everything we could learn about a dish, for {@code /term radar bridge verbose}. */
    public static List<String> diagnose(ServerLevel level, BlockPos near) {
        List<String> lines = new ArrayList<>();
        lines.add("create_radar: " + (installed() ? "installed" : "not installed"));
        if (!installed()) {
            return lines;
        }
        List<BlockEntity> dishes = radarsNear(level, near);
        lines.add("dishes within " + LINK_RANGE + ": " + dishes.size());
        for (BlockEntity dish : dishes) {
            lines.add("  " + dish.getClass().getName() + " @ " + dish.getBlockPos().toShortString());
            List<String> accessors = TrackScanner.accessors(dish.getClass());
            lines.add("  track-like accessors: " + (accessors.isEmpty() ? "none" : String.join(", ", accessors)));
            lines.add("  positions read: " + TrackScanner.scanAll(dish).size());
        }
        return lines;
    }

    /** True if this build of Create: Radars registers a block whose id mentions radar. */
    public static boolean hasRegisteredRadarBlock() {
        if (!installed()) {
            return false;
        }
        try {
            for (ResourceLocation id : BuiltInRegistries.BLOCK.keySet()) {
                if (MODID.equals(id.getNamespace()) && id.getPath().contains("radar")) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // A registry walk is a diagnostic, never a requirement.
        }
        return false;
    }
}
