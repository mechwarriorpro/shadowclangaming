package com.shadowclangaming.warhead.apex;

import com.shadowclangaming.perf.FxBudget;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.LargeFireball;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Random;

/**
 * What each of the seventeen apex warheads actually does to the world.
 *
 * THE SHAPE OF EVERY WEAPON HERE. One {@link #stage} call per scheduled stage, plus {@link #finish} at the
 * end. Stage 0 is the release (the charge has already been drawn on the client), the middle stages are the
 * front sweeping out or in, and the last stage is the release of whatever the weapon has been holding.
 *
 * WHY THE RADIUS COMES FROM OUTSIDE. The pad's slider - the "do not cap the blast radius" rule - is
 * authoritative. Every weapon here takes its radius as a parameter and scales its own effects against it,
 * so a slider set to 512 gives a 512-block black hole rather than a 96-block one with a bigger sound.
 *
 * THE BLOCK WORK IS DELEGATED. Spheres go through {@link WarheadType#blast} so they inherit the existing
 * multithreaded planner and the persistent-scar path for continent-scale radii; everything that is not a
 * sphere goes through {@link ApexCarver}, which is the same idea with a mask. Nothing in this file loops
 * over blocks on the server thread.
 */
public final class ApexWarheads {

    /** Entities are pulled toward an implosion front at most this hard, in blocks/tick. */
    public static final double PULL_STRENGTH = 1.2D;

    private ApexWarheads() {
    }

    /**
     * Entry point from {@link WarheadType#detonate}.
     *
     * @param sliderRadius the pad's radius slider, or -1 for the apex's own default
     * @return the radius in blocks that this weapon will affect, for the chat read-out
     */
    public static int fire(ServerLevel level, Vec3 pos, Apex apex, int sliderRadius, long seed) {
        int radius = sliderRadius > 0 ? sliderRadius : apex.radius();
        radius = Math.max(4, radius);

        // The wind-up is drawn locally by the client from the same timeline, so the server only has to
        // make the noise and (for weapons that hold something) start the pull.
        announce(level, pos, apex, radius);

        // The Soul Furnace is the one apex weapon whose length is not fixed: it keeps eating and growing
        // until a five-second look-around finds nothing left to consume, so it runs on its own clock
        // instead of booking a stage schedule it would never finish on time.
        if (apex == Apex.SOUL_FURNACE) {
            SoulFurnaceStages.start(level, pos, apex, radius, seed);
            return radius;
        }

        ApexStages.schedule(level, pos, apex, radius, seed);
        return radius;
    }

