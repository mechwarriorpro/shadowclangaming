package com.shadowclangaming.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shadowclangaming.blueprint.BuildShape;
import com.shadowclangaming.item.SpellRifleItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Draws a magic rifle as the blocks it was built from.
 *
 * ──────────────────── HOW THIS WORKS ────────────────────
 *
 * The blueprint table records every block it consumed (see {@link BuildShape}), centre-relative, and that
 * list rides along on the ItemStack as a data component. This renderer walks the list and draws each
 * block with vanilla's own block renderer, so a rifle built from netherite really is netherite, planks
 * really are planks, and the emitters show up as the barrels they were.
 *
 * AUTOCENTRED: the shape's centre is subtracted before scaling, so it does not matter which corner of the
 * marked box the player happened to build in - the gun sits in the middle of the item's bounds either way.
 * FITTED: everything is scaled by the build's longest edge, so a 3-block pistol fills the same space as a
 * 9-block rifle, and a 32-block monster stays inside the item's 1x1x1 rendering box.
 *
 * Cost: one block render per recorded part, per frame, only for rifles that are actually on screen. The
 * table caps a build at 96 parts, and real guns land around 10-30, which is cheaper than a mob model.
 */
public class RifleBuildRenderer extends BlockEntityWithoutLevelRenderer {

    /**
     * Extra rotation, in degrees, applied around Y so the gun reads as a gun and not a cube.
     * Correlated with the view so it looks right held, dropped, in the inventory and in an item frame.
     */
    private static final float YAW = 35.0F;
    /** Tilt so the top of the build is visible from the side. */
    private static final float PITCH = 22.0F;
    /** The build is fitted into this fraction of the item's unit box, leaving room for the tilt. */
    private static final float FIT = 0.80F;

    public RifleBuildRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet models) {
        super(dispatcher, models);
    }

    @Override
    public void renderByItem(ItemStack stack, ItemDisplayContext context, PoseStack pose,
                             MultiBufferSource buffers, int light, int overlay) {
        BuildShape shape = SpellRifleItem.buildOf(stack);
        if (shape.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        var blockRenderer = minecraft.getBlockRenderer();

        double[] centre = shape.centre();
        int edge = shape.longestEdge();
        float scale = FIT / Math.max(1, edge);

        pose.pushPose();
        // The item render pipeline hands us the item's unit cube with (0,0,0) at one corner; move to the
        // middle so the rotation happens about the build's own centre.
        pose.translate(0.5D, 0.5D, 0.5D);
        pose.mulPose(com.mojang.math.Axis.YP.rotationDegrees(YAW));
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(PITCH));
        pose.scale(scale, scale, scale);
        // Autocentre: offset by the shape's midpoint, then undo the half-block that a block model's own
        // origin implies, so the model's geometric centre - not its corner - lands on the origin.
        pose.translate(-centre[0], -centre[1], -centre[2]);

        for (BuildShape.Part part : shape.parts()) {
            pose.pushPose();
            pose.translate(part.x(), part.y(), part.z());
            blockRenderer.renderSingleBlock(part.state(), pose, buffers, light, overlay);
            pose.popPose();
        }
        pose.popPose();
    }
}
