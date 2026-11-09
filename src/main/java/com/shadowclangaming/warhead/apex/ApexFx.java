package com.shadowclangaming.warhead.apex;

import com.shadowclangaming.network.ScgNet;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Sends an apex detonation to the clients that can see it.
 *
 * ONE PACKET PER WEAPON, NOT ONE PER TICK. The whole animation is deterministic from four numbers - which
 * weapon, where, how big, and when it ended - because the client and the server read the same
 * {@link ApexTimeline} curves. So the server announces the weapon once and the client plays it; there is
 * no per-tick sync, no packet spam, and no chance of the ring and the crater disagreeing.
 *
 * Only players inside the effect's own reach (plus a margin for the sky effects) are told, because a
 * player four thousand blocks away does not need to run a lensing shader for a black hole they cannot see.
 */
public final class ApexFx {

    /** Extra blocks of reach beyond the blast radius, so the sky and the rings are visible. */
    public static final double MARGIN = 96.0D;

    /** A negative {@code ticks} means "cancel this weapon now". */
    public static void broadcast(ServerLevel level, Vec3 pos, Apex apex, int radius, int ticks) {
        double reach = radius + MARGIN;
        double reachSq = reach * reach;
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != level) {
                continue;
            }
            if (player.position().distanceToSqr(pos) > reachSq) {
                continue;
            }
            ScgNet.sendApexFx(player, apex, pos, radius, ticks);
        }
    }

    private ApexFx() {
    }
}
