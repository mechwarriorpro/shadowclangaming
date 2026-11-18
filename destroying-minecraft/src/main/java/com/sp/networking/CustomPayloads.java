package com.sp.networking;

import com.sp.DestroyingMinecraft;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import org.joml.Vector3f;

/**
 * NeoForge port of Fabric CustomPayloads — uses CustomPacketPayload + StreamCodec.
 * Keeps same IDs so shaders/pipelines still match (assets reference same namespaces).
 */
public class CustomPayloads {
    private static ResourceLocation id(String p) { return ResourceLocation.fromNamespaceAndPath(DestroyingMinecraft.MOD_ID, p); }

    public record DestructionPayload(String name, long startTime, Vector3f position) implements CustomPacketPayload {
        public static final Type<DestructionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DestroyingMinecraft.MOD_ID, "destruction"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DestructionPayload> CODEC = StreamCodec.of((buf, v) -> { buf.writeUtf(v.name); buf.writeLong(v.startTime); buf.writeFloat(v.position.x); buf.writeFloat(v.position.y); buf.writeFloat(v.position.z); }, buf -> new DestructionPayload(buf.readUtf(), buf.readLong(), new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SBEPayload(Vector3f position, int radius) implements CustomPacketPayload {
        public static final Type<SBEPayload> TYPE = new Type<>(id("sbe"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SBEPayload> CODEC = StreamCodec.of((buf, v) -> { buf.writeFloat(v.position.x); buf.writeFloat(v.position.y); buf.writeFloat(v.position.z); buf.writeInt(v.radius); }, buf -> new SBEPayload(new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readInt()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BraamPayload(SoundEvent soundEvent) implements CustomPacketPayload {
        public static final Type<BraamPayload> TYPE = new Type<>(id("asp"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BraamPayload> CODEC = StreamCodec.of(
                (buf, v) -> buf.writeResourceLocation(v.soundEvent().getLocation()),
                buf -> new BraamPayload(net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.getValue(buf.readResourceLocation())));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdatePlayZonePayload(double minX, double maxX, double minY, double maxY, double minZ, double maxZ, String pid, boolean remove) implements CustomPacketPayload {
        public static final Type<UpdatePlayZonePayload> TYPE = new Type<>(id("update_playzone"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePlayZonePayload> CODEC = StreamCodec.of((buf, v) -> { buf.writeDouble(v.minX); buf.writeDouble(v.maxX); buf.writeDouble(v.minY); buf.writeDouble(v.maxY); buf.writeDouble(v.minZ); buf.writeDouble(v.maxZ); buf.writeUtf(v.pid); buf.writeBoolean(v.remove); }, buf -> new UpdatePlayZonePayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WaitingRoomPacketPayload(boolean inWaitingRoom) implements CustomPacketPayload {
        public static final Type<WaitingRoomPacketPayload> TYPE = new Type<>(id("waiting_room"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WaitingRoomPacketPayload> CODEC = StreamCodec.of((buf, v) -> buf.writeBoolean(v.inWaitingRoom), buf -> new WaitingRoomPacketPayload(buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ShaderChangePacketPayload(String shaderName) implements CustomPacketPayload {
        public static final Type<ShaderChangePacketPayload> TYPE = new Type<>(id("shader_change"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShaderChangePacketPayload> CODEC = StreamCodec.of((buf, v) -> buf.writeUtf(v.shaderName), buf -> new ShaderChangePacketPayload(buf.readUtf()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LavaSpewPacketPayload(Vector3f pos) implements CustomPacketPayload {
        public static final Type<LavaSpewPacketPayload> TYPE = new Type<>(id("lava_spew"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LavaSpewPacketPayload> CODEC = StreamCodec.of((buf, v) -> { buf.writeFloat(v.pos.x); buf.writeFloat(v.pos.y); buf.writeFloat(v.pos.z); }, buf -> new LavaSpewPacketPayload(new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat())));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdatePhysicsDoorBlock(BlockPos blockEntityPos, BlockPos corner1, BlockPos corner2, Direction direction, int numOfBlocks, int speed, boolean showSelection, boolean playSound) implements CustomPacketPayload {
        public static final Type<UpdatePhysicsDoorBlock> TYPE = new Type<>(id("updatephysdoorblk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePhysicsDoorBlock> CODEC = StreamCodec.of((buf, v) -> { buf.writeBlockPos(v.blockEntityPos); buf.writeBlockPos(v.corner1); buf.writeBlockPos(v.corner2); buf.writeEnum(v.direction); buf.writeInt(v.numOfBlocks); buf.writeInt(v.speed); buf.writeBoolean(v.showSelection); buf.writeBoolean(v.playSound); }, buf -> new UpdatePhysicsDoorBlock(buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos(), buf.readEnum(Direction.class), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean()));
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
