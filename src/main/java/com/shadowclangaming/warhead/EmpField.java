package com.shadowclangaming.warhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.util.RandomSource;

import java.util.Iterator;
import java.util.ArrayList;
import java.util.List;

/**
 * Giant blue EMP pulse field that lasts 30 seconds (600 ticks).
 * No block damage — purely visual + energy drain loop.
 * Handles giant blue pulse with chaotic blue/white lightning randomly (not spiral).
 */
public final class EmpField {
    private static final List<Active> FIELDS = new ArrayList<>();

    private static final class Active {
        final ServerLevel level;
        final Vec3 pos;
        final int radius;
        int ticksLeft;
        int tickCounter = 0;
        Active(ServerLevel level, Vec3 pos, int radius, int ticks) {
            this.level = level; this.pos = pos; this.radius = radius; this.ticksLeft = ticks;
        }
    }

    public static void create(ServerLevel level, Vec3 pos, int radius, int durationTicks) {
        FIELDS.add(new Active(level, pos, radius, durationTicks));
    }

    /** Called from main ServerTick listener */
    public static void tick(net.minecraft.server.MinecraftServer server) {
        if (FIELDS.isEmpty()) return;
        Iterator<Active> it = FIELDS.iterator();
        while (it.hasNext()) {
            Active f = it.next();
            if (f.level.hasChunkAt(BlockPos.containing(f.pos.x, f.pos.y, f.pos.z)) == false) {
                // level may be unloading; still tick down
            }
            f.ticksLeft--;
            f.tickCounter++;
            ServerLevel level = f.level;
            Vec3 pos = f.pos;
            int radius = f.radius;

            // Every 10 ticks: re-drain, re-shutdown, chaotic lightning
            if (f.tickCounter % 10 == 0) {
                BlockPos centre = BlockPos.containing(pos.x, pos.y, pos.z);
                // re-drain in radius
                for (BlockPos p : BlockPos.betweenClosed(centre.offset(-radius, -1, -radius), centre.offset(radius, 1, radius))) {
                    if (p.distSqr(centre) > (long)radius*radius) continue;
                    BlockEntity be = level.getBlockEntity(p);
                    if (be == null) continue;
                    // drain FE via capability (re-energized machines get drained again)
                    var storage = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, p, null);
                    if (storage == null) for (var d : net.minecraft.core.Direction.values()) {
                        storage = level.getCapability(net.neoforged.neoforge.capabilities.Capabilities.EnergyStorage.BLOCK, p, d);
                        if (storage != null) break;
                    }
                    if (storage != null) storage.extractEnergy(Integer.MAX_VALUE, false);
                    com.shadowclangaming.compat.extremereactors.ExtremeReactorCompat.shutdownReactor(level, p, be);
                    com.shadowclangaming.compat.create.CreateStressCompat.overstress(level, p, be, 20);
                }
                // chaotic blue/white lightning particles randomly placed, not spiral
                RandomSource rng = level.getRandom();
                for (int i=0;i<6;i++) {
                    double ox = (rng.nextDouble()-0.5)*radius*0.9;
                    double oz = (rng.nextDouble()-0.5)*radius*0.9;
                    double oy = (rng.nextDouble()-0.5)*4;
                    Vec3 s = new Vec3(pos.x+ox, pos.y+oy, pos.z+oz);
                    var particle = (i%2==0) ? ParticleTypes.ELECTRIC_SPARK : ParticleTypes.END_ROD;
                    com.shadowclangaming.perf.FxBudget.send(level, particle, s, 3, 0.2, 0.2, 0.2, 0.01);
                }
                // blue pulse ring
                com.shadowclangaming.perf.FxBudget.send(level, ParticleTypes.SOUL_FIRE_FLAME, pos, 6, radius*0.45, 0.8, radius*0.45, 0.02);
                // entity effect refresh
                AABB box = new AABB(pos, pos).inflate(radius);
                for (Entity e : level.getEntities(null, box)) {
                    if (e instanceof net.minecraft.world.entity.LivingEntity le) {
                        le.addEffect(new net.minecraft.world.effect.MobEffectInstance(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN, 30, 0));
                    }
                }
            }
            // periodic rumble
            if (f.tickCounter % 60 == 0) {
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.6F, 1.6F + level.getRandom().nextFloat()*0.4F);
                try { Class.forName("com.shadowclangaming.compat.vs2.SubgridEmpCompat").getMethod("empSubgrids", ServerLevel.class, Vec3.class, int.class, int.class).invoke(null, level, pos, radius, 20); } catch(Throwable ignored){}
            }

            if (f.ticksLeft <= 0) it.remove();
        }
    }

    private EmpField() {}
}
