package com.shadowclangaming.esper;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;

@EventBusSubscriber(modid="shadowclangaming")
public final class EsperEvents {
    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e){
        if(!(e.getEntity() instanceof ServerPlayer sp)) return;
        var data = EsperHolder.get(sp);
        // If first time (level 1 and only one skill and no NBT marker), prompt class selection
        // We use persistent data flag "esper_prompted"
        if(!sp.getPersistentData().getBoolean("esper_prompted")){
            sp.getPersistentData().putBoolean("esper_prompted", true);
            sp.sendSystemMessage(Component.literal("Choose your Esper class: use /esper choose <electromaster|teleporter|vector|meltdowner|graviton|pyrokinetic|aerohand|mental_out>").withStyle(ChatFormatting.AQUA), false);
            sp.sendSystemMessage(Component.literal("Upgrade at Ability Chamber with XP levels. Skill cards are craftable.").withStyle(ChatFormatting.GRAY), false);
            // give a free selector book as item (use existing book texture)
            // we use a paper named Class Selector that runs command on use — simplified: just message
        }
    }
}
