package com.sp.block.entity.client;

import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;

public class PhysicsDoorBlockRenderer implements BlockEntityRenderer<BlockEntity> {
    public PhysicsDoorBlockRenderer(BlockEntityRendererProvider.Context ctx) {}
    @Override public void render(BlockEntity be, float pt, com.mojang.blaze3d.vertex.PoseStack pose, net.minecraft.client.renderer.MultiBufferSource buffers, int light, int overlay) {}
}
