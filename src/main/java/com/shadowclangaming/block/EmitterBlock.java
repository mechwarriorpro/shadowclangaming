package com.shadowclangaming.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The muzzle of a built rifle.
 *
 * On its own it is a lump of metal with a crystal in it: it does nothing and it is not a weapon. Its
 * whole purpose is to be the one block inside a blueprint volume that says "this build points
 * somewhere". The blueprint table refuses a build without exactly one of these, which is what stops a
 * player from marking an arbitrary box and calling it a gun.
 */
public class EmitterBlock extends Block {

    public EmitterBlock(Properties properties) {
        super(properties);
    }

    /** A little amethyst shimmer so a built emitter is findable at night. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) != 0) {
            return;
        }
        level.addParticle(ParticleTypes.END_ROD,
                pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.4D,
                pos.getY() + 1.05D,
                pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.4D,
                0.0D, 0.01D, 0.0D);
    }
}
