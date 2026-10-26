package com.shadowclangaming.computer;

import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The terminal's memory: which frequency it is tuned to, which cards are plugged in, and the last
 * forty lines it printed.
 *
 * The log lives on the block entity rather than in a GUI packet. That means it can be read by a
 * command, by a neighbouring block, or by a future screen, without any of them needing their own
 * transport.
 */
public class TerminalBlockEntity extends BlockEntity implements com.shadowclangaming.network.ScgNet.ConsoleHost {

    public static final int MAX_LOG = 40;

    private int frequency;
    /** The CPU in the terminal's own socket. Racks add to it; see ComputeBudget. */
    private CpuTier cpu = CpuTier.BASIC;
    /** A GPU fitted straight to the terminal. Most machines put their GPUs in a rack instead. */
    private GpuTier gpu = GpuTier.OFF;
    /** Let the machine engage hostile radar contacts on its own, every AUTO_ENGAGE ticks. */
    private boolean autoEngage;
    private long lastAutoEngage;
    private final Set<TerminalModule> modules = EnumSet.noneOf(TerminalModule.class);
    private final List<String> log = new ArrayList<>();
    /** Six redstone outputs, indexed by Direction.ordinal(). Survive a restart on purpose. */
    private final int[] outputs = new int[6];
    /** Game tick each side's pulse should end at, 0 = not pulsing. */
    private final long[] pulseEnds = new long[6];

