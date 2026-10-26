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
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * An orbital processing node: a computer you put above the clouds and talk to over a downlink.
 *
 * It exists because a base has one terminal and a hundred things worth computing, and because "run this
 * subcommand somewhere else" is the one genuinely new capability hardware can add without turning the
 * mod into a scripting engine. The rules are simple and all of them are visible from the block:
 *
 *   - It needs sky access. No sky, no downlink, queue held.
 *   - It works through its queue one subcommand at a time, one every {@code cpu.latencyTicks()}. A
 *     QUANTUM node in orbit is fast; a BASIC one is a dial-up modem with a solar panel.
 *   - It runs the subcommand *as the terminal that queued it*, at the node's own position. So a radar
 *     scan queued to orbit still uses the terminal's cards and the terminal's channel, and a fired
 *     shot still goes through a launch pad the terminal was already allowed to fire.
 *   - Its own hardware is a real upgrade: fit a better CPU by right-clicking it with one.
 *
 * The queue is saved. An orbital node that forgot its orders when a chunk unloaded would be worse than
 * no node at all.
 */
public class OrbitalNodeBlockEntity extends BlockEntity {

    /** More queued work than this is a player trying to use orbit as a lag machine. */
    public static final int MAX_QUEUE = 64;
    /** Results kept for /term node status. */
    public static final int MAX_RESULTS = 12;

    private CpuTier cpu = CpuTier.BASIC;
    private final List<Subcommand> queue = new ArrayList<>();
    private final List<String> results = new ArrayList<>();
    private BlockPos controller;
    private long lastRun;
    private long completed;
    private String lastNote = "idle";

