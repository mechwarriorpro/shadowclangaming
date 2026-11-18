package com.sp.world.playzone;

import net.minecraft.world.phys.AABB;

public class PlayZone {
    private final String id;
    private final AABB box;
    public PlayZone(String id, AABB box) { this.id = id; this.box = box; }
    public AABB getBoundingBox() { return box; }
    public String getId() { return id; }
}
