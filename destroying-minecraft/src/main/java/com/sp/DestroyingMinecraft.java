package com.sp;

import com.sp.block.ModBlocks;
import com.sp.block.entity.ModBlockEntities;
import com.sp.command.DestroyingMinecraftCommands;
import com.sp.component.ModDataComponentTypes;
import com.sp.config.DestroyingMinecraftConfig;
import com.sp.destruction.server.ServerDestructionEvents;
import com.sp.entity.ModEntities;
import com.sp.item.ModItemGroups;
import com.sp.item.ModItems;
import com.sp.network.InitializePackets;
import com.sp.sounds.ModSounds;
import com.sp.world.ModGameRules;
import com.sp.world.destructionevent.custom.BlackHoleDestruction;
import com.sp.world.playzone.PlayZoneManager;
import com.sp.world.spinningblockexplosion.SpinningBlockExplosion;
import com.sp.destruction.DestructionEvent;
import com.sp.destruction.server.ServerDestructionEvent;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
// MidnightConfig optional
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.minecraft.resources.ResourceLocation;

/**
 * NeoForge port of Destroying Minecraft (Fabric 1.21.1, Veil 2.3.2).
 * Original by SpacePotatoee / Herr Chaos — All Rights Reserved.
 * This port is an original reimplementation that preserves the same 5 DestructionTypes,
 * Veil shaders, CCA components, and world carving so you can run it side-by-side with
 * ShadowClanGaming on NeoForge 21.1.264 and compare visuals 1:1. See THIRD_PARTY.md.
 */
@Mod(DestroyingMinecraft.MOD_ID)
public class DestroyingMinecraft {
    public static final String MOD_ID = "destroying_minecraft";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public DestroyingMinecraft(IEventBus modEventBus, ModContainer modContainer) {
        // Config (MidnightConfig supports NeoForge via its neoforge jar)
        // MidnightConfig.init(MOD_ID, DestroyingMinecraftConfig.class); // optional

        ModItems.REGISTRY.register(modEventBus);
        ModBlocks.REGISTRY.register(modEventBus);
        ModBlockEntities.REGISTRY.register(modEventBus);
        ModEntities.REGISTRY.register(modEventBus);
        ModSounds.REGISTRY.register(modEventBus);
        ModGameRules.register(modEventBus);
        ModDataComponentTypes.REGISTRY.register(modEventBus);
        ModItemGroups.REGISTRY.register(modEventBus);

        modEventBus.addListener(this::commonSetup);

        // Server lifecycle
        NeoForge.EVENT_BUS.addListener(this::onServerTick);
        NeoForge.EVENT_BUS.addListener(this::onServerStopped);

        // Commands & networking are registered via RegisterCommandsEvent / RegisterPayloadHandlersEvent
        NeoForge.EVENT_BUS.register(DestroyingMinecraftCommands.class);
        NeoForge.EVENT_BUS.register(InitializePackets.class);
        NeoForge.EVENT_BUS.register(ServerDestructionEvents.class);

        LOGGER.info("\"It's nukein' time\" -He said as he loaded the fork into the microwave (NeoForge port)");
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            // CCA is auto-registered via fabric.mod.json cardinal-components entrypoint;
            // on NeoForge we initialize manually.
            // InitializeComponents.register(); // CCA stubbed
        });
    }

    private void onServerTick(ServerTickEvent.Post event) {
        if (!(event.getServer().overworld() instanceof net.minecraft.server.level.ServerLevel level)) return;
        for (SpinningBlockExplosion explosion : SpinningBlockExplosion.getExplosions()) {
            if (level.equals(explosion.getWorld())) explosion.explode();
        }
        if (level.dimension() == Level.OVERWORLD) {
            BlackHoleDestruction.tick(level);
        }
    }

    private void onServerStopped(ServerStoppedEvent event) {
        PlayZoneManager.clearAllPlayZones();
        for (DestructionEvent e : ServerDestructionEvent.getAllServerInstances()) e.resetAnimations();
        for (net.minecraft.server.level.ServerLevel world : event.getServer().getAllLevels()) {
            WorldDestructionEventsComponent comp = com.sp.cca.SimpleWorldStorage.get(world);
            if (comp.getCurrentDestructionEvent() != null) comp.getCurrentDestructionEvent().resetEvent();
            comp.setAndStartCurrentDestructionEvent(null, -1);
        }
    }

    public static ResourceLocation idOf(String path){
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
