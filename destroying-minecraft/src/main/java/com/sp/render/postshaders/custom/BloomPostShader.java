package com.sp.render.postshaders.custom;

import com.sp.DestroyingMinecraft;
import com.sp.render.postshaders.PostShader;
import com.sp.util.BetterUniforms;
import net.minecraft.resources.ResourceLocation;

public class BloomPostShader extends PostShader {
    public static final ResourceLocation BLOOM_POST = DestroyingMinecraft.idOf("bloom");
    public static final ResourceLocation[] BLUR_IDENTIFIERS = { DestroyingMinecraft.idOf("bloom/blur/horizontal"), DestroyingMinecraft.idOf("bloom/blur/vertical") };
    public BloomPostShader() { super(BLOOM_POST, BLOOM_POST); }
    @Override public void setUniforms(Object context, float tickDelta, Object client, Object clientLevel) {
        if (context == null) return;
        for (ResourceLocation id : BLUR_IDENTIFIERS) {
            try {
                Object prog = context.getClass().getMethod("getShader", ResourceLocation.class).invoke(context, id);
                if (prog != null) { BetterUniforms.setFloat(prog, "blurStrength", 1.0f); BetterUniforms.setFloat(prog, "xLimit", 0.53f); }
            } catch (Exception ignored) {}
        }
    }
}
