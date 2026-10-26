package com.shadowclangaming.warhead;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.Locale;

/**
 * AE2 cells, recognised without linking against AE2.
 *
 * WHAT THE PLAYER ASKED FOR
 *   - "load in an AE2 cell for items, cell will be dropped instead of items"
 *   - "a missile that you can load an AE2 spatial cell into and wherever it hits it loads that area
 *      into the cell"
 *
 * Both features only need to answer two questions: is this stack an ITEM cell, and is it a SPATIAL
 * cell. Everything else (what is inside it, where the spatial pocket goes) is AE2's business.
 *
 * AE2 is a hard-ish dependency of the server admin's choice, not of ours, so this is reflective like
 * every other optional integration in this mod: one warning at most, and a clean fallback when the mod
 * is absent. The fallback for recognition is an id heuristic, which is enough because a cell that we
 * misidentify is still dropped as an item - the worst case is "it behaves like any other item".
 */
public final class Ae2Cells {

    public static final String MODID = "appliedenergistics2";

    private static boolean attempted;
    private static volatile boolean available;
    private static MethodHandle isCellHandled;   // StorageCells.isCellHandled(ItemStack) -> boolean
    private static MethodHandle getCellInventory; // StorageCells.getCellInventory(ItemStack, null) -> Object
    private static Class<?> spatialCellClass;     // appeng.api.storage.ISpatialStorageCell

    private Ae2Cells() {
    }

    private static synchronized void resolve() {
        if (attempted) {
            return;
        }
        attempted = true;
        if (!ModList.get().isLoaded(MODID) && !ModList.get().isLoaded("ae2")) {
            return;
        }
        try {
            MethodHandles.Lookup lookup = MethodHandles.publicLookup();
            Class<?> storageCells = Class.forName("appeng.api.storage.StorageCells");
            isCellHandled = lookup.findStatic(storageCells, "isCellHandled",
                    MethodType.methodType(boolean.class, ItemStack.class));
            try {
                getCellInventory = lookup.findStatic(storageCells, "getCellInventory",
                        MethodType.methodType(Class.forName("appeng.api.storage.ICellInventory"),
                                ItemStack.class, Class.forName("appeng.api.storage.ISaveProvider")));
            } catch (Throwable ignored) {
                getCellInventory = null; // signature moved; recognition alone is enough
            }
            try {
                spatialCellClass = Class.forName("appeng.api.implementations.items.ISpatialStorageCell");
            } catch (Throwable ignored) {
                spatialCellClass = null;
            }
            available = true;
            ShadowClanGaming.LOGGER.info("AE2 found - item cells drop intact and spatial cells can be loaded.");
        } catch (Throwable throwable) {
            available = false;
            ShadowClanGaming.LOGGER.info("AE2 storage API not reachable ({}); cells are recognised by id only.",
                    throwable.toString());
        }
    }

    public static boolean available() {
        resolve();
        return available;
    }

    /** Is AE2's spatial storage class present on this installation? */
    public static boolean spatialAvailable() {
        resolve();
        return spatialCellClass != null;
    }

    /**
     * Is this stack an AE2 item cell (any storage cell, portable or not)?
     *
     * Recognised by API when AE2 answers, by id when it does not - {@code ae2:item_storage_cell_1k},
     * {@code ae2:fluid_cell_4k}, and also our own world's namespaced variants.
     */
    public static boolean isItemCell(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        resolve();
        if (available && isCellHandled != null) {
            try {
                Object result = isCellHandled.invoke(stack);
                if (result instanceof Boolean handled && handled) {
                    return true;
                }
            } catch (Throwable ignored) {
                // fall through to the id test
            }
        }
        return idLooksLikeCell(stack);
    }

