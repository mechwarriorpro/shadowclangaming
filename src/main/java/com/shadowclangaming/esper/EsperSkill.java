package com.shadowclangaming.esper;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.phys.Vec3;

/**
 * Each skill is a callable ability. Clean-room: effects are vanilla particles/sounds + mob effects,
 * not copied logic. Cost is XP levels via Ability Chamber (less grindy).
 */
public record EsperSkill(String id, String name, int levelRequired, int xpCost, String categoryId) {

    public boolean canUse(EsperData data){
        return data.level() >= levelRequired && data.unlocked().contains(id);
    }

    public void use(ServerLevel level, ServerPlayer player, EsperData data){
        if(!canUse(data)) return;
        // 2s cooldown per skill via EsperData map so relog does not reset it (persistentData exploit)
        long now = level.getGameTime();
        if(now - data.cooldownAt(id) < 40) return;
        data.setCooldown(id, now); EsperHolder.set(player, data);
        // Minimal effect per skill family — expand per category
        // Esper team-damage fix: don't hurt allies (same scoreboard team), check creative invuln
        java.util.function.Predicate<net.minecraft.world.entity.LivingEntity> hostile = e -> {
            if(e==player) return false;
            if(e.isAlliedTo(player)) return false;
            if(e.isInvulnerable()) return false;
            return true;
        };
        switch(id){
            case "thunder_bolt" -> {
                var target = player.pick(24,0,false);
                if(target.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK){
                    var pos = ((net.minecraft.world.phys.BlockHitResult)target).getBlockPos();
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, pos.getX()+0.5,pos.getY()+1,pos.getZ()+0.5, 30,0.6,0.6,0.6,0.3);
                    level.playSound(null, pos, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.PLAYERS,1.0f,1.2f);
                    for(var e: level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(pos).inflate(3))) {
                        if(!hostile.test(e)) continue; e.hurt(level.damageSources().lightningBolt(), 8+data.level()*2);
                    }
                }
            }
            case "railgun" -> {
                Vec3 from = player.getEyePosition(); Vec3 dir = player.getLookAngle();
                var to = from.add(dir.scale(48));
                level.sendParticles(ParticleTypes.FLASH, to.x,to.y,to.z, 4,0.5,0.5,0.5,0);
                for(var e: level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(from,to).inflate(1))){
                    if(e==player) continue; e.hurt(level.damageSources().playerAttack(player), 18+data.level()*4);
                    e.setDeltaMovement(dir.scale(1.2)); e.hurtMarked=true;
                }
                level.playSound(null, player.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS,1.0f,0.6f);
            }
            case "short_teleport" -> {
                Vec3 dir = player.getLookAngle().scale(8 + data.level()*2);
                Vec3 dest = player.position().add(dir);
                // simple safe check: keep Y
                if(level.getBlockState(net.minecraft.core.BlockPos.containing(dest)).isAir()){
                    player.teleportTo(dest.x, dest.y, dest.z);
                    level.sendParticles(ParticleTypes.PORTAL, dest.x,dest.y+1,dest.z, 20,0.4,0.6,0.4,0.2);
                }
            }
            case "vector_reflect" -> {
                player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 120, 3));
                player.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 120, 0));
                level.sendParticles(ParticleTypes.CRIT, player.getX(),player.getY()+1,player.getZ(), 24,0.5,0.5,0.5,0.2);
            }
            case "light_beam" -> {
                Vec3 from2 = player.getEyePosition(); Vec3 dir2 = player.getLookAngle();
                var end = from2.add(dir2.scale(32));
                level.sendParticles(ParticleTypes.END_ROD, (from2.x+end.x)/2,(from2.y+end.y)/2,(from2.z+end.z)/2, 60, dir2.x*6,dir2.y*2,dir2.z*6,0.05);
                for(var e: level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(from2,end).inflate(1))){
                    if(e==player) continue; e.hurt(level.damageSources().magic(), 10+data.level()*3); e.igniteForSeconds(2);
                }
            }
            case "grav_push" -> {
                for(var e: level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(6+data.level()))){
                    if(e==player) continue;
                    Vec3 away = e.position().subtract(player.position()).normalize().scale(1.6);
                    e.setDeltaMovement(away); e.hurtMarked=true;
                }
                level.playSound(null, player.blockPosition(), SoundEvents.ENDER_DRAGON_FLAP, SoundSource.PLAYERS,1.0f,0.7f);
            }
            case "fire_jet" -> {
                Vec3 dir3 = player.getLookAngle();
                for(int i=1;i<=8;i++){ level.sendParticles(ParticleTypes.FLAME, player.getX()+dir3.x*i, player.getY()+1+dir3.y*i, player.getZ()+dir3.z*i, 6,0.2,0.2,0.2,0.02); }
                for(var e: level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(8))){
                    if(e==player) continue;
                    if(e.position().distanceTo(player.position())<8) { e.igniteForSeconds(4); e.hurt(level.damageSources().onFire(), 6+data.level()); }
                }
            }
            case "telekinetic_push" -> {
                for(var e: level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(10))){
                    if(e==player) continue; Vec3 away = e.position().subtract(player.position()).normalize().scale(1.0+data.level()*0.2); e.setDeltaMovement(away); e.hurtMarked=true;
                }
            }
            case "mind_stun" -> {
                for(var e: level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, new net.minecraft.world.phys.AABB(player.blockPosition()).inflate(8))){
                    if(e==player) continue; e.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2));
                    e.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 80, 1));
                }
                level.sendParticles(ParticleTypes.WITCH, player.getX(),player.getY()+1,player.getZ(), 24,0.6,0.6,0.6,0.1);
            }
            default -> {
                // generic buff for unhandled skills so nothing is dead
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 140, 0));
                level.sendParticles(ParticleTypes.HAPPY_VILLAGER, player.getX(),player.getY()+1,player.getZ(), 12,0.4,0.4,0.4,0.1);
            }
        }
        // small hunger cost as stamina
        player.causeFoodExhaustion(1.5F);
    }

    public static EsperSkill byId(String id){
        for(var cat: EsperCategory.values()) for(var sid: cat.skills()) if(sid.equals(id)) {
            int lvl = cat.skills().indexOf(id)/2 +1; // rough leveling: first 2 skills L1 etc
            return new EsperSkill(id, sid.replace("_"," "), Math.max(1,lvl), 2+lvl*2, cat.id());
        }
        return new EsperSkill(id, id, 1, 2, "electromaster");
    }
}
