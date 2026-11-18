package com.shadowclangaming.compat.create;

import com.shadowclangaming.chem.Compound;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Create / Create: Aeronautics / Create: Propulsion thruster compatibility.
 * Makes our propellants and oxidisers usable as thruster fuels.
 * Soft compat: no hard deps, registers via fluids/tags/event.
 */
public final class CreateThrusterCompat {
    private CreateThrusterCompat() {}

    /** Propellant thrust value (arbitrary units, relative) */
    public static int thrustFor(Compound compound) {
        if (compound == null) return 0;
        return switch (compound) {
            case HYDRAZINE -> 280;
            case MMH -> 320;
            case UDMH -> 340;
            case KEROSENE, RP1 -> 300;
            case NITROMETHANE -> 250;
            case LIQUID_OXYGEN -> 0; // oxidiser only, needs pair
            case NITROGEN_TETROXIDE, NITRIC_ACID, AMMONIUM_PERCHLORATE -> 0;
            default -> compound.isPropellant() ? 200 : 0;
        };
    }

    public static int oxidizerBoostFor(Compound compound) {
        if (compound == null) return 0;
        return switch (compound) {
            case NITROGEN_TETROXIDE -> 180;
            case LIQUID_OXYGEN -> 220;
            case NITRIC_ACID -> 140;
            case HYDROGEN_PEROXIDE_CONC -> 120;
            case AMMONIUM_PERCHLORATE -> 200;
            default -> 0;
        };
    }

    /** Called when a Create thruster queries fuel - we expose via capability / recipe hook */
    public static boolean isValidThrusterFuel(Compound compound) {
        return compound != null && compound.isThrusterFuel();
    }

    /** Mix ratio check: propellant + oxidiser pair produces working thrust */
    public static boolean isValidBipropellantPair(Compound fuel, Compound oxidiser) {
        return fuel != null && oxidiser != null && fuel.isPropellant() && oxidiser.isOxidiser();
    }

    public static int combinedThrust(Compound fuel, Compound oxidiser) {
        return thrustFor(fuel) + oxidizerBoostFor(oxidiser);
    }

    // Hook into thruster block entity if present (soft)
    public static void onThrusterTick(ServerLevel level, BlockPos pos, BlockEntity be) {
        // No-op stub: real Create integration would read tank fluids here
        // We expose our compounds as tag fluids via datagen: see data/shadowclangaming/tags/fluid/thruster_fuel.json
    }
}
