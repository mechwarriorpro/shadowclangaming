package com.sp.destruction.client.custom;

import com.sp.DestroyingMinecraftClient;
import com.sp.destruction.DestructionType;
import com.sp.destruction.client.ClientDestructionEvent;
import com.sp.render.postshaders.PostShaders;
import com.sp.util.BetterUniforms;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import com.sp.util.timer.ShaderTimer;
import net.minecraft.world.level.Level;

public class PlanetDestructionClient extends ClientDestructionEvent {
    private static final ShaderTimer planetFallTimer = new ShaderTimer();
    private static final ShaderTimer flashTimer = new ShaderTimer();

    public PlanetDestructionClient() { super(DestructionType.PLANET, PostShaders.PLANET, 2400); }

    @Override public void resetEvent() {
        DestroyingMinecraftClient.destructionDistance = Integer.MAX_VALUE;
        planetFallTimer.reset(); flashTimer.reset(); super.resetEvent();
    }

    @Override public void setUniforms(Object shaderProgram, float tickDelta) {
        BetterUniforms.setFloat(shaderProgram, "planetFallTimer", planetFallTimer.getTimer(tickDelta));
        BetterUniforms.setFloat(shaderProgram, "flashTimer", flashTimer.getTimer(tickDelta));
    }

    @Override protected KeyframeAnimation initAnimations(Level level) {
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration,
                new Keyframe(0.0),
                new Keyframe(300.0 / this.duration, () -> {}),
                new Keyframe(1800.0 / this.duration),
                new Keyframe(2170.0 / this.duration, () -> {}, (g, l) -> flashTimer.setTimer((float) Math.min(l * 2.0f, 1.0f))),
                new Keyframe(2200.0 / this.duration, () -> {}, (g, l) -> flashTimer.setTimer(1.0f)),
                new Keyframe(2300.0 / this.duration, (g, l) -> DestroyingMinecraftClient.destructionDistance = (int) ((1.0f - l) * 300))
        ).globalAction((g, l) -> planetFallTimer.setTimer((float) g)).build();
    }
}
