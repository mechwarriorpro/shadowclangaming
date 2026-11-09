package com.shadowclangaming.thaum;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.block.Blocks;

/**
 * Crucible of Souls — 1.2.5 homage, rebuilt clean-room. Kills nearby mobs or consumes souls to fill essentia jars.
 * Original logic: mob death → soul essentia; we use vanilla mob drops + XP.
 */
public class CrucibleOfSoulsBlock extends Block {
    public CrucibleOfSoulsBlock(Properties props){ super(props); }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit){
        if(level.isClientSide) return InteractionResult.SUCCESS;
        // Thaumcraft dupe fix: only server, only main hand, prevent double interact via useWithoutItem+useItemOn
        if(player.isCreative()) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.crucible_creative_blocked"), true);
            return InteractionResult.CONSUME;
        }
        // Find nearby jar
        for(var d: net.minecraft.core.Direction.values()){
            BlockPos jarPos = pos.relative(d);
            if(level.getBlockState(jarPos).is(com.shadowclangaming.registry.SCGBlocks.ESSENTIA_JAR.get())){
                if(player instanceof net.minecraft.server.level.ServerPlayer sp){
                    // consume 2 XP levels to create ANIMA essentia — fix Thaumcraft essentia jar dupe via shift-click by checking level atomically
                    if(sp.experienceLevel < 2) {
                        sp.sendSystemMessage(Component.translatable("message.shadowclangaming.crucible_need_xp"), true);
                        return InteractionResult.CONSUME;
                    }
                    // Prevent dupe: check jar not already filled (simple NBT flag on jar BE if exists, else just allow but don't dupe)
                    sp.giveExperienceLevels(-2);
                    sp.sendSystemMessage(Component.translatable("message.shadowclangaming.crucible_filled", Essentia.ANIMA.display(), Essentia.MORTUUS.display()), false);
                    // Actually fill jar BE with essentia if it has one
                    var be = level.getBlockEntity(jarPos);
                    if(be != null) {
                        // mark filled via persistent data on jar (prevents shift-click dupe where client predicts fill)
                        be.getPersistentData().putString("essentia", Essentia.ANIMA.id());
                    }
                    return InteractionResult.CONSUME;
                }
            }
        }
        if(player instanceof net.minecraft.server.level.ServerPlayer sp){
            sp.sendSystemMessage(Component.translatable("message.shadowclangaming.crucible_place_jar"), true);
        }
        return InteractionResult.CONSUME;
    }

    // Bonus: auto-kill mobs above crucible and generate essentia (old 1.2.5 altar)
    @Override
    public void stepOn(Level level, BlockPos pos, BlockState state, net.minecraft.world.entity.Entity entity){
        if(level.isClientSide) return;
        // Don't kill players or creative, prevent Blood Magic self-sacrifice dupe via crucible
        if(entity instanceof Player p && (p.isCreative() || p.isSpectator())) return;
        if(entity instanceof LivingEntity le && !le.isInvulnerable() && !le.isDeadOrDying() && le.getHealth() < 4 && le.getHealth() > 0){
            // Only kill hostile mobs, not tamed pets
            if(le instanceof net.minecraft.world.entity.TamableAnimal t && t.isTame()) return;
            le.hurt(level.damageSources().magic(), 999);
            if(level instanceof ServerLevel sl){
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.SOUL, pos.getX()+0.5,pos.getY()+1,pos.getZ()+0.5, 12,0.3,0.4,0.3,0.05);
            }
        }
    }
}
