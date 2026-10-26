package com.shadowclangaming.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.config.SCGConfig;
import com.shadowclangaming.warhead.BlastStyle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The post-processing half of every ordinary warhead: the answer to "explosives have no shaders".
 *
 * HOW A SHADER HAPPENS WITHOUT A SHADER PACK. Minecraft's own mechanism is a <em>post chain</em>: the world
 * has already been drawn into {@code minecraft:main}, and a chain of full-screen passes samples that image
 * and writes a new one. It is vanilla ({@link PostChain}), needs no Iris/OptiFine, and works underneath a
 * shader pack.
 *
 * Explosion visuals are now rendered at the detonation in world space by {@link WorldBlastShader}; this
 * class only selects and measures effects for camera-shake/status diagnostics. No HUD flash or fullscreen
 * color grade is drawn here.
 *
 * ONE CHAIN AT A TIME. Two overlapping detonations would otherwise need two full-screen passes; the strongest
 * visible effect wins and the rest only contribute particles, which is also why a carpet barrage does not
 * turn the screen into soup.
 */
public final class BlastShaderFx {

    /** Chain name we asked for -> built chain. */
    private static final Map<String, PostChain> CHAINS = new HashMap<>();
    /** Chain names that will not build. Never retried. */
    private static final Set<String> FAILED = new HashSet<>();
    /** Style id -> "own", "fallback" or "overlay", for the diagnostics command. */
    private static final Map<String, String> MODE = new HashMap<>();

    private static String current;
    private static float currentStrength;
    private static float currentProgress;
    private static BlastStyle currentStyle = BlastStyle.FALLBACK;
    private static long startedAtMillis = System.currentTimeMillis();
    private static int lastWidth = -1;
    private static int lastHeight = -1;
    private static long renderFrame;

    private BlastShaderFx() {
    }

    /** True while a chain is drawing this frame. */
    public static boolean active() {
        return current != null && currentStrength > 0.01F;
    }

