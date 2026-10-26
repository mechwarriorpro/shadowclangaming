package com.shadowclangaming.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * The mod's config files.
 *
 * TWO FILES, ON PURPOSE.
 *
 *   config/shadowclangaming-client.toml   what a single player sees: shaders, screen shake, particles.
 *   config/shadowclangaming-common.toml   what the world does: how far effect packets travel, whether the
 *                                         server sends them at all, and the blast worker count.
 *
 * The split matters because a client cannot decide "does the server broadcast effects" and a server cannot
 * decide "does this player want camera shake". Every value also has a sane default, so the files are
 * optional: a server owner who never opens them gets the intended experience.
 *
 * READING THEM. {@link #blastFxEnabled()} and friends are plain static getters so the hundreds of call sites
 * do not have to know about {@link ModConfigSpec}. They never throw: a config that failed to load, or one
 * being read before the spec is bound (the client's post-chain code can run very early), returns the default
 * rather than crashing a frame.
 */
public final class SCGConfig {

    // ------------------------------------------------------------------ client

    public static final ModConfigSpec CLIENT_SPEC;
    public static final ModConfigSpec.BooleanValue SHADERS_ENABLED;
    /** Legacy generic chains remain opt-in; the scene-aware shader uses its own dedicated chain. */
    public static final ModConfigSpec.BooleanValue POST_CHAINS_ENABLED;
    /** World-space explosive core shader; draws volumetric billboards in the level, never a HUD overlay. */
    public static final ModConfigSpec.BooleanValue CINEMATIC_SHADERS_ENABLED;
    public static final ModConfigSpec.DoubleValue SHADER_STRENGTH;
    public static final ModConfigSpec.DoubleValue SHADER_DISTANCE;
    public static final ModConfigSpec.BooleanValue FALLBACK_SHADERS;
    public static final ModConfigSpec.BooleanValue FALLBACK_OVERLAY;
    public static final ModConfigSpec.BooleanValue SCREEN_SHAKE;
    public static final ModConfigSpec.DoubleValue SHAKE_STRENGTH;
    public static final ModConfigSpec.BooleanValue FLASH_ENABLED;
    // ------------------------------------------------------------------ common

    public static final ModConfigSpec COMMON_SPEC;
    public static final ModConfigSpec.BooleanValue FX_PACKETS;
    public static final ModConfigSpec.IntValue FX_RANGE;
    public static final ModConfigSpec.IntValue BLAST_THREADS;
    public static final ModConfigSpec.DoubleValue PARTICLE_SCALE;

    static {
        ModConfigSpec.Builder client = new ModConfigSpec.Builder();
        client.comment("Visual settings for ShadowClanGaming. These only affect YOUR screen.")
                .push("screen_effects");

        SHADERS_ENABLED = client
                .comment("Enable styled screen effects for warhead detonations.")
                .define("shaders_enabled", true);

        POST_CHAINS_ENABLED = client
                .comment("Use legacy per-style PostChains. Usually leave false; the scene-aware renderer",
                        "below is the preferred pipeline and has a safe scene-preserving blend.")
                .define("post_chains_enabled", false);

        CINEMATIC_SHADERS_ENABLED = client
                .comment("Draw shader-rendered explosive fireballs and shock shells in world space.",
                        "The shader geometry is camera-facing but positioned at the actual detonation and depth-tested",
                        "against terrain. No GUI/HUD flash is used. Turn off to use in-world vanilla particle fallback.")
                .define("cinematic_shaders_enabled", true);

        SHADER_STRENGTH = client
                .comment("Global multiplier on how hard the shaders push (0.0 - 2.0).")
                .defineInRange("shader_strength", 1.0D, 0.0D, 2.0D);

        SHADER_DISTANCE = client
                .comment("Multiplier on how far away a blast can still tint your screen.",
                        "Higher = effects reach further, at the cost of a busier screen in a big battle.")
                .defineInRange("shader_distance", 1.0D, 0.25D, 4.0D);

        FALLBACK_SHADERS = client
                .comment("If a warhead's own shader will not load, try the generic 'blast_fallback' chain.",
                        "Leave this on: without it, one broken chain means one warhead with no effects.")
                .define("fallback_shaders", true);

        FALLBACK_OVERLAY = client
                .comment("Draw safe styled screen effects in the HUD when post chains are disabled or unavailable.",
                        "This path does not modify the world framebuffer and cannot black out the screen.")
                .define("fallback_overlay", true);

        FLASH_ENABLED = client
                .comment("The instant white-out on a detonation. Some players turn this off for migraines;",
                        "it is separate from shaders for exactly that reason.")
                .define("flash_enabled", true);

        SCREEN_SHAKE = client
                .comment("Shake the camera when a warhead goes off nearby.")
                .define("screen_shake", true);

        SHAKE_STRENGTH = client
                .comment("Camera shake multiplier (0.0 - 3.0).")
                .defineInRange("shake_strength", 1.0D, 0.0D, 3.0D);

        client.pop();
        CLIENT_SPEC = client.build();

        ModConfigSpec.Builder common = new ModConfigSpec.Builder();
        common.comment("Server-side behaviour. These affect the world, not one player's screen.")
                .push("effects");

        FX_PACKETS = common
                .comment("Send warhead effect packets to nearby clients.",
                        "Off means no shaders for anyone on this server (particles still spawn server-side,",
                        "because those are normal entities). Useful for a very busy server.")
                .define("send_effect_packets", true);

        FX_RANGE = common
                .comment("How far, in blocks, an effect packet reaches. Blasts larger than this still send",
                        "to everyone within the blast radius plus this margin.")
                .defineInRange("effect_range", 512, 64, 4096);

        BLAST_THREADS = common
                .comment("Worker threads for block damage (0 = let the mod choose from the CPU count).")
                .defineInRange("blast_threads", 0, 0, 64);

        PARTICLE_SCALE = common
                .comment("Multiplier on the particles the server spawns for explosions (0.0 - 3.0).",
                        "This lives here, not in the client file, because particles are spawned on the",
                        "server: a client cannot ask for fewer of them without a plugin channel.")
                .defineInRange("particle_scale", 1.0D, 0.0D, 3.0D);

        common.pop();
        COMMON_SPEC = common.build();
    }

    private SCGConfig() {
    }

    // ------------------------------------------------------------------ accessors
    //
    // Every accessor is defensive: `get()` throws if the spec has not been loaded yet, and the post-chain
    // code can legitimately run before the client config is bound (a detonation arriving during the first
    // frame of a world load). Defaults here match the defaults in the spec above, so early calls behave.

    private static <T> T or(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T read = value.get();
            return read == null ? fallback : read;
        } catch (RuntimeException notLoadedYet) {
            return fallback;
        }
    }

    public static boolean shadersEnabled() {
        return or(SHADERS_ENABLED, Boolean.TRUE);
    }

    public static boolean postChainsEnabled() {
        return shadersEnabled() && or(POST_CHAINS_ENABLED, Boolean.FALSE);
    }

    public static boolean cinematicShadersEnabled() {
        return shadersEnabled() && or(CINEMATIC_SHADERS_ENABLED, Boolean.TRUE);
    }

    public static double shaderStrength() {
        return or(SHADER_STRENGTH, 1.0D);
    }

    public static double shaderDistance() {
        return or(SHADER_DISTANCE, 1.0D);
    }

    public static boolean fallbackShaders() {
        return or(FALLBACK_SHADERS, Boolean.TRUE);
    }

    public static boolean fallbackOverlay() {
        return or(FALLBACK_OVERLAY, Boolean.TRUE);
    }

    public static boolean flashEnabled() {
        return or(FLASH_ENABLED, Boolean.TRUE);
    }

    public static boolean screenShake() {
        return or(SCREEN_SHAKE, Boolean.TRUE);
    }

    public static double shakeStrength() {
        return or(SHAKE_STRENGTH, 1.0D);
    }

    public static double particleScale() {
        return or(PARTICLE_SCALE, 1.0D);
    }

    /** Convenience for call sites that just want the combined multiplier. */
    public static double effectStrength() {
        return shadersEnabled() ? shaderStrength() : 0.0D;
    }

    public static boolean fxPackets() {
        return or(FX_PACKETS, Boolean.TRUE);
    }

    public static int fxRange() {
        return or(FX_RANGE, 512);
    }

    public static int blastThreads() {
        return or(BLAST_THREADS, 0);
    }
}
