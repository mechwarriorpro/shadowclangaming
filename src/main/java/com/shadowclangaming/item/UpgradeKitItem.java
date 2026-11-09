package com.shadowclangaming.item;

import com.shadowclangaming.launcher.LauncherLevel;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * An Upgrade Kit: the one item that raises launcher hardware by a tier.
 *
 * Right-click in the air to change the kit's own tier; then use it on a launch pad, a control panel or
 * a support rod. A tier 3 kit only upgrades a tier 2 block - the ladder is climbed one rung at a time,
 * and a wrong-tier kit is refused rather than wasted.
 */
public class UpgradeKitItem extends Item {

    public UpgradeKitItem(Properties properties) {
        super(properties);
    }

    public static int tierOf(ItemStack stack) {
        Integer tier = stack.get(SCGDataComponents.KIT_TIER.get());
        return tier == null ? 1 : Math.max(LauncherLevel.MIN, Math.min(LauncherLevel.MAX, tier));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        int next = tierOf(stack) >= LauncherLevel.MAX ? 1 : tierOf(stack) + 1;
        stack.set(SCGDataComponents.KIT_TIER.get(), next);
        player.displayClientMessage(Component.translatable("message.shadowclangaming.kit_tier", next)
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.kit.tier", tierOf(stack))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.kit.use").withStyle(ChatFormatting.GRAY));
    }
}