    /**
     * Draw the strongest visible detonation's chain.
     *
     * Called from {@code RenderLevelStageEvent} AFTER_LEVEL: the world is in {@code minecraft:main} and the HUD
     * has not been drawn yet, which is the only moment a full-screen pass can sample the finished frame.
     */
    public static void render(float partialTick) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            current = null;
            return;
        }
        long frame = ++renderFrame;
        ClientBlastQueue.Entry best = strongest(minecraft, frame);
        if (best == null) {
            com.shadowclangaming.debug.ShadowDebug.log("BLAST_RENDER_FRAME",
                    "frame={} queue=0 selected=none chainsEnabled={}", frame, SCGConfig.postChainsEnabled());
            current = null;
            currentStrength = 0.0F;
            currentProgress = 0.0F;
            return;
        }
        currentStyle = best.style;
        currentProgress = best.progress();
        currentStrength = strengthOf(best, minecraft);
        com.shadowclangaming.debug.ShadowDebug.log("BLAST_SELECTED",
                "frame={} style={} shader={} pos=({}, {}, {}) radius={} age={}/{} progress={} strength={} chainsEnabled={}",
                frame, best.style.id(), best.style.shader(), best.x, best.y, best.z, best.radius,
                best.age, best.lifetime, best.progress(), currentStrength, SCGConfig.postChainsEnabled());

        if (SCGConfig.shadersEnabled()) {
            current = null; // Full-screen post chains are intentionally not used for explosion visuals.
            com.shadowclangaming.debug.ShadowDebug.log("BLAST_SCREEN_PASS_SKIPPED",
                    "frame={} reason=world_space_shader_only", frame);
            return;
        }
        if (!SCGConfig.postChainsEnabled()) {
            current = null; // safe HUD treatment is drawn after the world; no framebuffer/post-chain mutation
            com.shadowclangaming.debug.ShadowDebug.log("POSTCHAIN_SKIPPED",
                    "frame={} effect=blast reason=config_disabled fallback=HUD strength={}", frame, currentStrength);
            return;
        }

        String chain = resolve(best.style);
        current = chain;
        if (chain == null) {
            // No world shader: WorldBlastShader.tickFallback supplies in-world particles.
            return;
        }

        PostChain post = CHAINS.get(chain);
        if (post == null) {
            current = null;
            return;
        }
        RenderTarget main = minecraft.getMainRenderTarget();
        if (main.width != lastWidth || main.height != lastHeight) {
            post.resize(main.width, main.height);
            lastWidth = main.width;
            lastHeight = main.height;
        }
        float shaderTime = (System.currentTimeMillis() - startedAtMillis) / 1000.0F;
        post.setUniform("Strength", currentStrength);
        post.setUniform("Time", shaderTime);
        long processStart = System.nanoTime();
        com.shadowclangaming.debug.ShadowDebug.log("POSTCHAIN_PROCESS_BEGIN",
                "frame={} effect=blast shader={} strength={} time={} main={}x{}", frame, chain,
                currentStrength, shaderTime, main.width, main.height);
        try {
            post.process(partialTick);
            main.bindWrite(true);
            com.shadowclangaming.debug.ShadowDebug.log("POSTCHAIN_PROCESS_END",
                    "frame={} effect=blast shader={} result=success elapsedMicros={} mainWriteBound=true",
                    frame, chain, (System.nanoTime() - processStart) / 1000L);
        } catch (RuntimeException exception) {
            FAILED.add(chain);
            CHAINS.remove(chain);
            current = null;
            com.shadowclangaming.debug.ShadowDebug.logThrowable("POSTCHAIN_PROCESS_FAIL",
                    "frame=" + frame + " effect=blast shader=" + chain + " elapsedMicros="
                            + ((System.nanoTime() - processStart) / 1000L), exception);
            ShadowClanGaming.LOGGER.error("Blast shader '{}' failed at draw time; falling back to overlay", chain,
                    exception);
        }
    }

    /** The detonation that should own the screen right now, or null. */
    private static ClientBlastQueue.Entry strongest(Minecraft minecraft, long frame) {
        var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        ClientBlastQueue.Entry best = null;
        float bestStrength = 0.0F;
        var entries = ClientBlastQueue.active();
        com.shadowclangaming.debug.ShadowDebug.log("BLAST_RENDER_FRAME",
                "frame={} dimension={} camera=({}, {}, {}) entries={} chainsEnabled={}",
                frame, minecraft.level.dimension().location(), camera.x, camera.y, camera.z,
                entries.size(), SCGConfig.postChainsEnabled());
        for (ClientBlastQueue.Entry entry : entries) {
            float strength = strengthOf(entry, minecraft);
            double dx = entry.x-camera.x, dy = entry.y-camera.y, dz = entry.z-camera.z;
            double distance = Math.sqrt(dx*dx + dy*dy + dz*dz);
            double reach = (entry.radius + 96.0D) * SCGConfig.shaderDistance();
            com.shadowclangaming.debug.ShadowDebug.log("BLAST_CANDIDATE",
                    "frame={} style={} pos=({}, {}, {}) radius={} age={}/{} progress={} distance={} reach={} intensity={} strength={}",
                    frame, entry.style.id(), entry.x, entry.y, entry.z, entry.radius, entry.age,
                    entry.lifetime, entry.progress(), distance, reach, entry.intensity(), strength);
            if (strength > bestStrength) {
                bestStrength = strength;
                best = entry;
            }
        }
        return bestStrength <= 0.01F ? null : best;
    }

    /** 0..1 for one entry: distance falloff, the entry's own timeline, and the player's config. */
    public static float strengthOf(ClientBlastQueue.Entry entry, Minecraft minecraft) {
        var camera = minecraft.gameRenderer.getMainCamera().getPosition();
        double dx = entry.x - camera.x;
        double dy = entry.y - camera.y;
        double dz = entry.z - camera.z;
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double reach = (entry.radius + 96.0D) * SCGConfig.shaderDistance();
        if (distance > reach) {
            return 0.0F;
        }
        double near = 1.0D - Math.min(1.0D, distance / reach);
        double curve = entry.intensity();
        double value = near * near * curve * entry.style.flash() * SCGConfig.shaderStrength();
        return (float) Math.max(0.0D, Math.min(1.0D, value));
    }

    /**
     * Which chain to use for a style, trying (in order) the style's own, the fallback, then nothing.
     *
     * The result is cached per style in {@link #MODE} so this is a map lookup after the first detonation.
     */
    private static String resolve(BlastStyle style) {
        String own = style.shader();
        if (chain(own) != null) {
            MODE.put(style.id(), "own");
            return own;
        }
        if (SCGConfig.fallbackShaders()) {
            String fallback = BlastStyle.FALLBACK.shader();
            if (chain(fallback) != null) {
                MODE.put(style.id(), "fallback");
                return fallback;
            }
        }
        MODE.put(style.id(), "overlay");
        return null;
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

    /** Build (once) and return a chain, or null if it cannot be built. */
    private static PostChain chain(String name) {
        if (name == null || FAILED.contains(name)) {
            return null;
        }
        PostChain existing = CHAINS.get(name);
        if (existing != null) {
            return existing;
        }
        Minecraft minecraft = Minecraft.getInstance();
        com.shadowclangaming.debug.ShadowDebug.log("BLAST_SHADER_RESOURCES",
                "chain={} effectJson={} programJson={} vertex={} fragment={}", name,
                hasResource(minecraft, "post_effect/" + name + ".json"),
                hasResource(minecraft, "shaders/program/post/" + name + ".json"),
                hasResource(minecraft, "shaders/program/post/apex_common.vsh"),
                hasResource(minecraft, "shaders/program/post/" + name + ".fsh"));
        try {
            PostChain chain = new PostChain(
                    minecraft.getTextureManager(),
                    minecraft.getResourceManager(),
                    minecraft.getMainRenderTarget(),
                    ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID,
                            "post_effect/" + name + ".json"));
            CHAINS.put(name, chain);
            com.shadowclangaming.debug.ShadowDebug.log("BLAST_SHADER", "loaded chain={}", name);
            ShadowClanGaming.LOGGER.info("Blast shader '{}' loaded", name);
            return chain;
        } catch (Exception exception) {
            FAILED.add(name);
            com.shadowclangaming.debug.ShadowDebug.log("BLAST_SHADER_FAIL", "chain={} error={}",
                    name, exception.toString());
            ShadowClanGaming.LOGGER.error("Blast shader '{}' could not load; falling back to overlay", name,
                    exception);
            return null;
        }
    }

    // ------------------------------------------------------------------ diagnostics

    /** Chains that are alive. */
    public static List<String> loaded() {
        return new ArrayList<>(CHAINS.keySet());
    }

    /** Chains that refused to build on this machine. */
    public static List<String> failed() {
        return List.copyOf(FAILED);
    }

    /** Style id -> the path it is currently taking: own / fallback / overlay. */
    public static Map<String, String> modes() {
        return Map.copyOf(MODE);
    }

    /** Every chain name the roster can ask for. */
    public static List<String> declared() {
        Set<String> names = new LinkedHashSet<>();
        for (BlastStyle style : BlastStyle.values()) {
            names.add(style.shader());
        }
        return List.copyOf(names);
    }

    /** True when the HUD overlay is doing the work because no chain loaded. */
    public static boolean usingOverlay() {
        return current == null && currentStrength > 0.01F && SCGConfig.fallbackOverlay();
    }

    public static float overlayStrength() {
        return usingOverlay() ? currentStrength : 0.0F;
    }

    public static BlastStyle overlayStyle() {
        return currentStyle;
    }

    public static float overlayProgress() {
        return currentProgress;
    }

    /** How hard the current effect is pushing, whether or not a chain managed to draw it. */
    public static float strength() {
        return currentStrength;
    }

    /** Release GL resources on shutdown. */
    public static void close() {
        for (PostChain chain : CHAINS.values()) {
            chain.close();
        }
        CHAINS.clear();
        FAILED.clear();
        MODE.clear();
        current = null;
        currentStrength = 0.0F;
    }
}
