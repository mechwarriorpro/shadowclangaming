package com.shadowclangaming.launcher;

import com.shadowclangaming.entity.MissileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.*;

/**
 * Pending interdimensional warheads that could not be delivered because the destination
 * dimension was not loaded at the moment the missile reached orbit.
 *
 * The ascending missile queues itself here and is retried once per second from the server tick.
 * If the dimension appears, the drop spawns; if the server stops, the queue clears.
 */
public final class InterdimensionalTransit {

    private record Pending(
            net.minecraft.resources.ResourceKey<Level> targetDimension,
            BlockPos target,
            net.minecraft.world.item.ItemStack item,
            com.shadowclangaming.warhead.WarheadType warhead,
            float power,
            double cruise,
            long funding,
            com.shadowclangaming.spell.Spell spell,
            List<net.minecraft.world.item.ItemStack> cargo,
            java.util.UUID tracked,
            int slider,
            long seed,
            BlockPos origin) {}

    private static final List<Pending> QUEUE = new ArrayList<>();

    private InterdimensionalTransit() {}

    public static void queue(MissileEntity missile) {
        if (missile.getTargetDimension() == null || missile.getTarget() == null) return;
        QUEUE.add(new Pending(
                missile.getTargetDimension(), missile.getTarget(), missile.getItem().copy(),
                missile.getWarhead(), missile.getExplosionPower(), missile.getCruiseSpeed(),
                missile.getFunding(), missile.getSpell(), missile.getCargo(),
                missile.getTrackedEntity(), missile.getSliderRadius(), missile.getBuildSeed(),
                missile.getOriginPos()));
    }

    // Accessors needed for queue — add getters to MissileEntity via reflection helper
    // We add package-private helpers in MissileEntity instead; here we use public getters we will add.

    public static void tick(MinecraftServer server) {
        if (QUEUE.isEmpty()) return;
        Iterator<Pending> it = QUEUE.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            ServerLevel dest = server.getLevel(p.targetDimension());
            if (dest == null) continue;
            Vec3 spawn = new Vec3(p.target().getX() + 0.5D, MissileEntity.INTERDIMENSIONAL_REENTRY_HEIGHT, p.target().getZ() + 0.5D);
            MissileEntity drop = new MissileEntity(dest,
                    spawn.x, spawn.y, spawn.z, p.target(), p.item(), p.warhead(), p.power(),
                    p.cruise(), p.funding(), p.spell(), p.cargo(), p.tracked(), null, p.slider(), p.seed());
            drop.setPhase(MissileEntity.Phase.REENTRY);
            drop.setPos(spawn.x, spawn.y, spawn.z);
            drop.setDeltaMovement(0.0D, -MissileEntity.INTERDIMENSIONAL_REENTRY_SPEED, 0.0D);
            dest.addFreshEntity(drop);
            it.remove();
        }
    }

    public static void clear() { QUEUE.clear(); }
    public static int size() { return QUEUE.size(); }
}
