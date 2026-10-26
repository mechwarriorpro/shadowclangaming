package com.shadowclangaming.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;

/**
 * BEEF SUPREME.
 *
 * "Spawns a cow with a boss health bar, 5000 health, deals 30 damage if it hits you with a ram attack,
 * however before it rams it will slow down for 5 seconds and have dirt and flame particles under its
 * feet."
 *
 * A {@link Cow} by blood - same renderer, same moo, same milk - with three things added:
 *
 *   1. A boss bar, driven by a {@link ServerBossEvent} this entity owns.
 *   2. 5000 health and 30 damage, set on the attributes rather than faked in the damage hook, so armour,
 *      enchantments and everything else in the game behave normally against it.
 *   3. A RAM: a five-second wind-up during which it slows to a crawl and kicks up dirt and flame, then a
 *      charge at the target. The wind-up is deliberate - "before it rams it will slow down" is a tell,
 *      and a tell is what turns a 30-damage hit from unfair into a skill check.
 *
 * The charge is driven here rather than by a goal, because it has to survive the goal being interrupted
 * and because it needs to apply damage exactly once per ram.
 */
public class BeefSupremeEntity extends Cow {

    /** Health, as asked for. */
    public static final double MAX_HEALTH = 5000.0D;

    // The ram's numbers live in RamTiming so they can be tested without a running world.
    public static final float RAM_DAMAGE = RamTiming.RAM_DAMAGE;
    public static final int WINDUP_TICKS = RamTiming.WINDUP_TICKS;
    public static final double CHARGE_SPEED = RamTiming.CHARGE_SPEED;
    public static final double WINDUP_SLOWDOWN = RamTiming.WINDUP_SLOWDOWN;
    public static final double RAM_RANGE = RamTiming.RAM_RANGE;
    public static final double HIT_RANGE = RamTiming.HIT_RANGE;
    public static final int RAM_COOLDOWN = RamTiming.RAM_COOLDOWN;

    private final ServerBossEvent bossEvent = new ServerBossEvent(
            Component.translatable("entity.shadowclangaming.beef_supreme"),
            BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.PROGRESS);

    /** 0 = idle, 1 = winding up, 2 = charging. */
    private int ramState;
    private int ramTicks;
    private int cooldown;
    private Vec3 chargeDirection = Vec3.ZERO;
    /** Set once per ram so one charge cannot hit the same target twice. */
    private boolean ramConnected;
    /** The base speed, so the wind-up slow-down can be undone exactly. */
    private double baseSpeed = 0.25D;

    public BeefSupremeEntity(EntityType<? extends Cow> type, Level level) {
        super(type, level);
        this.bossEvent.setProgress(1.0F);
        if (getAttribute(Attributes.MOVEMENT_SPEED) != null) {
            baseSpeed = getAttribute(Attributes.MOVEMENT_SPEED).getBaseValue();
        }
    }

