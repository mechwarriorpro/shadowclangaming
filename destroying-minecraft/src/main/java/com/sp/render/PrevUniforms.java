package com.sp.render;

import foundry.veil.api.client.render.CameraMatrices;
import foundry.veil.api.client.render.VeilRenderSystem;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Faithful port: identical to original Fabric 1.21.1 (no Yarn→Mojmap rename needed).
 * Veil's CameraMatrices is shared between veil-fabric and veil-neoforge (same JAR API).
 */
public class PrevUniforms {
    private static Matrix4f prevProjMat;
    private static Matrix4f prevModelViewMat;
    private static Vector3f prevCameraPos;
    private static boolean init = false;

    public static void update(){
        CameraMatrices matrices = VeilRenderSystem.renderer().getCameraMatrices();

        prevProjMat = new Matrix4f(matrices.getProjectionMatrix());
        prevModelViewMat = new Matrix4f(matrices.getViewMatrix());
        prevCameraPos = new Vector3f(matrices.getCameraPosition());

        init = true;
    }

    public static boolean isInitialized(){ return init; }
    public static Matrix4f getPrevProjMat() { return prevProjMat; }
    public static Matrix4f getPrevModelViewMat() { return prevModelViewMat; }
    public static Vector3f getPrevCameraPos() { return prevCameraPos; }
}
