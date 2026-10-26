package com.shadowclangaming.client;

import com.shadowclangaming.ShadowClanGaming;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Client hooks for the apex warheads.
 *
 * Two jobs, two events:
 *
 *   {@link LevelTickEvent.Post} (client level only) advances the effect queue and spawns the particle
 *   fields - once per tick, with no per-frame allocation.
 *
 *   {@link RenderLevelStageEvent} draws the post-processing chain, after the world has been rendered and
 *   before the HUD: that is the only point in the frame where {@code minecraft:main} holds the finished
 *   world image for a shader to sample.
 *
 * This class is GAME bus and Dist.CLIENT, so a dedicated server never loads it.
 */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ApexClientEvents {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) {
            ApexVisuals.tick();
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            return;
        }
        ApexShaderFx.render(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        // An ordinary warhead only gets the post chain when no apex weapon is already using it. Ordered here,
        // not in a second listener, because listener order between two classes is not something to rely on.
        if (!ApexShaderFx.active()) {
            BlastShaderFx.render(event.getPartialTick().getGameTimeDeltaPartialTick(false));
        }
        // Shader geometry is drawn in world space at AFTER_PARTICLES by WorldBlastShader. These legacy
        // managers only maintain intensity for camera-shake diagnostics; no HUD or framebuffer flash is drawn.
    }

    private ApexClientEvents() {
    }
}
