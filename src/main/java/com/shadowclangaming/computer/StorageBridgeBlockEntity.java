package com.shadowclangaming.computer;

import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The storage bridge: a block that sits against an AE2 network (or a chest, if AE2 is not installed)
 * and lets the terminal read it.
 *
 * It exists so the reading happens where the player put it, not wherever the nearest AE2 block happens
 * to be. A terminal with the Storage Card, a cable run, and a bridge against the ME drive is a system
 * you can point at.
 */
public class StorageBridgeBlockEntity extends BlockEntity {

    /** Last read, cached so /term storage twice in a row does not scan twice. */
    private String lastNote = "never read";
    private int lastEntries;

    public StorageBridgeBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.STORAGE_BRIDGE.get(), pos, state);
    }

    public String lastNote() {
        return lastNote;
    }

    public int lastEntries() {
        return lastEntries;
    }

    public void record(Ae2Access.Report report) {
        this.lastNote = report.note();
        this.lastEntries = report.entries().size();
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("LastNote", lastNote);
        tag.putInt("LastEntries", lastEntries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        lastNote = tag.getString("LastNote");
        lastEntries = tag.getInt("LastEntries");
    }
}
