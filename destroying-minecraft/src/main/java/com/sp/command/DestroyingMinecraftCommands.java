package com.sp.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class DestroyingMinecraftCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        DestructionCommand.register(event.getDispatcher());
        try { RipPlatformOutCommand.register(event.getDispatcher()); } catch (Exception ignored) {}
        try { PlayersCommand.register(event.getDispatcher()); } catch (Exception ignored) {}
    }
}
