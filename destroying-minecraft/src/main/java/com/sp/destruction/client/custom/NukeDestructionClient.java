package com.sp.destruction.client.custom;

import com.sp.cca.SimpleWorldStorage;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.destruction.DestructionType;
import com.sp.destruction.client.ClientDestructionEvent;
import com.sp.render.postshaders.PostShaders;
import com.sp.util.BetterUniforms;
import com.sp.util.timer.ShaderTimer;
import foundry.veil.api.client.render.shader.program.ShaderProgram;
import foundry.veil.api.client.util.Easing;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Faithful port: original tick() uses World.getTime() + Easing.EASE_OUT_SINE.
 * Fabric World.getTime() → NeoForge Level.getGameTime() (same tick clock).
 * InitializeComponents.EVENTS.get(world) → SimpleWorldStorage.get(level) (CCA → AttachmentType shim).
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
        if(this.active) {
            if(this.startTime == -1L) this.startTime = level.getGameTime();

            if(level.getGameTime() < this.startTime){
                this.resetEvent();
            }

            this.floatProgress = (float) Math.min((double) (level.getGameTime() - this.startTime) / this.duration, 1.0);
            if (this.floatProgress <= this.duration) {
                smokeRiseTimer.setTimer(Easing.EASE_OUT_SINE.ease(this.floatProgress));
                flashTimer.setTimer(Easing.EASE_OUT_SINE.ease(Math.min(this.floatProgress*2.75f, 1.0f)));
            }
        } else {
            this.resetEvent();
        }
    }

    @Override
    public void resetEvent() {
        smokeRiseTimer.reset();
        flashTimer.reset();
        this.startTime = -1;
        super.resetEvent();
    }

    @Override
    public void setUniforms(ShaderProgram shaderProgram, float tickDelta) {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            WorldDestructionEventsComponent component = SimpleWorldStorage.get(level);
            BetterUniforms.setVector3f(shaderProgram, "NUKE_POS", component.getDestructionEventPosition().toVector3f());
        }
        BetterUniforms.setFloat(shaderProgram, "smokeRiseTimer", smokeRiseTimer.getTimer(tickDelta));
        BetterUniforms.setFloat(shaderProgram, "flashTimer", flashTimer.getTimer(tickDelta));
    }
}
