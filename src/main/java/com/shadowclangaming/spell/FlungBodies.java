package com.shadowclangaming.spell;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The "flung body" list: mobs that have been thrown by BAD GAS and are waiting to get their AI back.
 *
 * ──────────────────── WHY A QUEUE ────────────────────
 *
 * Vanilla has no ragdoll. The closest a mob gets is {@code setNoAi(true)} plus a large velocity: it
 * stops steering, stops deciding, and just falls, tumbles and gets thrown exactly like a body would.
 * The only missing piece is getting its brain back afterwards, and there is no vanilla timer for that,
 * so it lives here - a handful of entries, ticked from the server tick, never saved.
 *
 * Deliberately memory-only, like {@link SpellDelays}: if the server restarts mid-fling, the mob wakes
 * up with its AI intact, which is the safe failure. A saved entry would be a mob standing still
 * forever if the restore ever got lost.
 *
 * When Sable: Ragdolls is installed this queue is not used at all - that mod owns the body.
 */
public final class FlungBodies {

    /** Safety valve: a hundred flung mobs is a battlefield, a thousand is a bug. */
    private static final int MAX_PENDING = 512;

    private record Pending(UUID id, ResourceKey<Level> dimension, long restoreAt) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private FlungBodies() {
    }

    /**
     * Take a mob's AI away for {@code ticks}. Its velocity is left alone - whoever flung it set that -
     * and the body keeps its momentum until it lands.
     */
    public static void schedule(Mob mob, int ticks) {
        if (mob == null || mob.isRemoved()) {
            return;
        }
        mob.setNoAi(true);
        mob.setTarget(null);
        mob.hurtMarked = true;
        if (PENDING.size() >= MAX_PENDING) {
            PENDING.remove(0);
        }
        long now = mob.level() instanceof ServerLevel server ? server.getGameTime() : 0L;
        PENDING.add(new Pending(mob.getUUID(),
                mob.level().dimension(), now + Math.max(1, ticks)));
    }

    /** How many bodies are currently tumbling. Exposed for the console verb and the harness. */
    public static int active() {
        return PENDING.size();
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        Iterator<Pending> iterator = PENDING.iterator();
        while (iterator.hasNext()) {
            Pending pending = iterator.next();
            ServerLevel level = server.getLevel(pending.dimension());
            if (level == null) {
                iterator.remove();
                continue;
            }
            if (level.getGameTime() < pending.restoreAt()) {
                continue;
            }
            iterator.remove();
            if (level.getEntity(pending.id()) instanceof Mob mob && mob.isAlive()) {
                mob.setNoAi(false);
                mob.hurtMarked = true;
            }
        }
    }

    /** Drop a mob from the queue (used when it dies mid-flight). */
    public static void forget(UUID id) {
        PENDING.removeIf(pending -> pending.id().equals(id));
    }
}
