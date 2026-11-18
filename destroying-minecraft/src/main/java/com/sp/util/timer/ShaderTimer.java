package com.sp.util.timer;

import net.minecraft.util.Mth;

public class ShaderTimer {
    private float timer;
    private float prevTimer;

    public void setTimer(float timer) {
        this.prevTimer = this.timer;
        this.timer = timer;
    }

    public void maxTimer() {
        this.prevTimer = 1.0f;
        this.timer = 1.0f;
    }

    public void reset() {
        this.prevTimer = 0.0f;
        this.timer = 0.0f;
    }

    public float getTimer(float tickDelta) {
        return Mth.lerp(tickDelta, this.prevTimer, this.timer);
    }
}
