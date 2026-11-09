package com.sp.cca;

import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.cca.custom.entity.PlayerComponent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.ladysnake.cca.api.v3.component.ComponentKey;
import org.ladysnake.cca.api.v3.component.ComponentRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.entity.EntityComponentInitializer;
import org.ladysnake.cca.api.v3.world.WorldComponentFactoryRegistry;
import org.ladysnake.cca.api.v3.world.WorldComponentInitializer;

public class InitializeComponents implements WorldComponentInitializer, EntityComponentInitializer {
    public static final ComponentKey<WorldDestructionEventsComponent> EVENTS = ComponentRegistry.getOrCreate(
        com.sp.DestroyingMinecraft.idOf("events"), WorldDestructionEventsComponent.class);
    public static final ComponentKey<PlayerComponent> PLAYERS = ComponentRegistry.getOrCreate(
        com.sp.DestroyingMinecraft.idOf("players"), PlayerComponent.class);

    public static void register() {}

    @Override public void registerWorldComponentFactories(WorldComponentFactoryRegistry registry) {
        registry.registerForLevels(EVENTS, level -> new WorldDestructionEventsComponent(level));
    }
    @Override public void registerEntityComponentFactories(EntityComponentFactoryRegistry registry) {
        registry.registerForPlayers(PLAYERS, player -> new PlayerComponent());
    }
}
