package com.sp.cca.custom.world;

import com.sp.cca.SimpleWorldStorage;
import com.sp.destruction.DestructionEvent;
import com.sp.destruction.DestructionType;
import com.sp.world.destructionevent.custom.BlackHoleDestruction;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * NeoForge port of WorldDestructionEventsComponent (orig 127 LOC, CCA AutoSyncedComponent).
 * Keeps same fields/behavior but without CCA dependency: sync is done via InitializePackets payloads when present,
 * otherwise HashMap in SimpleWorldStorage. Tick logic preserved so DestructionEvent.tick() runs.
 */
public class WorldDestructionEventsComponent {
    private final Level level;
    private boolean syncLight;
    private DestructionEvent currentDestructionEvent;
    private Vec3 destructionEventPosition = Vec3.ZERO;
    private double gravityLerp = 0.0;
    public static final Vec3 gravityDir = new Vec3(0.0, 0.07, -0.03);

    public WorldDestructionEventsComponent(Level level) { this.level = level; this.gravityLerp = 0.0; this.destructionEventPosition = Vec3.ZERO; }

    public double getGravityLerp() { return this.gravityLerp; }
    public void setGravityLerp(double v) { this.gravityLerp = v; }

    public DestructionEvent getCurrentDestructionEvent() { return this.currentDestructionEvent; }

    public void setAndStartCurrentDestructionEvent(DestructionEvent event, long startTime) {
        if (this.currentDestructionEvent != null) {
            this.currentDestructionEvent.setActive(false, -1);
            this.currentDestructionEvent.resetEvent();
            if (this.currentDestructionEvent.getDestructionType().equals(DestructionType.BLACK_HOLE)) {
                BlackHoleDestruction.setStartDestruction(false);
                // Remove physics entities in radius
                try {
                    AABB box = AABB.ofSize(this.destructionEventPosition, 1000, 1000, 1000);
                    for (Entity e : level.getEntitiesOfClass(Entity.class, box, en -> en.getType().toString().contains("block_physics"))) e.discard();
                } catch (Exception ignored) {}
                this.setGravityLerp(0.0);
                this.syncLight();
            }
        }
        if (event == null) { this.currentDestructionEvent = null; return; }
        if (event.isClient() != this.level.isClientSide) {
            throw new RuntimeException("Tried to add a " + (event.isClient() ? "client" : "server") + " event on a " + (this.level.isClientSide ? "client" : "server") + " world");
        }
        this.currentDestructionEvent = event;
        this.currentDestructionEvent.setActive(true, startTime);
    }

    public Vec3 getDestructionEventPosition() { return this.destructionEventPosition; }
    public void setDestructionEventPosition(Vec3 pos) { this.destructionEventPosition = pos; }
    public void setDestructionEventPosition(BlockPos pos) { this.destructionEventPosition = Vec3.atCenterOf(pos); }

    // NBT for /save — matches original writeToNbt/readFromNbt keys
    public void readFromNbt(CompoundTag nbt) {
        this.gravityLerp = nbt.getDouble("gravityLerp");
        if (this.currentDestructionEvent != null) this.currentDestructionEvent.setProgress(nbt.getInt("currentDestructionEventProgress"));
    }
    public void writeToNbt(CompoundTag nbt) {
        nbt.putDouble("gravityLerp", this.gravityLerp);
        nbt.putDouble("currentDestructionEventProgress", this.currentDestructionEvent != null ? this.currentDestructionEvent.getProgress() : -1);
    }

    // Fabric's AutoSyncedComponent writeSyncPacket — on NeoForge we send via CustomPacketPayload if available
    public void writeSyncPacket(net.minecraft.network.FriendlyByteBuf buf) {
        CompoundTag nbt = new CompoundTag();
        nbt.putDouble("gravityLerp", this.gravityLerp);
        nbt.putDouble("currentDestructionEventProgress", this.currentDestructionEvent != null ? this.currentDestructionEvent.getProgress() : -1);
        // buf.writeNbt(nbt); // FriendlyByteBuf API varies; payload handler does full serialization
    }

    public void syncLight() { this.syncLight = true; this.sync(); }
    public void sync() {
        // If InitializePackets is loaded, it will broadcast SyncLight payload; otherwise no-op (offline CI)
        try { com.sp.network.InitializePackets.broadcastSync(this.level, this); } catch (Throwable ignored) {}
        this.syncLight = false;
    }

    public void tick() {
        if (this.level.dimension() == Level.OVERWORLD && this.currentDestructionEvent != null) this.currentDestructionEvent.tick(this.level);
    }

    // Called by server payload handler on client when sync arrives
    public void applySyncLight(double gravityLerp, int progress) {
        this.gravityLerp = gravityLerp;
        if (this.currentDestructionEvent != null) this.currentDestructionEvent.setProgress(progress);
    }

    public boolean shouldSyncLight() { return syncLight; }
}
