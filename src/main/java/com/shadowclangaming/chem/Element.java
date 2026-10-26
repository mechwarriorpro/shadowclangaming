package com.shadowclangaming.chem;

import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

import java.util.Locale;

/**
 * The periodic table, as this mod understands it.
 *
 * IMPORTANT: this is OUR table, written from scratch. Invented mechanics that happen to resemble
 * older chemistry mods are fine; copied code and art are not. Nothing here comes from any other mod.
 *
 * This is deliberately an enum rather than a datapack registry: it keeps the first version readable.
 * When you outgrow it (M2 in DESIGN.md), convert it to a data-driven registry so your server can add
 * custom isotopes via a datapack.
 *
 * Adding an element = one line + a lang entry ("element.shadowclangaming.<name>") + (optional) putting it in
 * DecompositionTable / SynthesisTable.
 */
public enum Element implements StringRepresentable {
    //      name     symbol atomicNo mass  colour(ARGB)   fissile fusionFuel
    H       ("Hydrogen",  "H",    1,   1.0,  0xFFE8F4FF, false, false),
    LI      ("Lithium",   "Li",   3,   6.9,  0xFFD9D9FF, false, true),
    C       ("Carbon",    "C",    6,  12.0,  0xFF303030, false, false),
    N       ("Nitrogen",  "N",    7,  14.0,  0xFF7FB0FF, false, false),
    O       ("Oxygen",    "O",    8,  16.0,  0xFFFF5A5A, false, false),
    NA      ("Sodium",    "Na",  11,  23.0,  0xFFF5F5B0, false, false),
    AL      ("Aluminium", "Al",  13,  27.0,  0xFFC8D0D8, false, false),
    SI      ("Silicon",   "Si",  14,  28.1,  0xFF8A8F9A, false, false),
    S       ("Sulfur",    "S",   16,  32.1,  0xFFE8D24A, false, false),
    K       ("Potassium", "K",   19,  39.1,  0xFFB9A0D8, false, false),
    FE      ("Iron",      "Fe",  26,  55.8,  0xFFD8D8D8, false, false),
    CU      ("Copper",    "Cu",  29,  63.5,  0xFFE08A4A, false, false),
    ZN      ("Zinc",      "Zn",  30,  65.4,  0xFF9FB6A8, false, false),
    AU      ("Gold",      "Au",  79, 197.0,  0xFFFFD54A, false, false),
    PB      ("Lead",      "Pb",  82, 207.2,  0xFF6E7280, false, false),
    T       ("Tritium",   "T",    1,   3.0,  0xFF9CFF6E, false, true),
    U235    ("Uranium-235","U-235",92, 235.0, 0xFF7CE07C, true,  false),
    U238    ("Uranium-238","U-238",92, 238.0, 0xFF4F9E4F, false, false),
    PU239   ("Plutonium-239","Pu-239",94,239.0, 0xFFE0A0E0, true, false);

    public static final Codec<Element> CODEC = StringRepresentable.fromEnum(Element::values);
    public static final StreamCodec<ByteBuf, Element> STREAM_CODEC =
            ByteBufCodecs.STRING_UTF8.map(Element::byName, Element::getSerializedName);

    private final String displayName;
    private final String symbol;
    private final int atomicNumber;
    private final double atomicMass;
    private final int colour;
    private final boolean fissile;
    private final boolean fusionFuel;

    Element(String displayName, String symbol, int atomicNumber, double atomicMass,
            int colour, boolean fissile, boolean fusionFuel) {
        this.displayName = displayName;
        this.symbol = symbol;
        this.atomicNumber = atomicNumber;
        this.atomicMass = atomicMass;
        this.colour = colour;
        this.fissile = fissile;
        this.fusionFuel = fusionFuel;
    }

    public String displayName() {
        return displayName;
    }

    public String symbol() {
        return symbol;
    }

    public int atomicNumber() {
        return atomicNumber;
    }

    public double atomicMass() {
        return atomicMass;
    }

    /** 0xAARRGGBB - used for laser colours, vial tints, potion effects, later GUI. */
    public int colour() {
        return colour;
    }

    /** Can this split in a chain reaction? Used by warhead yield maths (M1). */
    public boolean fissile() {
        return fissile;
    }

    /** Can this fuse? Used by fusion warheads. */
    public boolean fusionFuel() {
        return fusionFuel;
    }

    /** Translation key for this element's display name. */
    public String translationKey() {
        return "element.shadowclangaming." + getSerializedName();
    }

    @Override
    public String getSerializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Element byName(String name) {
        for (Element element : values()) {
            if (element.getSerializedName().equalsIgnoreCase(name)) {
                return element;
            }
        }
        return H;
    }
}
