package com.shadowclangaming.computer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an Applied Energistics 2 network - without linking against AE2.
 *
 * WHY REFLECTION
 * AE2 is a big optional dependency with a fast-moving API. Compiling against it would make the whole
 * mod refuse to load when AE2 updates, for a feature most players never touch. Everything here is
 * looked up by name at runtime, cached on first success, and degrades to a plain, readable error line.
 *
 * WHAT IT DOES
 *   1. finds the closest block entity whose class is from appeng (a ME drive, an interface, a chest...)
 *   2. asks it for its grid: getGrid(), or getMainNode().getGrid(), or getActionableNode().getGrid()
 *   3. grid.getStorageService().getInventory()  -> the ME storage
 *   4. storage.getAvailableStacks(KeyCounter)   -> key/value pairs, read as display name + count
 *
 * IF AE2 IS ABSENT
 * the same command falls back to reading every vanilla container sitting against the terminal, so
 * /term storage is useful before AE2 is installed and identical in shape afterwards.
 */
public final class Ae2Access {

    /** How far from the terminal we look for an AE2 block entity. */
    public static final int SEARCH_RADIUS = 6;
    /** Most stacks we will report in one read. */
    public static final int MAX_ENTRIES = 14;

    private static final String AE2_PACKAGE = "appeng.";

    /** One line of the report: what, and how many. */
    public record Entry(String name, long count) {
    }

    /** The whole result: success or a reason, plus the entries. */
    public record Report(boolean ok, String note, List<Entry> entries) {
        public static Report failure(String note) {
            return new Report(false, note, List.of());
        }
    }

    private static boolean resolved;
    private static Method gridFromHost;
    private static Method storageService;
    private static Method inventory;
    private static Method availableStacks;
    private static boolean available;

    private Ae2Access() {
    }

    public static boolean isInstalled() {
        return ModList.get().isLoaded("ae2");
    }

    /** True if AE2 is installed AND we found a call shape that works on this version. */
    public static boolean isAvailable() {
        resolve();
        return available;
    }

