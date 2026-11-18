package com.shadowclangaming.block;

import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * Dimensional Beacon — the anchor for interdimensional missile strikes.
 *
 * Place one in the target dimension at the place you want orbital drops to land.
 * Control panel and launch pad can list beacons via command `beacons` and set target to nearest.
 * Crafted from one of every addon catalyst, it is the literal interlink of all addons.
 */
public class DimensionalBeaconBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<DimensionalBeaconBlock> CODEC = simpleCodec(DimensionalBeaconBlock::new);

    public DimensionalBeaconBlock(Properties props) {
        super(props);
    }

    public DimensionalBeaconBlock() {
        this(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_PURPLE)
                .strength(5.0F, 1200.0F)
                .sound(SoundType.STONE)
                .lightLevel(s -> 14)
                .requiresCorrectToolForDrops());
    }

    @Override protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState s) { return RenderShape.MODEL; }

    @Nullable @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DimensionalBeaconBlockEntity(pos, state);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (level instanceof ServerLevel sl) com.shadowclangaming.launcher.DimensionalBeaconRegistry.register(sl, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (level instanceof ServerLevel sl && !state.is(newState.getBlock())) {
            com.shadowclangaming.launcher.DimensionalBeaconRegistry.unregister(sl, pos);
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Nullable @Override
    public <T extends net.minecraft.world.level.block.entity.BlockEntity>
        net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide) return null;
        return (lvl, p, st, be) -> {
            if (be instanceof DimensionalBeaconBlockEntity b && lvl instanceof ServerLevel sl) DimensionalBeaconBlockEntity.tick(sl, p);
        };
    }
}
