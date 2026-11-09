package com.shadowclangaming.compat.projecte;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.Direction;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * ProjectE EMC bridge - EMC is a {@code long}, which is exactly why the cable is long-based too.
 *
 * WHAT EMC ACTUALLY IS. EMC is not a power network. It is matter value, stored per item, per player
 * knowledge, and in a handful of block entities (EMC Links, condensers, relays). There is no
 * "EMC pipe" concept in vanilla ProjectE, so the only way a cable can carry it is to find those
 * storages itself and move EMC in and out directly. That is what this class does.
 *
 * WHY THE METHOD PROBING. ProjectE's EMC interface has been reshaped at least twice:
 *
 *   1.19+/1.20+  IEmcStorage.getStoredEmc(): long
 *                IEmcStorage.extractEmc(long, EmcAction) / insertEmc(long, EmcAction)
 *   older        IEmcAcceptor.acceptEMC(side, long) / IEmcProvider.provideEMC(side, long)
 *
 * We compile against neither (that would be a hard dependency on a GPL-adjacent API and would break
 * the build the moment they refactor). Instead we look the shapes up at runtime, newest first, and
 * fall back gracefully. If nothing matches, EMC transfer turns itself off with one log line and the
 * cable keeps working as a pure FE device. Nothing here can crash the server: every call site catches
 * Throwable and disables the channel on the first surprise.
 */
public final class ProjectEEmc {

    public static final String MODID = "projecte";

    private static final String[] STORAGE_CLASSES = {
            "moze_intel.projecte.api.capabilities.IEmcStorage",
            "moze_intel.projecte.api.tile.IEmcStorage"
    };

    private static boolean attempted;
    private static boolean available;

    private static Class<?> storageClass;
    private static Method getStoredEmc;
    private static Method getMaximumEmc;

    /** Modern API: extractEmc(long, EmcAction) / insertEmc(long, EmcAction). */
    private static Method extractEmcHandle;
    private static Method insertEmcHandle;
    private static Object actionExecute;
    private static Object actionSimulate;

    /** Legacy API: provideEMC(Direction, long) / acceptEMC(Direction, long). */
    private static Method provideEmc;
    private static Method acceptEmc;

    private static synchronized void init() {
        if (attempted) {
            return;
        }
        attempted = true;
        if (!ModList.get().isLoaded(MODID)) {
            return;
        }
        try {
            for (String name : STORAGE_CLASSES) {
                try {
                    storageClass = Class.forName(name);
                    break;
                } catch (ClassNotFoundException ignored) {
                    // try the next shape
                }
            }
            if (storageClass == null) {
                ShadowClanGaming.LOGGER.warn("ProjectE is installed but its EMC interface was not found - "
                        + "EMC transfer on cables disabled.");
                return;
            }

            getStoredEmc = storageClass.getMethod("getStoredEmc");
            getMaximumEmc = storageClass.getMethod("getMaximumEmc");

            // --- modern methods, if present ---
            try {
                Class<?> actionClass = Class.forName("moze_intel.projecte.api.capabilities.IEmcStorage$EmcAction");
                if (actionClass.isEnum()) {
                    actionExecute = enumConstant(actionClass, "EXECUTE");
                    actionSimulate = enumConstant(actionClass, "SIMULATE");
                }
                extractEmcHandle = findByNameAndArity(storageClass, "extractEmc", 2);
                insertEmcHandle = findByNameAndArity(storageClass, "insertEmc", 2);
            } catch (Throwable ignored) {
                extractEmcHandle = null;
                insertEmcHandle = null;
            }

            // --- legacy methods, if present ---
            provideEmc = findByNameAndArity(storageClass, "provideEMC", 2);
            acceptEmc = findByNameAndArity(storageClass, "acceptEMC", 2);

            boolean canRead = getStoredEmc != null;
            boolean canExtract = extractEmcHandle != null || provideEmc != null;
            boolean canInsert = insertEmcHandle != null || acceptEmc != null;

            available = canRead;
            ShadowClanGaming.LOGGER.info("ProjectE EMC bridge: read={} extract={} insert={}",
                    canRead, canExtract, canInsert);
        } catch (Throwable throwable) {
            available = false;
            ShadowClanGaming.LOGGER.warn("ProjectE EMC bridge failed ({}). EMC channel disabled.",
                    throwable.toString());
        }
    }

    private static Object enumConstant(Class<?> enumClass, String name) {
        for (Object constant : enumClass.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(name)) {
                return constant;
            }
        }
        return null;
    }

    private static Method findByNameAndArity(Class<?> owner, String name, int arity) {
        for (Method method : owner.getMethods()) {
            if (method.getName().equals(name) && method.getParameterCount() == arity) {
                return method;
            }
        }
        return null;
    }

    public static boolean isAvailable() {
        init();
        return available;
    }

    /** @return true if this object is one of ProjectE's EMC storages. */
    public static boolean isEmcStorage(Object candidate) {
        init();
        return available && candidate != null && storageClass.isInstance(candidate);
    }

    public static long storedEmc(Object storage) {
        init();
        if (!available || storage == null || getStoredEmc == null) {
            return 0L;
        }
        try {
            Object value = getStoredEmc.invoke(storage);
            return value instanceof Long l ? l : 0L;
        } catch (Throwable throwable) {
            disable(throwable);
            return 0L;
        }
    }

    public static long maximumEmc(Object storage) {
        init();
        if (!available || storage == null || getMaximumEmc == null) {
            return 0L;
        }
        try {
            Object value = getMaximumEmc.invoke(storage);
            return value instanceof Long l ? l : 0L;
        } catch (Throwable throwable) {
            disable(throwable);
            return 0L;
        }
    }

    /** @return how much EMC was actually pulled out. */
    public static long extractEmc(Object storage, long amount, Direction side) {
        init();
        if (!available || storage == null || amount <= 0L) {
            return 0L;
        }
        try {
            if (extractEmcHandle != null && actionExecute != null) {
                Object moved = extractEmcHandle.invoke(storage, amount, actionExecute);
                return moved instanceof Long l ? l : 0L;
            }
            if (provideEmc != null) {
                Object moved = provideEmc.invoke(storage, side, amount);
                return moved instanceof Long l ? l : 0L;
            }
            return 0L;
        } catch (Throwable throwable) {
            disable(throwable);
            return 0L;
        }
    }

    /** @return how much EMC was actually inserted. */
    public static long insertEmc(Object storage, long amount, Direction side) {
        init();
        if (!available || storage == null || amount <= 0L) {
            return 0L;
        }
        try {
            if (insertEmcHandle != null && actionExecute != null) {
                Object moved = insertEmcHandle.invoke(storage, amount, actionExecute);
                return moved instanceof Long l ? l : 0L;
            }
            if (acceptEmc != null) {
                Object moved = acceptEmc.invoke(storage, side, amount);
                return moved instanceof Long l ? l : 0L;
            }
            return 0L;
        } catch (Throwable throwable) {
            disable(throwable);
            return 0L;
        }
    }

    /** @return how much EMC this storage would accept right now, without changing anything. */
    public static long roomFor(Object storage) {
        long max = maximumEmc(storage);
        if (max <= 0L) {
            return Long.MAX_VALUE / 4L; // "unbounded" storage: let the caller cap it
        }
        return Math.max(0L, max - storedEmc(storage));
    }

    private static void disable(Throwable throwable) {
        if (available) {
            available = false;
            ShadowClanGaming.LOGGER.warn("ProjectE EMC bridge disabled after an unexpected API change: {}",
                    throwable.toString());
        }
    }

    private ProjectEEmc() {
    }
}
