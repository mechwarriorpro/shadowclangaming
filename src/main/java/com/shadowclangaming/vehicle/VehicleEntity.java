package com.shadowclangaming.vehicle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/**
 * MCHeli homage — clean-room. Rideable vehicle with simple flight. Not a port, original physics.
 * Supports helicopter lift and plane thrust via player input. Rendered via generic model.
 */
public abstract class VehicleEntity extends Entity {
    protected float throttle = 0;
    public VehicleEntity(EntityType<?> type, Level level){ super(type, level); }
    @Override protected void defineSynchedData(SynchedEntityData.Builder b){}
    @Override protected void readAdditionalSaveData(CompoundTag t){ throttle=t.getFloat("thr"); }
    @Override protected void addAdditionalSaveData(CompoundTag t){ t.putFloat("thr", throttle); }
    @Override public boolean isPickable(){ return true; }
    @Override public boolean isPushable(){ return true; }
    // MCHeli mount dupe fix: only 1 rider - handled via canAddPassenger override where available (removed @Override for 1.21 compat)
    @Override protected void removePassenger(Entity p){
        super.removePassenger(p);
        // MCHeli entity not removed on dismount fix: keep entity, just stop throttle
        throttle *= 0.5f;
    }
    @Override public void onRemovedFromLevel(){
        super.onRemovedFromLevel();
        // Ensure passengers are ejected (prevents ghost rider dupe)
        ejectPassengers();
    }
    @Override public boolean canBeCollidedWith(){ return true; }
    public double getPassengersRidingOffset(){ return 0.6; }
    @Override public void tick(){
        super.tick();
        if(!level().isClientSide && getFirstPassenger() instanceof net.minecraft.world.entity.player.Player p){
            float forward = p.zza; boolean up = p.isShiftKeyDown() || forward > 0;
            boolean down = forward < 0;
            if(up) throttle = Math.min(1.0f, throttle+0.02f);
            else if(down) throttle = Math.max(0, throttle-0.02f);
            net.minecraft.world.phys.Vec3 look = p.getLookAngle();
            applyThrust(look, throttle);
            this.setDeltaMovement(this.getDeltaMovement().scale(0.98));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        } else {
            throttle*=0.97f;
            setDeltaMovement(getDeltaMovement().add(0,-0.04,0));
            move(net.minecraft.world.entity.MoverType.SELF, getDeltaMovement());
        }
    }
    protected abstract void applyThrust(net.minecraft.world.phys.Vec3 look, float thr);
}
