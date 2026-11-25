package com.shadowclangaming.block;

import com.shadowclangaming.computer.NetworkRackBlockEntity;
import com.shadowclangaming.item.HardwareItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Network rack: hardware in, compute out.
 *
 * Right-click it with a CPU or a GPU and the module goes into a socket; right-click with an empty hand
 * and it reads its own sockets back. There is no GUI, for the same reason the terminal has none - the
 * rack is a peripheral, and the thing that talks to it is a computer.
 */
public class NetworkRackBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<NetworkRackBlock> CODEC =
            simpleCodec(NetworkRackBlock::new);

    public NetworkRackBlock(Properties properties) {
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
        return new NetworkRackBlockEntity(pos, state);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof NetworkRackBlockEntity rack)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!(stack.getItem() instanceof HardwareItem hardware)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        boolean installed = hardware.isCpu()
                ? rack.installCpu(hardware.cpu())
                : rack.installGpu(hardware.gpu());
        if (!installed) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.rack_full")
                    .withStyle(ChatFormatting.RED), true);
            return ItemInteractionResult.CONSUME;
        }
        if (!player.getAbilities().instabuild) {
            stack.shrink(1);
        }
        player.displayClientMessage(Component.translatable("message.shadowclangaming.rack_installed",
                Component.translatable(hardware.isCpu() ? hardware.cpu().translationKey()
                        : hardware.gpu().translationKey())).withStyle(ChatFormatting.GREEN), true);
        return ItemInteractionResult.CONSUME;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof NetworkRackBlockEntity rack)) {
            return InteractionResult.PASS;
        }
        player.displayClientMessage(Component.literal("-- network rack --")
                .withStyle(ChatFormatting.AQUA), false);
        for (String line : rack.status()) {
            player.displayClientMessage(Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return InteractionResult.CONSUME;
    }
}
