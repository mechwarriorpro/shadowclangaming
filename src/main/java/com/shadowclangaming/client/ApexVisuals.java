package com.shadowclangaming.client;

import com.shadowclangaming.warhead.apex.Apex;
import com.shadowclangaming.warhead.apex.ApexTimeline;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

import java.util.Random;

/**
 * The particle half of an apex warhead.
 *
 * WHY PARTICLES AT ALL, WHEN THERE IS A SHADER. A post effect grades the whole screen; it cannot put a
 * ring where the ring is. The crater, the spiral, the accretion disc and the tendrils all live in world
 * space, so they are particles - and the shader is what makes them read as a black hole rather than as a
 * cloud of white dots.
 *
 * BUDGET. Particles are the expensive part of any explosion on a CPU-bound client, so this spawns a fixed,
 * small number per tick per effect ({@link #PARTICLES_PER_TICK}) and stops entirely beyond
 * {@link #VISIBLE_RANGE}. A radius-256 weapon does not spawn more particles than a radius-32 one; it
 * spreads the same ones over a larger ring, which is also what looks right.
 *
 * Everything here is client-side and derived from {@link ApexTimeline}, the same curves the server used to
 * remove the blocks, so the ring you see is the ring that took the ground.
 */
public final class ApexVisuals {

    /** Particles per tick, per live effect. */
    public static final int PARTICLES_PER_TICK = 48;

    /** Beyond this many blocks an effect spawns nothing: you cannot see it anyway. */
    public static final double VISIBLE_RANGE = 320.0D;

    private static final Random RANDOM = new Random();

    private ApexVisuals() {
    }

