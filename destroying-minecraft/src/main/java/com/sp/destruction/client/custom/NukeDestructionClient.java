package com.sp.destruction.client.custom;

import com.sp.cca.SimpleWorldStorage;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.destruction.DestructionType;
import com.sp.destruction.client.ClientDestructionEvent;
import com.sp.render.postshaders.PostShaders;
import com.sp.util.BetterUniforms;
import com.sp.util.timer.ShaderTimer;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * NeoForge port — Fabric World -> Level, Vec3d -> Vec3, MathHelper -> Mth, Easing via Mth.
 * Keeps same shader timers so nuke.fsh visuals replay 1:1.
 */
public class NukeDestructionClient extends ClientDestructionEvent {
    private static final ShaderTimer smokeRiseTimer = new ShaderTimer();
    private static final ShaderTimer flashTimer = new ShaderTimer();
    private long startTime = -1L;
    private float floatProgress;

    public NukeDestructionClient() {
        super(DestructionType.NUKE, PostShaders.NUKE, 100);
    }

    @Override
    public void tick(Level level) {
        if (this.active) {
            if (this.startTime == -1L) this.startTime = level.getGameTime();
            if (level.getGameTime() < this.startTime) { this.resetEvent(); return; }
            this.floatProgress = (float) Math.min((double) (level.getGameTime() - this.startTime) / this.duration, 1.0);
            if (this.floatProgress <= this.duration) {
                smokeRiseTimer.setTimer(easeOutSine(this.floatProgress));
                flashTimer.setTimer(easeOutSine(Math.min(this.floatProgress * 2.75f, 1.0f)));
            }
        } else {
            this.resetEvent();
        }
    }

    @Override
    public void resetEvent() {
        smokeRiseTimer.reset(); flashTimer.reset(); this.startTime = -1; super.resetEvent();
    }

    @Override
    public void setUniforms(Object shaderProgram, float tickDelta) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            WorldDestructionEventsComponent comp = SimpleWorldStorage.get(level);
            try {
                BetterUniforms.setVector3f(shaderProgram, "NUKE_POS", new org.joml.Vector3f((float) comp.getDestructionEventPosition().x, (float) comp.getDestructionEventPosition().y, (float) comp.getDestructionEventPosition().z));
            } catch (Exception ignored) {}
        }
        BetterUniforms.setFloat(shaderProgram, "smokeRiseTimer", smokeRiseTimer.getTimer(tickDelta));
        BetterUniforms.setFloat(shaderProgram, "flashTimer", flashTimer.getTimer(tickDelta));
    }

    private static float easeOutSine(float x) { return (float) Math.sin(x * Math.PI * 0.5); }
}
