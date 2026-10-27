package com.shadowclangaming.client;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.config.SCGConfig;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * Client hooks for the ordinary warhead effects.
 *
 * Four events, four jobs, all of them cheap:
 *
 *   LevelTickEvent.Post          advance the queue (once per tick, not per frame)
 *   (the post chain itself is drawn from ApexClientEvents, so only one full-screen pass ever runs)
 *   RenderGuiLayerEvent.Post     the HUD fallback when no chain will build
 *   ViewportEvent.CameraAngles   camera shake
 *
 * GAME bus + Dist.CLIENT, so a dedicated server never loads any of it.
 */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class BlastClientEvents {

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (event.getLevel().isClientSide()) {
            ClientBlastQueue.tick();
            WorldBlastShader.tickFallback();
            // The shader-free half of every effect: world particles from the same motif table, so an
            // effect is still attached to the detonation point on a machine with no working shader.
            CinematicFx.tickParticles(net.minecraft.client.Minecraft.getInstance());
        }
    }

    /**
     * The world-space effect itself.
     *
     * AFTER_PARTICLES, after the terrain and the weather are in the framebuffer, so the volume is
     * depth-tested against the world rather than painted over it. Volumetric first, triangles only if the
     * raymarcher is not usable on this machine - and the particle pass above runs either way.
     */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!VolumetricFx.render(event)) {
            CinematicFx.render(event);
        }
    }

    // The post chain is drawn from ApexClientEvents, immediately after the apex chain and only when an
    // apex weapon is NOT already owning the screen: two full-screen passes in one frame is a measurable cost
    // and turning the image into mush. One screen effect at a time, always.

    /**
     * Camera shake.
     *
     * Two different frequencies on purpose: a slow one for the ground moving and a fast one for the pressure
     * wave, so it reads as a shockwave rather than a camera wobble. The amount is the same strength value the
     * shaders use, so a player who turns shake down to zero still gets everything else.
     */
    /** How long the camera keeps moving after a blast lands, in seconds. */
    private static final double SHAKE_SECONDS = 0.75D;
    private static long shakeStartMs = Long.MIN_VALUE / 4;
    private static float lastStrength;

    @SubscribeEvent
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (!SCGConfig.screenShake() || SCGConfig.shakeStrength() <= 0.0D) {
            return;
        }
        float strength = BlastShaderFx.strength();
        if (strength <= 0.01F) {
            lastStrength = 0.0F;
            return;
        }
        long now = System.currentTimeMillis();
        // A shockwave is an impulse, not a state. The shake used to track the effect's own strength for
        // the whole life of the effect, and these run up to 1200 ticks - so the camera shook for a full
        // minute after every grenade. Restart on a rising edge (a new blast, or a stronger one) and decay
        // to nothing inside three quarters of a second regardless of how long the effect plays.
        if (strength > lastStrength + 0.02F) {
            shakeStartMs = now;
        }
        lastStrength = strength;
        double envelope = 1.0D - (now - shakeStartMs) / 1000.0D / SHAKE_SECONDS;
        if (envelope <= 0.0D) {
            return;
        }
        envelope *= envelope;
        float scale = (float) (SCGConfig.shakeStrength() * strength * envelope
                * BlastShaderFx.overlayStyle().shake() * 1.6F);
        if (scale <= 0.0F) {
            return;
        }
        double time = (now % 100_000L) / 1000.0D;
        float yaw = (float) (Math.sin(time * 47.0D) * Math.sin(time * 13.0D)) * scale;
        float pitch = (float) (Math.cos(time * 41.0D) * Math.sin(time * 11.0D)) * scale * 0.6F;
        float roll = (float) (Math.sin(time * 29.0D)) * scale * 0.5F;
        event.setYaw(event.getYaw() + yaw);
        event.setPitch(event.getPitch() + pitch);
        event.setRoll(event.getRoll() + roll);
    }

    private BlastClientEvents() {
    }
}
