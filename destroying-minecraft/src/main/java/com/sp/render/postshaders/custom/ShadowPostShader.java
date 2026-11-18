package com.sp.render.postshaders.custom;

import com.sp.DestroyingMinecraft;
import com.sp.render.postshaders.PostShader;
import net.minecraft.resources.ResourceLocation;

public class ShadowPostShader extends PostShader {
    public static final ResourceLocation SHADOW_POST = DestroyingMinecraft.idOf("shadows");
    public static final ResourceLocation SHADOW_SHADER = DestroyingMinecraft.idOf("shadows/shadows");
    public ShadowPostShader() { super(SHADOW_POST, SHADOW_SHADER); }
}
