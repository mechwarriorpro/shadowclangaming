package com.shadowclangaming.compat.orbitalrailgun;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;

/**
 * Orbital Railgun compat — clean-room.
 * If a mod providing an orbital strike is present, delegate to it via reflection.
 * Otherwise return false so WarheadType does its vanilla scaled beam fallback.
 * Power is already scaled by SCGConfig.
 */
public final class OrbitalRailgunCompat {
    private OrbitalRailgunCompat() {}

    public static boolean tryOrbitalStrike(ServerLevel level, Vec3 pos, float power) {
        // Try known mod ids from 1.6.4 era and modern forks. Add more as discovered.
        String[] modIds = {"orbitalrailgun", "orbital_railgun", "railgun", "orailgun", "advancedrocketry", "icbmclassic"};
        boolean anyLoaded = false;
        for (String id : modIds) {
            try {
                if (ModList.get().isLoaded(id)) { anyLoaded = true; break; }
            } catch (Throwable ignored) {}
        }
        if (!anyLoaded) return false;

        // Try reflective hooks — each mod has different entry points; try several.
        String[] candidates = {
                "com.railgunmod.OrbitalStrikeHandler",
                "com.orbitalrailgun.OrbitalRailgun",
                "net.mcheli.weapon.MCH_WeaponOrbital",
                "icbm.classic.content.explosive.blast.BlastOrbital"
        };
        for (String cls : candidates) {
            try {
                Class<?> c = Class.forName(cls);
                // try static trigger(ServerLevel, Vec3, float)
                try {
                    var m = c.getMethod("trigger", ServerLevel.class, Vec3.class, float.class);
                    Object r = m.invoke(null, level, pos, power);
                    if (r instanceof Boolean b && b) return true;
                    return true; // if no exception, consider handled
                } catch (NoSuchMethodException ignored) {}
                try {
                    var m2 = c.getMethod("fire", ServerLevel.class, Vec3.class, float.class);
                    m2.invoke(null, level, pos, power);
                    return true;
                } catch (NoSuchMethodException ignored) {}
            } catch (Throwable ignored) {}
        }
        // If mod is loaded but no reflective hook matched, do scaled vanilla beam but report handled
        // so we don't double-strike. Mod's own beam will still be triggered via its event if it listens.
        return false;
    }
}
