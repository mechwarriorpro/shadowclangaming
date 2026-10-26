package com.shadowclangaming.entity;

import com.shadowclangaming.item.ExplosiveGrenadeItem;
import com.shadowclangaming.warhead.ExplosivePackaging;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A thrown warhead.
 *
 * Extends {@link ThrowableItemProjectile}, which is the vanilla base for snowballs and eggs: gravity, drag,
 * collision and the item renderer all come for free, and all this class has to do is decide what happens
 * on impact. The warhead comes from the stack, so one entity type serves all 38 grenades.
 *
 * <p>IMPACT LIGHTS IT; IT DOES NOT DETONATE IT. This used to go off the instant it touched anything, which
 * made every grenade identical and made the declared form fuse a lie. Now impact starts a timer whose
 * length is that warhead's own effect duration in grenade form ({@link ExplosivePackaging#fuseTicks}) -
 * short for a grenade, but real: you can bounce an incendiary off a wall and still get clear, and a heavy
 * warhead's grenade visibly cooks before it goes.
 */
public class GrenadeEntity extends ThrowableItemProjectile {

    /** -1 = flying and unarmed; 0 or more = ticks left on the fuse after impact. */
    private int fuse = -1;

    public GrenadeEntity(EntityType<? extends GrenadeEntity> type, Level level) {
        super(type, level);
    }

    public GrenadeEntity(Level level, LivingEntity thrower, ItemStack stack) {
        super(com.shadowclangaming.registry.SCGEntities.GRENADE.get(), thrower, level);
        setItem(stack.copyWithCount(1));
    }

    public GrenadeEntity(Level level, double x, double y, double z) {
        super(com.shadowclangaming.registry.SCGEntities.GRENADE.get(), x, y, z, level);
    }

    @Override
    protected Item getDefaultItem() {
        // Never empty: ThrowableItemProjectile's sync and the client particle path both read this.
        return com.shadowclangaming.registry.SCGItems.EXPLOSIVE_GRENADES.get(0).get();
    }

    /** This warhead's grenade fuse, in ticks. */
    public int fuseTicks() {
        WarheadType warhead = warheadOf();
        return ExplosivePackaging.fuseTicks(warhead.id(), warhead.basePower(),
                ExplosivePackaging.Form.GRENADE);
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (level().isClientSide || fuse >= 0) {
            // Already cooking: a grenade that bounces must not restart its own fuse.
            return;
        }
        if (!(level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        fuse = fuseTicks();
        // It has landed: stop it rolling away while it burns.
        setDeltaMovement(Vec3.ZERO);
        // And the effect starts here, running for exactly the fuse: a grenade charges visibly for as long
        // as it is still a grenade, then goes off.
        com.shadowclangaming.warhead.BlastFx.emitCharge(level, warheadOf(), position(),
                ExplosivePackaging.power(warheadOf().basePower(), ExplosivePackaging.Form.GRENADE), fuse);
        level.playSound(null, position().x, position().y, position().z, SoundEvents.TNT_PRIMED,
                SoundSource.NEUTRAL, 1.0F, 1.3F);
        level.sendParticles(ParticleTypes.SMOKE, position().x, position().y, position().z, 12,
                0.4D, 0.4D, 0.4D, 0.02D);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || fuse < 0) {
            return;
        }
        if (fuse > 0) {
            fuse--;
            if (fuse % 4 == 0 && level() instanceof net.minecraft.server.level.ServerLevel server) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.2D, getZ(),
                        3, 0.15D, 0.15D, 0.15D, 0.0D);
            }
            return;
        }
        detonate();
    }

    /** The warhead goes off, wherever the grenade ended up. */
    private void detonate() {
        if (!(level() instanceof net.minecraft.server.level.ServerLevel level)) {
            discard();
            return;
        }
        WarheadType warhead = warheadOf();
        Vec3 at = position();
        float power = ExplosivePackaging.power(warhead.basePower(),
                ExplosivePackaging.Form.GRENADE);
        level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, 12, 0.4D, 0.4D, 0.4D, 0.02D);
        warhead.detonate(level, at, power, 0L, null, java.util.List.of(), -1, 0L);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.NEUTRAL,
                warhead == WarheadType.DECOY ? 2.0F : 1.0F, 1.4F);
        discard();
    }

    /**
     * Public, not protected: {@code ThrowableItemProjectile} widens both of these in 1.21.1, and an
     * override may not narrow what it overrides.
     */
    @Override
    public void addAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ScgFuse", fuse);
    }

    @Override
    public void readAdditionalSaveData(net.minecraft.nbt.CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        fuse = tag.getInt("ScgFuse");
    }

    /** The warhead this grenade carries. Falls back to HE if the stack lost its item. */
    public WarheadType warheadOf() {
        ItemStack stack = getItem();
        String id = stack.get(com.shadowclangaming.registry.SCGDataComponents.MODULE_WARHEAD.get());
        if (id != null && !id.isBlank()) {
            return WarheadType.byId(id);
        }
        return stack.getItem() instanceof ExplosiveGrenadeItem grenade
                ? grenade.warhead() : WarheadType.HE;
    }
}
