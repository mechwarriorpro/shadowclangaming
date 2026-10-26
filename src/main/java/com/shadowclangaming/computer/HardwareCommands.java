package com.shadowclangaming.computer;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.List;

/**
 * /term cpu, /term rack and /term node.
 *
 * Three views of the same machine:
 *
 *   cpu    what this computer is, right now, added up
 *   rack   the sockets in every rack on this wire
 *   node   the orbital nodes on this wire, and what they are working on
 *
 * Hardware needs no card. A card is permission to touch the world; a rack is just a box of parts you had
 * to craft and place, and the terminal is welcome to count it.
 */
public final class HardwareCommands {

    private HardwareCommands() {
    }

    /** Every rack reachable from this terminal: on the wire first, then anything within 8 blocks. */
    public static List<NetworkRackBlockEntity> racks(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal) {
        List<NetworkRackBlockEntity> found = new ArrayList<>();
        for (BlockPos member : NetworkGraph.walk(level, pos, terminal.frequency()).devices()) {
            if (level.getBlockEntity(member) instanceof NetworkRackBlockEntity rack) {
                found.add(rack);
            }
        }
        if (found.isEmpty()) {
            for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-8, -4, -8), pos.offset(8, 4, 8))) {
                if (level.getBlockEntity(cursor) instanceof NetworkRackBlockEntity rack
                        && !found.contains(rack)) {
                    found.add(rack);
                }
            }
        }
        return found;
    }

    /** Positions of orbital nodes on this wire (or within 12 blocks if the wire finds none). */
    public static List<BlockPos> nodes(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal) {
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos member : NetworkGraph.walk(level, pos, terminal.frequency()).devices()) {
            if (level.getBlockEntity(member) instanceof OrbitalNodeBlockEntity) {
                found.add(member);
            }
        }
        if (found.isEmpty()) {
            for (BlockPos cursor : BlockPos.betweenClosed(pos.offset(-12, -8, -12), pos.offset(12, 8, 12))) {
                if (level.getBlockEntity(cursor) instanceof OrbitalNodeBlockEntity) {
                    found.add(cursor.immutable());
                }
            }
        }
        return found;
    }

    /** The machine's total budget, recomputed on demand - never cached, so a pulled rack is felt at once. */
    public static ComputeBudget budget(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal) {
        int nodes = 0;
        for (BlockPos nodePos : nodes(level, pos, terminal)) {
            if (level.getBlockEntity(nodePos) instanceof OrbitalNodeBlockEntity node
                    && node.hasUplink(level)) {
                nodes++;
            }
        }
        return ComputeBudget.of(terminal.cpu(), terminal.gpu(), racks(level, pos, terminal), nodes);
    }

    // ------------------------------------------------------------------ /term cpu

    public static void budget(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal,
                              List<String> out) {
        ComputeBudget budget = budget(level, pos, terminal);
        out.add("cpu: " + budget.cpu().id() + "  " + budget.cpu().cores() + " core(s) @ "
                + budget.cpu().clockMhz() + " MHz, " + budget.cpu().jobs() + " job(s), latency "
                + budget.cpu().latencyTicks() + "t");
        out.add("gpu: " + (budget.gpu().present() ? budget.gpu().id() : "none")
                + (budget.gpu().present()
                        ? "  " + budget.gpu().shaderUnits() + " units, " + budget.blastThreads()
                                + " blast thread(s), " + budget.radarPipelines() + " radar pipeline(s)"
                        : ""));
        out.add("total: " + budget.cores() + " cores, " + budget.jobs() + " job(s), "
                + budget.blastThreads() + " blast thread(s), " + budget.radarPipelines()
                + " radar pipeline(s) (+" + budget.radarContactBonus() + " radar contacts), "
                + budget.fePerTick() + " FE/t");
        out.add("racks: " + budget.racks() + "  orbital nodes: " + budget.orbitalNodes()
                + "  queue capacity: " + budget.queueCapacity());
    }

    // ------------------------------------------------------------------ /term rack

    public static void rack(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal, String[] parts,
                            List<String> out) {
        List<NetworkRackBlockEntity> racks = racks(level, pos, terminal);
        String action = parts.length < 2 ? "status" : parts[1].toLowerCase(java.util.Locale.ROOT);
        if (action.equals("count")) {
            out.add(racks.size() + " rack(s) reachable");
            return;
        }
        if (!action.equals("status") && !action.equals("list")) {
            out.add("usage: rack status|count");
            return;
        }
        if (racks.isEmpty()) {
            out.add("no network racks on this wire (place one, then fit cpu/gpu modules)");
            return;
        }
        for (NetworkRackBlockEntity rack : racks) {
            out.addAll(rack.status());
        }
    }

    // ------------------------------------------------------------------ /term node

    /**
     *   node list                    nodes on this wire
     *   node status [n]              queue, uplink, cpu
     *   node exec <n> <subcommand>   run one subcommand there, right now
     *   node queue <n> <subcommand>  hand it work and forget about it
     *   node flush <n>               drop its queue
     *
     * The subcommand is run *as this terminal*: same cards, same channel, same permissions. A node is a
     * remote core, never a second identity.
     */
    public static void node(ServerLevel level, BlockPos pos, TerminalBlockEntity terminal, String[] parts,
                            List<String> out) {
        List<BlockPos> nodes = nodes(level, pos, terminal);
        String action = parts.length < 2 ? "list" : parts[1].toLowerCase(java.util.Locale.ROOT);

        if (action.equals("list")) {
            if (nodes.isEmpty()) {
                out.add("no orbital processing nodes on this wire");
                return;
            }
            int index = 1;
            for (BlockPos nodePos : nodes) {
                if (level.getBlockEntity(nodePos) instanceof OrbitalNodeBlockEntity node) {
                    out.add("node " + index + " at " + nodePos.toShortString() + ": cpu " + node.cpu().id()
                            + ", " + node.pending() + " queued, " + node.completed() + " done"
                            + (node.hasUplink(level) ? "" : " (no sky access)"));
                }
                index++;
            }
            return;
        }

        if (action.equals("status") || action.equals("show")) {
            if (nodes.isEmpty()) {
                out.add("no orbital processing nodes on this wire");
                return;
            }
            int index = 1;
            for (BlockPos nodePos : nodes) {
                if (level.getBlockEntity(nodePos) instanceof OrbitalNodeBlockEntity node) {
                    if (parts.length >= 3) {
                        Integer wanted = parseIndex(parts[2]);
                        if (wanted != null && wanted != index) {
                            index++;
                            continue;
                        }
                    }
                    out.addAll(node.status());
                }
                index++;
            }
            return;
        }

        if (action.equals("flush")) {
            if (parts.length < 3) {
                out.add("usage: node flush <n>");
                return;
            }
            Integer which = parseIndex(parts[2]);
            if (which == null || which < 1 || which > nodes.size()) {
                out.add("node: no such node");
                return;
            }
            if (level.getBlockEntity(nodes.get(which - 1)) instanceof OrbitalNodeBlockEntity target) {
                target.clearQueue();
                out.add("node " + which + ": queue cleared");
            }
            return;
        }
        if (parts.length < 4) {
            out.add("usage: node exec|queue <n> <subcommand>");
            return;
        }
        Integer wanted = parseIndex(parts[2]);
        if (wanted == null) {
            out.add("node: which node? give its number from 'node list'");
            return;
        }
        if (wanted < 1 || wanted > nodes.size()) {
            out.add("node: no node " + wanted + " on this wire");
            return;
        }
        BlockPos nodePos = nodes.get(wanted - 1);
        if (!(level.getBlockEntity(nodePos) instanceof OrbitalNodeBlockEntity node)) {
            out.add("node: that node is gone");
            return;
        }
        String subcommand = joinFrom(parts, 3);
        if (subcommand.isEmpty()) {
            out.add("node: nothing to run");
            return;
        }
        if (subcommand.toLowerCase(java.util.Locale.ROOT).startsWith("node ")) {
            out.add("node: a node cannot command nodes (type it at the terminal instead)");
            return;
        }

        switch (action) {
            case "exec" -> out.addAll(node.runNow(level, terminal, subcommand));
            case "queue" -> {
                if (node.enqueue(subcommand, terminal.getBlockPos())) {
                    out.add("queued on node " + wanted + ": " + subcommand
                            + " (" + node.pending() + " waiting, latency "
                            + node.cpu().latencyTicks() + "t)");
                } else {
                    out.add("node " + wanted + ": queue full (" + OrbitalNodeBlockEntity.MAX_QUEUE + ")");
                }
            }
            case "flush" -> {
                node.clearQueue();
                out.add("node " + wanted + ": queue cleared");
            }
            default -> out.add("unknown node action: " + action);
        }
    }

    private static Integer parseIndex(String text) {
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Rebuild the rest of a command line, because subcommands contain spaces. */
    static String joinFrom(String[] parts, int from) {
        StringBuilder builder = new StringBuilder();
        for (int i = from; i < parts.length; i++) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(parts[i]);
        }
        return builder.toString();
    }

    /** Is this position one of the blocks the wire can see? Used by the tag command in tests. */
    static boolean isDevice(BlockEntity blockEntity) {
        return NetworkGraph.isDevice(blockEntity);
    }
}
