package com.shadowclangaming.client.photoreal;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class PhotorealShaderRegistration {
    private static final Map<PhotorealFx.PhotorealMode, ShaderInstance> SHADERS=new HashMap<>();
    private static final Map<PhotorealFx.PhotorealMode, Boolean> FAILED=new HashMap<>();
    private PhotorealShaderRegistration(){}
    public static void register(RegisterShadersEvent event){
        for(PhotorealFx.PhotorealMode mode: PhotorealFx.PhotorealMode.values()){
            if(mode==PhotorealFx.PhotorealMode.NONE) continue;
            ResourceLocation loc=PhotorealFx.shaderFor(mode);
            if(loc==null) continue;
            try{
                event.registerShader(new ShaderInstance(event.getResourceProvider(), loc.toString(),
                        DefaultVertexFormat.POSITION), loaded->{
                    SHADERS.put(mode, loaded);
                    FAILED.put(mode,false);
                    ShadowClanGaming.LOGGER.info("Registered photoreal shader {}",loc);
                });
            }catch(IOException|RuntimeException ex){
                FAILED.put(mode,true);
                ShadowClanGaming.LOGGER.warn("Photoreal shader {} failed, falling back to volume",loc,ex);
            }
        }
    }
    public static ShaderInstance get(PhotorealFx.PhotorealMode mode){ return SHADERS.get(mode); }
    public static boolean available(PhotorealFx.PhotorealMode mode){ return SHADERS.containsKey(mode) && SHADERS.get(mode)!=null && !FAILED.getOrDefault(mode,false); }
    public static boolean isPhotoreal(ShaderInstance program){ return program!=null && SHADERS.containsValue(program); }
}
