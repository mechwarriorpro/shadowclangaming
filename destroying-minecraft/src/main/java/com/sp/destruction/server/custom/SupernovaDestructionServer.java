package com.sp.destruction.server.custom;

import com.sp.cca.SimpleWorldStorage;
import com.sp.destruction.DestructionType;
import com.sp.destruction.server.ServerDestructionEvent;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SupernovaDestructionServer extends ServerDestructionEvent {
    public SupernovaDestructionServer() { super(DestructionType.SUPERNOVA, 2930); }

    @Override protected KeyframeAnimation initAnimations(Level level) {
        var component = SimpleWorldStorage.get(level);
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration,
                new Keyframe(0.0),
                new Keyframe(2880.0 / this.duration, () -> {
                    try {
                        var explosion = new com.sp.world.spinningblockexplosion.custom.DirectionalSBE(50, 80, -90, 0.2f, component.getDestructionEventPosition());
                        if (level instanceof ServerLevel sl) explosion.beginExplosion(sl);
                    } catch (Exception ignored) {}
                })
        ).endAction(() -> {
            for (var p : level.players()) if (p instanceof net.minecraft.server.level.ServerPlayer sp) try { com.sp.network.InitializePackets.sendWaitingRoom(sp, true); } catch (Exception ignored) {}
            this.setActive(false, -1); this.resetEvent();
        }).build();
    }
}
