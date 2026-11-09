package com.shadowclangaming.block;

import com.shadowclangaming.radar.RadarBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
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
 * The radar dish.
 *
 * Right-click: sweep once and print the scope. Sneak-right-click: toggle automatic sweeping. That is the
 * whole block-level interface; everything else about it is a computer peripheral, which is the point -
 * a radar you have to stand next to is a curiosity, a radar on a wire is air defence.
 */
public class RadarBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<RadarBlock> CODEC = simpleCodec(RadarBlock::new);

    public RadarBlock(Properties properties) {
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
        return new RadarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
                                                                 BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof RadarBlockEntity radar && tickLevel instanceof ServerLevel server) {
                radar.tick(server, tickLevel.getGameTime());
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (!(level instanceof ServerLevel serverLevel)
                || !(level.getBlockEntity(pos) instanceof RadarBlockEntity radar)) {
            return InteractionResult.PASS;
        }
        if (player.isShiftKeyDown()) {
            radar.setAutoSweep(!radar.autoSweep());
            player.displayClientMessage(Component.translatable(
                    radar.autoSweep() ? "message.shadowclangaming.radar_auto_on"
                            : "message.shadowclangaming.radar_auto_off").withStyle(ChatFormatting.GREEN), true);
            return InteractionResult.CONSUME;
        }
        radar.sweep(serverLevel, 0);
        player.displayClientMessage(Component.literal("-- radar --").withStyle(ChatFormatting.AQUA), false);
        for (String line : radar.report()) {
            player.displayClientMessage(Component.literal(line).withStyle(ChatFormatting.GRAY), false);
        }
        player.displayClientMessage(Component.literal(
                com.shadowclangaming.radar.CreateRadarBridge.describe(serverLevel, pos))
                .withStyle(ChatFormatting.DARK_AQUA), false);
        return InteractionResult.CONSUME;
    }

    /** No item interaction of its own, but declared so a hand with an item behaves like an empty one. */
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(net.minecraft.world.item.ItemStack stack,
                                                                 BlockState state, Level level, BlockPos pos,
                                                                 Player player, InteractionHand hand,
                                                                 BlockHitResult hit) {
        return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }
}
