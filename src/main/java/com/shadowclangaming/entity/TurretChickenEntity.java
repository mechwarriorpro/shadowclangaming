package com.shadowclangaming.entity;

import com.shadowclangaming.registry.SCGEntities;
import com.shadowclangaming.warhead.CluckerStrike;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * A chicken that has been to war.
 *
 * It IS a {@link Chicken} - same model, same breeding, same drops, same vanilla renderer - with one
 * extra goal: every second and a half it looks for the nearest player or hostile mob within
 * {@link #RANGE} and lobs one of its own eggs at them. The eggs are {@link TurretEggEntity}, which
 * hatch into more turret chickens rather than plain ones.
 *
 * The AI is four goals, and together they are the behaviour the design asks for: **notice, stare, chase,
 * shoot**.
 *
 *   AcquireGoal  picks a target and REMEMBERS it for a while after losing sight (10 s), so a chicken
 *                that has seen you come around a wall instead of forgetting you existed.
 *   StareGoal    turns the whole bird - not just the head - to face its enemy, constantly, at any range it
 *                can see. This is the "stare down": the threat arrives before the bird does.
 *   ChaseGoal    walks toward the target until it is {@link ChickenTactics#KEEP_DISTANCE} away, re-pathing
 *                on a timer rather than every tick (re-pathing every tick is how mobs get stuck on
 *                corners), and never crowds closer than arm's reach.
 *   ShootGoal    lobs an egg when it is inside {@link ChickenTactics#FIRE_RANGE}.
 *
 * All the numbers live in {@link ChickenTactics}, which is pure Java and tested in the harness.
 *
 * The goals run at priorities that lose to panic (avoid water, avoid players): a chicken that ignores a
 * wolf to keep shooting is a chicken that dies doing its job, which is not the design.
 */
public class TurretChickenEntity extends Chicken {

    /** How far it will shoot. */
    public static final double RANGE = 16.0D;
    /** Ticks between shots. */
    public static final int COOLDOWN = 30;

    private int cooldown;

    public TurretChickenEntity(EntityType<? extends Chicken> type, Level level) {
        super(type, level);
    }

    /** The enemy it is currently staring down, held across ticks so the bird commits to one target. */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.world.entity.Entity mark;
    /** Tick number when the mark was last actually seen (for the memory window). */
    private int markSeen;

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Priority order matters. Vanilla's panic/float goals sit at 0-2, so these come after them: a
        // chicken in water still swims, a chicken on fire still runs. 3 look, 4 move, 5 shoot.
        this.goalSelector.addGoal(1, new AcquireGoal(this));
        this.goalSelector.addGoal(3, new StareGoal(this));
        this.goalSelector.addGoal(4, new ChaseGoal(this));
        this.goalSelector.addGoal(5, new ShootGoal(this));
    }

    /** The entity this bird has decided to hate, or null. */
    @org.jetbrains.annotations.Nullable
    public net.minecraft.world.entity.Entity mark() {
        return mark;
    }

    public void setMark(@org.jetbrains.annotations.Nullable net.minecraft.world.entity.Entity target) {
        this.mark = target;
        this.markSeen = tickCount;
    }

    /** Note that the mark is still visible this tick, which is what keeps it remembered. */
    public void refreshMark() {
        this.markSeen = tickCount;
    }

    public boolean markVisible() {
        return mark != null && mark.isAlive() && ChickenTactics.rememberTarget(tickCount, markSeen);
    }

    /** Distance to the mark, or +infinity when there is nothing to hate. */
    public double markDistance() {
        return mark == null ? Double.MAX_VALUE : Math.sqrt(distanceToSqr(mark));
    }

    /** Fires the egg. Public so the goal stays a thin wrapper and the numbers live in one place. */
    public void shootAt(Entity target) {
        if (!(level() instanceof ServerLevel server)) {
            return;
        }
        TurretEggEntity egg = new TurretEggEntity(server, this);
        // The egg MUST carry a non-empty item stack. Vanilla ThrownEgg's break effect (entity event 3)
        // builds `new ItemParticleOption(ParticleTypes.ITEM, getItem())` on the CLIENT, and
        // ItemParticleOption's constructor throws "Empty stacks are not allowed" for an empty one - that
        // crash lands in the client's packet handler and takes the game down, not just the egg.
        // Vanilla's own egg throw sets Items.EGG here for exactly this reason.
        egg.setItem(new net.minecraft.world.item.ItemStack(
                net.minecraft.world.item.Items.EGG));
        Vec3 from = new Vec3(getX(), getEyeY() - 0.1D, getZ());
        Vec3 to = new Vec3(target.getX(), target.getY() + target.getBbHeight() * 0.5D, target.getZ());
        Vec3 delta = to.subtract(from);
        double flat = Math.sqrt(delta.x * delta.x + delta.z * delta.z);
        // A lobbed arc: enough vertical lift to clear a fence, proportional to the range.
        egg.shoot(delta.x, delta.y + flat * 0.28D, delta.z, 1.4F, 4.0F);
        server.addFreshEntity(egg);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.CHICKEN_EGG, SoundSource.NEUTRAL, 0.7F, 1.4F);
        server.sendParticles(ParticleTypes.SMOKE, getX(), getEyeY(), getZ(), 3, 0.1D, 0.1D, 0.1D, 0.01D);
    }

    /** The AI: acquire, shoot, wait. */
    private static class ShootGoal extends Goal {
        private final TurretChickenEntity chicken;

        ShootGoal(TurretChickenEntity chicken) {
            this.chicken = chicken;
            setFlags(EnumSet.noneOf(Flag.class)); // does not block movement or look control
        }

        @Override
        public boolean canUse() {
            if (chicken.cooldown > 0) {
                chicken.cooldown--;
                return false;
            }
            // Fire at whatever it is staring down; fall back to a fresh scan so a bird that has not run its
            // acquire goal yet still shoots at something standing in front of it.
            if (chicken.mark != null && chicken.mark.isAlive()
                    && ChickenTactics.shouldShoot(chicken.markDistance())) {
                return true;
            }
            return chicken.level() instanceof ServerLevel server
                    && CluckerStrike.findTarget(server, chicken.position(), RANGE) != null;
        }

        @Override
        public void start() {
            if (!(chicken.level() instanceof ServerLevel server)) {
                return;
            }
            Entity target = chicken.mark != null && chicken.mark.isAlive()
                    && ChickenTactics.shouldShoot(chicken.markDistance())
                    ? chicken.mark
                    : CluckerStrike.findTarget(server, chicken.position(), RANGE);
            if (target != null) {
                chicken.shootAt(target);
            }
            chicken.cooldown = COOLDOWN;
        }
    }

    // ------------------------------------------------------------------ the three new goals

    /**
     * ACQUIRE: find an enemy, and keep it.
     *
     * Scans on a timer rather than every tick (50 chickens × 20 scans a second is how a warhead turns into
     * a server stall), and holds the mark for {@link ChickenTactics#TARGET_MEMORY} ticks after it was last
     * seen so a bird does not lose the plot behind a wall.
     */
    private static class AcquireGoal extends Goal {
        private final TurretChickenEntity chicken;
        private int scan;

        AcquireGoal(TurretChickenEntity chicken) {
            this.chicken = chicken;
            setFlags(EnumSet.noneOf(Flag.class));
        }

        @Override
        public boolean canUse() {
            if (!(chicken.level() instanceof ServerLevel)) {
                return false;
            }
            return chicken.markVisible() || --scan <= 0;
        }

        @Override
        public void start() {
            scan = ChickenTactics.SCAN_INTERVAL;
            if (chicken.markVisible()) {
                chicken.refreshMark();
                return;
            }
            if (chicken.level() instanceof ServerLevel server) {
                chicken.setMark(CluckerStrike.findTarget(server, chicken.position(),
                        ChickenTactics.SIGHT_RANGE));
            }
        }
    }

    /**
     * STARE: face the enemy, always.
     *
     * Runs whenever there is a remembered mark in range, including while walking and while shooting, so
     * the bird is looking at you the entire time it is coming. `Flag.LOOK` means it owns the head and the
     * body yaw without fighting the move goal for it.
     */
    private static class StareGoal extends Goal {
        private final TurretChickenEntity chicken;

        StareGoal(TurretChickenEntity chicken) {
            this.chicken = chicken;
            setFlags(EnumSet.of(Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            return chicken.markVisible()
                    && ChickenTactics.shouldStare(chicken.markDistance(), true);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }

        @Override
        public void tick() {
            if (chicken.mark == null) {
                return;
            }
            if (chicken.hasLineOfSight(chicken.mark)) {
                chicken.refreshMark();
            }
            chicken.getLookControl().setLookAt(chicken.mark,
                    ChickenTactics.STARE_TURN_SPEED, ChickenTactics.STARE_TURN_SPEED);
        }
    }

    /**
     * CHASE: close the distance, then hold it.
     *
     * Pathfinding every tick is the classic way to make a mob stutter on a corner, so this re-paths on a
     * timer, and stops issuing moves once it is inside {@link ChickenTactics#KEEP_DISTANCE} - at which
     * point the stare goal keeps the bird turned toward you and the shoot goal keeps it firing.
     */
    private static class ChaseGoal extends Goal {
        private final TurretChickenEntity chicken;
        private int repath;

        ChaseGoal(TurretChickenEntity chicken) {
            this.chicken = chicken;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return chicken.markVisible()
                    && ChickenTactics.shouldChase(chicken.markDistance());
        }

        @Override
        public boolean canContinueToUse() {
            return chicken.markVisible()
                    && chicken.markDistance() > ChickenTactics.KEEP_DISTANCE;
        }

        @Override
        public void start() {
            repath = 0;
        }

        @Override
        public void stop() {
            chicken.getNavigation().stop();
        }

        @Override
        public void tick() {
            if (chicken.mark == null) {
                return;
            }
            if (chicken.hasLineOfSight(chicken.mark)) {
                chicken.refreshMark();
            }
            if (--repath <= 0) {
                repath = ChickenTactics.REPATH_INTERVAL;
                chicken.getNavigation().moveTo(chicken.mark, ChickenTactics.CHASE_SPEED);
            }
        }
    }

    /** Convenience for the warhead: spawn one at a position. */
    public static TurretChickenEntity spawn(ServerLevel level, Vec3 at) {
        TurretChickenEntity chicken =
                (TurretChickenEntity) SCGEntities.TURRET_CHICKEN.get().create(level);
        if (chicken == null) {
            return null;
        }
        chicken.moveTo(at.x, at.y, at.z, level.getRandom().nextFloat() * 360.0F, 0.0F);
        level.addFreshEntity(chicken);
        return chicken;
    }
}
