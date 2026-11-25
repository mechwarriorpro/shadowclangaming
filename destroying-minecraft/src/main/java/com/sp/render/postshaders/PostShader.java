package com.sp.render.postshaders;

import net.minecraft.resources.ResourceLocation;

/**
 * NeoForge port — Fabric Identifier -> ResourceLocation, PostPipeline via reflection so offline CI compiles.
 * When Veil is present the context is a PostPipeline.Context; we resolve ShaderProgram reflectively.
 */
public abstract class PostShader {
    protected final ResourceLocation POST;
    protected final ResourceLocation SHADER;
    protected DestructionUniformCallback destructionUniformCallback;

    public PostShader(ResourceLocation post, ResourceLocation shader) { this.POST = post; this.SHADER = shader; }

    public void setUniformCallback(DestructionUniformCallback cb) { this.destructionUniformCallback = cb; }

    public void setUniforms(Object context, float tickDelta, Object client, Object clientLevel) {
        if (context == null) return;
        try {
            Object shaderProgram = context.getClass().getMethod("getShader", ResourceLocation.class).invoke(context, this.SHADER);
            if (shaderProgram != null) this.setUniformsForShader(shaderProgram, tickDelta, client, clientLevel);
        } catch (Exception ignored) {}
    }

    public void setUniformsForShader(Object shaderProgram, float tickDelta, Object client, Object clientLevel) {
        if (this.destructionUniformCallback != null) this.destructionUniformCallback.setUniforms(shaderProgram, tickDelta);
    }

    public ResourceLocation getShader() { return SHADER; }
    public ResourceLocation getPost() { return POST; }

    @FunctionalInterface
    public interface DestructionUniformCallback { void setUniforms(Object shaderProgram, float tickDelta); }
}
