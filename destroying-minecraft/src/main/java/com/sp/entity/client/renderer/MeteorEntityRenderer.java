package com.sp.entity.client.renderer;

import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.resources.ResourceLocation;
import com.sp.DestroyingMinecraft;

public class MeteorEntityRenderer extends EntityRenderer<Entity> {
    public MeteorEntityRenderer(EntityRendererProvider.Context ctx) { super(ctx); }
    @Override public ResourceLocation getTextureLocation(Entity e) { return DestroyingMinecraft.idOf("textures/entity/meteor.png"); }
}
