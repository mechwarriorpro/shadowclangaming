package com.shadowclangaming.client;

import com.shadowclangaming.ShadowClanGaming;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** MOD-bus hook for the two in-world explosion shaders: the legacy core glow and the cinematic geometry. */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class WorldBlastShaderRegistration {
    private WorldBlastShaderRegistration() {}

    @SubscribeEvent
    public static void register(RegisterShadersEvent event) {
        WorldBlastShader.registerShaders(event);
        // The per-warhead world geometry (shells, rings, sky circles, arcs) has its own, much simpler
        // program: vertex colour straight out. Registering it separately means one failing to compile
        // cannot take the other with it.
        CinematicFx.registerShaders(event);
    }
}
