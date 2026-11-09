package com.sp.destruction.server;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

public class ServerDestructionEvents {
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        // Per-world tick is driven by ServerDestructionEvent.tick() via CCA
    }
    public static void registerServerEvents() {}
}