    /**
     * Is this stack a spatial storage cell?
     *
     * Spatial cells are the ones whose job is to hold a pocket of the world, and they are the only
     * thing a SPATIAL warhead will accept. Returning false for a normal item cell matters: firing a
     * 1k item cell into the ground must not try to store terrain in it.
     */
    public static boolean isSpatialCell(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        resolve();
        if (spatialCellClass != null && spatialCellClass.isInstance(stack.getItem())) {
            return true;
        }
        String path = idOf(stack);
        return path.contains("spatial") && path.contains("cell");
    }

    /** True if the payload contains a cell that should be dropped whole rather than unpacked. */
    public static boolean containsCell(java.util.List<ItemStack> payload) {
        if (payload == null) {
            return false;
        }
        for (ItemStack stack : payload) {
            if (isItemCell(stack)) {
                return true;
            }
        }
        return false;
    }

    private static boolean idLooksLikeCell(ItemStack stack) {
        String path = idOf(stack);
        return path.contains("cell")
                && (path.contains("storage") || path.contains("item") || path.contains("fluid")
                || path.contains("gas") || path.contains("spatial"));
    }

    private static String idOf(ItemStack stack) {
        var key = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        return key == null ? "" : key.toString().toLowerCase(Locale.ROOT);
    }

    /**
     * Store the region around {@code radius} into a spatial cell.
     *
     * AE2's real spatial API is called reflectively: {@code ISpatialStorageCell#saveSpatialStorage}
     * takes their own storage dimension and a size, so a version drift fails here rather than at
     * class-load. Every failure path returns a reason string the warhead puts in chat - a spatial
     * missile that silently deletes a base would be unforgivable.
     *
     * @return null on success, or a human-readable reason for the failure
     */
    public static String storeRegion(net.minecraft.server.level.ServerLevel level, ItemStack cell,
                                     net.minecraft.core.BlockPos center, int radius) {
        if (!isSpatialCell(cell)) {
            return "that is not a spatial storage cell";
        }
        if (spatialCellClass == null) {
            return "AE2 spatial storage is not available on this server";
        }
        try {
            Object item = cell.getItem();
            // getSpatialStorageScale(ItemStack) -> int: how many blocks the cell can hold per axis.
            MethodHandle scale = MethodHandles.publicLookup().findVirtual(spatialCellClass,
                    "getSpatialStorageScale", MethodType.methodType(int.class, ItemStack.class));
            Object size = scale.invoke(item, cell);
            if (size instanceof Integer max && max > 0 && radius * 2 + 1 > max) {
                return "the cell holds " + max + " blocks per side, this volume is " + (radius * 2 + 1);
            }
            // The save call itself differs between AE2 releases; look for any method called
            // saveSpatialStorage and hand it the level, the stack and the origin.
            for (java.lang.reflect.Method method : spatialCellClass.getMethods()) {
                if (!method.getName().equals("saveSpatialStorage")) {
                    continue;
                }
                method.setAccessible(true);
                Object[] args = new Object[method.getParameterCount()];
                Class<?>[] types = method.getParameterTypes();
                for (int i = 0; i < types.length; i++) {
                    if (types[i].isAssignableFrom(level.getClass())) {
                        args[i] = level;
                    } else if (types[i] == ItemStack.class) {
                        args[i] = cell;
                    } else if (types[i] == int.class) {
                        args[i] = radius * 2 + 1;
                    } else if (types[i] == BlockPosLike.TYPE) {
                        args[i] = center;
                    }
                }
                method.invoke(item, args);
                return null;
            }
            return "this AE2 build exposes no saveSpatialStorage method";
        } catch (Throwable throwable) {
            ShadowClanGaming.LOGGER.warn("AE2 spatial store failed: {}", throwable.toString());
            return "AE2 refused the spatial transfer (" + throwable.getClass().getSimpleName() + ")";
        }
    }

    /** Tiny shim so the reflective argument match above reads cleanly. */
    private static final class BlockPosLike {
        static final Class<?> TYPE = net.minecraft.core.BlockPos.class;
    }
}
