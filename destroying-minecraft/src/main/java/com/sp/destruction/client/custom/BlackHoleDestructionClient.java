package com.sp.destruction.client.custom;

import com.sp.destruction.DestructionType;
import com.sp.destruction.client.ClientDestructionEvent;
import com.sp.render.postshaders.PostShaders;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.world.level.Level;

/**
 * Faithful port: original BlackHoleDestructionClientPart2 is 86 LOC but almost entirely commented out.
 * Active code is just new KeyframeAnimation(0.0f) — kept verbatim.
 * BlackHole visuals are driven by PostShader's PrevUniforms + gravityLerp, not client keyframes.
 */
public class BlackHoleDestructionClient extends ClientDestructionEvent {
    public BlackHoleDestructionClient() { super(DestructionType.BLACK_HOLE, PostShaders.BLACK_HOLE, 3400); }
    @Override public void setUniforms(ShaderProgram shaderProgram, float tickDelta) {}
    @Override protected KeyframeAnimation initAnimations(Level level) {
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration, new Keyframe(0.0f)).build();
    }
}
