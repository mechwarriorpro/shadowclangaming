package com.shadowclangaming.compat.vs2;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;

/**
 * Valkyrien Skies 2 / Sable / Create Aeronautics subgrid EMP.
 * Affect ships/subgrids within EMP radius: drain their onboard energy,
 * freeze motion, overstress thrusters, shutdown onboard reactors.
 * Fully optional - reflection-safe.
 */
public final class SubgridEmpCompat {
    private SubgridEmpCompat() {}

    public static int empSubgrids(ServerLevel level, Vec3 pos, int radius, int durationTicks) {
        int hits = 0;
        // Try VS2 API via reflection if present
        try {
            // Valkyrien Skies: org.valkyrienskies.core.api.Ship
            Class<?> vsApi = Class.forName("org.valkyrienskies.mod.common.VSGameUtilsKt");
            // We don't have hard API, so fallback to entity scan for ship entities
            // Common ship entity names: "ship", "vs_ship", "sable_ship", "create_aeronautics"
            for (var entity : level.getAllEntities()) {
                if (entity == null) continue;
                String tn = entity.getType().getDescriptionId().toLowerCase();
                String cn = entity.getClass().getName().toLowerCase();
                boolean isShip = tn.contains("ship") || tn.contains("airship") || tn.contains("aeronautics") || cn.contains("ship") || cn.contains("sable");
                if (!isShip) continue;
                double ds = entity.position().distanceTo(pos);
                if (ds > radius) continue;
                // Drain FE from ship entity
                var cap = entity.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.ENTITY);
                if (cap != null) cap.extractEnergy(Integer.MAX_VALUE, false);
                // Freeze motion
                entity.setDeltaMovement(Vec3.ZERO);
                entity.setOnGround(true);
                entity.getPersistentData().putInt("emp:frozenTicks", durationTicks);
                hits++;
                // Try to drain block entities riding ship via VS2 getShipBlocks
                try {
                    var bePos = BlockPos.containing(entity.position());
                    var be = level.getBlockEntity(bePos);
                    if (be != null) {
                        var storage = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, bePos, null);
                        if (storage != null) storage.extractEnergy(Integer.MAX_VALUE, false);
                    }
                } catch (Exception ignored) {}
            }
        } catch (ClassNotFoundException e) {
            // VS2 not present, scan generically for any entity tagged as ship
            for (var entity : level.getAllEntities()) {
                if (entity == null) continue;
                String tn = entity.getType().getDescriptionId().toLowerCase();
                if (!tn.contains("ship") && !entity.getPersistentData().getBoolean("vs:ship")) continue;
                if (entity.position().distanceTo(pos) > radius) continue;
                entity.setDeltaMovement(Vec3.ZERO);
                entity.getPersistentData().putInt("emp:frozenTicks", durationTicks);
                hits++;
            }
        } catch (Exception ignored) {}

        // Also scan block entities that belong to subgrids via Sable API tags
        // Sable marks subgrid blocks with persistent data
        try {
            BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
            for (BlockPos p : BlockPos.betweenClosed(centre.offset(-radius, -2, -radius), centre.offset(radius, 2, radius))) {
                if (p.distSqr(centre) > (long)radius*radius) continue;
                var be = level.getBlockEntity(p);
                if (be == null) continue;
                if (be.getPersistentData().contains("sable:subgrid") || be.getPersistentData().contains("vs:shipId")) {
                    var storage = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, p, null);
                    if (storage != null) storage.extractEnergy(Integer.MAX_VALUE, false);
                    com.shadowclangaming.compat.create.CreateStressCompat.overstress(level, p, be, durationTicks);
                    com.shadowclangaming.compat.extremereactors.ExtremeReactorCompat.shutdownReactor(level, p, be);
                    hits++;
                }
            }
        } catch (Exception ignored) {}

        return hits;
    }
}
