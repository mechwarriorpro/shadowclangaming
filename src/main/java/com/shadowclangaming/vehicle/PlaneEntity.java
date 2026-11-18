package com.shadowclangaming.vehicle;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class PlaneEntity extends VehicleEntity {
    public PlaneEntity(EntityType<?> type, Level level){ super(type, level); }
    @Override protected void applyThrust(Vec3 look, float thr){
        // plane: mostly forward, little lift
        Vec3 thrust = new Vec3(look.x*thr*1.0, look.y*thr*0.25 + thr*0.08, look.z*thr*1.0);
        setDeltaMovement(getDeltaMovement().add(thrust.scale(0.15)));
    }
}
