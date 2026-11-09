package com.sp.command;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public class DestroyingMinecraftCommands {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        // /destroying-minecraft <type> — wired to ServerDestructionEvent.create(type, level, pos).start()
    }
}
