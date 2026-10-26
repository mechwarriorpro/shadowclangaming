package com.shadowclangaming.item;

import com.shadowclangaming.magic.CreepingEvil;
import com.shadowclangaming.magic.ManaApi;
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
 * The Holy Necklace: a brake pedal for the curse, not a cure.
 *
 * Simply carrying it freezes Creeping Evil - the timer stops, no new level, no more hearts lost. It
 * does not reduce the level that is already there, and it costs something serious: while you carry it
 * you regenerate no mana at all, from any source. That turns the curse into a choice - a smaller
 * heart bar and no clock, or a working mana supply and a curse that keeps eating.
 *
 * To actually be rid of it you also need Holy Water, from a cleric, and to use it at midday while
 * carrying this. The necklace glows at midday; that glow is the only warning that the window is open.
 */
public class HolyNecklaceItem extends Item {

    public HolyNecklaceItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        boolean cursed = ManaApi.isEvil(level.getServer(), player.getUUID());
        if (!cursed) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.necklace_no_curse")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        player.displayClientMessage(Component.translatable("message.shadowclangaming.necklace_holding",
                CreepingEvil.describe(level.getServer(), serverPlayer)).withStyle(ChatFormatting.GOLD), false);
        player.displayClientMessage(Component.translatable(CreepingEvil.isMidday(serverPlayer.serverLevel())
                        ? "message.shadowclangaming.necklace_glows"
                        : "message.shadowclangaming.necklace_dark")
                .withStyle(ChatFormatting.DARK_GRAY), false);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.necklace.freeze").withStyle(ChatFormatting.GOLD));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.necklace.cost").withStyle(ChatFormatting.RED));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.necklace.cure").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.necklace.carry").withStyle(ChatFormatting.DARK_GRAY));
    }
}
