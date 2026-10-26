package com.shadowclangaming.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.warhead.apex.Apex;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The post-processing half of an apex warhead.
 *
 * HOW THIS WORKS IN A MOD THAT CANNOT SHIP IRIS. A custom shader in Minecraft is a <em>post chain</em>:
 * the game draws the world into {@code minecraft:main}, and a chain of full-screen passes samples that
 * target and writes a new image back. That is a vanilla mechanism ({@link PostChain}) with no third-party
 * dependency, so the seventeen weapons can grade and distort the screen without requiring a shader pack -
 * and a player who uses Iris still gets these, because post chains run before/alongside the pack's own.
 *
 * The chain is built from our own files:
 *   assets/shadowclangaming/post_effect/&lt;name&gt;.json     the passes
 *   assets/shadowclangaming/shaders/program/post/&lt;name&gt;.json  the pipeline binding
 *   assets/shadowclangaming/shaders/post/&lt;name&gt;.fsh|.vsh      the GLSL
 *
 * FAILURE IS NOT FATAL. A shader that will not compile, a driver that refuses the program, or a resource
 * that failed to load all end the same way: the chain is marked dead, one line is logged, and the weapon
 * keeps its particles and its crater. Losing a screen effect must never lose the explosion.
 *
 * ONE CHAIN AT A TIME. Two overlapping detonations would need two full-screen passes; instead the strongest
 * active effect wins and the strength falls off with distance and with the timeline, so firing an Omega
 * into a Reality Breaker still reads as one event instead of a stack of filters.
 */
public final class ApexShaderFx {

    /** Chains we have tried to build, by name. A null value means "tried and failed". */
    private static final Map<String, PostChain> CHAINS = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();

    /** Chain currently mid-process, so we can read its strength. */
    private static String current;
    private static Apex currentApex;
    private static float currentStrength;
    private static long startedAtMillis;
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static long renderFrame;

    private ApexShaderFx() {
    }

    /** True if a chain is loaded and wants to draw this frame. */
    public static boolean active() {
        return current != null && currentStrength > 0.01F;
    }

    /**
     * Chooses the effect for this frame from the live detonations and draws it.
     *
     * Called once per frame after the world has been rendered: the screen already holds the world, so the
     * chain samples the finished image and writes it back.
     */
    public static void render(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            current = null;
            return;
        }

        // Pick the strongest effect near the camera; trace every candidate every render frame for diagnosis.
        long frame = ++renderFrame;
        String best = null;
        float bestStrength = 0.0F;
        var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        var activeEntries = ClientApexQueue.active();
        com.shadowclangaming.debug.ShadowDebug.log("APEX_RENDER_FRAME",
                "frame={} dimension={} camera=({}, {}, {}) entries={} chainsEnabled={} shaderStrength={} distanceScale={}",
                frame, minecraft.level.dimension().location(), camera.x, camera.y, camera.z,
                activeEntries.size(), com.shadowclangaming.config.SCGConfig.postChainsEnabled(),
                com.shadowclangaming.config.SCGConfig.shaderStrength(),
                com.shadowclangaming.config.SCGConfig.shaderDistance());
        for (ClientApexQueue.Entry entry : activeEntries) {
            float strength = strengthOf(entry, camera);
            double distance = Math.sqrt((entry.x-camera.x)*(entry.x-camera.x)
                    + (entry.y-camera.y)*(entry.y-camera.y) + (entry.z-camera.z)*(entry.z-camera.z));
            com.shadowclangaming.debug.ShadowDebug.log("APEX_CANDIDATE",
                    "frame={} id={} shader={} age={}/{} progress={} pos=({}, {}, {}) radius={} distance={} reach={} intensity={} computedStrength={}",
                    frame, entry.apex.id(), entry.apex.shader(), entry.age, entry.lifetime,
                    entry.age/(float)Math.max(1,entry.lifetime), entry.x, entry.y, entry.z,
                    entry.radius, distance, entry.radius+128.0D,
                    com.shadowclangaming.warhead.apex.ApexTimeline.coreIntensity(entry.apex, entry.age),
                    strength);
            if (strength > bestStrength) {
                bestStrength = strength;
                best = entry.apex.shader();
            }
        }
        current = best;
        currentApex = null;
        currentStrength = bestStrength;
        if (best == null || bestStrength <= 0.01F) {
            return;
        }
        for (ClientApexQueue.Entry entry : ClientApexQueue.active()) {
            if (entry.apex.shader().equals(best)
                    && strengthOf(entry, camera) >= bestStrength - 1.0E-6F) {
                currentApex = entry.apex;
                if (entry.age % 10 == 0) {
                    com.shadowclangaming.debug.ShadowDebug.log("APEX_FRAME",
                            "id={} age={}/{} progress={} strength={} postChains={}",
                            entry.apex.id(), entry.age, entry.lifetime,
                            entry.age / (float) Math.max(1, entry.lifetime), bestStrength,
                            com.shadowclangaming.config.SCGConfig.postChainsEnabled());
                }
                break;
            }
        }
        com.shadowclangaming.debug.ShadowDebug.log("APEX_SELECTED",
                "frame={} best={} strength={} overlayApex={} overlayStrength={} chainsEnabled={}",
                frame, best, bestStrength, currentApex == null ? "none" : currentApex.id(),
                overlayStrength(), com.shadowclangaming.config.SCGConfig.postChainsEnabled());
        if (com.shadowclangaming.config.SCGConfig.shadersEnabled()) {
            current = null; // Full-screen post chains are intentionally not used for explosion visuals.
            com.shadowclangaming.debug.ShadowDebug.log("APEX_SCREEN_PASS_SKIPPED",
                    "frame={} reason=world_space_shader_only", frame);
            return;
        }
        if (!com.shadowclangaming.config.SCGConfig.postChainsEnabled()) {
            current = null; // safe GUI effect path; never submit a full-screen framebuffer pass by default
            com.shadowclangaming.debug.ShadowDebug.log("POSTCHAIN_SKIPPED",
                    "frame={} effect=apex reason=config_disabled fallback=HUD strength={}",
                    frame, currentStrength);
            return;
        }

