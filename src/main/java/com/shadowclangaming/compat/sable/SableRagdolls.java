package com.shadowclangaming.compat.sable;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Optional bridge to <b>Sable: Ragdolls</b> ({@code sable_player_ragdoll}).
 *
 * ──────────────────── WHY REFLECTION ────────────────────
 *
 * That mod is All Rights Reserved, so nothing of it may be compiled against or bundled - but it does
 * publish an addon API at {@code dev.leo.sableplayerragdoll.api.RagdollAPI}, and calling a public API
 * is exactly what it is for. The same rule as AE2 and Create: the mod is optional, so the hook is
 * looked up at runtime and every failure is silent.
 *
 * The signatures on that API are:
 *
 *     RagdollAPI.launch(Player, velocity[, options])
 *     RagdollAPI.launchMob(level, mob, linearVelocity[, angularVelocity, options])
 *
 * and the wiki does not say what type "velocity" is. Rather than guess, the bridge inspects the
 * parameter type it actually finds and builds a matching argument - {@code net.minecraft.world.phys.Vec3},
 * {@code org.joml.Vector3f} or {@code org.joml.Vector3d}. If none of that works, or the mod is not
 * installed, {@link #ragdoll} returns false and the caller falls back to vanilla physics.
 *
 * The user's rule for optional integrations, restated: {@code hbm} style detection only, never a hard
 * dependency.
 */
public final class SableRagdolls {

    /** Mod id of the ragdoll addon. */
    public static final String MOD_ID = "sable_player_ragdoll";

    private static final String API_CLASS = "dev.leo.sableplayerragdoll.api.RagdollAPI";

    private static boolean looked;
    private static Class<?> api;
    /** {@code launchMob(level, mob, velocity)} - the shortest form, so no options objects to build. */
    private static Method launchMob;
    /** {@code launch(player, velocity)}. */
    private static Method launchPlayer;

    private SableRagdolls() {
    }

    /** True when Sable: Ragdolls is installed and its API was found. */
    public static boolean available() {
        initialise();
        return api != null && (launchMob != null || launchPlayer != null);
    }

    /**
     * Try to turn {@code entity} into a physics ragdoll travelling at {@code velocity}.
     *
     * @return true if the ragdoll mod took it. False means "do it yourself" - the entity is untouched.
     */
    public static boolean ragdoll(ServerLevel level, LivingEntity entity, Vec3 velocity) {
        initialise();
        try {
            if (entity instanceof Player player && launchPlayer != null) {
                Object vector = velocity(launchPlayer.getParameterTypes()[1], velocity);
                if (vector != null) {
                    launchPlayer.invoke(null, player, vector);
                    return true;
                }
            }
            if (entity instanceof Mob mob && launchMob != null) {
                Class<?>[] params = launchMob.getParameterTypes();
                Object vector = velocity(params[2], velocity);
                if (vector != null) {
                    launchMob.invoke(null, level, mob, vector);
                    return true;
                }
            }
        } catch (Throwable ignored) {
            // An API change, a version mismatch, an internal exception - the spell still has to work.
            // Returning false hands the entity to the vanilla fallback, which cannot throw.
        }
        return false;
    }

    private static void initialise() {
        if (looked) {
            return;
        }
        looked = true;
        try {
            api = Class.forName(API_CLASS);
        } catch (Throwable missing) {
            return;
        }
        try {
            for (Method method : api.getMethods()) {
                if (!Modifier.isStatic(method.getModifiers())) {
                    continue;
                }
                String name = method.getName();
                Class<?>[] params = method.getParameterTypes();
                if (name.equals("launchMob") && params.length >= 3
                        && params[2] != null && isVelocityType(params[2])
                        && (launchMob == null || params.length < launchMob.getParameterCount())) {
                    launchMob = method;
                } else if (name.equals("launch") && params.length >= 2
                        && isVelocityType(params[1])
                        && (launchPlayer == null || params.length < launchPlayer.getParameterCount())) {
                    launchPlayer = method;
                }
            }
            // Kotlin may expose the API under a different surface; if nothing was found, give up
            // cleanly rather than half-working.
            if (launchMob != null) {
                launchMob.setAccessible(true);
            }
            if (launchPlayer != null) {
                launchPlayer.setAccessible(true);
            }
        } catch (Throwable ignored) {
            api = null;
        }
    }

    /** Is this parameter type something we can hand a velocity as? */
    private static boolean isVelocityType(Class<?> type) {
        if (type == Vec3.class) {
            return true;
        }
        String name = type.getName();
        return name.equals("org.joml.Vector3f") || name.equals("org.joml.Vector3d");
    }

    /** Build the argument for a velocity parameter, or null if the type is not one we know. */
    private static Object velocity(Class<?> type, Vec3 value) {
        if (type == Vec3.class) {
            return value;
        }
        String name = type.getName();
        try {
            if (name.equals("org.joml.Vector3f")) {
                return type.getConstructor(float.class, float.class, float.class)
                        .newInstance((float) value.x, (float) value.y, (float) value.z);
            }
            if (name.equals("org.joml.Vector3d")) {
                return type.getConstructor(double.class, double.class, double.class)
                        .newInstance(value.x, value.y, value.z);
            }
        } catch (Throwable ignored) {
            // fall through to null
        }
        return null;
    }
}
