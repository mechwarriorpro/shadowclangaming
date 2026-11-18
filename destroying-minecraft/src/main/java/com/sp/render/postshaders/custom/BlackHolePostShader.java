package com.sp.render.postshaders.custom;

import com.sp.DestroyingMinecraft;
import com.sp.cca.SimpleWorldStorage;
import com.sp.render.PrevUniforms;
import com.sp.render.postshaders.PostShader;
import com.sp.util.BetterUniforms;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.joml.Vector3f;

/**
 * Faithful port: Identifier→ResourceLocation, MinecraftClient→Minecraft, World→Level.
 * Veil ShaderProgram API identical between loaders.
 */
public class BlackHolePostShader extends PostShader {
    public static final ResourceLocation BLACK_HOLE_POST = DestroyingMinecraft.idOf("black_hole");
    public static final ResourceLocation BLACK_HOLE_SHADER = DestroyingMinecraft.idOf("blackhole/black_hole");
    public BlackHolePostShader() { super(BLACK_HOLE_POST, BLACK_HOLE_SHADER); }
    @Override public void setUniformsForShader(ShaderProgram shaderProgram, float tickDelta, Minecraft client, Level clientLevel) {
        super.setUniformsForShader(shaderProgram, tickDelta, client, clientLevel);
        if (PrevUniforms.isInitialized()) {
            BetterUniforms.setMatrix(shaderProgram, "prevProjMat", PrevUniforms.getPrevProjMat());
            BetterUniforms.setMatrix(shaderProgram, "prevViewMat", PrevUniforms.getPrevModelViewMat());
            BetterUniforms.setVector3f(shaderProgram, "prevCameraPos", PrevUniforms.getPrevCameraPos());
        }
        Level lvl = client.level;
        if (lvl == null) lvl = clientLevel;
        if (lvl != null) {
            var comp = SimpleWorldStorage.get(lvl);
            float redMul = (float) Math.max(1.0f - comp.getGravityLerp(), 0.01f);
            BetterUniforms.setVector3f(shaderProgram, "redMultiplier", new Vector3f(1.0f, redMul, redMul));
        }
        PrevUniforms.update();
    }
}
