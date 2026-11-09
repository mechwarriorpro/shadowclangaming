package com.shadowclangaming.block;

import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A mine that remembers what it replaced.
 *
 * ──────────────────── WHY MIMICRY NEEDS STATE ────────────────────
 *
 * A landmine is only useful if you cannot see it. The mine is therefore placed INTO the ground - it takes
 * the place of the block that was there (dirt, stone, sand, deepslate...) - and the client draws that same
 * block in its place. Nothing about "dirt" hints that there is a mine under your feet until it goes off.
 *
 * The block it replaced is stored here, synced to the client (via the update tag), because the renderer
 * needs to know what to draw. Players wearing Night Vision see through the disguise instead: the renderer
 * switches to the mine's own model, which is the trade-off that keeps the field discoverable with the
 * right preparation.
 */
public class MineBlockEntity extends BlockEntity {

    /** The block this mine is pretending to be. Null until the world writes it. */
    private BlockState mimic;

    public MineBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.MINE.get(), pos, state);
    }

    /** What to draw. Falls back to dirt so a mine with no stored state is still hidden, not invisible. */
    public BlockState mimic() {
        return mimic == null ? Blocks.DIRT.defaultBlockState() : mimic;
    }

    public void setMimic(BlockState state) {
        this.mimic = state;
        setChanged();
        // The client needs the new disguise immediately; sendBlockUpdated also rebuilds the chunk mesh.
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_ALL);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (mimic != null) {
            tag.put("Mimic", NbtUtils.writeBlockState(mimic));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Mimic")) {
            mimic = NbtUtils.readBlockState(
                    registries.lookupOrThrow(Registries.BLOCK), tag.getCompound("Mimic"));
        }
    }

    /** The disguise travels to the client in the same packet as the block entity itself. */
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
