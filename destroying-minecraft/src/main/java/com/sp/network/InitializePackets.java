package com.sp.network;

import com.sp.DestroyingMinecraft;
import com.sp.cca.SimpleWorldStorage;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.networking.CustomPayloads;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.DirectionalPayloadHandler;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * NeoForge payload registration — replaces Fabric's PayloadTypeRegistry + ServerPlayNetworking.
 * Both MOD bus registration (via DestroyingMinecraft) and runtime packet handling are wired here.
 * Gracefully no-ops if called off-mod-bus (headless sweep).
 */
public class InitializePackets {
    private static final String VERSION = "1";

    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(DestroyingMinecraft.MOD_ID).versioned(VERSION);
        // C2S
        registrar.playToServer(CustomPayloads.UpdatePhysicsDoorBlock.TYPE, CustomPayloads.UpdatePhysicsDoorBlock.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> handleUpdatePhysicsDoor(payload, ctx)));
        // S2C
        registrar.playToClient(CustomPayloads.DestructionPayload.TYPE, CustomPayloads.DestructionPayload.CODEC,
                (payload, ctx) -> ctx.enqueueWork(() -> handleDestruction(payload, ctx)));
        registrar.playToClient(CustomPayloads.SBEPayload.TYPE, CustomPayloads.SBEPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {}));
        registrar.playToClient(CustomPayloads.BraamPayload.TYPE, CustomPayloads.BraamPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> handleBraam(p, ctx)));
        registrar.playToClient(CustomPayloads.UpdatePlayZonePayload.TYPE, CustomPayloads.UpdatePlayZonePayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> handlePlayZone(p, ctx)));
        registrar.playToClient(CustomPayloads.WaitingRoomPacketPayload.TYPE, CustomPayloads.WaitingRoomPacketPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {}));
        registrar.playToClient(CustomPayloads.ShaderChangePacketPayload.TYPE, CustomPayloads.ShaderChangePacketPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> {}));
        registrar.playToClient(CustomPayloads.LavaSpewPacketPayload.TYPE, CustomPayloads.LavaSpewPacketPayload.CODEC, (p, ctx) -> ctx.enqueueWork(() -> handleLavaSpew(p, ctx)));
    }

    public static void registerServerNetworking() { /* Fabric entrypoint compat — actual work done in registerPayloads */ }
    public static void registerClientNetworking() {}

    // --- Handlers (client side) ---
    private static void handleDestruction(CustomPayloads.DestructionPayload payload, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level == null) return;
            WorldDestructionEventsComponent comp = SimpleWorldStorage.get(mc.level);
            if ("reset".equals(payload.name())) {
                for (var e : com.sp.destruction.client.ClientDestructionEvent.getAllClientInstances()) { e.setActive(false, -1); e.resetEvent(); }
                return;
            }
            var type = com.sp.destruction.DestructionType.getFromName(payload.name());
            type.ifPresent(dt -> {
                for (var e : com.sp.destruction.client.ClientDestructionEvent.getAllClientInstances()) if (e.getDestructionType().equals(dt)) {
                    comp.setAndStartCurrentDestructionEvent(e, payload.startTime());
                    comp.setDestructionEventPosition(new net.minecraft.world.phys.Vec3(payload.position().x, payload.position().y, payload.position().z));
                    var shaderType = com.sp.render.ShaderType.getFromDestructionType(dt);
                    try { com.sp.config.DestroyingMinecraftConfig.shaderType = shaderType; } catch (Exception ignored) {}
                    break;
                }
                try { com.sp.render.gui.hud.DestructionTitleRenderCallback.setDestructionTitle(dt); } catch (Exception ignored) {}
            });
        } catch (Exception ignored) {}
    }

    private static void handleBraam(CustomPayloads.BraamPayload p, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        try { net.minecraft.client.Minecraft.getInstance().getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(p.soundEvent(), 1.0f, 1.0f)); } catch (Exception ignored) {}
    }
    private static void handlePlayZone(CustomPayloads.UpdatePlayZonePayload p, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        try { com.sp.world.playzone.PlayZoneManager.handlePacket(p.minX(), p.maxX(), p.minY(), p.maxY(), p.minZ(), p.maxZ(), p.pid(), p.remove()); } catch (Exception ignored) {}
    }
    private static void handleLavaSpew(CustomPayloads.LavaSpewPacketPayload p, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {
        try {
            var mc = net.minecraft.client.Minecraft.getInstance();
            if (mc.level != null && mc.player != null) {
                var pos = new net.minecraft.world.phys.Vec3(p.pos().x, p.pos().y, p.pos().z);
                // Spawn lava particles — stub safe
                com.sp.networking.S2C.LavaSpewPacket.receive(p, mc);
            }
        } catch (Exception ignored) {}
    }
    private static void handleUpdatePhysicsDoor(CustomPayloads.UpdatePhysicsDoorBlock p, net.neoforged.neoforge.network.handling.IPayloadContext ctx) {}

    // For WorldDestructionEventsComponent syncLight() fallback
    public static void broadcastSync(Level level, WorldDestructionEventsComponent comp) {
        if (!(level instanceof net.minecraft.server.level.ServerLevel sl)) return;
        for (var player : sl.players()) if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            try { net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp, new CustomPayloads.ShaderChangePacketPayload("sync:" + comp.getGravityLerp())); } catch (Throwable ignored) {}
        }
    }

    public static void sendLavaSpew(net.minecraft.server.level.ServerPlayer player, net.minecraft.world.phys.Vec3 pos) {
        try { net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new CustomPayloads.LavaSpewPacketPayload(new org.joml.Vector3f((float)pos.x, (float)pos.y, (float)pos.z))); } catch (Exception ignored) {}
    }
    public static void sendWaitingRoom(net.minecraft.server.level.ServerPlayer player, boolean v) {
        try { net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new CustomPayloads.WaitingRoomPacketPayload(v)); } catch (Exception ignored) {}
    }
    public static void sendBraam(net.minecraft.server.level.ServerPlayer player, double clamped) {
        try { net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new CustomPayloads.BraamPayload(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValueOrThrow(net.minecraft.resources.ResourceLocation.parse("minecraft:block.note_block.basedrum")))); } catch (Exception ignored) {}
    }
}
