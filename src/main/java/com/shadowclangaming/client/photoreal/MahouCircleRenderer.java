package com.shadowclangaming.client.photoreal;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.warhead.BlastMotif;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

import java.util.HashMap;
import java.util.Map;

/**
 * Mahou Tsukai authentic magic circles: HD layered rings with runes.
 * Each warhead gets a distinct palette/tint and rotation profile.
 * Textures: textures/effect/circles/mahou_{warhead}.png + shared layers.
 */
public final class MahouCircleRenderer {
    private MahouCircleRenderer(){}

    // Per-warhead circle tint (ARGB of the base warhead palette)
    private static final Map<String,int[]> TINTS=new HashMap<>();
    static {
        TINTS.put("world_rune", new int[]{0xFFEEDD60,0xFFB0FF70});
        TINTS.put("arcane_singularity", new int[]{0xFFB070FF,0xFF9040E0});
        TINTS.put("soul_furnace", new int[]{0xFFC01018,0xFFE02028});
        TINTS.put("abyssal_covenant", new int[]{0xFF601060,0xFF8040A0});
        TINTS.put("eldritch_pulse", new int[]{0xFF5030A0,0xFF8060FF});
        TINTS.put("mana_overload", new int[]{0xFF00FFFF,0xFF00FA9A});
        TINTS.put("spell", new int[]{0xFFC070FF,0xFF9070FF});
    }

    public static ResourceLocation textureFor(String warheadId){
        String id=warheadId==null?"generic":warheadId.toLowerCase(java.util.Locale.ROOT);
        // try per-warhead, fallback generic
        ResourceLocation per=ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID,"textures/effect/circles/mahou_"+id+".png");
        // existence checked at runtime; caller falls back to generic if missing
        return per;
    }
    public static ResourceLocation genericTexture(){
        return ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID,"textures/effect/circles/mahou_generic.png");
    }
    public static ResourceLocation glyphRing(){
        return ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID,"textures/effect/circles/mahou_glyph_ring.png");
    }

    /**
     * Draw a horizontal magic circle at ground level.
     * Called from CinematicFx after VolumetricFx.
     */
    public static void renderCircle(PoseStack pose, double x,double y,double z,
                                    String warheadId, float progress, float radius){
        Minecraft mc=Minecraft.getInstance();
        if(mc.level==null) return;
        ResourceLocation tex=textureFor(warheadId);
        // tint from map
        int[] tint=TINTS.getOrDefault(warheadId==null?"":warheadId.toLowerCase(java.util.Locale.ROOT),
                new int[]{0xFFFFFFFF,0xFFB0A0FF});
        float r=((tint[0]>>16)&255)/255f, g=((tint[0]>>8)&255)/255f, b=(tint[0]&255)/255f, a= ((tint[0]>>24)&255)/255f;
        float spin= (progress*40f)%360f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, tex);

        Matrix4f mat=pose.last().pose();
        Tesselator tess=Tesselator.getInstance();
        BufferBuilder buf=tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        // quad facing up at y, size radius*2
        float s=(float)radius*1.8f;
        // two triangles as quad with rotation around Y
        double cos=Math.cos(Math.toRadians(spin)), sin=Math.sin(Math.toRadians(spin));
        // corners in xz
        float[] xs={-s,-s,s,s}, zs={-s,s,s,-s};
        for(int i=0;i<4;i++){
            float rx=(float)(xs[i]*cos - zs[i]*sin);
            float rz=(float)(xs[i]*sin + zs[i]*cos);
            buf.addVertex(mat, (float)(x+rx), (float)(y+0.02), (float)(z+rz)).setUv(i%2, i/2).setColor(r,g,b,a*0.9f);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        // second glyph ring counter-rotating
        RenderSystem.setShaderTexture(0, glyphRing());
        float s2=s*0.72f;
        float spin2=-spin*1.4f;
        double c2=Math.cos(Math.toRadians(spin2)), s22=Math.sin(Math.toRadians(spin2));
        buf=tess.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for(int i=0;i<4;i++){
            float rx=(float)(xs[i]*c2 - zs[i]*s22) *0.72f;
            float rz=(float)(xs[i]*s22 + zs[i]*c2)*0.72f;
            // reuse s scaling via 0.72
            buf.addVertex(mat, (float)(x+rx), (float)(y+0.025), (float)(z+rz)).setUv(i%2,i/2).setColor(1,1,1,0.85f);
        }
        BufferUploader.drawWithShader(buf.buildOrThrow());
        RenderSystem.disableBlend();
    }
}
