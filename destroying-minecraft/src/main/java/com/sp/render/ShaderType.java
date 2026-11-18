package com.sp.render;

import com.mojang.serialization.Codec;
import com.sp.destruction.DestructionType;
import com.sp.destruction.client.ClientDestructionEvents;
import com.sp.render.postshaders.PostShader;
import com.sp.render.postshaders.PostShaders;
import net.minecraft.util.StringRepresentable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public enum ShaderType implements StringRepresentable {
    NONE("none", false, false, false, false, (PostShader) null),
    NUKE("nuke", (PostShader) ClientDestructionEvents.NUKE_CLIENT.getPostShader()),
    CRACKS("orbital_laser", (PostShader) ClientDestructionEvents.CRACKS_CLIENT.getPostShader()),
    PLANET("planet", (PostShader) ClientDestructionEvents.PLANET_CLIENT.getPostShader()),
    SUPERNOVA("supernova", (PostShader) null),
    BLACK_HOLE("black_hole", true, false, true, true, (PostShader) ClientDestructionEvents.BLACK_HOLE_CLIENT.getPostShader());

    public static final Codec<ShaderType> CODEC = StringRepresentable.fromEnum(ShaderType::values);
    final String id;
    final List<PostShader> enabledShaders;

    ShaderType(String id, PostShader... postShaders) { this(id, true, true, true, true, postShaders); }

    ShaderType(String id, boolean enableShadows, boolean enableSky, boolean enableBloom, boolean enablePost, PostShader... postShaders) {
        this.id = id; this.enabledShaders = new ArrayList<>();
        if (enableSky) { try { this.enabledShaders.add((PostShader) ClientDestructionEvents.SUPERNOVA_CLIENT.getPostShader()); } catch (Exception ignored) {} }
        if (enableShadows) this.enabledShaders.add(PostShaders.SHADOW);
        if (postShaders != null) this.enabledShaders.addAll(Arrays.stream(postShaders).filter(s -> s != null).toList());
        if (enableBloom) this.enabledShaders.add(PostShaders.BLOOM);
        if (enablePost) this.enabledShaders.add(PostShaders.POST);
    }

    public List<PostShader> getEnabledShaders() { return this.enabledShaders; }
    @Override public String getSerializedName() { return this.id; }
    public static ShaderType getFromString(String shader) { for (ShaderType t : values()) if (shader.equals(t.id)) return t; return NONE; }
    public static ShaderType getFromDestructionType(DestructionType type) {
        return switch (type) {
            case NUKE -> NUKE; case ORBITAL_LASER -> CRACKS; case PLANET -> PLANET; case SUPERNOVA -> SUPERNOVA; case BLACK_HOLE -> BLACK_HOLE;
        };
    }
}
