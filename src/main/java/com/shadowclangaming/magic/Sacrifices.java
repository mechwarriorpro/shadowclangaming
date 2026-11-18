package com.shadowclangaming.magic;

import com.shadowclangaming.block.BloodCircleBlock;
import com.shadowclangaming.registry.SCGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.monster.Witch;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/**
 * The blood pact: the strongest and worst way to get mana.
 *
 * ──────────────────── ACTIVATION ────────────────────
 *
 * Kill a WITCH, a BABY VILLAGER and a CAT within 16 blocks of a blood circle, all three on the same
 * full-moon night, while holding a Totem of Undying in your offhand. That is the ritual, and it is
 * deliberately a shopping list: every ingredient is a thing you have to go and find and bring back
 * alive, and the cat is the one that hurts.
 *
 * The totem is consumed from your offhand on activation. It is not a protective item here - it is the
 * payment for something that should not be survivable.
 *
 * ──────────────────── WHAT IT BUYS ────────────────────
 *
 * A blood pact: 500 mana per second, ANYWHERE in the world, no circle required. For comparison amethyst
 * gives 5/s and the best chalk circle 100/s, so this is not a small upgrade - it is a different
 * relationship with the resource.
 *
 * ──────────────────── WHAT IT COSTS ────────────────────
 *
 *   1. It expires. The first pact lasts 20 minutes and then you are mortal again.
 *   2. Keeping it alive means killing villagers. Constantly.
 *   3. It permanently bars you from chalk circles. {@code evil} is a one-way flag: the good route
 *      closes the moment you choose the other one, and no amount of penance reopens it.
 *
 * ──────────────────── UPKEEP ────────────────────
 *
 * Once you are on the bloody path, sacrifices near a blood circle extend the pact:
 *
 *     adult villager   +10 minutes
 *     baby villager    +30 minutes      (the ritual wants babies; so does the upkeep)
 *     witch or cat     +5 minutes
 *     anything else    +1 minute        (works, barely)
 */
public final class Sacrifices {

    public static final int MASK_WITCH = 1;
    public static final int MASK_BABY_VILLAGER = 2;
    public static final int MASK_CAT = 4;
    public static final int MASK_ALL = MASK_WITCH | MASK_BABY_VILLAGER | MASK_CAT;

    /** How close to a blood circle a victim has to die. */
    public static final double RITUAL_RADIUS = 16.0D;

    /** Duration of the first pact: 20 minutes. */
    public static final long PACT_ACTIVATION_TICKS = 20L * 60L * 20L;

    public static final long EXTEND_VILLAGER_TICKS = 20L * 60L * 10L;
    public static final long EXTEND_BABY_VILLAGER_TICKS = 20L * 60L * 30L;
    public static final long EXTEND_SPECIAL_TICKS = 20L * 60L * 5L;
    public static final long EXTEND_OTHER_TICKS = 20L * 60L;

    /** Ritual progress goes stale after one full night, so you cannot spread the three kills over weeks. */
    public static final long RITUAL_WINDOW_TICKS = 12_000L;

