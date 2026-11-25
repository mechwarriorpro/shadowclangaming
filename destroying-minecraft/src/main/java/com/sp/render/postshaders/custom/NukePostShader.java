package com.sp.render.postshaders.custom;

import com.sp.DestroyingMinecraft;
import com.sp.render.postshaders.PostShader;
import net.minecraft.resources.ResourceLocation;

public class NukePostShader extends PostShader {
    public static final ResourceLocation NUKE_POST = DestroyingMinecraft.idOf("nuke");
    public static final ResourceLocation NUKE_SHADER = DestroyingMinecraft.idOf("nuke/nuke");
    public NukePostShader() { super(NUKE_POST, NUKE_SHADER); }
}
