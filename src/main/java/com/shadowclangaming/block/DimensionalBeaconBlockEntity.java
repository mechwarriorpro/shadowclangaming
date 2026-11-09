package com.shadowclangaming.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class DimensionalBeaconBlockEntity extends BlockEntity {
    private String label = "";

    public DimensionalBeaconBlockEntity(BlockPos pos, BlockState state) {
        super(com.shadowclangaming.registry.SCGBlockEntities.DIMENSIONAL_BEACON.get(), pos, state);
    }

    public String getLabel() { return label.isEmpty() ? "Beacon " + worldPosition.toShortString() : label; }
    public void setLabel(String l) { label = l; setChanged(); }

    @Override public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel sl) com.shadowclangaming.launcher.DimensionalBeaconRegistry.register(sl, worldPosition);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.saveAdditional(tag, r);
        tag.putString("Label", label);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider r) {
        super.loadAdditional(tag, r);
        label = tag.getString("Label");
    }

    public static void tick(ServerLevel level, BlockPos pos) {
        // Light FX every second — cheap
        if (level.getGameTime() % 20 == 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.PORTAL,
                    pos.getX() + 0.5D, pos.getY() + 1.2D, pos.getZ() + 0.5D, 6, 0.4D, 0.3D, 0.4D, 0.03D);
        }
    }
}