    /** Attributes: the numbers this boss is defined by. */
    public static AttributeSupplier.Builder createAttributes() {
        return Cow.createAttributes()
                .add(Attributes.MAX_HEALTH, MAX_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, RAM_DAMAGE)
                .add(Attributes.ARMOR, 8.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.FOLLOW_RANGE, 48.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.3D)
                // A cow steps over 0.6 blocks; Beef Supreme steps over the 2.5 the player asked for.
                .add(Attributes.STEP_HEIGHT, RamTiming.MAX_STEP_HEIGHT);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        // Friendly fire off, players are fair game, and a normal melee swipe as the fallback attack.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.goalSelector.addGoal(2, new RamGoal(this));
        this.goalSelector.addGoal(3, new MeleeAttackGoal(this, 1.0D, true));
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide) {
            return;
        }
        if (cooldown > 0) {
            cooldown--;
        }
        switch (ramState) {
            case 1 -> windUp();
            case 2 -> charge();
            default -> {
                // Idle: nothing to do beyond normal cow business.
            }
        }
        this.bossEvent.setProgress(this.getHealth() / this.getMaxHealth());
    }

    /** Five seconds of slowing down and kicking up dirt and flame. */
    private void windUp() {
        ramTicks++;
        // Face the target for the whole wind-up: the tell includes WHERE it is about to go.
        LivingEntity target = getTarget();
        if (target != null) {
            getLookControl().setLookAt(target, 30.0F, 30.0F);
        }

        // Slowed to a crawl by a movement-speed modifier, which expires on its own if we are interrupted.
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(baseSpeed * WINDUP_SLOWDOWN);
        }

        if (level() instanceof ServerLevel server) {
            // Dirt and flame under the feet, every other tick: churning ground, then a spark.
            Vec3 at = position();
            // BLOCK particles carry their block state, so it needs the option wrapper.
            server.sendParticles(new net.minecraft.core.particles.BlockParticleOption(
                            ParticleTypes.BLOCK, net.minecraft.world.level.block.Blocks.DIRT.defaultBlockState()),
                    at.x, at.y + 0.05D, at.z, 6, 0.7D, 0.05D, 0.7D, 0.4D);
            server.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.1D, at.z, 2, 0.6D, 0.05D, 0.6D, 0.01D);
            if (ramTicks % 2 == 0) {
                server.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.1D, at.z, 4, 0.6D, 0.05D, 0.6D, 0.02D);
            }
            if (ramTicks % 20 == 0) {
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.COW_AMBIENT,
                        SoundSource.HOSTILE, 1.6F, 0.6F);
                server.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ROAR,
                        SoundSource.HOSTILE, 1.2F, 0.5F);
            }
        }

        // The state machine itself lives in RamTiming, which the harness tests - so the code that runs
        // in game and the code that is checked are the same code.
        if (RamTiming.nextState(RamTiming.WINDUP, ramTicks, false, false) == RamTiming.CHARGE) {
            beginCharge();
        }
    }

    /** Start the charge: lock the direction in now, so a sidestep actually works. */
    private void beginCharge() {
        LivingEntity target = getTarget();
        Vec3 direction;
        if (target != null) {
            direction = new Vec3(target.getX() - getX(), 0.0D, target.getZ() - getZ());
        } else {
            Vec3 look = getLookAngle();
            direction = new Vec3(look.x, 0.0D, look.z);
        }
        if (direction.lengthSqr() < 1.0E-4D) {
            direction = new Vec3(0.0D, 0.0D, 1.0D);
        }
        chargeDirection = direction.normalize();
        ramState = 2;
        ramTicks = 0;
        ramConnected = false;

        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(baseSpeed);
        }
        if (level() instanceof ServerLevel server) {
            server.playSound(null, getX(), getY(), getZ(), SoundEvents.RAVAGER_ATTACK,
                    SoundSource.HOSTILE, 2.0F, 0.8F);
        }
    }

    /** Fly forward, hit whatever is in the way once, then stop. */
    private void charge() {
        ramTicks++;
        setDeltaMovement(chargeDirection.x * CHARGE_SPEED, getDeltaMovement().y, chargeDirection.z * CHARGE_SPEED);
        hasImpulse = true;

        if (level() instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2D, getZ(), 4, 0.3D, 0.1D, 0.3D, 0.02D);
        }

        if (!ramConnected) {
            LivingEntity struck = findStruckTarget();
            if (struck != null) {
                ramConnected = true;
                struck.hurt(damageSources().mobAttack(this), RAM_DAMAGE);
                Vec3 push = chargeDirection.scale(1.4D).add(0.0D, 0.45D, 0.0D);
                struck.push(push.x, push.y, push.z);
                if (level() instanceof ServerLevel server) {
                    server.playSound(null, struck.getX(), struck.getY(), struck.getZ(),
                            SoundEvents.RAVAGER_ATTACK, SoundSource.HOSTILE, 2.0F, 0.6F);
                }
                // Connecting ends the charge: the cow has done its job and should not drag a player.
                finishRam();
                return;
            }
        }

        if (horizontalCollision) {
            // Hit something. Before giving up, try to climb it: a charge that a one-block lip stops is
            // a boss you beat with a fence, and the player asked for a 2.5 block step.
            if (!tryStepUp()) {
                finishRam();
            }
            return;
        }
        if (RamTiming.nextState(RamTiming.CHARGE, ramTicks, false, false) == RamTiming.IDLE) {
            finishRam();
        }
    }

    private void finishRam() {
        ramState = 0;
        ramTicks = 0;
        cooldown = RAM_COOLDOWN;
        setDeltaMovement(getDeltaMovement().x * 0.2D, getDeltaMovement().y, getDeltaMovement().z * 0.2D);
    }

    private LivingEntity findStruckTarget() {
        LivingEntity target = getTarget();
        if (target != null && target.isAlive()
                && target.distanceToSqr(this) <= HIT_RANGE * HIT_RANGE) {
            return target;
        }
        // Anything else in the way gets hit too - a ram does not ask for ID.
        for (LivingEntity candidate : level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(HIT_RANGE), entity -> entity != this && entity.isAlive()
                        && !(entity instanceof Cow))) {
            return candidate;
        }
        return null;
    }

    /**
     * Climb whatever is in the way of the charge, up to {@link RamTiming#MAX_STEP_HEIGHT}.
     *
     * Looks one block ahead along the charge direction, at heights from +1 up to the step limit, and
     * takes the first landing spot that is free. This is deliberately a teleport of half a block: the
     * vanilla step logic only handles 0.6, and a dash needs the climb to happen in one tick or the
     * momentum is gone.
     *
     * @return true if it found somewhere to stand and kept charging
     */
    private boolean tryStepUp() {
        Vec3 ahead = position().add(chargeDirection.scale(0.9D));
        for (double rise = 1.0D; rise <= RamTiming.MAX_STEP_HEIGHT; rise += 0.5D) {
            net.minecraft.core.BlockPos feet = net.minecraft.core.BlockPos.containing(
                    ahead.x, getY() + rise, ahead.z);
            // Free at feet height and free at head height, solid underfoot.
            boolean feetFree = level().getBlockState(feet).getCollisionShape(level(), feet).isEmpty();
            net.minecraft.core.BlockPos head = feet.above();
            boolean headFree = level().getBlockState(head).getCollisionShape(level(), head).isEmpty();
            net.minecraft.core.BlockPos below = feet.below();
            boolean floor = !level().getBlockState(below).getCollisionShape(level(), below).isEmpty();
            if (feetFree && headFree && floor) {
                setPos(ahead.x, feet.getY(), ahead.z);
                setDeltaMovement(chargeDirection.x * RamTiming.CHARGE_SPEED, 0.0D,
                        chargeDirection.z * RamTiming.CHARGE_SPEED);
                if (level() instanceof ServerLevel server) {
                    server.sendParticles(ParticleTypes.CLOUD, getX(), getY() + 0.2D, getZ(),
                            8, 0.4D, 0.1D, 0.4D, 0.05D);
                }
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ death

    /**
     * On death: one blast, then the steak.
     *
     * ORDER MATTERS. The explosion runs first and the steak is spawned afterwards, because an item
     * entity inside a blast radius is destroyed - "explodes leaving steak behind" has to mean the steak
     * survives its own explosion.
     *
     * The blast does not break blocks: a 5000-health boss that deletes a base when it dies would be a
     * griefing tool rather than a fight. It does damage and throw entities, which is the part that
     * matters. (Change {@link #breaksBlocksOnDeath()} if that is not what you want.)
     */
    @Override
    public void die(DamageSource source) {
        // Vanilla loot (raw beef, leather) is suppressed: Beef Supreme drops steak, in stacks.
        super.die(source);
        if (!(level() instanceof ServerLevel server)) {
            return;
        }

        server.explode(this, getX(), getY() + 0.5D, getZ(), RamTiming.DEATH_EXPLOSION_POWER,
                breaksBlocksOnDeath()
                        ? net.minecraft.world.level.Level.ExplosionInteraction.MOB
                        : net.minecraft.world.level.Level.ExplosionInteraction.NONE);
        server.sendParticles(ParticleTypes.EXPLOSION_EMITTER, getX(), getY() + 0.5D, getZ(),
                3, 1.5D, 1.0D, 1.5D, 0.0D);
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE,
                SoundSource.HOSTILE, 2.0F, 0.6F);

        dropSteak(server);
    }

    /** Set true if the death blast should carve terrain as well. */
    protected boolean breaksBlocksOnDeath() {
        return false;
    }

    /** "A few stacks of steak": three stacks, thrown clear of the crater. */
    private void dropSteak(ServerLevel server) {
        int remaining = RamTiming.steakTotal();
        while (remaining > 0) {
            int count = Math.min(RamTiming.STEAK_PER_STACK, remaining);
            remaining -= count;
            net.minecraft.world.item.ItemStack steak =
                    new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.COOKED_BEEF, count);
            net.minecraft.world.entity.item.ItemEntity drop = new net.minecraft.world.entity.item.ItemEntity(
                    server, getX(), getY() + 0.6D, getZ(), steak);
            drop.setDefaultPickUpDelay();
            drop.setDeltaMovement(
                    (server.getRandom().nextDouble() - 0.5D) * 0.6D,
                    0.35D + server.getRandom().nextDouble() * 0.25D,
                    (server.getRandom().nextDouble() - 0.5D) * 0.6D);
            server.addFreshEntity(drop);
        }
    }

    /** No vanilla drops: the steak burst replaces them. */
    @Override
    protected void dropCustomDeathLoot(ServerLevel level, DamageSource source, boolean recentlyHit) {
        // Intentionally empty - see dropSteak.
    }

    // ------------------------------------------------------------------ boss bar

    @Override
    public void startSeenByPlayer(ServerPlayer player) {
        super.startSeenByPlayer(player);
        this.bossEvent.addPlayer(player);
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        this.bossEvent.removePlayer(player);
    }

    @Override
    public void setCustomName(net.minecraft.network.chat.Component name) {
        super.setCustomName(name);
        this.bossEvent.setName(getDisplayName());
    }

    @Override
    public void remove(RemovalReason reason) {
        this.bossEvent.removeAllPlayers();
        // Undo the wind-up slow-down, so a killed boss does not leave its attribute modified anywhere.
        var speed = getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null) {
            speed.setBaseValue(baseSpeed);
        }
        super.remove(reason);
    }

    // ------------------------------------------------------------------ misc

    /** True while it is telegraphing: used by the warhead and by tests. */
    public boolean isWindingUp() {
        return ramState == 1;
    }

    public boolean isCharging() {
        return ramState == 2;
    }

    @Override
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    protected net.minecraft.sounds.SoundEvent getAmbientSound() {
        return SoundEvents.COW_AMBIENT;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source) {
        // No drowning, no cactus: a boss that dies to a lily pad is not a boss.
        return source.is(net.minecraft.tags.DamageTypeTags.IS_DROWNING) || super.isInvulnerableTo(source);
    }

    /** The goal that decides when to start a ram; the ram itself runs from aiStep. */
    private static class RamGoal extends Goal {
        private final BeefSupremeEntity cow;

        RamGoal(BeefSupremeEntity cow) {
            this.cow = cow;
            setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse() {
            if (cow.ramState != 0 || cow.cooldown > 0) {
                return false;
            }
            LivingEntity target = cow.getTarget();
            return target != null && target.isAlive()
                    && cow.distanceToSqr(target) <= RAM_RANGE * RAM_RANGE;
        }

        @Override
        public void start() {
            cow.ramState = 1;
            cow.ramTicks = 0;
            if (cow.level() instanceof ServerLevel server) {
                server.playSound(null, cow.getX(), cow.getY(), cow.getZ(),
                        SoundEvents.RAVAGER_ROAR, SoundSource.HOSTILE, 1.6F, 0.6F);
            }
        }

        @Override
        public boolean canContinueToUse() {
            // The ram owns itself once started; this goal only has to not fight it.
            return false;
        }
    }
}
