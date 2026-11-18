package com.sp.networking;

import com.sp.render.ShaderType;
import com.sp.world.playzone.PlayZone;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * NeoForge port — Fabric ServerPlayNetworking.send() -> PacketDistributor.sendToPlayer()
 */
public class ServerPacketManager {
    private static void send(Player player, CustomPacketPayload payload) {
        if (player instanceof ServerPlayer sp) PacketDistributor.sendToPlayer(sp, payload);
    }

    public static void sendPointSBEPacket(Player player, Vec3 pos, int radius) { send(player, new CustomPayloads.SBEPayload(pos.toVector3f(), radius)); }
    public static void sendBraamPacket(Player player, SoundEvent soundEvent) { send(player, new CustomPayloads.BraamPayload(soundEvent)); }
    public static void sendBraamPacket(Player player, double clamped) {
        // Fallback when we only have clamped double — pick a placeholder braam
        try { send(player, new CustomPayloads.BraamPayload(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "block.note_block.basedrum")))); } catch (Exception ignored) {}
    }
    public static void sendUpdatePlayZonePacket(Player player, PlayZone playZone, boolean remove) {
        AABB b = playZone.getBoundingBox();
        send(player, new CustomPayloads.UpdatePlayZonePayload(b.minX, b.maxX, b.minY, b.maxY, b.minZ, b.maxZ, playZone.getId(), remove));
    }
    public static void sendWaitingRoomPacket(Player player, boolean inWaitingRoom) { send(player, new CustomPayloads.WaitingRoomPacketPayload(inWaitingRoom)); }
    public static void sendShaderChangePacket(Player player, ShaderType shaderType) { send(player, new CustomPayloads.ShaderChangePacketPayload(shaderType.getSerializedName())); }
    public static void sendLavaSpewPacket(Player player, Vec3 pos) { send(player, new CustomPayloads.LavaSpewPacketPayload(pos.toVector3f())); }

    // Broadcast helpers used by WorldDestructionEventsComponent / commands
    public static void sendDestruction(ServerPlayer player, String name, long startTime, Vec3 pos) {
        PacketDistributor.sendToPlayer(player, new CustomPayloads.DestructionPayload(name, startTime, pos.toVector3f()));
    }
}
