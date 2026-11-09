package com.shadowclangaming.vehicle;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class HelicopterEntity extends VehicleEntity {
    public HelicopterEntity(EntityType<?> type, Level level){ super(type, level); }
    @Override protected void applyThrust(Vec3 look, float thr){
        // helicopter: vertical lift + forward when pitched
        Vec3 thrust = new Vec3(look.x*thr*0.6, thr*0.7 -0.02, look.z*thr*0.6);
        setDeltaMovement(getDeltaMovement().add(thrust.scale(0.12)));
    }
}
