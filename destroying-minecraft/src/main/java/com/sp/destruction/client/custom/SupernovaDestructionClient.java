package com.sp.destruction.client.custom;

import com.sp.DestroyingMinecraftClient;
import com.sp.destruction.DestructionType;
import com.sp.destruction.client.ClientDestructionEvent;
import com.sp.render.postshaders.PostShaders;
import com.sp.util.BetterUniforms;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import com.sp.util.timer.ShaderTimer;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * Faithful port: original 184 LOC. supernova reuses sky shader (no dedicated PostShader).
 * Uniforms: supernovaTimer/implodeTimer, flashTimer, explosionTimer, laserPos, laserLength, flashFrame.
 */
public class SupernovaDestructionClient extends ClientDestructionEvent {
    private static final ShaderTimer implodeTimer = new ShaderTimer();
    private static final ShaderTimer flashTimer = new ShaderTimer();
    private static final ShaderTimer explosionTimer = new ShaderTimer();
    private static float laserLength;
    private static Vec3 laserPos = Vec3.ZERO;
    private static int flashFrame = -1;

    public SupernovaDestructionClient() { super(DestructionType.SUPERNOVA, PostShaders.SUPERNOVA, 2930); }

    @Override public void resetEvent() {
        implodeTimer.reset(); flashTimer.reset(); explosionTimer.reset(); laserLength = 0;
        DestroyingMinecraftClient.destructionDistance = Integer.MAX_VALUE; super.resetEvent();
    }

    @Override public void setUniforms(ShaderProgram shaderProgram, float tickDelta) {
        BetterUniforms.setVector3f(shaderProgram, "laserPos", new org.joml.Vector3f((float) laserPos.x, (float) laserPos.y, (float) laserPos.z));
        BetterUniforms.setFloat(shaderProgram, "supernovaTimer", implodeTimer.getTimer(tickDelta));
        BetterUniforms.setFloat(shaderProgram, "flashTimer", flashTimer.getTimer(tickDelta));
        BetterUniforms.setFloat(shaderProgram, "explosionTimer", explosionTimer.getTimer(tickDelta));
        BetterUniforms.setFloat(shaderProgram, "laserLength", laserLength);
        BetterUniforms.setInt(shaderProgram, "flashFrame", flashFrame);
    }

    @Override protected KeyframeAnimation initAnimations(Level level) {
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration,
                new Keyframe(0.0f),
                new Keyframe(300.0 / this.duration, () -> {}),
                new Keyframe(1200.0 / this.duration, () -> {}),
                new Keyframe(2200.0 / this.duration, () -> {}, (g, l) -> flashTimer.setTimer((float) Math.min(l * 2.0, 1.0))),
                new Keyframe(2930.0 / this.duration)
        ).globalAction((g, l) -> implodeTimer.setTimer((float) g)).build();
    }
}
