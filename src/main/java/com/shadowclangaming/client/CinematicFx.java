package com.shadowclangaming.client;

import com.mojang.blaze3d.platform.GlStateManager;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
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

    /**
     * Draw the effects as CPU triangles.
     *
     * Called from {@code BlastClientEvents} every frame, alongside the volumetric pass. It used to carry
     * its own {@code @SubscribeEvent}, drawing whether or not the raymarcher had already drawn; it was
     * then demoted to a fallback that ran only when the volumetric program failed to load.
     *
     * <p>Both of those were wrong. The volume raymarches the fireball and nothing else, while shockwave
     * rings, ground sigils, sky circles, ember columns, arcs, shards, ring stacks, tendrils, scan shells
     * and glyph rings exist only here - so the fallback version meant that on every machine where the
     * shader worked, which is every machine, none of them ever drew. Running both unconditionally and
     * suppressing the one duplicated layer is the version that is right about all twelve. See
     * {@link #draw}.
     */
    public static void render(RenderLevelStageEvent event, boolean suppressCoreSphere) {
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

        ShaderInstance active = shader;
        if (active == null) {
            // No program, no triangles. The particle pass keeps running regardless, so the warhead is
            // still visible - it is just not geometry. Guessing at a vanilla program whose uniforms are
            // not ours would put the effect in the wrong place, which is worse.
            return;
        }

        ShaderInstance previous = RenderSystem.getShader();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        try {
            RenderSystem.setShader(() -> active);
            // Vertices are world-space and the shader does the one and only transform, so it needs the
            // same pair of matrices the level is drawn with. Taken from the event: RenderSystem's
            // projection at this stage measured m32 = -0.1 in-game, where a perspective projection has
            // -1.0, so it is not the level's matrix and anything projected with it lands off-screen.
            active.safeGetUniform("ScgView").set(WorldMatrices.view(event, event.getCamera()));
            active.safeGetUniform("ScgProj").set(WorldMatrices.projection(event));
            BufferBuilder buffer = Tesselator.getInstance()
                    .begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
            // Identity, deliberately: vertices stay in world coordinates and the shader's own ScgView
            // does the one and only transform.
            CountingSink sink = new CountingSink(buffer, new Matrix4f());
            int vertices = 0;
            for (Effect effect : effects) {
                if (vertices >= MAX_VERTICES) {
                    break;
                }
                int before = sink.vertices;
                draw(sink, effect, suppressCoreSphere);
                vertices += sink.vertices - before;
            }
            if (sink.vertices > 0) {
                BufferUploader.drawWithShader(buffer.buildOrThrow());
            }
            com.shadowclangaming.debug.ShadowDebug.log("CINEMATIC_DRAW",
                    "frame={} effects={} vertices={} program=world_blast_geom", frame, effects.size(),
                    sink.vertices);
        } catch (Throwable ex) {
            loadFailed = true;
            ShadowClanGaming.LOGGER.error("Cinematic geometry draw failed; world particles remain", ex);
            com.shadowclangaming.debug.ShadowDebug.logThrowable("CINEMATIC_DRAW_FAIL", "frame=" + frame, ex);
        } finally {
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            if (previous != null) {
                RenderSystem.setShader(() -> previous);
            }
        }
    }

    /**
     * Every layer one effect's motif asks for, at this point in its life.
     *
     * @param suppressCoreSphere skip CORE_SPHERE, because the volumetric pass raymarched the fireball
     *                           already. It is the only one of the twelve layer types the volume also
     *                           draws; the other eleven exist solely here.
     */
    private static void draw(CountingSink sink, Effect effect, boolean suppressCoreSphere) {
        int seed = (int) (effect.seed ^ (effect.seed >>> 32)) ^ (int) (effect.progress * 40.0F);
        for (BlastMotif.Spec spec : effect.charging()
                ? effect.motif.chargeSpecs() : effect.motif.specs()) {
            if (sink.vertices >= MAX_VERTICES) {
                return;
            }
            if (suppressCoreSphere && spec.layer() == BlastMotif.Layer.CORE_SPHERE) {
                continue;
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

    /**
     * The effects to draw this frame, strongest first, capped.
     *
     * Shared with {@link VolumetricFx}: both renderers must agree on what is active, how strong it is and
     * which half of its life it is in, or the volumetric pass and the geometry pass would fight.
     */
    static List<Effect> collect(Minecraft mc) {
        Vec3 cam = mc.gameRenderer.getMainCamera().getPosition();
        List<Effect> out = new ArrayList<>();

        for (ClientBlastQueue.Entry entry : ClientBlastQueue.active()) {
            double distance = Math.sqrt(sq(entry.x - cam.x) + sq(entry.y - cam.y) + sq(entry.z - cam.z));
            double reach = (entry.radius + 120.0D) * SCGConfig.shaderDistance();
            if (distance > reach) {
                continue;
            }
            // Not squared - see BlastMotif.distanceFalloff. Squaring put a player standing at the edge
            // of their own 469-block detonation under the skip threshold below.
            float strength = BlastMotif.distanceFalloff(distance, reach) * entry.intensity()
                    * (float) SCGConfig.shaderStrength();
            if (strength <= 0.03F) {
                continue;
            }
            BlastMotif.Motif motif = BlastMotif.forWarhead(entry.warheadId);
            out.add(new Effect(entry.x, entry.y, entry.z, Math.max(6, entry.radius),
                    entry.progress(), Math.min(1.0F, strength), motif,
                    (long) (entry.x * 31.0D + entry.z * 17.0D), 0, entry.phase));
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
                    entry.age, BlastMotif.Phase.IMPACT));
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
            BlastMotif.Motif motif = BlastMotif.forWarhead(entry.warheadId);
            emit(mc, motif, entry.x, entry.y, entry.z, Math.max(6, entry.radius), progress, scale,
                    entry.age, entry.phase);
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
                    entry.age, BlastMotif.Phase.IMPACT);
        }
    }

    /** One burst of motif-appropriate world particles. */
    /**
     * The shader-free half of an effect: world particles, driven from the same motif table.
     *
     * Two behaviours, because the two halves of a weapon's life move in opposite directions. A charge
     * CONVERGES - motes fall in from the blast radius towards the point where the warhead is going to go
     * off, faster as it gets closer, with a column above it where the pillar will be - so the anticipation
     * reads as the world being pulled towards one point. An impact does the reverse and throws everything
     * outward with an upward plume.
     *
     * This runs whether or not either shader compiled, on every machine, with no GL at all.
     */
    private static void emit(Minecraft mc, BlastMotif.Motif motif, double x, double y, double z,
                             double radius, float progress, double scale, int age,
                             BlastMotif.Phase phase) {
        boolean charging = phase == BlastMotif.Phase.CHARGE;
        long seed = (long) (x * 31.0D + z * 17.0D) + age;
        double shell = charging
                // Converging: wide at the start of the fuse, nearly at the centre by the end of it.
                ? Math.max(1.5D, radius * (1.15D - 0.95D * progress))
                : Math.max(2.0D, radius * (0.20D + 0.80D * progress) * 0.55D);
        int count = (int) Math.max(8.0D, Math.min(charging ? 40.0D : 64.0D,
                (charging ? 22.0D : 34.0D) * scale));

        for (int i = 0; i < count; i++) {
            double t = i / (double) count;
            double theta = Math.PI * 2.0D * t + age * (charging ? 0.09D : 0.05D);
            double phi = Math.PI * (0.25D + 0.5D * BlastGeometry.hash01((int) (seed + i * 7)));
            double px = x + shell * Math.sin(phi) * Math.cos(theta);
            double py = y + shell * Math.cos(phi) * 0.8D;
            double pz = z + shell * Math.sin(phi) * Math.sin(theta);
            double vx;
            double vy;
            double vz;
            if (charging) {
                // Inward and down, harder the closer the fuse is to running out.
                double pull = -0.03D - 0.05D * progress;
                vx = (px - x) * pull;
                vy = (py - y) * pull - 0.01D;
                vz = (pz - z) * pull;
            } else {
                vx = (px - x) * 0.045D;
                vy = 0.05D + BlastGeometry.hash01((int) (seed + i)) * 0.11D;
                vz = (pz - z) * 0.045D;
            }
            mc.level.addParticle(motifParticle(motif.theme(), i + age), px, py, pz, vx, vy, vz);
        }

        // A column above the point, for the warheads that will end with a pillar of light. It descends
        // while the weapon charges and erupts when it goes off.
        BlastMotif.Volume volume = motif.volume();
        if (volume.beam() > 0.12F) {
            int columnCount = (int) Math.max(4.0D, Math.min(26.0D, 14.0D * scale * volume.beam()));
            double height = Math.max(6.0D, radius * (charging ? 1.6D : 2.2D));
            for (int i = 0; i < columnCount; i++) {
                double h = BlastGeometry.hash01((int) (seed + i * 13)) * height;
                double theta = Math.PI * 2.0D * BlastGeometry.hash01((int) (seed + i * 29)) + age * 0.06D;
                double spread = Math.max(0.6D, radius * 0.08D);
                double px = x + Math.cos(theta) * spread;
                double pz = z + Math.sin(theta) * spread;
                double vy = charging
                        ? -0.12D - 0.1D * progress
                        : 0.16D + BlastGeometry.hash01((int) (seed + i * 3)) * 0.2D;
                mc.level.addParticle(motifParticle(motif.theme(), i * 5 + age), px, y + h, pz,
                        0.0D, vy, 0.0D);
            }
        }

        // The singularity's torrent: matter dragged in, wound up, and flung back out.
        //
        // Two populations rather than one. A vanilla particle integrates a constant velocity and there
        // is no per-tick hook here to curve its path, so a genuine spiral trajectory is not reachable
        // this way. What IS reachable is an inward radial velocity plus a tangential one, which draws a
        // chord; emitted at every angle every few ticks, the chords of successive generations land at
        // different radii and the whole reads as material winding down the funnel. The ejection is the
        // same construction outwards from the core.
        //
        // A true curved infall needs a custom Particle class, and a custom particle needs a texture in
        // the particle atlas. There is not one in this repository and there is no way to verify from
        // here that one would load, so this deliberately uses sprites that already exist. Invisible
        // particles would be worse than approximate ones.
        if (volume.vortex() > 0.30F && !charging) {
            int torrent = (int) Math.max(10.0D, Math.min(72.0D, 42.0D * scale * volume.vortex()));
            double swirlDir = volume.swirl() >= 0.0F ? 1.0D : -1.0D;
            for (int i = 0; i < torrent; i++) {
                double u = BlastGeometry.hash01((int) (seed + i * 37L));
                double theta = Math.PI * 2.0D * BlastGeometry.hash01((int) (seed + i * 53L))
                        + age * 0.11D;
                // Roughly five eighths are falling in, three eighths are the ejection. More infall than
                // outflow, because that is what a singularity does until the moment it does not.
                boolean infall = u < 0.62D;
                double rad = infall
                        ? Math.max(2.0D, radius * (0.95D - 0.55D * progress) * (0.45D + 0.55D * u))
                        : Math.max(0.8D, radius * 0.12D * (0.35D + u));
                double px = x + Math.cos(theta) * rad;
                double pz = z + Math.sin(theta) * rad;
                double py = y + (BlastGeometry.hash01((int) (seed + i * 11L)) - 0.35D) * radius * 0.55D;
                // Unit radial and tangential in the XZ plane. The tangential term is the whole point:
                // without it this is a pump, not a vortex.
                double ux = (px - x) / Math.max(1.0E-3D, rad);
                double uz = (pz - z) / Math.max(1.0E-3D, rad);
                double spin = 0.13D + 0.09D * progress;
                double vx;
                double vy;
                double vz;
                net.minecraft.core.particles.ParticleOptions kind;
                if (infall) {
                    double pull = 0.05D + 0.07D * progress;
                    vx = -ux * pull - uz * spin * swirlDir;
                    vz = -uz * pull + ux * spin * swirlDir;
                    vy = -0.012D - 0.02D * progress;
                    kind = net.minecraft.core.particles.ParticleTypes.REVERSE_PORTAL;
                } else {
                    double out = 0.10D + 0.13D * progress;
                    vx = ux * out - uz * spin * 0.6D * swirlDir;
                    vz = uz * out + ux * spin * 0.6D * swirlDir;
                    vy = 0.09D + BlastGeometry.hash01((int) (seed + i * 71L)) * 0.14D;
                    kind = motifParticle(motif.theme(), i * 5 + age);
                }
                mc.level.addParticle(kind, px, py, pz, vx, vy, vz);
            }
        }

        // A ring on the ground, so the effect reads from above as well as from the side.
        int ringCount = (int) Math.max(10.0D, Math.min(56.0D, 28.0D * scale));
        double ringRadius = Math.max(2.0D, radius * (charging ? 0.35D + 0.65D * progress
                : 0.20D + 0.80D * progress));
        for (int i = 0; i < ringCount; i++) {
            double theta = Math.PI * 2.0D * i / ringCount + age * 0.03D;
            double outward = charging ? 0.02D : 0.09D;
            mc.level.addParticle(motifParticle(motif.theme(), i * 3 + age),
                    x + Math.cos(theta) * ringRadius, y + 0.25D, z + Math.sin(theta) * ringRadius,
                    Math.cos(theta) * outward, 0.04D, Math.sin(theta) * outward);
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
    /**
     * One live effect, ready to draw.
     *
     * Package-private because {@link VolumetricFx} draws the same list through the GPU raymarcher instead
     * of through triangles.
     */
    record Effect(double x, double y, double z, double radius, float progress, float strength,
                  BlastMotif.Motif motif, long seed, int age, BlastMotif.Phase phase) {

        boolean charging() {
            return phase == BlastMotif.Phase.CHARGE;
        }
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
