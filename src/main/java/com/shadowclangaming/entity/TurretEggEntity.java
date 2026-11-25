package com.shadowclangaming.entity;

import com.shadowclangaming.registry.SCGEntities;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.projectile.ThrownEgg;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The turret chicken's egg.
 *
 * A vanilla {@link ThrownEgg} in every respect except the last line: when it would have spawned a
 * chicken, it spawns a {@link TurretChickenEntity} instead. That is the whole of
 * "if an egg hatches, instead of a regular chicken it's another turret chicken" - and because it is a
 * subclass of the vanilla projectile, knockback, particles and the renderer all come for free.
 *
 * One thing does NOT come for free: damage. A vanilla thrown egg deals **zero** - it only knocks back.
 * A turret egg is ammunition, so it carries {@link #DAMAGE} (2.5 hearts) on every hit.
 *
 * Vanilla's 1/8 hatch chance is kept: a 100% hatch rate would carpet the world in chickens within a
 * minute, and "sometimes" is what makes it unsettling rather than silly.
 */
public class TurretEggEntity extends ThrownEgg {

    /**
     * Damage per hit, in half-hearts: 5.0 = 2.5 hearts.
     *
     * Two and a half hearts is deliberately not lethal - a stack of turret chickens is a harassment
     * engine, not an execution. It is enough that a flock is a real threat to an unarmoured player and
     * enough that armour matters, without a single egg killing anyone who is paying attention.
     */
    public static final float DAMAGE = 5.0F;

    public TurretEggEntity(EntityType<? extends ThrownEgg> type, Level level) {
        super(type, level);
        // Never leave the carried stack empty: vanilla's break effect builds an ItemParticleOption from
        // getItem() on the client, and that constructor rejects empty stacks (see TurretChicken#shootAt).
        setItem(new ItemStack(Items.EGG));
    }

    public TurretEggEntity(Level level, LivingEntity shooter) {
        super(SCGEntities.TURRET_EGG.get(), level);
        setItem(new ItemStack(Items.EGG));
        setOwner(shooter);
        setPos(shooter.getX(), shooter.getEyeY() - 0.1D, shooter.getZ());
    }

    public TurretEggEntity(Level level, double x, double y, double z) {
        super(SCGEntities.TURRET_EGG.get(), level);
        setItem(new ItemStack(Items.EGG));
        setPos(x, y, z);
    }

    /**
     * Deal the damage.
     *
     * Deliberately does NOT call `super.onHitEntity`: vanilla's version is
     * `entity.hurt(damageSources().thrown(this, getOwner()), 0.0F)`, and a zero-damage hurt still opens
     * the target's invulnerability window on some paths - which would then swallow part of the real hit
     * in the same tick. One hurt call, with the real number.
     */
    @Override
    protected void onHitEntity(EntityHitResult result) {
        net.minecraft.world.entity.Entity target = result.getEntity();
        if (target == getOwner()) {
            return; // a chicken does not shoot itself in the back
        }
        target.hurt(damageSources().thrown(this, getOwner()), DAMAGE);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        // super() already ran the impact (damage, particles, sound). Its hatch branch spawned a plain
        // chicken; convert any chicken it just made into a turret chicken so the count is right.
        if (level().isClientSide || !(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        if (getRandom().nextInt(8) != 0) {
            return;
        }
        server.getEntitiesOfClass(Chicken.class, getBoundingBox().inflate(1.5D), chicken ->
                !(chicken instanceof TurretChickenEntity) && chicken.tickCount < 2).forEach(chicken -> {
            chicken.discard();
            TurretChickenEntity turret =
                    (TurretChickenEntity) SCGEntities.TURRET_CHICKEN.get().create(server);
            if (turret != null) {
                turret.moveTo(chicken.getX(), chicken.getY(), chicken.getZ(), chicken.getYRot(), 0.0F);
                server.addFreshEntity(turret);
            }
        });
        // And in case nothing was converted (the vanilla spawn failed on a full block), place ours.
        if (server.getEntitiesOfClass(TurretChickenEntity.class, getBoundingBox().inflate(2.0D)).isEmpty()) {
            TurretChickenEntity turret =
                    (TurretChickenEntity) SCGEntities.TURRET_CHICKEN.get().create(server);
            if (turret != null) {
                Vec3 at = position();
                turret.moveTo(at.x, at.y, at.z, getRandom().nextFloat() * 360.0F, 0.0F);
                server.addFreshEntity(turret);
            }
        }
    }

    /** Kept for the renderer: an egg, not an empty hand. */
    public ItemStack visualStack() {
        return new ItemStack(Items.EGG);
    }

    /** Impact poof, called where the vanilla projectile would show one. */
    protected void spawnImpactParticles() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel server)) {
            return;
        }
        server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, new ItemStack(Items.EGG)),
                getX(), getY(), getZ(), 8, 0.1D, 0.1D, 0.1D, 0.05D);
    }

}
