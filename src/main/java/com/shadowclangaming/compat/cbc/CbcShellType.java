package com.shadowclangaming.compat.cbc;

/**
 * Shell families from CBC and CBC Enhanced Shells, plus warhead-converted variants.
 * <p>
 * Original 9 types from Enhanced Shells (arginou57):
 * HE, AP, AP Shot, Solid Shot, Shrapnel, Smoke, Fluid, Mortar Stone, Grape Shot
 * + 5 exotic shader shells: Plasma, Gravitational, Vortex, Disintegration, Singularity
 * + Doomsday: Nuclear, H-Bomb
 * <p>
 * This enum adds a generic WARHEAD carrier that maps every ShadowClan warhead to a CBC shell.
 * Each type has distinct mass, damage, blast behaviour — same as original balancing.
 */
public enum CbcShellType {
    // Standard CBC types
    HE("he", 2.0f, 8.0f, 30f, true, FuzeType.IMPACT),
    AP("ap", 6.0f, 5.0f, 15f, true, FuzeType.BASE),
    AP_SHOT("ap_shot", 8.0f, 0f, 50f, false, FuzeType.NONE),
    SOLID_SHOT("solid_shot", 3.0f, 0f, 25f, false, FuzeType.NONE),
    SHRAPNEL("shrapnel", 2.0f, 2.0f, 10f, true, FuzeType.TIMED),
    SMOKE("smoke", 2.0f, 0f, 0f, false, FuzeType.IMPACT),
    FLUID("fluid", 2.0f, 2.0f, 5f, false, FuzeType.IMPACT),
    MORTAR_STONE("mortar_stone", 4.0f, 6.0f, 20f, false, FuzeType.IMPACT),
    GRAPESHOT("grapeshot", 1.0f, 0f, 8f, false, FuzeType.NONE),
    // Enhanced / Doomsday
    NUCLEAR("nuclear", 5.0f, 240f, 100f, true, FuzeType.TIMED),
    H_BOMB("h_bomb", 8.0f, 320f, 150f, true, FuzeType.TIMED),
    // Shader exotics — these are the 5 with screen-distortion GLSL (permission granted)
    PLASMA("plasma", 3.0f, 12f, 40f, true, FuzeType.IMPACT),
    GRAVITATIONAL("gravitational", 4.0f, 16f, 45f, true, FuzeType.IMPACT),
    VORTEX("vortex", 3.0f, 14f, 42f, true, FuzeType.TIMED),
    DISINTEGRATION("disintegration", 3.0f, 18f, 48f, true, FuzeType.IMPACT),
    SINGULARITY("singularity", 6.0f, 22f, 60f, true, FuzeType.TIMED),
    // Warhead carrier — every WarheadType maps to this family for CBC firing
    WARHEAD("warhead", 3.0f, 10f, 35f, true, FuzeType.IMPACT);

    public enum FuzeType { NONE, IMPACT, BASE, TIMED, PROXIMITY }

    private final String id;
    private final float mass;
    private final float baseBlast;
    private final float baseDamage;
    private final boolean explosive;
    private final FuzeType fuze;

    CbcShellType(String id, float mass, float baseBlast, float baseDamage, boolean explosive, FuzeType fuze) {
        this.id = id; this.mass = mass; this.baseBlast = baseBlast; this.baseDamage = baseDamage; this.explosive = explosive; this.fuze = fuze;
    }
    public String id() { return id; }
    public float mass() { return mass; }
    public float baseBlast() { return baseBlast; }
    public float baseDamage() { return baseDamage; }
    public boolean explosive() { return explosive; }
    public FuzeType fuze() { return fuze; }

    public boolean hasShader() { return this == PLASMA || this == GRAVITATIONAL || this == VORTEX || this == DISINTEGRATION || this == SINGULARITY; }
    public String shaderId() { return hasShader() ? "cbc_" + id : null; }

    public static CbcShellType byId(String id) {
        for (CbcShellType t : values()) if (t.id.equalsIgnoreCase(id)) return t;
        return HE;
    }
}
