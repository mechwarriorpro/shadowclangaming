package com.shadowclangaming.block;

import com.shadowclangaming.launcher.LauncherLevel;
import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A support rod: the thing that stops a level 5 launcher from tearing itself off its foundation.
 *
 * It only stores a tier. The launcher counts them within 6 blocks and takes the LOWEST tier, so two
 * tier 5 rods and one tier 1 rod is a tier 1 launcher - build all of them the same or do not bother.
 */
public class SupportRodBlockEntity extends BlockEntity {

    private int tier = 1;

    public SupportRodBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.SUPPORT_ROD.get(), pos, state);
    }

    public int tier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = Math.max(LauncherLevel.MIN, Math.min(LauncherLevel.MAX, tier));
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Tier", tier);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        tier = Math.max(1, Math.min(LauncherLevel.MAX, tag.getInt("Tier")));
    }
}
