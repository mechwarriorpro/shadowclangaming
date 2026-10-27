package com.shadowclangaming.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.config.SCGConfig;
import com.shadowclangaming.warhead.BlastMotif;
import net.minecraft.client.Minecraft;
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
    public static final double PROXY_BOX = 3.4D;

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
        return "running, " + frames + " frame(s) drawn, " + lastDrawn + " volume(s) last frame";
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
            Matrix4f view = WorldMatrices.view(event.getCamera());
            Matrix4f projection = WorldMatrices.projection();
            float time = (float) ((mc.level.getGameTime() % 72000L)
                    + event.getPartialTick().getGameTimeDeltaPartialTick(false)) / 20.0F;

            RenderSystem.setShader(() -> shader);
            for (CinematicFx.Effect effect : effects) {
                if (drawn >= MAX_VOLUMES) {
                    break;
                }
                if (effect.strength() <= 0.04F) {
                    continue;
                }
                double distance = Math.sqrt((effect.x() - cam.x) * (effect.x() - cam.x)
                        + (effect.y() - cam.y) * (effect.y() - cam.y)
                        + (effect.z() - cam.z) * (effect.z() - cam.z));
                setUniforms(shader, effect, cam, view, projection, time, distance);
                // Skybox FIRST, then the blast over it. Both are premultiplied and neither writes
                // depth, so drawing in this order puts the fireball in front of the sky it is
                // silhouetted against - the other way round the nebula would cover the explosion.
                BlastMotif.Volume vol = effect.motif().volume();
                // The shaft from orbit, first: it is the tallest thing drawn here and the blast sits at its
                // foot, so painting it before the others puts the fireball in front of the shaft feeding it
                // rather than the other way round. Center has to move up to the middle of the shaft's own
                // cube and be put back afterwards - the sky and blast passes both want the impact point.
                if (vol.shaft() > 0.01F) {
                    set(shader, "ShaftPass", 1.0F);
                    set(shader, "Shaft", vol.shaft());
                    set(shader, "Center", (float) effect.x(), (float) (effect.y() + SHAFT_HALF),
                            (float) effect.z());
                    drawProxy(effect.x(), effect.y() + SHAFT_HALF, effect.z(), SHAFT_HALF);
                    set(shader, "ShaftPass", 0.0F);
                    set(shader, "Shaft", 0.0F);
                    set(shader, "Center", (float) effect.x(), (float) effect.y(), (float) effect.z());
                }
                // The shader marches radius * SkyBox and the geometry covers skyHalf, so those two have
                // to be the same number or the march samples space the cube does not cover. SkyBox is
                // therefore derived from skyHalf and never floored or clamped afterwards - and if the
                // block cap leaves no room outside the blast's own cube, there is no sky to draw.
                double skyRadii = BlastMotif.skyBoxRadii(effect.radius(), SKY_BOX_RADII,
                        SKY_BOX_MAX_BLOCKS, PROXY_BOX);
                if (vol.sky() > 0.01F && skyRadii > 0.0D) {
                    double skyHalf = skyRadii * Math.max(1.0D, effect.radius());
                    set(shader, "SkyPass", 1.0F);
                    set(shader, "SkyBox", (float) skyRadii);
                    set(shader, "Steps", (float) Math.max(MIN_STEPS, BASE_STEPS - (int) (distance / 96.0D)));
                    drawProxy(effect.x(), effect.y(), effect.z(), skyHalf);
                    set(shader, "SkyPass", 0.0F);
                    set(shader, "Steps",
                            (float) Math.max(MIN_STEPS, BASE_STEPS - (int) (distance / 48.0D)));
                }
                drawProxy(effect.x(), effect.y(), effect.z(), effect.radius() * PROXY_BOX);
                drawn++;
            }
            lastDrawn = drawn;
            com.shadowclangaming.debug.ShadowDebug.log("VOLUME_FRAME",
                    "frame={} active={} drawn={} program=world_blast_volume", frame, effects.size(), drawn);
        } catch (Throwable ex) {
            // Not loadFailed: the program compiled and is still usable. Recording a draw error as a load
            // failure is what made fx status report a JSON problem that had already been fixed.
            drawFailed = true;
            drawFailure = describe(ex);
            ShadowClanGaming.LOGGER.error("Volumetric blast draw failed; falling back to geometry", ex);
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
        set(program, "Steps", (float) Math.max(MIN_STEPS, BASE_STEPS - (int) (distance / 48.0D)));
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
    }

    /** The proxy cube: six faces, two triangles each, in world coordinates. */
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
    // By name. This used to go through safeGetUniform precisely to avoid importing the uniform class -
    // "it has moved packages between versions and a wrong import is a build failure". That reasoning was
    // the bug: safeGetUniform's whole behaviour is to hide a missing uniform behind a DUMMY, so the one
    // thing worth knowing (that the program does not have this uniform) was unreachable, and setting it
    // threw instead. Naming com.mojang.blaze3d.shaders.Uniform costs one import and buys the distinction.

    /** Uniforms already reported missing, so a per-frame failure logs once instead of 300 times a second. */
    private static final java.util.Set<String> reportedMissing = new java.util.HashSet<>();

    /**
     * The named uniform, or null if the program does not have one.
     *
     * <p>{@code safeGetUniform} never returns null - when the name is absent it returns a shared DUMMY
     * built as {@code new Uniform("dummy", -1, 1, null)}, and type -1 allocates neither buffer. Calling
     * {@code set} on it throws {@code NullPointerException: ... because "this.floatValues" is null}, which
     * is what was killing every frame: the shader loaded fine, then the first {@code set(program, "Center",
     * ...)} threw, and the catch-all below recorded it as a load failure and pointed at the JSON.
     *
     * <p>So resolve through the nullable getter and say which uniform is missing. A shader with one absent
     * uniform should lose that uniform, not the entire effect.
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
