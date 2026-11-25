package com.sp.item.custom;

import com.sp.cca.SimpleWorldStorage;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.destruction.DestructionEvent;
import com.sp.destruction.DestructionType;
import com.sp.destruction.server.ServerDestructionEvent;
import com.sp.networking.CustomPayloads;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

public class DestructionTriggerItem extends Item {
    private final DestructionType type;
    public DestructionTriggerItem(Properties props, DestructionType type) { super(props); this.type = type; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            BlockPos pos = player.blockPosition();
            Vec3 vec = Vec3.atCenterOf(pos);
            WorldDestructionEventsComponent comp = SimpleWorldStorage.get(sl);
            for (DestructionEvent e : ServerDestructionEvent.getAllServerInstances()) {
                if (e.getDestructionType().equals(type)) {
                    comp.setAndStartCurrentDestructionEvent(e, sl.getGameTime());
                    comp.setDestructionEventPosition(vec);
                    break;
                }
            }
            for (ServerPlayer p : sl.players()) {
                PacketDistributor.sendToPlayer(p, new CustomPayloads.DestructionPayload(type.getName(), sl.getGameTime(), new org.joml.Vector3f((float)vec.x, (float)vec.y, (float)vec.z)));
            }
            player.displayClientMessage(Component.literal("Triggered " + type.getName() + " at " + pos.toShortString()), false);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
