package com.shadowclangaming.computer;

/**
 * How much thinking a computer can do.
 *
 * The terminal used to be one machine with one speed: whatever it did, it did at once. That makes for a
 * boring upgrade path, so a terminal now has a CPU - one that ships with the block and can be replaced,
 * or added to by racks - and everything the machine does is measured against it.
 *
 * The numbers are deliberately not Minecraft-shaped. A CPU has cores, a clock, a number of jobs it can
 * hold at once, a power draw and a latency (how many ticks an orbital node takes to chew through one
 * queued subcommand). Those five numbers are the entire model, and {@link ComputeBudget} is the only
 * place that adds them up.
 *
 *   BASIC      the CPU in the terminal's box. One core, slow, cheap to run.
 *   ADVANCED   two cores; a terminal can hold this, and so can a rack.
 *   QUANTUM    four cores. Racks only - it does not fit through the terminal's hatch.
 *   PHOTONIC   eight cores. Racks only, and it drinks power.
 */
public enum CpuTier {
    BASIC("basic", 1, 800, 1, 4, 20, 1),
    ADVANCED("advanced", 2, 1600, 2, 12, 10, 2),
    QUANTUM("quantum", 4, 3200, 4, 32, 5, 3),
    PHOTONIC("photonic", 8, 6400, 8, 80, 2, 4);

    /** How many CPU modules one network rack can hold. */
    public static final int RACK_SLOTS = 2;

    private final String id;
    private final int cores;
    private final int clockMhz;
    private final int jobs;
    private final int fePerTick;
    private final int latencyTicks;
    private final int tier;

    CpuTier(String id, int cores, int clockMhz, int jobs, int fePerTick, int latencyTicks, int tier) {
        this.id = id;
        this.cores = cores;
        this.clockMhz = clockMhz;
        this.jobs = jobs;
        this.fePerTick = fePerTick;
        this.latencyTicks = latencyTicks;
        this.tier = tier;
    }

    public String id() {
        return id;
    }

    public int cores() {
        return cores;
    }

    public int clockMhz() {
        return clockMhz;
    }

    /** How many subcommands this CPU can have in flight at once. */
    public int jobs() {
        return jobs;
    }

    public int fePerTick() {
        return fePerTick;
    }

    /** Ticks an orbital processing node spends on one queued subcommand. */
    public int latencyTicks() {
        return latencyTicks;
    }

    public int tier() {
        return tier;
    }

    /** The terminal's built-in means a replacement only goes in if it is actually better. */
    public boolean betterThan(CpuTier other) {
        return other == null || tier > other.tier;
    }

    public String itemId() {
        return "cpu_" + id;
    }

    public String translationKey() {
        return "cpu.shadowclangaming." + id;
    }

    public static CpuTier byId(String id) {
        for (CpuTier tier : values()) {
            if (tier.id.equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return BASIC;
    }
}
