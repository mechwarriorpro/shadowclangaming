package com.shadowclangaming.warhead.apex;

import com.shadowclangaming.perf.FxBudget;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The Soul Furnace, running.
 *
 * <p>Every other apex weapon is a row in {@link ApexStages}: a fixed number of stages, spaced evenly,
 * finished when the last one lands. The Soul Furnace is not that. Its length depends on how much life is
 * standing near it, so it gets its own clock here - one that asks a question every five seconds
 * ("is there anything left to eat?") instead of counting stages, and only lets go when the answer is no.
 * The schedule itself is {@link SoulFurnacePlan}, which is pure numbers and harness-tested; this class is
 * only the part that touches the world.
 *
 * <p>WHAT A TICK DOES. Age the sphere, throw its particles, and on each check tick: consume whatever is in
 * range, grow, re-announce the effect with its new size and its new lifetime, and tell the people standing
 * near it how many it has eaten. When a check comes back empty - or the safety ceiling is reached - the
 * furnace releases: one blast, scaled by everything it consumed, and a cancel packet so the client stops
 * drawing the sphere.
 *
 * <p>NOT SAVED, for the same reason {@link ApexStages} is not: a furnace is a minute of animation, and a
 * furnace resuming after a server restart, mid-growth, over a chunk that may have been rebuilt, is worse
 * than losing the tail of one explosion.
 */
public final class SoulFurnaceStages {

    /** How many furnaces may be growing at once. Far more than anyone can fire, far fewer than a crash. */
    public static final int MAX_PENDING = 8;

    private static final List<Burning> BURNING = new ArrayList<>();

    private SoulFurnaceStages() {
    }

    /** One running furnace. */
    private static final class Burning {
        final ServerLevel level;
        final Vec3 pos;
        final Apex apex;
        final int baseRadius;
        final long seed;
        int souls;
        int checks;
        int age;

        Burning(ServerLevel level, Vec3 pos, Apex apex, int baseRadius, long seed) {
            this.level = level;
            this.pos = pos;
            this.apex = apex;
            this.baseRadius = Math.max(4, baseRadius);
            this.seed = seed;
        }

        int radius() {
            return SoulFurnacePlan.radius(baseRadius, checks);
        }

        int lifetime() {
            return SoulFurnacePlan.lifetimeTicks(souls);
        }
    }

    /**
     * Light the furnace: eat what is standing there now, put the sphere on every nearby client, and book
     * the first availability check.
     */
    public static void start(ServerLevel level, Vec3 pos, Apex apex, int radius, long seed) {
        if (BURNING.size() >= MAX_PENDING) {
            Burning oldest = BURNING.remove(0);
            release(oldest, "furnace queue full");
        }
        Burning burning = new Burning(level, pos, apex, radius, seed);
        // Ignition consumes immediately: the weapon should feel like it took something from the very first
        // tick, not after a five-second pause.
        burning.souls = ApexWarheads.devour(level, pos, burning.radius());
        BURNING.add(burning);
        announce(burning);
        com.shadowclangaming.debug.ShadowDebug.log("SOUL_FURNACE_START",
                "pos=({}, {}, {}) baseRadius={} firstSouls={} firstCheckTick={} lifetime={} maxTicks={}",
                pos.x, pos.y, pos.z, burning.baseRadius, burning.souls,
                SoulFurnacePlan.checkTick(0), burning.lifetime(), SoulFurnacePlan.maximumTicks());
    }

    /** Live furnaces, for the perf read-out. */
    public static int pending() {
        return BURNING.size();
    }

