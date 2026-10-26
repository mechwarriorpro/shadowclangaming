package com.shadowclangaming.gun;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * The four manufacturers. Each one is a personality expressed as three numbers, so a Kestrel gun
 * always feels like a Kestrel gun even when the parts are random.
 *
 * These are our own names and our own biases - nothing borrowed.
 */
public enum GunMaker implements StringRepresentable {
    /** Slow, heavy, hits like a truck. */
    VULCAN("vulcan", 1.35F, 0.80F, 0.90F, 0.85F),
    /** Fast, light, inaccurate. */
    KESTREL("kestrel", 0.75F, 1.45F, 0.82F, 1.15F),
    /** Balanced, precise, expensive to keep fed. */
    HALCYON("halcyon", 1.05F, 1.05F, 1.15F, 1.00F),
    /** Odd numbers, big magazines, strange parts. */
    DRUMLIN("drumlin", 0.95F, 1.15F, 0.95F, 1.55F);

    public static final Codec<GunMaker> CODEC = StringRepresentable.fromEnum(GunMaker::values);

    private final String id;
    private final float damage;
    private final float fireRate;
    private final float accuracy;
    private final float magazine;

    GunMaker(String id, float damage, float fireRate, float accuracy, float magazine) {
        this.id = id;
        this.damage = damage;
        this.fireRate = fireRate;
        this.accuracy = accuracy;
        this.magazine = magazine;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public String id() {
        return id;
    }

    public float damageBias() {
        return damage;
    }

    public float rateBias() {
        return fireRate;
    }

    public float accuracyBias() {
        return accuracy;
    }

    public float magazineBias() {
        return magazine;
    }

    public String translationKey() {
        return "maker.shadowclangaming." + id;
    }

    public static GunMaker pick(net.minecraft.util.RandomSource random) {
        GunMaker[] all = values();
        return all[random.nextInt(all.length)];
    }

    public static GunMaker byId(String id) {
        for (GunMaker maker : values()) {
            if (maker.id.equalsIgnoreCase(id)) {
                return maker;
            }
        }
        return HALCYON;
    }
}
