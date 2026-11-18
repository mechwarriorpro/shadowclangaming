package com.sp.destruction.server;

import com.sp.DestroyingMinecraft;
import com.sp.destruction.server.custom.BlackHoleDestructionServer;
import com.sp.destruction.server.custom.LaserDestructionServer;
import com.sp.destruction.server.custom.PlanetDestructionServer;
import com.sp.destruction.server.custom.SupernovaDestructionServer;

public class ServerDestructionEvents {
    public static final LaserDestructionServer CRACKS_SERVER = ServerDestructionEvent.register(new LaserDestructionServer());
    public static final PlanetDestructionServer PLANET_SERVER = ServerDestructionEvent.register(new PlanetDestructionServer());
    public static final SupernovaDestructionServer SUPERNOVA_SERVER = ServerDestructionEvent.register(new SupernovaDestructionServer());
    public static final BlackHoleDestructionServer BLACK_HOLE_SERVER = ServerDestructionEvent.register(new BlackHoleDestructionServer());

    public static void registerServerEvents() {
        DestroyingMinecraft.LOGGER.info("Registering server-side destruction events");
    }
}
