package com.sp.destruction;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringIdentifiable;
import java.util.Objects;
import java.util.Optional;

public enum DestructionType implements StringIdentifiable {
    NUKE("nuke"),
    ORBITAL_LASER("laser"),
    PLANET("planet"),
    SUPERNOVA("supernova"),
    BLACK_HOLE("black_hole");
    private final String name;
    public static final Codec<DestructionType> CODEC = StringIdentifiable.createCodec(DestructionType::values);
    DestructionType(String name) { this.name = name; }
    public String getName() { return this.name; }
    public static Optional<DestructionType> getFromName(String name) {
        for (DestructionType d : values()) if (Objects.equals(d.getName(), name)) return Optional.of(d);
        return Optional.empty();
    }
    @Override public String asString() { return this.name; }
}
