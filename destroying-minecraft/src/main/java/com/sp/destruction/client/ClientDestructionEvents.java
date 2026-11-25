package com.sp.destruction.client;

import com.sp.DestroyingMinecraft;
import com.sp.destruction.client.custom.BlackHoleDestructionClient;
import com.sp.destruction.client.custom.LaserDestructionClient;
import com.sp.destruction.client.custom.NukeDestructionClient;
import com.sp.destruction.client.custom.PlanetDestructionClient;
import com.sp.destruction.client.custom.SupernovaDestructionClient;

public class ClientDestructionEvents {
    public static final NukeDestructionClient NUKE_CLIENT = ClientDestructionEvent.register(new NukeDestructionClient());
    public static final LaserDestructionClient CRACKS_CLIENT = ClientDestructionEvent.register(new LaserDestructionClient());
    public static final PlanetDestructionClient PLANET_CLIENT = ClientDestructionEvent.register(new PlanetDestructionClient());
    public static final SupernovaDestructionClient SUPERNOVA_CLIENT = ClientDestructionEvent.register(new SupernovaDestructionClient());
    public static final BlackHoleDestructionClient BLACK_HOLE_CLIENT = ClientDestructionEvent.register(new BlackHoleDestructionClient());

    public static void registerClientEvents() {
        DestroyingMinecraft.LOGGER.info("Registering client-side destruction events");
    }
}
