package com.sp.destruction.server.custom;

import com.sp.cca.SimpleWorldStorage;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.destruction.DestructionType;
import com.sp.destruction.server.ServerDestructionEvent;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import com.sp.world.destructionevent.custom.BlackHoleDestruction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

public class BlackHoleDestructionServer extends ServerDestructionEvent {
    private static double prevGravityLerp;

    public BlackHoleDestructionServer() { super(DestructionType.BLACK_HOLE, 3400); }

    @Override protected KeyframeAnimation initAnimations(Level level) {
        WorldDestructionEventsComponent component = SimpleWorldStorage.get(level);
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration,
                new Keyframe(0.0),
                new Keyframe(155.0 / this.duration, () -> BlackHoleDestruction.setStartDestruction(true))
        ).globalAction((g, l) -> {
            double clamped = Math.floor(g * 10) * 0.1;
            if (clamped != prevGravityLerp) {
                component.setGravityLerp(clamped);
                component.syncLight();
                prevGravityLerp = clamped;
                // Braam sounds at 0.2/0.4/0.6/0.8 — stubbed when ModSounds missing
                if (Mth.equal((float) clamped, 0.2f) || Mth.equal((float) clamped, 0.4f) || Mth.equal((float) clamped, 0.6f) || Mth.equal((float) clamped, 0.8f)) {
                    for (var p : level.players()) if (p instanceof net.minecraft.server.level.ServerPlayer sp) try { com.sp.network.InitializePackets.sendBraam(sp, clamped); } catch (Exception ignored) {}
                }
            }
        }).endAction(() -> {
            prevGravityLerp = 0.0;
            component.setGravityLerp(1.2); component.syncLight();
            for (var p : level.players()) if (p instanceof net.minecraft.server.level.ServerPlayer sp) try { com.sp.network.InitializePackets.sendBraam(sp, 1.0); } catch (Exception ignored) {}
        }).build();
    }
}
