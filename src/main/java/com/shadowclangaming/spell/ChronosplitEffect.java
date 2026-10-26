package com.shadowclangaming.spell;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Chronosplit - the status effect itself.
 *
 * The effect does three of the four things that "frozen in time" has to mean, and the fourth lives in
 * {@link Chronosplit}:
 *
 * <ol>
 *   <li><b>Movement.</b> A {@code MOVEMENT_SPEED} modifier of −100 % makes the attribute zero, which is
 *       applied on the client too - so a frozen player cannot even walk, rather than walking and being
 *       rubber-banded back.</li>
 *   <li><b>Momentum.</b> Every tick the delta movement is zeroed, so nothing pushes them: explosions,
 *       knockback, water, another spell.</li>
 *   <li><b>Mob intent.</b> A mob that is frozen gets {@code setNoAi(true)}, so it stops pathing,
 *       stops looking around, and stops mid-swing. {@link Chronosplit} puts the AI back when the
 *       effect ends, however it ends.</li>
 *   <li><b>Interaction.</b> Block breaking, block use, item use, entity use, attacking and item
 *       dropping are cancelled by the event listeners in {@link Chronosplit}, because those are player
 *       actions rather than entity state.</li>
 * </ol>
 *
 * The effect is applied visible, so the victim can see the timer and everybody else can see that they
 * are out of the fight for fifteen seconds.
 */
public class ChronosplitEffect extends MobEffect {

    /** Violet - the colour of a clock that has stopped. */
    public static final int COLOUR = 0x6E5AC8;

    public ChronosplitEffect() {
        super(MobEffectCategory.HARMFUL, COLOUR);
        // −100 % of the total: whatever they had, they now have none. Applied every tick by the effect,
        // removed automatically when it expires.
        addAttributeModifier(Attributes.MOVEMENT_SPEED,
                ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "chronosplit_frozen"),
                -1.0D, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
    }

    /** Run every tick: this is a freeze, so there is no "every 20 ticks" version of it. */
    @Override
    public boolean shouldApplyEffectTickThisTick(int duration, int amplifier) {
        return true;
    }

    @Override
    public boolean applyEffectTick(LivingEntity entity, int amplifier) {
        // Nothing moves. Not even a little, not even from an explosion.
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        entity.setSprinting(false);
        if (entity instanceof Mob mob) {
            mob.getNavigation().stop();
            mob.setTarget(null);
        }
        if (entity instanceof Player player) {
            // A frozen player keeps their input but the world refuses it: the server-side push back is
            // what stops a client that thinks it is still walking.
            player.setDeltaMovement(Vec3.ZERO);
        }
        return true;
    }

    /** The moment the freeze lands, drop the mob's AI. {@link Chronosplit} restores it on removal. */
    @Override
    public void onEffectStarted(LivingEntity entity, int amplifier) {
        entity.setDeltaMovement(Vec3.ZERO);
        entity.hurtMarked = true;
        if (entity instanceof Mob mob) {
            mob.setNoAi(true);
        }
    }
}
