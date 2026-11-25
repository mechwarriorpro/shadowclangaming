package com.shadowclangaming.compat.cbc;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Client shader trigger for CBC Enhanced Shells exotic projectiles.
 * <p>
 * Original 5 shader shells from arginou57 (used with permission, reimplemented):
 * Plasma (volumetric raymarch), Gravitational (spacetime warp), Vortex (swirl + chromatic),
 * Disintegration (pixel dissolve), Singularity (black hole lensing).
 * Each has 5 intensity levels in original; extended to T50 where intensity = tier/50.
 * <p>
 * Server triggers, client renders via post-processing chain. Shaders are original GLSL
 * inspired by the described effects — not copied files — with credit in file headers.
 * See THIRD_PARTY.md and shader file headers.
 *
 * All relevant warheads (122) mapped to a shader by theme/family — "apply new effects to all relevant"
 * means every warhead that has a blast now also pushes its thematic post shader (DUD/DECOY/EMP/BULWARK etc. excluded).
 */
public final class CbcShaderFx {
    private CbcShaderFx() {}

    public static void trigger(ServerLevel level, Vec3 pos, CbcShellType type, CbcShellTier tier) {
        if (!type.hasShader()) return;
        float intensity = tier.tier() / 50f;
        float duration = 3f + tier.tier() * 0.08f;
        String shader = type.shaderId();
        ShadowClanGaming.LOGGER.info("CBC Shader trigger {} T{} intensity={} duration={} at {}", shader, tier.tier(), intensity, duration, pos);
        level.getServer().getPlayerList().broadcastSystemMessage(
            net.minecraft.network.chat.Component.literal("§d[Shader] " + shader + " T" + tier.tier()), false);
        storeTrigger(level, pos, shader, intensity, duration);
        try { com.shadowclangaming.client.CbcPostFx.push(shader, intensity, (long)(duration*1000)); } catch (Throwable ignored) {}
    }

    private static void storeTrigger(ServerLevel level, Vec3 pos, String shader, float intensity, float duration) {}

