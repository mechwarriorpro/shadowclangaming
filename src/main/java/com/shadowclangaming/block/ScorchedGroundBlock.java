package com.shadowclangaming.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Thin scorched crust left by beams and blast fronts. 2 layers high (2px), no collision for entities
 * to walk through, fades after 2-3 minutes. Replaces only air; original ground stays underneath.
 */
public class ScorchedGroundBlock extends Block {
    private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 2, 16);
    public ScorchedGroundBlock(Properties props) { super(props); }
    public ScorchedGroundBlock() {
        super(Properties.of().mapColor(net.minecraft.world.level.material.MapColor.COLOR_BLACK)
                .strength(0.3F).sound(SoundType.BASALT).noOcclusion().noCollission().lightLevel(s -> 1));
    }
    @Override public VoxelShape getShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c){ return SHAPE; }
    @Override public VoxelShape getCollisionShape(BlockState s, BlockGetter l, BlockPos p, CollisionContext c){ return Shapes.empty(); }
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved) {
        if(!level.isClientSide) level.scheduleTick(pos, this, 2400 + level.getRandom().nextInt(1200)); // 2-3 min
    }
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource r) {
        level.removeBlock(pos, false);
    }
    @Override public boolean canSurvive(BlockState state, net.minecraft.world.level.LevelReader l, BlockPos p){
        return !l.getBlockState(p.below()).isAir();
    }
}
