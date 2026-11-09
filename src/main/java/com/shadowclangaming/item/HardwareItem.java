package com.shadowclangaming.item;

import com.shadowclangaming.computer.CpuTier;
import com.shadowclangaming.computer.GpuTier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import java.util.List;

/**
 * A CPU or a GPU, as an item.
 *
 * One class for both because they are the same interaction in three places (terminal socket, rack socket,
 * orbital node socket) and because a player should be able to tell what they are holding from the tooltip
 * rather than from the registry name.
 *
 * Everything here is data: {@link CpuTier} and {@link GpuTier} hold the numbers, and
 * {@link com.shadowclangaming.computer.ComputeBudget} is the only thing that adds them up. That is why a new
 * tier is an enum line plus a lang key, not a new system.
 */
public class HardwareItem extends Item {

    public enum Kind {
        CPU,
        GPU
    }

    private final Kind kind;
    private final CpuTier cpu;
    private final GpuTier gpu;

    private HardwareItem(Properties properties, Kind kind, CpuTier cpu, GpuTier gpu) {
        super(properties);
        this.kind = kind;
        this.cpu = cpu;
        this.gpu = gpu;
    }

    public static HardwareItem cpu(CpuTier tier, Properties properties) {
        return new HardwareItem(properties, Kind.CPU, tier, GpuTier.OFF);
    }

    public static HardwareItem gpu(GpuTier tier, Properties properties) {
        return new HardwareItem(properties, Kind.GPU, null, tier);
    }

    public Kind kind() {
        return kind;
    }

    public CpuTier cpu() {
        return cpu;
    }

    public GpuTier gpu() {
        return gpu;
    }

    public boolean isCpu() {
        return kind == Kind.CPU;
    }

    public boolean isGpu() {
        return kind == Kind.GPU;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip,
                                TooltipFlag flag) {
        if (isCpu()) {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.cpu",
                    cpu.cores(), cpu.clockMhz(), cpu.jobs(), cpu.fePerTick())
                    .withStyle(ChatFormatting.AQUA));
            tooltip.add(Component.translatable("tooltip.shadowclangaming.cpu.latency", cpu.latencyTicks())
                    .withStyle(ChatFormatting.DARK_GRAY));
        } else {
            tooltip.add(Component.translatable("tooltip.shadowclangaming.gpu",
                    gpu.shaderUnits(), gpu.blastThreads(), gpu.radarPipelines(), gpu.fePerTick())
                    .withStyle(ChatFormatting.LIGHT_PURPLE));
        }
        tooltip.add(Component.translatable("tooltip.shadowclangaming.hardware.use")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
