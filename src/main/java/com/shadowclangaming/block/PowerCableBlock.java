package com.shadowclangaming.block;

import com.mojang.serialization.MapCodec;
import com.shadowclangaming.compat.projecte.ProjectEEmc;
import com.shadowclangaming.registry.SCGBlockEntities;
import com.shadowclangaming.registry.SCGBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import org.jetbrains.annotations.Nullable;

/**
 * HYPERCONDUCTOR CABLE - the long-scale power trunk. See {@link PowerCableBlockEntity} for the
 * transfer rules and why the long/int split exists.
 *
 * It renders as a core plus an arm per connection, and it connects to a neighbour when that
 * neighbour has ANY of the recognised power protocols, so a player never has to think about which
 * mod's machine is which: put the cable down, it finds the power.
 */
public class PowerCableBlock extends BaseEntityBlock {
    public static final MapCodec<PowerCableBlock> CODEC = simpleCodec(PowerCableBlock::new);

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private static final VoxelShape CORE = Block.box(6.0D, 6.0D, 6.0D, 10.0D, 10.0D, 10.0D);
    private static final VoxelShape ARM_UP = Block.box(6.0D, 10.0D, 6.0D, 10.0D, 16.0D, 10.0D);
    private static final VoxelShape ARM_DOWN = Block.box(6.0D, 0.0D, 6.0D, 10.0D, 6.0D, 10.0D);
    private static final VoxelShape ARM_NORTH = Block.box(6.0D, 6.0D, 0.0D, 10.0D, 10.0D, 6.0D);
    private static final VoxelShape ARM_SOUTH = Block.box(6.0D, 6.0D, 10.0D, 10.0D, 10.0D, 16.0D);
    private static final VoxelShape ARM_WEST = Block.box(0.0D, 6.0D, 6.0D, 6.0D, 10.0D, 10.0D);
    private static final VoxelShape ARM_EAST = Block.box(10.0D, 6.0D, 6.0D, 16.0D, 10.0D, 10.0D);

    public PowerCableBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false).setValue(EAST, false).setValue(SOUTH, false)
                .setValue(WEST, false).setValue(UP, false).setValue(DOWN, false));
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    // ------------------------------------------------------------------ connections

    public static BooleanProperty propertyFor(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case EAST -> EAST;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    /**
     * Does the block at {@code pos} speak any power protocol we understand?
     *
     * FE is asked for through the capability system, which is how Mekanism, Ender IO, Extreme
     * Reactors, NuclearCraft, Nuclearscience, Draconic Evolution, AE2's Energy Acceptor and the
     * Dynamic Electricity motors all present themselves. EMC is asked for by type, because ProjectE
     * does not use an FE-shaped capability for it.
     */
    public static boolean isPowerSource(LevelReader level, BlockPos pos, Direction side) {
        if (level.getBlockState(pos).is(SCGBlocks.POWER_CABLE.get())) {
            return true;
        }
        if (level.getBlockEntity(pos) instanceof PowerCableBlockEntity) {
            return true;
        }
        if (level instanceof Level realLevel) {
            if (realLevel.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side) != null) {
                return true;
            }
        }
        return ProjectEEmc.isEmcStorage(level.getBlockEntity(pos));
    }

    public static BlockState recompute(LevelReader level, BlockPos pos, BlockState state) {
        BlockState result = state;
        for (Direction direction : Direction.values()) {
            boolean connected = isPowerSource(level, pos.relative(direction), direction.getOpposite());
            if (result.getValue(propertyFor(direction)) != connected) {
                result = result.setValue(propertyFor(direction), connected);
            }
        }
        return result;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return recompute(context.getLevel(), context.getClickedPos(), defaultBlockState());
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        boolean connected = isPowerSource(level, neighbourPos, direction.getOpposite());
        return state.setValue(propertyFor(direction), connected);
    }

    /** Capabilities arrive and leave (machines are placed, broken, replaced), so re-check. */
    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block block,
                                   BlockPos fromPos, boolean isMoving) {
        super.neighborChanged(state, level, pos, block, fromPos, isMoving);
        if (level.isClientSide) {
            return;
        }
        BlockState updated = recompute(level, pos, state);
        if (updated != state) {
            level.setBlock(pos, updated, Block.UPDATE_ALL);
        }
    }

    // ------------------------------------------------------------------ shape / render

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        VoxelShape shape = CORE;
        if (state.getValue(UP)) {
            shape = Shapes.or(shape, ARM_UP);
        }
        if (state.getValue(DOWN)) {
            shape = Shapes.or(shape, ARM_DOWN);
        }
        if (state.getValue(NORTH)) {
            shape = Shapes.or(shape, ARM_NORTH);
        }
        if (state.getValue(SOUTH)) {
            shape = Shapes.or(shape, ARM_SOUTH);
        }
        if (state.getValue(WEST)) {
            shape = Shapes.or(shape, ARM_WEST);
        }
        if (state.getValue(EAST)) {
            shape = Shapes.or(shape, ARM_EAST);
        }
        return shape;
    }

    /** Empty hand: read out the long-scale numbers. This is how a player verifies throughput. */
    @Override
    protected net.minecraft.world.InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                                   net.minecraft.world.entity.player.Player player,
                                                                   net.minecraft.world.phys.BlockHitResult hit) {
        if (level.isClientSide) {
            return net.minecraft.world.InteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof PowerCableBlockEntity cable)) {
            return net.minecraft.world.InteractionResult.PASS;
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.shadowclangaming.cable_status",
                String.format("%,d", cable.stored()),
                String.format("%,d", PowerCableBlockEntity.CAPACITY),
                String.format("%,d", cable.lastMoved())), false);

        java.util.List<String> protocols = new java.util.ArrayList<>();
        protocols.add("FE");
        if (ProjectEEmc.isAvailable()) {
            protocols.add("EMC");
        }
        player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                "message.shadowclangaming.cable_protocols", String.join(", ", protocols)), true);
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    // ------------------------------------------------------------------ block entity

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new PowerCableBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof PowerCableBlockEntity cable) {
                PowerCableBlockEntity.serverTick(tickLevel, pos, tickState, cable);
            }
        };
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,
                SCGBlockEntities.POWER_CABLE.get(),
                (blockEntity, side) -> blockEntity.feView());
    }
}
