package com.shadowclangaming.computer;

import net.minecraft.core.Direction;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * The terminal's redstone face.
 *
 * Six independent outputs, one per direction, each 0..15, plus a read of whatever is wired into the
 * block. Read and write are separate on purpose: a computer that could read its own output would
 * happily latch itself into a loop, and a redstone loop in a tick handler is how servers die.
 *
 * Outputs are stored on the block entity, so they survive a restart - a terminal that was holding a
 * door open keeps it open. That is the behaviour an operator expects from a control system.
 */
public final class RedstonePort {

    public static final int MAX_SIGNAL = 15;

    private RedstonePort() {
    }

    public static int clamp(int value) {
        return Math.max(0, Math.min(MAX_SIGNAL, value));
    }

    /** Name of a side, as the player types it. */
    public static Direction side(String name) {
        return switch (name.toLowerCase(java.util.Locale.ROOT)) {
            case "up", "top" -> Direction.UP;
            case "down", "bottom" -> Direction.DOWN;
            case "north" -> Direction.NORTH;
            case "south" -> Direction.SOUTH;
            case "east" -> Direction.EAST;
            case "west" -> Direction.WEST;
            default -> null;
        };
    }

    /** Index into the block entity's six-element array. */
    public static int index(Direction direction) {
        return direction.ordinal();
    }

    public static String name(Direction direction) {
        return direction.getName();
    }

    /** What is currently being fed into the terminal, after vanilla's strongest-neighbour rule. */
    public static int input(ServerLevel level, BlockPos pos) {
        return level.getBestNeighborSignal(pos);
    }
}
