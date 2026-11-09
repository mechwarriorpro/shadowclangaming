package com.sp.config;

import eu.midnightdust.lib.config.MidnightConfig;

public class DestroyingMinecraftConfig extends MidnightConfig {
    @Entry public static ShaderType shaderType = ShaderType.NUKE;
    public enum ShaderType { NONE, NUKE, CRACKS, BLACK_HOLE, PLANET, SUPERNOVA, METEOR, STAR_PIERCER }
}
