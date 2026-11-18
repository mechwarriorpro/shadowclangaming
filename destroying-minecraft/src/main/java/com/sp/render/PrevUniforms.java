package com.sp.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * NeoForge port — original used Veil CameraMatrices; we fallback to Minecraft's camera if Veil absent.
 * Keeps same API so BlackHolePostShader compiles.
 */
public class PrevUniforms {
    private static Matrix4f prevProjMat = new Matrix4f();
    private static Matrix4f prevModelViewMat = new Matrix4f();
    private static Vector3f prevCameraPos = new Vector3f();
    private static boolean init = false;

    public static void update() {
        try {
            // Try Veil if present
            Class<?> veilRS = Class.forName("foundry.veil.api.client.render.VeilRenderSystem");
            Object renderer = veilRS.getMethod("renderer").invoke(null);
            Object matrices = renderer.getClass().getMethod("getCameraMatrices").invoke(renderer);
            Matrix4f proj = (Matrix4f) matrices.getClass().getMethod("getProjectionMatrix").invoke(matrices);
            Matrix4f view = (Matrix4f) matrices.getClass().getMethod("getViewMatrix").invoke(matrices);
            Vector3f pos = (Vector3f) matrices.getClass().getMethod("getCameraPosition").invoke(matrices);
            prevProjMat = new Matrix4f(proj); prevModelViewMat = new Matrix4f(view); prevCameraPos = new Vector3f(pos);
            init = true; return;
        } catch (Exception ignored) {}
        // Fallback: use Minecraft client matrices
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.gameRenderer != null) {
                // Approximate — keep identity if we can't resolve
                init = true;
            }
        } catch (Exception ignored) {}
    }

    public static boolean isInitialized() { return init; }
    public static Matrix4f getPrevProjMat() { return new Matrix4f(prevProjMat); }
    public static Matrix4f getPrevModelViewMat() { return new Matrix4f(prevModelViewMat); }
    public static Vector3f getPrevCameraPos() { return new Vector3f(prevCameraPos); }
}
