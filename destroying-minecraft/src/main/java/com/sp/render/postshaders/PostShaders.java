package com.sp.render.postshaders;

import com.sp.DestroyingMinecraft;
import com.sp.render.postshaders.custom.*;

public class PostShaders {
    public static final NukePostShader NUKE = new NukePostShader();
    public static final CracksPostShader CRACKS = new CracksPostShader();
    public static final PlanetPostShader PLANET = new PlanetPostShader();
    public static final SupernovaPostShader SUPERNOVA = new SupernovaPostShader();
    public static final BlackHolePostShader BLACK_HOLE = new BlackHolePostShader();
    public static final BloomPostShader BLOOM = new BloomPostShader();
    public static final PostProcessingPostShader POST = new PostProcessingPostShader();
    public static final ShadowPostShader SHADOW = new ShadowPostShader();

    public static void registerPostShaders() {
        DestroyingMinecraft.LOGGER.info("Registering client post shaders");
    }
}
