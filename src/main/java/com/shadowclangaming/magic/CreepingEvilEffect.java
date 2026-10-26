package com.shadowclangaming.magic;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * Creeping Evil - the status effect itself.
 *
 * The effect object is almost empty on purpose. Everything that matters (the level ladder, the six-hour
 * timer, the hearts it eats, the necklace that freezes it, the cure) lives in {@link CreepingEvil},
 * which drives this effect's duration and amplifier from the player's saved curse state. An effect
 * with its own internal logic could not survive a restart, and this curse absolutely has to.
 *
 * It is applied with {@code visible = false, showIcon = false}, so it never appears in the HUD. It is
 * meant to be felt, not read - the player learns about it by losing hearts.
 */
public class CreepingEvilEffect extends MobEffect {

    public CreepingEvilEffect() {
        super(MobEffectCategory.HARMFUL, 0x4A0B12);
    }

    /** No per-tick action: the driver ticks once a second, not twenty times. */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return false;
    }
}
