package com.sp.destruction.client.custom;

import com.sp.destruction.DestructionType;
import com.sp.destruction.client.ClientDestructionEvent;
import com.sp.render.postshaders.PostShaders;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import net.minecraft.world.level.Level;

public class BlackHoleDestructionClient extends ClientDestructionEvent {
    public BlackHoleDestructionClient() { super(DestructionType.BLACK_HOLE, PostShaders.BLACK_HOLE, 3400); }
    @Override public void setUniforms(Object shaderProgram, float tickDelta) {}
    @Override protected KeyframeAnimation initAnimations(Level level) {
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration, new Keyframe(0.0f)).build();
    }
}