    private static synchronized void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        if (!isInstalled()) {
            return;
        }
        try {
            // The host side: any AE2 block entity that owns a grid. Different AE2 versions expose
            // this under different names, so probe all of the known ones and keep whichever answers.
            Class<?> gridClass = Class.forName("appeng.api.networking.IGrid");
            for (String method : new String[]{"getGrid", "getMainNode", "getActionableNode"}) {
                try {
                    gridFromHost = Class.forName("appeng.api.networking.IGridNode").getMethod("getGrid");
                    break;
                } catch (NoSuchMethodException ignored) {
                    // try the next shape
                }
            }
            storageService = gridClass.getMethod("getStorageService");
            Class<?> serviceClass = Class.forName("appeng.api.networking.storage.IStorageService");
            inventory = serviceClass.getMethod("getInventory");
            Class<?> storageClass = Class.forName("appeng.api.storage.MEStorage");
            Class<?> keyCounterClass = Class.forName("appeng.api.stacks.KeyCounter");
            availableStacks = storageClass.getMethod("getAvailableStacks", keyCounterClass);
            available = gridFromHost != null;
        } catch (Throwable t) {
            // Anything at all going wrong means "not supported on this AE2 build", never a crash.
            available = false;
        }
    }

    /**
     * Read storage next to a terminal.
     *
     * @return a report; never null, never throws
     */
    public static Report read(ServerLevel level, BlockPos terminalPos) {
        BlockEntity host = findAe2Host(level, terminalPos);
        if (host == null) {
            return vanillaFallback(level, terminalPos,
                    isInstalled() ? "no AE2 block within " + SEARCH_RADIUS + " blocks" : "AE2 not installed");
        }
        if (!isAvailable()) {
            return vanillaFallback(level, terminalPos, "AE2 present but its API did not answer");
        }
        try {
            Object grid = callGrid(host);
            if (grid == null) {
                return vanillaFallback(level, terminalPos, "AE2 block next door is not on a grid");
            }
            Object service = storageService.invoke(grid);
            Object storage = inventory.invoke(service);
            Object counter = Class.forName("appeng.api.stacks.KeyCounter").getConstructor().newInstance();
            availableStacks.invoke(storage, counter);

            List<Entry> entries = new ArrayList<>();
            long total = 0L;
            // KeyCounter is an Iterable<Map.Entry<AEKey, Long>> in current AE2.
            for (Object element : (Iterable<?>) counter) {
                if (entries.size() >= MAX_ENTRIES) {
                    break;
                }
                if (!(element instanceof java.util.Map.Entry<?, ?> pair)) {
                    continue;
                }
                long amount = pair.getValue() instanceof Number number ? number.longValue() : 0L;
                total += amount;
                entries.add(new Entry(keyName(pair.getKey()), amount));
            }
            entries.sort((a, b) -> Long.compare(b.count(), a.count()));
            return new Report(true, "AE2: " + total + " items in " + entries.size() + " kinds", entries);
        } catch (Throwable t) {
            return vanillaFallback(level, terminalPos, "AE2 read failed: " + t.getClass().getSimpleName());
        }
    }

    private static Object callGrid(BlockEntity host) {
        for (String name : new String[]{"getGrid", "getMainNode", "getActionableNode"}) {
            try {
                Method method = host.getClass().getMethod(name);
                Object value = method.invoke(host);
                if (value == null) {
                    continue;
                }
                if (value instanceof Iterable<?> || value.getClass().getName().startsWith(AE2_PACKAGE)) {
                    // Either already a grid, or a node that can hand us one.
                    try {
                        return gridFromHost.invoke(value);
                    } catch (Throwable notANode) {
                        return value;
                    }
                }
            } catch (Throwable ignored) {
                // try the next name
            }
        }
        return null;
    }

    /** Best-effort readable name for an AEKey. */
    private static String keyName(Object key) {
        if (key == null) {
            return "?";
        }
        for (String method : new String[]{"getDisplayName", "dropSecondary"}) {
            try {
                Object name = key.getClass().getMethod(method).invoke(key);
                if (name != null) {
                    return name.toString();
                }
            } catch (Throwable ignored) {
                // fall through
            }
        }
        return key.toString();
    }

    private static BlockEntity findAe2Host(ServerLevel level, BlockPos pos) {
        BlockEntity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dx = -SEARCH_RADIUS; dx <= SEARCH_RADIUS; dx++) {
            for (int dy = -SEARCH_RADIUS; dy <= SEARCH_RADIUS; dy++) {
                for (int dz = -SEARCH_RADIUS; dz <= SEARCH_RADIUS; dz++) {
                    BlockPos cursor = pos.offset(dx, dy, dz);
                    if (!level.isLoaded(cursor)) {
                        continue;
                    }
                    BlockEntity blockEntity = level.getBlockEntity(cursor);
                    if (blockEntity == null
                            || !blockEntity.getClass().getName().startsWith(AE2_PACKAGE)) {
                        continue;
                    }
                    double distance = cursor.distSqr(pos);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = blockEntity;
                    }
                }
            }
        }
        return best;
    }

    /** Vanilla containers touching the terminal - the same report, no AE2 in the way. */
    private static Report vanillaFallback(ServerLevel level, BlockPos pos, String note) {
        List<Entry> entries = new ArrayList<>();
        long total = 0L;
        for (Direction direction : Direction.values()) {
            BlockPos neighbour = pos.relative(direction);
            var handler = level.getCapability(
                    net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK,
                    neighbour, direction.getOpposite());
            if (handler == null) {
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && entries.size() < MAX_ENTRIES; slot++) {
                var stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) {
                    continue;
                }
                total += stack.getCount();
                entries.add(new Entry(stack.getHoverName().getString(), stack.getCount()));
            }
        }
        if (entries.isEmpty()) {
            return Report.failure(note + "; no containers against the terminal");
        }
        entries.sort((a, b) -> Long.compare(b.count(), a.count()));
        return new Report(true, note + "; found " + total + " items in nearby containers", entries);
    }
}
