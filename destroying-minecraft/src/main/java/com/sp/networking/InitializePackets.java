package com.sp.networking;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Fabric-compat alias — original code imported com.sp.networking.InitializePackets.
 * Delegates to com.sp.network.InitializePackets so both packages work.
 */
public class InitializePackets {
    public static void registerServerNetworking() { com.sp.network.InitializePackets.registerServerNetworking(); }
    public static void registerClientNetworking() { com.sp.network.InitializePackets.registerClientNetworking(); }
    public static void registerPayloads(RegisterPayloadHandlersEvent event) { com.sp.network.InitializePackets.registerPayloads(event); }
}
