package com.shadowclangaming.util;

import net.neoforged.fml.ModList;

/**
 * Optional-mod detection.
 *
 * ShadowClanGaming is built to work ALONE, but it is much more fun next to the Create stack.
 * Instead of hard-depending on those mods (which would crash the game if one is missing),
 * check for them at runtime and only then run the integration code.
 *
 * The mod ids below are correct for the published 1.21.1 builds at time of writing.
 * If an integration ever fails to trigger, dump the id list in-game with:
 *     /neoforge mods   (or look at the logs at startup)
 */
public final class SCGCompat {
    public static final String MODID_CREATE = "create";
    public static final String MODID_CREATE_AERONAUTICS = "aeronautics";
    public static final String MODID_CREATE_BIG_CANNONS = "createbigcannons";
    public static final String MODID_NUMISMATICS = "numismatics";
    public static final String MODID_NTM = "hbm";  // NTM: Decentralized Edition registers as "hbm"

    public static boolean create() {
        return isLoaded(MODID_CREATE);
    }

    public static boolean aeronautics() {
        return isLoaded(MODID_CREATE_AERONAUTICS);
    }

    public static boolean bigCannons() {
        return isLoaded(MODID_CREATE_BIG_CANNONS);
    }

    public static boolean numismatics() {
        return isLoaded(MODID_NUMISMATICS);
    }

    public static boolean ntm() {
        return isLoaded(MODID_NTM);
    }

    /**
     * Use this before touching any class from an optional mod.
     * Calling isLoaded() is always safe; referencing the other mod's classes is only safe
     * inside a branch that is guarded by one of these checks.
     */
    public static boolean isLoaded(String modId) {
        return ModList.get().isLoaded(modId);
    }

    private SCGCompat() {
    }
}
