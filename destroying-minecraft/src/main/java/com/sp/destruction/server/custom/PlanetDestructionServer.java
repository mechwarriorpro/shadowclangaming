package com.sp.destruction.server.custom;

import com.sp.cca.SimpleWorldStorage;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.destruction.DestructionType;
import com.sp.destruction.server.ServerDestructionEvent;
import com.sp.entity.ModEntities;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class PlanetDestructionServer extends ServerDestructionEvent {
    private int meteorCooldown;
    private int trackingMeteorCooldown = 300;
    private final RandomSource random = RandomSource.create();

    public PlanetDestructionServer() { super(DestructionType.PLANET, 2400); }

    @Override protected KeyframeAnimation initAnimations(Level level) {
        WorldDestructionEventsComponent component = SimpleWorldStorage.get(level);
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration,
                new Keyframe(0.0),
                new Keyframe(320.0 / this.duration, (g, l) -> {
                    trackingMeteorCooldown--;
                    if (meteorCooldown-- >= 0) return;
                    double rx = (random.nextDouble() * 2.0 - 1.0) * 100;
                    double rz = (random.nextDouble() * 2.0 - 1.0) * 100;
                    try {
                        var meteor = ModEntities.METEOR_ENTITY.get().create(level);
                        if (meteor != null) {
                            Vec3 center = component.getDestructionEventPosition();
                            meteor.setPos(center.add(rx + 120, 120, rz));
                            level.addFreshEntity(meteor);
                        }
                        if (trackingMeteorCooldown <= 0) {
                            for (var p : level.players()) {
                                if (!p.canBeHitByProjectile()) continue;
                                var tm = ModEntities.METEOR_ENTITY.get().create(level);
                                if (tm != null) { tm.setPos(p.position().add(120, 120, 0)); level.addFreshEntity(tm); }
                            }
                            trackingMeteorCooldown = random.nextIntBetweenInclusive(200, 300);
                        }
                    } catch (Exception ignored) {}
                    meteorCooldown = random.nextIntBetweenInclusive(2, 5);
                }),
                new Keyframe(1800.0 / this.duration),
                new Keyframe(2350.0 / this.duration, () -> {
                    Vec3 avg = Vec3.ZERO; int count = 0;
                    for (var p : level.players()) if (!p.isCreative() && !p.isSpectator()) { avg = avg.add(p.position()); count++; }
                    if (count == 0) return;
                    avg = avg.scale(1.0 / count);
                    try {
                        var sbe = new com.sp.world.spinningblockexplosion.custom.DirectionalSBE(50, 50, -90, 0.2f, new Vec3(avg.x, 67, avg.z));
                        if (level instanceof ServerLevel sl) sbe.beginExplosion(sl);
                    } catch (Exception ignored) {}
                })
        ).endAction(() -> {
            for (var p : level.players()) if (p instanceof net.minecraft.server.level.ServerPlayer sp) try { com.sp.network.InitializePackets.sendWaitingRoom(sp, true); } catch (Exception ignored) {}
        }).build();
    }
}
