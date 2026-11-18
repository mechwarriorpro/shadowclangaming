package com.sp.render.postshaders.custom;

import com.sp.DestroyingMinecraft;
import com.sp.render.postshaders.PostShader;
import com.sp.util.BetterUniforms;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Faithful port: Fabric Identifier→ResourceLocation, same Veil PostPipeline.Context.
 */
public class BloomPostShader extends PostShader {
    public static final ResourceLocation BLOOM_POST = DestroyingMinecraft.idOf("bloom");
    public static final ResourceLocation[] BLUR_IDENTIFIERS = { DestroyingMinecraft.idOf("bloom/blur/horizontal"), DestroyingMinecraft.idOf("bloom/blur/vertical") };
    public BloomPostShader() { super(BLOOM_POST, BLOOM_POST); }
    @Override public void setUniforms(PostPipeline.Context context, float tickDelta, Minecraft client, Level clientLevel) {
        for (ResourceLocation id : BLUR_IDENTIFIERS) {
            ShaderProgram prog = context.getShader(id);
            if (prog != null) { BetterUniforms.setFloat(prog, "blurStrength", 1.0f); BetterUniforms.setFloat(prog, "xLimit", 0.53f); }
        }
    }
}
