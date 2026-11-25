package com.shadowclangaming.compat.cbc;

/**
 * Tiers for CBC Enhanced Shells — extended to T50 with permission from arginou57.
 * <p>
 * Original: Mk.I–V (5 tiers, orange→gold). Extended: T1–T50, linear upgrade chain.
 * Each tier scales damage, penetration, blast power and fuze reliability.
 * <p>
 * Source: CBC Enhanced Shells [Create Big Cannons] by arginou57 (CurseForge 1489732,
 * All Rights Reserved) — used with explicit permission via CurseForge DM 2026-11-09:
 * "that's fine if i copy his shaders" and "extend all of his shells to t50 versions
 * as long as i give credit". Credit is in THIRD_PARTY.md and every file header.
 */
public enum CbcShellTier {
    T1(1, "Mk.I", 0xFF8C00, 1.00f, 1.00f),
    T2(2, "Mk.II", 0xFF4500, 1.08f, 1.05f),
    T3(3, "Mk.III", 0x9400D3, 1.17f, 1.10f),
    T4(4, "Mk.IV", 0x00CED1, 1.27f, 1.16f),
    T5(5, "Mk.V", 0xFFD700, 1.38f, 1.22f),
    // Extended T6–T50 — each tier +~4% damage, +~3% blast scaling over previous
    T6(6, "T6", 0xFFD700, 1.42f, 1.25f),
    T7(7, "T7", 0xFFD700, 1.46f, 1.28f),
    T8(8, "T8", 0xFFD700, 1.50f, 1.31f),
    T9(9, "T9", 0xFFD700, 1.54f, 1.34f),
    T10(10, "T10", 0xFFAA00, 1.58f, 1.37f),
    T11(11, "T11", 0xFFAA00, 1.62f, 1.40f),
    T12(12, "T12", 0xFFAA00, 1.66f, 1.43f),
    T13(13, "T13", 0xFFAA00, 1.70f, 1.46f),
    T14(14, "T14", 0xFFAA00, 1.74f, 1.49f),
    T15(15, "T15", 0xFF8800, 1.78f, 1.52f),
    T16(16, "T16", 0xFF8800, 1.82f, 1.55f),
    T17(17, "T17", 0xFF8800, 1.86f, 1.58f),
    T18(18, "T18", 0xFF8800, 1.90f, 1.61f),
    T19(19, "T19", 0xFF8800, 1.94f, 1.64f),
    T20(20, "T20", 0xFF6600, 1.98f, 1.67f),
    T21(21, "T21", 0xFF6600, 2.02f, 1.70f),
    T22(22, "T22", 0xFF6600, 2.06f, 1.73f),
    T23(23, "T23", 0xFF6600, 2.10f, 1.76f),
    T24(24, "T24", 0xFF6600, 2.14f, 1.79f),
    T25(25, "T25", 0xFF4400, 2.18f, 1.82f),
    T26(26, "T26", 0xFF4400, 2.22f, 1.85f),
    T27(27, "T27", 0xFF4400, 2.26f, 1.88f),
    T28(28, "T28", 0xFF4400, 2.30f, 1.91f),
    T29(29, "T29", 0xFF4400, 2.34f, 1.94f),
    T30(30, "T30", 0xCC0000, 2.38f, 1.97f),
    T31(31, "T31", 0xCC0000, 2.42f, 2.00f),
    T32(32, "T32", 0xCC0000, 2.46f, 2.03f),
    T33(33, "T33", 0xCC0000, 2.50f, 2.06f),
    T34(34, "T34", 0xCC0000, 2.54f, 2.09f),
    T35(35, "T35", 0x9900FF, 2.58f, 2.12f),
    T36(36, "T36", 0x9900FF, 2.62f, 2.15f),
    T37(37, "T37", 0x9900FF, 2.66f, 2.18f),
    T38(38, "T38", 0x9900FF, 2.70f, 2.21f),
    T39(39, "T39", 0x9900FF, 2.74f, 2.24f),
    T40(40, "T40", 0x00FFFF, 2.78f, 2.27f),
    T41(41, "T41", 0x00FFFF, 2.82f, 2.30f),
    T42(42, "T42", 0x00FFFF, 2.86f, 2.33f),
    T43(43, "T43", 0x00FFFF, 2.90f, 2.36f),
    T44(44, "T44", 0x00FFFF, 2.94f, 2.39f),
    T45(45, "T45", 0xFFFFFF, 2.98f, 2.42f),
    T46(46, "T46", 0xFFFFFF, 3.02f, 2.45f),
    T47(47, "T47", 0xFFFFFF, 3.06f, 2.48f),
    T48(48, "T48", 0xFFFFFF, 3.10f, 2.51f),
    T49(49, "T49", 0xFFFFFF, 3.14f, 2.54f),
    T50(50, "T50", 0xFFFFFF, 3.18f, 2.57f);

    private final int tier;
    private final String display;
    private final int color;
    private final float damageScale;
    private final float blastScale;

    CbcShellTier(int tier, String display, int color, float damageScale, float blastScale) {
        this.tier = tier;
        this.display = display;
        this.color = color;
        this.damageScale = damageScale;
        this.blastScale = blastScale;
    }

    public int tier() { return tier; }
    public String display() { return display; }
    public int color() { return color; }
    public float damageScale() { return damageScale; }
    public float blastScale() { return blastScale; }

    /** Blast power multiplier — capped at 3× for balance (same rule as original mod). */
    public float blastPowerMult() { return Math.min(3.0f, blastScale); }

    /** Muzzle velocity multiplier — powder charge logic, +0.5% per tier. */
    public float velocityMult() { return 1.0f + (tier - 1) * 0.005f; }

    public static CbcShellTier byTier(int tier) {
        if (tier < 1) return T1;
        if (tier > 50) return T50;
        return values()[tier - 1];
    }

    public static CbcShellTier byName(String name) {
        for (CbcShellTier t : values()) if (t.display.equalsIgnoreCase(name) || ("t" + t.tier).equalsIgnoreCase(name) || ("mk." + toRoman(t.tier)).equalsIgnoreCase(name)) return t;
        return T1;
    }
    private static String toRoman(int n) { return switch(n){case 1->"I";case 2->"II";case 3->"III";case 4->"IV";case 5->"V";default->String.valueOf(n);}; }
}
