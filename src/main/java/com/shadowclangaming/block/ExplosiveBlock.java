package com.shadowclangaming.block;

import com.shadowclangaming.warhead.ExplosivePackaging;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A warhead you can place: {@code explosive_<warhead>}.
 *
 * Every warhead has one. It is the infantry version of a silo round: cheaper, immobile, and dangerous to
 * the person who built it.
 *
 * TRIGGERS. Redstone, or a right-click with anything in hand (which is the "light it" gesture - no need to
 * make players carry flint and steel for every warhead variant). Once primed it flashes and detonates after
 * its own fuse - which is the warhead's own effect length, not a constant: see
 * {@link ExplosivePackaging#fuseTicks}. A Big Bang Seed charge gives you half a minute to walk away and
 * watch it charge; a dud charge goes off almost immediately, because there is nothing to charge.
 *
 * CHAIN REACTIONS. A block destroyed by another explosion does not survive, but a primed one still goes
 * off on its own timer - so a row of these behaves like a fuse train, which is exactly what a row of
 * explosive blocks should do.
 *
 * The warhead is a per-block field, not a block-state property: {@link WarheadType} is an enum with an id
 * string, and turning that into a state property would mean a property with 38 values that nothing can
 * switch on cheaply. Each registered block holds its own type.
 */
public class ExplosiveBlock extends Block {

    /** Primed: flashing, counting down. */
    public static final BooleanProperty PRIMED = BooleanProperty.create("primed");

    private final WarheadType warhead;

    public ExplosiveBlock(WarheadType warhead, Properties properties) {
        super(properties);
        this.warhead = warhead;
        registerDefaultState(stateDefinition.any().setValue(PRIMED, false));
    }

    public WarheadType warhead() {
        return warhead;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(PRIMED);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(PRIMED, context.getLevel().hasNeighborSignal(context.getClickedPos()));
    }

    @Override
    protected void neighborChanged(BlockState state, Level level, BlockPos pos, Block neighborBlock,
                                   BlockPos neighborPos, boolean movedByPiston) {
        if (!level.isClientSide && level.hasNeighborSignal(pos) && !state.getValue(PRIMED)) {
            prime(level, pos, state);
        }
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
                                               BlockHitResult hit) {
        if (state.getValue(PRIMED)) {
            return InteractionResult.CONSUME;
        }
        if (!level.isClientSide) {
            prime(level, pos, state);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Light it. The fuse is scheduled, so a primed block survives being looked at and then goes off. */
    public void prime(Level level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state.setValue(PRIMED, true), Block.UPDATE_ALL);
        level.playSound(null, pos, SoundEvents.TNT_PRIMED, SoundSource.BLOCKS, 1.0F, 0.9F);
        if (level instanceof ServerLevel serverLevel) {
            int fuse = fuseTicks();
            serverLevel.scheduleTick(pos, this, fuse);
            // The effect IS the fuse. It starts on this tick, runs for exactly this long, and the blast
            // lands when it ends - so the thing you watch while walking away is the warhead itself, not a
            // wisp of smoke standing in for it.
            com.shadowclangaming.warhead.BlastFx.emitCharge(serverLevel, warhead, Vec3.atCenterOf(pos),
                    ExplosivePackaging.power(warhead.basePower(), ExplosivePackaging.Form.BLOCK), fuse);
        }
    }

    /**
     * How long this charge burns for, in ticks: the warhead's own cinematic duration in block form, from
     * the same table the effect packet and the grenade and minecart fuses read.
     */
    public int fuseTicks() {
        return ExplosivePackaging.fuseTicks(warhead.id(), warhead.basePower(),
                ExplosivePackaging.Form.BLOCK);
    }

    /**
     * A burning fuse you can see. With fuses now running from 2 to 45 seconds depending on the warhead, a
     * charge that sits there silently is indistinguishable from one that failed to light.
     */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos,
                            net.minecraft.util.RandomSource random) {
        if (!state.getValue(PRIMED)) {
            return;
        }
        double x = pos.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.7D;
        double y = pos.getY() + 1.05D;
        double z = pos.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.7D;
        level.addParticle(net.minecraft.core.particles.ParticleTypes.SMOKE, x, y, z, 0.0D, 0.05D, 0.0D);
        if (random.nextInt(3) == 0) {
            level.addParticle(net.minecraft.core.particles.ParticleTypes.ELECTRIC_SPARK,
                    x, y - 0.1D, z, 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource random) {
        detonate(level, pos);
    }

    /** Go off, and remove the block first so the warhead cannot be re-triggered by its own blast. */
    public void detonate(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        Vec3 at = Vec3.atCenterOf(pos);
        float power = ExplosivePackaging.power(warhead.basePower(), ExplosivePackaging.Form.BLOCK);
        warhead.detonate(level, at, power, 0L, null, java.util.List.of(), -1, pos.asLong());
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        // A block placed into an already-powered circuit primes immediately, which is what everyone
        // expects when they drop a charge onto a live wire.
        if (!level.isClientSide && state.getValue(PRIMED) && !oldState.is(this)) {
            level.scheduleTick(pos, this, fuseTicks());
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return false;
    }

    @Override
    protected int getSignal(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos,
                            Direction direction) {
        return 0;
    }
}
