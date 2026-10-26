package com.shadowclangaming.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.config.SCGConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Actual world-space shader-rendered blast billboards, depth-tested against the world. Never draws HUD pixels. */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class WorldBlastShader {
    private static final ResourceLocation PROGRAM = ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID, "world_blast");
    private static ShaderInstance shader;
    private static boolean loadFailed;
    private static long renderFrame;

    private WorldBlastShader() {}

    public static void registerShaders(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), PROGRAM.toString(),
                    DefaultVertexFormat.POSITION_TEX_COLOR), loaded -> {
                shader = loaded;
                loadFailed = false;
                ShadowClanGaming.LOGGER.info("Registered in-world explosive shader {}", PROGRAM);
            });
        } catch (IOException | RuntimeException ex) {
            loadFailed = true;
            ShadowClanGaming.LOGGER.error("Could not register in-world explosive shader {}; using world particles", PROGRAM, ex);
            com.shadowclangaming.debug.ShadowDebug.logThrowable("WORLD_BLAST_SHADER_REGISTER_FAIL", PROGRAM.toString(), ex);
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || !SCGConfig.cinematicShadersEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long frame=++renderFrame;
        List<Effect> effects=collect(mc);
        com.shadowclangaming.debug.ShadowDebug.log("WORLD_BLAST_SHADER_FRAME",
                "frame={} active={} registered={} failed={} stage=AFTER_PARTICLES",frame,effects.size(),shader!=null,loadFailed);
        if (effects.isEmpty() || shader==null) return;
        var camera=event.getCamera();
        Vec3 cam=camera.getPosition();
        Vector3f forward=camera.getLookVector();
        Vector3f up=camera.getUpVector();
        Vector3f right=forward.cross(up,new Vector3f()).normalize();
        Vector3f upOrtho=right.cross(forward,new Vector3f()).normalize();
        ShaderInstance previous=RenderSystem.getShader();
        PoseStack poseStack=event.getPoseStack();
        poseStack.pushPose();
        // Match the established world-FX render path: translate the event pose by -camera, submit
        // world-space vertices, and let Minecraft's active ModelView matrix apply camera rotation once.
        // Previously vertices were already camera-relative while their billboard axes were world-space;
        // the active camera ModelView rotated them a second time, often clipping the entire quad away.
        poseStack.translate(-cam.x,-cam.y,-cam.z);
        var worldView=poseStack.last().pose();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        try {
            RenderSystem.setShader(() -> shader);
            for (Effect e:effects) draw(e,right,upOrtho,worldView,frame);
        } catch (Throwable ex) {
            loadFailed=true;
            ShadowClanGaming.LOGGER.error("World explosive shader draw failed; retaining in-world particle fallback",ex);
            com.shadowclangaming.debug.ShadowDebug.logThrowable("WORLD_BLAST_SHADER_DRAW_FAIL","frame="+frame,ex);
        } finally {
            poseStack.popPose();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.enableCull();
            if(previous!=null) RenderSystem.setShader(() -> previous);
        }
    }

    private static void draw(Effect e, Vector3f right, Vector3f up, Matrix4f worldView, long frame) {
        float visualRadius=(float)Math.max(2.2,Math.min(36.0,Math.sqrt(Math.max(1,e.radius))*2.25));
        float expand=0.26F+0.74F*e.progress;
        float half=visualRadius*expand;
        float cx=(float)e.x, cy=(float)e.y, cz=(float)e.z;
        shader.safeGetUniform("GameTime").set((System.currentTimeMillis()%120000L)/1000.0F);
        shader.safeGetUniform("EffectType").set((float)e.type);
        shader.safeGetUniform("Progress").set(e.progress);
        shader.safeGetUniform("Intensity").set(e.strength);
        BufferBuilder b=Tesselator.getInstance().begin(VertexFormat.Mode.QUADS,DefaultVertexFormat.POSITION_TEX_COLOR);
        vertex(b,worldView,cx,cy,cz,right,up,-half,-half,0,1,e);
        vertex(b,worldView,cx,cy,cz,right,up, half,-half,1,1,e);
        vertex(b,worldView,cx,cy,cz,right,up, half, half,1,0,e);
        vertex(b,worldView,cx,cy,cz,right,up,-half, half,0,0,e);
        BufferUploader.drawWithShader(b.buildOrThrow());
        com.shadowclangaming.debug.ShadowDebug.log("WORLD_BLAST_DRAW_SUBMIT",
                "frame={} type={} center=({},{},{}) radius={} strength={} progress={}",
                frame,e.type,e.x,e.y,e.z,half,e.strength,e.progress);
    }

    private static void vertex(BufferBuilder b,Matrix4f worldView,float cx,float cy,float cz,Vector3f right,Vector3f up,
                               float rx,float uy,float u,float v,Effect e) {
        float x=cx+right.x()*rx+up.x()*uy;
        float y=cy+right.y()*rx+up.y()*uy;
        float z=cz+right.z()*rx+up.z()*uy;
        b.addVertex(worldView,x,y,z).setUv(u,v).setColor(e.r,e.g,e.b,255);
    }

    private static List<Effect> collect(Minecraft mc) {
        Vec3 cam=mc.gameRenderer.getMainCamera().getPosition();
        List<Effect> out=new ArrayList<>();
        for(var e:ClientBlastQueue.active()) {
            double distance=Math.sqrt(sq(e.x-cam.x)+sq(e.y-cam.y)+sq(e.z-cam.z));
            double reach=(e.radius+100.0)*SCGConfig.shaderDistance();
            if(distance>reach) continue;
            float proximity=(float)(1-Math.min(1,distance/reach));
            float strength=(float)Math.max(0,Math.min(1,proximity*proximity*e.intensity()*e.style.flash()*SCGConfig.shaderStrength()));
            if(strength>0.025F) {
                int c=e.style.colour();
                out.add(new Effect(e.x,e.y,e.z,e.radius,e.progress(),strength,type(e.style.id()),
                        (c>>16)&255,(c>>8)&255,c&255));
            }
        }
        for(var e:ClientApexQueue.active()) {
            double distance=Math.sqrt(sq(e.x-cam.x)+sq(e.y-cam.y)+sq(e.z-cam.z));
            double reach=(e.radius+150.0)*SCGConfig.shaderDistance();
            if(distance>reach) continue;
            float proximity=(float)(1-Math.min(1,distance/reach));
            float t=Math.min(1,e.age/(float)Math.max(1,e.lifetime));
            float strength=(float)Math.max(0.0,Math.min(1.0,proximity*proximity*(1.0F-t)*SCGConfig.shaderStrength()));
            if(strength>0.025F) {
                // Apex packets can put their center below terrain (including y<0). A camera-facing
                // billboard there is completely hidden by the terrain depth buffer. Lift only the
                // rendered visual to the local surface; the server-side blast origin/damage is unchanged.
                BlockPos origin=BlockPos.containing(e.x,e.y,e.z);
                int surfaceY=mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,origin.getX(),origin.getZ());
                double visualY=Math.max(e.y,surfaceY+0.25D);
                if(visualY!=e.y) {
                    com.shadowclangaming.debug.ShadowDebug.log("WORLD_BLAST_SURFACE_LIFT",
                            "id={} originY={} surfaceY={} visualY={}",e.apex.id(),e.y,surfaceY,visualY);
                }
                int[] c=apexColour(e.apex.id());
                out.add(new Effect(e.x,visualY,e.z,e.radius,t,strength,5,c[0],c[1],c[2]));
            }
        }
        out.sort(Comparator.comparingDouble((Effect e)->e.strength).reversed());
        if(out.size()>5) return new ArrayList<>(out.subList(0,5));
        return out;
    }

    /** Low-cost world-space vanilla fallback if shader registration/drawing is unavailable; never a HUD wash. */
    public static void tickFallback() {
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null) return;
        Vec3 cam=mc.gameRenderer.getMainCamera().getPosition();
        boolean shaderUnavailable=!SCGConfig.cinematicShadersEnabled() || shader==null || loadFailed;
        // Always emit a small in-world particle signature for apex detonations. Shader registration alone
        // does not guarantee that a custom RenderType draw reaches the active level framebuffer (some render
        // pipelines/mod combinations bypass or replace that pass). These particles are world entities, not HUD.
        for(var e:ClientApexQueue.active()) {
            if(e.age>24 || e.age%2!=0) continue;
            BlockPos pos=BlockPos.containing(e.x,e.y,e.z);
            int surfaceY=mc.level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,pos.getX(),pos.getZ());
            double visualY=Math.max(e.y,surfaceY+0.35D);
            double dx=e.x-cam.x,dy=visualY-cam.y,dz=e.z-cam.z;
            if(dx*dx+dy*dy+dz*dz>320.0*320.0) continue;
            double radius=1.2+e.age*0.72;
            int count=16;
            for(int i=0;i<count;i++) {
                double a=(Math.PI*2*i/count)+e.age*0.09;
                double px=e.x+Math.cos(a)*radius, pz=e.z+Math.sin(a)*radius;
                mc.level.addParticle(ParticleTypes.END_ROD,px,visualY+0.25,pz,
                        Math.cos(a)*0.025,0.06,Math.sin(a)*0.025);
                if(i%2==0) mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,px,visualY+0.25,pz,
                        Math.cos(a)*0.10,0.08,Math.sin(a)*0.10);
            }
            mc.level.addParticle(ParticleTypes.FLAME,e.x,visualY+0.5,e.z,0.0,0.08,0.0);
        }
        if(shaderUnavailable) for(var e:ClientBlastQueue.active()) {
            if(e.age>8 || e.age%2!=0) continue;
            double dx=e.x-cam.x,dy=e.y-cam.y,dz=e.z-cam.z;
            if(dx*dx+dy*dy+dz*dz>220.0*220.0) continue;
            int count=8;
            for(int i=0;i<count;i++) {
                double a=(Math.PI*2*i/count)+e.age*0.13;
                double r=0.7+e.age*0.42;
                mc.level.addParticle(ParticleTypes.FLAME,e.x+Math.cos(a)*r,e.y+0.25,e.z+Math.sin(a)*r,
                        Math.cos(a)*0.03,0.04,Math.sin(a)*0.03);
                if(i%2==0) mc.level.addParticle(ParticleTypes.ELECTRIC_SPARK,e.x+Math.cos(a)*r,e.y+0.25,e.z+Math.sin(a)*r,
                        Math.cos(a)*0.12,0.08,Math.sin(a)*0.12);
            }
        }
    }

    private static int type(String id) {
        String s=id.toLowerCase();
        if(s.contains("gravity")||s.contains("singularity")||s.contains("void")) return 2;
        if(s.contains("vortex")||s.contains("rift")||s.contains("spacetime")) return 3;
        if(s.contains("toxic")||s.contains("radiation")||s.contains("rad")) return 4;
        if(s.contains("black")||s.contains("apex")||s.contains("omega")) return 5;
        return 1;
    }
    private static int[] apexColour(String id) {
        String s=id.toLowerCase();
        if(s.contains("void")||s.contains("abyss")||s.contains("annihilation")) return new int[]{145,86,255};
        if(s.contains("soul")) return new int[]{80,255,147};
        if(s.contains("zero")) return new int[]{160,232,255};
        if(s.contains("reality")||s.contains("rift")||s.contains("arcane")) return new int[]{199,85,255};
        return new int[]{130,196,255};
    }
    private static double sq(double v){return v*v;}
    private record Effect(double x,double y,double z,int radius,float progress,float strength,int type,int r,int g,int b) {}
}
