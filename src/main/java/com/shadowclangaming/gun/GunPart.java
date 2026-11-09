package com.shadowclangaming.gun;

import com.mojang.serialization.Codec;
import net.minecraft.util.StringRepresentable;

/**
 * A gun is six slots, and every slot holds one part. Parts are pure multipliers, which is what makes
 * the generator honest: the numbers on the tooltip are the product of the parts you can read.
 *
 * Six slots x several parts each is already a five-figure number of possible guns before rarity,
 * manufacturer and element are counted.
 */
public enum GunPart implements StringRepresentable {

    // ---- barrel: damage and accuracy ----
    BARREL_SMOOTH(Slot.BARREL, "barrel_smooth", 1.00F, 1.00F, 1.02F, 1.00F),
    BARREL_RIFLED(Slot.BARREL, "barrel_rifled", 1.12F, 0.95F, 1.12F, 1.00F),
    BARREL_SAWN(Slot.BARREL, "barrel_sawn", 1.22F, 1.05F, 0.80F, 1.00F),
    BARREL_LONG(Slot.BARREL, "barrel_long", 1.30F, 0.85F, 1.25F, 1.00F),
    BARREL_SPLIT(Slot.BARREL, "barrel_split", 1.45F, 0.80F, 0.70F, 1.00F),

    // ---- body: rate of fire ----
    BODY_STANDARD(Slot.BODY, "body_standard", 1.00F, 1.00F, 1.00F, 1.00F),
    BODY_LIGHT(Slot.BODY, "body_light", 0.92F, 1.22F, 1.02F, 1.00F),
    BODY_HEAVY(Slot.BODY, "body_heavy", 1.18F, 0.82F, 0.98F, 1.00F),
    BODY_AUTOMATIC(Slot.BODY, "body_automatic", 0.85F, 1.55F, 0.88F, 1.00F),
    BODY_OVERCHARGED(Slot.BODY, "body_overcharged", 1.35F, 1.30F, 0.75F, 1.00F),

    // ---- grip: handling ----
    GRIP_PLAIN(Slot.GRIP, "grip_plain", 1.00F, 1.00F, 1.00F, 1.00F),
    GRIP_WRAPPED(Slot.GRIP, "grip_wrapped", 1.00F, 1.05F, 1.08F, 1.00F),
    GRIP_ANGLED(Slot.GRIP, "grip_angled", 1.02F, 1.08F, 1.14F, 1.00F),
    GRIP_VENTED(Slot.GRIP, "grip_vented", 1.00F, 1.12F, 1.20F, 1.00F),

    // ---- stock: recoil control ----
    STOCK_NONE(Slot.STOCK, "stock_none", 1.10F, 1.10F, 0.72F, 1.00F),
    STOCK_FOLDING(Slot.STOCK, "stock_folding", 1.02F, 1.02F, 0.92F, 1.00F),
    STOCK_SOLID(Slot.STOCK, "stock_solid", 1.00F, 0.96F, 1.10F, 1.00F),
    STOCK_COUNTERWEIGHT(Slot.STOCK, "stock_counterweight", 0.98F, 0.92F, 1.22F, 1.00F),

    // ---- sight: accuracy ----
    SIGHT_IRON(Slot.SIGHT, "sight_iron", 1.00F, 1.00F, 1.00F, 1.00F),
    SIGHT_DOT(Slot.SIGHT, "sight_dot", 1.00F, 1.00F, 1.10F, 1.00F),
    SIGHT_SCOPE(Slot.SIGHT, "sight_scope", 1.05F, 0.95F, 1.28F, 1.00F),
    SIGHT_THERMAL(Slot.SIGHT, "sight_thermal", 1.03F, 0.98F, 1.35F, 1.00F),

    // ---- magazine: capacity ----
    MAG_SHORT(Slot.MAGAZINE, "mag_short", 1.00F, 1.00F, 1.00F, 0.70F),
    MAG_STANDARD(Slot.MAGAZINE, "mag_standard", 1.00F, 1.00F, 1.00F, 1.00F),
    MAG_EXTENDED(Slot.MAGAZINE, "mag_extended", 0.98F, 0.96F, 1.00F, 1.60F),
    MAG_DRUM(Slot.MAGAZINE, "mag_drum", 0.94F, 0.90F, 0.98F, 2.40F);

    /** Which of the six positions this part fills. */
    public enum Slot {
        BARREL, BODY, GRIP, STOCK, SIGHT, MAGAZINE
    }

    public static final Codec<GunPart> CODEC = StringRepresentable.fromEnum(GunPart::values);

    private final Slot slot;
    private final String id;
    private final float damage;
    private final float rate;
    private final float accuracy;
    private final float magazine;

    GunPart(Slot slot, String id, float damage, float rate, float accuracy, float magazine) {
        this.slot = slot;
        this.id = id;
        this.damage = damage;
        this.rate = rate;
        this.accuracy = accuracy;
        this.magazine = magazine;
    }

    @Override
    public String getSerializedName() {
        return id;
    }

    public Slot slot() {
        return slot;
    }

    public String id() {
        return id;
    }

    public float damageMult() {
        return damage;
    }

    public float rateMult() {
        return rate;
    }

    public float accuracyMult() {
        return accuracy;
    }

    public float magazineMult() {
        return magazine;
    }

    public String translationKey() {
        return "part.shadowclangaming." + id;
    }

    /** Every part that fits a given slot - the generator picks one of these. */
    public static GunPart[] forSlot(Slot slot) {
        java.util.List<GunPart> parts = new java.util.ArrayList<>();
        for (GunPart part : values()) {
            if (part.slot == slot) {
                parts.add(part);
            }
        }
        return parts.toArray(new GunPart[0]);
    }

    public static GunPart byId(String id) {
        for (GunPart part : values()) {
            if (part.id.equalsIgnoreCase(id)) {
                return part;
            }
        }
        return BARREL_SMOOTH;
    }
}
