package com.sp.render.postshaders.custom;

import com.sp.DestroyingMinecraft;
import com.sp.render.postshaders.PostShader;
import net.minecraft.resources.ResourceLocation;

public class SupernovaPostShader extends PostShader {
    public static final ResourceLocation SUPERNOVA_POST = DestroyingMinecraft.idOf("supernova");
    public static final ResourceLocation SUPERNOVA_SHADER = DestroyingMinecraft.idOf("supernova/supernova");
    public SupernovaPostShader() { super(SUPERNOVA_POST, SUPERNOVA_SHADER); }
}
