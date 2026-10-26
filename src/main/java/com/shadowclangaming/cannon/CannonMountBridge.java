package com.shadowclangaming.cannon;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.fml.ModList;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Optional: drive Create Big Cannons mounts.
 *
 * WHY REFLECTIVE, AGAIN. CBC is LGPL and we are not linking against it: a compile dependency would make
 * our mod refuse to load on every pack that does not ship cannons, and it would also mean their 5.x ->
 * 6.x reshuffle breaks our build. So the bridge probes, and falls back.
 *
 * WHAT CBC ACTUALLY EXPOSES (read from their 1.21.1 source, {@code createbigcannons} 5.11.x,
 * {@code rbasamoyai.createbigcannons.cannon_control.cannon_mount.CannonMountBlockEntity}), newest first:
 *
 *   setYaw(float) / setPitch(float)                     public - this is how we aim
 *   getYawOffset(float) / getDisplayPitch()             public - this is how we read the barrel
 *   isRunning()                                         public - true once a cannon is assembled
 *   onRedstoneUpdate(boolean assemblyPowered, boolean prevAssemblyPowered,
 *                    boolean firePowered, boolean prevFirePowered, int firePower)
 *                                                       public - this is how we fire
 *
 * Firing through {@code onRedstoneUpdate} with {@code assemblyPowered = prevAssemblyPowered = false} is
 * the safe route: CBC only reassembles when those two differ, and it only fires when the fire flag
 * changes, so one call with (false, false, true, false, 15) fires exactly one shot and leaves the
 * cannon assembled. Two ticks later we call it again with (false, false, false, true, 0) to release the
 * trigger, otherwise the next shot would not register a change.
 *
 * If any of that has moved, we do not throw: the director block emits an actual redstone pulse at the
 * mount instead, which is the mechanism CBC documents for vanilla players anyway.
 */
public final class CannonMountBridge {

    public static final String MODID = "createbigcannons";
    /** How far from a director a mount may sit. */
    public static final int LINK_RANGE = 6;

    private static boolean attempted;
    private static boolean available;

    private CannonMountBridge() {
    }

    /** A CBC mount, as much as we are allowed to know about it. */
    public record Mount(BlockPos pos, float yaw, float pitch, boolean assembled) {
        public String describe() {
            return String.format(java.util.Locale.ROOT, "mount %d %d %d yaw %.1f pitch %.1f%s",
                    pos.getX(), pos.getY(), pos.getZ(), yaw, pitch,
                    assembled ? "" : " (not assembled)");
        }
    }

    public static boolean installed() {
        if (!attempted) {
            attempted = true;
            available = ModList.get().isLoaded(MODID);
            if (!available) {
                ShadowClanGaming.LOGGER.info("Create Big Cannons is not installed - cannon directors stay idle.");
            }
        }
        return available;
    }

    /** Every mount near a director. Cheap enough for a command, never called during a tick. */
    public static List<Mount> findMounts(ServerLevel level, BlockPos near, int radius) {
        List<Mount> mounts = new ArrayList<>();
        if (!installed()) {
            return mounts;
        }
        int r = Math.max(1, radius);
        for (BlockPos cursor : BlockPos.betweenClosed(near.offset(-r, -r, -r), near.offset(r, r, r))) {
            if (!level.isLoaded(cursor)) {
                continue;
            }
            BlockEntity blockEntity = level.getBlockEntity(cursor);
            if (blockEntity == null || !isMount(blockEntity)) {
                continue;
            }
            mounts.add(read(blockEntity));
        }
        return mounts;
    }

    public static boolean isMount(BlockEntity blockEntity) {
        return blockEntity.getClass().getName().contains("CannonMountBlockEntity");
    }

    public static Mount read(BlockEntity mount) {
        float yaw = floatOfArg(mount, "getYawOffset", 0.0F);
        if (Float.isNaN(yaw)) {
            yaw = fieldFloat(mount, "cannonYaw");
        }
        // getDisplayPitch() is the modern accessor; getPitchOffset(float) is the older one.
        float pitch = floatOf(mount, "getDisplayPitch");
        if (Float.isNaN(pitch)) {
            pitch = floatOfArg(mount, "getPitchOffset", 0.0F);
        }
        if (Float.isNaN(pitch)) {
            pitch = fieldFloat(mount, "cannonPitch");
        }
        boolean assembled = bool(mount, "isRunning");
        return new Mount(mount.getBlockPos(), yaw, Float.isNaN(pitch) ? 0.0F : pitch, assembled);
    }

