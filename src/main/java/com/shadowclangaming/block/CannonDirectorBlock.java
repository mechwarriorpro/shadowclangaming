package com.shadowclangaming.block;

import com.shadowclangaming.cannon.CannonDirectorBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
 * Cannon director: aims and fires a Create Big Cannons mount.
 *
 * Right-click with an empty hand reads it back; sneak-right-click links it to the nearest mount. It is
 * also a redstone source, used only for the fallback path when the CBC bridge cannot answer - then the
 * director pulses the face that looks at the mount, which is how CBC expects a vanilla player to fire.
 */
public class CannonDirectorBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<CannonDirectorBlock> CODEC =
            simpleCodec(CannonDirectorBlock::new);

    public CannonDirectorBlock(Properties properties) {
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
        return new CannonDirectorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CannonDirectorBlockEntity director
                    && tickLevel instanceof ServerLevel server) {
                director.tick(server, tickLevel.getGameTime());
            }
        };
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return true;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                            Direction direction) {
        return level.getBlockEntity(pos) instanceof CannonDirectorBlockEntity director
                ? director.powerFor(direction)
                : 0;
    }

    @Override
    protected int getDirectSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                                  Direction direction) {
        return getSignal(state, level, pos, direction);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof CannonDirectorBlockEntity director)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            player.displayClientMessage(Component.literal(director.link(serverLevel))
                    .withStyle(ChatFormatting.GREEN), false);
            return InteractionResult.CONSUME;
        }
        player.displayClientMessage(Component.literal("-- cannon director --")
                .withStyle(ChatFormatting.AQUA), false);
        for (String line : director.status()) {
            player.displayClientMessage(Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        return InteractionResult.CONSUME;
    }
}
