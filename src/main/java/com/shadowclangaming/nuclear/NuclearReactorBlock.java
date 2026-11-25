package com.shadowclangaming.nuclear;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Nuclear reactor — homage to Nuclear Science / ICBM / HBM / IC2.
 * Generates FE, needs cooling, can meltdown if overheated. Clean-room, not copied.
 */
public class NuclearReactorBlock extends Block implements EntityBlock {
    public NuclearReactorBlock(Properties p){ super(p); }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state){ return new NuclearReactorBlockEntity(pos, state); }
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit){
        if(level.isClientSide) return InteractionResult.SUCCESS;
        if(player instanceof net.minecraft.server.level.ServerPlayer sp){
            var be = level.getBlockEntity(pos);
            if(be instanceof NuclearReactorBlockEntity r){
                sp.sendSystemMessage(Component.literal("Reactor: "+r.heat()+"/"+r.maxHeat()+" heat, "+r.energy()+" FE, rods "+(r.rodsIn()?"IN":"OUT")), false);
                if(sp.isShiftKeyDown()) r.toggleRods();
            }
        }
        return InteractionResult.CONSUME;
    }
    @Override public void onPlace(BlockState state, Level level, BlockPos pos, BlockState old, boolean moved){
        if(level instanceof ServerLevel sl && !sl.isClientSide) sl.scheduleTick(pos, this, 20);
        // register FE capability for reactor (fixes capability leak where reactor never exposed FE)
        if(level instanceof ServerLevel) { /* capability registered in ShadowClanGaming registerCapabilities */ }
    }
    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, net.minecraft.util.RandomSource rnd){
        if(!level.getBlockState(pos).is(this)) return;
        var be = level.getBlockEntity(pos);
        if(be instanceof NuclearReactorBlockEntity r) r.tick();
        // Only reschedule if block still exists (ICBM infinite tick recursion fix)
        if(level.getBlockState(pos).is(this)) level.scheduleTick(pos, this, 20);
    }
    // Expose FE capability so cables find it
    public static void registerCapabilities(net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent e){
        e.registerBlockEntity(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, com.shadowclangaming.registry.SCGBlockEntities.NUCLEAR_REACTOR.get(), (be, side) -> be);
    }
}
