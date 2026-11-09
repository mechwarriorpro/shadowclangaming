package com.shadowclangaming.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A camouflaged landmine.
 *
 * It sits IN the ground: deploying a field replaces the top block of each column with the mine and stores
 * what was there, so the mine renders as that block until it goes off. The only way to see a mine in a
 * field is Night Vision - {@code MineRenderer} swaps to the mine's own model for players who have it.
 *
 * COST: the block entity holds one block state and does no ticking. Triggering uses a random tick -
 * vanilla's cheap, rate-limited scheduler - so a field of hundreds costs essentially nothing even though
 * every mine now needs a block entity to remember its disguise.
 */
public class MineBlock extends BaseEntityBlock {

    public static final com.mojang.serialization.MapCodec<MineBlock> CODEC = simpleCodec(MineBlock::new);

    /** Blocks a mine can see. */
    public static final double TRIGGER_RADIUS = 1.4D;
    /** Explosion power. Enough to kill, not enough to level a base. */
    public static final float BLAST_POWER = 6.0F;

    public MineBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    /**
     * The chunk mesher must NOT draw this block: the disguise is drawn by MineRenderer, which needs to
     * know whether the viewer has Night Vision.
     */
    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MineBlockEntity(pos, state);
    }

    @Override
    protected void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // One in four random ticks is roughly once a minute per block; the field is dense enough that
        // the first mine a player steps on goes off in well under a second of standing still.
        if (random.nextInt(4) != 0) {
            return;
        }
        boolean somethingOnIt = !level.getEntitiesOfClass(Entity.class,
                new AABB(pos).inflate(TRIGGER_RADIUS),
                entity -> entity.isAlive() && !(entity instanceof PrimedTnt)).isEmpty();
        if (somethingOnIt) {
            detonate(level, pos);
        }
    }

    /** Manual detonation, also used by the EMP warhead and by a player breaking it by hand. */
    public static void detonate(ServerLevel level, BlockPos pos) {
        BlockState disguise = level.getBlockEntity(pos) instanceof MineBlockEntity mine
                ? mine.mimic()
                : null;
        // Put the ground back first, then let the explosion take it: the blast looks like it came out of
        // the terrain, and the mine does not leave a neat one-block hole behind.
        if (disguise != null) {
            level.setBlock(pos, disguise, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
            level.getChunkSource().getChunk(pos.getX() >> 4, pos.getZ() >> 4, true);
        } else {
            level.removeBlock(pos, false);
        }
        level.explode(null, pos.getX() + 0.5D, pos.getY() + 0.2D, pos.getZ() + 0.5D,
                BLAST_POWER, Level.ExplosionInteraction.TNT);
        level.playSound(null, pos, SoundEvents.GENERIC_EXPLODE.value(), SoundSource.BLOCKS, 1.4F, 0.9F);
    }

    /** Stepping on one should hurt, and being blown up by one should be your problem, not mine. */
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, Entity entity) {
        if (level.isClientSide || !(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (entity instanceof LivingEntity) {
            detonate(serverLevel, pos);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        // A hidden mine must not puff smoke in a player's face. Only hint at it for someone who can
        // already see it (Night Vision), which keeps the camouflage honest.
        var player = level.getNearestPlayer(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                16.0D, false);
        if (player == null || !player.hasEffect(net.minecraft.world.effect.MobEffects.NIGHT_VISION)) {
            return;
        }
        if (random.nextInt(12) == 0) {
            level.addParticle(ParticleTypes.SMOKE, pos.getX() + 0.5D, pos.getY() + 0.15D,
                    pos.getZ() + 0.5D, 0.0D, 0.01D, 0.0D);
        }
    }

    /** Mines do not survive explosions of their own kind. */
    @Override
    public void playerDestroy(Level level, Player player, BlockPos pos, BlockState state,
                              net.minecraft.world.level.block.entity.BlockEntity blockEntity,
                              net.minecraft.world.item.ItemStack tool) {
        if (!level.isClientSide && level instanceof ServerLevel serverLevel) {
            // Breaking a mine leaves the block it was hiding as, not a hole.
            BlockState disguise = level.getBlockEntity(pos) instanceof MineBlockEntity mine
                    ? mine.mimic()
                    : null;
            serverLevel.removeBlock(pos, false);
            if (disguise != null && level.getBlockState(pos).isAir()) {
                serverLevel.setBlock(pos, disguise, net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
        }
        super.playerDestroy(level, player, pos, state, blockEntity, tool);
    }
}