    /** Point the barrel. Returns false if this build of CBC did not answer. */
    public static boolean aim(ServerLevel level, BlockPos mountPos, float yaw, float pitch) {
        if (!installed()) {
            return false;
        }
        BlockEntity mount = level.getBlockEntity(mountPos);
        if (mount == null || !isMount(mount)) {
            return false;
        }
        boolean yawOk = invoke(mount, "setYaw", new Class<?>[]{float.class}, yaw);
        boolean pitchOk = invoke(mount, "setPitch", new Class<?>[]{float.class}, CannonMath.clampPitch(pitch));
        if (!yawOk) {
            yawOk = setField(mount, "cannonYaw", yaw);
        }
        if (!pitchOk) {
            pitchOk = setField(mount, "cannonPitch", CannonMath.clampPitch(pitch));
        }
        return yawOk && pitchOk;
    }

    /** Pull the trigger once. The director must call {@link #release} two ticks later. */
    public static boolean fire(ServerLevel level, BlockPos mountPos) {
        if (!installed()) {
            return false;
        }
        BlockEntity mount = level.getBlockEntity(mountPos);
        if (mount == null || !isMount(mount)) {
            return false;
        }
        Method method = findMethod(mount.getClass(), "onRedstoneUpdate",
                boolean.class, boolean.class, boolean.class, boolean.class, int.class);
        if (method == null) {
            return false;
        }
        try {
            method.setAccessible(true);
            method.invoke(mount, false, false, true, false, 15);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** Let the trigger back out so the next shot registers as a change. */
    public static boolean release(ServerLevel level, BlockPos mountPos) {
        if (!installed()) {
            return false;
        }
        BlockEntity mount = level.getBlockEntity(mountPos);
        if (mount == null || !isMount(mount)) {
            return false;
        }
        Method method = findMethod(mount.getClass(), "onRedstoneUpdate",
                boolean.class, boolean.class, boolean.class, boolean.class, int.class);
        if (method == null) {
            return false;
        }
        try {
            method.setAccessible(true);
            method.invoke(mount, false, false, false, true, 0);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** One line for /term cannon, so the player knows whether the integration answered. */
    public static String describe(ServerLevel level, BlockPos near) {
        if (!installed()) {
            return "createbigcannons: not installed";
        }
        List<Mount> mounts = findMounts(level, near, LINK_RANGE);
        if (mounts.isEmpty()) {
            return "createbigcannons: installed, no cannon mount within " + LINK_RANGE + " blocks";
        }
        int assembled = 0;
        for (Mount mount : mounts) {
            if (mount.assembled()) {
                assembled++;
            }
        }
        return "createbigcannons: " + mounts.size() + " mount(s), " + assembled + " assembled";
    }

    // ------------------------------------------------------------------ reflection helpers

    private static boolean invoke(Object target, String name, Class<?>[] types, Object... args) {
        Method method = findMethod(target.getClass(), name, types);
        if (method == null) {
            return false;
        }
        try {
            method.setAccessible(true);
            method.invoke(target, args);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    /** A no-argument float accessor such as getDisplayPitch(). */
    private static float floatOf(Object target, String name) {
        Method method = findMethod(target.getClass(), name);
        if (method == null) {
            return Float.NaN;
        }
        try {
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof Number number ? number.floatValue() : Float.NaN;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    /** A one-float-argument accessor such as getYawOffset(float). */
    private static float floatOfArg(Object target, String name, float argument) {
        Method method = findMethod(target.getClass(), name, float.class);
        if (method == null) {
            return Float.NaN;
        }
        try {
            method.setAccessible(true);
            Object value = method.invoke(target, argument);
            return value instanceof Number number ? number.floatValue() : Float.NaN;
        } catch (Throwable ignored) {
            return Float.NaN;
        }
    }

    private static float fieldFloat(Object target, String name) {
        for (Class<?> cursor = target.getClass(); cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field.getFloat(target);
            } catch (NoSuchFieldException ignored) {
                // keep walking
            } catch (Throwable ignored) {
                return Float.NaN;
            }
        }
        return Float.NaN;
    }

    private static boolean bool(Object target, String name) {
        Method method = findMethod(target.getClass(), name);
        if (method == null) {
            return false;
        }
        try {
            method.setAccessible(true);
            Object value = method.invoke(target);
            return value instanceof Boolean flag && flag;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static boolean setField(Object target, String name, float value) {
        for (Class<?> cursor = target.getClass(); cursor != null && cursor != Object.class;
             cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                field.setFloat(target, value);
                return true;
            } catch (NoSuchFieldException ignored) {
                // keep walking
            } catch (Throwable ignored) {
                return false;
            }
        }
        return false;
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... types) {
        for (Class<?> cursor = type; cursor != null && cursor != Object.class; cursor = cursor.getSuperclass()) {
            try {
                return cursor.getDeclaredMethod(name, types);
            } catch (NoSuchMethodException ignored) {
                // keep walking
            }
        }
        return null;
    }
}