    /** Runs one stage of a scheduled detonation. */
    public static void stage(ServerLevel level, Vec3 pos, Apex apex, int radius, int stage, long seed) {
        Random random = new Random(seed * 31L + stage);
        double front = ApexTimeline.frontRadius(apex, ApexTimeline.stageTick(apex, stage), radius);

        switch (apex) {
            case WORLD_EATER -> {
                // Everything falls in; the front closes; the last stage is the void opening.
                pullEntities(level, pos, front, 1.0D);
                ApexCarver.carve(level, pos, (int) Math.max(2, front), ApexCarver.Mask.SHELL, 4,
                        null);
                spiral(level, pos, front, 40, seed);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power());
                    ApexCarver.carveSphere(level, pos, (int) front);
                    FxBudget.send(level, ParticleTypes.SQUID_INK, pos, 120, front / 8, front / 8,
                            front / 8, 0.05);
                }
            }
            case STELLAR_REQUIEM, STARFORGE -> {
                // A star that grows: shell after shell of corona, and the terrain under it goes.
                ApexCarver.carve(level, pos, (int) Math.max(2, front), ApexCarver.Mask.SHELL, 6, null);
                FxBudget.send(level, ParticleTypes.FLAME, pos, 90, front / 6, front / 6, front / 6, 0.1);
                FxBudget.send(level, ParticleTypes.END_ROD, pos, 60, front / 8, front / 8, front / 8, 0.3);
                scorch(level, pos, (int) front, 0.10F);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power());
                    FxBudget.send(level, ParticleTypes.EXPLOSION_EMITTER, pos, 12, front / 4, 2.0,
                            front / 4, 0.0);
                }
            }
            case REALITY_BREAKER, ABSOLUTE_ANNIHILATION -> {
                // Cracks first, then a vortex, then the tear eats the landscape.
                cracks(level, pos, (int) Math.max(4, front), seed + stage);
                ApexCarver.carve(level, pos, (int) Math.max(2, front * 0.6D), ApexCarver.Mask.SPHERE, 0, null);
                pullEntities(level, pos, front, 0.8D);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power());
                    ApexCarver.carveSphere(level, pos, (int) front);
                    FxBudget.send(level, ParticleTypes.PORTAL, pos, 160, front / 6, front / 6,
                            front / 6, 0.4);
                }
            }
            case SPACETIME_HAMMER -> {
                // Each stage is one ring, wider than the last, that leaves a trench behind it.
                int ringCount = ApexTimeline.ringCount(apex);
                double ringRadius = radius * ApexTimeline.ringFraction(stage, ringCount);
                ApexCarver.carve(level, pos, (int) Math.max(3, ringRadius), ApexCarver.Mask.TRENCH,
                        3, null);
                WarheadType.blast(level, pos, (float) (apex.power() * (0.35D + 0.15D * stage)));
                level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.LIGHTNING_BOLT_THUNDER,
                        SoundSource.BLOCKS, 2.0F, 0.5F + 0.1F * stage);
                knockDown(level, pos, ringRadius, 1.6D);
            }
            case UNIVERSAL_IMPLODER, ARCANE_SINGULARITY -> {
                pullEntities(level, pos, front, 1.4D);
                ApexCarver.carve(level, pos, (int) Math.max(2, front), ApexCarver.Mask.SHELL, 8, null);
                spiral(level, pos, front, 60, seed + stage);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power() * 1.1F);
                    ApexCarver.carveSphere(level, pos, (int) front);
                }
            }
            case RAGNAROK -> {
                // Sky first (the client grades it red), then meteors, then the ground gives up.
                meteors(level, pos, radius, ApexTimeline.meteors(apex, radius) / Math.max(1, apex.stages()),
                        random);
                spectralLightning(level, pos, (int) front, 6);
                ApexCarver.carve(level, pos, (int) Math.max(2, front * 0.8D),
                        ApexCarver.Mask.SHELL, 6, null);
                scorch(level, pos, (int) front, 0.14F);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power());
                }
            }
            case WORLD_RUNE -> {
                // The glyph is drawn stroke by stroke - the crater is the drawing.
                engraveRune(level, pos, radius, stage, apex.stages(), seed);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power());
                    ApexCarver.carveSphere(level, pos, (int) (radius * 0.7D));
                    FxBudget.send(level, ParticleTypes.END_ROD, pos, 200, radius / 4, 1.5,
                            radius / 4, 0.35);
                }
            }
            case ABYSSAL_COVENANT -> {
                tendrils(level, pos, radius, stage, seed);
                portals(level, pos, radius, ApexTimeline.portals(radius) / Math.max(1, apex.stages()),
                        random);
                pullEntities(level, pos, front, 1.6D);
                swallow(level, pos, (int) Math.max(2, front * 0.5D));
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power() * 0.8F);
                }
            }
            case SOUL_FURNACE -> {
                // Not reached in normal play: fire() routes this weapon to SoulFurnaceStages, which owns
                // its clock (consume, check every five seconds, grow, and only release when a check finds
                // nothing left). The fixed five-stage version that used to live here detonated on a
                // schedule whether the battlefield had anything left in it or not, which is exactly the
                // behaviour that was rejected. Hand off instead of running it.
                SoulFurnaceStages.start(level, pos, apex, radius, seed);
            }
            case QUASAR_LANCE -> {
                // A jet, not a bomb: one long carve down the line the weapon arrived on.
                Vec3 direction = lanceDirection(seed);
                int width = ApexTimeline.lanceWidth(radius);
                if (stage == 0) {
                    ApexCarver.carve(level, pos, radius, ApexCarver.Mask.BEAM, width, direction);
                } else {
                    // The second and third stages widen the cut and blow the walls out.
                    WarheadType.blast(level, pos.add(direction.scale(radius)), apex.power() * 0.6F);
                    ApexCarver.carveSphere(level, pos.add(direction.scale(radius)), width * 3);
                }
                FxBudget.send(level, ParticleTypes.END_ROD, pos, 120,
                        direction.x * ApexTimeline.lanceLength(radius) * 0.5,
                        direction.y, direction.z * ApexTimeline.lanceLength(radius) * 0.5, 0.6);
            }
            case BIG_BANG_SEED -> {
                // A sphere of energy that grows until the world runs out of room.
                ApexCarver.carve(level, pos, (int) Math.max(2, front), ApexCarver.Mask.SHELL, 10, null);
                FxBudget.send(level, ParticleTypes.END_ROD, pos, 80, front / 4, front / 4, front / 4, 0.5);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power());
                    ApexCarver.carveSphere(level, pos, (int) front);
                }
            }
            case REALITY_CORRUPTOR -> {
                corrupt(level, pos, (int) Math.max(4, front), seed + stage);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power() * 0.9F);
                }
            }
            case ABSOLUTE_ZERO -> {
                // Nothing moves, nothing falls, nothing makes a sound worth hearing - until it does.
                freeze(level, pos, front);
                pullEntities(level, pos, front, 0.4D);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power() * 1.3F);
                    ApexCarver.carveSphere(level, pos, (int) front);
                    level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.GLASS_BREAK,
                            SoundSource.BLOCKS, 2.0F, 0.4F);
                }
            }
            case OMEGA -> {
                // Annihilation front outward, then the secondary implosion on the last stage.
                ApexCarver.carve(level, pos, (int) Math.max(2, front), ApexCarver.Mask.SHELL, 12, null);
                knockDown(level, pos, front, 2.0D);
                if (lastStage(apex, stage)) {
                    WarheadType.blast(level, pos, apex.power());
                    pullEntities(level, pos, radius, 2.0D);
                }
            }
            default -> WarheadType.blast(level, pos, apex.power());
        }
    }

    /** Runs when every stage is done: the last word, the broadcast, the clean-up. */
    public static void finish(ServerLevel level, Vec3 pos, Apex apex, int radius, long seed) {
        FxBudget.send(level, ParticleTypes.LARGE_SMOKE, pos, 60, radius / 6, 1.5, radius / 6, 0.02);
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.apex_done",
                        Component.translatable(apex.translationKey())), false);
        // Tell the client the weapon is over so it can stop the shader and the overlay early.
        com.shadowclangaming.warhead.apex.ApexFx.broadcast(level, pos, apex, radius, -1);
    }

    // ------------------------------------------------------------------ shared effects

    /** True if this stage is the weapon's last. */
    private static boolean lastStage(Apex apex, int stage) {
        return stage >= apex.stages() - 1;
    }

    private static void announce(ServerLevel level, Vec3 pos, Apex apex, int radius) {
        com.shadowclangaming.debug.ShadowDebug.log("APEX_START",
                "apex={} shader=apex_{} pos=({}, {}, {}) radius={} totalTicks={} charge={} stages={} interval={}",
                apex.id(), apex.shader(), pos.x, pos.y, pos.z, radius, apex.totalTicks(),
                apex.chargeTicks(), apex.stages(), apex.intervalTicks());
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.END_PORTAL_SPAWN,
                SoundSource.BLOCKS, 2.0F, 0.5F);
        // The client renders the whole timeline from four numbers; this is the packet that carries them.
        ApexFx.broadcast(level, pos, apex, radius, apex.totalTicks());
        level.getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("message.shadowclangaming.apex_launch",
                        Component.translatable(apex.translationKey()), radius), false);
    }

    /** Drags entities toward the centre. The classic implosion, shared by six weapons. */
    private static void pullEntities(ServerLevel level, Vec3 pos, double radius, double strength) {
        AABB box = new AABB(pos, pos).inflate(radius);
        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            Vec3 offset = pos.subtract(entity.position());
            double distance = offset.length();
            if (distance < 0.5D || distance > radius) {
                continue;
            }
            double falloff = 1.0D - distance / radius;
            Vec3 pull = offset.normalize().scale(Math.min(PULL_STRENGTH, strength * falloff * 1.5D));
            entity.push(pull.x, pull.y * 0.6D + 0.05D, pull.z);
            entity.hurtMarked = true;
        }
    }

    /** Pushes structures over: a radial shove with an upward component. */
    private static void knockDown(ServerLevel level, Vec3 pos, double radius, double strength) {
        AABB box = new AABB(pos, pos).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, Entity::isAlive)) {
            if (entity instanceof Player player && player.isCreative()) {
                continue;
            }
            Vec3 push = entity.position().subtract(pos);
            if (push.lengthSqr() < 0.01D) {
                continue;
            }
            entity.push(push.normalize().scale(strength).x, 0.6D, push.normalize().scale(strength).z);
            entity.hurtMarked = true;
        }
    }

    /** Blocks spiral inward along curved streams: the signature of a gravitational well. */
    private static void spiral(ServerLevel level, Vec3 pos, double radius, int count, long seed) {
        int samples = Math.min(count, 120);
        double step = radius / 6.0D;
        for (int i = 0; i < samples; i++) {
            double angle = (i / (double) samples) * Math.PI * 2.0D * 3.0D + (seed % 360) * 0.01D;
            double distance = step + (i % 6) * step;
            BlockPos at = BlockPos.containing(pos.x + Math.cos(angle) * distance, pos.y,
                    pos.z + Math.sin(angle) * distance);
            if (!level.isLoaded(at)) {
                continue;
            }
            if (level.getBlockState(at).isAir()) {
                continue;
            }
            FallingBlockEntity debris = FallingBlockEntity.fall(level, at,
                    level.getBlockState(at));
            debris.setDeltaMovement(-Math.cos(angle) * 0.35D, 0.25D, -Math.sin(angle) * 0.35D);
            debris.hurtMarked = true;
            level.setBlock(at, Blocks.AIR.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Thin vertical slices through the crust: the "jagged cracks" of a reality tear. */
    private static void cracks(ServerLevel level, Vec3 pos, int radius, long seed) {
        Random random = new Random(seed);
        int count = Math.max(4, Math.min(24, radius / 5));
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = 2.0D + random.nextDouble() * radius;
            BlockPos at = BlockPos.containing(pos.x + Math.cos(angle) * distance, pos.y,
                    pos.z + Math.sin(angle) * distance);
            ApexCarver.carve(level, Vec3.atCenterOf(at), 3, ApexCarver.Mask.SPHERE, 0, null);
            FxBudget.send(level, ParticleTypes.PORTAL, Vec3.atCenterOf(at), 12, 0.4, 1.2, 0.4, 0.2);
        }
    }

    /** The Abyssal Covenant's tendrils: a random walk across the surface, carving as it goes. */
    private static void tendrils(ServerLevel level, Vec3 pos, int radius, int stage, long seed) {
        Random random = new Random(seed * 71L + stage);
        int count = Math.max(6, ApexTimeline.tendrils(radius) / 6);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double reach = radius * (0.4D + random.nextDouble() * 0.6D);
            BlockPos end = BlockPos.containing(pos.x + Math.cos(angle) * reach, pos.y,
                    pos.z + Math.sin(angle) * reach);
            ApexCarver.carve(level, Vec3.atCenterOf(end), Math.max(2, radius / 16),
                    ApexCarver.Mask.SPHERE, 0, null);
            FxBudget.send(level, ParticleTypes.SCULK_SOUL, Vec3.atCenterOf(end), 20, 0.5, 0.5, 0.5, 0.05);
        }
    }

    private static void portals(ServerLevel level, Vec3 pos, int radius, int count, Random random) {
        int samples = Math.min(count, 60);
        for (int i = 0; i < samples; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = random.nextDouble() * radius;
            Vec3 at = pos.add(Math.cos(angle) * distance, random.nextDouble() * 8.0D - 2.0D,
                    Math.sin(angle) * distance);
            FxBudget.send(level, ParticleTypes.REVERSE_PORTAL, at, 14, 0.3, 0.6, 0.3, 0.05);
            level.playSound(null, at.x, at.y, at.z, SoundEvents.PORTAL_AMBIENT,
                    SoundSource.BLOCKS, 0.5F, 0.6F);
        }
    }

    /** Drops the surface into the ground: the "dragging blocks into the abyss" effect, budgeted. */
    private static void swallow(ServerLevel level, Vec3 pos, int radius) {
        ApexCarver.carve(level, pos, radius, ApexCarver.Mask.TRENCH, 4, null);
    }

    /**
     * Kills or damages what the vortex caught, and reports the tally for the final blast size.
     *
     * Public because {@link SoulFurnaceStages} calls it once per availability check: the furnace's whole
     * schedule is "eat, look again in five seconds, keep growing while there is something to eat".
     */
    public static int devour(ServerLevel level, Vec3 pos, double radius) {
        AABB box = new AABB(pos, pos).inflate(radius);
        int souls = 0;
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && !(e instanceof Player))) {
            double distance = entity.position().distanceTo(pos);
            if (distance > radius) {
                continue;
            }
            entity.hurt(level.damageSources().magic(), 12.0F);
            entity.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                    MobEffects.WITHER, 200, 1));
            souls += entity.isAlive() ? 1 : 3;
        }
        for (Player player : level.getEntitiesOfClass(Player.class, box, Player::isAlive)) {
            player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 300, 1));
            player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 200, 0));
            souls += 1;
        }
        return souls;
    }

    /** Ragnarok's bombardment. Real fireballs, so the craters are real craters. */
    private static void meteors(ServerLevel level, Vec3 pos, int radius, int count, Random random) {
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = random.nextDouble() * radius;
            double x = pos.x + Math.cos(angle) * distance;
            double z = pos.z + Math.sin(angle) * distance;
            // Do not use the shooter constructor with a null owner: 1.21.1 dereferences the shooter
            // while initializing AbstractHurtingProjectile. The type/level constructor is ownerless-safe.
            LargeFireball fireball = new LargeFireball(EntityType.FIREBALL, level);
            fireball.setPos(x, pos.y + 60.0D + random.nextDouble() * 20.0D, z);
            fireball.setDeltaMovement(0.0D, -1.6D, 0.0D);
            fireball.accelerationPower = 0.0D;
            // Each meteor uses the vanilla fireball's conservative default power; the bombardment is the weapon.
            level.addFreshEntity(fireball);
        }
    }

    private static void spectralLightning(ServerLevel level, Vec3 pos, int radius, int count) {
        Random random = new Random(pos.hashCode() * 13L);
        for (int i = 0; i < count; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double distance = random.nextDouble() * radius;
            BlockPos at = BlockPos.containing(pos.x + Math.cos(angle) * distance, pos.y,
                    pos.z + Math.sin(angle) * distance);
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt == null) {
                continue;
            }
            bolt.moveTo(Vec3.atBottomCenterOf(at));
            bolt.setVisualOnly(true);
            level.addFreshEntity(bolt);
        }
    }

    /** The World Rune: strokes on the ground, one stage at a time, then the whole glyph goes up. */
    private static void engraveRune(ServerLevel level, Vec3 pos, int radius, int stage, int stages,
                                    long seed) {
        int strokes = ApexTimeline.runeStrokes(radius);
        int from = (int) ((long) strokes * stage / Math.max(1, stages));
        int to = (int) ((long) strokes * (stage + 1) / Math.max(1, stages));
        Random random = new Random(seed);
        for (int i = from; i < to; i++) {
            // A ring with radial ticks: readable as a glyph from above, and cheap to describe.
            double angle = (i / (double) strokes) * Math.PI * 2.0D;
            double distance = radius * (0.55D + (i % 3) * 0.12D);
            double x = pos.x + Math.cos(angle) * distance;
            double z = pos.z + Math.sin(angle) * distance;
            int groundY = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE,
                    (int) Math.floor(x), (int) Math.floor(z));
            BlockPos at = BlockPos.containing(x, groundY - 1, z);
            ApexCarver.carve(level, Vec3.atCenterOf(at), 2, ApexCarver.Mask.SPHERE, 0, null);
            FxBudget.send(level, ParticleTypes.ENCHANT, Vec3.atCenterOf(at), 10, 0.4, 0.2, 0.4, 0.05);
            if (random.nextBoolean()) {
                FxBudget.send(level, ParticleTypes.END_ROD, Vec3.atCenterOf(at), 4, 0.2, 0.6, 0.2, 0.02);
            }
        }
    }

    /** Reality Corruptor: the ground turns into something that should not be made of blocks. */
    private static void corrupt(ServerLevel level, Vec3 pos, int radius, long seed) {
        Random random = new Random(seed);
        BlockPos centre = BlockPos.containing(pos);
        int r = Math.min(radius, 48);
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -8, -r), centre.offset(r, 8, r))) {
            if (random.nextFloat() > 0.22F) {
                continue;
            }
            if (!level.isLoaded(p) || level.getBlockState(p).isAir()) {
                continue;
            }
            if (level.getBlockEntity(p) != null) {
                continue;
            }
            var replacement = switch (random.nextInt(5)) {
                case 0 -> Blocks.SCULK.defaultBlockState();
                case 1 -> Blocks.CHORUS_PLANT.defaultBlockState();
                case 2 -> Blocks.CRYING_OBSIDIAN.defaultBlockState();
                case 3 -> Blocks.SHROOMLIGHT.defaultBlockState();
                default -> Blocks.MAGENTA_GLAZED_TERRACOTTA.defaultBlockState();
            };
            level.setBlock(p, replacement, net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
        // And some of it is simply deleted, which is what makes it read as corruption rather than decay.
        ApexCarver.carve(level, pos, Math.max(3, radius / 3), ApexCarver.Mask.SPHERE, 0, null);
        FxBudget.send(level, ParticleTypes.REVERSE_PORTAL, pos, 40, radius / 6, 1.0, radius / 6, 0.2);
    }

    /** Absolute Zero: light and motion stop. Blocks stay; everything alive stops wanting to move. */
    private static void freeze(ServerLevel level, Vec3 pos, double radius) {
        AABB box = new AABB(pos, pos).inflate(radius);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, box, Entity::isAlive)) {
            entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 6));
            entity.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 6));
            entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0));
            entity.setDeltaMovement(Vec3.ZERO);
            entity.hurtMarked = true;
        }
        FxBudget.send(level, ParticleTypes.SNOWFLAKE, pos, 80, radius / 6, radius / 6, radius / 6, 0.01);
    }

    /** Sets fire on grass and wood in a radius, with a density rather than a blanket. */
    private static void scorch(ServerLevel level, Vec3 pos, int radius, float density) {
        BlockPos centre = BlockPos.containing(pos);
        int r = Math.min(radius, 40);
        Random random = new Random(pos.hashCode());
        for (BlockPos p : BlockPos.betweenClosed(centre.offset(-r, -4, -r), centre.offset(r, 4, r))) {
            if (random.nextFloat() > density) {
                continue;
            }
            if (!level.isLoaded(p) || !level.getBlockState(p).isAir()) {
                continue;
            }
            if (level.getBlockState(p.below()).isAir()) {
                continue;
            }
            level.setBlock(p, Blocks.FIRE.defaultBlockState(),
                    net.minecraft.world.level.block.Block.UPDATE_CLIENTS);
        }
    }

    /** Deterministic lance direction, so the same shot always cuts the same canyon. */
    private static Vec3 lanceDirection(long seed) {
        Random random = new Random(seed);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        return new Vec3(Math.cos(angle), 0.0D, Math.sin(angle));
    }

    /** Every apex warhead type, in the order they appear on the item roster. */
    public static List<Apex> all() {
        return List.of(Apex.values());
    }
}
