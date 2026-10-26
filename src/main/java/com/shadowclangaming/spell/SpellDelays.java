package com.shadowclangaming.spell;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Delayed casting: the queue behind the DELAY and CHANT modifiers.
 *
 * ──────────────────── WHY A QUEUE ────────────────────
 *
 * "This step lands three seconds from now" cannot be done with a sleep - the server thread is shared
 * with eighty other things and blocking it for sixty ticks is how a mod gets uninstalled. So a delayed
 * step is snapshotted here and re-entered from the server tick instead: same spell, same step index,
 * same power, same caster, later.
 *
 * ──────────────────── WHY IT IS NOT SAVED DATA ────────────────────
 *
 * Every other persistent thing in this mod (zones, runes, storms, blast scars) writes itself to disk,
 * because those are world state that a player expects to survive a restart. A two-second pending cast
 * is not: it is part of one player's action, and if the server stops in the middle of it the honest
 * answer is that the spell did not finish. Re-starting a delayed Firestorm five minutes later, at a
 * player who has wandered off, would be a worse bug than losing it. So the queue is memory-only and is
 * cleared with the server.
 *
 * The cost of that decision: a crash in the 2-3 second window loses the cast. The mana was already
 * spent (it is spent when the rifle fires), which is rarer and less confusing than a surprise explosion.
 */
public final class SpellDelays {

    /** Hard cap, so a spam-clicking player cannot grow an unbounded queue. */
    public static final int MAX_PENDING = 256;

    /**
     * One pending step.
     *
     * {@code casterUuid} rather than a reference: two seconds is long enough for the caster to log out,
     * die, or change dimension, and a strong reference would either leak them or fire a spell from a
     * body that no longer exists.
     */
    private record Pending(ServerLevel level, UUID casterUuid, Vec3 point, Spell spell, int fromStep,
                           float power, Vec3 look, long fireAt) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    /**
     * Queue a step for later.
     *
     * @param delayTicks ticks from now until the step resolves
     */
    public static void schedule(ServerLevel level, LivingEntity caster, Vec3 point, Spell spell,
                                int fromStep, float power, Vec3 look, float unused, int delayTicks) {
        if (delayTicks <= 0 || spell.isEmpty()) {
            return;
        }
        if (PENDING.size() >= MAX_PENDING) {
            PENDING.remove(0);
        }
        PENDING.add(new Pending(level, caster == null ? null : caster.getUUID(), point, spell,
                fromStep, power, look, level.getGameTime() + delayTicks));
    }

    /** How many casts are waiting. Used by the perf log. */
    public static int pending() {
        return PENDING.size();
    }

    /**
     * Called once per server tick from the mod's game-bus listener.
     *
     * Same hazard as the zone and storm ticks, same fix: a delayed step can contain ANOTHER DELAY or
     * CHANT step (or an ECHO, which is scheduled as a delayed cast), so the list can grow while it is
     * being walked. Index-based access with in-place removal cannot throw.
     */
    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        long now = server.overworld().getGameTime();
        List<Pending> due = new ArrayList<>();

        for (int i = 0; i < PENDING.size(); i++) {
            Pending pending = PENDING.get(i);
            if (pending.level().getServer() != server) {
                PENDING.remove(i);
                i--;
                continue;
            }
            if (now >= pending.fireAt()) {
                due.add(pending);
                PENDING.remove(i);
                i--;
            }
        }

        // Fire AFTER the list has been cleaned, so anything these casts schedule simply waits for the
        // next tick instead of being visited by the loop above.
        for (Pending pending : due) {
            LivingEntity caster = pending.casterUuid() == null ? null
                    : server.getPlayerList().getPlayer(pending.casterUuid());
            // A caster who logged out keeps their spell: it fires anyway, from where they left it.
            SpellCasting.run(pending.level(), caster, null, pending.point(), pending.spell(),
                    pending.fromStep(), pending.power(), pending.look());
            pending.level().sendParticles(net.minecraft.core.particles.ParticleTypes.ENCHANT,
                    pending.point().x, pending.point().y + 1.0D, pending.point().z,
                    20, 0.4D, 0.4D, 0.4D, 0.02D);
        }
    }

    private SpellDelays() {
    }
}
