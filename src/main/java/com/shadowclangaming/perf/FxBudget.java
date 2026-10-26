package com.shadowclangaming.perf;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Particle budget.
 *
 * Vanilla particles are NOT free on the client: each one is ticked in Java, tessellated on the CPU and
 * only then drawn by the GPU. That is why an explosion with thousands of particles drops FPS on the
 * machine hosting the world - and why "let the GPU do the explosion" is really an argument about
 * particles, not about block removal.
 *
 * So the honest GPU-adjacent optimisation is: spawn far fewer particles, and only for players close
 * enough to actually see them. Both of those cut real frame time.
 */
public final class FxBudget {

    /** Scale from the server profile (0.5 on a client-hosted world, 1.0 dedicated). */
    public static double particleScale() {
        // Server profile first (a client-hosted world gets fewer particles), then the config, so a server
        // owner can turn the particle cost down without touching the profile heuristics.
        return ServerProfile.particleScale() * com.shadowclangaming.config.SCGConfig.particleScale();
    }

    /**
     * Sends a particle burst, subject to: global scale, per-burst cap, and distance to each player.
     */
    public static void send(ServerLevel level, ParticleOptions particle, Vec3 pos, int count,
                            double dx, double dy, double dz, double speed) {
        int scaled = (int) Math.round(count * particleScale());
        scaled = Math.min(scaled, ServerProfile.maxParticlesPerBurst());
        if (scaled <= 0) {
            return;
        }

        double limitSq = ServerProfile.particleRadius() * ServerProfile.particleRadius();
        var players = level.getServer().getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            if (player.level() != level) {
                continue;
            }
            if (player.position().distanceToSqr(pos) > limitSq) {
                continue;
            }
            level.sendParticles(player, particle, true, pos.x, pos.y, pos.z, scaled, dx, dy, dz, speed);
        }
    }

    private FxBudget() {
    }
}
