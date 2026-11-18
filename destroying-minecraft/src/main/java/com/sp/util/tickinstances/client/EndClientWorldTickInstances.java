package com.sp.util.tickinstances.client;

import net.minecraft.world.level.Level;
import java.util.Vector;

public abstract class EndClientWorldTickInstances {
    private static final Vector<EndClientWorldTickInstances> all = new Vector<>();
    public EndClientWorldTickInstances() { all.add(this); }
    public abstract void tickClientWorld(Level level);
    public static Vector<EndClientWorldTickInstances> getInstances() { return (Vector<EndClientWorldTickInstances>) all.clone(); }
}
