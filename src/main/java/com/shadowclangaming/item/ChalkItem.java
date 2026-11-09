package com.shadowclangaming.item;

import com.shadowclangaming.block.ChalkCircleBlock;
import com.shadowclangaming.block.CircleBlock;
import com.shadowclangaming.magic.ManaApi;
import com.shadowclangaming.registry.SCGBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * Chalk. Two jobs: draw a circle, and make an existing circle bigger.
 *
 *   right-click the ground       -> draw a small circle
 *   right-click a chalk circle   -> one size bigger, costs one chalk
 *   break a circle               -> chalk comes back
 *
 * Four sizes, and the jump between them is steep on purpose: the grand circle is worth 20 small ones,
 * so the cost of getting there is chalk you had to go and mine out of a geode.
 *
 * Players who took the blood pact cannot draw at all. Chalk simply refuses to mark the ground for them.
 */
public class ChalkItem extends Item {

    public ChalkItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos pos = context.getClickedPos();
        ItemStack stack = context.getItemInHand();

        if (player == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && ManaApi.isEvil(((net.minecraft.server.level.ServerLevel) level).getServer(), player.getUUID())) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.chalk_refused")
                    .withStyle(ChatFormatting.DARK_RED), true);
            return InteractionResult.FAIL;
        }

        BlockState clicked = level.getBlockState(pos);

        // 1. upgrade an existing chalk circle
        if (clicked.getBlock() instanceof ChalkCircleBlock) {
            if (level.isClientSide) {
                return InteractionResult.SUCCESS;
            }
            int size = clicked.getValue(CircleBlock.SIZE);
            if (size >= 3) {
                player.displayClientMessage(Component.translatable("message.shadowclangaming.circle_max")
                        .withStyle(ChatFormatting.GRAY), true);
                return InteractionResult.CONSUME;
            }
            level.setBlockAndUpdate(pos, clicked.setValue(CircleBlock.SIZE, size + 1));
            if (!player.getAbilities().instabuild) {
                stack.shrink(1);
            }
            player.displayClientMessage(Component.translatable("message.shadowclangaming.circle_upgraded",
                    size + 2, (int) CircleBlock.CHALK_RADII[size + 1],
                    CircleBlock.CHALK_MANA_PER_SECOND[size + 1]), true);
            return InteractionResult.CONSUME;
        }

        // 2. draw a new circle on a floor
        if (context.getClickedFace() != Direction.UP) {
            return InteractionResult.PASS;
        }
        BlockPos target = pos.above();
        if (!level.getBlockState(target).canBeReplaced()) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockState circle = SCGBlocks.CHALK_CIRCLE.get().defaultBlockState();
        if (!circle.canSurvive(level, target)) {
            return InteractionResult.PASS;
        }
        level.setBlockAndUpdate(target, circle);
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.shadowclangaming.circle_drawn",
                (int) CircleBlock.CHALK_RADII[0], CircleBlock.CHALK_MANA_PER_SECOND[0]), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.shadowclangaming.chalk.sizes").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.shadowclangaming.chalk.upgrade").withStyle(ChatFormatting.DARK_GRAY));
    }
}
