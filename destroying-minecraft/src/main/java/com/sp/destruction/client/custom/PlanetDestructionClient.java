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

/**
 * Faithful port: original 135 LOC. Only mapping: World→Level, MinecraftClient→Minecraft.
 * planet.fsh uniforms: planetFallTimer (global), flashTimer, destructionDistance (CPU cull).
 */
public class PlanetDestructionClient extends ClientDestructionEvent {
    private static final ShaderTimer planetFallTimer = new ShaderTimer();
    private static final ShaderTimer flashTimer = new ShaderTimer();

    public PlanetDestructionClient() { super(DestructionType.PLANET, PostShaders.PLANET, 2400); }

    @Override public void resetEvent() {
        DestroyingMinecraftClient.destructionDistance = Integer.MAX_VALUE;
        planetFallTimer.reset(); flashTimer.reset();
        try { net.minecraft.client.Minecraft.getInstance().getSoundManager().stop(null); } catch (Exception ignored) {}
        super.resetEvent();
    }

    @Override public void setUniforms(ShaderProgram shaderProgram, float tickDelta) {
        BetterUniforms.setFloat(shaderProgram, "planetFallTimer", planetFallTimer.getTimer(tickDelta));
        BetterUniforms.setFloat(shaderProgram, "flashTimer", flashTimer.getTimer(tickDelta));
    }

    @Override protected KeyframeAnimation initAnimations(Level level) {
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration,
                new Keyframe(0.0, () -> { try { playSound("planet_ambience"); } catch (Exception ignored) {} }),
                new Keyframe(300.0 / this.duration, () -> { try { playSound("planet_rumble"); } catch (Exception ignored) {} }),
                new Keyframe(1800.0 / this.duration),
                new Keyframe(2170.0 / this.duration, () -> {}, (g, l) -> flashTimer.setTimer((float) Math.min(l * 2.0f, 1.0f))),
                new Keyframe(2200.0 / this.duration, () -> {}, (g, l) -> flashTimer.setTimer(1.0f)),
                new Keyframe(2300.0 / this.duration, (g, l) -> DestroyingMinecraftClient.destructionDistance = (int) ((1.0f - l) * 300))
        ).globalAction((g, l) -> planetFallTimer.setTimer((float) g)).build();
    }

    private void playSound(String id) {
        try {
            var loc = com.sp.DestroyingMinecraft.idOf(id);
            var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(loc);
            if (event != null) net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(event, 1.0f, 1.0f));
        } catch (Exception ignored) {}
    }
}
