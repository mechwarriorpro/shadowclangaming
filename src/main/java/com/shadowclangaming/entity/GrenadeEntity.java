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
 * made every grenade identical and made the declared form fuse a lie. Now impact hands a timer to
 * {@code warhead/ChargeSequence} whose length is that warhead's own effect duration in grenade form
 * ({@link ExplosivePackaging#fuseTicks}) - short for a grenade, but real: you can bounce an incendiary
 * off a wall and still get clear, and a heavy warhead's grenade visibly cooks before it goes.
 *
 * <p>WHY THE TIMER IS NOT A FIELD ON THIS CLASS. It was, and it never ran: vanilla's
 * {@code ThrowableItemProjectile.onHitBlock} discards a throwable that lands on a block, so {@code tick()}
 * was never called again and the countdown stopped at whatever it was armed with. A grenade showed its
 * whole charge effect and then nothing happened.
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
        WarheadType warhead = warheadOf();
        fuse = fuseTicks();
        // It has landed: stop it rolling away while it burns.
        setDeltaMovement(Vec3.ZERO);
        // THE TIMER CANNOT LIVE ON THIS ENTITY. super.onHit() above has already run, and for a throwable
        // that lands on a block vanilla's onHitBlock ends by discarding the entity - so tick() was never
        // called again, the fuse counted down to nothing, and detonate() never ran. The grenade armed,
        // played its charge effect for the whole fuse, and then did not exist. Every form now hands its
        // clock to ChargeSequence, which is a server-side list ticked from ShadowClanGaming and does not
        // care whether the entity that started it is still in the world.
        com.shadowclangaming.warhead.ChargeSequence.start(level, warhead, position(),
                ExplosivePackaging.power(warhead.basePower(), ExplosivePackaging.Form.GRENADE),
                0L, null, java.util.List.of(), -1, 0L, fuse);
        level.playSound(null, position().x, position().y, position().z, SoundEvents.TNT_PRIMED,
                SoundSource.NEUTRAL, 1.0F, 1.3F);
        level.sendParticles(ParticleTypes.SMOKE, position().x, position().y, position().z, 12,
                0.4D, 0.4D, 0.4D, 0.02D);
    }

    /**
     * Sparks while it cooks, if it is still in the world to be seen. The countdown itself is not here -
     * see {@link #onHit}. This is decoration only, so it is harmless if the entity has already been
     * removed by the time it would have run.
     */
    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide || fuse <= 0 || age % 4 != 0) {
            return;
        }
        if (level() instanceof net.minecraft.server.level.ServerLevel server) {
            server.sendParticles(ParticleTypes.ELECTRIC_SPARK, getX(), getY() + 0.2D, getZ(),
                    3, 0.15D, 0.15D, 0.15D, 0.0D);
        }
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
