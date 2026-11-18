package com.sp.util.keyframes;

public class Keyframe {
    private boolean initialized;
    private final double keyframeTime;
    private final Runnable initAction;
    private final KeyframeAction action;

    public Keyframe(double keyframeTime) {
        this(keyframeTime, () -> {}, (globalTime, localTime) -> {});
    }

    public Keyframe(double keyframeTime, KeyframeAction action) {
        this(keyframeTime, () -> {}, action);
    }

    public Keyframe(double keyframeTime, Runnable initAction) {
        this(keyframeTime, initAction, (globalTime, localTime) -> {});
    }

    public Keyframe(double keyframeTime, Runnable initAction, KeyframeAction action) {
        this.keyframeTime = keyframeTime;
        this.initAction = initAction;
        this.action = action;
    }

    protected KeyframeAction getAction() { return this.action; }
    protected Runnable getInitAction() { return this.initAction; }
    protected double getKeyframeTime() { return this.keyframeTime; }
    protected boolean isInitialized() { return this.initialized; }
    protected void setInitialized(boolean initialized) { this.initialized = initialized; }

    @FunctionalInterface
    public interface KeyframeAction {
        void run(double globalTime, double localTime);
    }
}
