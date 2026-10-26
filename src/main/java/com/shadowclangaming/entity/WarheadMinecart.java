package com.shadowclangaming.entity;

import com.shadowclangaming.warhead.ExplosivePackaging;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.MinecartTNT;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * A minecart that carries a warhead instead of TNT.
 *
 * Extends {@link MinecartTNT} rather than writing a minecart from scratch: rails, physics, the priming
 * rules, the activator-rail trigger and the renderer all come from vanilla.
 *
 * <p>TWO THINGS ARE OVERRIDDEN, and they are the whole feature. What "explode" means, and how long the
 * fuse is: vanilla's TNT cart cooks for a fixed 80 ticks whatever is in it, so this cart keeps vanilla's
 * private fuse at -1 (by overriding {@link #primeMinecart} without calling super) and runs its own, whose
 * length comes from the warhead's own effect duration ({@link ExplosivePackaging#fuseTicks}). An activator
 * rail, a collision or a destroyed cart lights it; the warhead then goes off on the warhead's clock, not
 * on a constant one.
 *
 * The warhead rides on the entity (NBT `Warhead`) rather than being re-derived from the item every time,
 * because a minecart that has been picked up and put back down must still be the same weapon.
 */
public class WarheadMinecart extends MinecartTNT {

    /** Used when an old or hand-spawned cart has no warhead recorded. */
    private static final WarheadType FALLBACK = WarheadType.HE;

    private WarheadType warhead = FALLBACK;
    /** The item this cart drops, so a broken cart gives back the warhead it was carrying. */
    private Item dropItem;
    /** -1 = not lit; 0 or more = ticks left before the warhead goes off. Ours, not vanilla's. */
    private int scgFuse = -1;

    public WarheadMinecart(EntityType<? extends WarheadMinecart> type, Level level) {
        super(type, level);
    }

    public WarheadMinecart(Level level, double x, double y, double z, WarheadType warhead, Item dropItem) {
        super(com.shadowclangaming.registry.SCGEntities.EXPLOSIVE_MINECART.get(), level);
        setPos(x, y, z);
        this.warhead = warhead == null ? FALLBACK : warhead;
        this.dropItem = dropItem;
    }

    public WarheadType warhead() {
        return warhead;
    }

    /**
     * The actual detonation.
     *
     * Vanilla's version is an explosion; ours hands off to the warhead, which knows what it means. The
     * cart is discarded first so the blast cannot re-prime it.
     */
    /** This warhead's minecart fuse, in ticks. */
    public int fuseTicks() {
        return ExplosivePackaging.fuseTicks(warhead.id(), warhead.basePower(),
                ExplosivePackaging.Form.MINECART);
    }

    /**
     * Light it.
     *
     * Deliberately does NOT call {@code super.primeMinecart()}: that would set vanilla's own 80-tick fuse,
     * and vanilla would then call {@link #explode} on its schedule instead of ours. Leaving vanilla's fuse
     * at -1 means this cart detonates exactly once, when our timer says so.
     */
    @Override
    public void primeMinecart() {
        if (this.level().isClientSide) {
            return;
        }
        if (this.scgFuse >= 0) {
            return;
        }
        this.scgFuse = fuseTicks();
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(),
                net.minecraft.sounds.SoundEvents.TNT_PRIMED,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 0.9F);
    }

    /** Our own countdown. Vanilla's tick still runs the rails and the physics underneath. */
    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide || this.scgFuse < 0) {
            return;
        }
        if (this.scgFuse > 0) {
            this.scgFuse--;
            if (this.scgFuse % 4 == 0) {
                this.level().sendParticles(net.minecraft.core.particles.ParticleTypes.SMOKE,
                        this.getX(), this.getY() + 0.6D, this.getZ(), 4, 0.2D, 0.2D, 0.2D, 0.02D);
            }
            return;
        }
        this.scgFuse = -1;
        explode(0.0D);
    }

    /** The fuse left, for read-outs and debugging. -1 when the cart is not lit. */
    public int fuseLeft() {
        return scgFuse;
    }

    @Override
    public void explode(double radiusScale) {
        if (this.level().isClientSide) {
            return;
        }
        Vec3 at = position();
        discard();
        if (!(this.level() instanceof net.minecraft.server.level.ServerLevel level)) {
            return;
        }
        float power = ExplosivePackaging.power(warhead.basePower(),
                ExplosivePackaging.Form.MINECART);
        warhead.detonate(level, at, power, 0L, null, java.util.List.of(), -1,
                blockPosition().asLong());
    }

    @Override
    protected Item getDropItem() {
        if (dropItem != null) {
            return dropItem;
        }
        return com.shadowclangaming.registry.SCGItems.EXPLOSIVE_MINECARTS.values().stream()
                .findFirst().map(holder -> (Item) holder.get()).orElse(super.getDropItem());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putString("Warhead", warhead.id());
        tag.putInt("ScgFuse", scgFuse);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        warhead = WarheadType.byId(tag.getString("Warhead"));
        scgFuse = tag.getInt("ScgFuse");
    }

    @Override
    public ItemStack getPickResult() {
        return new ItemStack(getDropItem());
    }
}
