package com.shadowclangaming.esper;

import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import java.util.List;

/**
 * Esper categories — clean-room homage to AcademyCraft / Railgun Index esper powers.
 * Only esper (science) side, no magic. Our names/balance are original, not copied.
 * Each category maps to a Level 5 archetype seen in the series.
 */
public enum EsperCategory {
    ELECTROMASTER("electromaster", "Electromaster", ChatFormatting.AQUA,
            "Lightning, magnetism, the Railgun. High burst, needs line of sight.",
            List.of("thunder_bolt","railgun","iron_sand","lightning_rod","electro_shield")),
    TELEPORTER("teleporter", "Teleporter", ChatFormatting.LIGHT_PURPLE,
            "11th-dimension movement. Self, touch, and tactical swaps.",
            List.of("short_teleport","touch_teleport","mass_teleport","spatial_swap","teleport_beacon")),
    VECTOR("vector", "Vector Control", ChatFormatting.WHITE,
            "Control of all vectors — reflection, wind, plasma. Accelerator lineage.",
            List.of("vector_reflect","air_cannon","plasma_storm","vector_shield","ground_slam")),
    MELTDOWNER("meltdowner", "Meltowner", ChatFormatting.GREEN,
            "Particle-waveform light. Beams that pierce and detonate.",
            List.of("light_beam","multi_beam","meltdown_blast","solar_flare","photon_wall")),
    GRAVITON("graviton", "Graviton", ChatFormatting.DARK_PURPLE,
            "Gravity wells, flight, crushing pressure.",
            List.of("grav_push","grav_pull","grav_well","zero_g","singularity_pull")),
    PYROKINETIC("pyrokinetic", "Pyrokinetic", ChatFormatting.RED,
            "Thermal control — jets, armor, and field burns.",
            List.of("fire_jet","flame_armor","heat_wave","magma_pillar","burn_field")),
    AEROHAND("aerohand", "Aero Hand", ChatFormatting.GRAY,
            "Telekinesis & air. Push, pull, cut, and shield.",
            List.of("telekinetic_push","telekinetic_pull","air_cutter","repulse_field","flight_wing")),
    MENTAL_OUT("mental_out", "Mental Out", ChatFormatting.GOLD,
            "Neural interference — debuff, buff, and misdirection.",
            List.of("mind_stun","memory_haze","amplify","share_sight","command_word"));

    private final String id;
    private final String display;
    private final ChatFormatting color;
    private final String desc;
    private final List<String> skills;

    EsperCategory(String id, String display, ChatFormatting color, String desc, List<String> skills) {
        this.id=id; this.display=display; this.color=color; this.desc=desc; this.skills=skills;
    }
    public String id(){return id;}
    public String display(){return display;}
    public ChatFormatting color(){return color;}
    public String desc(){return desc;}
    public List<String> skills(){return skills;}
    public Component displayName(){ return Component.literal(display).withStyle(color); }
    public static EsperCategory byId(String id){ for(var c:values()) if(c.id.equalsIgnoreCase(id)) return c; return ELECTROMASTER; }
    public static EsperCategory pick(net.minecraft.util.RandomSource r){ return values()[r.nextInt(values().length)]; }
}
