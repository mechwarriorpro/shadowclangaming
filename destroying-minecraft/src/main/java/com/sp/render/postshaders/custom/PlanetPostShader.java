package com.sp.render.postshaders.custom;

import com.sp.DestroyingMinecraft;
import com.sp.render.postshaders.PostShader;
import net.minecraft.resources.ResourceLocation;

public class PlanetPostShader extends PostShader {
    public static final ResourceLocation PLANET_POST = DestroyingMinecraft.idOf("planet");
    public static final ResourceLocation PLANET_SHADER = DestroyingMinecraft.idOf("planet/planet");
    public PlanetPostShader() { super(PLANET_POST, PLANET_SHADER); }
}
