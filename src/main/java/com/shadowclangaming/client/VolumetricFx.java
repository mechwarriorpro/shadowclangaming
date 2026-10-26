package com.shadowclangaming.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.shader.Uniform;
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
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
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
 * field through that cube: fireball, shockwave ring, pillar of light, overhead circle, radial streaks,
 * all of it computed per pixel from the warhead's own parameters. There are no textures, no sprites and
 * no billboards in this path - the shape is a function, evaluated on the GPU, so it holds up from any
 * angle, at any distance, and at any size.
 *
 * <p>WHY A CUBE. A volume has to be raymarched through something. The cube is the proxy: depth-tested
 * against the terrain (so a hill occludes it and a doorway frames it), drawn with no depth write and no
 * culling, and the fragment shader works out for itself whether the camera is outside it or standing
 * inside the fireball, then shades each pixel exactly once.
 *
 * <p>FALLBACK. If the program does not compile on this machine, {@link #render} returns false and
 * {@link CinematicFx} draws the same effect as CPU triangles instead, with the particle pass running in
 * both cases. A driver that cannot run this shader still gets a warhead that looks like itself.
 */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class VolumetricFx {

    private static final ResourceLocation PROGRAM =
            ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "world_blast_volume");

    /** Half-extent of the proxy cube in blast radii. Must match PROXY_BOX in the fragment shader. */
    public static final double PROXY_BOX = 3.0D;

    /** How many volumes may be marched in one frame. Each is a full-screen-ish raymarch; this is the cap. */
    public static final int MAX_VOLUMES = 4;

    private static final int BASE_STEPS = 44;
    private static final int MIN_STEPS = 16;

    private static ShaderInstance shader;
    private static boolean loadFailed;
    private static long frames;

    /** Cached uniform handles, rebuilt whenever the program instance changes. */
    private static Uniforms uniforms;
    private static ShaderInstance uniformsFor;

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
            ShadowClanGaming.LOGGER.error("Volumetric blast shader failed to register; using geometry", ex);
        }
    }

    /** True when the raymarcher is usable. */
    public static boolean available() {
        return shader != null && !loadFailed;
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
            com.shadowclangaming.debug.ShadowDebug.log("VOLUME_FRAME", "frame={} active=0", frame);
            return true;
        }

        ShaderInstance previous = RenderSystem.getShader();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        int drawn = 0;
        try {
            Uniforms u = uniforms(shader);
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
                if (effect.strength <= 0.04F) {
                    continue;
                }
                double distance = Math.sqrt((effect.x - cam.x) * (effect.x - cam.x)
                        + (effect.y - cam.y) * (effect.y - cam.y)
                        + (effect.z - cam.z) * (effect.z - cam.z));
                setUniforms(u, effect, cam, view, projection, time, distance);
                drawProxy(effect.x, effect.y, effect.z, effect.radius * PROXY_BOX);
                drawn++;
            }
            com.shadowclangaming.debug.ShadowDebug.log("VOLUME_FRAME",
                    "frame={} active={} drawn={} program=world_blast_volume", frame, effects.size(), drawn);
        } catch (Throwable ex) {
            loadFailed = true;
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

    private static void setUniforms(Uniforms u, CinematicFx.Effect effect, Vec3 cam, Matrix4f view,
                                    Matrix4f projection, float time, double distance) {
        BlastMotif.Volume v = effect.motif.volume();
        set(u.scgView, view);
        set(u.scgProj, projection);
        set(u.center, (float) effect.x, (float) effect.y, (float) effect.z);
        set(u.camPos, (float) cam.x, (float) cam.y, (float) cam.z);
        set(u.colorA, v.colorA(), effect.strength);
        set(u.colorB, v.colorB(), effect.strength);
        set(u.colorC, v.colorC(), effect.strength);
        set(u.radius, (float) Math.max(1.0D, effect.radius));
        set(u.progress, effect.progress);
        set(u.phase, effect.charging() ? 0.0F : 1.0F);
        set(u.time, time);
        // Fewer steps for distant volumes: the same march over a blast on the horizon is wasted work.
        set(u.steps, (float) Math.max(MIN_STEPS, BASE_STEPS - (int) (distance / 48.0D)));
        set(u.beam, v.beam());
        set(u.ring, v.ring());
        set(u.skyRing, v.skyRing());
        set(u.implode, v.implode());
        set(u.turb, v.turb());
        set(u.rise, v.rise());
        set(u.streaks, v.streaks());
        set(u.dark, v.dark());
        set(u.glow, v.glow() * effect.strength);
        set(u.swirl, v.swirl());
        set(u.seed, v.seed());
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

    /** Uniform handles are looked up once per program instance; a missing one is simply never set. */
    private static Uniforms uniforms(ShaderInstance instance) {
        if (uniforms == null || uniformsFor != instance) {
            uniforms = new Uniforms(instance);
            uniformsFor = instance;
        }
        return uniforms;
    }

    private static void set(Uniform uniform, float value) {
        if (uniform != null) {
            uniform.set(value);
        }
    }

    private static void set(Uniform uniform, float x, float y, float z) {
        if (uniform != null) {
            uniform.set(x, y, z);
        }
    }

    private static void set(Uniform uniform, Matrix4f matrix) {
        if (uniform != null) {
            uniform.set(matrix);
        }
    }

    /** Palette entry as a vec4, with the effect's own strength folded into the alpha. */
    private static void set(Uniform uniform, int argb, float strength) {
        if (uniform == null) {
            return;
        }
        float scale = 1.0F / 255.0F;
        uniform.set(((argb >>> 16) & 0xFF) * scale, ((argb >>> 8) & 0xFF) * scale,
                (argb & 0xFF) * scale, Math.max(0.0F, Math.min(1.0F, strength)));
    }

    private static final class Uniforms {
        private final Uniform scgView;
        private final Uniform scgProj;
        private final Uniform center;
        private final Uniform camPos;
        private final Uniform colorA;
        private final Uniform colorB;
        private final Uniform colorC;
        private final Uniform radius;
        private final Uniform progress;
        private final Uniform phase;
        private final Uniform time;
        private final Uniform steps;
        private final Uniform beam;
        private final Uniform ring;
        private final Uniform skyRing;
        private final Uniform implode;
        private final Uniform turb;
        private final Uniform rise;
        private final Uniform streaks;
        private final Uniform dark;
        private final Uniform glow;
        private final Uniform swirl;
        private final Uniform seed;

        Uniforms(ShaderInstance instance) {
            scgView = instance.getUniform("ScgView");
            scgProj = instance.getUniform("ScgProj");
            center = instance.getUniform("Center");
            camPos = instance.getUniform("CamPos");
            colorA = instance.getUniform("ColorA");
            colorB = instance.getUniform("ColorB");
            colorC = instance.getUniform("ColorC");
            radius = instance.getUniform("Radius");
            progress = instance.getUniform("Progress");
            phase = instance.getUniform("Phase");
            time = instance.getUniform("Time");
            steps = instance.getUniform("Steps");
            beam = instance.getUniform("Beam");
            ring = instance.getUniform("Ring");
            skyRing = instance.getUniform("SkyRing");
            implode = instance.getUniform("Implode");
            turb = instance.getUniform("Turb");
            rise = instance.getUniform("Rise");
            streaks = instance.getUniform("Streaks");
            dark = instance.getUniform("Dark");
            glow = instance.getUniform("Glow");
            swirl = instance.getUniform("Swirl");
            seed = instance.getUniform("Seed");
        }
    }
}
