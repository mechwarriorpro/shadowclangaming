package com.shadowclangaming.item;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Block;

import java.util.List;

/**
 * BlockItem for the Auto-Trader. Exists only so the machine explains itself in the inventory -
 * a block with no GUI has to make its rules discoverable somewhere.
 */
public class TraderBlockItem extends BlockItem {
    public TraderBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.auto_trader.usage")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.auto_trader.rate")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.card.usage")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
