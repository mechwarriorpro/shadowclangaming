package com.sp.networking.S2C;

import com.sp.networking.CustomPayloads;
import net.minecraft.client.Minecraft;

public class LavaSpewPacket {
    public static void receive(CustomPayloads.LavaSpewPacketPayload payload, Minecraft client) {
        // Spawn lava spew particles at payload.pos() — stub renders nothing offline
    }
    public static void receive(Object payload, Object ctx) {}
}
