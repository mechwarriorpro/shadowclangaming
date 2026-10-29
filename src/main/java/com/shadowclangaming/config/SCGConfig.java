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

    // ------------------------------------------------- volumetric lighting (phase 1 experiment)
    //
    // The raymarcher in world_blast_volume.fsh was, before phase 1, a PURELY EMISSIVE volume: the march
    // added `emission * a * transmittance` and used extinction only to attenuate itself, so nothing in the
    // image came from a light. These knobs switch in a single-scatter directional model. Every one of them
    // is read per draw, so mode 0 restores the exact pre-phase-1 arithmetic - that is the fallback.

    /** 0.0 = the original camera-relative lighting, 1.0 = directional single-scatter. */
    public static final ModConfigSpec.DoubleValue VOLUMETRIC_LIGHTING_MODE;
    /** Radiance of the test light. Not Minecraft's sun - see the note on volumetric_light_direction. */
    public static final ModConfigSpec.DoubleValue VOLUMETRIC_LIGHT_STRENGTH;
    /** Henyey-Greenstein asymmetry: 0 isotropic, >0 forward-scatter (smoke/fire), <0 back-scatter. */
    public static final ModConfigSpec.DoubleValue VOLUMETRIC_PHASE_G;
    /** Approximate self-shadowing by attenuating toward the light. Off = the light reaches everything. */
    public static final ModConfigSpec.BooleanValue VOLUMETRIC_SELF_SHADOW;
    /** Keep the gradient rim as its own additive boundary term instead of multiplying the whole ray. */
    public static final ModConfigSpec.BooleanValue VOLUMETRIC_SEPARATE_RIM;
    /** The uniform halo term, `bloom * X * (1 - opacity)`. 0.0 measures the model without it. */
    public static final ModConfigSpec.DoubleValue VOLUMETRIC_GLOW_BOOST;
    /** Test-light direction (world space). Explicitly NOT Minecraft's sun. */
    public static final ModConfigSpec.ConfigValue<String> VOLUMETRIC_LIGHT_DIRECTION;

    // ------------------------------------------------- volumetric refinement (phase 2 experiment)
    //
    // Three corrections, each traced to a specific line rather than to the screenshot, which the coding
    // model cannot see. All three default ON and all three are independently switchable back to the exact
    // phase 1 image.

    /** Emission modulation by the density field's own noise. 0.0 = flat phase 1 emission. */
    public static final ModConfigSpec.DoubleValue VOLUMETRIC_EMISSION_DETAIL;
    /** Replace the hard min(acc, opacity) clip with a hue-preserving soft knee. */
    public static final ModConfigSpec.BooleanValue VOLUMETRIC_SOFT_KNEE;
    /** Where the knee starts, as a fraction of the ceiling. Below it the image is unchanged. */
    public static final ModConfigSpec.DoubleValue VOLUMETRIC_KNEE;
    /** Make the halo fall off with distance from the blast instead of being a per-ray constant. */
    public static final ModConfigSpec.BooleanValue VOLUMETRIC_HALO_FALLOFF;

    // ------------------------------------------------- volumetric refinement (phase 3 experiment)

    /** Soften the sparks from a flat-topped disc to a Gaussian. */
    public static final ModConfigSpec.BooleanValue VOLUMETRIC_SPARK_SOFT;
    /** Internal density structure. Zero-mean, so the blast still occludes what is behind it. */
    public static final ModConfigSpec.DoubleValue VOLUMETRIC_INTERIOR_DETAIL;
    /** Exponent on the nebula's contrast curve. Lower = wisps dissipate instead of cutting off. */
    public static final ModConfigSpec.DoubleValue VOLUMETRIC_WISP_EXPONENT;
    /** Half-LSB output dither, to break up banding in smooth gradients like the nebula sky. */
    public static final ModConfigSpec.BooleanValue VOLUMETRIC_DITHER;
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

        VOLUMETRIC_LIGHTING_MODE = client
                .comment("Volumetric lighting model. 0.0 = the original camera-relative shading, exactly as",
                        "before phase 1. 1.0 = directional single-scatter with a phase function. Use 0.0 to",
                        "revert instantly if the new model looks wrong on your hardware.")
                .defineInRange("volumetric_lighting_mode", 1.0D, 0.0D, 1.0D);

        VOLUMETRIC_LIGHT_STRENGTH = client
                .comment("Radiance of the volumetric test light (0.0 - 4.0). The in-scattered term is added",
                        "to the volume's own emission, so 0.0 leaves the effect fully self-lit.")
                .defineInRange("volumetric_light_strength", 1.0D, 0.0D, 4.0D);

        VOLUMETRIC_PHASE_G = client
                .comment("Henyey-Greenstein asymmetry for the phase function. 0.0 = isotropic. Positive",
                        "scatters forward (fire, smoke seen against the light), negative back-scatters.")
                .defineInRange("volumetric_phase_g", 0.35D, -0.9D, 0.9D);

        VOLUMETRIC_SELF_SHADOW = client
                .comment("Attenuate the light by an estimate of the volume between the sample and the lit",
                        "side. This is a boundary-distance approximation, not a shadow map - the pipeline",
                        "binds no samplers at all.")
                .define("volumetric_self_shadow", true);

        VOLUMETRIC_SEPARATE_RIM = client
                .comment("Add the gradient rim as its own boundary contribution rather than multiplying the",
                        "whole accumulated ray by it. Applies only where the field has a real surface.")
                .define("volumetric_separate_rim", true);

        VOLUMETRIC_GLOW_BOOST = client
                .comment("Weight of the uniform halo term (0.0 - 1.0). Was hard-coded at 0.30. Set 0.0 to",
                        "measure how much of the flat, washed-out look comes from that term alone.")
                .defineInRange("volumetric_glow_boost", 0.3D, 0.0D, 1.0D);

        VOLUMETRIC_LIGHT_DIRECTION = client
                .comment("World-space direction the volumetric test light comes FROM, as 'x,y,z'.",
                        "THIS IS A TEST VECTOR, NOT MINECRAFT'S SUN. Deriving the real sun direction from",
                        "Level.getSunAngle() is unresolved (see docs/RENDERING_AUDIT.md) so no sun is",
                        "claimed here. The vector is normalised in the shader; a zero length is ignored.")
                .define("volumetric_light_direction", "0.35,0.86,-0.38");

        VOLUMETRIC_EMISSION_DETAIL = client
                .comment("How strongly the density field's own noise modulates emission (0.0 - 1.0).",
                        "The tendrils were flat because emission saw the field only through a clamped heat",
                        "term. This is ZERO-MEAN: it redistributes brightness locally and adds no energy,",
                        "and at 0.0 the emission is exactly what phase 1 produced.")
                .defineInRange("volumetric_emission_detail", 0.35D, 0.0D, 1.0D);

        VOLUMETRIC_SOFT_KNEE = client
                .comment("Compress bright cores with a hue-preserving knee instead of clipping them.",
                        "The old hard clamp pinned every channel above the ceiling to the same number, so",
                        "a pale palette went to (1,1,1) pure white and lost both its hue and its detail.",
                        "Turn off to restore the phase 1 hard clip.")
                .define("volumetric_soft_knee", true);

        VOLUMETRIC_KNEE = client
                .comment("Where the knee starts, as a fraction of the ceiling (0.05 - 0.95). Lower",
                        "compresses more of the range and holds more core detail at the cost of contrast;")
                .defineInRange("volumetric_knee", 0.75D, 0.05D, 0.95D);

        VOLUMETRIC_HALO_FALLOFF = client
                .comment("Let the emission halo fall off with the ray's distance from the blast.",
                        "The halo term is fed by one average over the whole march, so without this it is",
                        "the same strength everywhere and the outer transition reads as a shelf.")
                .define("volumetric_halo_falloff", true);

        VOLUMETRIC_SPARK_SOFT = client
                .comment("Draw the scattered sparks as soft glowing points instead of flat discs.",
                        "The old profile was exactly 1.0 across the whole interior and exactly 0.0 outside",
                        "a fixed radius, so each spark was a hard-edged disc with no internal gradient -",
                        "which is what made them read as superimposed sprites.")
                .define("volumetric_spark_soft", true);

        VOLUMETRIC_INTERIOR_DETAIL = client
                .comment("Internal density structure inside the fireball (0.0 - 1.0). The body was",
                        "authored flat so it would occlude what is behind it, which is also why the dense",
                        "interior read as one slab. This is ZERO-MEAN: mean density, and therefore the",
                        "occlusion, is unchanged. 0.0 restores the flat phase 2 interior.")
                .defineInRange("volumetric_interior_detail", 0.5D, 0.0D, 1.0D);

        VOLUMETRIC_WISP_EXPONENT = client
                .comment("Contrast exponent on the outer wisps (0.4 - 2.5). The old 1.6 was a soft",
                        "threshold - a 40x swing over the middle of the noise range - which is why the",
                        "wisps cut off sharply instead of dissipating. Lower is softer.")
                .defineInRange("volumetric_wisp_exponent", 1.15D, 0.4D, 2.5D);

        VOLUMETRIC_DITHER = client
                .comment("Dither the output by half an LSB. Smooth low-contrast gradients such as the",
                        "nebula sky otherwise band on an 8-bit framebuffer. The pattern is stable across",
                        "frames, so it does not flicker.")
                .define("volumetric_dither", true);

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

    /** 0.0 = the original lighting, 1.0 = the directional single-scatter model. */
    public static float volumetricLightingMode() {
        return (float) clamp(or(VOLUMETRIC_LIGHTING_MODE, 1.0D), 0.0D, 1.0D);
    }

    public static float volumetricLightStrength() {
        return (float) clamp(or(VOLUMETRIC_LIGHT_STRENGTH, 1.0D), 0.0D, 4.0D);
    }

    public static float volumetricPhaseG() {
        return (float) clamp(or(VOLUMETRIC_PHASE_G, 0.35D), -0.9D, 0.9D);
    }

    public static boolean volumetricSelfShadow() {
        return or(VOLUMETRIC_SELF_SHADOW, Boolean.TRUE);
    }

    public static boolean volumetricSeparateRim() {
        return or(VOLUMETRIC_SEPARATE_RIM, Boolean.TRUE);
    }

    public static float volumetricGlowBoost() {
        return (float) clamp(or(VOLUMETRIC_GLOW_BOOST, 0.3D), 0.0D, 1.0D);
    }

    /**
     * The test-light direction, as a unit vector pointing FROM the volume TOWARD the light.
     *
     * <p>Parsed defensively: a config value a player has hand-edited is untrusted text, and a NaN or a
     * zero-length vector reaching the shader would put NaNs on screen across the whole blast. Anything
     * unparsable falls back to the default rather than throwing during a draw.
     */
    public static float[] volumetricLightDirection() {
        float[] fallback = {0.35F, 0.86F, -0.38F};
        String raw = or(VOLUMETRIC_LIGHT_DIRECTION, "0.35,0.86,-0.38");
        if (raw == null) {
            return fallback;
        }
        String[] parts = raw.split(",");
        if (parts.length != 3) {
            return fallback;
        }
        float[] out = new float[3];
        for (int i = 0; i < 3; i++) {
            try {
                float value = Float.parseFloat(parts[i].trim());
                if (!Float.isFinite(value)) {
                    return fallback;
                }
                out[i] = value;
            } catch (RuntimeException unparsable) {
                return fallback;
            }
        }
        double length = Math.sqrt(out[0] * (double) out[0] + out[1] * (double) out[1] + out[2] * (double) out[2]);
        if (!(length > 1.0E-5D) || !Double.isFinite(length)) {
            return fallback;
        }
        out[0] /= (float) length;
        out[1] /= (float) length;
        out[2] /= (float) length;
        return out;
    }

    /** 0.0 = flat phase 1 emission; 1.0 = full modulation by the density field's own noise. */
    public static float volumetricEmissionDetail() {
        return (float) clamp(or(VOLUMETRIC_EMISSION_DETAIL, 0.35D), 0.0D, 1.0D);
    }

    public static boolean volumetricSoftKnee() {
        return or(VOLUMETRIC_SOFT_KNEE, Boolean.TRUE);
    }

    /**
     * Knee position as a fraction of the ceiling.
     *
     * <p>Held strictly inside (0, 1) at both ends: the shader's asymptote divides by {@code 1 - knee}, so a
     * value of 1.0 would be a division by zero and a NaN across the entire blast. The shader clamps too;
     * this is the half of the guard that keeps a hand-edited config from ever producing that value.
     */
    public static float volumetricKnee() {
        return (float) clamp(or(VOLUMETRIC_KNEE, 0.75D), 0.05D, 0.95D);
    }

    public static boolean volumetricHaloFalloff() {
        return or(VOLUMETRIC_HALO_FALLOFF, Boolean.TRUE);
    }

    public static boolean volumetricSparkSoft() {
        return or(VOLUMETRIC_SPARK_SOFT, Boolean.TRUE);
    }

    public static float volumetricInteriorDetail() {
        return (float) clamp(or(VOLUMETRIC_INTERIOR_DETAIL, 0.5D), 0.0D, 1.0D);
    }

    /**
     * Exponent on the nebula's contrast curve.
     *
     * <p>Held strictly above zero: the shader evaluates {@code pow(x, exponent)} and an exponent of 0
     * returns 1.0 everywhere - solid fog instead of wisps - while a negative one divides by zero at x = 0.
     */
    public static float volumetricWispExponent() {
        return (float) clamp(or(VOLUMETRIC_WISP_EXPONENT, 1.15D), 0.4D, 2.5D);
    }

    public static boolean volumetricDither() {
        return or(VOLUMETRIC_DITHER, Boolean.TRUE);
    }

    private static double clamp(double value, double min, double max) {
        if (Double.isNaN(value)) {
            return min;
        }
        return Math.max(min, Math.min(max, value));
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
