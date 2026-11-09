package com.shadowclangaming.computer.api;

import java.util.List;

/**
 * What the API layer needs from a computer block entity.
 *
 * The block entity implements this; the API itself is Minecraft-free so the whole surface can be tested on
 * a bare JVM (see tools/run_api_harness.sh). That separation is also what keeps the tick honest: the API
 * never asks "what time is it", it asks the machine for its uptime, and it never sleeps, it suspends.
 */
public interface Machine {

    /** Every component this machine can reach, including ones on the network cable. */
    List<Component> components();

    EventQueue events();

    /** Ticks this machine has been running, for {@code computer.uptime}. */
    long uptimeTicks();

    /** Address reported by {@code computer.address()}. */
    String address();

    /** Where errors and {@code print} go: the terminal log for a real machine, stdout in tests. */
    default void log(String line) {
    }

    /** Reported memory, in bytes. Kept honest-ish but harmless: scripts only use it to pick a strategy. */
    default long totalMemory() {
        return 1024L * 1024L;
    }

    default long freeMemory() {
        return totalMemory() / 2L;
    }

    /** Instruction budget per tick. A faster CPU raises it. */
    default int budgetPerTick() {
        return 20_000;
    }
}
