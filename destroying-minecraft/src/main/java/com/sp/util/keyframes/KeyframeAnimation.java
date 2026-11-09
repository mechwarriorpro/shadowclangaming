package com.sp.util.keyframes;

import net.minecraft.world.level.Level;

public class KeyframeAnimation {
    public int skippedTime = 0;
    public long startTime = 0;
    public int getProgress() { return skippedTime; }
    public void run(Level level) {}
    public void skipKeyframe() {}
    public void resetAnimation() {}
    public void resetToCurrentTime(Level level) {}
    public void keyframeSkippedCallback(Runnable r) {}
}
