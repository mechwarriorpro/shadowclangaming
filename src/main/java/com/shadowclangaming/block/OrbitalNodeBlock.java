package com.shadowclangaming.block;

import com.shadowclangaming.computer.OrbitalNodeBlockEntity;
import com.shadowclangaming.item.HardwareItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Orbital processing node.
 *
 * Build it high, keep the sky clear above it, and a terminal on the same wire can hand it subcommands to
 * chew through in the background. Right-click with a CPU to upgrade it; right-click with an empty hand to
 * read its queue.
 */
public class OrbitalNodeBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<OrbitalNodeBlock> CODEC =
            simpleCodec(OrbitalNodeBlock::new);

    public OrbitalNodeBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrbitalNodeBlockEntity(pos, state);
    }

    /** One queued subcommand per latency window, server side only. */
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof OrbitalNodeBlockEntity node && tickLevel instanceof ServerLevel server) {
                node.tick(server, tickLevel.getGameTime());
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof OrbitalNodeBlockEntity node)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(stack.getItem() instanceof HardwareItem hardware) || !hardware.isCpu()) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!node.installCpu(hardware.cpu())) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.node_not_upgrade",
                    node.cpu().id()).withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.shadowclangaming.node_upgraded",
                Component.translatable(hardware.cpu().translationKey())).withStyle(ChatFormatting.GREEN), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof OrbitalNodeBlockEntity node)) {
            return InteractionResult.PASS;
        }
        player.displayClientMessage(Component.literal("-- orbital node --")
                .withStyle(ChatFormatting.AQUA), false);
        if (level instanceof ServerLevel server && !node.hasUplink(server)) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.node_no_sky")
                    .withStyle(ChatFormatting.RED), false);
        }
        for (String line : node.status()) {
            player.displayClientMessage(Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return InteractionResult.CONSUME;
    }
}
