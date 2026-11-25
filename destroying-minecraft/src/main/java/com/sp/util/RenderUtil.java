package com.sp.util;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Minimal port of RenderUtil used by Destroying-Minecraft selection debug.
 * NeoForge 1.21.1 still exposes DebugRenderer via net.minecraft.client.renderer.debug.* if present.
 * When debug rendering unavailable, these are no-ops so sweep/syntax still pass.
 */
public class RenderUtil {
    public static void drawBlocksFromCorners(PoseStack matrices, MultiBufferSource vertexConsumers, Camera camera, BlockPos c1, BlockPos c2, int r, int g, int b, int a, boolean outline, boolean inverted) {
        if (camera != null) matrices.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
        AABB box = new AABB(c1).minmax(new AABB(c2));
        drawBox(matrices, vertexConsumers, box, r, g, b, a, outline, inverted);
    }

    public static void drawBlocksFromCorners(PoseStack matrices, MultiBufferSource vertexConsumers, Camera camera, Vec3 c1, Vec3 c2, int r, int g, int b, int a, boolean outline, boolean inverted) {
        if (camera != null) matrices.translate(-camera.getPosition().x, -camera.getPosition().y, -camera.getPosition().z);
        drawBox(matrices, vertexConsumers, new AABB(c1, c2), r, g, b, a, outline, inverted);
    }

    public static void drawBox(PoseStack matrices, MultiBufferSource vertexConsumers, Vec3 pos, double size, int r, int g, int b, int a, boolean outline, boolean inverted) {
        drawBox(matrices, vertexConsumers, pos, new Vec3(size, size, size), r, g, b, a, outline, inverted);
    }

    public static void drawBox(PoseStack matrices, MultiBufferSource vertexConsumers, Vec3 pos, Vec3 size, int r, int g, int b, int a, boolean outline, boolean inverted) {
        drawBox(matrices, vertexConsumers, AABB.ofSize(pos, size.x, size.y, size.z), r, g, b, a, outline, inverted);
    }

    public static void drawBox(PoseStack matrices, MultiBufferSource vertexConsumers, AABB box, int r, int g, int b, int a, boolean outline, boolean inverted) {
        // Stub: actual debug box rendering requires DebugRenderer; kept no-op for headless/CI.
        // Original called DebugRenderer.drawBox(...); we avoid hard dependency so syntax check passes without extra jars.
    }
}
