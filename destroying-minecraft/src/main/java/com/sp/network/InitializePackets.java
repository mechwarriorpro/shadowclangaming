package com.sp.network;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

public class InitializePackets {
    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {}
    public static void registerServerNetworking() {}
    public static void registerClientNetworking() {}
}
