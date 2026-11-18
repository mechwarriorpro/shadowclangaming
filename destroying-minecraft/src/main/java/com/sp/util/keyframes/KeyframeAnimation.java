package com.sp.util.keyframes;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import net.minecraft.world.level.Level;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * NeoForge port of KeyframeAnimation (original 215 LOC, Fabric World#getTime()).
 * Maps World -> Level, uses Level#getGameTime() (same tick clock).
 * Logic identical to SpacePotatoee original so 5 DestructionTypes replay frame-accurate.
 */
public class KeyframeAnimation {
    private final List<Keyframe> keyframeList;
    private final Keyframe.KeyframeAction globalAction;
    private final Runnable endAction;
    private final int duration;
    public long startTime = -1L;
    private int progress;
    private boolean shouldSkipKeyframe;
    private Runnable keyframeSkippedCallback = () -> {};
    public int skippedTime;
    private final Int2IntArrayMap skippingCache = new Int2IntArrayMap();
    private int currentKeyframeIndex;
    private boolean endActionRan;

    private KeyframeAnimation(int duration, Keyframe.KeyframeAction globalAction, Runnable endAction, Keyframe... keyframes) {
        if (keyframes.length == 0) throw new RuntimeException("Cannot make a keyframe animation with zero keyframes");
        this.keyframeList = Arrays.stream(keyframes).sorted((o1, o2) -> {
            int comp = Double.compare(o1.getKeyframeTime(), o2.getKeyframeTime());
            if (comp == 0) throw new RuntimeException("Keyframes cannot have the same time value");
            return comp;
        }).toList();
        this.duration = duration;
        this.globalAction = globalAction;
        this.endAction = endAction;
    }

    public void run(Level level) {
        if (this.startTime == -1) this.startTime = level.getGameTime();
        this.progress = Math.toIntExact(level.getGameTime() - this.startTime) + this.skippedTime;
        if (this.shouldSkipKeyframe) {
            int nextKeyframeTime = (int) Math.floor(this.duration * this.getNextKeyframeTime());
            this.skippedTime += nextKeyframeTime - this.progress;
            this.skippingCache.put(Math.toIntExact(level.getGameTime()), this.skippedTime);
            this.shouldSkipKeyframe = false;
            this.keyframeSkippedCallback.run();
        }
        this.updateKeyframeAnimation((double) this.progress / this.duration);
    }

    public void updateKeyframeAnimation(double time) {
        if (time >= 1.0) {
            if (!endActionRan) { endAction.run(); endActionRan = true; }
            return;
        }
        Keyframe currentKeyframe = this.getCurrentKeyframe();
        Keyframe nextKeyframe = this.getNextKeyframe();
        if (nextKeyframe != null && nextKeyframe.getKeyframeTime() <= time) {
            currentKeyframe = nextKeyframe;
            currentKeyframeIndex++;
            nextKeyframe = currentKeyframeIndex + 1 < keyframeList.size() - 1 ? keyframeList.get(currentKeyframeIndex + 1) : null;
        }
        if (!currentKeyframe.isInitialized()) {
            currentKeyframe.getInitAction().run();
            currentKeyframe.setInitialized(true);
        }
        double currentKeyframeTime = currentKeyframe.getKeyframeTime();
        double nextKeyframeTime = nextKeyframe != null ? nextKeyframe.getKeyframeTime() : 1.0;
        double localTime = (time - currentKeyframeTime) / (nextKeyframeTime - currentKeyframeTime);
        currentKeyframe.getAction().run(time, localTime);
        globalAction.run(time, time);
    }

    public void resetToCurrentTime(Level level) {
        int worldTime = Math.toIntExact(level.getGameTime());
        if (level.getGameTime() < this.startTime) { this.resetAnimation(); return; }
        this.skippedTime = 0;
        Map.Entry<Integer, Integer> maxEntry = null;
        for (Map.Entry<Integer, Integer> entry : this.skippingCache.int2IntEntrySet()) {
            if (entry.getKey() <= worldTime && (maxEntry == null || maxEntry.getKey() < entry.getKey())) maxEntry = entry;
        }
        if (maxEntry != null) this.skippedTime = maxEntry.getValue();
        this.progress = Math.toIntExact(level.getGameTime() - this.startTime) + this.skippedTime;
        float time = (float) this.progress / this.duration;
        for (int i = 0; i < keyframeList.size(); i++) {
            Keyframe kf = keyframeList.get(i);
            if (i + 1 >= keyframeList.size()) { this.currentKeyframeIndex = i; break; }
            Keyframe next = keyframeList.get(i + 1);
            if (kf.getKeyframeTime() < time && next.getKeyframeTime() > time) { this.currentKeyframeIndex = i; break; }
        }
    }

    public void keyframeSkippedCallback(Runnable callback) { this.keyframeSkippedCallback = callback; }
    public double getNextKeyframeTime() { Keyframe n = this.getNextKeyframe(); return n == null ? 1.0 : n.getKeyframeTime(); }
    public void skipKeyframe() { this.shouldSkipKeyframe = true; }
    public int getProgress() { return progress; }
    private Keyframe getCurrentKeyframe() { return keyframeList.get(currentKeyframeIndex); }
    private Keyframe getNextKeyframe() { return currentKeyframeIndex + 1 <= keyframeList.size() - 1 ? keyframeList.get(currentKeyframeIndex + 1) : null; }

    public void resetAnimation() {
        this.progress = 0; this.startTime = -1L; this.shouldSkipKeyframe = false;
        currentKeyframeIndex = 0; endActionRan = false; skippingCache.clear(); this.skippedTime = 0;
        for (Keyframe kf : keyframeList) kf.setInitialized(false);
    }

    public static class KeyframeAnimationBuilder {
        private final int duration;
        private final Keyframe[] keyframeList;
        private Keyframe.KeyframeAction globalAction = (g, l) -> {};
        private Runnable endAction = () -> {};
        public KeyframeAnimationBuilder(int duration, Keyframe... keyframes) { this.duration = duration; this.keyframeList = keyframes; }
        public KeyframeAnimationBuilder globalAction(Keyframe.KeyframeAction a) { this.globalAction = a; return this; }
        public KeyframeAnimationBuilder endAction(Runnable a) { this.endAction = a; return this; }
        public KeyframeAnimation build() { return new KeyframeAnimation(this.duration, this.globalAction, this.endAction, this.keyframeList); }
    }
}
