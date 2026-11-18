package com.shadowclangaming.gun;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * Borderlands gun types — 1.6.4 homage. Each type changes base stats and shot behavior.
 * Pistol/SMG/AR/Sniper scale with manufacturer; Shotgun fires pellets; Launcher fires explosive projectile.
 */
public enum GunType implements StringRepresentable {
    PISTOL("pistol", 1.1F, 1.0F, 0.95F, 0.6F, 1, false),
    SHOTGUN("shotgun", 1.3F, 0.6F, 0.65F, 1.4F, 8, false),
    SMG("smg", 0.8F, 1.45F, 0.80F, 0.9F, 1, false),
    ASSAULT_RIFLE("assault_rifle", 1.0F, 1.1F, 0.85F, 1.2F, 1, false),
    SNIPER("sniper", 1.8F, 0.45F, 1.15F, 0.5F, 1, false),
    ROCKET_LAUNCHER("rocket_launcher", 2.8F, 0.30F, 0.75F, 1.8F, 1, true);

    public static final Codec<GunType> CODEC = StringRepresentable.fromEnum(GunType::values);

    private final String id;
    private final float damageBias;
    private final float rateBias;
    private final float accuracyBias;
    private final float magBias;
    private final int pellets;
    private final boolean explosive;

    GunType(String id, float dmg, float rate, float acc, float mag, int pellets, boolean explosive) {
        this.id = id;
        this.damageBias = dmg;
        this.rateBias = rate;
        this.accuracyBias = acc;
        this.magBias = mag;
        this.pellets = pellets;
        this.explosive = explosive;
    }

    public String getSerializedName() { return id; }
    public String id() { return id; }
    public float damageBias() { return damageBias; }
    public float rateBias() { return rateBias; }
    public float accuracyBias() { return accuracyBias; }
    public float magBias() { return magBias; }
    public int pellets() { return pellets; }
    public boolean isExplosive() { return explosive; }
    public String translationKey() { return "guntype.shadowclangaming."+id; }
    public static GunType byId(String id) {
        for (GunType t: values()) if(t.id.equalsIgnoreCase(id)) return t;
        return PISTOL;
    }
    public static GunType pick(net.minecraft.util.RandomSource r) {
        return values()[r.nextInt(values().length)];
    }
}
