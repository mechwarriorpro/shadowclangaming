package com.shadowclangaming.chem;

import com.shadowclangaming.item.ChemicalBucketItem;
import com.shadowclangaming.item.ElementVialItem;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles chemical coating of weapons and poisoning of food items via sneak-right-click gestures:
 * <ul>
 *   <li>Hold weapon in main hand + Element Vial / Chem Bucket in off hand -> Sneak-right-click coats weapon!</li>
 *   <li>Hold food in main hand + Element Vial / Chem Bucket in off hand -> Sneak-right-click poisons food!</li>
 * </ul>
 */
public final class ChemicalInteractionEvents {

    private ChemicalInteractionEvents() {}

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        if (!player.isShiftKeyDown()) {
            return;
        }

        InteractionHand hand = event.getHand();
        if (hand != InteractionHand.MAIN_HAND) {
            return;
        }

        ItemStack main = player.getMainHandItem();
        ItemStack off = player.getOffhandItem();

        if (main.isEmpty() || off.isEmpty()) {
            return;
        }

        Element element = null;
        Compound compound = null;
        int charges = 0;
        boolean fromBucket = false;

        if (off.getItem() instanceof ElementVialItem) {
            element = ElementVialItem.elementOf(off);
            charges = WeaponCoatingSystem.VIAL_CHARGES;
        } else if (off.getItem() instanceof com.shadowclangaming.item.CompoundVialItem) {
            compound = com.shadowclangaming.item.CompoundVialItem.compoundOf(off);
            charges = WeaponCoatingSystem.VIAL_CHARGES;
        } else if (off.getItem() instanceof ChemicalBucketItem) {
            element = ChemicalBucketItem.elementOf(off);
            charges = WeaponCoatingSystem.BUCKET_CHARGES;
            fromBucket = true;
        }

        if (element == null && compound == null) {
            return;
        }

        // 1. Weapon Chemical Coating
        if (isWeapon(main)) {
            if (compound != null) {
                WeaponCoatingSystem.applyCompoundCoating(main, compound, charges);
            } else {
                WeaponCoatingSystem.applyCoating(main, element, charges);
            }

            if (!player.isCreative()) {
                off.shrink(1);
                if (fromBucket) {
                    player.getInventory().add(new ItemStack(Items.BUCKET));
                }
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                        SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0F, 1.3F);
                serverLevel.sendParticles(ParticleTypes.ENCHANT,
                        player.getX(), player.getY() + 1.2, player.getZ(),
                        15, 0.3, 0.3, 0.3, 0.05);

                if (player instanceof ServerPlayer serverPlayer) {
                    String chemName = compound != null ? compound.displayName() : element.displayName();
                    serverPlayer.displayClientMessage(
                            Component.literal("§6Coated §f" + main.getHoverName().getString() + "§6 with §f" + chemName + " §7(" + charges + " hits)!"), true);
                }
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide()));
            return;
        }

        // 2. Poisoning Food
        if (main.has(DataComponents.FOOD)) {
            if (compound != null) {
                FoodPoisoningSystem.poisonFoodWithCompound(main, compound);
            } else {
                FoodPoisoningSystem.poisonFood(main, element);
            }

            if (!player.isCreative()) {
                off.shrink(1);
                if (fromBucket) {
                    player.getInventory().add(new ItemStack(Items.BUCKET));
                }
            }

            if (player.level() instanceof ServerLevel serverLevel) {
                serverLevel.playSound(null, player.getX(), player.getY() + 1.0, player.getZ(),
                        SoundEvents.BREWING_STAND_BREW, SoundSource.PLAYERS, 1.0F, 0.8F);
                serverLevel.sendParticles(ParticleTypes.WITCH,
                        player.getX(), player.getY() + 1.2, player.getZ(),
                        12, 0.2, 0.2, 0.2, 0.02);

                if (player instanceof ServerPlayer serverPlayer) {
                    String chemName = compound != null ? compound.displayName() : element.displayName();
                    serverPlayer.displayClientMessage(
                            Component.literal("§cLaced §f" + main.getHoverName().getString() + "§c with §f" + chemName + "!"), true);
                }
            }

            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.sidedSuccess(player.level().isClientSide()));
        }
    }

    private static boolean isWeapon(ItemStack stack) {
        String id = stack.getItem().getDescriptionId().toLowerCase();
        return id.contains("sword") || id.contains("axe") || id.contains("bow")
                || id.contains("spear") || id.contains("blade") || id.contains("rifle")
                || id.contains("gun") || stack.has(SCGDataComponents.GUN.get());
    }
}
