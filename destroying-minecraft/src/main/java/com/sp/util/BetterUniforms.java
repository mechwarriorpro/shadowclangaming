package com.sp.util;

import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.render.shader.uniform.ShaderUniform;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

/**
 * Faithful port: original BetterUniforms kept verbatim (no Fabric→NeoForge rename).
 * Veil's ShaderUniform API is identical on both loaders.
 */
public class BetterUniforms {

    public static void setMatrix(ShaderProgram shaderProgram, String charSequence, Matrix4f matrix4f) {
        ShaderUniform uniform = shaderProgram.getUniform(charSequence);
        if(uniform != null) {
            uniform.setMatrix(matrix4f);
        }
    }

    public static void setVector3f(ShaderProgram shaderProgram, String charSequence, Vector3f vector3f) {
        ShaderUniform uniform = shaderProgram.getUniform(charSequence);
        if(uniform != null) {
            uniform.setVector(vector3f);
        }
    }

    public static void setVector2f(ShaderProgram shaderProgram, String charSequence, Vector2f vector2f) {
        ShaderUniform uniform = shaderProgram.getUniform(charSequence);
        if(uniform != null) {
            uniform.setVector(vector2f);
        }
    }

    public static void setFloat(ShaderProgram shaderProgram, String charSequence, float value) {
        ShaderUniform uniform = shaderProgram.getUniform(charSequence);
        if(uniform != null) {
            uniform.setFloat(value);
        }
    }

    public static void setInt(ShaderProgram shaderProgram, String charSequence, int value) {
        ShaderUniform uniform = shaderProgram.getUniform(charSequence);
        if(uniform != null) {
            uniform.setInt(value);
        }
    }
}
