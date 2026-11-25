package com.sp.util.tickinstances.client;

import net.minecraft.client.Minecraft;
import java.util.Vector;

public abstract class EndClientTickInstances {
    private static final Vector<EndClientTickInstances> all = new Vector<>();
    public EndClientTickInstances() { all.add(this); }
    public abstract void tickClient(Minecraft client);
    public static Vector<EndClientTickInstances> getInstances() { return (Vector<EndClientTickInstances>) all.clone(); }
}
