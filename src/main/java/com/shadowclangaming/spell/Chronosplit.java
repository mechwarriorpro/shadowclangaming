package com.shadowclangaming.spell;

import com.shadowclangaming.registry.SCGMobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.item.ItemTossEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Chronosplit - the plumbing around {@link ChronosplitEffect}.
 *
 * "Frozen for 15 seconds, unable to move or interact with anything" is four separate systems in
 * Minecraft, so it takes four separate hooks:
 *
 * | What the victim must not do | Where it is stopped |
 * |---|---|
 * | move, jump, get knocked around | the effect: zero movement speed + delta zeroed every tick |
 * | break or place blocks | {@code PlayerInteractEvent.LeftClickBlock / RightClickBlock} |
 * | use items, eat, shoot, throw | {@code PlayerInteractEvent.RightClickItem}, {@code LivingEntityUseItemEvent.Start} |
 * | interact with entities / attack | {@code PlayerInteractEvent.EntityInteract}, {@code LivingIncomingDamageEvent} |
 * | drop things | {@code ItemTossEvent} |
 * | mobs pathing or swinging | {@code Mob#setNoAi(true)} while the effect is on |
 *
 * Everything the effect itself can do lives in {@link ChronosplitEffect}; everything that needs a hook
 * lives here. Both are registered from the mod class.
 */
public final class Chronosplit {

    /** Fifteen seconds, exactly as specified. */
    public static final int FREEZE_TICKS = 15 * 20;

    /** Freeze one entity. Called from the spell dispatch; safe to call with null. */
    public static void freeze(LivingEntity target) {
        if (target == null || !target.isAlive()) {
            return;
        }
        target.addEffect(new MobEffectInstance(SCGMobEffects.CHRONOSPLIT, FREEZE_TICKS, 0, false, true, true));
        target.setDeltaMovement(Vec3.ZERO);
        target.hurtMarked = true;
        if (target.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.END_ROD, target.getX(), target.getY() + 1.0D, target.getZ(),
                    30, 0.3D, 0.6D, 0.3D, 0.02D);
            level.sendParticles(ParticleTypes.SNOWFLAKE, target.getX(), target.getY() + 1.0D, target.getZ(),
                    20, 0.4D, 0.7D, 0.4D, 0.01D);
            level.playSound(null, target.blockPosition(), SoundEvents.AMETHYST_BLOCK_CHIME,
                    SoundSource.PLAYERS, 1.0F, 0.5F);
        }
    }

    /** True while {@code entity} is inside a Chronosplit. */
    public static boolean isFrozen(LivingEntity entity) {
        return entity != null && entity.hasEffect(SCGMobEffects.CHRONOSPLIT);
    }

    /** Registers the interaction locks. Called once, from the mod constructor. */
    public static void register() {
        // --- things the victim tries to DO ------------------------------------------------------
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.LeftClickBlock event) -> {
            if (isFrozen(event.getEntity())) {
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (isFrozen(event.getEntity())) {
                event.setUseBlock(net.neoforged.neoforge.common.util.TriState.FALSE);
                event.setUseItem(net.neoforged.neoforge.common.util.TriState.FALSE);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickItem event) -> {
            if (isFrozen(event.getEntity())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.EntityInteract event) -> {
            if (isFrozen(event.getEntity())) {
                event.setCancellationResult(net.minecraft.world.InteractionResult.FAIL);
                event.setCanceled(true);
            }
        });
        // (Left-click-empty is not cancellable - swinging at air is harmless, and the swing itself does
        // nothing while the damage event below is refused.)
        // Bows, shields, food, potions, spyglasses - all of it.
        NeoForge.EVENT_BUS.addListener((LivingEntityUseItemEvent.Start event) -> {
            if (isFrozen(event.getEntity())) {
                event.setCanceled(true);
            }
        });
        // A frozen thing cannot drop its loot to avoid a death, and cannot hand items to a hopper.
        NeoForge.EVENT_BUS.addListener((ItemTossEvent event) -> {
            if (isFrozen(event.getPlayer())) {
                event.setCanceled(true);
            }
        });

        // --- things the victim tries to DO TO OTHERS -------------------------------------------
        // Frozen in time means frozen: no swings, no arrows in flight, no fire.
        NeoForge.EVENT_BUS.addListener((LivingIncomingDamageEvent event) -> {
            if (event.getSource().getEntity() instanceof LivingEntity attacker && isFrozen(attacker)) {
                event.setCanceled(true);
            }
        });

        // --- putting the AI back --------------------------------------------------------------
        // Two events, because an effect can end by running out (Expired) or by being removed (milk,
        // /effect clear, death, dispel). Missing either one would leave a mob standing there forever.
        NeoForge.EVENT_BUS.addListener((MobEffectEvent.Expired event) -> restoreAi(event.getEntity(), event.getEffectInstance()));
        NeoForge.EVENT_BUS.addListener((MobEffectEvent.Remove event) -> restoreAi(event.getEntity(), event.getEffectInstance()));
    }

    private static void restoreAi(LivingEntity entity, MobEffectInstance instance) {
        if (entity == null || instance == null) {
            return;
        }
        if (!instance.getEffect().is(SCGMobEffects.CHRONOSPLIT.getKey())) {
            return;
        }
        // A second Chronosplit can be running underneath the one that just ended - only wake up when
        // the last one is really gone.
        if (entity.hasEffect(SCGMobEffects.CHRONOSPLIT)) {
            return;
        }
        if (entity instanceof Mob mob) {
            mob.setNoAi(false);
        }
        if (entity instanceof Player player) {
            // Nudge the client's idea of where they are, so a player who was mid-step is not left with
            // a movement prediction debt when the freeze lifts.
            player.hurtMarked = true;
        }
    }

    private Chronosplit() {
    }
}
