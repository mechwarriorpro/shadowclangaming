package com.shadowclangaming.block;

import net.minecraft.core.BlockPos;
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
 * Shared behaviour for the Decomposer and the Synthesizer. Both are "put an item in, wait, take
 * something out" machines, so they share one block entity and differ only by {@link #kind()}.
 *
 * No GUI on purpose: right-click with an item to insert, right-click with an empty hand to collect.
 * A GUI (menu + screen) is the natural M2 upgrade once the mechanics feel right.
 */
public abstract class ProcessorBlock extends BaseEntityBlock {
    protected ProcessorBlock(Properties properties) {
        super(properties);
    }

    public abstract ProcessorKind kind();

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ProcessorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null; // all processing happens on the server
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ProcessorBlockEntity processor) {
                ProcessorBlockEntity.serverTick(tickLevel, pos, tickState, processor);
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                               Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof ProcessorBlockEntity processor)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!processor.insert(stack, player)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ProcessorBlockEntity processor) {
            if (processor.collect(player)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (blockEntity instanceof ProcessorBlockEntity processor) {
            processor.dropContents(level, pos);
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }

    @Override
    protected boolean hasAnalogOutputSignal(BlockState state) {
        return true;
    }

    @Override
    protected int getAnalogOutputSignal(BlockState state, Level level, BlockPos pos) {
        return level.getBlockEntity(pos) instanceof ProcessorBlockEntity processor
                ? processor.comparatorSignal()
                : 0;
    }
}
