package com.shadowclangaming.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.shadowclangaming.block.MineBlockEntity;
import com.shadowclangaming.registry.SCGBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Draws a landmine.
 *
 * Two modes, decided per frame by looking at the local player:
 *
 *   no Night Vision   draw the block the mine replaced. A field in a desert is sand; in a forest it is
 *                     dirt and grass; in a stone base it is stone. You cannot tell it is there.
 *   Night Vision      draw the mine itself (its own model and texture), so a prepared defender can sweep
 *                     a field and a prepared attacker knows what they are walking into.
 *
 * The block's render shape is ENTITYBLOCK_ANIMATED, so the chunk mesher never draws it and there is no
 * double image: whatever this renderer draws is the only thing at that position.
 *
 * Drawing another block's model is one call - {@code BlockRenderer.renderSingleBlock} - which is exactly
 * what an item frame or an enderman holding a block uses. No block-state json of our own is involved, so
 * a mine disguised as a 1.21 block we have never heard of still renders correctly.
 */
public class MineRenderer implements BlockEntityRenderer<MineBlockEntity> {

    public MineRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(MineBlockEntity mine, float partialTick, PoseStack pose, MultiBufferSource buffers,
                       int packedLight, int packedOverlay) {
        Minecraft minecraft = Minecraft.getInstance();
        BlockState toDraw = seesTheMine(minecraft)
                ? SCGBlocks.MINE.get().defaultBlockState()
                : mine.mimic();

        BlockPos pos = mine.getBlockPos();
        pose.pushPose();
        // renderSingleBlock draws from 0,0,0 in block space; the renderer is already positioned at the
        // block entity, so nothing needs translating. Light is the block's own light, which is what makes
        // a buried mine sit in the terrain's lighting instead of glowing.
        minecraft.getBlockRenderer().renderSingleBlock(toDraw, pose, buffers,
                packedLight, packedOverlay);
        pose.popPose();
    }

    /**
     * Can this player see through the disguise?
     *
     * Night Vision only. Spectators are also included (they are not really playing), and a player holding
     * the mod's own mine item in either hand gets no help - the reveal is the potion, as asked for.
     */
    private static boolean seesTheMine(Minecraft minecraft) {
        if (minecraft.player == null) {
            return false;
        }
        if (minecraft.player.isSpectator()) {
            return true;
        }
        return minecraft.player.hasEffect(MobEffects.NIGHT_VISION);
    }

    /** Mines are small; the default cull distance is generous enough, but this keeps a big field cheap. */
    @Override
    public boolean shouldRenderOffScreen(MineBlockEntity mine) {
        return false;
    }
}
