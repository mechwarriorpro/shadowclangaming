package com.shadowclangaming.block;

import com.shadowclangaming.computer.NetworkCableBlockEntity;
import com.shadowclangaming.item.FrequencyChipItem;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * A network cable: the wire that lets a terminal, a control panel, a launch pad and a storage bridge
 * find each other by touch instead of by channel number alone.
 *
 * Shape-wise it is a cross: a thin core plus one arm per connected neighbour. getShape builds that per
 * state, so a cable that only runs east-west looks like a cable that only runs east-west - there is no
 * multipart JSON to keep in sync, and no model loader to register.
 */
public class NetworkCableBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<NetworkCableBlock> CODEC =
            simpleCodec(NetworkCableBlock::new);

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final VoxelShape CORE = Block.box(6.0D, 6.0D, 6.0D, 10.0D, 10.0D, 10.0D);
    private static final VoxelShape ARM_UP = Block.box(6.0D, 10.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private static final VoxelShape ARM_DOWN = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 6.0D, 10.0D);
    private static final VoxelShape ARM_NORTH = Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 6.0D);
    private static final VoxelShape ARM_SOUTH = Block.box(6.0D, 6.0D, 10.0D, 10.0D, 10.0D, 16.0D);
    private static final VoxelShape ARM_EAST = Block.box(10.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D);
    private static final VoxelShape ARM_WEST = Block.box(0.0D, 6.0D, 6.0D, 6.0D, 10.0D, 10.0D);

    public NetworkCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(SOUTH, false)
                .setValue(EAST, false).setValue(WEST, false)
                .setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, SOUTH, EAST, WEST, UP, DOWN);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new NetworkCableBlockEntity(pos, state);
    }

    // ------------------------------------------------------------------ shape

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, ARM_NORTH);
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, ARM_SOUTH);
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, ARM_EAST);
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, ARM_WEST);
        }
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, ARM_UP);
        }
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, ARM_DOWN);
        }
        return shape;
    }

    /**
     * The same cross as the selection shape.
     *
     * A cable used to be walk-through, which on a 4 px invisible model reads as "the block has no
     * hitbox". A small, honest collision box is the difference between a cable and a rumour: you can
     * stand a mineturtle on it, you cannot fall through it, and you can still walk past it because
     * the box is 4 px wide.
     */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return getShape(state, level, pos, context);
    }

    /** Cables are not a full cube, so neighbouring faces must not be culled. */
    @Override
    protected boolean skipRendering(BlockState state, BlockState adjacentState, Direction side) {
        return false;
    }

    // ------------------------------------------------------------------ connections

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return connect(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        return state.setValue(propertyFor(direction), connectsTo(level, pos, neighbourPos));
    }

    /** Recompute every arm. Called on placement and whenever the channel changes. */
    public static BlockState connect(LevelAccessor level, BlockPos pos, BlockState state) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            result = result.setValue(propertyFor(direction), connectsTo(level, pos, pos.relative(direction)));
        }
        return result;
    }

    /**
     * Does this cable join the block at {@code neighbourPos}?
     *
     * Deliberately does NOT require our own block entity: at placement time the cable is not in the
     * world yet, so getBlockEntity(pos) is null and an earlier version armed nothing at all. A cable
     * therefore connects to any neighbouring cable, and the channel check is applied afterwards by
     * {@link #connect} / the chip, which do have both block entities.
     */
    private static boolean connectsTo(LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        BlockState neighbourState = level.getBlockState(neighbourPos);
        if (neighbourState.getBlock() instanceof NetworkCableBlock) {
            BlockEntity self = level.getBlockEntity(pos);
            BlockEntity other = level.getBlockEntity(neighbourPos);
            if (self instanceof NetworkCableBlockEntity mine && other instanceof NetworkCableBlockEntity theirs
                    && com.shadowclangaming.computer.Channels.isSet(mine.channel())
                    && com.shadowclangaming.computer.Channels.isSet(theirs.channel())
                    && mine.channel() != theirs.channel()) {
                // Two different channels never join: the model must not promise a link the network
                // does not make. Untuned cables (-1) join anything.
                return false;
            }
            return true;
        }
        BlockEntity neighbour = level.getBlockEntity(neighbourPos);
        return neighbour != null && com.shadowclangaming.computer.NetworkGraph.isDevice(neighbour);
    }

    private static BooleanProperty propertyFor(Direction direction) {
        return switch (direction) {
            case UP -> UP;
            case DOWN -> DOWN;
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
        };
    }

    // ------------------------------------------------------------------ tuning

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof NetworkCableBlockEntity cable)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return ItemInteractionResult.SUCCESS;
        }
        if (!(stack.getItem() instanceof FrequencyChipItem chip)) {
            player.displayClientMessage(Component.literal("cable channel: "
                    + com.shadowclangaming.computer.Channels.describe(cable.channel()))
                    .withStyle(ChatFormatting.GRAY), true);
            return ItemInteractionResult.CONSUME;
        }
        int channel = chip.frequencyOf(stack);
        cable.setChannel(channel);
        // Straight away, recompute the whole run so every arm matches the new channel.
        level.setBlock(pos, connect(level, pos, state), 3);
        player.displayClientMessage(Component.translatable("message.shadowclangaming.cable_tuned", channel)
                .withStyle(ChatFormatting.AQUA), true);
        return ItemInteractionResult.CONSUME;
    }
}
