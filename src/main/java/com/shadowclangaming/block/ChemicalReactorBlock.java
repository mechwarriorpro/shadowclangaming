package com.shadowclangaming.block;

import com.mojang.serialization.MapCodec;
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
 * High-energy chemical reactor for nuclear fission and fusion of elements.
 */
public class ChemicalReactorBlock extends BaseEntityBlock {
    public static final MapCodec<ChemicalReactorBlock> CODEC = simpleCodec(ChemicalReactorBlock::new);

    public ChemicalReactorBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ChemicalReactorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide()) return null;
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof ChemicalReactorBlockEntity reactor) {
                ChemicalReactorBlockEntity.serverTick(tickLevel, pos, tickState, reactor);
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ChemicalReactorBlockEntity reactor) {
            if (reactor.insert(stack, player)) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ChemicalReactorBlockEntity reactor) {
            if (player.isShiftKeyDown()) {
                reactor.toggleMode(player);
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
            if (reactor.collect(player)) {
                return InteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              @Nullable BlockEntity blockEntity, ItemStack tool) {
        if (blockEntity instanceof ChemicalReactorBlockEntity reactor) {
            reactor.dropContents(level, pos);
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }
}
