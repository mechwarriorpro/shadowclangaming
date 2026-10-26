package com.shadowclangaming.warhead;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.registry.SCGEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.monster.WitherSkeleton;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * THE VIOLATOR.
 *
 * "Spawns a wither upon impact however wither has 3x health, skull blast radius and strength increased
 * by 5x, and once every 30 seconds it should rapid fire skulls for 5 seconds, 1 skull every 0.25
 * seconds."
 *
 * THE BUFFS
 *   health       300 (vanilla 300) x 3 = 900, and it is healed to full on spawn
 *   skulls       ours are tagged, and their blasts are scaled 5x - see {@link #scaleBlast}
 *
 * THE BURST
 *   Every 600 ticks it enters a 100-tick burst and fires a SHOTGUN VOLLEY every 4 ticks: EIGHT skulls
 *   per volley, each aimed at a different target when there is more than one. Vanilla wither skulls
 *   come from its own AI on its own schedule, which we cannot retune without mixing into its goal
 *   list; firing them ourselves is simpler, more reliable across versions, and lets the burst be
 *   visibly different.
 *
 * THE ANTI-BASE PART
 *   Range is 64 blocks - six chunks in every direction - and a volley spreads across up to
 *   {@link #SHOTGUN} different targets at once, so a group of players cannot all hide behind one
 *   shield or one corner. With fewer than eight targets the extra skulls fan out in a cone around the
 *   nearest ones, which is what makes a volley hard to sidestep rather than just loud.
 *
 * Skull scaling is done by tagging each skull we spawn with {@code scg_violator} and intercepting the
 * explosion: vanilla's block damage is cancelled and replaced with our own blast at 5x power, so a
 * single skull breaks a crater rather than a hole.
 */
public final class ViolatorWither {

    /** Vanilla wither health, times three. */
    public static final double HEALTH_MULTIPLIER = 3.0D;
    /** Blast radius and damage per skull, times five. */
    public static final float SKULL_MULTIPLIER = 5.0F;

    public static final int BURST_INTERVAL = 600;  // 30 s
    public static final int BURST_LENGTH = 100;    // 5 s
    /** Ticks between volleys. 4 = five volleys a second, "extremely fast". */
    public static final int BURST_SHOT_GAP = 4;
    /** Skulls per volley. Eight is a shotgun, not a poke. */
    public static final int SHOTGUN = 8;
    /** How far it looks for something to shoot. */
    public static final double RANGE = 64.0D;
    /**
     * Ceiling on live Violator skulls.
     *
     * A burst is 25 volleys x 8 skulls = 200 projectiles in five seconds, and a second wither could
     * double that. This cap is not a balance limit on the weapon - it is what keeps a server at 20 tps
     * while two Violators argue; past it, the extra skulls of a volley are skipped.
     */
    public static final int MAX_LIVE_SKULLS = 192;
    /** Radius of the fan when there are fewer targets than skulls. */
    public static final double SPREAD = 0.10D;

    private static final String TAG = "scg_violator";

    /** Withers we own, and when each one next bursts. */
    private static final Map<UUID, BurstState> BURSTS = new WeakHashMap<>();

    private ViolatorWither() {
    }

    private static final class BurstState {
        long nextBurst;
        long burstEnd;
        long nextShot;
        int shots;
    }

    /** Spawn the Violator at the impact point. @return the wither, or null if the level refused it. */
    public static WitherBoss spawn(ServerLevel level, Vec3 pos) {
        WitherBoss wither = net.minecraft.world.entity.EntityType.WITHER.create(level);
        if (wither == null) {
            return null;
        }
        wither.moveTo(pos.x, pos.y + 1.0D, pos.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        double max = wither.getAttribute(Attributes.MAX_HEALTH) == null
                ? 300.0D
                : wither.getAttribute(Attributes.MAX_HEALTH).getBaseValue();
        if (wither.getAttribute(Attributes.MAX_HEALTH) != null) {
            wither.getAttribute(Attributes.MAX_HEALTH).setBaseValue(max * HEALTH_MULTIPLIER);
        }
        wither.setHealth((float) (max * HEALTH_MULTIPLIER));
        wither.getPersistentData().putBoolean(TAG, true);
        wither.setCustomName(net.minecraft.network.chat.Component.translatable("entity.shadowclangaming.violator"));
        wither.setCustomNameVisible(true);
        level.addFreshEntity(wither);

        BurstState state = new BurstState();
        state.nextBurst = level.getGameTime() + BURST_INTERVAL;
        state.burstEnd = 0L;
        state.shots = 0;
        synchronized (BURSTS) {
            BURSTS.put(wither.getUUID(), state);
        }

        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, pos.x, pos.y + 1.0D, pos.z, 4, 3.0D, 2.0D, 3.0D, 0.0D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, pos.x, pos.y + 1.0D, pos.z, 80, 3.0D, 2.0D, 3.0D, 0.05D);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.WITHER_SPAWN, SoundSource.HOSTILE, 3.0F, 0.6F);
        return wither;
    }

    /** Is this wither a Violator? */
    public static boolean isViolator(Entity entity) {
        return entity instanceof WitherBoss && entity.getPersistentData().getBoolean(TAG);
    }

    /** Called once per server tick: drives the burst schedule for every live Violator. */
    public static void tick(MinecraftServer server) {
        List<WitherBoss> violators = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (isViolator(entity) && entity instanceof WitherBoss wither && wither.isAlive()) {
                    violators.add(wither);
                }
            }
        }
        if (violators.isEmpty()) {
            synchronized (BURSTS) {
                BURSTS.clear();
            }
            return;
        }
        for (WitherBoss wither : violators) {
            ServerLevel level = (ServerLevel) wither.level();
            long now = level.getGameTime();
            BurstState state;
            synchronized (BURSTS) {
                state = BURSTS.computeIfAbsent(wither.getUUID(), id -> {
                    BurstState fresh = new BurstState();
                    fresh.nextBurst = now + BURST_INTERVAL;
                    return fresh;
                });
            }
            if (now >= state.nextBurst) {
                state.burstEnd = now + BURST_LENGTH;
                state.nextShot = now;
                state.shots = 0;
                state.nextBurst = now + BURST_INTERVAL;
                level.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                        SoundEvents.WITHER_AMBIENT, SoundSource.HOSTILE, 3.0F, 0.5F);
                level.sendParticles(ParticleTypes.SOUL, wither.getX(), wither.getY() + 2.0D, wither.getZ(),
                        40, 1.5D, 1.0D, 1.5D, 0.02D);
            }
            if (now < state.burstEnd && now >= state.nextShot) {
                state.shots += volley(level, wither);
                state.nextShot = now + BURST_SHOT_GAP;
            }
        }
    }

    /**
     * One volley: up to {@link #SHOTGUN} skulls, spread over as many different targets as there are.
     *
     * Target assignment is round-robin over the nearest N enemies, so a group of four players each
     * catch two skulls instead of the nearest one eating all eight. When there are fewer targets than
     * skulls, the remainder are fanned around the nearest target's line of fire - close enough to
     * bracket a dodging player, loose enough to still hit things behind them.
     *
     * @return how many skulls were actually spawned
     */
    private static int volley(ServerLevel level, WitherBoss wither) {
        List<LivingEntity> targets = targetsInRange(level, wither, SHOTGUN);
        int live = countLiveSkulls(level, wither);
        int budget = Math.max(0, MAX_LIVE_SKULLS - live);
        int firing = Math.min(SHOTGUN, Math.max(budget, 0));
        if (firing <= 0) {
            return 0;
        }

        Vec3 from = new Vec3(wither.getX(), wither.getY() + 2.0D, wither.getZ());
        for (int i = 0; i < firing; i++) {
            LivingEntity target = targets.isEmpty() ? null : targets.get(i % targets.size());
            Vec3 direction;
            if (target != null) {
                Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
                direction = to.subtract(from).normalize();
                // Beyond the first full set of targets, spread: the same target, a different angle.
                if (i >= targets.size()) {
                    direction = spread(direction, level.getRandom().nextDouble() * SPREAD * 2.0D - SPREAD,
                            level.getRandom().nextDouble() * SPREAD * 2.0D - SPREAD);
                }
            } else {
                // Nothing in range: hose the area in front rather than standing there.
                Vec3 look = wither.getLookAngle();
                direction = spread(look,
                        (level.getRandom().nextDouble() - 0.5D) * 0.6D,
                        (level.getRandom().nextDouble() - 0.5D) * 0.6D);
            }
            spawnSkull(level, wither, from, direction);
        }
        level.playSound(null, wither.getX(), wither.getY(), wither.getZ(),
                SoundEvents.WITHER_SHOOT, SoundSource.HOSTILE, 2.4F, 0.7F + level.getRandom().nextFloat() * 0.2F);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, from.x, from.y, from.z, 12, 0.4D, 0.4D, 0.4D, 0.02D);
        return firing;
    }

    /** Nudge a direction sideways so successive skulls in a volley do not stack on one line. */
    private static Vec3 spread(Vec3 direction, double yawOffset, double pitchOffset) {
        Vec3 flat = new Vec3(direction.x, 0.0D, direction.z);
        if (flat.lengthSqr() < 1.0E-6D) {
            return direction.add(yawOffset, pitchOffset, yawOffset).normalize();
        }
        Vec3 right = new Vec3(-flat.z, 0.0D, flat.x).normalize();
        return direction.add(right.scale(yawOffset)).add(0.0D, pitchOffset, 0.0D).normalize();
    }

    private static void spawnSkull(ServerLevel level, WitherBoss wither, Vec3 from, Vec3 direction) {
        WitherSkull skull = new WitherSkull(level, wither, direction);
        skull.setPos(from.x + direction.x * 1.5D, from.y + direction.y * 1.5D, from.z + direction.z * 1.5D);
        skull.setDangerous(level.getRandom().nextBoolean());
        // Tag the skull so the explosion handler knows to scale it.
        skull.getPersistentData().putBoolean(TAG, true);
        level.addFreshEntity(skull);
    }

    private static int countLiveSkulls(ServerLevel level, WitherBoss wither) {
        int count = 0;
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof WitherSkull skull && skull.getPersistentData().getBoolean(TAG)) {
                count++;
            }
        }
        return count;
    }

    /** The nearest {@code limit} valid targets within {@link #RANGE}, nearest first. */
    private static List<LivingEntity> targetsInRange(ServerLevel level, WitherBoss wither, int limit) {
        AABB box = wither.getBoundingBox().inflate(RANGE);
        List<LivingEntity> candidates = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class, box,
                entity -> isTarget(wither, entity)));
        candidates.sort(java.util.Comparator.comparingDouble(wither::distanceToSqr));
        return candidates.size() > limit ? new ArrayList<>(candidates.subList(0, limit)) : candidates;
    }

    /** Everything a Violator considers fair game. */
    private static boolean isTarget(WitherBoss wither, LivingEntity entity) {
        if (entity == wither || !entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            // Creative and spectator players are not targets; a builder in creative should not be shot
            // off their scaffolding by a warhead they were watching.
            return !player.isCreative() && !player.isSpectator();
        }
        // Anything living that is not one of its own kind. Animals included: a Violator does not
        // distinguish between a raiding party and their cattle.
        return !(entity instanceof WitherSkeleton) && !(entity instanceof WitherBoss);
    }

    /**
     * Scale a Violator skull's blast.
     *
     * Called from the explosion handler. Vanilla has already decided the radius by the time the event
     * fires, so the honest fix is: cancel what it was going to do and run our own explosion at 5x.
     *
     * @return true if this explosion was ours and has been handled
     */
    public static boolean scaleBlast(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return false;
        }
        Entity source = event.getExplosion().getDirectSourceEntity();
        if (source == null || !source.getPersistentData().getBoolean(TAG)) {
            return false;
        }
        if (source instanceof WitherSkull) {
            Vec3 position = event.getExplosion().center();
            float power = event.getExplosion().radius() * SKULL_MULTIPLIER;
            event.getAffectedBlocks().clear();
            event.getAffectedEntities().clear();
            ShadowClanGaming.LOGGER.debug("Violator skull scaled: power {} at {}", power, position);
            // Our own blast, at the scaled power. It has no source entity, so this cannot re-enter.
            level.explode(null, position.x, position.y, position.z, power,
                    net.minecraft.world.level.Level.ExplosionInteraction.TNT);
            return true;
        }
        return false;
    }

    /** NBT helper used by the missile so a loaded Violator keeps its tag across a save. */
    public static void tag(CompoundTag tag) {
        tag.putBoolean(TAG, true);
    }
}
