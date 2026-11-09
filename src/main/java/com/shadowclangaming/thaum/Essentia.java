package com.shadowclangaming.thaum;

import net.minecraft.ChatFormatting;
import java.util.List;

/**
 * Essentia — clean-room homage to Thaumcraft. Primal + compound aspects, modern list expanded.
 * Old 1.2.5 crucible-of-souls concept (soul essentia) kept, new essentia added with combos.
 * Textures are original, not copied; effects are vanilla-based.
 */
public enum Essentia {
    // Primals (1.2.5 + modern)
    AER("aer", "Air", 0xFFFFF7, ChatFormatting.YELLOW, true, List.of()),
    TERRA("terra", "Earth", 0x2FFF2F, ChatFormatting.GREEN, true, List.of()),
    IGNIS("ignis", "Fire", 0xFF4500, ChatFormatting.RED, true, List.of()),
    AQUA("aqua", "Water", 0x3CD4FC, ChatFormatting.AQUA, true, List.of()),
    ORDO("ordo", "Order", 0xD4D4D4, ChatFormatting.WHITE, true, List.of()),
    PERDITIO("perditio", "Entropy", 0x3A3A3A, ChatFormatting.DARK_GRAY, true, List.of()),
    // Compounds — old + modern essentia with new combos/effects
    VICTUS("victus", "Life", 0xFF89AC, ChatFormatting.LIGHT_PURPLE, false, List.of("aer","terra")),
    MORTUUS("mortuus", "Death", 0x5A5A5A, ChatFormatting.DARK_GRAY, false, List.of("victus","perditio")),
    ANIMA("anima", "Soul", 0x9B30FF, ChatFormatting.DARK_PURPLE, false, List.of("victus","vinculum")), // crucible of souls core
    VINCLUM("vinculum", "Binding", 0x6B8E23, ChatFormatting.DARK_GREEN, false, List.of("ordo","perditio")),
    PRAECANTATIO("praecantatio", "Magic", 0xCF00FF, ChatFormatting.LIGHT_PURPLE, false, List.of("aer","terra")),
    AURAM("auram", "Aura", 0xFFC0CB, ChatFormatting.WHITE, false, List.of("aer","praecantatio")),
    VITIUM("vitium", "Taint", 0x800080, ChatFormatting.DARK_RED, false, List.of("perditio","praecantatio")),
    ALIENIS("alienis", "Eldritch", 0x2F4F4F, ChatFormatting.GRAY, false, List.of("ordo","perditio")),
    TENEBRAE("tenebrae", "Darkness", 0x1A1A1A, ChatFormatting.BLACK, false, List.of("perditio","vacuum")),
    LUX("lux", "Light", 0xFFFF99, ChatFormatting.YELLOW, false, List.of("aer","ignis")),
    MOTUS("motus", "Motion", 0xCD853F, ChatFormatting.GOLD, false, List.of("aer","ordo")),
    GELOUM("gelum", "Ice", 0xA0FFFF, ChatFormatting.AQUA, false, List.of("aqua","perditio")),
    VITREUS("vitreus", "Crystal", 0xB0E0E6, ChatFormatting.WHITE, false, List.of("terra","aqua")),
    METALLUM("metallum", "Metal", 0xC0C0C0, ChatFormatting.GRAY, false, List.of("terra","ordo")),
    HUMANUS("humanus", "Human", 0xFFDEB3, ChatFormatting.YELLOW, false, List.of("victus","cognitio")),
    COGNITIO("cognitio", "Mind", 0xFFD700, ChatFormatting.GOLD, false, List.of("ignis","ordo")),
    SENSUS("sensus", "Sense", 0x87CEEB, ChatFormatting.BLUE, false, List.of("aer","cognitio")),
    VACUOS("vacuos", "Void", 0x4B0082, ChatFormatting.DARK_PURPLE, false, List.of("perditio","aqua"));

    private final String id;
    private final String name;
    private final int color;
    private final ChatFormatting format;
    private final boolean primal;
    private final List<String> components;

    Essentia(String id, String name, int color, ChatFormatting format, boolean primal, List<String> comps){
        this.id=id; this.name=name; this.color=color; this.format=format; this.primal=primal; this.components=comps;
    }
    public String id(){return id;}
    public String display(){return name;}
    public int color(){return color;}
    public ChatFormatting format(){return format;}
    public boolean isPrimal(){return primal;}
    public List<String> components(){return components;}
    public static Essentia byId(String id){ for(var e:values()) if(e.id.equalsIgnoreCase(id)) return e; return AER; }
}
