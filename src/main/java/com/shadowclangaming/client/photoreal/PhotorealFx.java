package com.shadowclangaming.client.photoreal;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.warhead.BlastMotif;
import net.minecraft.resources.ResourceLocation;

import java.util.Set;

/**
 * Selects photorealistic rendering path per warhead.
 * Stellar -> star/nebula/supernova, Beam -> cylinder shaft, Ground-break -> cracks.
 * Based on Destroying MC's 5 events mapped to SCG's 122 warheads.
 */
public final class PhotorealFx {
    private PhotorealFx(){}

    public enum PhotorealMode { NONE, NUKE, BEAM, CRACKS, STAR }

    // Warheads that merit photoreal star path (STELLAR theme + apex stellar)
    private static final Set<String> STELLAR_IDS = Set.of(
            "stellar_requiem","starforge","colossal_star","big_bang_seed","stellar_swarm",
            "fusion","bloodstar","eclipse_catalyst"
    );
    private static final Set<String> BEAM_IDS = Set.of(
            "quasar_lance","orbital_railgun","meltdowner_beam","orbital_rain","railgun_barrage",
            "kaiju_cannon","plasma_caster","assembly_lance","vector_storm"
    );
    private static final Set<String> CRACK_IDS = Set.of(
            "kinetic","spacetime_hammer","gravity_crush","factory_reset","disassembler",
            "kinetic_overdrive","torgue_boom","matter_condenser","absolute_zero"
    );

    public static PhotorealMode modeFor(String warheadId, BlastMotif.Theme theme){
        String id = warheadId==null? "": warheadId.toLowerCase(java.util.Locale.ROOT);
        // Destroying Minecraft reference — direct mapping to original DestructionType visuals
        if(id.startsWith("destroying_")){
            return switch(id){
                case "destroying_nuke" -> PhotorealMode.NUKE;
                case "destroying_laser" -> PhotorealMode.BEAM;
                case "destroying_black_hole" -> PhotorealMode.STAR;
                case "destroying_planet" -> PhotorealMode.STAR;
                case "destroying_supernova" -> PhotorealMode.STAR;
                default -> PhotorealMode.NUKE;
            };
        }
        if(STELLAR_IDS.contains(id)) return PhotorealMode.STAR;
        if(BEAM_IDS.contains(id)) return PhotorealMode.BEAM;
        if(CRACK_IDS.contains(id)) return PhotorealMode.CRACKS;
        if(theme==BlastMotif.Theme.STELLAR) return PhotorealMode.STAR;
        if(id.contains("railgun")||id.contains("beam")||id.contains("lance")||id.contains("cannon")) return PhotorealMode.BEAM;
        if(id.contains("kinetic")||id.contains("crush")||id.contains("hammer")||id.contains("factory")) return PhotorealMode.CRACKS;
        // nuclear defaults to NUKE photoreal
        if(theme==BlastMotif.Theme.NUCLEAR || theme==BlastMotif.Theme.CATACLYSM) return PhotorealMode.NUKE;
        return PhotorealMode.NONE;
    }

    public static ResourceLocation shaderFor(PhotorealMode mode){
        return switch(mode){
            case NUKE -> ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID,"photoreal/photoreal_nuke");
            case BEAM -> ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID,"photoreal/photoreal_beam");
            case CRACKS -> ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID,"photoreal/photoreal_cracks");
            case STAR -> ResourceLocation.fromNamespaceAndPath(ShadowClanGaming.MOD_ID,"photoreal/photoreal_star");
            default -> null;
        };
    }

    /** Whether this warhead should draw Mahou Tsukai style magic circles (ARCANE/SOUL). */
    public static boolean needsMagicCircles(String warheadId, BlastMotif.Theme theme){
        String id=warheadId==null?"":warheadId.toLowerCase(java.util.Locale.ROOT);
        return theme==BlastMotif.Theme.ARCANE || theme==BlastMotif.Theme.SOUL
                || id.contains("world_rune") || id.contains("arcane") || id.contains("glyph")
                || id.contains("mana") || id.contains("soul") || id.contains("abyssal");
    }
}
