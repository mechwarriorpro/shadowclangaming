package com.shadowclangaming.warhead;

import com.shadowclangaming.registry.SCGEntities;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * THE MOTHER CLUCKER.
 *
 * "It just spawns like 50 chickens upon impact and the chickens should shoot eggs at nearby mobs and
 * players and if an egg hatches instead of a regular chicken its another turret chicken."
 *
 * Every chicken spawned here is our {@code TurretChickenEntity}, a real {@code Chicken} subclass, so
 * breeding, shearing and the vanilla renderer all keep working. What is different is its AI: it
 * targets players and hostile mobs in a radius and lobs eggs at them. Its eggs are our own
 * {@code TurretEggEntity}, whose hatch produces another turret chicken - so a clucker strike that
 * nobody cleans up becomes a self-sustaining problem, which is the joke and the threat at once.
 *
 * The count is fixed at 50: a hundred would just add lag, and fifty chickens shooting eggs is already
 * a firework display.
 */
public final class CluckerStrike {

    public static final int CHICKENS = 50;
    /** How far the chickens are scattered from the impact. */
    public static final double SCATTER = 7.0D;

    private CluckerStrike() {
    }

    /** @return how many chickens actually spawned (space can be full) */
    public static int release(ServerLevel level, Vec3 pos) {
        int spawned = 0;
        for (int i = 0; i < CHICKENS; i++) {
            double angle = level.getRandom().nextDouble() * Math.PI * 2.0D;
            double distance = level.getRandom().nextDouble() * SCATTER;
            double x = pos.x + Math.cos(angle) * distance;
            double z = pos.z + Math.sin(angle) * distance;
            double y = pos.y + 0.6D + level.getRandom().nextDouble() * 1.5D;

            Entity chicken = SCGEntities.TURRET_CHICKEN.get().create(level);
            if (chicken == null) {
                continue;
            }
            chicken.moveTo(x, y, z, level.getRandom().nextFloat() * 360.0F, 0.0F);
            chicken.setDeltaMovement((level.getRandom().nextDouble() - 0.5D) * 0.4D, 0.35D,
                    (level.getRandom().nextDouble() - 0.5D) * 0.4D);
            level.addFreshEntity(chicken);
            spawned++;
        }
        level.sendParticles(ParticleTypes.POOF, pos.x, pos.y + 1.0D, pos.z, 60, 4.0D, 1.5D, 4.0D, 0.05D);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 2.0F, 0.5F);
        level.playSound(null, pos.x, pos.y, pos.z, SoundEvents.CHICKEN_AMBIENT, SoundSource.NEUTRAL, 2.0F, 0.6F);
        return spawned;
    }

    /** Nearest attackable entity to a chicken. Used by the turret AI; kept here so it is one rule. */
    public static Entity findTarget(ServerLevel level, Vec3 from, double range) {
        AABB box = new AABB(from.x - range, from.y - range, from.z - range,
                from.x + range, from.y + range, from.z + range);
        Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Entity candidate : level.getEntities((Entity) null, box, CluckerStrike::isValidTarget)) {
            double distance = candidate.distanceToSqr(from);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    private static boolean isValidTarget(Entity entity) {
        if (!entity.isAlive() || entity.isSpectator()) {
            return false;
        }
        if (entity instanceof net.minecraft.world.entity.player.Player player) {
            return !player.isCreative() && !player.isSpectator();
        }
        // Mobs that fight each other are fair game; the chickens are on nobody's side.
        return entity instanceof net.minecraft.world.entity.Mob mob
                && !(mob instanceof net.minecraft.world.entity.animal.Chicken);
    }
}
