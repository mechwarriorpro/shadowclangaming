package com.sp.render.postshaders.custom;

import com.sp.DestroyingMinecraft;
import com.sp.render.postshaders.PostShader;
import net.minecraft.resources.ResourceLocation;

public class CracksPostShader extends PostShader {
    public static final ResourceLocation CRACKS_HOLE_POST = DestroyingMinecraft.idOf("cracks");
    public static final ResourceLocation CRACKS_HOLE_SHADER = DestroyingMinecraft.idOf("cracks/cracks");
    public CracksPostShader() { super(CRACKS_HOLE_POST, CRACKS_HOLE_SHADER); }
}
