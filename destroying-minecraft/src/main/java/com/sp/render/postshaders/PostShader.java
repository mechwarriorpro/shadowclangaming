package com.sp.render.postshaders;

import com.sp.destruction.client.ClientDestructionEvent;
import com.sp.destruction.client.ClientDestructionEvents;
import foundry.veil.api.client.render.post.PostPipeline;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Faithful Fabric→NeoForge port: original 63 LOC kept verbatim.
 * Fabric Identifier → Mojmap ResourceLocation (NeoForge 21.1.264). No other changes.
 * Veil API is identical between veil-fabric-2.3.2 and veil-neoforge-2.3.2 (same package).
 */
public abstract class PostShader {
    protected final ResourceLocation POST;
    protected final ResourceLocation SHADER;
    protected DestructionUniformCallback destructionUniformCallback;

    public PostShader(ResourceLocation post, ResourceLocation shader){
        this.POST = post;
        this.SHADER = shader;
    }

    public void setUniformCallback(DestructionUniformCallback destructionUniformCallback) {
        this.destructionUniformCallback = destructionUniformCallback;
    }

    public void setUniforms(PostPipeline.Context context, float tickDelta, Minecraft client, Level clientLevel) {
        ShaderProgram shaderProgram = context.getShader(this.SHADER);
        if (shaderProgram != null) {
            this.setUniformsForShader(shaderProgram, tickDelta, client, clientLevel);
        }
    }

    public void setUniformsForShader(ShaderProgram shaderProgram, float tickDelta, Minecraft client, Level clientLevel) {
        if (this.destructionUniformCallback != null) {
            this.destructionUniformCallback.setUniforms(shaderProgram, tickDelta);
        }
    }

    public ResourceLocation getShader() { return SHADER; }
    public ResourceLocation getPost() { return POST; }

    public interface DestructionUniformCallback {
        void setUniforms(ShaderProgram shaderProgram, float tickDelta);
    }
}
