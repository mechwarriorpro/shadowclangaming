package com.shadowclangaming.computer;

import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * A network rack: four sockets, two for CPUs and two for GPUs.
 *
 * The terminal is a single block and there is only so much you can bolt to the outside of one. A rack is
 * where the real machine lives - it is a peripheral on the wire, and every terminal that can reach it
 * adds its cores, its jobs, its blast threads and its radar pipelines to its own budget.
 *
 * Racks are deliberately dumb: they store hardware and report numbers. They never run a command
 * themselves, so a rack cannot be used to fire anything without a terminal, which keeps the paper trail
 * pointing at one block a player had to be standing next to.
 */
public class NetworkRackBlockEntity extends BlockEntity implements ComputeBudget.HardwareBank {

    /** Everything a rack can hold. 2 + 2, see {@link CpuTier#RACK_SLOTS}. */
    public static final int SLOTS = CpuTier.RACK_SLOTS + GpuTier.RACK_SLOTS;

    private final CpuTier[] cpus = new CpuTier[CpuTier.RACK_SLOTS];
    private final GpuTier[] gpus = new GpuTier[GpuTier.RACK_SLOTS];

    public NetworkRackBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.NETWORK_RACK.get(), pos, state);
    }

    // ------------------------------------------------------------------ hardware

    /** Slide a CPU into the first free socket. False means the rack is full. */
    public boolean installCpu(CpuTier tier) {
        if (tier == null) {
            return false;
        }
        for (int i = 0; i < cpus.length; i++) {
            if (cpus[i] == null) {
                cpus[i] = tier;
                setChanged();
                return true;
            }
        }
        return false;
    }

    public boolean installGpu(GpuTier tier) {
        if (tier == null || !tier.present()) {
            return false;
        }
        for (int i = 0; i < gpus.length; i++) {
            if (gpus[i] == null) {
                gpus[i] = tier;
                setChanged();
                return true;
            }
        }
        return false;
    }

    public CpuTier[] cpuSlots() {
        return cpus.clone();
    }

    public GpuTier[] gpuSlots() {
        return gpus.clone();
    }

    public boolean empty() {
        return cpuCores() == 0 && blastThreads() == 0;
    }

    // ------------------------------------------------------------------ HardwareBank

    @Override
    public int cpuCores() {
        int cores = 0;
        for (CpuTier tier : cpus) {
            if (tier != null) {
                cores += tier.cores();
            }
        }
        return cores;
    }

    @Override
    public int cpuJobs() {
        int jobs = 0;
        for (CpuTier tier : cpus) {
            if (tier != null) {
                jobs += tier.jobs();
            }
        }
        return jobs;
    }

    @Override
    public int blastThreads() {
        int threads = 0;
        for (GpuTier tier : gpus) {
            if (tier != null) {
                threads += tier.blastThreads();
            }
        }
        return threads;
    }

    @Override
    public int radarPipelines() {
        int pipelines = 0;
        for (GpuTier tier : gpus) {
            if (tier != null) {
                pipelines += tier.radarPipelines();
            }
        }
        return pipelines;
    }

    @Override
    public int fePerTick() {
        int draw = 0;
        for (CpuTier tier : cpus) {
            if (tier != null) {
                draw += tier.fePerTick();
            }
        }
        for (GpuTier tier : gpus) {
            if (tier != null) {
                draw += tier.fePerTick();
            }
        }
        return draw;
    }

    // ------------------------------------------------------------------ display

    public List<String> status() {
        List<String> out = new ArrayList<>();
        out.add("rack at " + worldPosition.toShortString() + ": " + fePerTick() + " FE/t, "
                + cpuCores() + " cores, " + cpuJobs() + " job(s), " + blastThreads()
                + " blast thread(s), " + radarPipelines() + " radar pipeline(s)");
        for (int i = 0; i < cpus.length; i++) {
            out.add("  cpu " + (i + 1) + ": " + (cpus[i] == null ? "empty" : cpus[i].id()));
        }
        for (int i = 0; i < gpus.length; i++) {
            out.add("  gpu " + (i + 1) + ": " + (gpus[i] == null ? "empty" : gpus[i].id()));
        }
        return out;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        for (int i = 0; i < cpus.length; i++) {
            tag.putString("Cpu" + i, cpus[i] == null ? "" : cpus[i].id());
        }
        for (int i = 0; i < gpus.length; i++) {
            tag.putString("Gpu" + i, gpus[i] == null ? "" : gpus[i].id());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        for (int i = 0; i < cpus.length; i++) {
            String id = tag.getString("Cpu" + i);
            cpus[i] = id.isEmpty() ? null : CpuTier.byId(id);
        }
        for (int i = 0; i < gpus.length; i++) {
            String id = tag.getString("Gpu" + i);
            gpus[i] = id.isEmpty() ? null : GpuTier.byId(id);
        }
    }
}
