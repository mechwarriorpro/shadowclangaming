package com.shadowclangaming.network;

import net.minecraft.core.BlockPos;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Where incoming console lines wait for a screen to pick them up.
 *
 * A payload handler runs on the network thread; a screen draws on the render thread. Buffering here
 * (per block position, bounded) means the screen never has to care when the packet arrived, and a
 * screen that has already been closed cannot leak its lines into a newly opened one.
 *
 * This class touches no client-only class, so it can live in the shared source tree and be referenced
 * from the payload handler on a dedicated server without a class-loading crash.
 */
public final class ClientConsoleQueue {

    private static final int MAX_BUFFERED = 200;
    private static final Map<Long, List<String>> BUFFERS = new HashMap<>();

    private ClientConsoleQueue() {
    }

    public static void accept(BlockPos pos, List<String> lines) {
        if (pos == null || lines == null || lines.isEmpty()) {
            return;
        }
        List<String> buffer = BUFFERS.computeIfAbsent(pos.asLong(), key -> new ArrayList<>());
        synchronized (buffer) {
            for (String line : lines) {
                buffer.add(line);
                if (buffer.size() > MAX_BUFFERED) {
                    buffer.remove(0);
                }
            }
        }
    }

    /** Takes everything waiting for a position and clears it. Never returns null. */
    public static List<String> drain(BlockPos pos) {
        if (pos == null) {
            return List.of();
        }
        List<String> buffer = BUFFERS.get(pos.asLong());
        if (buffer == null) {
            return List.of();
        }
        synchronized (buffer) {
            if (buffer.isEmpty()) {
                return List.of();
            }
            List<String> copy = List.copyOf(buffer);
            buffer.clear();
            return copy;
        }
    }

    public static void clear() {
        BUFFERS.clear();
    }
}
