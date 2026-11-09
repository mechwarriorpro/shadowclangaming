package com.shadowclangaming.compat.create;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * Create machinery overstress during EMP.
 * Optional soft compat: no hard Create dependency at compile.
 * When EMP hits, kinetic blocks get overstressed (stress units exceeded) for durationTicks.
 */
public final class CreateStressCompat {
    private CreateStressCompat() {}

    public static boolean overstress(ServerLevel level, BlockPos pos, BlockEntity be, int durationTicks) {
        if (be == null) return false;
        String cls = be.getClass().getName().toLowerCase();
        // Heuristic detect Create kinetic / stress blocks
        boolean isCreate = cls.contains("create") || cls.contains("kinetic") || cls.contains("stress") || cls.contains("shaft") || cls.contains("gearbox") || cls.contains("encased");
        // Also check block id
        if (!isCreate) {
            var id = level.getBlockState(pos).getBlock().builtInRegistryHolder().key().location().toString().toLowerCase();
            if (id.contains("create:")) isCreate = true;
        }
        if (!isCreate) return false;

        try {
            // Try to set stress tag via persistent data (Create reads this in some versions)
            be.getPersistentData().putInt("emp:overstressTicks", durationTicks);
            be.getPersistentData().putBoolean("emp:overstressed", true);
            // Try reflection for KineticBlockEntity methods
            for (String m : new String[]{"setOverStressed", "overStress", "setStressed", "addStressImpact", "setStress"}) {
                try {
                    var method = be.getClass().getMethod(m, boolean.class);
                    method.invoke(be, true);
                    be.setChanged();
                    return true;
                } catch (NoSuchMethodException ignored) {}
                try {
                    var method = be.getClass().getMethod(m, int.class);
                    method.invoke(be, 1000000);
                    be.setChanged();
                    return true;
                } catch (Exception ignored) {}
            }
            // Also try to freeze speed
            for (String m : new String[]{"setSpeed", "setKineticSpeed"}) {
                try {
                    var method = be.getClass().getMethod(m, float.class);
                    method.invoke(be, 0.0f);
                    be.setChanged();
                } catch (Exception ignored) {}
            }
            be.setChanged();
            level.sendBlockUpdated(pos, level.getBlockState(pos), level.getBlockState(pos), 3);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
