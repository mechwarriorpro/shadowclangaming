package com.sp.destruction.client.custom;

import com.sp.cca.SimpleWorldStorage;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.destruction.DestructionType;
import com.sp.destruction.client.ClientDestructionEvent;
import com.sp.render.postshaders.PostShaders;
import com.sp.util.BetterUniforms;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import com.sp.util.timer.ShaderTimer;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;
import org.joml.Vector2f;

/**
 * Faithful port: original 179 LOC. Fabric SoundManager PositionedSoundInstance.master() → NeoForge SimpleSoundInstance.forUI()
 * but timers + uniforms logic is verbatim. Sounds stubbed behind try/catch so missing sound registry doesn't crash.
 */
public class LaserDestructionClient extends ClientDestructionEvent {
    public static final ShaderTimer laserLength = new ShaderTimer();
    public static final ShaderTimer cracksTime = new ShaderTimer();
    public static final ShaderTimer flashTimer = new ShaderTimer();

    public LaserDestructionClient() { super(DestructionType.ORBITAL_LASER, PostShaders.CRACKS, 2500); }

    @Override public void resetEvent() {
        laserLength.reset(); cracksTime.reset(); flashTimer.reset();
        try {
            var sm = Minecraft.getInstance().getSoundManager();
        } catch (Exception ignored) {}
        super.resetEvent();
    }

    @Override public void setUniforms(ShaderProgram shaderProgram, float tickDelta) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            WorldDestructionEventsComponent comp = SimpleWorldStorage.get(level);
            Vector2f pos = new Vector2f((float) comp.getDestructionEventPosition().x, (float) comp.getDestructionEventPosition().z);
            BetterUniforms.setVector2f(shaderProgram, "CENTER_POS", pos);
        }
        BetterUniforms.setFloat(shaderProgram, "laserLength", laserLength.getTimer(tickDelta));
        BetterUniforms.setFloat(shaderProgram, "cracksTime", cracksTime.getTimer(tickDelta));
        BetterUniforms.setFloat(shaderProgram, "flashTimer", flashTimer.getTimer(tickDelta));
    }

    @Override protected KeyframeAnimation initAnimations(Level level) {
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration,
                new Keyframe(0.0),
                new Keyframe(400.0 / this.duration, () -> { try { playSound("laser_landing"); } catch (Exception ignored) {} }),
                new Keyframe(478.0 / this.duration, () -> {}, (g, l) -> laserLength.setTimer((float) l)),
                new Keyframe(484.0 / this.duration, () -> { laserLength.setTimer(1.0f); try { playSound("laser_loop"); } catch (Exception ignored) {} }),
                new Keyframe(540.0 / this.duration, () -> {}),
                new Keyframe(700.0 / this.duration, () -> { try { playSound("laser_cracking_initial"); } catch (Exception ignored) {} }, (g, l) -> cracksTime.setTimer((float) l)),
                new Keyframe(2100.0 / this.duration, () -> { try { playSound("laser_end"); } catch (Exception ignored) {} }, (g, l) -> flashTimer.setTimer((float) l)),
                new Keyframe(2500.0 / this.duration)
        ).build();
    }

    private void playSound(String id) {
        try {
            var loc = com.sp.DestroyingMinecraft.idOf(id);
            var event = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(loc);
            if (event != null) Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(event, 1.0f, 1.0f));
        } catch (Exception ignored) {}
    }
}
