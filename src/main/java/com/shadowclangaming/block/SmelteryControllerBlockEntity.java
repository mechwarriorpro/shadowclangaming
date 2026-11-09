package com.shadowclangaming.block;

import com.shadowclangaming.smeltery.SmelteryTank;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import com.shadowclangaming.registry.SCGBlockEntities;

public class SmelteryControllerBlockEntity extends BlockEntity {
    private final SmelteryTank tank = new SmelteryTank();
    private boolean formed = false;
    private int heatCheckCooldown = 20;
    private int alloyCooldown = 5;

    public SmelteryControllerBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.SMELTERY_CONTROLLER.get(), pos, state);
    }

    public SmelteryTank getTank() { return tank; }
    public boolean isFormed() { return formed; }

    public void tick(ServerLevel level) {
        if (heatCheckCooldown-- <= 0) {
            heatCheckCooldown = 20;
            // Simple multiblock check: look for lava or fire below controller within 3 blocks
            boolean hasHeat = false;
            for (int dy = -1; dy >= -4; dy--) {
                BlockPos p = worldPosition.offset(0, dy, 0);
                var s = level.getBlockState(p);
                if (s.is(Blocks.LAVA) || s.is(Blocks.MAGMA_BLOCK) || s.is(Blocks.CAMPFIRE) || s.getBlock().toString().toLowerCase().contains("seared_heater")) {
                    hasHeat = true; break;
                }
            }
            // Multiblock formed if surrounding has at least 6 seared bricks
            int bricks = 0;
            for (int dx=-2; dx<=2; dx++) for (int dz=-2; dz<=2; dz++) for (int dy=-1; dy<=2; dy++) {
                BlockPos q = worldPosition.offset(dx, dy, dz);
                var b = level.getBlockState(q).getBlock();
                String id = b.builtInRegistryHolder().key().location().toString();
                if (id.contains("seared") || b == Blocks.BRICKS) bricks++;
            }
            formed = hasHeat || bricks >= 6; // lenient for standalone testing
            tank.setCapacity( 4 * 1296 + bricks * 720 ); // expand with size
            if (hasHeat) tank.setTemperature(Math.max(tank.temperature(), 1400));
            else if (formed) tank.setTemperature(Math.max(500, tank.temperature()));
            else tank.setTemperature(Math.max(0, tank.temperature() - 5));
        }
        if (alloyCooldown-- <= 0) {
            alloyCooldown = 5;
            if (formed) {
                boolean alloyed = tank.tryAlloy();
                if (alloyed) setChanged();
            }
        }
        // Passive cooling if not heated
        if (!formed && level.getGameTime() % 40 == 0) {
            if (tank.temperature() > 20) tank.setTemperature(tank.temperature() - 10);
        }
    }
}
