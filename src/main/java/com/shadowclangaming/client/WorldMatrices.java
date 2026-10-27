package com.shadowclangaming.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import org.joml.Matrix4f;

/**
 * The two matrices the in-world effect shaders are drawn with, built here rather than borrowed.
 *
 * <p>WHY THIS EXISTS. A {@code RenderLevelStageEvent} hands over a pose stack that the level renderer has
 * already been pushing and popping for the whole frame, and the exact state of the camera translation in
 * it is not something a mod can read from the event. Guessing wrong does not fail loudly: the effect
 * simply appears somewhere else, or nowhere, and every earlier build of this feature was rejected for
 * looking wrong. So the effect shaders own their own {@code ScgView}/{@code ScgProj} uniforms, filled from
 * the camera, and the pose stack is left alone. Vertices are submitted in world coordinates and the view
 * matrix here is exactly vanilla's - rotate by pitch, rotate by yaw+180, translate by minus the camera -
 * so a blast at (x, y, z) is drawn at (x, y, z).
 */
public final class WorldMatrices {

    private WorldMatrices() {
    }

    /** Vanilla's view matrix for this camera: world space in, view space out. */
    /**
     * The camera-space transform for this frame, straight from the engine.
     *
     * <p>This used to be rebuilt by hand - rotationX(xRot), rotateY(yRot + 180), translate(-cam). That is
     * only correct if every sign and order convention matches Minecraft's own camera setup, and a single
     * wrong sign projects the proxy cube off-screen. From outside the game that is indistinguishable from
     * a shader that renders nothing: the frame counter climbs either way. The camera argument stays in the
     * signature so callers read the same, but the engine's matrix is the source of truth now.
     */
    public static Matrix4f view(Camera camera) {
        Matrix4f modelView = RenderSystem.getModelViewMatrix();
        return modelView != null ? new Matrix4f(modelView) : new Matrix4f();
    }

    /** The projection matrix Minecraft is drawing the level with this frame. */
    public static Matrix4f projection() {
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        return projection != null ? new Matrix4f(projection) : new Matrix4f();
    }
}
