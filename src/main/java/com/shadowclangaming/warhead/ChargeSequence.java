package com.shadowclangaming.warhead;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * A warhead that has arrived already armed, and is now playing out its effect before it goes off.
 *
 * <p>WHY THIS EXISTS. A placed charge, a grenade and a minecart all have a physical fuse, so they announce
 * their anticipation the moment they are lit and detonate when it runs out. A missile impact has no fuse:
 * it used to land and go off in the same tick, which is the least cinematic thing a two-kilometre warhead
 * can do. This is the same behaviour for the delivery forms that arrive armed - the effect plays, for as
 * long as that warhead's own effect lasts, and the blast lands on the frame the effect ends.
 *
 * <p>The anticipation length is not a new number. It is {@link ExplosivePackaging#fuseTicks} for the block
 * form: the same per-warhead table the placed charges read, so a Big Bang Seed that lands from orbit cooks
 * for exactly as long as a Big Bang Seed you put on the ground yourself.
 *
 * <p>Warheads with no blast finale still play their effect and then simply stop - which is the point of
 * having the flag: a Cargo round lands, plays out, and delivers.
 */
public final class ChargeSequence {

    /** One armed warhead counting down to its own detonation. */
    private record Pending(ServerLevel level, WarheadType type, Vec3 pos, float power, long funding,
                           com.shadowclangaming.spell.Spell spell, List<ItemStack> cargo, int sliderRadius,
                           long buildSeed, int ticksLeft) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();

    private ChargeSequence() {
    }

    /**
     * Play the effect now; detonate when it ends.
     *
     * @return the number of ticks the anticipation will run for
     */
    public static int start(ServerLevel level, WarheadType type, Vec3 pos, float power, long funding,
                            com.shadowclangaming.spell.Spell spell, List<ItemStack> cargo, int sliderRadius,
                            long buildSeed) {
        return start(level, type, pos, power, funding, spell, cargo, sliderRadius, buildSeed,
                chargeTicks(type));
    }

    /**
     * The same, for a delivery form whose fuse is not the block form's.
     *
     * <p>A grenade cooks for a third of the block fuse and a minecart for half, and the effect has to be
     * exactly as long as the form's own fuse or the blast lands before or after the thing you watched.
     *
     * @param ticks the anticipation length, in ticks
     */
    public static int start(ServerLevel level, WarheadType type, Vec3 pos, float power, long funding,
                            com.shadowclangaming.spell.Spell spell, List<ItemStack> cargo, int sliderRadius,
                            long buildSeed, int ticks) {
        ticks = Math.max(1, ticks);
        BlastFx.emitCharge(level, type, pos, power, ticks);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS,
                2.0F, 0.7F);
        PENDING.add(new Pending(level, type, pos, power, funding, spell,
                cargo == null ? List.of() : List.copyOf(cargo), sliderRadius, buildSeed, ticks));
        com.shadowclangaming.debug.ShadowDebug.log("CHARGE_SEQUENCE",
                "warhead={} pos=({}, {}, {}) ticks={} power={} finale={}",
                type.id(), pos.x, pos.y, pos.z, ticks, power,
                BlastMotif.forWarhead(type.id()).hasBlastFinale());
        return ticks;
    }

    /** How long this warhead's anticipation runs for, in ticks: its own fuse, in block form. */
    public static int chargeTicks(WarheadType type) {
        return ExplosivePackaging.fuseTicks(type.id(), type.basePower(), ExplosivePackaging.Form.BLOCK);
    }

    /** One server tick. Called from {@code ShadowClanGaming} next to the other staged weapons. */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (PENDING.isEmpty()) {
            return;
        }
        // Backwards, so removing a finished warhead cannot skip the one behind it.
        for (int i = PENDING.size() - 1; i >= 0; i--) {
            Pending pending = PENDING.get(i);
            ServerLevel level = pending.level();
            if (level.getServer() != server) {
                continue;
            }
            if (pending.ticksLeft() > 1) {
                PENDING.set(i, new Pending(level, pending.type(), pending.pos(), pending.power(),
                        pending.funding(), pending.spell(), pending.cargo(), pending.sliderRadius(),
                        pending.buildSeed(), pending.ticksLeft() - 1));
                continue;
            }
            PENDING.remove(i);
            // The effect has finished, so the warhead goes off. Warheads that were never going to explode
            // take the same path and do their own non-explosive thing at the end of it.
            pending.type().detonate(level, pending.pos(), pending.power(), pending.funding(),
                    pending.spell(), pending.cargo(), pending.sliderRadius(), pending.buildSeed());
        }
    }

    /** How many warheads are currently playing out their anticipation. */
    public static int size() {
        return PENDING.size();
    }

    /** Discard every armed warhead. Called on server stop, so a restart never inherits a pending blast. */
    public static void clear() {
        PENDING.clear();
    }
}