    public TerminalBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.TERMINAL.get(), pos, state);
    }

    public int frequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        // Signed, and "unset" is Channels.NONE - see Channels for why -1 is not a valid sentinel.
        this.frequency = com.shadowclangaming.computer.Channels.sanitise(frequency);
        setChanged();
    }

    public CpuTier cpu() {
        return cpu;
    }

    public GpuTier gpu() {
        return gpu;
    }

    /** Socket rule: a CPU only goes in if it is better than what is already there. */
    public boolean installCpu(CpuTier tier) {
        if (tier == null || !tier.betterThan(cpu)) {
            return false;
        }
        this.cpu = tier;
        log("cpu fitted: " + tier.id());
        setChanged();
        return true;
    }

    public boolean installGpu(GpuTier tier) {
        if (tier == null || tier.tier() <= gpu.tier()) {
            return false;
        }
        this.gpu = tier;
        log("gpu fitted: " + tier.id());
        setChanged();
        return true;
    }

    public boolean autoEngage() {
        return autoEngage;
    }

    public void setAutoEngage(boolean autoEngage) {
        this.autoEngage = autoEngage;
        setChanged();
    }

    public Set<TerminalModule> modules() {
        return Set.copyOf(modules);
    }

    public boolean has(TerminalModule module) {
        return modules.contains(module);
    }

    public void install(TerminalModule module) {
        if (modules.add(module)) {
            log("installed card: " + module.id());
        }
        setChanged();
    }

    /**
     * The menu's view of this terminal, as numbers.
     *
     * A live {@link ContainerData}: the getters read the block entity, so vanilla's per-tick "has anything
     * changed" poll (which calls {@code get} on every slot) automatically ships an update when a card is
     * installed or the CPU is swapped. Nothing has to remember to sync.
     */
    public ContainerData data() {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> frequency;
                    case 1 -> cpu.ordinal();
                    case 2 -> gpu.ordinal();
                    case 3 -> {
                        int mask = 0;
                        for (TerminalModule module : modules) {
                            mask |= 1 << module.ordinal();
                        }
                        yield mask;
                    }
                    case 4 -> autoEngage ? 1 : 0;
                    case 5 -> {
                        int mask = 0;
                        for (int i = 0; i < outputs.length; i++) {
                            if (outputs[i] > 0) {
                                mask |= 1 << i;
                            }
                        }
                        yield mask;
                    }
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // read-only: the client never drives the terminal's state directly
            }

            @Override
            public int getCount() {
                return 6;
            }
        };
    }

    public List<String> lines() {
        return List.copyOf(log);
    }

    public void clearLog() {
        log.clear();
        setChanged();
    }

    /** Append one line and keep the buffer bounded. */
    public void log(String line) {
        log.add(line);
        while (log.size() > MAX_LOG) {
            log.remove(0);
        }
        setChanged();
    }

    public void log(List<String> lines) {
        for (String line : lines) {
            log.add(line);
        }
        while (log.size() > MAX_LOG) {
            log.remove(0);
        }
        setChanged();
    }

    // ------------------------------------------------------------------ console host

    /**
     * One line typed into the terminal screen.
     *
     * The screen does not get its own command set: it sends the same line {@code /term <line>} would, so
     * a command that works from chat works from the GUI and vice versa. Output is returned to the caller
     * (which puts it on the CRT) and also kept in the terminal's own log, so a player who walks up later
     * reads the same history.
     */
    @Override
    public java.util.List<String> runConsoleCommand(net.minecraft.server.level.ServerLevel level,
                                                    net.minecraft.server.level.ServerPlayer player,
                                                    String line) {
        return com.shadowclangaming.computer.TerminalShell.runForScreen(
                level, worldPosition, this, player, line);
    }

    // ------------------------------------------------------------------ redstone

    /** Signal this terminal emits on a side, 0..15. */
    public int output(net.minecraft.core.Direction direction) {
        return outputs[direction.ordinal()];
    }

    public void setOutput(net.minecraft.core.Direction direction, int value) {
        int clamped = Math.max(0, Math.min(15, value));
        if (outputs[direction.ordinal()] == clamped) {
            return;
        }
        outputs[direction.ordinal()] = clamped;
        setChanged();
        if (level != null && !level.isClientSide) {
            // Wake the neighbours: without this a repeater downstream never updates.
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    /** Drive a side high for a while, then drop it automatically. */
    public void pulse(long now, net.minecraft.core.Direction direction, int ticks) {
        setOutput(direction, 15);
        pulseEnds[direction.ordinal()] = now + Math.max(1, ticks);
    }

    /**
     * Called every tick by the block. Ends any pulse that has run its course, and - if the machine has
     * both a radar and a cannon card and auto-engage is on - lets it defend itself once every
     * {@link com.shadowclangaming.cannon.CannonCommands#AUTO_ENGAGE_TICKS} ticks.
     */
    public void tick(ServerLevel level, long now) {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            long ends = pulseEnds[direction.ordinal()];
            if (ends > 0L && now >= ends) {
                pulseEnds[direction.ordinal()] = 0L;
                setOutput(direction, 0);
            }
        }
        if (autoEngage && now - lastAutoEngage >= com.shadowclangaming.cannon.CannonCommands.AUTO_ENGAGE_TICKS) {
            lastAutoEngage = now;
            com.shadowclangaming.cannon.CannonCommands.autoEngage(level, worldPosition, this);
        }
    }

    public void clearOutputs() {
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            setOutput(direction, 0);
        }
    }

    public String outputSummary() {
        StringBuilder out = new StringBuilder();
        for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
            if (outputs[direction.ordinal()] > 0) {
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(direction.getName()).append('=').append(outputs[direction.ordinal()]);
            }
        }
        return out.isEmpty() ? "all outputs 0" : out.toString();
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Frequency", frequency);
        tag.putString("Cpu", cpu.id());
        tag.putString("Gpu", gpu.id());
        tag.putBoolean("AutoEngage", autoEngage);

        ListTag moduleList = new ListTag();
        for (TerminalModule module : modules) {
            moduleList.add(StringTag.valueOf(module.id()));
        }
        tag.put("Modules", moduleList);

        ListTag logList = new ListTag();
        for (String line : log) {
            logList.add(StringTag.valueOf(line));
        }
        tag.put("Log", logList);

        ListTag outputList = new ListTag();
        for (int value : outputs) {
            outputList.add(net.minecraft.nbt.IntTag.valueOf(value));
        }
        tag.put("Outputs", outputList);
        // 1.21.1 ListTag has no getLong(int), so the six pulse deadlines ride as one long array.
        tag.putLongArray("PulseEnds", pulseEnds);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        frequency = tag.getInt("Frequency");
        cpu = CpuTier.byId(tag.getString("Cpu"));
        gpu = GpuTier.byId(tag.getString("Gpu"));
        autoEngage = tag.getBoolean("AutoEngage");
        modules.clear();
        ListTag moduleList = tag.getList("Modules", Tag.TAG_STRING);
        for (int i = 0; i < moduleList.size(); i++) {
            modules.add(TerminalModule.byId(moduleList.getString(i)));
        }
        log.clear();
        ListTag logList = tag.getList("Log", Tag.TAG_STRING);
        for (int i = 0; i < logList.size(); i++) {
            log.add(logList.getString(i));
        }
        ListTag outputList = tag.getList("Outputs", Tag.TAG_INT);
        for (int i = 0; i < Math.min(outputList.size(), 6); i++) {
            outputs[i] = Math.max(0, Math.min(15, outputList.getInt(i)));
        }
        long[] storedPulses = tag.getLongArray("PulseEnds");
        for (int i = 0; i < Math.min(storedPulses.length, pulseEnds.length); i++) {
            pulseEnds[i] = storedPulses[i];
        }
    }
}
