package com.shadowclangaming.block;

import com.shadowclangaming.magic.ManaApi;
import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * One second, one ring of particles, one mana payment per player standing inside.
 *
 * The circle is the only regen source that is a *place* rather than a state, which is what makes it
 * the interesting one: you have to go back to it, and it can be found and broken.
 */
public class CircleBlockEntity extends BlockEntity {

    /** Pulse interval. Mana is handed out on the pulse, so rates are per second. */
    public static final int PULSE_TICKS = 20;

    private boolean active;

    public CircleBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.CIRCLE.get(), pos, state);
    }

    public boolean isBlood() {
        return getBlockState().getBlock() instanceof BloodCircleBlock;
    }

    public boolean isActive() {
        if (!isBlood()) {
            return true;
        }
        if (active) {
            return true;
        }
        BlockState state = getBlockState();
        return state.hasProperty(BloodCircleBlock.ACTIVE) && state.getValue(BloodCircleBlock.ACTIVE);
    }

    public void setActive(boolean active) {
        this.active = active;
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CircleBlockEntity circle) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (serverLevel.getGameTime() % PULSE_TICKS != 0L) {
            return;
        }

        CircleBlock block = (CircleBlock) state.getBlock();
        int size = state.hasProperty(CircleBlock.SIZE) ? state.getValue(CircleBlock.SIZE) : 0;

        if (block.isBlood()) {
            // Blood circles pay no mana - the pact does, anywhere in the world. This is only the site.
            if (circle.isActive()) {
                block.ringParticles(serverLevel, pos, 6.0D, true);
            } else {
                block.ringParticles(serverLevel, pos, 2.5D, true);
            }
            return;
        }

        double radius = CircleBlock.CHALK_RADII[Math.min(size, CircleBlock.CHALK_RADII.length - 1)];
        long rate = CircleBlock.CHALK_MANA_PER_SECOND[Math.min(size, CircleBlock.CHALK_MANA_PER_SECOND.length - 1)];
        block.ringParticles(serverLevel, pos, radius, false);

        double centreX = pos.getX() + 0.5D;
        double centreZ = pos.getZ() + 0.5D;
        AABB area = new AABB(
                centreX - radius, pos.getY() - 1.0D, centreZ - radius,
                centreX + radius, pos.getY() + 2.5D, centreZ + radius);

        for (ServerPlayer player : serverLevel.getPlayers(p -> true)) {
            if (!area.contains(player.getX(), player.getY(), player.getZ())) {
                continue;
            }
            // Must be actually inside the ring, not merely in its bounding box.
            double dx = player.getX() - centreX;
            double dz = player.getZ() - centreZ;
            if (dx * dx + dz * dz > radius * radius) {
                continue;
            }
            if (ManaApi.isEvil(serverLevel.getServer(), player.getUUID())) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.shadowclangaming.chalk_refused"), true);
                continue;
            }
            long granted = ManaApi.grantMana(player, rate);
            if (granted > 0L) {
                player.displayClientMessage(
                        net.minecraft.network.chat.Component.translatable(
                                "message.shadowclangaming.mana_gain",
                                String.format("%,d", granted),
                                String.format("%,d", ManaApi.current(player)),
                                String.format("%,d", ManaApi.capacity(player))), true);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Active", active);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        active = tag.getBoolean("Active");
    }
}
