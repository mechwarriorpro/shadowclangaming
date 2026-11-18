package com.sp.util.tickinstances.client;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * NeoForge port — Fabric ClientTickEvents -> NeoForge ClientTickEvent + LevelTickEvent
 * Original iterated EndClientTickInstances / EndClientWorldTickInstances; we preserve that API
 * but also tick destruction events directly so visuals work even if no other tick instances exist.
 */
public class ClientTickInstances {
    public static void registerAllClientTickInstances() {
        NeoForge.EVENT_BUS.addListener(ClientTickInstances::onClientTick);
        NeoForge.EVENT_BUS.addListener(ClientTickInstances::onLevelTick);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        for (EndClientTickInstances inst : EndClientTickInstances.getInstances()) inst.tickClient(mc);
    }

    private static void onLevelTick(LevelTickEvent.Post event) {
        Level level = event.getLevel();
        if (!level.isClientSide) return;
        for (EndClientWorldTickInstances inst : EndClientWorldTickInstances.getInstances()) inst.tickClientWorld(level);
        // Also tick world destruction component so client animations advance
        try { com.sp.cca.SimpleWorldStorage.get(level).tick(); } catch (Exception ignored) {}
    }
}
