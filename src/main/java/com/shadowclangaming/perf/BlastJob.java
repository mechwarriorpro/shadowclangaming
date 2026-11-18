package com.shadowclangaming.perf;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * One queued explosion.
 *
 * The section lists are produced on a worker thread and published through a volatile field, which is
 * the only safe way to hand data from a worker to the server thread. The server thread only ever
 * READS them, and it owns all world access.
 */
public final class BlastJob {

    /**
     * @param sectionOrigin minimum corner of the 16^3 section
     * @param positions     absolute block positions packed with BlockPos.asLong
     * @param fullyInterior true when the whole section is inside the sphere (no distance checks needed)
     */
    public record SectionWork(net.minecraft.core.BlockPos sectionOrigin, long[] positions, boolean fullyInterior) {
    }

    public final ServerLevel level;
    public final Vec3 centre;
    public final int radius;
    public final float power;

    /** Written by the planner thread, read by the server thread. */
    private volatile List<SectionWork> sections;
    private volatile boolean planned = false;
    private volatile boolean failed = false;

    private int applied = 0;

    public BlastJob(ServerLevel level, Vec3 centre, int radius, float power) {
        this.level = level;
        this.centre = centre;
        this.radius = radius;
        this.power = power;
        this.sections = List.of();
    }

    void completePlanning(List<SectionWork> plannedSections) {
        this.sections = plannedSections;
        this.planned = true;
    }

    void fail() {
        this.failed = true;
        this.planned = true;
    }

    public boolean isPlanned() {
        return planned;
    }

    public boolean hasFailed() {
        return failed;
    }

    public List<SectionWork> sections() {
        return sections;
    }

    public int appliedSections() {
        return applied;
    }

    public void markApplied(int count) {
        applied += count;
    }

    public boolean isFinished() {
        return planned && (failed || applied >= sections.size());
    }

    public float progress() {
        if (!planned) {
            return 0.0F;
        }
        return sections.isEmpty() ? 1.0F : (float) applied / sections.size();
    }
}
