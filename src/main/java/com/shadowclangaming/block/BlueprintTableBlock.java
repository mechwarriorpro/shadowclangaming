package com.shadowclangaming.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * The blueprint table: where a rifle stops being blocks and becomes an item.
 *
 * Controls, with no GUI and no extra wand item:
 *   right-click          mark corner A on the block you are looking at
 *   right-click again    mark corner B on the block you are looking at (a different block)
 *   right-click again    move corner B - keep clicking until the box is right
 *   sneak + right-click  scan the box and build the rifle
 *   sneak + right-click  with only corner A set: clear the box and start again
 *
 * THE CORNER IS COMPUTED ON THE CLIENT. The server is always a packet behind the camera, and a corner
 * taken from a stale angle lands on the wrong block; so the client ray-traces what it is pointing at
 * and ships the answer (see {@code BlueprintCornerPayload}). The server validates the distance and the
 * block, and falls back to its own ray only if no packet arrives. The ray deliberately ignores the
 * table itself, since that is the block you are right-clicking.
 *
 * Every step talks back in chat, including the current box, so a player can learn it by doing it rather
 * than by reading a wiki.
 */
public class BlueprintTableBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<BlueprintTableBlock> CODEC =
            simpleCodec(BlueprintTableBlock::new);

    public BlueprintTableBlock(Properties properties) {
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
        return new BlueprintTableBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BlueprintTableBlockEntity table)) {
            return InteractionResult.PASS;
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
            // ONE table, one GUI: the corner-marking flow is gone (see BlueprintTableBlockEntity).
            serverPlayer.openMenu(new net.minecraft.world.SimpleMenuProvider(
                    (containerId, inventory, p) -> new BlueprintTableMenu(containerId, inventory, table),
                    Component.translatable("block.shadowclangaming.blueprint_table")), pos);
        }
        return InteractionResult.CONSUME;
    }
}
