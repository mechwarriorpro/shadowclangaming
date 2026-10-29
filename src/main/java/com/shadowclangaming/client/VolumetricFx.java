package com.shadowclangaming.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexSorting;
import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.config.SCGConfig;
import com.shadowclangaming.warhead.BlastMotif;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.List;

/**
 * The volumetric half of a warhead effect: a procedural, raymarched, GPU-drawn blast.
 *
 * <p>WHAT IT IS. For every live effect this draws one cube the size of the blast around the detonation
 * point and hands the frame to {@code world_blast_volume.fsh}, which marches a domain-warped value-noise
 * field through that cube and composes twenty-two structural features - fireball, shockwave shells, black
 * hole, stellar corona, spacetime tear, meteors, lightning, ground runes, sky circles, portals, jet,
 * coins, smoke, sub-charges, flash, dome, embers - all computed per pixel from the warhead's own
 * authored parameters, so a World Eater is a hole with an accretion disc and a Numismatic blast is made
 * of tumbling gold. There are no textures, no sprites and
 * no billboards in this path - the shape is a function, evaluated on the GPU, so it holds up from any
 * angle, at any distance, and at any size.
 *
 * <p>WHY A CUBE. A volume has to be raymarched through something. The cube is the proxy: depth-tested
 * against the terrain (so a hill occludes it and a doorway frames it), drawn with no depth write and no
 * culling, and the fragment shader works out for itself whether the camera is outside it or standing
 * inside the fireball, then shades each pixel exactly once.
 *
 * <p>NO EVENT-BUS ANNOTATION, deliberately. This class has no listener methods: the shader is registered
 * from {@code WorldBlastShaderRegistration} and the frame is drawn from {@code BlastClientEvents}, which
 * decides between this and the triangle fallback. NeoForge refuses to register a class that carries
 * {@code @EventBusSubscriber} with nothing to subscribe - it fails the whole mod at construction - and
 * that is exactly the crash this class caused on its first launch.
 *
 * <p>FALLBACK. If the program does not compile on this machine, {@link #render} returns false and
 * {@link CinematicFx} draws the same effect as CPU triangles instead, with the particle pass running in
 * both cases. A driver that cannot run this shader still gets a warhead that looks like itself.
 */
public final class VolumetricFx {

