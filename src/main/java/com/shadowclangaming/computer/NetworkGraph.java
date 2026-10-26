package com.shadowclangaming.computer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Cable connectivity.
 *
 * A terminal, a control panel and a pad used to find each other purely by channel number in saved data.
 * With cables they can also find each other by *wire*, which is what makes a base feel built rather
 * than configured:
 *
 *   - a cable carries a channel number (set with a Frequency Chip)
 *   - a device is "on the wire" if it touches a cable of the same channel, or touches another device
 *     that is already on the wire (so a pad can be one block away from the cable, through a panel)
 *
 * The walk is a bounded breadth-first search: at most {@link #MAX_VISITED} positions, so a player
 * cannot lag the server by building a cable the size of a chunk and asking for a status read.
 */
public final class NetworkGraph {

    /** Hard cap on how many positions one connectivity walk may touch. */
    public static final int MAX_VISITED = 4096;
    /** A cable run longer than this is not a base, it is a prank. */
    public static final int MAX_DEPTH = 256;

    /** What the walk found. */
    public record Members(List<BlockPos> cables, List<BlockPos> devices, int channel, boolean truncated) {
        public boolean empty() {
            return cables.isEmpty() && devices.isEmpty();
        }
    }

    private NetworkGraph() {
    }

    /**
     * Walk the wire starting at a device (a terminal).
     *
     * @param channel the channel to follow; cables on other channels stop the walk
     */
    public static Members walk(ServerLevel level, BlockPos start, int channel) {
        Set<BlockPos> seen = new HashSet<>();
        List<BlockPos> cables = new ArrayList<>();
        List<BlockPos> devices = new ArrayList<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> visited = new HashSet<>();

        queue.add(start.immutable());
        visited.add(start.immutable());
        boolean truncated = false;
        int depth = 0;

        while (!queue.isEmpty()) {
            if (visited.size() > MAX_VISITED || depth++ > MAX_DEPTH * 8) {
                truncated = true;
                break;
            }
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction).immutable();
                if (visited.contains(next) || !level.isLoaded(next)) {
                    continue;
                }
                BlockEntity blockEntity = level.getBlockEntity(next);
                if (blockEntity instanceof NetworkCableBlockEntity cable) {
                    if (cable.channel() != channel) {
                        continue; // a cable on another channel is a wall, not a bridge
                    }
                    visited.add(next);
                    cables.add(next);
                    queue.add(next);
                } else if (blockEntity != null && isDevice(blockEntity)) {
                    if (seen.add(next)) {
                        devices.add(next);
                    }
                    // Devices do not relay: a cable must touch the device directly. This keeps the
                    // graph honest - you can see why something is (not) connected.
                }
            }
        }
        return new Members(List.copyOf(cables), List.copyOf(devices), channel, truncated);
    }

    /** Block entities the network is allowed to see. */
    public static boolean isDevice(BlockEntity blockEntity) {
        return blockEntity instanceof TerminalBlockEntity
                || blockEntity instanceof com.shadowclangaming.block.ControlPanelBlockEntity
                || blockEntity instanceof com.shadowclangaming.block.LaunchPadBlockEntity
                || blockEntity instanceof com.shadowclangaming.computer.StorageBridgeBlockEntity
                || blockEntity instanceof com.shadowclangaming.computer.NetworkRackBlockEntity
                || blockEntity instanceof com.shadowclangaming.computer.OrbitalNodeBlockEntity
                || blockEntity instanceof com.shadowclangaming.radar.RadarBlockEntity
                || blockEntity instanceof com.shadowclangaming.cannon.CannonDirectorBlockEntity;
    }

    /** Human-readable name for a device, used by /term net. */
    public static String describe(BlockEntity blockEntity) {
        if (blockEntity instanceof TerminalBlockEntity) {
            return "terminal";
        }
        if (blockEntity instanceof com.shadowclangaming.block.ControlPanelBlockEntity panel) {
            return "panel (tier " + panel.tier() + (panel.armed() ? ", armed)" : "");
        }
        if (blockEntity instanceof com.shadowclangaming.block.LaunchPadBlockEntity) {
            return "launch pad";
        }
        if (blockEntity instanceof StorageBridgeBlockEntity) {
            return "storage bridge";
        }
        if (blockEntity instanceof NetworkRackBlockEntity rack) {
            return "network rack (" + rack.cpuCores() + " cores, " + rack.blastThreads() + " threads)";
        }
        if (blockEntity instanceof OrbitalNodeBlockEntity node) {
            return "orbital node (cpu " + node.cpu().id() + ", " + node.pending() + " queued)";
        }
        if (blockEntity instanceof com.shadowclangaming.radar.RadarBlockEntity radar) {
            return "radar (" + (int) radar.radius() + " blocks, " + radar.contacts().size() + " contact(s))";
        }
        if (blockEntity instanceof com.shadowclangaming.cannon.CannonDirectorBlockEntity director) {
            return "cannon director (" + (director.armed() ? "armed" : "disarmed") + ")";
        }
        return blockEntity.getClass().getSimpleName();
    }
}
