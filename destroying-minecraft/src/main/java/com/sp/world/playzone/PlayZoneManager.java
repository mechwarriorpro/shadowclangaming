package com.sp.world.playzone;

import net.minecraft.world.phys.AABB;

import java.util.HashMap;
import java.util.Map;

public class PlayZoneManager {
    private static final Map<String, PlayZone> zones = new HashMap<>();
    public static void clearAllPlayZones() { zones.clear(); }
    public static void handlePacket(double minX, double maxX, double minY, double maxY, double minZ, double maxZ, String pid, boolean remove) {
        if (remove) zones.remove(pid); else zones.put(pid, new PlayZone(pid, new AABB(minX, minY, minZ, maxX, maxY, maxZ)));
    }
}
