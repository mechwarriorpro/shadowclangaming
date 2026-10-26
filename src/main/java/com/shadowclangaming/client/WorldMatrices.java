package com.shadowclangaming.client;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.world.phys.Vec3;
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
    public static Matrix4f view(Camera camera) {
        Vec3 cam = camera.getPosition();
        return new Matrix4f()
                .rotationX((float) Math.toRadians(camera.getXRot()))
                .rotateY((float) Math.toRadians(camera.getYRot() + 180.0F))
                .translate((float) -cam.x, (float) -cam.y, (float) -cam.z);
    }

    /** The projection matrix Minecraft is drawing the level with this frame. */
    public static Matrix4f projection() {
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        return projection != null ? new Matrix4f(projection) : new Matrix4f();
    }
}