    public OrbitalNodeBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.ORBITAL_NODE.get(), pos, state);
    }

    // ------------------------------------------------------------------ hardware

    public CpuTier cpu() {
        return cpu;
    }

    /** Returns false if the offered CPU is not an upgrade - sockets are one-way, like cards. */
    public boolean installCpu(CpuTier tier) {
        if (tier == null || !tier.betterThan(cpu)) {
            return false;
        }
        this.cpu = tier;
        setChanged();
        return true;
    }

    /** Best CPU this node can take, so the block can tell a player what they are holding. */
    public int budgetCores() {
        return cpu.cores();
    }

    // ------------------------------------------------------------------ queue

    public boolean enqueue(String command, BlockPos controllerPos) {
        if (command == null || command.isBlank() || queue.size() >= MAX_QUEUE) {
            return false;
        }
        if (controllerPos != null) {
            this.controller = controllerPos.immutable();
        }
        queue.add(Subcommand.pending(command.trim()));
        setChanged();
        return true;
    }

    public int queueSize() {
        return queue.size();
    }

    public int pending() {
        int pending = 0;
        for (Subcommand command : queue) {
            if (!command.done()) {
                pending++;
            }
        }
        return pending;
    }

    public void clearQueue() {
        queue.clear();
        setChanged();
    }

    public BlockPos controller() {
        return controller;
    }

    public long completed() {
        return completed;
    }

    public List<String> results() {
        return List.copyOf(results);
    }

    public String lastNote() {
        return lastNote;
    }

    /** Orbital nodes need an unobstructed view: that is the whole fiction, and the whole limit. */
    public boolean hasUplink(ServerLevel level) {
        return level.canSeeSky(worldPosition.above());
    }

    /**
     * Run at most one queued subcommand, if the node is due.
     *
     * Called from the block's ticker. Nothing here blocks: a subcommand is a synchronous command line
     * that returns a few strings, exactly like typing it.
     */
    public void tick(ServerLevel level, long now) {
        if (queue.isEmpty()) {
            return;
        }
        if (now - lastRun < cpu.latencyTicks()) {
            return;
        }
        if (!hasUplink(level)) {
            lastNote = "no sky access - queue held";
            return;
        }
        if (controller == null) {
            lastNote = "no uplink controller - queue held";
            return;
        }
        if (!(level.getBlockEntity(controller) instanceof TerminalBlockEntity terminal)) {
            lastNote = "controller missing at " + controller.toShortString();
            return;
        }

        int index = -1;
        for (int i = 0; i < queue.size(); i++) {
            if (!queue.get(i).done()) {
                index = i;
                break;
            }
        }
        if (index < 0) {
            queue.clear();
            setChanged();
            return;
        }

        Subcommand job = queue.get(index);
        List<String> output = TerminalShell.executeOnNode(level, worldPosition, terminal, job.command());
        String summary = output.isEmpty() ? "(no output)" : String.join(" | ", trimmed(output));
        queue.set(index, job.finished(summary));
        completed++;
        lastRun = now;
        lastNote = "ran \"" + job.command() + "\": " + summary;
        recordResults(job.command(), output);

        // Keep the queue from growing forever: finished jobs older than the last twelve go.
        while (queue.size() > 12) {
            Subcommand head = queue.get(0);
            if (!head.done()) {
                break;
            }
            queue.remove(0);
        }
        setChanged();
    }

    private static List<String> trimmed(List<String> output) {
        List<String> lines = new ArrayList<>();
        for (int i = 0; i < output.size() && i < 2; i++) {
            String line = output.get(i);
            lines.add(line.length() > 60 ? line.substring(0, 60) + "..." : line);
        }
        if (output.size() > 2) {
            lines.add("+" + (output.size() - 2) + " more");
        }
        return lines;
    }

    private void recordResults(String command, List<String> output) {
        results.add("> " + command);
        for (String line : output) {
            results.add("  " + line);
        }
        while (results.size() > MAX_RESULTS) {
            results.remove(0);
        }
        // Mirror to the controller's log so a player sees orbit work from the terminal they typed at.
        if (controller == null || level == null) {
            return;
        }
        if (level.getBlockEntity(controller) instanceof TerminalBlockEntity terminal) {
            terminal.log("[node] " + command);
            terminal.log(output);
        }
    }

    /**
     * Run one subcommand right now, ignoring the queue and the latency clock.
     *
     * This is what "node exec" does: the same code path the queue uses, so a direct run and a queued run
     * can never disagree about what a command means.
     */
    public List<String> runNow(ServerLevel level, TerminalBlockEntity terminal, String command) {
        if (!hasUplink(level)) {
            lastNote = "no sky access - command refused";
            setChanged();
            return List.of("node: no sky access, cannot run \"" + command + "\"");
        }
        List<String> output = TerminalShell.executeOnNode(level, worldPosition, terminal, command);
        completed++;
        lastRun = level.getGameTime();
        lastNote = "ran \"" + command + "\" directly";
        recordResults(command, output);
        setChanged();
        return output;
    }

    public List<String> status() {
        List<String> out = new ArrayList<>();
        out.add("orbital node at " + worldPosition.toShortString() + ": cpu " + cpu.id()
                + ", latency " + cpu.latencyTicks() + "t, queue " + pending() + "/" + MAX_QUEUE
                + ", completed " + completed);
        out.add("  uplink: " + lastNote);
        int index = 0;
        for (Subcommand command : queue) {
            if (index++ >= 8) {
                out.add("  ... and " + (queue.size() - 8) + " more");
                break;
            }
            out.add("  " + (command.done() ? "[done] " : "[ ] ") + command.command()
                    + (command.done() && !command.result().isEmpty() ? " -> " + command.result() : ""));
        }
        return out;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("Cpu", cpu.id());
        tag.putLong("LastRun", lastRun);
        tag.putLong("Completed", completed);
        tag.putString("LastNote", lastNote);
        if (controller != null) {
            tag.putLong("Controller", controller.asLong());
        }

        ListTag jobs = new ListTag();
        for (Subcommand command : queue) {
            jobs.add(command.save());
        }
        tag.put("Queue", jobs);

        ListTag log = new ListTag();
        for (String line : results) {
            log.add(StringTag.valueOf(line));
        }
        tag.put("Results", log);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        cpu = CpuTier.byId(tag.getString("Cpu"));
        lastRun = tag.getLong("LastRun");
        completed = tag.getLong("Completed");
        lastNote = tag.getString("LastNote");
        controller = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;

        queue.clear();
        ListTag jobs = tag.getList("Queue", Tag.TAG_COMPOUND);
        for (int i = 0; i < jobs.size() && i < MAX_QUEUE; i++) {
            queue.add(Subcommand.load(jobs.getCompound(i)));
        }

        results.clear();
        ListTag log = tag.getList("Results", Tag.TAG_STRING);
        for (int i = 0; i < log.size(); i++) {
            results.add(log.getString(i));
        }
    }
}
