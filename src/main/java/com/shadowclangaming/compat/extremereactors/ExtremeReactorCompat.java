package com.shadowclangaming.compat.extremereactors;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Extreme Reactors / BiggerReactors compatibility - shutdown reactors during EMP.
 * Optional: uses reflection / soft class check so no hard dependency.
 * If BiggerReactors / Extreme Reactors is present, their reactor controllers have
 * an "active" or "setActive" state; we force them off via block property or BE method.
 */
public final class ExtremeReactorCompat {
    private ExtremeReactorCompat() {}

    public static boolean shutdownReactor(ServerLevel level, BlockPos pos, BlockEntity be) {
        if (be == null) return false;
        String cls = be.getClass().getName().toLowerCase();
        // Heuristic: classes containing reactor / biggerreactors
        if (!cls.contains("reactor") && !cls.contains("bigger")) return false;
        try {
            // Try common method names via reflection
            for (String m : new String[]{"setActive", "setReactorActive", "setPowered", "shutdown", "scram", "setEnabled"}) {
                try {
                    var method = be.getClass().getMethod(m, boolean.class);
                    method.invoke(be, false);
                    be.setChanged();
                    // Also try to update block property "active" -> false
                    var state = level.getBlockState(pos);
                    for (var prop : state.getProperties()) {
                        if (prop.getName().equals("active") || prop.getName().equals("powered") || prop.getName().equals("enabled")) {
                            try {
                                @SuppressWarnings("unchecked")
                                var p = (net.minecraft.world.level.block.state.properties.BooleanProperty) prop;
                                if (state.hasProperty(p) && state.getValue(p)) {
                                    level.setBlock(pos, state.setValue(p, false), 3);
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                    return true;
                } catch (NoSuchMethodException ignored) {}
            }
            // Fallback: try boolean field
            for (var f : be.getClass().getDeclaredFields()) {
                if (f.getType() == boolean.class && (f.getName().equalsIgnoreCase("active") || f.getName().equalsIgnoreCase("enabled"))) {
                    f.setAccessible(true);
                    if (f.getBoolean(be)) {
                        f.setBoolean(be, false);
                        be.setChanged();
                        return true;
                    }
                }
            }
            // If we detected a reactor but couldn't find toggle, still count as hit and force block entity nbt tag
            be.getPersistentData().putBoolean("emp:shutdown", true);
            be.setChanged();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Unobtainium fuel handling (500x power, 100x lifetime)
    public static final String FUEL_UNOBTAINIUM = "unobtainium";
    public static final double POWER_MULTIPLIER = 500.0;
    public static final double LIFETIME_MULTIPLIER = 100.0;

    /** Called by BiggerReactors API if present - we expose via datapack/compat hook */
    public static double getFuelPowerMultiplier(String fuelId) {
        if (FUEL_UNOBTAINIUM.equalsIgnoreCase(fuelId)) return POWER_MULTIPLIER;
        return 1.0;
    }
    public static double getFuelLifetimeMultiplier(String fuelId) {
        if (FUEL_UNOBTAINIUM.equalsIgnoreCase(fuelId)) return LIFETIME_MULTIPLIER;
        return 1.0;
    }
}
