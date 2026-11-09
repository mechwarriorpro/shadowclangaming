package com.shadowclangaming.block;

import com.shadowclangaming.computer.Ae2Access;
import com.shadowclangaming.computer.StorageBridgeBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Storage bridge.
 *
 * Place it against an AE2 network (or a chest) and a terminal with a Storage Card can read it. Right-
 * click it directly to see what it can see, which is the fastest way to find out whether AE2's API
 * answered on this version.
 */
public class StorageBridgeBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<StorageBridgeBlock> CODEC =
            simpleCodec(StorageBridgeBlock::new);

    public StorageBridgeBlock(Properties properties) {
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
        return new StorageBridgeBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof StorageBridgeBlockEntity bridge)) {
            return InteractionResult.PASS;
        }
        Ae2Access.Report report = Ae2Access.read(serverLevel, pos);
        bridge.record(report);

        player.displayClientMessage(Component.literal(report.ok() ? "-- storage bridge --"
                : "-- storage bridge (no read) --").withStyle(ChatFormatting.AQUA), false);
        player.displayClientMessage(Component.literal(report.note()).withStyle(
                report.ok() ? ChatFormatting.GRAY : ChatFormatting.RED), false);
        int shown = 0;
        for (Ae2Access.Entry entry : report.entries()) {
            if (shown++ >= 8) {
                break;
            }
            player.displayClientMessage(Component.literal("  " + entry.count() + " x " + entry.name())
                    .withStyle(ChatFormatting.DARK_AQUA), false);
        }
        return InteractionResult.CONSUME;
    }
}
