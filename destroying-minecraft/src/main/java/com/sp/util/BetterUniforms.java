package com.sp.util;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Veil helper — when Veil is present sets uniforms via its ShaderProgram API.
 * When Veil is not on classpath (offline CI / no JarInJar) calls are no-op so
 * the mod still loads and gameplay works, just without post-shaders.
 * Uses reflection to avoid hard compile dependency; keep signatures identical to original.
 */
public class BetterUniforms {
    public static void setMatrix(Object shaderProgram, String name, Matrix4f mat) {
        trySet(shaderProgram, name, u -> u.getClass().getMethod("setMatrix", Matrix4f.class).invoke(u, mat));
    }
    public static void setVector3f(Object shaderProgram, String name, Vector3f vec) {
        trySet(shaderProgram, name, u -> u.getClass().getMethod("setVector", Vector3f.class).invoke(u, vec));
    }
    public static void setVector2f(Object shaderProgram, String name, Vector2f vec) {
        trySet(shaderProgram, name, u -> u.getClass().getMethod("setVector", Vector2f.class).invoke(u, vec));
    }
    public static void setFloat(Object shaderProgram, String name, float v) {
        trySet(shaderProgram, name, u -> u.getClass().getMethod("setFloat", float.class).invoke(u, v));
    }
    public static void setInt(Object shaderProgram, String name, int v) {
        trySet(shaderProgram, name, u -> u.getClass().getMethod("setInt", int.class).invoke(u, v));
    }

    private interface UniformSetter { void set(Object uniform) throws Exception; }

    private static void trySet(Object shaderProgram, String name, UniformSetter setter) {
        if (shaderProgram == null) return;
        try {
            Object uniform = shaderProgram.getClass().getMethod("getUniform", String.class).invoke(shaderProgram, name);
            if (uniform != null) setter.set(uniform);
        } catch (Exception ignored) {
            // Veil not present or API changed — safe no-op for CI / headless
        }
    }
}
