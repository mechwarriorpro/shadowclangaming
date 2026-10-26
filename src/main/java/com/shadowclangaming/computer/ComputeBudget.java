package com.shadowclangaming.computer;

import java.util.List;

/**
 * The one place the machine's numbers are added up.
 *
 * Everything else in the mod asks this record what it is allowed to do, so there is exactly one
 * upgrade curve to reason about and exactly one place to unit test. Nothing in here touches a Level,
 * which is why the arithmetic can be exercised by a plain JVM harness.
 *
 * The model:
 *
 *   cores          terminal CPU cores + rack CPU cores + 2 per orbital node
 *   jobs           terminal CPU jobs + rack CPU jobs + 1 per orbital node, capped at
 *                  {@link #MAX_JOBS}; this is how many subcommands may be queued or in flight
 *   blastThreads   GPU threads from every rack, capped at {@link #MAX_BLAST_THREADS}
 *   radarPipelines GPU radar pipelines + one per two orbital nodes, capped at
 *                  {@link #MAX_RADAR_PIPELINES}; each pipeline is 4 extra radar contacts
 *   fePerTick      the machine's idle draw; informational, it is what the rack's meter reads
 */
public record ComputeBudget(
        CpuTier cpu,
        GpuTier gpu,
        int cores,
        int jobs,
        int blastThreads,
        int radarPipelines,
        int fePerTick,
        int racks,
        int orbitalNodes) {

    /** No machine needs more than this many subcommands in flight, and no server wants more. */
    public static final int MAX_JOBS = 32;
    /** Beyond this the blast scheduler is just fighting the chunk worker pool. */
    public static final int MAX_BLAST_THREADS = 32;
    public static final int MAX_RADAR_PIPELINES = 32;
    /** Contacts added to a radar's tracked list per pipeline. */
    public static final int CONTACTS_PER_PIPELINE = 4;

    /** A terminal that has never been opened: the CPU in its box, no GPU, nothing else. */
    public static ComputeBudget bare() {
        return of(CpuTier.BASIC, GpuTier.OFF, List.of(), 0);
    }

    /**
     * Add up a terminal plus every hardware bank (rack) on its wire plus its orbital nodes.
     *
     * @param terminalCpu  the CPU in the terminal's own socket
     * @param terminalGpu  a GPU fitted directly to the terminal, normally {@link GpuTier#OFF}
     * @param banks        racks, in any order
     * @param orbitalNodes number of orbital processing nodes online for this machine
     */
    public static ComputeBudget of(CpuTier terminalCpu, GpuTier terminalGpu,
                                   List<? extends HardwareBank> banks, int orbitalNodes) {
        CpuTier cpu = terminalCpu == null ? CpuTier.BASIC : terminalCpu;
        GpuTier gpu = terminalGpu == null ? GpuTier.OFF : terminalGpu;
        int nodes = Math.max(0, orbitalNodes);

        int cores = cpu.cores();
        int jobs = cpu.jobs();
        int threads = gpu.blastThreads();
        int pipelines = gpu.radarPipelines();
        int draw = cpu.fePerTick() + gpu.fePerTick();

        for (HardwareBank bank : banks) {
            if (bank == null) {
                continue;
            }
            cores += bank.cpuCores();
            jobs += bank.cpuJobs();
            threads += bank.blastThreads();
            pipelines += bank.radarPipelines();
            draw += bank.fePerTick();
        }

        // An orbital node is a remote core: it adds brute force, and a little intelligence (one job
        // and half a radar pipeline each), but it cannot out-core a proper rack because it answers
        // over a downlink.
        cores += 2 * nodes;
        jobs += nodes;
        pipelines += nodes / 2;
        draw += 4 * nodes;

        return new ComputeBudget(cpu, gpu,
                Math.min(cores, 4096),
                Math.min(jobs, MAX_JOBS),
                Math.min(threads, MAX_BLAST_THREADS),
                Math.min(pipelines, MAX_RADAR_PIPELINES),
                draw,
                banks.size(),
                nodes);
    }

    /** Radar contacts this machine's GPUs add to any radar on its wire. */
    public int radarContactBonus() {
        return radarPipelines * CONTACTS_PER_PIPELINE;
    }

    /** How many subcommands may be queued to orbital nodes at once. */
    public int queueCapacity() {
        return Math.max(8, jobs * 4);
    }

    public String summary() {
        return "cpu " + cpu.id() + " (" + cores + " cores, " + jobs + " job(s))"
                + " | gpu " + gpu.id()
                + " | threads " + blastThreads
                + " | radar pipelines " + radarPipelines
                + " | " + fePerTick + " FE/t"
                + " | racks " + racks + " | orbital nodes " + orbitalNodes;
    }

    /**
     * A hardware bank: anything that contributes compute in bulk. Racks implement it; the terminal
     * itself is passed separately because it is the machine rather than a peripheral.
     */
    public interface HardwareBank {
        int cpuCores();

        int cpuJobs();

        int blastThreads();

        int radarPipelines();

        int fePerTick();
    }
}