    /** Wires the death listener. Call from the mod constructor. */
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level)) {
            return;
        }
        MinecraftServer server = level.getServer();
        long now = level.getGameTime();

        if (!nearBloodCircle(level, victim.blockPosition())) {
            return; // sacrifices only count at a ritual site
        }

        boolean evil = ManaApi.isEvil(server, killer.getUUID());
        int bits = ritualBitsFor(victim);

        // ---- activation path: all three, on a full moon, holding a totem ----
        if (!evil && bits != 0) {
            if (!isFullMoon(level) || !killer.getOffhandItem().is(Items.TOTEM_OF_UNDYING)) {
                // Wrong night or empty offhand: the kill simply does not count. Say why, once.
                killer.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.ritual_conditions"), true);
                return;
            }
            int mask = ManaApi.ritualMask(server, killer.getUUID());
            if (ManaApi.ritualExpiresAt(server, killer.getUUID()) < now) {
                mask = 0; // stale progress from an earlier night
            }
            mask |= bits;
            ManaApi.setRitual(server, killer.getUUID(), mask, now + RITUAL_WINDOW_TICKS);

            if ((mask & MASK_ALL) == MASK_ALL) {
                activatePact(server, level, killer, now);
            } else {
                killer.displayClientMessage(Component.translatable(
                        "message.shadowclangaming.ritual_progress",
                        Integer.bitCount(mask), 3), false);
            }
            return;
        }

        // ---- upkeep path: a pact holder feeding the pact ----
        if (evil) {
            long extension = upkeepTicksFor(victim);
            // extendPact counts from max(expiry, now), so a lapsed pact is restarted rather than
            // extended backwards into the past.
            ManaApi.extendPact(server, killer.getUUID(), extension, now);
            killer.displayClientMessage(Component.translatable(
                    "message.shadowclangaming.pact_extended",
                    String.format("%.1f", extension / 20.0D / 60.0D)), false);
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.DAMAGE_INDICATOR,
                    victim.getX(), victim.getY() + 1.0D, victim.getZ(), 24, 0.6D, 0.8D, 0.6D, 0.05D);
        }
    }

    private static void activatePact(MinecraftServer server, ServerLevel level, ServerPlayer killer, long now) {
        ManaApi.markEvil(server, killer.getUUID());
        ManaApi.extendPact(server, killer.getUUID(), PACT_ACTIVATION_TICKS, now);
        // Creeping Evil starts now, at level 1, with a full six-hour timer on it.
        ManaApi.setCurse(server, killer.getUUID(), 1, CreepingEvil.TICKS_PER_LEVEL);
        ManaApi.setRitual(server, killer.getUUID(), 0, 0L);

        if (!killer.getAbilities().instabuild) {
            killer.getOffhandItem().shrink(1);
        }

        BlockPos circle = findBloodCircle(level, killer.blockPosition());
        if (circle != null && level.getBlockState(circle).hasProperty(BloodCircleBlock.ACTIVE)) {
            level.setBlockAndUpdate(circle, level.getBlockState(circle)
                    .setValue(BloodCircleBlock.ACTIVE, Boolean.TRUE));
        }

        level.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL_FIRE_FLAME,
                killer.getX(), killer.getY() + 1.0D, killer.getZ(), 120, 1.5D, 2.0D, 1.5D, 0.1D);
        level.playSound(null, killer.blockPosition(),
                net.minecraft.sounds.SoundEvents.WITHER_SPAWN, net.minecraft.sounds.SoundSource.PLAYERS,
                1.0F, 0.6F);

        server.getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.pact_formed", killer.getDisplayName()), false);
        killer.displayClientMessage(Component.translatable("message.shadowclangaming.pact_warning"), false);
    }

    private static int ritualBitsFor(LivingEntity victim) {
        if (victim instanceof Witch) {
            return MASK_WITCH;
        }
        if (victim instanceof Villager villager && villager.isBaby()) {
            return MASK_BABY_VILLAGER;
        }
        if (victim instanceof Cat) {
            return MASK_CAT;
        }
        return 0;
    }

    private static long upkeepTicksFor(LivingEntity victim) {
        if (victim instanceof Villager villager) {
            return villager.isBaby() ? EXTEND_BABY_VILLAGER_TICKS : EXTEND_VILLAGER_TICKS;
        }
        if (victim instanceof Witch || victim instanceof Cat) {
            return EXTEND_SPECIAL_TICKS;
        }
        return EXTEND_OTHER_TICKS;
    }

    /** Full moon in vanilla terms: brightness 1.0. */
    public static boolean isFullMoon(ServerLevel level) {
        return level.getMoonBrightness() >= 0.99F;
    }

    public static boolean nearBloodCircle(ServerLevel level, BlockPos pos) {
        return findBloodCircle(level, pos) != null;
    }

    /** @return the nearest blood circle within {@link #RITUAL_RADIUS}, or null. */
    public static BlockPos findBloodCircle(ServerLevel level, BlockPos pos) {
        int r = (int) Math.ceil(RITUAL_RADIUS);
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-r, -r / 2, -r), pos.offset(r, r / 2, r))) {
            if (!level.getBlockState(p).is(SCGBlocks.BLOOD_CIRCLE.get())) {
                continue;
            }
            double distance = p.distSqr(pos);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = p.immutable();
            }
        }
        return best;
    }

    private Sacrifices() {
    }
}
