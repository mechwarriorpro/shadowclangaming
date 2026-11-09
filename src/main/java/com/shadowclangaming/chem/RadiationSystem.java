package com.shadowclangaming.chem;

import com.shadowclangaming.armor.ArmorSet;
import com.shadowclangaming.armor.ArmorSets;
import com.shadowclangaming.item.ElementVialItem;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;

import java.util.List;

/**
 * Radiation and radioactive decay mechanics for elements.
 *
 * <p>Inspired by the classic MineChem radiation hazard and decay loops:
 * <ul>
 *   <li><b>Radioactivity Hazard:</b> Holding unshielded radioactive elements causes radiation sickness
 *       (hunger, weakness, poison, and wither under extreme exposure). Heavy Sentinel armor provides
 *       lead-equivalent shielding (25% per piece, 100% for a full suit).</li>
 *   <li><b>Radioactive Decay:</b> Elements with a finite half-life occasionally decay in inventory,
 *       yielding their decay daughter isotope (e.g. actinides decay toward stable Lead, Tritium to Helium).</li>
 *   <li><b>Criticality Incidents:</b> Hoarding critical mass (&ge; 16 vials of fissile material such as
 *       U-235 or Pu-239) triggers an acute Cherenkov radiation flash, dealing a severe radiation burst
 *       to everything nearby.</li>
 * </ul>
 */
public final class RadiationSystem {

    /** Number of fissile vials needed in one inventory to trigger a criticality event. */
    public static final int CRITICAL_MASS = 16;

    private static final EquipmentSlot[] ARMOR_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private RadiationSystem() {}

    /**
     * Called once per second from the server tick loop for each online player.
     */
    public static void tickPlayer(ServerPlayer player) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }

        int totalRadioactive = 0;
        int totalFissile = 0;
        double radiationDose = 0.0;
        RandomSource random = player.getRandom();

        // Scan main inventory
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.isEmpty() || !(stack.getItem() instanceof ElementVialItem)) {
                continue;
            }

            Element element = stack.get(SCGDataComponents.ELEMENT.get());
            if (element == null) {
                continue;
            }

            int count = stack.getCount();

            if (element.fissile()) {
                totalFissile += count;
            }

            if (element.radioactive()) {
                totalRadioactive += count;
                // High-activity elements (short half-life) emit more radiation per second
                double activity = element.halfLifeTicks() > 0
                        ? Math.min(10.0, 24000.0 / element.halfLifeTicks())
                        : 1.0;
                radiationDose += activity * count;

                // Decay check: chance per second proportional to (1 / halfLifeTicks)
                if (element.halfLifeTicks() > 0 && random.nextInt(element.halfLifeTicks()) < 20 * count) {
                    Element daughter = decayDaughter(element);
                    if (daughter != element) {
                        stack.shrink(1);
                        ItemStack daughterVial = ElementVialItem.create(daughter, 1);
                        if (!player.getInventory().add(daughterVial)) {
                            player.drop(daughterVial, false);
                        }
                        player.displayClientMessage(
                                Component.translatable("message.shadowclangaming.element_decayed",
                                        Component.translatable(element.translationKey()),
                                        Component.translatable(daughter.translationKey()))
                                        .withStyle(ChatFormatting.YELLOW), true);
                    }
                }
            }
        }

        // Criticality incident check
        if (totalFissile >= CRITICAL_MASS) {
            triggerCriticality(player, totalFissile);
        }

        // Apply radiation sickness if dose > 0
        if (radiationDose > 0.0) {
            float shielding = calculateShielding(player);
            double effectiveDose = radiationDose * (1.0F - shielding);

            if (effectiveDose > 0.5) {
                applyRadiationEffects(player, effectiveDose);
            }
        }
    }

    /**
     * Determines the decay product for a radioactive element.
     */
    public static Element decayDaughter(Element parent) {
        return switch (parent) {
            case T -> Element.HE;
            case TC, PM -> Element.RU;
            case PO, AT, RN, FR, RA -> Element.PB;
            case U235, U238 -> Element.PB;
            case PU239 -> Element.U235;
            default -> {
                // Actinides & transactinides decay toward lead or down atomic number
                if (parent.atomicNumber() > 82) {
                    yield Element.PB;
                }
                yield parent;
            }
        };
    }

    /**
     * Calculates radiation shielding percentage (0.0 to 1.0) based on worn armor.
     * Sentinel armor provides heavy lead lining (25% per piece, 100% full suit).
     */
    public static float calculateShielding(LivingEntity entity) {
        float shielding = 0.0F;
        for (EquipmentSlot slot : ARMOR_SLOTS) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (stack.isEmpty()) continue;
            // Sentinel armor provides superior radiation absorption
            if (stack.getItem().getDescriptionId().contains("sentinel")) {
                shielding += 0.25F;
            } else {
                // Other heavy metallic armor provides modest protection
                shielding += 0.05F;
            }
        }
        return Math.min(1.0F, shielding);
    }

    private static void applyRadiationEffects(ServerPlayer player, double dose) {
        if (dose >= 50.0) {
            // Severe radiation: Wither II, Weakness II, Slowness II, Hunger
            player.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
            player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 1));
            player.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 100, 0));
        } else if (dose >= 20.0) {
            // Moderate radiation: Poison, Weakness, Slowness
            player.addEffect(new MobEffectInstance(MobEffects.POISON, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 1));
        } else if (dose >= 5.0) {
            // Mild radiation: Hunger and mild weakness
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 60, 0));
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 40, 0));
        } else {
            // Very low trace: subtle hunger
            player.addEffect(new MobEffectInstance(MobEffects.HUNGER, 40, 0));
        }
    }

    private static void triggerCriticality(ServerPlayer source, int fissileCount) {
        ServerLevel level = source.serverLevel();
        double x = source.getX();
        double y = source.getY() + 1.0;
        double z = source.getZ();

        // Blue Cherenkov radiation flash audio
        level.playSound(null, x, y, z, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 2.0F, 1.8F);
        level.playSound(null, x, y, z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 1.5F, 0.5F);

        // Visual blue particles (Cherenkov glow)
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 50, 1.5, 1.5, 1.5, 0.2);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 30, 1.0, 1.0, 1.0, 0.05);

        // Warning message
        source.displayClientMessage(
                Component.translatable("message.shadowclangaming.criticality_warning")
                        .withStyle(ChatFormatting.AQUA, ChatFormatting.BOLD), false);

        // Radiation pulse to all entities within 10 blocks
        AABB zone = new AABB(x - 10, y - 10, z - 10, x + 10, y + 10, z + 10);
        List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class, zone);
        for (LivingEntity victim : victims) {
            float shielding = calculateShielding(victim);
            float damage = Math.max(2.0F, (fissileCount * 0.5F) * (1.0F - shielding));
            victim.hurt(victim.damageSources().magic(), damage);
            victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 160, 1));
            victim.addEffect(new MobEffectInstance(MobEffects.CONFUSION, 160, 0));
            victim.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
        }
    }
}
