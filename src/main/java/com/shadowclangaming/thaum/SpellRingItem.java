package com.shadowclangaming.thaum;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;

/**
 * Spell Ring — 1.2.5 homage, expanded with modern essentia combos.
 * Each essentia combo = different effect. Clean-room: uses vanilla particles/effects, not copied.
 * Craftable, not dungeon loot.
 */
public class SpellRingItem extends Item {
    public SpellRingItem(Properties props){ super(props); }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand){
        ItemStack stack = player.getItemInHand(hand);
        if(level.isClientSide) return InteractionResultHolder.success(stack);
        if(!(player instanceof ServerPlayer sp)) return InteractionResultHolder.success(stack);

        // Read stored essentia from NBT (simple string)
        String ess = stack.getOrDefault(com.shadowclangaming.registry.SCGDataComponents.ESSENTIA.get(), "aer");
        Essentia e = Essentia.byId(ess);
        ServerLevel sl = sp.serverLevel();
        Vec3 pos = sp.position();

        // New effects per modern essentia, plus combos
        switch(e){
            case AER -> { // wind push
                for(var le: sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(sp.blockPosition()).inflate(6))){
                    if(le==sp) continue; le.setDeltaMovement(le.position().subtract(pos).normalize().scale(1.0)); le.hurtMarked=true;
                }
                sl.sendParticles(ParticleTypes.CLOUD, pos.x, pos.y+1, pos.z, 20,1,0.5,1,0.1);
            }
            case IGNIS -> {
                for(var le: sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(sp.blockPosition()).inflate(5))){
                    if(le==sp) continue; le.igniteForSeconds(5); le.hurt(sl.damageSources().onFire(), 6);
                }
                sl.playSound(null, sp.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS,1,1);
            }
            case AQUA -> {
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WATER_BREATHING, 600,0));
                sl.sendParticles(ParticleTypes.BUBBLE, pos.x,pos.y+1,pos.z, 30,0.5,0.5,0.5,0.2);
            }
            case TERRA -> {
                // raise earth wall (3 blocks)
                for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++){
                    var bp = sp.blockPosition().offset(dx,0,dz);
                    if(sl.getBlockState(bp).isAir()) sl.setBlockAndUpdate(bp, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState());
                }
                sl.sendParticles(ParticleTypes.CRIT, pos.x,pos.y+1,pos.z, 20,1,0.5,1,0.1);
            }
            case ANIMA -> {
                // soul: heal + summon wisp
                sp.heal(4); sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.REGENERATION,100,1));
                sl.sendParticles(ParticleTypes.SOUL, pos.x,pos.y+1,pos.z, 24,0.6,0.6,0.6,0.05);
            }
            case PRAECANTATIO -> {
                // magic missile
                Vec3 dir = sp.getLookAngle().scale(24);
                Vec3 end = sp.getEyePosition().add(dir);
                sl.sendParticles(ParticleTypes.ENCHANTED_HIT, (sp.getX()+end.x)/2,(sp.getY()+end.y)/2,(sp.getZ()+end.z)/2, 60, dir.x*0.3,dir.y*0.2,dir.z*0.3,0.05);
                for(var le: sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(sp.getEyePosition(), end).inflate(1))){
                    if(le==sp) continue; le.hurt(sl.damageSources().magic(), 8);
                }
            }
            case VITIUM -> {
                // taint field
                for(var le: sl.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(sp.blockPosition()).inflate(7))){
                    if(le==sp) continue; le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.WITHER,80,0));
                }
                sl.sendParticles(ParticleTypes.SMOKE, pos.x,pos.y+1,pos.z, 24,0.8,0.4,0.8,0.02);
            }
            case LUX -> {
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.NIGHT_VISION,600,0));
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.GLOWING,200,0));
            }
            default -> {
                // compound combos: e.g., AER+IGNIS = firestorm, etc — simplified as random modern essentia effect
                sl.sendParticles(ParticleTypes.ENCHANT, pos.x,pos.y+1,pos.z, 30,0.6,0.6,0.6,0.3);
                sp.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED,200,1));
            }
        }
        // rotate essentia on shift-use to cycle without needing many rings
        if(sp.isShiftKeyDown()){
            var vals = Essentia.values();
            int idx = (e.ordinal()+1)%vals.length;
            stack.set(com.shadowclangaming.registry.SCGDataComponents.ESSENTIA.get(), vals[idx].id());
            sp.sendSystemMessage(Component.literal("Spell Ring attuned to "+vals[idx].display()+" ("+vals[idx].id()+")"), true);
        }
        sp.getCooldowns().addCooldown(this, 40);
        return InteractionResultHolder.success(stack);
    }
}