    /** Runs every server tick, alongside {@link ApexStages#tick}. */
    public static void tick(MinecraftServer server) {
        if (BURNING.isEmpty()) {
            return;
        }
        Iterator<Burning> it = BURNING.iterator();
        while (it.hasNext()) {
            Burning burning = it.next();
            if (burning.level.getServer() == null || burning.level.getServer().isStopped()) {
                it.remove();
                continue;
            }
            burning.age++;

            // The sphere and its lightning are drawn by the client from the packets below; these are the
            // world particles that make it read as a furnace even with the renderer switched off.
            if (burning.age % 2 == 0) {
                spark(burning);
            }

            if (burning.age < SoulFurnacePlan.checkTick(burning.checks)) {
                continue;
            }

            // --- one availability check ----------------------------------------------------
            int before = burning.souls;
            int eaten = ApexWarheads.devour(burning.level, burning.pos, burning.radius());
            burning.checks++;
            burning.souls += eaten;
            boolean mobsRemaining = eaten > 0;

            com.shadowclangaming.debug.ShadowDebug.log("SOUL_FURNACE_CHECK",
                    "check={} eaten={} total={} radius={} mobsRemaining={} terminal={}",
                    burning.checks, eaten, burning.souls, burning.radius(), mobsRemaining,
                    SoulFurnacePlan.terminal(mobsRemaining, burning.checks));

            if (SoulFurnacePlan.terminal(mobsRemaining, burning.checks)) {
                release(burning, mobsRemaining ? "safety ceiling" : "nothing left to consume");
                it.remove();
                continue;
            }

            // It found more, so it grows and the effect gets longer. Re-announcing with the new radius and
            // the new lifetime is the whole sync: the client interpolates the sphere from those numbers.
            announce(burning);
            if (burning.souls != before) {
                tally(burning);
            }
        }
    }

    /** Discard every running furnace. Called on server stop. */
    public static void clear() {
        BURNING.clear();
    }

    // ------------------------------------------------------------------ internals

    /** The terminal blast: everything it ate, at once. */
    private static void release(Burning burning, String reason) {
        ServerLevel level = burning.level;
        Vec3 pos = burning.pos;
        float power = SoulFurnacePlan.terminalPower(burning.apex.power(), burning.souls);

        // The sphere collapses before the blast: a flash where the furnace was, then the crater.
        FxBudget.send(level, ParticleTypes.SOUL_FIRE_FLAME, pos, 160, burning.radius() / 4.0D,
                burning.radius() / 4.0D, burning.radius() / 4.0D, 0.05D);
        FxBudget.send(level, ParticleTypes.EXPLOSION_EMITTER, pos, 12, burning.radius() / 6.0D, 2.0D,
                burning.radius() / 6.0D, 0.0D);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE,
                3.0F, 0.6F);
        WarheadType.blast(level, pos, power);

        // Tell the client the sphere is over, then broadcast the tally.
        ApexFx.broadcast(level, pos, burning.apex, burning.radius(), -1);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.soul_furnace", burning.souls), false);
        com.shadowclangaming.debug.ShadowDebug.log("SOUL_FURNACE_END",
                "reason={} checks={} souls={} radius={} power={} age={}",
                reason, burning.checks, burning.souls, burning.radius(), power, burning.age);
    }

    /** Put the current sphere on every client that can see it. */
    private static void announce(Burning burning) {
        ApexFx.broadcast(burning.level, burning.pos, burning.apex, burning.radius(), burning.lifetime());
        burning.level.playSound(null, burning.pos.x, burning.pos.y, burning.pos.z,
                SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 1.6F, 0.7F);
    }

    /** "How many has it eaten" - an action-bar line, because the sphere alone cannot say a number. */
    private static void tally(Burning burning) {
        double reach = burning.radius() + ApexFx.MARGIN;
        double reachSq = reach * reach;
        for (ServerPlayer player : burning.level.getServer().getPlayerList().getPlayers()) {
            if (player.level() != burning.level || player.position().distanceToSqr(burning.pos) > reachSq) {
                continue;
            }
            player.displayClientMessage(Component.translatable(
                    "message.shadowclangaming.soul_furnace_growing", burning.souls,
                    String.format("%.1f", SoulFurnacePlan.growth(burning.checks))), true);
        }
    }

    /**
     * The furnace's own weather: a shell of soul fire and crackling sparks that tracks the sphere's
     * current size, budgeted by {@link FxBudget} so a three-times-grown furnace does not spawn three
     * times the particles.
     */
    private static void spark(Burning burning) {
        double r = burning.radius() * SoulFurnacePlan.growth(burning.checks) * 0.35D;
        FxBudget.send(burning.level, ParticleTypes.SOUL_FIRE_FLAME, burning.pos, 24, r, r, r, 0.02D);
        FxBudget.send(burning.level, ParticleTypes.ELECTRIC_SPARK, burning.pos, 16, r * 1.1D, r * 1.1D,
                r * 1.1D, 0.05D);
        if (burning.age % 20 == 0) {
            FxBudget.send(burning.level, ParticleTypes.SOUL, burning.pos, 12, r * 0.8D, r * 1.4D,
                    r * 0.8D, 0.04D);
        }
    }
}
