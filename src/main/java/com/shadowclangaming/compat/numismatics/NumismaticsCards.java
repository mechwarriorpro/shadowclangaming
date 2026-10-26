package com.shadowclangaming.compat.numismatics;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.block.LaunchPadBlockEntity;
import com.shadowclangaming.item.BankCardItem;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Makes a Create: Numismatics Bank Card work on OUR launch pad.
 *
 * WHY AN EVENT AND NOT AN ITEM. Their card is their item; we cannot add a useOn() branch to it. But
 * NeoForge fires {@code PlayerInteractEvent.RightClickBlock} before the item's own useOn runs, so we
 * can watch for "a Numismatics card was right-clicked on one of our pads" and pay for the warhead
 * ourselves. Returning SUCCESS and cancelling keeps their card from also doing something.
 *
 * The player's mental model is the part that matters: if a card pays at a Vendor, it should pay at a
 * launch pad. Which account gets billed is decided by Money/NumismaticsBank - a card bound to a shared
 * Blaze Banker account bills that account, not the holder's pocket.
 */
public final class NumismaticsCards {

    private NumismaticsCards() {
    }

    public static void register() {
        NeoForge.EVENT_BUS.addListener((PlayerInteractEvent.RightClickBlock event) -> {
            if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
                return;
            }
            if (!(event.getLevel() instanceof ServerLevel level)) {
                return;
            }
            ItemStack held = event.getItemStack();
            if (!NumismaticsBank.isCard(held)) {
                return;
            }
            if (!(level.getBlockEntity(event.getPos()) instanceof LaunchPadBlockEntity pad)) {
                return;
            }
            // -1 = "exactly enough for the next radius step": a foreign card has no tier setting of
            // ours to read, and the next step is the cheapest thing that visibly does something.
            InteractionResult result = BankCardItem.fund(level, player, held, pad, -1L);
            event.setCancellationResult(result);
            event.setCanceled(true);
            if (result == InteractionResult.SUCCESS) {
                ShadowClanGaming.LOGGER.debug("Numismatics card funded a warhead at {}", event.getPos());
            }
        });
    }
}
