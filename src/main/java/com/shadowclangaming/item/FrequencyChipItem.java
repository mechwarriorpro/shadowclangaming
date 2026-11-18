package com.shadowclangaming.item;

import com.shadowclangaming.computer.FrequencyRegistry;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * A channel selector.
 *
 * Right-click in the air to step the channel up. Use it on a launch pad to put that pad on the
 * channel. Use it on a terminal to tune the terminal to it. Two blocks, one number, and they are
 * talking - that is the whole linking protocol, and there is no pairing UI or key exchange to get
 * wrong.
 */
public class FrequencyChipItem extends Item {

    /** Kept for the docs and tooltips; the real limits live in {@link com.shadowclangaming.computer.Channels}. */
    public static final int MAX_FREQUENCY = com.shadowclangaming.computer.Channels.MAX;

    public FrequencyChipItem(Properties properties) {
        super(properties);
    }

    /**
     * The channel this chip currently holds.
     *
     * Unset reads as 0 (a chip you can just use), and the number is NOT folded into 0..99 any more: channels
     * are signed, so -193 must survive a round trip through the chip or the designator/pad cannot agree.
     */
    public int frequencyOf(ItemStack stack) {
        Integer value = stack.get(SCGDataComponents.FREQUENCY.get());
        return value == null ? 0 : com.shadowclangaming.computer.Channels.sanitise(value);
    }

    /** Write an exact channel into the chip (the launch pad screen does this). */
    public void setFrequency(ItemStack stack, int channel) {
        stack.set(SCGDataComponents.FREQUENCY.get(),
                com.shadowclangaming.computer.Channels.sanitise(channel));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, net.minecraft.world.entity.player.Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(player.getItemInHand(hand));
        }
        // Right-click steps up, sneak-right-click steps down. Both wrap over the whole signed range, so a
        // player can reach -193 by holding sneak, or by setting it exactly in the pad's channel field.
        int step = player.isShiftKeyDown() ? -1 : 1;
        int next = com.shadowclangaming.computer.Channels.step(frequencyOf(stack), step);
        stack.set(SCGDataComponents.FREQUENCY.get(), next);
        player.displayClientMessage(Component.translatable("message.shadowclangaming.chip_frequency", next)
                .withStyle(ChatFormatting.AQUA), true);
        return InteractionResultHolder.success(player.getItemInHand(hand));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel) || context.getPlayer() == null) {
            return InteractionResult.SUCCESS;
        }
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();
        int frequency = frequencyOf(stack);

        if (level.getBlockEntity(pos) instanceof com.shadowclangaming.block.LaunchPadBlockEntity pad) {
            pad.setFrequency(frequency);
            FrequencyRegistry.get(serverLevel.getServer()).bind(frequency,
                    GlobalPos.of(level.dimension(), pos));
            context.getPlayer().displayClientMessage(
                    Component.translatable("message.shadowclangaming.pad_frequency", frequency)
                            .withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        }
        return InteractionResult.PASS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.chip.frequency", frequencyOf(stack))
                .withStyle(ChatFormatting.AQUA));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.chip.use").withStyle(ChatFormatting.GRAY));
    }
}