        PostChain chain = chain(best);
        if (chain == null) {
            current = null;
            return;
        }

        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.width != lastWidth || main.height != lastHeight) {
            chain.resize(main.width, main.height);
            lastWidth = main.width;
            lastHeight = main.height;
        }
        float shaderTime = (System.currentTimeMillis() - startedAtMillis) / 1000.0F;
        chain.setUniform("Strength", bestStrength);
        chain.setUniform("Time", shaderTime);
        long processStart = System.nanoTime();
        com.shadowclangaming.debug.ShadowDebug.log("POSTCHAIN_PROCESS_BEGIN",
                "frame={} effect=apex shader={} strength={} time={} main={}x{} chainClass={}",
                frame, best, bestStrength, shaderTime, main.width, main.height,
                chain.getClass().getName());
        try {
            chain.process(partialTick);
            main.bindWrite(true);
            com.shadowclangaming.debug.ShadowDebug.log("POSTCHAIN_PROCESS_END",
                    "frame={} effect=apex shader={} result=success elapsedMicros={} mainWriteBound=true",
                    frame, best, (System.nanoTime() - processStart) / 1000L);
        } catch (RuntimeException exception) {
            // A broken chain must not take the frame with it.
            FAILED.add(best);
            CHAINS.remove(best);
            current = null;
            com.shadowclangaming.debug.ShadowDebug.logThrowable("POSTCHAIN_PROCESS_FAIL",
                    "frame=" + frame + " effect=apex shader=" + best + " elapsedMicros="
                            + ((System.nanoTime() - processStart) / 1000L), exception);
            ShadowClanGaming.LOGGER.warn("Apex shader '{}' failed at draw time; safe overlay fallback", best,
                    exception);
        }
    }

    /**
     * How hard an effect should push, 0..1.
     *
     * Three multipliers, because each one is a different way for a screen effect to be wrong: too strong
     * at the edge of the blast, static through the whole animation, or applied to a player who is nowhere
     * near it. Distance falls off over the effect's own radius plus a margin; the timeline drives the rest.
     */
    public static float strengthOf(ClientApexQueue.Entry entry, net.minecraft.world.phys.Vec3 camera) {
        double dx = entry.x - camera.x;
        double dy = entry.y - camera.y;
        double dz = entry.z - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double reach = entry.radius + 128.0D;
        if (distance > reach) {
            return 0.0F;
        }
        double near = 1.0D - Math.min(1.0D, distance / reach);
        double curve = com.shadowclangaming.warhead.apex.ApexTimeline.coreIntensity(entry.apex, entry.age);
        // The client lifetime is the apex's own totalTicks(), not a shared hard-coded timeout. Give each
        // timeline a short attack and a visible tail, so a 25-tick Quasar and a 100+-tick Omega do not read
        // as the same fixed-duration screen filter.
        double progress = Math.min(1.0D, entry.age / (double) Math.max(1, entry.lifetime));
        double attack = Math.min(1.0D, entry.age / 4.0D);
        double tail = progress <= 0.80D ? 1.0D : Math.max(0.0D, (1.0D - progress) / 0.20D);
        // Distance falloff and a conservative cap prevent any one shader from blacking out the whole screen.
        double strength = near * near * curve * attack * tail;
        return (float) Math.max(0.0D, Math.min(0.68D, strength));
    }

    private static boolean hasResource(Minecraft minecraft, String path) {
        try {
            return minecraft.getResourceManager().getResource(
                    ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, path)).isPresent();
        } catch (RuntimeException exception) {
            com.shadowclangaming.debug.ShadowDebug.logThrowable("RESOURCE_PROBE_FAIL", path, exception);
            return false;
        }
    }

    /** Loads (once) and returns the chain for a shader name, or null if it cannot be built. */
    private static PostChain chain(String name) {
        if (FAILED.contains(name)) {
            return null;
        }
        PostChain existing = CHAINS.get(name);
        if (existing != null) {
            return existing;
        }
        Minecraft minecraft = Minecraft.getInstance();
        String programName = "apex_" + name;
        com.shadowclangaming.debug.ShadowDebug.log("APEX_SHADER_RESOURCES",
                "suffix={} effectJson={} programJson={} vertex={} fragment={}", name,
                hasResource(minecraft, "post_effect/apex_" + name + ".json"),
                hasResource(minecraft, "shaders/program/post/" + programName + ".json"),
                hasResource(minecraft, "shaders/program/post/apex_common.vsh"),
                hasResource(minecraft, "shaders/program/post/" + programName + ".fsh"));
        try {
            PostChain chain = new PostChain(
                    minecraft.getTextureManager(),
                    minecraft.getResourceManager(),
                    minecraft.getMainRenderTarget(),
                    // Apex.shader() stores a semantic suffix (e.g. "runes", "shock"). Resource files
                    // are consistently named apex_<suffix>.json; without the prefix every apex chain asks
                    // for a file that does not exist and silently degrades to particles only.
                    ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID,
                            "post_effect/apex_" + name + ".json"));
            CHAINS.put(name, chain);
            com.shadowclangaming.debug.ShadowDebug.log("APEX_SHADER", "loaded suffix={} asset=apex_{}.json",
                    name, name);
            startedAtMillis = System.currentTimeMillis();
            ShadowClanGaming.LOGGER.info("Apex shader '{}' loaded", name);
            return chain;
        } catch (Exception exception) {
            FAILED.add(name);
            com.shadowclangaming.debug.ShadowDebug.log("APEX_SHADER_FAIL", "suffix={} error={}",
                    name, exception.toString());
            ShadowClanGaming.LOGGER.warn("Apex shader '{}' could not load; particles only", name,
                    exception);
            return null;
        }
    }

    /** Names of the chains that are alive, for {@code /shadowclangaming fx}. */
    public static List<String> loaded() {
        return new ArrayList<>(CHAINS.keySet());
    }

    /** Names that failed to build, so a bug report can say which one. */
    public static List<String> failed() {
        return List.copyOf(FAILED);
    }

    /** Every shader the roster can ask for, for the docs command. */
    public static List<String> declared() {
        Set<String> names = new java.util.LinkedHashSet<>();
        for (Apex apex : Apex.values()) {
            names.add(apex.shader());
        }
        return List.copyOf(names);
    }

    /** Strength for the safe styled HUD fallback while experimental post chains are disabled or unavailable. */
    public static float overlayStrength() {
        return current == null && currentStrength > 0.01F
                && com.shadowclangaming.config.SCGConfig.fallbackOverlay() ? currentStrength : 0.0F;
    }

    /** Apex whose signature should be used by the HUD fallback. */
    public static Apex overlayApex() {
        return currentApex;
    }

    /** Normalized timeline progress for the current HUD fallback. */
    public static float overlayProgress() {
        if (currentApex == null) return 0.0F;
        for (ClientApexQueue.Entry entry : ClientApexQueue.active()) {
            if (entry.apex == currentApex) {
                return Math.min(1.0F, entry.age / (float) Math.max(1, entry.lifetime));
            }
        }
        return 1.0F;
    }

    /** Release the GL resources. Called on client shutdown. */
    public static void close() {
        for (PostChain chain : CHAINS.values()) {
            chain.close();
        }
        CHAINS.clear();
        FAILED.clear();
        current = null;
    }
}