    private static final ResourceLocation PROGRAM =
            ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "world_blast_volume");

    /** Half-extent of the proxy cube in blast radii. Must match PROXY_BOX in the fragment shader. */
    public static final double PROXY_BOX = 2.0D;

    /**
     * Half-extent of the SKYBOX cube, in blast radii, and the cap on it in blocks.
     *
     * <p>Much larger than the blast, because a sky has to reach the horizon: a grenade still gets a
     * ~380-block dome over it, and a kilometre-wide cataclysm would otherwise want one 28,000 blocks
     * across, which is past the render distance and past the point where float vertex positions are
     * still precise. Past the cap the sky is the same sky, so it is simply not drawn any bigger.
     */
    /** Half-height of the orbital shaft's volume, in blocks. The shaft runs twice this, impact to sky. */
    public static final double SHAFT_HALF = 170.0D;

    /** Shaft radius in blocks. Not scaled by the blast - see the shader. */
    public static final double SHAFT_WIDTH = 7.0D;

    public static final double SKY_BOX_RADII = 48.0D;
    // Was 3000, which quietly deleted the skybox from exactly the warheads that were supposed to have it.
    // skyBoxRadii() refuses to draw when the cap leaves the sky inside the blast's own cube, and that cube
    // is radius * PROXY_BOX - so with a 3000-block cap any blast wider than 3000 / 3.4 = 882 blocks got no
    // sky at all. Absolute Annihilation is 1237 blocks across. The bigger the weapon, the less sky it got,
    // which is backwards for the stellar tier. The field is evaluated from direction only, so a larger cube
    // costs nothing per pixel: the march still takes the same handful of steps.
    public static final double SKY_BOX_MAX_BLOCKS = 16000.0D;

    /** How many volumes may be marched in one frame. Each is a full-screen-ish raymarch; this is the cap. */
    public static final int MAX_VOLUMES = 4;

    private static final int BASE_STEPS = 44;
    private static final int MIN_STEPS = 16;
    /** The shader's own clamp is 224; going past it is wasted work the GPU throws away. */
    private static final int MAX_STEPS = 224;

    private static ShaderInstance shader;
    private static boolean loadFailed;
    /** Why the shader is unusable, for /scg fx status. Null while everything is fine. */
    private static String failureReason;
    private static boolean drawFailed;
    private static String drawFailure;
    private static long frames;
    private static int lastDrawn;

    private VolumetricFx() {
    }

    /** MOD-bus: hand the program to Minecraft to compile. Called from {@code WorldBlastShaderRegistration}. */
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), PROGRAM.toString(),
                    DefaultVertexFormat.POSITION), loaded -> {
                shader = loaded;
                loadFailed = false;
                ShadowClanGaming.LOGGER.info("Registered volumetric blast shader {}", PROGRAM);
            });
        } catch (IOException | RuntimeException ex) {
            loadFailed = true;
            shader = null;
            failureReason = describe(ex);
            ShadowClanGaming.LOGGER.error("Volumetric blast shader failed to register; using geometry", ex);
        }
    }

    /**
     * Everything worth knowing about a shader load failure, in something that fits in chat.
     *
     * <p>The first version of this stored {@code ex.getMessage()}, and the failure that mattered had no
     * message at all - so {@code fx status} said "shader failed to load: null", which is the one answer
     * guaranteed to be useless. Class names survive a null message, the cause chain survives a wrapped
     * one, and the top stack frames say which side threw. The full trace still goes to the log via
     * LOGGER.error; this is the part you can read without opening a file.
     */
    private static String describe(Throwable ex) {
        StringBuilder out = new StringBuilder();
        Throwable cursor = ex;
        for (int depth = 0; cursor != null && depth < 5; depth++) {
            if (out.length() > 0) {
                out.append("  <-  ");
            }
            out.append(cursor.getClass().getName());
            if (cursor.getMessage() != null) {
                out.append(": ").append(cursor.getMessage().replace('\n', ' '));
            }
            cursor = cursor.getCause() == cursor ? null : cursor.getCause();
        }
        StackTraceElement[] frames = ex.getStackTrace();
        for (int i = 0; i < Math.min(4, frames.length); i++) {
            out.append("\n      at ").append(frames[i]);
        }
        return out.toString();
    }

    /** True when the raymarcher is usable. */
    public static boolean available() {
        return shader != null && !loadFailed;
    }

    /**
     * One line for {@code /scg fx status}: is the raymarcher actually drawing, and if not, why.
     *
     * <p>This existed because the failure is silent in every other place a player would look. A shader that
     * does not compile does not fail the build - it fails at load, this class catches it, and the game draws
     * CPU triangles instead. The visible result is "the effects are transparent and all look the same",
     * which is indistinguishable from the shader merely looking bad, and the config lines that
     * {@code fx status} already printed said nothing either way.
     */
    public static String statusLine() {
        if (drawFailed) {
            return "NOT RUNNING - shader loaded but threw while drawing, so it was disabled: " + drawFailure
                    + "\n      (full trace in logs/latest.log under 'Volumetric blast draw failed')";
        }
        if (loadFailed) {
            return "NOT RUNNING - shader failed to load, drawing CPU triangles instead: " + failureReason
                    + "\n      (full trace in logs/latest.log under 'Volumetric blast shader failed to register')";
        }
        if (shader == null) {
            return "NOT RUNNING - shader never registered";
        }
        if (!SCGConfig.cinematicShadersEnabled()) {
            return "NOT RUNNING - cinematicShaders is off in config, drawing CPU triangles instead";
        }
        return "running, " + frames + " frame(s) drawn, " + lastDrawn + " volume(s) last frame"
                + "; " + probeLine() + "; " + solidLine() + "\n      " + scaleLine();
    }

    /**
     * Draw this frame's volumes.
     *
     * @return true if the volumetric pass handled the frame, false if the caller should draw triangles
     */
    public static boolean render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return false;
        }
        // Before the availability checks on purpose: the probe has to be drawable in exactly the states
        // where the volumetric pass is not.
        drawProbe(event, event.getCamera().getPosition());
        if (!available() || !SCGConfig.cinematicShadersEnabled()) {
            return false;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        List<CinematicFx.Effect> effects = CinematicFx.collect(mc);
        long frame = ++frames;
        if (effects.isEmpty()) {
            lastDrawn = 0;
            com.shadowclangaming.debug.ShadowDebug.log("VOLUME_FRAME", "frame={} active=0", frame);
            return true;
        }

        tickSolid();
        ShaderInstance previous = RenderSystem.getShader();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        // Premultiplied alpha: src.rgb is already emission-weighted by the march, so ONE/ONE_MINUS_SRC_ALPHA
        // is the correct composite. The old SRC_ALPHA/ONE was purely additive - it added the effect on top
        // of the framebuffer and never attenuated it, so the terrain showed through at full brightness no
        // matter how dense the volume was, which is exactly "the sphere is transparent".
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE,
                GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.disableCull();
        int drawn = 0;
        try {
            Vec3 cam = event.getCamera().getPosition();
            Matrix4f view = WorldMatrices.view(event, event.getCamera());
            Matrix4f projection = WorldMatrices.projection(event);
            float time = (float) ((mc.level.getGameTime() % 72000L)
                    + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0F;

            // Phase-ordered, not per effect. Drawing the shaft, sky and blast for one effect before
            // moving to the next let the SECOND effect's skybox - a cube up to 3000 blocks across, parked
            // on the far plane - composite on top of the first effect's already-drawn fireball. With up to
            // MAX_VOLUMES active at once that is the reported overlap. Every sky now goes down first as a
            // shared backdrop, then every shaft, then every blast, so no sky can cover a fireball and no
            // shaft can cover a blast.
            List<CinematicFx.Effect> active = new java.util.ArrayList<>();
            for (CinematicFx.Effect effect : effects) {
                if (active.size() >= MAX_VOLUMES) {
                    break;
                }
                if (effect.strength() <= 0.04F) {
                    continue;
                }
                active.add(effect);
            }

            RenderSystem.setShader(() -> shader);
            for (int pass = 0; pass < 3; pass++) {
                for (CinematicFx.Effect effect : active) {
                    double distance = Math.sqrt((effect.x() - cam.x) * (effect.x() - cam.x)
                            + (effect.y() - cam.y) * (effect.y() - cam.y)
                            + (effect.z() - cam.z) * (effect.z() - cam.z));
                    setUniforms(shader, effect, cam, view, projection, time, distance);
                    BlastMotif.Volume vol = effect.motif().volume();
                    // The shader marches radius * SkyBox and the geometry covers skyHalf, so those two
                    // have to be the same number or the march samples space the cube does not cover.
                    double skyRadii = BlastMotif.skyBoxRadii(effect.radius(), SKY_BOX_RADII,
                            SKY_BOX_MAX_BLOCKS, PROXY_BOX);

                    if (pass == 0) {
                        if (vol.sky() > 0.01F && skyRadii > 0.0D) {
                            double skyHalf = skyRadii * Math.max(1.0D, effect.radius());
                            set(shader, "SkyPass", 1.0F);
                            set(shader, "SkyBox", (float) skyRadii);
                            // The sky is smooth and its box is huge, so it wants a fraction of the steps.
                            set(shader, "Steps", (float) Math.max(MIN_STEPS, stepsFor(effect, distance) * 2 / 5));
                            drawProxy(effect.x(), effect.y(), effect.z(), skyHalf);
                        }
                    } else if (pass == 2) {
                        // The shaft from orbit, last so the growing fireball cannot occlude it. Center
                        // moves up to the middle of the shaft's own cube.
                        if (vol.shaft() > 0.01F) {
                            set(shader, "ShaftPass", 1.0F);
                            set(shader, "Shaft", vol.shaft());
                            set(shader, "Center", (float) effect.x(), (float) (effect.y() + SHAFT_HALF),
                                    (float) effect.z());
                            drawProxy(effect.x(), effect.y() + SHAFT_HALF, effect.z(), SHAFT_HALF);
                        }
                    } else if (pass == 1) {
                        set(shader, "ShaftPass", 0.0F);
                        set(shader, "Shaft", 0.0F);
                        set(shader, "SkyPass", 0.0F);
                        // Was left at the sky's step count by the branch above, so the scaling stepsFor
                        // does was never reaching the blast whenever a sky pass had run.
                        set(shader, "Steps", (float) stepsFor(effect, distance));
                        set(shader, "Center", (float) effect.x(), (float) effect.y(), (float) effect.z());
                        double half = effect.radius() * PROXY_BOX;
                        lastRadius = effect.radius();
                        lastBoxHalf = half;
                        lastSteps = stepsFor(effect, distance);
                        drawProxy(effect.x(), effect.y(), effect.z(), half);
                        drawn++;
                    }
                }
            }
            lastDrawn = drawn;
            com.shadowclangaming.debug.ShadowDebug.log("VOLUME_FRAME",
                    "frame={} active={} drawn={} program=world_blast_volume", frame, effects.size(), drawn);
        } catch (Throwable ex) {
            // Not loadFailed: the program compiled and is still usable. Recording a draw error as a load
            // failure is what made fx status report a JSON problem that had already been fixed.
            // Logged once: this runs on the render thread, so an unguarded log here fills the file
            // several times a second for as long as an effect is alive.
            if (!drawFailed) {
                drawFailure = describe(ex);
                ShadowClanGaming.LOGGER.error("Volumetric blast draw failed; falling back to geometry", ex);
            }
            drawFailed = true;
            com.shadowclangaming.debug.ShadowDebug.logThrowable("VOLUME_FAIL", "frame=" + frame, ex);
            return false;
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            if (previous != null) {
                RenderSystem.setShader(() -> previous);
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ one volume

    /**
     * Push one effect's parameters into the program.
     *
     * Looked up by name on every draw rather than cached; a couple of dozen map lookups a frame is nothing
     * next to a raymarch. See {@link #uniform} for why that goes through the nullable getter and not
     * {@code safeGetUniform}.
     */
    private static void setUniforms(ShaderInstance program, CinematicFx.Effect effect, Vec3 cam,
                                    Matrix4f view, Matrix4f projection, float time, double distance) {
        BlastMotif.Volume v = effect.motif().volume();
        set(program, "ScgView", view);
        set(program, "ScgProj", projection);
        set(program, "Center", (float) effect.x(), (float) effect.y(), (float) effect.z());
        set(program, "CamPos", (float) cam.x, (float) cam.y, (float) cam.z);
        set(program, "ColorA", v.colorA(), effect.strength());
        set(program, "ColorB", v.colorB(), effect.strength());
        set(program, "ColorC", v.colorC(), effect.strength());
        set(program, "Radius", (float) Math.max(1.0D, effect.radius()));
        set(program, "Progress", effect.progress());
        set(program, "Phase", effect.charging() ? 0.0F : 1.0F);
        set(program, "Time", time);
        // Fewer steps for distant volumes: the same march over a blast on the horizon is wasted work.
        set(program, "Steps", (float) stepsFor(effect, distance));

        // ------------------------------------------------ directional lighting (phase 1 experiment)
        //
        // ScgLighting 0.0 makes the shader run the ORIGINAL camera-relative arithmetic, so this block is
        // also the revert switch - the same compiled program either way, no shader reload. Everything here
        // is read from config on every draw, so a change in the config screen takes effect on the next
        // detonation without a restart.
        //
        // ScgLightDir is a TEST LIGHT, not Minecraft's sun. Level.getSunAngle() is getTimeOfDay()*2*PI, a
        // day fraction rather than a direction, and deriving the world-space sun from vanilla's sky
        // rotations put sunrise ninety degrees off at the zenith - unresolved without running the game, so
        // no sun is claimed. See SCGConfig.VOLUMETRIC_LIGHT_DIRECTION.
        set(program, "ScgLighting", SCGConfig.volumetricLightingMode());
        float[] light = SCGConfig.volumetricLightDirection();
        set(program, "ScgLightDir", light[0], light[1], light[2]);
        float[] lightColor = SCGConfig.volumetricLightColor();
        set(program, "ScgLightColor", lightColor[0], lightColor[1], lightColor[2]);
        set(program, "ScgLightStrength", SCGConfig.volumetricLightStrength());
        set(program, "ScgPhaseG", SCGConfig.volumetricPhaseG());
        set(program, "ScgShadowMode", SCGConfig.volumetricSelfShadow() ? 1.0F : 0.0F);
        set(program, "ScgSurfaceMode", SCGConfig.volumetricSeparateRim() ? 1.0F : 0.0F);
        set(program, "ScgRimStrength", SCGConfig.volumetricRimStrength());
        set(program, "ScgGlowBoost", SCGConfig.volumetricGlowBoost());

        // -------------------------------------------------- refinement (phase 2 experiment)
        //
        // Three corrections, each traced to a line in the shader rather than to the screenshot:
        //   ScgEmissionDetail - emission saw the density field only through a heat term clamped to [0,1],
        //                       which saturates across the inner core, so detailed density met flat
        //                       brightness. This folds the field's own sample back in, zero-mean.
        //   ScgSoftKnee/Knee  - min(acc, opacity) is a hard clamp that pins every channel above the ceiling
        //                       to the same number, turning a pale palette into (1,1,1). arcane needs only
        //                       bright >= 1.90 to get there and Lens reaches 3.4, so the core went white.
        //   ScgHaloFalloff    - bloom is one average over the whole march, so the halo it feeds is the same
        //                       strength everywhere and the outer transition reads as a shelf.
        // Every one degrades to the exact phase 1 image at its off value, and the JSON defaults are the off
        // values, so a uniform this method somehow fails to set cannot half-apply the experiment.
        set(program, "ScgEmissionDetail", SCGConfig.volumetricEmissionDetail());
        set(program, "ScgSoftKnee", SCGConfig.volumetricSoftKnee() ? 1.0F : 0.0F);
        set(program, "ScgKnee", SCGConfig.volumetricKnee());
        set(program, "ScgHaloFalloff", SCGConfig.volumetricHaloFalloff() ? 1.0F : 0.0F);

        // -------------------------------------------------- refinement (phase 3 experiment)
        //
        // Four corrections, again each traced to a line rather than to the screenshot:
        //   ScgSparkSoft      - `1 - smoothstep(0, 0.09, d)` is a flat-topped disc with a hard rim and one
        //                       scalar brightness for the whole cell, so the sparks read as sprites.
        //   ScgInteriorDetail - the fireball body is authored flat ("only the SURFACE noisy"), which is why
        //                       the dense interior reads as a single slab. Zero-mean, from the two fbm
        //                       samples the domain warp already computed, so it costs nothing.
        //   ScgWispExponent   - pow(x, 1.6) is a soft threshold, a 40x swing across the middle of the noise
        //                       range, which is why the outer wisps cut off instead of dissipating.
        //   ScgDither         - the nebula is a smooth gradient on an 8-bit target, so it bands.
        // JSON defaults are the phase 2 values, so an unset uniform cannot half-apply this.
        set(program, "ScgSparkSoft", SCGConfig.volumetricSparkSoft() ? 1.0F : 0.0F);
        set(program, "ScgInteriorDetail", SCGConfig.volumetricInteriorDetail());
        set(program, "ScgWispExponent", SCGConfig.volumetricWispExponent());
        set(program, "ScgDither", SCGConfig.volumetricDither() ? 1.0F : 0.0F);

        // ---------------------------------------------------------- tuning knobs
        // Numbers that were chosen without being able to see the result, exposed so they can be compared
        // in-game instead of needing a rebuilt jar per guess. Read every draw, so a change in the config
        // screen takes effect on the next detonation.
        set(program, "ScgSparkSigma", SCGConfig.volumetricSparkSigma());
        set(program, "ScgDitherAmount", SCGConfig.volumetricDitherAmount());

        set(program, "Beam", v.beam());
        set(program, "Ring", v.ring());
        set(program, "SkyRing", v.skyRing());
        set(program, "Implode", v.implode());
        set(program, "Turb", v.turb());
        set(program, "Rise", v.rise());
        set(program, "Streaks", v.streaks());
        set(program, "Dark", v.dark());
        set(program, "Glow", v.glow() * effect.strength());
        set(program, "Swirl", v.swirl());
        set(program, "Seed", v.seed());
        // The thirty-one structural features. Every one is authored per warhead in BlastMotif.Volume.author;
        // the shader sums whatever is non-zero, so this list is what turns one fireball into thirty-eight
        // different weapons.
        set(program, "Hole", v.hole());
        set(program, "Star", v.star());
        set(program, "Rift", v.rift());
        set(program, "Waves", v.waves());
        set(program, "Meteors", v.meteors());
        set(program, "Lightning", v.lightning());
        set(program, "Glyphs", v.glyphs());
        set(program, "Portals", v.portals());
        set(program, "Jet", v.jet());
        set(program, "Coins", v.coins());
        set(program, "Smoke", v.smoke());
        set(program, "SubCharges", v.subCharges());
        set(program, "Flash", v.flash());
        set(program, "Dome", v.dome());
        set(program, "Embers", v.embers());
        set(program, "Cap", v.cap());
        set(program, "Debris", v.debris());
        set(program, "Dust", v.dust());
        set(program, "Vortex", v.vortex());
        set(program, "Cracks", v.cracks());
        set(program, "Sparks", v.sparks());
        set(program, "Halo", v.halo());
        set(program, "Disc", v.disc());
        set(program, "Lens", v.lens());
        set(program, "Granule", v.granule());
        set(program, "Prominence", v.prominence());
        set(program, "Shell", v.shell());
        set(program, "Nebula", v.nebula());
        set(program, "Cone", v.cone());
        set(program, "Circle", v.circle());
        set(program, "Sky", v.sky());
        // Set per draw by render(), not here: the same program draws the skybox and the blast.
        set(program, "ShaftPass", 0.0F);
        set(program, "Shaft", 0.0F);
        set(program, "ShaftHalf", (float) SHAFT_HALF);
        set(program, "ShaftWidth", (float) SHAFT_WIDTH);
        set(program, "SkyPass", 0.0F);
        set(program, "SkyBox", (float) SKY_BOX_RADII);
        // Scales density as well as glow, so the volume thins out as the effect ends instead of turning
        // into an opaque black ball that sits there after the fire is gone.
        set(program, "Strength", Math.max(0.0F, Math.min(1.0F, effect.strength())));
        set(program, "Solid", solidFrames > 0 ? 1.0F : 0.0F);
    }

    /**
     * How many march steps a volume gets.
     *
     * <p>Scaled by the box it has to cross. It used to be a flat 44 less the camera distance, which is
     * fine for a ten-block grenade and useless for a blast whose proxy cube is thousands of blocks
     * across: 44 strides of several hundred blocks each land between the features and sample almost
     * nothing. The shader clamps to 160 either way.
     */
    private static int stepsFor(CinematicFx.Effect effect, double distance) {
        double boxHalf = Math.max(1.0D, effect.radius()) * PROXY_BOX;
        return (int) Math.max(MIN_STEPS,
                Math.min(MAX_STEPS, BASE_STEPS * boxHalf / 40.0D - distance / 48.0D));
    }

    /** Radius, proxy half-extent and step count of the last volume drawn; read by fx status. */
    public static String scaleLine() {
        if (lastRadius <= 0.0D) {
            return "no volume drawn yet";
        }
        return String.format(java.util.Locale.ROOT,
                "last volume radius %.0f, proxy half %.0f blocks (%.0f across), %d steps",
                lastRadius, lastBoxHalf, lastBoxHalf * 2.0D, lastSteps);
    }

    /** The proxy cube: six faces, two triangles each, in world coordinates. */
    /** Frames left on the probe cube; see {@link #startProbe}. */
    private static int probeFrames;
    /** Frames left with the march bypassed; see {@link #startSolid}. */
    private static int solidFrames;
    /** Probe cubes that actually reached a draw call, so "I saw nothing" can be told apart from "the
     *  command never ran". Reported by fx status. */
    private static int probeDrawn;
    /** RenderSystem's matrices as measured on the probe's first frame; see {@link #matrices}. */
    private static String probeMatrices = "not measured";
    private static double lastRadius;
    private static double lastBoxHalf;
    private static int lastSteps;

    /**
     * Draw a solid magenta cube through Minecraft's own position-colour shader for a few seconds.
     *
     * <p>The raymarcher now reports itself running, which rules out loading but not drawing: a wrong view
     * matrix, a depth-rejected cube, a culled cube and a cube projected off-screen all look identical from
     * outside the game - nothing appears, and the frame counter climbs anyway. This settles it in one look.
     * It uses vanilla's shader on the same coordinates {@link #drawProxy} uses, so if the magenta cube is
     * visible the geometry path is sound and the fault is inside the march; if it is not, the march is not
     * where to look.
     */
    public static void startProbe(int frames) {
        probeFrames = frames;
        probeDrawn = 0;
    }

    /**
     * Bypass the march and write a flat colour for a few seconds.
     *
     * <p>The probe cube proved the geometry path works, so what is left is this shader on its own. A flat
     * colour splits it: a magenta box where the blast should be means the program, matrices, blend and
     * depth are all fine and the field maths is producing nothing; still nothing means the program is not
     * drawing at all and the march is not where to look.
     */
    public static void startSolid(int frames) {
        solidFrames = frames;
    }

    /** Decremented per frame; the blast pass and the sky pass both need it off again afterwards. */
    static void tickSolid() {
        if (solidFrames > 0) {
            solidFrames--;
        }
    }

    /** Read by fx status. */
    public static String solidLine() {
        return solidFrames > 0 ? solidFrames + " frame(s) of solid output left" : "solid off";
    }

    /**
     * What RenderSystem is holding for the transform at this stage.
     *
     * <p>Every draw in this class - the raymarcher's proxy cube and the vanilla probe cube alike - is
     * positioned by these matrices, so if they are not the level's at AFTER_PARTICLES then both fail the
     * same way and nothing appears however correct the geometry is. A perspective projection has m32 == -1
     * and a non-zero m11; identity or a stale ortho matrix has neither, and that is the tell.
     */
    private static String matrices(RenderLevelStageEvent event) {
        Matrix4f proj = event.getProjectionMatrix();
        Matrix4f view = event.getModelViewMatrix();
        Matrix4f stale = RenderSystem.getProjectionMatrix();
        // joml's Matrix4f is column-major: m00..m03 is the first COLUMN, so a field named mCR is column C,
        // row R. The translation therefore lives in m30/m31/m32, and m03/m13/m23 are row 3 of the first
        // three columns - zero for any affine matrix, so an earlier version of this diagnostic that
        // printed them and demanded they be non-zero was checking a constant. The projection settles it:
        // m32 measured -0.1000, which is column 3 row 2, i.e. 2*near*far/(near-far) at 0.05/1000.
        Matrix4f used = view == null ? null : WorldMatrices.view(event, event.getCamera());
        return String.format(java.util.Locale.ROOT,
                "proj m32=%.4f (=2*near*far/(near-far) at 0.05/1000, correct)"
                        + " | view translation m30=%.2f m31=%.2f m32=%.2f (must be -camera)",
                proj == null ? 0.0F : proj.m32(),
                used == null ? 0.0F : used.m30(), used == null ? 0.0F : used.m31(),
                used == null ? 0.0F : used.m32());
    }

    /** Read by fx status: did the probe reach a draw call, or not? */
    public static String probeLine() {
        if (probeDrawn > 0) {
            return probeDrawn + " probe cube(s) drew; " + probeMatrices;
        }
        return probeFrames > 0
                ? "probe requested, " + probeFrames + " frame(s) left, none drew"
                : "no probe run";
    }

    private static void drawProbe(RenderLevelStageEvent event, Vec3 cam) {
        if (probeFrames <= 0) {
            return;
        }
        probeFrames--;
        double half = 10.0D;
        // Camera-relative, unlike drawProxy. RenderSystem's model-view at this stage is rotation only -
        // the camera offset lives on the pose stack, not in the matrix - and there is no setter for it,
        // so these vertices carry the translation themselves. Still centred two blocks above the player's
        // feet; it just gets there by subtraction instead of by matrix.
        double x = Math.floor(cam.x) + 0.5D - cam.x;
        double y = Math.floor(cam.y) + 2.0D - cam.y;
        double z = Math.floor(cam.z) + 0.5D - cam.z;
        double x0 = x - half, x1 = x + half, y0 = y - half, y1 = y + half, z0 = z - half, z1 = z + half;
        int argb = 0xFFFF00FF;
        // Deliberately permissive: depth off, cull off, blend off. The first version drew with whatever
        // state the level renderer happened to leave behind and drew nothing, which is ambiguous - depth
        // rejection, backface culling (the camera is INSIDE this cube) and a draw that never reaches the
        // framebuffer all look identical from out here. With all three disabled the only way this stays
        // invisible is if the draw is not landing on screen at all.
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableBlend();
        RenderSystem.disableCull();
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        colourQuad(buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0, argb);
        colourQuad(buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1, argb);
        colourQuad(buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1, argb);
        colourQuad(buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0, argb);
        colourQuad(buffer, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0, argb);
        colourQuad(buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1, argb);
        if (probeDrawn == 0) {
            probeMatrices = matrices(event);
            ShadowClanGaming.LOGGER.info("SCG probe matrices: {}", probeMatrices);
        }
        // Vanilla's shader reads the projection out of RenderSystem, so hand it the level's for the
        // duration of this one draw. This is the same correction the raymarcher gets from the event.
        Matrix4f savedProjection = RenderSystem.getProjectionMatrix();
        RenderSystem.setProjectionMatrix(event.getProjectionMatrix(), VertexSorting.DISTANCE_TO_ORIGIN);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        if (savedProjection != null) {
            RenderSystem.setProjectionMatrix(savedProjection, VertexSorting.DISTANCE_TO_ORIGIN);
        }
        probeDrawn++;
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    private static void colourQuad(BufferBuilder buffer,
                                   double ax, double ay, double az, double bx, double by, double bz,
                                   double cx, double cy, double cz, double dx, double dy, double dz,
                                   int argb) {
        buffer.addVertex((float) ax, (float) ay, (float) az).setColor(argb);
        buffer.addVertex((float) bx, (float) by, (float) bz).setColor(argb);
        buffer.addVertex((float) cx, (float) cy, (float) cz).setColor(argb);
        buffer.addVertex((float) dx, (float) dy, (float) dz).setColor(argb);
    }

    private static void drawProxy(double cx, double cy, double cz, double half) {
        float x0 = (float) (cx - half);
        float x1 = (float) (cx + half);
        float y0 = (float) (cy - half);
        float y1 = (float) (cy + half);
        float z0 = (float) (cz - half);
        float z1 = (float) (cz + half);
        BufferBuilder buffer = Tesselator.getInstance()
                .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        // -z, +z, -x, +x, -y, +y
        quad(buffer, x0, y0, z0, x1, y0, z0, x1, y1, z0, x0, y1, z0);
        quad(buffer, x1, y0, z1, x0, y0, z1, x0, y1, z1, x1, y1, z1);
        quad(buffer, x0, y0, z1, x0, y0, z0, x0, y1, z0, x0, y1, z1);
        quad(buffer, x1, y0, z0, x1, y0, z1, x1, y1, z1, x1, y1, z0);
        quad(buffer, x0, y0, z1, x1, y0, z1, x1, y0, z0, x0, y0, z0);
        quad(buffer, x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
    }

    private static void quad(BufferBuilder buffer,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        buffer.addVertex(ax, ay, az);
        buffer.addVertex(bx, by, bz);
        buffer.addVertex(cx, cy, cz);
        buffer.addVertex(dx, dy, dz);
    }

    // ------------------------------------------------------------------ uniform plumbing
    //
    // By name.
    //
    // uniformTypesAreMinecraftsOwn: Minecraft's Uniform.getTypeFromString (1.21.1) parses exactly three
    // spellings - "int", "float", and "matrix" ending in 2x2/3x3/4x4 - and returns -1 for anything else.
    // There is no "vec2", "vec3" or "vec4". The constructor then does
    //
    //     if (type <= 3) { intValues = memAllocInt(count); floatValues = null; }
    //     else           { intValues = null; floatValues = memAllocFloat(count); }
    //
    // and -1 <= 3, so a uniform declared as "vec3" gets an int buffer and floatValues is left explicitly
    // null. The first float set on it throws
    //     NullPointerException: ... because "this.floatValues" is null
    // from inside Uniform.set - not at load, at draw, every frame, with the shader otherwise perfectly
    // healthy. upload() compounds it by switching on type, so -1 uploads through uploadAsInteger.
    //
    // Vectors are spelled "float" with count N: vanilla's own ChunkOffset is float/3 and ColorModulator is
    // float/4, and uploadAsFloat switches on type 4 -> glUniform1(FloatBuffer), which writes the count
    // floats straight into the vec3's consecutive slots. tools/sweep.py now rejects any other spelling.

    /** Uniforms already reported missing, so a per-frame failure logs once instead of 300 times a second. */
    private static final java.util.Set<String> reportedMissing = new java.util.HashSet<>();

    /**
     * The named uniform, or null if the program does not have one.
     *
     * <p>Resolving through the nullable getter is defence in depth, not the fix: an earlier theory blamed
     * {@code safeGetUniform}'s DUMMY fallback, and a live stack trace disproved it by reaching this line at
     * all. The real cause was in the JSON, not here - see {@link #uniformTypesAreMinecraftsOwn}.
     */
    private static Uniform uniform(ShaderInstance program, String name) {
        Uniform found = program.getUniform(name);
        if (found == null && reportedMissing.add(name)) {
            ShadowClanGaming.LOGGER.error("Volumetric blast shader has no uniform named '{}'; it is declared "
                    + "in the JSON but absent from the linked program, so it cannot be set", name);
            com.shadowclangaming.debug.ShadowDebug.log("VOLUME_UNIFORM_MISSING", "name={}", name);
        }
        return found;
    }

    private static void set(ShaderInstance program, String name, float value) {
        Uniform target = uniform(program, name);
        if (target != null) {
            target.set(value);
        }
    }

    private static void set(ShaderInstance program, String name, float x, float y, float z) {
        Uniform target = uniform(program, name);
        if (target != null) {
            target.set(x, y, z);
        }
    }

    private static void set(ShaderInstance program, String name, Matrix4f matrix) {
        Uniform target = uniform(program, name);
        if (target != null) {
            target.set(matrix);
        }
    }

    /** Palette entry as a vec4, with the effect's own strength folded into the alpha. */
    private static void set(ShaderInstance program, String name, int argb, float strength) {
        float scale = 1.0F / 255.0F;
        Uniform target = uniform(program, name);
        if (target == null) {
            return;
        }
        target.set(((argb >>> 16) & 0xFF) * scale, ((argb >>> 8) & 0xFF) * scale,
                (argb & 0xFF) * scale, Math.max(0.0F, Math.min(1.0F, strength)));
    }
}
