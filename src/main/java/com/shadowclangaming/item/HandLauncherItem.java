package com.shadowclangaming.item;

import com.shadowclangaming.entity.MissileEntity;
import com.shadowclangaming.launcher.ModuleTier;
import com.shadowclangaming.warhead.KamikazePlan;
import com.shadowclangaming.warhead.MissilePayload;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * The shoulder tube: fires T1 and T2 missiles from your own pockets.
 *
 * WHERE THIS SITS IN THE LADDER. A silo is a strategic weapon: it is expensive, it is immobile, and its
 * reach is the whole map. This is the other end of that - one person, one tube, whatever they could carry -
 * so the tier gate is the entire point. {@link KamikazePlan#HAND_LAUNCHER_MAX_TIER} caps it at T2 (25,000
 * blocks), which is still absurdly far, but everything above that is a silo decision, not a backpack one.
 *
 * AMMUNITION. It has no magazine to open: right-click fires the first T1/T2 missile in the inventory at
 * whatever you are looking at, and consumes it. That keeps the item a single button - there is no loading
 * state to get wrong, no GUI, and no way to be holding "the wrong missile".
 *
 * Plain missiles (the pre-module ICBM/MRBM items) are treated as unlimited-range by {@link ModuleTier} and
 * are therefore NOT hand-launchable. They are silo hardware.
 */
public class HandLauncherItem extends Item {

    public HandLauncherItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack tube = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(tube);
        }
        if (level.isClientSide) {
            return InteractionResultHolder.sidedSuccess(tube, true);
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return InteractionResultHolder.pass(tube);
        }

        Inventory inventory = player.getInventory();
        int slot = findAmmo(inventory);
        if (slot < 0) {
            player.displayClientMessage(Component.translatable(
                    "message.shadowclangaming.hand_launcher_no_ammo",
                    KamikazePlan.HAND_LAUNCHER_MAX_TIER), true);
            return InteractionResultHolder.fail(tube);
        }

        // Aim at what the player is looking at. A miss on the raycast means "the sky", and the missile is
        // aimed at the far end of the look vector so it still flies somewhere sensible.
        Vec3 eye = player.getEyePosition();
        HitResult hit = player.pick(KamikazePlan.HAND_LAUNCHER_RANGE, 0.0F, false);
        Vec3 aimPoint = hit.getType() == HitResult.Type.MISS
                ? eye.add(player.getLookAngle().scale(KamikazePlan.HAND_LAUNCHER_RANGE))
                : hit.getLocation();
        BlockPos target = BlockPos.containing(aimPoint);

        ItemStack stack = inventory.getItem(slot);
        if (!fire(serverLevel, player, stack, eye, player.getLookAngle(), target)) {
            return InteractionResultHolder.fail(tube);
        }
        stack.shrink(1);
        if (stack.isEmpty()) {
            inventory.setItem(slot, ItemStack.EMPTY);
        }

        player.getCooldowns().addCooldown(this, KamikazePlan.HAND_LAUNCHER_COOLDOWN);
        tube.hurtAndBreak(1, player, net.minecraft.world.entity.EquipmentSlot.MAINHAND);
        serverLevel.playSound(null, player.getX(), player.getY(), player.getZ(),
                SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1.4F, 0.8F);
        return InteractionResultHolder.success(tube);
    }

    /** The first hand-launchable missile in the inventory, or -1. */
    private static int findAmmo(Inventory inventory) {
        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && launchable(stack)) {
                return slot;
            }
        }
        return -1;
    }

    /** Is this stack something the tube will fire? Must be a missile, and its tier must be T1 or T2. */
    public static boolean launchable(ItemStack stack) {
        if (!(stack.getItem() instanceof MissileItem)) {
            return false;
        }
        ModuleTier tier = stack.getItem() instanceof MissileModuleItem module
                ? module.tier() : ModuleTier.DEFAULT;
        return KamikazePlan.handLaunchable(tier.tier());
    }

    private static boolean fire(ServerLevel level, Player player, ItemStack stack, Vec3 eye,
                                Vec3 look, BlockPos target) {
        if (!(stack.getItem() instanceof MissileItem item)) {
            return false;
        }
        WarheadType warhead = stack.getItem() instanceof MissileModuleItem
                ? MissileModuleItem.warheadOf(stack)
                : item.warhead();

        // Launched from the shoulder, a step ahead of the player so it does not immediately hit them.
        Vec3 from = eye.add(look.scale(1.1D));
        MissileEntity missile = new MissileEntity(level,
                from.x, from.y, from.z,
                target,
                stack.copyWithCount(1),
                warhead,
                item.powerFor(stack),
                item.cruiseSpeed(),
                MissileItem.fundingOf(stack),
                MissileItem.spellOf(stack),
                MissilePayload.of(stack),
                null,
                null,
                MissilePayload.radiusOf(stack),
                level.getGameTime() * 31L + player.getUUID().getMostSignificantBits());
        level.addFreshEntity(missile);
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.hand_launcher.tier",
                KamikazePlan.HAND_LAUNCHER_MAX_TIER).withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.hand_launcher.ammo")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.hand_launcher.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    @Override
    public boolean isEnchantable(ItemStack stack) {
        return false;
    }
}