    /** Once per client tick: advance the queue and spawn this tick's particles. */
    public static void tick() {
        ClientApexQueue.tick();
        var entries = ClientApexQueue.active();
        if (entries.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        Vec3 camera = minecraft.gameRenderer.getMainCamera().getPosition();
        for (ClientApexQueue.Entry entry : entries) {
            if (new Vec3(entry.x, entry.y, entry.z).distanceTo(camera) > VISIBLE_RANGE + entry.radius) {
                continue;
            }
            spawnField(level, entry);
        }
    }

    /** This tick's particle field for one effect. */
    private static void spawnField(ClientLevel level, ClientApexQueue.Entry entry) {
        Apex apex = entry.apex;
        double front = ApexTimeline.frontRadius(apex, entry.age, entry.radius);
        double curve = ApexTimeline.coreIntensity(apex, entry.age);
        int count = (int) Math.max(6, PARTICLES_PER_TICK * curve);

        for (int i = 0; i < count; i++) {
            // A ring, not a ball: the front is a surface, and sampling a surface is what makes it read as
            // one. The two extra jitter terms thicken it just enough to look like matter.
            double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
            double vertical = (RANDOM.nextDouble() - 0.5D) * 0.55D;
            double radius = front * (0.92D + RANDOM.nextDouble() * 0.16D);
            double cos = Math.cos(angle);
            double sin = Math.sin(angle);
            // Swirl: an imploding front turns, an exploding one does not.
            double swirl = apex.front() == Apex.Front.INWARD
                    ? (entry.age * 0.06D)
                    : -(entry.age * 0.02D);

            double x = entry.x + Math.cos(angle + swirl) * radius;
            double z = entry.z + Math.sin(angle + swirl) * radius;
            double y = entry.y + vertical * radius * 0.35D;

            ParticleOptions particle = particleFor(apex, RANDOM);
            double vx = cos * 0.05D;
            double vy = apex.front() == Apex.Front.INWARD ? 0.02D : 0.01D;
            double vz = sin * 0.05D;
            level.addParticle(particle, x, y, z, vx, vy, vz);
        }

        // The core itself: the thing everything is falling into or out of.
        if (curve > 0.35D) {
            for (int i = 0; i < 6; i++) {
                double x = entry.x + (RANDOM.nextDouble() - 0.5D) * 2.0D;
                double y = entry.y + (RANDOM.nextDouble() - 0.5D) * 2.0D;
                double z = entry.z + (RANDOM.nextDouble() - 0.5D) * 2.0D;
                level.addParticle(coreParticleFor(apex), x, y, z, 0.0D, 0.02D, 0.0D);
            }
        }

        // Sky effects: the ones you notice even when you are far away and looking up.
        if (ApexTimeline.isFxTick(entry.age)) {
            skyEffect(level, entry, front);
        }
    }

    /** Per-style particle palette. Sixteen looks, thirteen vanilla particle types, reused honestly. */
    private static ParticleOptions particleFor(Apex apex, Random random) {
        return switch (apex.style()) {
            case VOID -> random.nextBoolean() ? ParticleTypes.SQUID_INK : ParticleTypes.END_ROD;
            case STELLAR -> random.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.END_ROD;
            case RIFT -> random.nextBoolean() ? ParticleTypes.PORTAL : ParticleTypes.REVERSE_PORTAL;
            case HAMMER -> ParticleTypes.CLOUD;
            case IMPLODE -> random.nextBoolean() ? ParticleTypes.REVERSE_PORTAL : ParticleTypes.END_ROD;
            case APOCALYPSE -> random.nextBoolean() ? ParticleTypes.LAVA : ParticleTypes.FLAME;
            case RUNE -> random.nextBoolean() ? ParticleTypes.ENCHANT : ParticleTypes.END_ROD;
            case ABYSS -> random.nextBoolean() ? ParticleTypes.SCULK_SOUL : ParticleTypes.REVERSE_PORTAL;
            case SOUL -> ParticleTypes.SOUL_FIRE_FLAME;
            case ARCANE -> random.nextBoolean() ? ParticleTypes.WITCH : ParticleTypes.ENCHANT;
            case QUASAR -> ParticleTypes.END_ROD;
            case BIGBANG -> random.nextBoolean() ? ParticleTypes.FLASH : ParticleTypes.END_ROD;
            case CORRUPT -> random.nextBoolean() ? ParticleTypes.WARPED_SPORE : ParticleTypes.REVERSE_PORTAL;
            case ZERO -> ParticleTypes.SNOWFLAKE;
            case OMEGA -> random.nextBoolean() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.LAVA;
            case ANNIHILATION -> random.nextInt(3) == 0 ? ParticleTypes.FLASH : ParticleTypes.END_ROD;
        };
    }

    private static ParticleOptions coreParticleFor(Apex apex) {
        return switch (apex.style()) {
            case ZERO, VOID, ABYSS -> ParticleTypes.SQUID_INK;
            case STELLAR, QUASAR, BIGBANG, ANNIHILATION -> ParticleTypes.FLASH;
            case APOCALYPSE, OMEGA -> ParticleTypes.LAVA;
            case SOUL -> ParticleTypes.SOUL;
            case ARCANE, RUNE -> ParticleTypes.WITCH;
            default -> ParticleTypes.END_ROD;
        };
    }

    /** Sky and ground flourishes, at most a few per tick. */
    private static void skyEffect(ClientLevel level, ClientApexQueue.Entry entry, double front) {
        switch (entry.apex.style()) {
            case APOCALYPSE, OMEGA -> {
                // Burning debris raining in from above, which is what a red sky is for.
                for (int i = 0; i < 4; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double distance = RANDOM.nextDouble() * front;
                    level.addParticle(ParticleTypes.FALLING_LAVA,
                            entry.x + Math.cos(angle) * distance, entry.y + 40.0D,
                            entry.z + Math.sin(angle) * distance, 0.0D, -1.2D, 0.0D);
                }
            }
            case STELLAR, QUASAR, BIGBANG -> {
                for (int i = 0; i < 6; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double distance = front * (1.0D + RANDOM.nextDouble() * 0.6D);
                    level.addParticle(ParticleTypes.END_ROD,
                            entry.x + Math.cos(angle) * distance, entry.y + RANDOM.nextDouble() * 8.0D,
                            entry.z + Math.sin(angle) * distance, 0.0D, 0.15D, 0.0D);
                }
            }
            case ABYSS -> {
                for (int i = 0; i < 5; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double distance = RANDOM.nextDouble() * front;
                    level.addParticle(ParticleTypes.REVERSE_PORTAL,
                            entry.x + Math.cos(angle) * distance, entry.y + 1.0D,
                            entry.z + Math.sin(angle) * distance, 0.0D, 0.4D, 0.0D);
                }
            }
            case CORRUPT -> {
                for (int i = 0; i < 5; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double distance = RANDOM.nextDouble() * front;
                    level.addParticle(ParticleTypes.WARPED_SPORE,
                            entry.x + Math.cos(angle) * distance, entry.y + RANDOM.nextDouble() * 6.0D,
                            entry.z + Math.sin(angle) * distance, 0.0D, 0.05D, 0.0D);
                }
            }
            case RUNE, ARCANE -> {
                for (int i = 0; i < 5; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double distance = front * (0.4D + RANDOM.nextDouble() * 0.6D);
                    level.addParticle(ParticleTypes.ENCHANT,
                            entry.x + Math.cos(angle) * distance, entry.y + 1.0D,
                            entry.z + Math.sin(angle) * distance, 0.0D, -0.05D, 0.0D);
                }
            }
            case ZERO -> {
                for (int i = 0; i < 5; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double distance = RANDOM.nextDouble() * front;
                    level.addParticle(ParticleTypes.SNOWFLAKE,
                            entry.x + Math.cos(angle) * distance, entry.y + RANDOM.nextDouble() * 4.0D,
                            entry.z + Math.sin(angle) * distance, 0.0D, -0.02D, 0.0D);
                }
            }
            case HAMMER, IMPLODE -> {
                // The ring itself, made of dust: a flat annulus at the front.
                for (int i = 0; i < 12; i++) {
                    double angle = (i / 12.0D) * Math.PI * 2.0D;
                    level.addParticle(ParticleTypes.CLOUD,
                            entry.x + Math.cos(angle) * front, entry.y + 0.5D,
                            entry.z + Math.sin(angle) * front, 0.0D, 0.02D, 0.0D);
                }
            }
            case RIFT -> {
                for (int i = 0; i < 4; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double distance = front * (0.7D + RANDOM.nextDouble() * 0.4D);
                    level.addParticle(ParticleTypes.PORTAL,
                            entry.x + Math.cos(angle) * distance, entry.y + Mth.sin(RANDOM.nextFloat()) * 4.0D,
                            entry.z + Math.sin(angle) * distance, 0.0D, 0.1D, 0.0D);
                }
            }
            case SOUL -> {
                for (int i = 0; i < 5; i++) {
                    double angle = RANDOM.nextDouble() * Math.PI * 2.0D;
                    double distance = front * RANDOM.nextDouble();
                    level.addParticle(ParticleTypes.SOUL,
                            entry.x + Math.cos(angle) * distance, entry.y + RANDOM.nextDouble() * 3.0D,
                            entry.z + Math.sin(angle) * distance, 0.0D, -0.1D, 0.0D);
                }
            }
            default -> {
                // Nothing extra: the ring is enough.
            }
        }
    }
}
