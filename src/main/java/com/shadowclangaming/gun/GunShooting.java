package com.shadowclangaming.gun;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One shot, start to finish: spread, ray, damage, element, tracer.
 *
 * Hitscan rather than a bullet entity, on purpose. At a six-shots-a-second Kestrel, a projectile
 * entity per shot is hundreds of ticking entities in a firefight; a ray is free and lands instantly,
 * which is also how a gun is supposed to feel.
 */
public final class GunShooting {

    public static final double RANGE = 48.0D;

    private GunShooting() {
    }

    /** @return true if the shot was fired (false = no target line at all, which still counts as a shot) */
    public static boolean fire(ServerLevel level, ServerPlayer shooter, GunStats stats) {
        Vec3 from = shooter.getEyePosition();
        Vec3 direction = spread(shooter.getLookAngle(), stats.accuracy(), level);

        Vec3 to = from.add(direction.scale(RANGE));

        // Entity first, then the block behind it - a shot at a zombie should not stop at the wall.
        LivingEntity victim = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity candidate : level.getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(0.5D),
                entity -> entity != shooter && entity.isAlive() && entity.isPickable())) {
            var clip = candidate.getBoundingBox().inflate(0.15D).clip(from, to);
            if (clip.isPresent()) {
                double distance = from.distanceToSqr(clip.get());
                if (distance < nearest) {
                    nearest = distance;
                    victim = candidate;
                }
            }
        }

        BlockHitResult blockHit = level.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, shooter));
        double blockDistance = blockHit.getType() == HitResult.Type.MISS
                ? Double.MAX_VALUE
                : from.distanceToSqr(blockHit.getLocation());

        Vec3 end = to;
        if (victim != null && nearest <= blockDistance) {
            end = victim.getBoundingBox().getCenter();
            hit(level, shooter, victim, stats);
        } else if (blockDistance != Double.MAX_VALUE) {
            end = blockHit.getLocation();
            level.sendParticles(ParticleTypes.CRIT, end.x, end.y, end.z, 4, 0.1D, 0.1D, 0.1D, 0.02D);
        }

        tracer(level, from, end, stats.element());
        level.playSound(null, shooter.blockPosition(), SoundEvents.ARROW_SHOOT, SoundSource.PLAYERS,
                0.8F, 1.6F + (float) level.getRandom().nextGaussian() * 0.1F);
        return true;
    }

    private static void hit(ServerLevel level, ServerPlayer shooter, LivingEntity victim, GunStats stats) {
        float damage = stats.damage();
        switch (stats.element()) {
            case EMBER -> victim.setRemainingFireTicks(
                    Math.max(victim.getRemainingFireTicks(), 100));
            case ARC -> {
                // Shocking something already on fire is the whole point of the pair.
                if (victim.getRemainingFireTicks() > 0) {
                    damage *= 1.5F;
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                        victim.getX(), victim.getY() + 1.0D, victim.getZ(), 14, 0.3D, 0.5D, 0.3D, 0.2D);
            }
            case ACID -> victim.addEffect(new MobEffectInstance(MobEffects.POISON, 100, 0));
            case VOID -> {
                victim.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 0));
                shooter.heal(damage * 0.2F);
            }
            case KINETIC -> {
                Vec3 push = victim.position().subtract(shooter.position()).normalize().scale(0.25D);
                victim.setDeltaMovement(victim.getDeltaMovement().add(push));
                victim.hurtMarked = true;
            }
        }
        victim.hurt(level.damageSources().playerAttack(shooter), damage);
        level.sendParticles(ParticleTypes.DAMAGE_INDICATOR,
                victim.getX(), victim.getY() + 1.0D, victim.getZ(), 3, 0.2D, 0.2D, 0.2D, 0.05D);
    }

    /** Random cone whose width is the inverse of accuracy: 0.995 is a laser, 0.25 is a shotgun. */
    private static Vec3 spread(Vec3 look, float accuracy, ServerLevel level) {
        double cone = (1.0D - Math.max(0.25F, Math.min(0.995F, accuracy))) * 0.14D;
        if (cone <= 0.0D) {
            return look;
        }
        double yaw = (level.getRandom().nextDouble() - 0.5D) * cone * 2.0D;
        double pitch = (level.getRandom().nextDouble() - 0.5D) * cone * 2.0D;
        Vec3 side = look.cross(new Vec3(0.0D, 1.0D, 0.0D));
        if (side.lengthSqr() < 1.0E-4D) {
            side = new Vec3(1.0D, 0.0D, 0.0D);
        }
        side = side.normalize();
        Vec3 up = side.cross(look).normalize();
        return look.add(side.scale(yaw)).add(up.scale(pitch)).normalize();
    }

    private static void tracer(ServerLevel level, Vec3 from, Vec3 to, GunElement element) {
        Vec3 delta = to.subtract(from);
        int steps = (int) Math.min(60, delta.length() * 1.5D);
        var particle = switch (element) {
            case EMBER -> ParticleTypes.FLAME;
            case ARC -> ParticleTypes.ELECTRIC_SPARK;
            case ACID -> ParticleTypes.ITEM_SLIME;
            case VOID -> ParticleTypes.PORTAL;
            case KINETIC -> ParticleTypes.CRIT;
        };
        for (int i = 0; i <= steps; i++) {
            Vec3 at = from.add(delta.scale(steps == 0 ? 0.0D : (double) i / steps));
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }
}
