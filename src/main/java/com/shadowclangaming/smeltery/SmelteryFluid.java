package com.shadowclangaming.smeltery;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;

/**
 * Smeltery fluids - clean-room reimplementation of classic Tinkers Construct smeltery fluids.
 * NOT copied from old code: re-derived from mechanics description.
 * Includes molten metals from vanilla + mods, plus Unobtainium alloy.
 *
 * Yellorium = Extreme Reactors fuel. Molten Yellorium comes from melting Yellorium ingots.
 * Molten Nether Star comes from melting Nether Stars.
 * Unobtainium = 1 part Molten Nether Star + 8 parts Molten Yellorium => 9 ingots worth (1296 mB)
 *   500x power, 100x lifetime vs Yellorium.
 */
public enum SmelteryFluid {
    MOLTEN_IRON("molten_iron", 0xFFD80000, 600, 1450),
    MOLTEN_GOLD("molten_gold", 0xFFFFD700, 400, 1000),
    MOLTEN_COPPER("molten_copper", 0xFFB87333, 450, 1100),
    MOLTEN_TIN("molten_tin", 0xFFC0C0C0, 350, 900),
    MOLTEN_BRONZE("molten_bronze", 0xFFCD7F32, 500, 1200),
    MOLTEN_STEEL("molten_steel", 0xFF6E6E6E, 700, 1600),
    MOLTEN_MANYULLYN("molten_manyullyn", 0xFF9261CC, 800, 1800),
    MOLTEN_ALUMINIUM("molten_aluminium", 0xFFD9D9D9, 400, 1000),
    MOLTEN_COBALT("molten_cobalt", 0xFF333399, 750, 1700),
    MOLTEN_ARDITE("molten_ardite", 0xFFCC3300, 850, 1900),
    MOLTEN_YELLORIUM("molten_yellorium", 0xFFE8F020, 650, 1500),
    MOLTEN_NETHER_STAR("molten_nether_star", 0xFFAA00FF, 1200, 2500),
    MOLTEN_UNOBTAINIUM("molten_unobtainium", 0xFF00FFAA, 1500, 3000);

    private final String id;
    private final int colour;
    private final int meltTemp;
    private final int density;

    SmelteryFluid(String id, int colour, int meltTemp, int density) {
        this.id = id; this.colour = colour; this.meltTemp = meltTemp; this.density = density;
    }

    public String id() { return id; }
    public int colour() { return colour; }
    public int meltTemp() { return meltTemp; }
    public ResourceLocation location() { return ResourceLocation.fromNamespaceAndPath("shadowclangaming", id); }

    public static SmelteryFluid byId(String id) {
        if (id == null) return null;
        for (SmelteryFluid f : values()) if (f.id.equalsIgnoreCase(id)) return f;
        return null;
    }

    public String getSerializedName() { return name().toLowerCase(Locale.ROOT); }
}
