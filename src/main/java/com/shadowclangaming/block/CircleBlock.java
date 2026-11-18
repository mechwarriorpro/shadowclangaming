package com.shadowclangaming.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Shared behaviour for the two drawn circles: chalk (the good route) and blood (the other one).
 *
 * Both are drawn on the floor, so they are a 1/16 plate with no collision: you walk over them, and
 * the ring itself is drawn in the air with particles by the block entity. That keeps the whole thing
 * to one block instead of a multi-block structure, and it means the circle's radius is data rather
 * than geometry - a GRAND circle is the same block with a bigger number in it.
 */
public abstract class CircleBlock extends BaseEntityBlock {

    /** Ring radius in blocks, by drawn size. */
    public static final double[] CHALK_RADII = {2.0D, 3.5D, 5.0D, 7.0D};

    /** Mana per second granted to a player standing in the middle, by size. */
    public static final long[] CHALK_MANA_PER_SECOND = {5L, 15L, 40L, 100L};

    public static final IntegerProperty SIZE = IntegerProperty.create("size", 0, 3);

    private static final VoxelShape PLATE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 1.0D, 16.0D);

    protected CircleBlock(Properties properties) {
        super(properties);
    }

    public abstract boolean isBlood();

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(SIZE);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return PLATE;
    }

    /** Walk over it, not into it. */
    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
                                           CollisionContext context) {
        return Shapes.empty();
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState();
        // The blood circle has no SIZE property, so never assume it is there.
        if (state.hasProperty(SIZE)) {
            state = state.setValue(SIZE, 0);
        }
        return canSurvive(state, context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighbourState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighbourPos) {
        // Chalk does not survive the floor being removed.
        return canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    /** Renders the ring as particles. Called by the block entity, a few times a second. */
    public void ringParticles(ServerLevel level, BlockPos pos, double radius, boolean blood) {
        RandomSource random = level.getRandom();
        Vec3 centre = new Vec3(pos.getX() + 0.5D, pos.getY() + 0.08D, pos.getZ() + 0.5D);
        int points = (int) Math.max(12, Math.round(radius * 10.0D));
        for (int i = 0; i < points; i++) {
            double angle = (Math.PI * 2.0D / points) * i;
            double x = centre.x + Math.cos(angle) * radius;
            double z = centre.z + Math.sin(angle) * radius;
            level.sendParticles(blood ? ParticleTypes.DUST_PLUME : ParticleTypes.END_ROD,
                    x, centre.y, z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
        if (blood) {
            level.sendParticles(ParticleTypes.SMOKE, centre.x, centre.y + 0.2D, centre.z,
                    2, radius * 0.3D, 0.05D, radius * 0.3D, 0.01D);
        } else if (random.nextFloat() < 0.3F) {
            level.sendParticles(ParticleTypes.ENCHANT, centre.x, centre.y + 0.4D, centre.z,
                    1, radius * 0.4D, 0.3D, radius * 0.4D, 0.02D);
        }
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CircleBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide) {
            return null;
        }
        return (tickLevel, pos, tickState, blockEntity) -> {
            if (blockEntity instanceof CircleBlockEntity circle) {
                CircleBlockEntity.serverTick(tickLevel, pos, tickState, circle);
            }
        };
    }

    protected abstract MapCodec<? extends BaseEntityBlock> codec();
}
