package com.sp.destruction.server.custom;

import com.sp.cca.SimpleWorldStorage;
import com.sp.cca.custom.world.WorldDestructionEventsComponent;
import com.sp.destruction.DestructionType;
import com.sp.destruction.server.ServerDestructionEvent;
import com.sp.util.MathUtil;
import com.sp.util.keyframes.Keyframe;
import com.sp.util.keyframes.KeyframeAnimation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class LaserDestructionServer extends ServerDestructionEvent {
    private static final RandomSource random = RandomSource.create();
    private static final float radius = 23;
    private static int lavaSpewDelay = 200;
    public static float laserLength;
    public static float crackingTime;

    public LaserDestructionServer() { super(DestructionType.ORBITAL_LASER, 2500); }

    @Override public void resetEvent() { crackingTime = 0.0f; laserLength = 0.0f; super.resetEvent(); }

    @Override protected KeyframeAnimation initAnimations(Level level) {
        WorldDestructionEventsComponent component = SimpleWorldStorage.get(level);
        return new KeyframeAnimation.KeyframeAnimationBuilder(this.duration,
                new Keyframe(0.0),
                new Keyframe(484.0 / this.duration, () -> laserLength = 1.0f),
                new Keyframe(700.0 / this.duration, (g, l) -> {
                    crackingTime = (float) l;
                    if (lavaSpewDelay <= 0) {
                        float maxRadius = crackingTime * radius;
                        Vec3 offset = new Vec3(MathUtil.nextBetween(-maxRadius, maxRadius), 0, MathUtil.nextBetween(-maxRadius, maxRadius)).add(component.getDestructionEventPosition());
                        for (ServerPlayer p : level.players().stream().filter(e -> e instanceof ServerPlayer).map(e -> (ServerPlayer)e).toList()) {
                            try { com.sp.network.InitializePackets.sendLavaSpew(p, offset); } catch (Exception ignored) {}
                        }
                        try { level.playSound(null, offset.x, offset.y, offset.z, com.sp.sounds.ModSounds.LAVA_SPEW.get(), SoundSource.AMBIENT, 10.0f, MathUtil.nextBetween(0.8f, 1.2f)); } catch (Exception ignored) {}
                        lavaSpewDelay = random.nextIntBetweenInclusive(30, 100);
                    } else lavaSpewDelay--;
                    int playerCount = 0;
                    List<net.minecraft.world.entity.player.Player> players = level.players();
                    if (players.size() > 1) {
                        for (var p : players) if (!p.isCreative() && !p.isSpectator()) playerCount++;
                        if (playerCount <= 1) this.skipKeyframe();
                    }
                }),
                new Keyframe(2100.0 / this.duration)
        ).endAction(() -> {
            for (var p : level.players()) if (p instanceof ServerPlayer sp) { try { com.sp.network.InitializePackets.sendWaitingRoom(sp, true); } catch (Exception ignored) {} }
        }).build();
    }
}
