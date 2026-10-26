package com.shadowclangaming.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.config.SCGConfig;
import com.shadowclangaming.warhead.BlastMotif;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The cinematic effect renderer: warhead geometry, in the world, attached to the detonation.
 *
 * <p>WHAT THIS REPLACES. The previous world renderer drew one camera-facing quad per detonation through a
 * fragment shader, so from any angle every warhead was the same small glowing disc with sparkles in it.
 * That was rejected, and correctly: it was a sprite, not an event. This class draws the geometry that
 * {@link BlastMotif} describes instead - shells that enclose the crater, rings lying on the ground, giant
 * circles hanging in the sky, pillars, arcs, tendrils - and it is depth-tested against the terrain, so it
 * is occluded by a hill and visible through a window, exactly like the blocks around it.
 *
 * <p>THREE LEVELS OF REDUNDANCY, because a visual that fails silently is how this ended up rejected once
 * already:
 * <ol>
 *   <li>this mod's own core shader ({@code shaders/core/world_blast_geom}), a two-line fragment shader
 *       that cannot fail to compile on a driver that runs Minecraft at all;</li>
 *   <li>vanilla's {@code rendertype_position_color} program, if the mod shader did not register;</li>
 *   <li>{@link #tickParticles}, world particles driven from the same motif, which need no shader at all.</li>
 * </ol>
 *
 * <p>GAME bus, {@code Dist.CLIENT}: a dedicated server never loads it.
 */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class CinematicFx {

    private static final ResourceLocation PROGRAM =
            ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "world_blast_geom");

    /** Effects drawn per frame. A carpet barrage must not become a geometry stress test. */
    public static final int MAX_EFFECTS = 6;
    /** Vertex budget per frame. A single effect is typically 2-6 thousand vertices. */
    public static final int MAX_VERTICES = 48_000;

    private static ShaderInstance shader;
    private static boolean loadFailed;
    private static long frames;

    private CinematicFx() {
    }

    /** MOD-bus registration, called from {@link WorldBlastShaderRegistration}. */
    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), PROGRAM.toString(),
                    DefaultVertexFormat.POSITION_COLOR), loaded -> {
                shader = loaded;
                loadFailed = false;
                ShadowClanGaming.LOGGER.info("Registered in-world cinematic geometry shader {}", PROGRAM);
            });
        } catch (IOException | RuntimeException ex) {
            loadFailed = true;
            ShadowClanGaming.LOGGER.error("Could not register {}; falling back to vanilla geometry shading",
                    PROGRAM, ex);
            com.shadowclangaming.debug.ShadowDebug.logThrowable("CINEMATIC_SHADER_REGISTER_FAIL",
                    PROGRAM.toString(), ex);
        }
    }

    public static boolean shaderLoaded() {
        return shader != null && !loadFailed;
    }

    public static boolean shaderFailed() {
        return loadFailed;
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            return;
        }
        if (!SCGConfig.cinematicShadersEnabled()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        List<Effect> effects = collect(mc);
        long frame = ++frames;
        com.shadowclangaming.debug.ShadowDebug.log("CINEMATIC_FRAME",
                "frame={} active={} shader={} failed={}", frame, effects.size(), shader != null, loadFailed);
        if (effects.isEmpty()) {
            return;
        }

        ShaderInstance active = shader != null ? shader : GameRenderer.getPositionColorShader();
        if (active == null) {
            return;
        }

        Vec3 cam = event.getCamera().getPosition();
        ShaderInstance previous = RenderSystem.getShader();
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // Same camera convention as the established world-FX path: translate the event pose by -camera and
        // submit world-space vertices, letting the active ModelView matrix apply the camera rotation once.
        poseStack.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f worldView = poseStack.last().pose();

        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        try {
            RenderSystem.setShader(() -> active);
            BufferBuilder buffer = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            CountingSink sink = new CountingSink(buffer, worldView);
            int vertices = 0;
            for (Effect effect : effects) {
                if (vertices >= MAX_VERTICES) {
                    break;
                }
                int before = sink.vertices;
                draw(sink, effect);
                vertices += sink.vertices - before;
            }
            if (sink.vertices > 0) {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            }
            com.shadowclangaming.debug.ShadowDebug.log("CINEMATIC_DRAW",
                    "frame={} effects={} vertices={} shader={}", frame, effects.size(), sink.vertices,
                    shader != null ? "mod" : "vanilla");
        } catch (Throwable ex) {
            loadFailed = true;
            ShadowClanGaming.LOGGER.error("Cinematic geometry draw failed; world particles remain", ex);
            com.shadowclangaming.debug.ShadowDebug.logThrowable("CINEMATIC_DRAW_FAIL", "frame=" + frame, ex);
        } finally {
            poseStack.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            if (previous != null) {
                RenderSystem.setShader(() -> previous);
            }
        }
    }

    /** One effect: every layer its motif asks for, at this point in its life. */
    private static void draw(CountingSink sink, Effect effect) {
        int seed = (int) (effect.seed ^ (effect.seed >>> 32)) ^ (int) (effect.progress * 40.0F);
        for (BlastMotif.Spec spec : effect.motif.specs()) {
            if (sink.vertices >= MAX_VERTICES) {
                return;
            }
            BlastGeometry.layer(sink, spec, effect.progress, effect.x, effect.y, effect.z,
                    effect.radius, tint(spec.argb(), effect.strength), seed);
        }
    }

    /** Fade a spec's colour by the effect's current strength, keeping its own alpha as the ceiling. */
    private static int tint(int argb, float strength) {
        int alpha = (int) (((argb >>> 24) & 0xFF) * Math.max(0.0F, Math.min(1.0F, strength)));
        return (argb & 0xFFFFFF) | (Math.max(0, Math.min(255, alpha)) << 24);
    }

    // ------------------------------------------------------------------ collecting

    /** The effects to draw this frame, strongest first, capped. */
    private static List<Effect> collect(Minecraft mc) {
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        List<Effect> out = new ArrayList<>();

        for (ClientBlastQueue.Entry entry : ClientBlastQueue.active()) {
            double distance = Math.sqrt(sq(entry.x - cam.x) + sq(entry.y - cam.y) + sq(entry.z - cam.z));
            double reach = (entry.radius + 120.0D) * SCGConfig.shaderDistance();
            if (distance > reach) {
                continue;
            }
            float proximity = (float) (1.0D - Math.min(1.0D, distance / reach));
            float strength = proximity * proximity * entry.intensity()
                    * (float) SCGConfig.shaderStrength();
            if (strength <= 0.03F) {
                continue;
            }
            BlastMotif.Motif motif = BlastMotif.forWarhead(entry.warheadId);
            out.add(new Effect(entry.x, entry.y, entry.z, Math.max(6, entry.radius),
                    entry.progress(), Math.min(1.0F, strength), motif,
                    (long) (entry.x * 31.0D + entry.z * 17.0D), 0));
        }

        for (ClientApexQueue.Entry entry : ClientApexQueue.active()) {
            double distance = Math.sqrt(sq(entry.x - cam.x) + sq(entry.y - cam.y) + sq(entry.z - cam.z));
            double reach = (entry.radius + 200.0D) * SCGConfig.shaderDistance();
            if (distance > reach) {
                continue;
            }
            float proximity = (float) (1.0D - Math.min(1.0D, distance / reach));
            float progress = Math.min(1.0F, entry.age / (float) Math.max(1, entry.lifetime));
            // A weapon that is re-announced with a longer life (the Soul Furnace, growing) must not run
            // its own animation backwards.
            progress = Math.max(progress, entry.visualProgress);
            entry.visualProgress = progress;
            // Hold full strength for three quarters of the weapon's life, then let go. A linear fade made
            // a ninety-second Soul Furnace invisible for most of its own run.
            float life = 1.0F - BlastGeometry.smooth((progress - 0.75F) / 0.25F);
            float strength = proximity * proximity * life * (float) SCGConfig.shaderStrength();
            if (strength <= 0.03F) {
                continue;
            }
            // Apex packets can put their centre below terrain (including y<0), where geometry is buried by
            // the depth buffer. Lift the *visual* to the local surface; the server's blast origin is
            // untouched.
            BlockPos origin = BlockPos.containing(entry.x, entry.y, entry.z);
            int surfaceY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    origin.getX(), origin.getZ());
            double visualY = Math.max(entry.y, surfaceY + 0.25D);
            BlastMotif.Motif motif = BlastMotif.forWarhead(entry.apex.id());
            out.add(new Effect(entry.x, visualY, entry.z, Math.max(8, entry.radius), progress,
                    Math.min(1.0F, strength), motif, (long) (entry.x * 31.0D + entry.z * 17.0D),
                    entry.age));
        }

        out.sort(Comparator.comparingDouble((Effect e) -> e.strength).reversed());
        if (out.size() > MAX_EFFECTS) {
            return new ArrayList<>(out.subList(0, MAX_EFFECTS));
        }
        return out;
    }

    // ------------------------------------------------------------------ world particles

    /**
     * The shader-free half of every effect: world particles driven from the same motif table.
     *
     * Not a fallback that only runs when something is broken - it always runs, budgeted by distance and by
     * the common particle scale. It is what a player sees if their driver refuses both shaders, and it is
     * what sells the effect at a distance where the geometry is a few pixels across.
     */
    public static void tickParticles(Minecraft mc) {
        if (mc.level == null || !SCGConfig.shadersEnabled()) {
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        double scale = SCGConfig.particleScale();
        if (scale <= 0.0D) {
            return;
        }

        for (ClientBlastQueue.Entry entry : ClientBlastQueue.active()) {
            if (entry.age > 200 || entry.age % 3 != 0) {
                continue;
            }
            if (!within(cam, entry.x, entry.y, entry.z, entry.radius + 240.0D)) {
                continue;
            }
            float progress = entry.progress();
            BlastMotif.Motif motif = BlastMotif.forWarhead(entry.apex.id());
            emit(mc, motif, entry.x, entry.y, entry.z, Math.max(6, entry.radius), progress, scale,
                    entry.age);
        }

        for (ClientApexQueue.Entry entry : ClientApexQueue.active()) {
            if (entry.age % 3 != 0) {
                continue;
            }
            BlockPos origin = BlockPos.containing(entry.x, entry.y, entry.z);
            int surfaceY = mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                    origin.getX(), origin.getZ());
            double visualY = Math.max(entry.y, surfaceY + 0.35D);
            if (!within(cam, entry.x, visualY, entry.z, entry.radius + 320.0D)) {
                continue;
            }
            float progress = Math.min(1.0F, entry.age / (float) Math.max(1, entry.lifetime));
            BlastMotif.Motif motif = BlastMotif.forWarhead(entry.apex.id());
            emit(mc, motif, entry.x, visualY, entry.z, Math.max(8, entry.radius), progress, scale,
                    entry.age);
        }
    }

    /** One burst of motif-appropriate world particles. */
    private static void emit(Minecraft mc, BlastMotif.Motif motif, double x, double y, double z,
                             double radius, float progress, double scale, int age) {
        double grow = 0.20D + 0.80D * progress;
        double shell = Math.max(2.0D, radius * grow * 0.55D);
        int count = (int) Math.max(6.0D, Math.min(48.0D, 26.0D * scale));
        long seed = (long) (x * 31.0D + z * 17.0D) + age;

        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double theta = Math.PI * 2.0D * t + age * 0.05D;
            double phi = Math.PI * (0.25D + 0.5D * BlastGeometry.hash01((int) (seed + i * 7)));
            double px = x + shell * Math.sin(phi) * Math.cos(theta);
            double py = y + shell * Math.cos(phi) * 0.8D;
            double pz = z + shell * Math.sin(phi) * Math.sin(theta);
            double vx = (px - x) * 0.02D;
            double vy = 0.03D + BlastGeometry.hash01((int) (seed + i)) * 0.06D;
            double vz = (pz - z) * 0.02D;
            mc.level.addParticle(motifParticle(motif.theme(), i + age), px, py, pz, vx, vy, vz);
        }

        // A ring on the ground, so the effect reads from above as well as from the side.
        int ringCount = (int) Math.max(8.0D, Math.min(40.0D, 20.0D * scale));
        double ringRadius = Math.max(2.0D, radius * grow);
        for (int i = 0; i < ringCount; i++) {
            double theta = Math.PI * 2.0D * i / ringCount + age * 0.02D;
            mc.level.addParticle(motifParticle(motif.theme(), i * 3 + age),
                    x + Math.cos(theta) * ringRadius, y + 0.25D, z + Math.sin(theta) * ringRadius,
                    Math.cos(theta) * 0.05D, 0.04D, Math.sin(theta) * 0.05D);
        }
    }

    /** Which vanilla particle carries a theme. Two per theme, alternated, so it does not look uniform. */
    private static net.minecraft.core.particles.ParticleOptions motifParticle(BlastMotif.Theme theme,
                                                                              int index) {
        boolean alt = (index & 1) == 0;
        return switch (theme) {
            case ORDNANCE -> alt ? ParticleTypes.FLAME : ParticleTypes.LARGE_SMOKE;
            case INFERNO -> alt ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME;
            case NUCLEAR -> alt ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.SOUL_FIRE_FLAME;
            case ARCANE -> alt ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD;
            case STELLAR -> alt ? ParticleTypes.END_ROD : ParticleTypes.FLASH;
            case CATACLYSM -> alt ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.LAVA;
            case VOID -> alt ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.PORTAL;
            case PLAGUE -> alt ? ParticleTypes.ITEM_SLIME : ParticleTypes.SNEEZE;
            case CRYO -> alt ? ParticleTypes.SNOWFLAKE : ParticleTypes.END_ROD;
            case MACHINE -> alt ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD;
            case COIN -> alt ? ParticleTypes.HAPPY_VILLAGER : ParticleTypes.END_ROD;
            case BARRIER -> alt ? ParticleTypes.END_ROD : ParticleTypes.SNOWFLAKE;
            case LOGISTICS -> alt ? ParticleTypes.CLOUD : ParticleTypes.END_ROD;
            case SWARM -> alt ? ParticleTypes.CLOUD : ParticleTypes.FLAME;
            case BEAST -> alt ? ParticleTypes.LARGE_SMOKE : ParticleTypes.SOUL_FIRE_FLAME;
            case SOUL -> alt ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.SOUL;
            case SILENT -> alt ? ParticleTypes.CLOUD : ParticleTypes.SMALL_FLAME;
        };
    }

    private static boolean within(Vec3 cam, double x, double y, double z, double reach) {
        double dx = x - cam.x, dy = y - cam.y, dz = z - cam.z;
        return dx * dx + dy * dy + dz * dz <= reach * reach;
    }

    private static double sq(double value) {
        return value * value;
    }

    /** One drawable effect. */
    private record Effect(double x, double y, double z, double radius, float progress, float strength,
                          BlastMotif.Motif motif, long seed, int age) {
    }

    /** A {@link BlastGeometry.Sink} that writes into a real vertex buffer and counts what it wrote. */
    private static final class CountingSink implements BlastGeometry.Sink {
        private final BufferBuilder buffer;
        private final Matrix4f matrix;
        private int vertices;

        CountingSink(BufferBuilder buffer, Matrix4f matrix) {
            this.buffer = buffer;
            this.matrix = matrix;
        }

        @Override
        public void vertex(float x, float y, float z, int r, int g, int b, int a) {
            buffer.addVertex(matrix, x, y, z).setColor(r, g, b, a);
            vertices++;
        }
    }
}
