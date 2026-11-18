package com.shadowclangaming.perf;

import net.minecraft.server.MinecraftServer;

/**
 * Knows what kind of machine we are running on, and budgets accordingly.
 *
 * This exists because of one specific case: a server running ON a player's machine (single player, or
 * "open to LAN"). There, the server tick and the game's render loop compete for the same CPU, so a
 * huge explosion costs TPS *and* FPS at the same time. On a dedicated server that cannot happen.
 *
 * So:
 *   DEDICATED  (a real server box)   -> spend more per tick, nobody's frame rate to protect
 *   INTEGRATED (server == the client) -> smaller per-tick budget, softer particles, nobody's frame
 *                                       rate tanks while a 96-block crater is being carved
 *
 * Everything can be overridden with system properties if you disagree with the defaults.
 */
public final class ServerProfile {

    private static volatile boolean dedicated = true;

    /** Called on server start. */
    public static void detect(MinecraftServer server) {
        dedicated = server.isDedicatedServer();
    }

    public static boolean isIntegrated() {
        return !dedicated;
    }

    /**
     * Milliseconds of block-clearing allowed per tick.
     * 50ms is one tick. Leaving most of it free is how we avoid a TPS/FPS spike.
     */
    public static long applyBudgetMillis() {
        String property = System.getProperty("shadowclangaming.blastBudgetMs");
        if (property != null) {
            try {
                return Math.max(1, Math.min(20, Long.parseLong(property)));
            } catch (NumberFormatException ignored) {
                // fall through to defaults
            }
        }
        return isIntegrated() ? 2L : 3L;
    }

    /** Multiplier applied to every warhead's particle spawn. */
    public static double particleScale() {
        String property = System.getProperty("shadowclangaming.particleScale");
        if (property != null) {
            try {
                return clamp(Double.parseDouble(property), 0.0, 2.0);
            } catch (NumberFormatException ignored) {
                // fall through to defaults
            }
        }
        return isIntegrated() ? 0.5 : 1.0;
    }

    /** Never spawn more than this many particles in one burst, whatever the warhead asks for. */
    public static int maxParticlesPerBurst() {
        return isIntegrated() ? 60 : 160;
    }

    /**
     * Particles are only sent to players within this distance. On a host-and-play machine this matters:
     * the host is right there in the crater, and vanilla happily sends the full burst to everyone who
     * is merely "tracking" the chunk.
     */
    public static double particleRadius() {
        return isIntegrated() ? 96.0 : 160.0;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private ServerProfile() {
    }
}
