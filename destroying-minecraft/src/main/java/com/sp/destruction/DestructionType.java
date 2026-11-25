package com.sp.destruction;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;
import java.util.Objects;
import java.util.Optional;

public enum DestructionType implements StringRepresentable {
    NUKE("nuke"),
    ORBITAL_LASER("laser"),
    PLANET("planet"),
    SUPERNOVA("supernova"),
    BLACK_HOLE("black_hole");
    private final String name;
    public static final Codec<DestructionType> CODEC = StringRepresentable.fromEnum(DestructionType::values);
    DestructionType(String name) { this.name = name; }
    public String getName() { return this.name; }
    public static Optional<DestructionType> getFromName(String name) {
        for (DestructionType d : values()) if (Objects.equals(d.getName(), name)) return Optional.of(d);
        return Optional.empty();
    }
    @Override public String getSerializedName() { return this.name; }
}
