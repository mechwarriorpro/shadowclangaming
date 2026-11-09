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
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Handles weapon chemical coatings using both elements and classic MineChem compounds.
 */
public final class WeaponCoatingSystem {

    public static final int VIAL_CHARGES = 16;
    public static final int BUCKET_CHARGES = 128;

    private WeaponCoatingSystem() {}

    public static void applyCoating(ItemStack weapon, Element element, int charges) {
        weapon.set(SCGDataComponents.CHEMICAL_COATING.get(), ChemicalCoating.ofElement(element, charges));
    }

    public static void applyCompoundCoating(ItemStack weapon, Compound compound, int charges) {
        weapon.set(SCGDataComponents.CHEMICAL_COATING.get(), ChemicalCoating.ofCompound(compound, charges));
    }

    public static ChemicalCoating getCoating(ItemStack weapon) {
        return weapon.get(SCGDataComponents.CHEMICAL_COATING.get());
    }

    @SubscribeEvent
    public static void onDamage(LivingIncomingDamageEvent event) {
        if (!(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }

        ItemStack weapon = attacker.getMainHandItem();
        ChemicalCoating coating = getCoating(weapon);
        if (coating == null || coating.charges() <= 0) {
            return;
        }

        LivingEntity victim = event.getEntity();

        // 1. If coated with a Compound:
        if (coating.isCompound()) {
            coating.compound().applyEffect(victim);
        }
        // 2. If coated with an Element:
        else if (coating.isElement()) {
            applyChemicalHitEffect(attacker, victim, coating.element(), event);
        }

        // Decrement charges
        int remaining = coating.charges() - 1;
        if (remaining > 0) {
            weapon.set(SCGDataComponents.CHEMICAL_COATING.get(), coating.withCharges(remaining));
        } else {
            weapon.remove(SCGDataComponents.CHEMICAL_COATING.get());
            if (attacker instanceof ServerPlayer player) {
                player.playNotifySound(SoundEvents.ITEM_BREAK, SoundSource.PLAYERS, 0.7F, 1.4F);
                player.displayClientMessage(
                        Component.translatable("message.shadowclangaming.coating.worn_off", weapon.getHoverName())
                                .withStyle(ChatFormatting.GRAY), true);
            }
        }
    }

    private static void applyChemicalHitEffect(LivingEntity attacker, LivingEntity victim, Element element, LivingIncomingDamageEvent event) {
        if (attacker.level() instanceof ServerLevel level) {
            double x = victim.getX();
            double y = victim.getY() + victim.getBbHeight() * 0.5;
            double z = victim.getZ();

            // 1. Radioactive strike
            if (element.radioactive()) {
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 120, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 120, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.GLOWING, 160, 0));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 15, 0.3, 0.3, 0.3, 0.1);
                level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.6F, 1.8F);
                return;
            }

            // 2. Toxic Heavy Metals (As, Hg, Pb, Cd, Tl)
            if (element == Element.AS || element == Element.HG || element == Element.PB || element == Element.CD || element == Element.TL) {
                victim.addEffect(new MobEffectInstance(MobEffects.POISON, 140, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 120, 1));
                victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
                level.sendParticles(ParticleTypes.SQUID_INK, x, y, z, 12, 0.25, 0.25, 0.25, 0.05);
                level.playSound(null, x, y, z, SoundEvents.SPLASH_POTION_BREAK, SoundSource.PLAYERS, 0.6F, 1.2F);
                return;
            }

            // 3. Alkali Metals (Na, K, Li, Cs, Fr)
            if (element == Element.NA || element == Element.K || element == Element.LI || element == Element.CS || element == Element.FR) {
                victim.setRemainingFireTicks(120);
                event.setAmount(event.getAmount() + 3.0F);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 15, 0.4, 0.4, 0.4, 0.1);
                level.playSound(null, x, y, z, SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.8F, 1.2F);
                return;
            }

            // 4. Halogens / Acids (Cl, F, Br, I, S)
            if (element == Element.CL || element == Element.F || element == Element.BR || element == Element.I || element == Element.S) {
                victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 80, 0));
                event.setAmount(event.getAmount() + 2.5F); // Caustic acid damage
                level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 12, 0.3, 0.3, 0.3, 0.05);
                level.playSound(null, x, y, z, SoundEvents.LAVA_EXTINGUISH, SoundSource.PLAYERS, 0.6F, 1.6F);
                return;
            }

            // 5. Cryogenic (N, He, Ne, Ar, Kr, Xe)
            if (element == Element.N || element == Element.HE || element == Element.NE || element == Element.AR || element == Element.KR || element == Element.XE) {
                victim.setTicksFrozen(160);
                victim.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 3));
                level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 20, 0.3, 0.3, 0.3, 0.05);
                level.playSound(null, x, y, z, SoundEvents.PLAYER_HURT_FREEZE, SoundSource.PLAYERS, 0.8F, 1.2F);
                return;
            }

            // 6. Precious Catalysts (Au, Ag, Pt)
            if (element == Element.AU || element == Element.AG || element == Element.PT) {
                if (victim.isInvertedHealAndHarm()) {
                    event.setAmount(event.getAmount() * 1.5F + 2.0F); // Consecrated smite
                    level.sendParticles(ParticleTypes.ENCHANTED_HIT, x, y, z, 25, 0.4, 0.4, 0.4, 0.15);
                    level.playSound(null, x, y, z, SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 0.9F, 1.4F);
                }
                return;
            }

            // 7. Refractory Carbides (C, W, Ti)
            if (element == Element.C || element == Element.W || element == Element.TI) {
                event.setAmount(event.getAmount() + 3.0F); // Armor-piercing edge
                level.sendParticles(ParticleTypes.CRIT, x, y, z, 15, 0.3, 0.3, 0.3, 0.1);
                level.playSound(null, x, y, z, SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.4F, 1.8F);
            }
        }
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        ChemicalCoating coating = getCoating(event.getItemStack());
        if (coating == null) {
            return;
        }

        if (coating.isCompound()) {
            Compound comp = coating.compound();
            event.getToolTip().add(
                    Component.literal("§6Coated: §f" + comp.displayName() + " §7(" + comp.formula() + " - " + coating.charges() + " hits)"));
            event.getToolTip().add(
                    Component.literal("§eEffect: §7" + (comp.isToxic() ? "Lethal Toxicity" : comp.isAcid() ? "Caustic Acid Burn" : comp.isExplosive() ? "High Explosive Shockwave" : "Pharmacological Agent")));
        } else if (coating.isElement()) {
            Element element = coating.element();
            event.getToolTip().add(
                    Component.translatable("tooltip.shadowclangaming.coating.header",
                                    Component.translatable(element.translationKey()), coating.charges())
                            .withStyle(ChatFormatting.GOLD, ChatFormatting.BOLD));

            String effectKey = getEffectDescriptionKey(element);
            event.getToolTip().add(
                    Component.translatable("tooltip.shadowclangaming.coating.effect",
                                    Component.translatable(effectKey))
                            .withStyle(ChatFormatting.YELLOW));
        }
    }

    public static String getEffectDescriptionKey(Element element) {
        if (element.radioactive()) return "tooltip.shadowclangaming.coating.radioactive";
        if (element == Element.AS || element == Element.HG || element == Element.PB || element == Element.CD || element == Element.TL)
            return "tooltip.shadowclangaming.coating.toxic";
        if (element == Element.NA || element == Element.K || element == Element.LI || element == Element.CS || element == Element.FR)
            return "tooltip.shadowclangaming.coating.exothermic";
        if (element == Element.CL || element == Element.F || element == Element.BR || element == Element.I || element == Element.S)
            return "tooltip.shadowclangaming.coating.caustic";
        if (element == Element.N || element == Element.HE || element == Element.NE || element == Element.AR)
            return "tooltip.shadowclangaming.coating.cryogenic";
        if (element == Element.AU || element == Element.AG || element == Element.PT)
            return "tooltip.shadowclangaming.coating.catalyst";
        if (element == Element.C || element == Element.W || element == Element.TI)
            return "tooltip.shadowclangaming.coating.hardened";
        return "tooltip.shadowclangaming.coating.generic";
    }
}
