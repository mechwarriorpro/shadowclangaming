package com.sp.util.timer;

public class MsTimer {
    private long pauseTime;
    private long startTime = -1;
    private long startPauseTime = -1;
    private long tempPauseTime;

    public void start() {
        if (this.startTime == -1) this.startTime = System.currentTimeMillis();
    }

    public void pause() {
        if (this.startTime != -1 && this.startPauseTime == -1) this.startPauseTime = System.currentTimeMillis();
        this.tempPauseTime = System.currentTimeMillis() - this.startPauseTime;
    }

    public void resume() {
        if (this.startPauseTime != -1) {
            this.startPauseTime = -1;
            this.pauseTime += tempPauseTime;
            this.tempPauseTime = 0;
        }
    }

    public long getTime() {
        return System.currentTimeMillis() - startTime - pauseTime - tempPauseTime;
    }

    public void stop() {
        this.startTime = -1;
        this.startPauseTime = -1;
        this.pauseTime = 0;
        this.tempPauseTime = 0;
    }
}
