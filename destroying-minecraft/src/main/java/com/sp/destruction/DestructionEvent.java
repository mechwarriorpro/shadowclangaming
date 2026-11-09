package com.sp.destruction;

import com.sp.cca.InitializeComponents;
import com.sp.util.keyframes.KeyframeAnimation;
import net.minecraft.world.level.Level;

public abstract class DestructionEvent {
    private final DestructionType destructionType;
    private final boolean isClient;
    protected boolean active;
    protected boolean initAnimations;
    protected KeyframeAnimation animation;
    protected final int duration;

    public DestructionEvent(DestructionType destructionType, int duration, boolean isClient) {
        this.destructionType = destructionType;
        this.duration = duration;
        this.isClient = isClient;
    }

    public void tick(Level level) {
        if (!initAnimations) {
            animation = initAnimations(level);
            if (animation != null) animation.keyframeSkippedCallback(() -> InitializeComponents.EVENTS.get(level).syncLight());
            initAnimations = true;
        }
        if(animation != null) {
            if (this.isActive()) {
                this.animation.run(level);
                if ((animation.getProgress() % 200 == 0 && !level.isClientSide)) {
                    InitializeComponents.EVENTS.get(level).syncLight();
                }
            } else {
                this.resetEvent();
            }
        }
    }

    public void resetAnimationToCurrentTime(Level level) {
        if (this.animation != null) this.animation.resetToCurrentTime(level);
    }
    protected void skipKeyframe() { if (animation != null) animation.skipKeyframe(); }
    protected KeyframeAnimation initAnimations(Level level) { return null; }
    public int getProgress() { return animation != null ? animation.skippedTime : 0; }
    public void setProgress(int progress) { if (this.isClient() && this.animation != null) animation.skippedTime = progress; }
    public void resetAnimations() { this.initAnimations = false; this.animation = null; }
    public void resetEvent() { this.active = false; if (this.animation != null) this.animation.resetAnimation(); }
    public boolean isClient() { return this.isClient; }
    public boolean isActive() { return this.active; }
    public DestructionType getDestructionType(){ return this.destructionType; }
    public void setActive(boolean active, long startTime) {
        if (active && this.animation != null) this.animation.startTime = startTime;
        this.active = active;
    }
}
