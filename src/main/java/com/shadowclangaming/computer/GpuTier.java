package com.shadowclangaming.computer;

/**
 * The other half of the machine.
 *
 * A GPU in this mod does exactly two things, both of them things the computer genuinely could not do
 * before: it widens how many targets a radar can hold at once, and it gives the blast scheduler more
 * worker threads. It does not make commands "faster" in a hand-wavy way, and it never draws anything -
 * this is a server-side mod with no screen, so a card that promised shaders would be a lie.
 *
 *   OFF       no GPU. Registers nothing, draws nothing.
 *   BASIC     64 units, 2 blast threads, 1 radar pipeline.
 *   VECTOR    256 units, 4 threads, 2 pipelines.
 *   TENSOR    1024 units, 8 threads, 4 pipelines.
 *   PHOTONIC  4096 units, 16 threads, 8 pipelines. Loud, hot, worth it.
 */
public enum GpuTier {
    OFF("off", 0, 0, 0, 0, 0),
    BASIC("basic", 64, 2, 1, 8, 1),
    VECTOR("vector", 256, 4, 2, 24, 2),
    TENSOR("tensor", 1024, 8, 4, 72, 3),
    PHOTONIC("photonic", 4096, 16, 8, 200, 4);

    /** How many GPU modules one network rack can hold. */
    public static final int RACK_SLOTS = 2;

    private final String id;
    private final int shaderUnits;
    private final int blastThreads;
    private final int radarPipelines;
    private final int fePerTick;
    private final int tier;

    GpuTier(String id, int shaderUnits, int blastThreads, int radarPipelines, int fePerTick, int tier) {
        this.id = id;
        this.shaderUnits = shaderUnits;
        this.blastThreads = blastThreads;
        this.radarPipelines = radarPipelines;
        this.fePerTick = fePerTick;
        this.tier = tier;
    }

    public String id() {
        return id;
    }

    public int shaderUnits() {
        return shaderUnits;
    }

    /** Extra worker threads the blast scheduler may use. */
    public int blastThreads() {
        return blastThreads;
    }

    /** Extra radar contacts a linked radar can hold per sweep. */
    public int radarPipelines() {
        return radarPipelines;
    }

    public int fePerTick() {
        return fePerTick;
    }

    public int tier() {
        return tier;
    }

    public boolean present() {
        return this != OFF;
    }

    public String itemId() {
        return "gpu_" + id;
    }

    public String translationKey() {
        return "gpu.shadowclangaming." + id;
    }

    public static GpuTier byId(String id) {
        for (GpuTier tier : values()) {
            if (tier.id.equalsIgnoreCase(id)) {
                return tier;
            }
        }
        return OFF;
    }
}
