package com.sp.render.postshaders.custom;

import com.sp.DestroyingMinecraft;
import com.sp.render.postshaders.PostShader;
import net.minecraft.resources.ResourceLocation;

public class PostProcessingPostShader extends PostShader {
    public static final ResourceLocation POST_POST = DestroyingMinecraft.idOf("post");
    public static final ResourceLocation POST_SHADER = DestroyingMinecraft.idOf("post/post");
    public PostProcessingPostShader() { super(POST_POST, POST_SHADER); }
}