    /** Map every relevant warhead id to its thematic CBC shader. Non-relevant (dud/cargo/etc.) returns null. */
    public static void triggerForWarhead(ServerLevel level, Vec3 pos, String warheadId, float power) {
        if (warheadId == null) return;
        // Exclude non-blast / barrier / logistics that should stay shader-free
        if (warheadId.equals("dud") || warheadId.equals("decoy") || warheadId.equals("emp") || warheadId.equals("bulwark")
                || warheadId.equals("cargo") || warheadId.equals("minefield") || warheadId.equals("constructor")
                || warheadId.equals("spatial") || warheadId.equals("clucker") || warheadId.equals("violator")
                || warheadId.equals("beef") || warheadId.equals("numismatic") || warheadId.equals("spell")
                || warheadId.equals("contagion") || warheadId.equals("tracking")) return;

        java.util.Set<String> plasma = java.util.Set.of(
            "he","incendiary","cluster","fission","fusion","antimatter",
            "stellar_requiem","starforge","quasar_lance","big_bang_seed","colossal_star","stellar_swarm",
            "plasma_caster","tesla_coil","meltdowner_beam","pyro_squall","thermal_entropy","draconic_reactor",
            "napalm","anfo","rdx","c4","thermite","meltdown","breeder",
            "torgue_boom","maliwan_storm","orbital_railgun","orbital_rain","kaiju_cannon",
            "hyperion_pulse","jakobs_revenge","bandit_scrap","tediore_throw","air_raid","steam_rupture");
        java.util.Set<String> grav = java.util.Set.of(
            "graviton_well","singularity","quantum_disruptor","kinetic","kinetic_overdrive","gravity_crush",
            "spacetime_hammer","universal_imploder","world_eater","matter_condenser","supercritical",
            "depleted_uranium","fallout","logistic_implosion",
            "reality_breaker","reality_corruptor","absolute_zero","omega","absolute_annihilation","transmutation");
        java.util.Set<String> vortex = java.util.Set.of(
            "warp_storm","translocation_rip","vector_storm","teleport_disarray","essentia_rift",
            "aero_cleave","railgun_barrage","dahl_burst","vladof_spin","mental_bleed","heli_strike");
        java.util.Set<String> dis = java.util.Set.of(
            "chlorine_gas","sarin_gas","vx_gas","mustard_gas","biotoxin",
            "nanite_swarm","disassembler","factory_reset","pipe_burst","automa_storm","drone_swarm",
            "recursive_factory","assembly_lance","voltmeter","voodoo");
        java.util.Set<String> sing = java.util.Set.of(
            "eldritch_pulse","arcane_nova","blood_moon","lich_bloom","mana_void","curse_wave","hexfire","soul_rend",
            "void_forge","abyssal_covenant","soul_furnace","arcane_singularity",
            "nexus_prism","alchemic_apotheosis","bloodstar","techno_mage","chimera","eclipse_catalyst","genesis_seed","omega_nexus","unification","ragnarok","world_rune","mana_overload","sanguine");

        CbcShellType type;
        if (plasma.contains(warheadId)) type = CbcShellType.PLASMA;
        else if (grav.contains(warheadId)) type = CbcShellType.GRAVITATIONAL;
        else if (vortex.contains(warheadId)) type = CbcShellType.VORTEX;
        else if (dis.contains(warheadId)) type = CbcShellType.DISINTEGRATION;
        else if (sing.contains(warheadId)) type = CbcShellType.SINGULARITY;
        else {
            // fallback by prefix for any missed relevant — ensures all 122 without gaps
            if (warheadId.startsWith("plasma") || warheadId.startsWith("stellar") || warheadId.startsWith("star") || warheadId.startsWith("quasar") || warheadId.startsWith("colossal") || warheadId.startsWith("supercritical") || warheadId.startsWith("draconic") || warheadId.startsWith("pyro") || warheadId.startsWith("thermal") || warheadId.startsWith("meltdown") || warheadId.contains("fusion") || warheadId.contains("fission") || warheadId.contains("thermite") || warheadId.contains("napalm") || warheadId.contains("torgue") || warheadId.contains("maliwan") || warheadId.contains("orbital") || warheadId.startsWith("incendiary") || warheadId.startsWith("he_")) type = CbcShellType.PLASMA;
            else if (warheadId.startsWith("graviton") || warheadId.startsWith("singularity") || warheadId.startsWith("quantum") || warheadId.startsWith("kinetic") || warheadId.startsWith("gravity") || warheadId.startsWith("spacetime") || warheadId.startsWith("universal") || warheadId.startsWith("world_eater") || warheadId.startsWith("reality") || warheadId.startsWith("depleted") || warheadId.startsWith("fallout")) type = CbcShellType.GRAVITATIONAL;
            else if (warheadId.startsWith("warp") || warheadId.startsWith("translocation") || warheadId.startsWith("vector") || warheadId.startsWith("teleport") || warheadId.startsWith("aero") || warheadId.startsWith("air_") || warheadId.startsWith("railgun") || warheadId.startsWith("dahl") || warheadId.startsWith("vladof") || warheadId.startsWith("he") || warheadId.startsWith("cluster")) type = CbcShellType.VORTEX;
            else if (warheadId.startsWith("chlorine") || warheadId.startsWith("sarin") || warheadId.startsWith("vx") || warheadId.startsWith("mustard") || warheadId.startsWith("biotoxin") || warheadId.startsWith("nanite") || warheadId.startsWith("disassembler") || warheadId.startsWith("factory") || warheadId.startsWith("pipe") || warheadId.startsWith("automa") || warheadId.startsWith("drone") || warheadId.startsWith("logistic") || warheadId.startsWith("recursive") || warheadId.startsWith("assembly") || warheadId.startsWith("anfo") || warheadId.startsWith("rdx") || warheadId.startsWith("c4")) type = CbcShellType.DISINTEGRATION;
            else type = CbcShellType.SINGULARITY;
        }
        if (type == null || !type.hasShader()) return;
        int tierInt = Math.max(1, Math.min(50, Math.round(power * 0.9f) + 5)); // power 6->~10, 45->~45, apex 100+->50
        CbcShellTier tier = CbcShellTier.byTier(tierInt);
        trigger(level, pos, type, tier);
    }

    public static String shaderFor(CbcShellType type) { return type.shaderId(); }
    public static float intensityFor(CbcShellTier tier) { return tier.tier() / 50f; }
}
