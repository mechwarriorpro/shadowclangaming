package com.shadowclangaming.computer;

import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A cable. It stores one number - the channel it carries - and that is the whole object.
 *
 * Cables carry a channel rather than an identity because a base is normally wired on one channel, and
 * a channel number is something a player can see, set with the same Frequency Chip they already use
 * for pads and terminals. Two cables touch → they are the same wire, as long as the numbers match.
 */
public class NetworkCableBlockEntity extends BlockEntity {

    private int channel = com.shadowclangaming.computer.Channels.NONE;

    public NetworkCableBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.NETWORK_CABLE.get(), pos, state);
    }

    public int channel() {
        return channel;
    }

    public void setChannel(int channel) {
        channel = Channels.sanitise(channel);
        this.channel = channel;
        setChanged();
        if (level != null && !level.isClientSide) {
            // Re-render both sides of the connection so the cable's model can show its arms.
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Channel", channel);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        channel = tag.contains("Channel") ? tag.getInt("Channel") : -1;
    }
}
