package com.shadowclangaming.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Post-processing for CBC Enhanced Shells exotic projectiles.
 * <p>
 * Original 5 shader shells from arginou57 (plasma/gravitational/vortex/disintegration/singularity)
 * × T1–T50. Each detonation triggers a full-screen post chain with intensity = tier/50 and duration 3–7s.
 * <p>
 * Chains are the same mechanism as ApexShaderFx (vanilla PostChain, no Iris required):
 *   post_effect/cbc_<type>.json → shaders/program/post/cbc_<type>.json → shaders/post/cbc_<type>.fsh
 * <p>
 * Triggered from server compat via client queue; if no CBC is present the queue is fed by warhead detonate
 * fallback. This class is client-only and mirrors ApexShaderFx's structure for failure safety.
 */
public final class CbcPostFx {
    private static final Map<String, PostChain> CHAINS = new HashMap<>();
    private static final Set<String> FAILED = new HashSet<>();
    private static String current;
    private static float currentStrength;
    private static long startedAtMillis;
    private static int lastWidth = -1, lastHeight = -1;

    // Simple queue entry — server pushes via packet or direct client tick
    public record Entry(String shader, float intensity, long startedAt, long durationMillis) {
        float strengthAt(long now) {
            long age = now - startedAt;
            if (age < 0 || age > durationMillis) return 0f;
            // fade in 200ms, sustain, fade out 400ms
            if (age < 200) return intensity * (age/200f);
            if (age > durationMillis - 400) return intensity * ((durationMillis - age)/400f);
            return intensity;
        }
    }
    private static final java.util.List<Entry> ACTIVE = new java.util.ArrayList<>();

    private CbcPostFx() {}

    public static void push(String shader, float intensity, long durationMillis) {
        if (FAILED.contains(shader)) return;
        synchronized (ACTIVE) {
            ACTIVE.add(new Entry(shader, intensity, System.currentTimeMillis(), durationMillis));
        }
        // Cap at 4 overlapping — strongest wins is handled in render()
        ShadowClanGaming.LOGGER.info("CBC PostFx queued {} intensity={} duration={}ms", shader, intensity, durationMillis);
    }

    /** Convenience from server compat — maps tier to intensity/duration. */
    public static void push(com.shadowclangaming.compat.cbc.CbcShellType type, com.shadowclangaming.compat.cbc.CbcShellTier tier) {
        if (!type.hasShader()) return;
        float intensity = tier.tier() / 50f;
        long duration = 3000L + tier.tier() * 80L;
        push(type.shaderId(), intensity, duration);
    }

    public static boolean active() { return current != null && currentStrength > 0.01f; }

    public static void render(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { current = null; return; }
        long now = System.currentTimeMillis();
        String best = null;
        float bestStrength = 0f;
        synchronized (ACTIVE) {
            ACTIVE.removeIf(e -> now - e.startedAt > e.durationMillis);
            for (Entry e : ACTIVE) {
                float s = e.strengthAt(now);
                if (s > bestStrength) { bestStrength = s; best = e.shader; }
            }
        }
        current = best;
        currentStrength = bestStrength;
        if (best == null || bestStrength <= 0.01f) return;
        PostChain chain = chain(best);
        if (chain == null) { current = null; return; }
        RenderTarget main = mc.getMainRenderTarget();
        if (main.width != lastWidth || main.height != lastHeight) {
            chain.resize(main.width, main.height);
            lastWidth = main.width; lastHeight = main.height;
        }
        chain.setUniform("Strength", bestStrength);
        chain.setUniform("Time", (now - startedAtMillis)/1000f);
        try {
            chain.process(partialTick);
            main.bindWrite(true);
        } catch (RuntimeException ex) {
            FAILED.add(best);
            CHAINS.remove(best);
            current = null;
            ShadowClanGaming.LOGGER.warn("CBC shader '{}' failed at draw time; disabled", best, ex);
        }
    }

    private static PostChain chain(String name) {
        if (CHAINS.containsKey(name)) return CHAINS.get(name);
        if (FAILED.contains(name)) return null;
        try {
            Minecraft mc = Minecraft.getInstance();
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "post_effect/" + name);
            // PostChain loading is via ResourceLocation; this matches ApexShaderFx pattern
            PostChain chain = new PostChain(mc.getTextureManager(), mc.getResourceManager(), mainTarget(), id);
            chain.resize(mc.getMainRenderTarget().width, mc.getMainRenderTarget().height);
            CHAINS.put(name, chain);
            startedAtMillis = System.currentTimeMillis();
            return chain;
        } catch (Throwable t) {
            FAILED.add(name);
            ShadowClanGaming.LOGGER.warn("CBC shader '{}' failed to load; safe fallback", name, t);
            return null;
        }
    }

    private static RenderTarget mainTarget() {
        return Minecraft.getInstance().getMainRenderTarget();
    }

    public static String status() {
        if (current == null) return "CBC idle (" + ACTIVE.size() + " queued, " + FAILED.size() + " failed)";
        return "CBC active: " + current + " strength=" + String.format(java.util.Locale.ROOT,"%.2f", currentStrength);
    }
}
