package com.shadowclangaming.chem;

import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Handles poisoning / spiking food with chemical elements and compounds.
 *
 * <p>Inspired by the classic MineChem food poisoning & pharmacological effects:
 * <ul>
 *   <li>Food can be laced with chemical elements (Arsenic, Mercury, Polonium, Chlorine, etc.).</li>
 *   <li>The spiked food appears normal to unsuspecting players unless inspected with a Microscope.</li>
 *   <li>Ingesting the food triggers severe pharmacological & toxicological symptoms:
 *     <ul>
 *       <li><b>Arsenic / Mercury / Lead (As, Hg, Pb):</b> Lethal toxicity (Poison IV, Nausea, Slowness).</li>
 *       <li><b>Radioactive (U, Pu, Po, Ra):</b> Internal acute radiation syndrome (Wither III, Blindness).</li>
 *       <li><b>Halogens (Cl, F, Br):</b> Internal caustic acid burn (Instant damage, Nausea).</li>
 *       <li><b>Combustive (P, S, Na):</b> Internal combustion (sets consumer on fire).</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class FoodPoisoningSystem {

    private FoodPoisoningSystem() {}

    public static void poisonFood(ItemStack food, Element toxicant) {
        food.set(SCGDataComponents.POISONED_FOOD.get(), toxicant);
    }

    public static Element getToxicant(ItemStack food) {
        return food.get(SCGDataComponents.POISONED_FOOD.get());
    }

    @SubscribeEvent
    public static void onFinishUsingItem(LivingEntityUseItemEvent.Finish event) {
        ItemStack item = event.getItem();
        Element toxicant = getToxicant(item);
        if (toxicant == null) {
            return;
        }

        LivingEntity consumer = event.getEntity();
        applyFoodToxicity(consumer, toxicant);
    }

    private static void applyFoodToxicity(LivingEntity consumer, Element toxicant) {
        double x = consumer.getX();
        double y = consumer.getY() + 1.0;
        double z = consumer.getZ();

        if (consumer.level() instanceof ServerLevel level) {
            level.playSound(null, x, y, z, SoundEvents.WITCH_DRINK, SoundSource.PLAYERS, 1.0F, 0.8F);
            level.playSound(null, x, y, z, SoundEvents.PLAYER_HURT_DROWN, SoundSource.PLAYERS, 1.0F, 0.8F);

            // 1. Toxic heavy metals (As, Hg, Pb, Cd, Tl)
            if (toxicant == Element.AS || toxicant == Element.HG || toxicant == Element.PB || toxicant == Element.CD || toxicant == Element.TL) {
                consumer.addEffect(new MobEffectInstance(MobEffects.POISON, 200, 3));
                consumer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 240, 0));
                consumer.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 200, 1));
                consumer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 200, 1));
                level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
            }
            // 2. Radioactive elements (U, Pu, Po, Ra, Th)
            else if (toxicant.radioactive()) {
                consumer.hurt(level.damageSources().magic(), 10.0F);
                consumer.addEffect(new MobEffectInstance(MobEffects.WITHER, 240, 2));
                consumer.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 120, 0));
                consumer.addEffect(new MobEffectInstance(MobEffects.HUNGER, 200, 2));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 25, 0.4, 0.4, 0.4, 0.1);
            }
            // 3. Halogens / Acids (Cl, F, Br, I)
            else if (toxicant == Element.CL || toxicant == Element.F || toxicant == Element.BR || toxicant == Element.I) {
                consumer.hurt(level.damageSources().magic(), 8.0F);
                consumer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
                level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
            }
            // 4. Combustive / Exothermic (P, S, Na, K)
            else if (toxicant == Element.P || toxicant == Element.S || toxicant == Element.NA || toxicant == Element.K) {
                consumer.setRemainingFireTicks(160);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 25, 0.4, 0.4, 0.4, 0.1);
            }
            // 5. Mild sedative / other
            else {
                consumer.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 120, 0));
                consumer.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 1));
            }

            if (consumer instanceof ServerPlayer player) {
                player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.food.poisoned_alert")
                                .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD), true);
            }
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        Element toxicant = getToxicant(event.getItemStack());
        if (toxicant == null) {
            return;
        }

        // Only revealed if shift is held or player has chemist tools
        if (net.minecraft.client.gui.screens.Screen.hasShiftDown()) {
            event.getToolTip().add(
                    Component.translatable("tooltip.shadowclangaming.food.laced_with",
                                    Component.translatable(toxicant.translationKey()))
                            .withStyle(ChatFormatting.RED, ChatFormatting.ITALIC));
        }
    }
}
