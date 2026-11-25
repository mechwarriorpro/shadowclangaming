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

public class CustomPayloads {
    private static ResourceLocation id(String p) { return ResourceLocation.fromNamespaceAndPath(DestroyingMinecraft.MOD_ID, p); }

    public record DestructionPayload(String name, long startTime, Vector3f position) implements CustomPacketPayload {
        public static final Type<DestructionPayload> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(DestroyingMinecraft.MOD_ID, "destruction"));
        public static final StreamCodec<RegistryFriendlyByteBuf, DestructionPayload> CODEC = new StreamCodec<>() {
            public DestructionPayload decode(RegistryFriendlyByteBuf buf) { return new DestructionPayload(buf.readUtf(), buf.readLong(), new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat())); }
            public void encode(RegistryFriendlyByteBuf buf, DestructionPayload v) { buf.writeUtf(v.name); buf.writeLong(v.startTime); buf.writeFloat(v.position.x); buf.writeFloat(v.position.y); buf.writeFloat(v.position.z); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record SBEPayload(Vector3f position, int radius) implements CustomPacketPayload {
        public static final Type<SBEPayload> TYPE = new Type<>(id("sbe"));
        public static final StreamCodec<RegistryFriendlyByteBuf, SBEPayload> CODEC = new StreamCodec<>() {
            public SBEPayload decode(RegistryFriendlyByteBuf buf) { return new SBEPayload(new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readInt()); }
            public void encode(RegistryFriendlyByteBuf buf, SBEPayload v) { buf.writeFloat(v.position.x); buf.writeFloat(v.position.y); buf.writeFloat(v.position.z); buf.writeInt(v.radius); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record BraamPayload(SoundEvent soundEvent) implements CustomPacketPayload {
        public static final Type<BraamPayload> TYPE = new Type<>(id("asp"));
        public static final StreamCodec<RegistryFriendlyByteBuf, BraamPayload> CODEC = new StreamCodec<>() {
            public BraamPayload decode(RegistryFriendlyByteBuf buf) { return new BraamPayload(SoundEvent.createVariableRangeEvent(buf.readResourceLocation())); }
            public void encode(RegistryFriendlyByteBuf buf, BraamPayload v) { buf.writeResourceLocation(v.soundEvent().getLocation()); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdatePlayZonePayload(double minX, double maxX, double minY, double maxY, double minZ, double maxZ, String pid, boolean remove) implements CustomPacketPayload {
        public static final Type<UpdatePlayZonePayload> TYPE = new Type<>(id("update_playzone"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePlayZonePayload> CODEC = new StreamCodec<>() {
            public UpdatePlayZonePayload decode(RegistryFriendlyByteBuf buf) { return new UpdatePlayZonePayload(buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readUtf(), buf.readBoolean()); }
            public void encode(RegistryFriendlyByteBuf buf, UpdatePlayZonePayload v) { buf.writeDouble(v.minX); buf.writeDouble(v.maxX); buf.writeDouble(v.minY); buf.writeDouble(v.maxY); buf.writeDouble(v.minZ); buf.writeDouble(v.maxZ); buf.writeUtf(v.pid); buf.writeBoolean(v.remove); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record WaitingRoomPacketPayload(boolean inWaitingRoom) implements CustomPacketPayload {
        public static final Type<WaitingRoomPacketPayload> TYPE = new Type<>(id("waiting_room"));
        public static final StreamCodec<RegistryFriendlyByteBuf, WaitingRoomPacketPayload> CODEC = new StreamCodec<>() {
            public WaitingRoomPacketPayload decode(RegistryFriendlyByteBuf buf) { return new WaitingRoomPacketPayload(buf.readBoolean()); }
            public void encode(RegistryFriendlyByteBuf buf, WaitingRoomPacketPayload v) { buf.writeBoolean(v.inWaitingRoom); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record ShaderChangePacketPayload(String shaderName) implements CustomPacketPayload {
        public static final Type<ShaderChangePacketPayload> TYPE = new Type<>(id("shader_change"));
        public static final StreamCodec<RegistryFriendlyByteBuf, ShaderChangePacketPayload> CODEC = new StreamCodec<>() {
            public ShaderChangePacketPayload decode(RegistryFriendlyByteBuf buf) { return new ShaderChangePacketPayload(buf.readUtf()); }
            public void encode(RegistryFriendlyByteBuf buf, ShaderChangePacketPayload v) { buf.writeUtf(v.shaderName); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record LavaSpewPacketPayload(Vector3f pos) implements CustomPacketPayload {
        public static final Type<LavaSpewPacketPayload> TYPE = new Type<>(id("lava_spew"));
        public static final StreamCodec<RegistryFriendlyByteBuf, LavaSpewPacketPayload> CODEC = new StreamCodec<>() {
            public LavaSpewPacketPayload decode(RegistryFriendlyByteBuf buf) { return new LavaSpewPacketPayload(new Vector3f(buf.readFloat(), buf.readFloat(), buf.readFloat())); }
            public void encode(RegistryFriendlyByteBuf buf, LavaSpewPacketPayload v) { buf.writeFloat(v.pos.x); buf.writeFloat(v.pos.y); buf.writeFloat(v.pos.z); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public record UpdatePhysicsDoorBlock(BlockPos blockEntityPos, BlockPos corner1, BlockPos corner2, Direction direction, int numOfBlocks, int speed, boolean showSelection, boolean playSound) implements CustomPacketPayload {
        public static final Type<UpdatePhysicsDoorBlock> TYPE = new Type<>(id("updatephysdoorblk"));
        public static final StreamCodec<RegistryFriendlyByteBuf, UpdatePhysicsDoorBlock> CODEC = new StreamCodec<>() {
            public UpdatePhysicsDoorBlock decode(RegistryFriendlyByteBuf buf) { return new UpdatePhysicsDoorBlock(buf.readBlockPos(), buf.readBlockPos(), buf.readBlockPos(), buf.readEnum(Direction.class), buf.readInt(), buf.readInt(), buf.readBoolean(), buf.readBoolean()); }
            public void encode(RegistryFriendlyByteBuf buf, UpdatePhysicsDoorBlock v) { buf.writeBlockPos(v.blockEntityPos); buf.writeBlockPos(v.corner1); buf.writeBlockPos(v.corner2); buf.writeEnum(v.direction); buf.writeInt(v.numOfBlocks); buf.writeInt(v.speed); buf.writeBoolean(v.showSelection); buf.writeBoolean(v.playSound); }
        };
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }
}
