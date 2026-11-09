package com.shadowclangaming.item;

import com.shadowclangaming.magic.CreepingEvil;
import com.shadowclangaming.magic.ManaApi;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
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
 * Holy Water - bought from cleric villagers, and the only thing that actually ends the curse.
 *
 * It does nothing on its own. It needs all three conditions at once:
 *
 *   1. you are carrying a Holy Necklace   (the necklace is the focus)
 *   2. it is midday                        (the necklace glows; that is the window)
 *   3. you are cursed
 *
 * Miss any one of them and the water is not consumed - it is a rare trade, and burning it because the
 * player clicked at 11:59 would just be cruel.
 */
public class HolyWaterItem extends Item {

    public HolyWaterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return InteractionResultHolder.pass(player.getItemInHand(hand));
        }

        if (!ManaApi.isEvil(level.getServer(), player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.holy_water_no_curse")
                    .withStyle(ChatFormatting.GRAY), true);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        if (!CreepingEvil.carriesNecklace(serverPlayer)) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.holy_water_no_necklace")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        if (!CreepingEvil.isMidday(serverPlayer.serverLevel())) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.holy_water_not_midday")
                    .withStyle(ChatFormatting.RED), true);
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }

        if (CreepingEvil.breakCurse(serverPlayer)) {
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_DRINK, SoundSource.PLAYERS, 1.0F, 1.4F);
            player.displayClientMessage(Component.translatable("message.shadowclangaming.curse_broken_self")
                    .withStyle(ChatFormatting.WHITE), false);
        }
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.holy_water.use").withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.holy_water.conditions").withStyle(ChatFormatting.GRAY));
    }
}
