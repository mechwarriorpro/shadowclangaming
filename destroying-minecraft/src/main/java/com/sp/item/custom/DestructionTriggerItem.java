package com.sp.item.custom;

import com.sp.destruction.DestructionType;
import com.sp.destruction.server.ServerDestructionEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Creative-only trigger: starts the DestructionType at the player's feet.
 * Server carving + Veil shader are driven by ServerDestructionEvent.
 */
public class DestructionTriggerItem extends Item {
    private final DestructionType type;
    public DestructionTriggerItem(Properties props, DestructionType type) { super(props); this.type = type; }
    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide && level instanceof ServerLevel sl) {
            BlockPos pos = player.blockPosition();
            ServerDestructionEvent event = ServerDestructionEvent.create(type, sl, pos);
            event.start();
            player.displayClientMessage(Component.literal("Triggered " + type.getName() + " at " + pos.toShortString()), false);
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }
}
