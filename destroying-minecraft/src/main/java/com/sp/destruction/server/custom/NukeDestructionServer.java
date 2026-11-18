package com.sp.destruction.server.custom;

import com.sp.cca.SimpleWorldStorage;
import com.sp.destruction.DestructionType;
import com.sp.destruction.server.ServerDestructionEvent;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import net.minecraft.world.level.Level;

/**
 * NeoForge port — original Fabric had no dedicated Nuke server event (nuke was a simple explosion).
 * We provide a 1200-tick timeline that triggers a PointSBE crater so the 5th DestructionType is functional.
 */
public class NukeDestructionServer extends ServerDestructionEvent {
    public NukeDestructionServer() { super(DestructionType.NUKE, 1200); }

    @Override protected KeyframeAnimation initAnimations(Level level) {
        var component = SimpleWorldStorage.get(level);
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration,
                new Keyframe(0.0),
                new Keyframe(200.0 / this.duration, () -> {
                    try {
                        // Small directional explosion + fallout — mirrors original SpinningBlockExplosion usage
                        var sbe = new com.sp.world.spinningblockexplosion.custom.PointSBE(30, 0.3f, component.getDestructionEventPosition());
                        if (level instanceof net.minecraft.server.level.ServerLevel sl) sbe.beginExplosion(sl);
                    } catch (Exception ignored) {}
                }),
                new Keyframe(1100.0 / this.duration)
        ).endAction(() -> {
            for (var p : level.players()) if (p instanceof net.minecraft.server.level.ServerPlayer sp) try { com.sp.network.InitializePackets.sendWaitingRoom(sp, true); } catch (Exception ignored) {}
        }).build();
    }
}
