package com.shadowclangaming.entity;

import com.shadowclangaming.registry.SCGEntities;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;

/**
 * A guided missile.
 *
 * ──────────────────── FLIGHT MODEL ────────────────────
 *
 * {@link Projectile} in 1.21.1 does NOT move itself. Its {@code tick()} is bookkeeping only: subclasses
 * are expected to move. (Compare {@code AbstractArrow}, {@code ThrowableProjectile} - both step their own
 * position.) The first build of this class steered {@code deltaMovement} every tick and never applied it,
 * so missiles sat exactly where the pad spawned them with a velocity nobody used, and only ever went off
 * because they happened to be standing inside a block. That is fixed here: every tick the missile
 *
 *   1. picks an aim direction (vertical boost first, then a turn toward the target),
 *   2. asks {@link ProjectileUtil} what it is about to hit,
 *   3. lets {@code Projectile} route that hit to {@link #onHitBlock}/{@link #onHitEntity} (which detonate),
 *   4. steps its position by {@code deltaMovement},
 *   5. keeps the chunk under it loaded, so a long shot does not freeze in unloaded terrain.
 *
 * The two-phase profile is deliberate and is what ICBM-era missiles felt like: a hard vertical climb off
 * the pad, then a pitch over toward the target and a fast run in. A missile fired at something 20 blocks
 * away skips the drama because the boost is cut short when the target is close.
 */
public class MissileEntity extends Projectile implements ItemSupplier {
    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.ITEM_STACK);
    /** Synced so the client-side renderer can tint the nose and fins per warhead. */
    private static final EntityDataAccessor<Integer> DATA_WARHEAD =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.INT);
    /** Synced so a stalled or accelerating missile renders a bigger or smaller plume. */
    private static final EntityDataAccessor<Float> DATA_THROTTLE =
            SynchedEntityData.defineId(MissileEntity.class, EntityDataSerializers.FLOAT);

    /** Never slower than this, whatever the item says - a zero-speed missile is a bug, not a weapon. */
    private static final double MIN_SPEED = 0.35D;
    /**
     * Gravity for the flight, in blocks per tick squared.
     *
     * This is the single most important number in the class. The first version of this missile had no
     * gravity at all: it steered straight at the target, so every shot was a line - up a little, then a
     * beeline. A missile that ignores gravity cannot arc, and an ICBM that does not arc does not read as
     * an ICBM. 0.06 with the speeds below gives a flight of roughly 6 seconds for 200 blocks and 16
     * seconds for 2000, which is the pacing the old mods had.
     */
    private static final double GRAVITY = 0.06D;
    /** How high above the launch point an arc peaks, as a function of range. */
    private static final double APEX_BASE = 40.0D;
    private static final double APEX_PER_BLOCK = 0.35D;
    private static final double APEX_MAX = 700.0D;
    /** Inside this range of the target, stop flying the arc and dive onto it. */
    private static final double TERMINAL_RANGE = 40.0D;
    /** Absolute ceiling on horizontal speed so a long shot still looks like a missile. */
    private static final double MAX_SPEED = 8.0D;

    private BlockPos target;
    private WarheadType warhead = WarheadType.HE;
    private float explosionPower = 6.0F;
    private long funding = 0L;
    private double cruiseSpeed = 0.75D;
    /** Set for SPELL warheads only; every other warhead ignores it. */
    private Spell spell;
    /** Set for CARGO warheads only: what the chest will contain. */
    private java.util.List<ItemStack> cargo = java.util.List.of();
    /** The pad's radius slider at launch, or -1 for "let the warhead decide". */
    private int sliderRadius = -1;
    /** Stable seed for a constructor warhead's blend, so the same missile always builds the same way. */
    private long buildSeed = 0L;
    /** Set for TRACKING warheads (or any missile a Tracker aimed): the entity it follows. */
    private java.util.UUID trackedEntity;
    /** Target dimension for an interdimensional flight, or null for a normal launch. */
    private net.minecraft.resources.ResourceKey<Level> targetDimension;
    /** Which level the missile is currently in - compared against the target to trigger a jump. */
    private boolean jumped;
    private int age = 0;
    private int maxAge = 20 * 60;
    /** Ballistic solution, computed once by {@link #plan()} on the first flight tick. */
    private boolean planned = false;
    private double verticalSpeed;
    private double horizontalSpeed;
    /** The range the solution was built for, so a tracked target can trigger a re-plan if it runs. */
    private double plannedRange = -1.0D;
    private boolean detonated = false;
    /** Set before spawning children, so a failed discard or re-entrant tick cannot split twice. */
    private boolean clusterSplit = false;

    /**
     * True while this missile is sitting in a silo instead of flying.
     *
     * A LOADED MISSILE IS AN ENTITY, not an item hidden in a block. That is the whole point of the silo:
     * you can see what is armed, the clamps hold it, and a player can walk up to it. The pad keeps the
     * entity's UUID, so the pairing survives a chunk unload and a restart; the entity keeps the item
     * stack, so the warhead, funding and payload have exactly one home.
     */
    /**
     * What the airframe is for.
     *
     * Read from the item stack on spawn and again on launch, so a module that is swapped in the silo also
     * swaps the flight behaviour. Sub-munitions force this back to STANDARD, or a cluster frame would
     * split forever.
     */
    private com.shadowclangaming.launcher.ModuleKind kind = com.shadowclangaming.launcher.ModuleKind.STANDARD;
    /** Ticks until a homing frame re-acquires. */
    private int homingCooldown;

    private boolean docked = false;
    /** The pad's position, so a docked missile is drawn where the pad is even after a reload. */
    private BlockPos dockPos = null;
    /** Chunks this missile is holding loaded with PORTAL tickets, so a long flight does not freeze. */
    private final java.util.Set<ChunkPos> tickets = new java.util.HashSet<>();

    public MissileEntity(EntityType<? extends MissileEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
        this.noPhysics = false;
    }

    public MissileEntity(Level level, double x, double y, double z, BlockPos target, ItemStack displayStack,
                         WarheadType warhead, float explosionPower, double cruiseSpeed, long funding,
                         Spell spell, java.util.List<ItemStack> cargo, java.util.UUID trackedEntity,
                         net.minecraft.resources.ResourceKey<Level> targetDimension) {
        this(level, x, y, z, target, displayStack, warhead, explosionPower, cruiseSpeed, funding,
                spell, cargo, trackedEntity, targetDimension, -1, 0L);
    }

    public MissileEntity(Level level, double x, double y, double z, BlockPos target, ItemStack displayStack,
                         WarheadType warhead, float explosionPower, double cruiseSpeed, long funding,
                         Spell spell, java.util.List<ItemStack> cargo, java.util.UUID trackedEntity,
                         net.minecraft.resources.ResourceKey<Level> targetDimension,
                         int sliderRadius, long buildSeed) {
        this(SCGEntities.MISSILE.get(), level);
        this.setPos(x, y, z);
        this.target = target;
        this.warhead = warhead;
        this.explosionPower = explosionPower;
        this.cruiseSpeed = Math.max(MIN_SPEED, cruiseSpeed);
        this.funding = funding;
        this.spell = spell;
        this.cargo = cargo == null ? java.util.List.of() : java.util.List.copyOf(cargo);
        this.trackedEntity = trackedEntity;
        this.targetDimension = targetDimension;
        this.sliderRadius = sliderRadius;
        this.buildSeed = buildSeed;
        this.setItem(displayStack);
        this.kind = com.shadowclangaming.item.MissileModuleItem.kindOf(displayStack);
        this.setWarheadClient(warhead);
        this.setYRot(0.0F);
        this.setXRot(-90.0F);
        // Nothing is known about the arc yet - plan() runs on the first server tick, when the position
        // and the target are both final. A small upward nudge means a missile never looks stationary
        // for the one tick before that.
        this.setDeltaMovement(0.0D, MIN_SPEED, 0.0D);
        this.maxAge = lifetimeFor(Vec3.atCenterOf(target).distanceTo(new Vec3(x, y, z)), this.cruiseSpeed);
    }

    // ---------------------------------------------------------------- docked in a silo

    /** Put this missile in a silo: motionless, damage-proof, and anchored to the pad. */
    public void dock(BlockPos padPos) {
        this.docked = true;
        this.dockPos = padPos;
        this.setNoGravity(true);
        this.setInvulnerable(true);
        this.setDeltaMovement(Vec3.ZERO);
        Vec3 anchor = Vec3.atCenterOf(padPos.above());
        this.setPos(anchor.x, anchor.y, anchor.z);
        // Upright, nose up: it is on a launch rail, not in flight.
        this.setXRot(-90.0F);
        this.setYRot(0.0F);
    }

    /** The frame this missile is flying: standard, homing, cluster, high-velocity or transport. */
    public com.shadowclangaming.launcher.ModuleKind moduleKind() {
        return kind;
    }

    public boolean isDocked() {
        return docked;
    }

    /** Is this docked missile holding the pad at {@code padPos}? */
    public boolean dockedAt(BlockPos padPos) {
        return docked && padPos.equals(dockPos);
    }

    /**
     * Send a docked missile on its way.
     *
     * The entity already exists, already carries the item stack, and has already been looked at by the
     * player, so launching is "clear the dock state and hand it the target" - not "spawn a new missile and
     * delete the old one". Everything that lived on the stack (funding, spell, payload, radius override)
     * was read by the pad at this moment, so the two cannot disagree.
     */
    public void undockAndLaunch(BlockPos target, WarheadType warhead, float power, double cruiseSpeed,
                                long funding, Spell spell, java.util.List<ItemStack> cargo,
                                java.util.UUID tracked, net.minecraft.resources.ResourceKey<Level> dimension,
                                int slider, long seed) {
        this.docked = false;
        this.dockPos = null;
        this.setInvulnerable(false);
        this.kind = com.shadowclangaming.item.MissileModuleItem.kindOf(getItem());
        this.age = 0;
        this.detonated = false;
        this.planned = false;
        this.plannedRange = -1.0D;
        this.target = target;
        this.warhead = warhead;
        this.setWarheadClient(warhead);
        this.explosionPower = power;
        this.cruiseSpeed = Math.max(MIN_SPEED, cruiseSpeed);
        this.funding = funding;
        this.spell = spell;
        this.cargo = cargo == null ? java.util.List.of() : java.util.List.copyOf(cargo);
        this.trackedEntity = tracked;
        this.targetDimension = dimension;
        this.sliderRadius = slider;
        this.buildSeed = seed;
        this.jumped = false;
        this.verticalSpeed = 0.0D;
        this.horizontalSpeed = 0.0D;
        this.maxAge = lifetimeFor(Vec3.atCenterOf(target).distanceTo(position()), this.cruiseSpeed);
        this.setDeltaMovement(0.0D, MIN_SPEED, 0.0D);
    }

    /**
     * How long this flight is allowed to take.
     *
     * The old code had a flat 60 seconds, so a missile aimed 3,000 blocks away simply self-destructed in
     * mid-air. The budget is now derived from the distance and the speed, with generous slack for the boost
     * phase and for steering wobble.
     */
    private static int lifetimeFor(double distance, double speed) {
        double ticks = distance / Math.max(MIN_SPEED, speed) * 1.6D + 200.0D;
        return (int) Math.min(20 * 60 * 20, Math.max(20 * 20, ticks));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
        builder.define(DATA_WARHEAD, WarheadType.HE.ordinal());
        builder.define(DATA_THROTTLE, 0.0F);
    }

    // ---------------------------------------------------------------- client-side read access

    /** Ordinal of the warhead type - what the renderer tints the missile with. */
    public int warheadOrdinal() {
        return this.entityData.get(DATA_WARHEAD);
    }

    public float throttle() {
        return this.entityData.get(DATA_THROTTLE);
    }

    public Vec3 travelDirection() {
        Vec3 motion = getDeltaMovement();
        if (motion.lengthSqr() < 1.0E-6D) {
            return Vec3.directionFromRotation(getXRot(), getYRot());
        }
        return motion.normalize();
    }

    private void setWarheadClient(WarheadType type) {
        this.entityData.set(DATA_WARHEAD, type == null ? WarheadType.HE.ordinal() : type.ordinal());
    }

    // ---------------------------------------------------------------- flight

    @Override
    public void tick() {
        super.tick();

        if (docked) {
            // Nothing flies, nothing ages, nothing detonates. Just hold position and look armed.
            setDeltaMovement(Vec3.ZERO);
            if (dockPos != null) {
                Vec3 anchor = Vec3.atCenterOf(dockPos.above());
                if (position().distanceToSqr(anchor) > 0.0001D) {
                    setPos(anchor.x, anchor.y, anchor.z);
                }
            }
            return;
        }

        if (++age > maxAge) {
            detonate();
            return;
        }

        if (this.level().isClientSide) {
            spawnExhaust();
            return;
        }

        ServerLevel serverLevel = (ServerLevel) this.level();

        // Interdimensional leg: hop to the target's level once, then fly normally there.
        if (!jumped && targetDimension != null && !serverLevel.dimension().equals(targetDimension)) {
            jumped = true;
            ServerLevel destination = serverLevel.getServer().getLevel(targetDimension);
            if (destination != null) {
                releaseTickets(serverLevel);
                this.changeDimension(new net.minecraft.world.level.portal.DimensionTransition(
                        destination, position(), getDeltaMovement(), getYRot(), getXRot(),
                        net.minecraft.world.level.portal.DimensionTransition.DO_NOTHING));
                return;
            }
            // No such dimension: fall through and fly at the coordinates anyway.
        }

        keepChunkLoaded(serverLevel);

        // Tracking: if we are following an entity and it is still here, aim at IT, not the last known
        // block. A locked target that dies or leaves stops steering - the missile carries on to the last
        // place it was seen, which is the behaviour players expect.
        if (trackedEntity != null) {
            var tracked = serverLevel.getEntity(trackedEntity);
            if (tracked != null && tracked.isAlive()) {
                this.target = tracked.blockPosition();
                // A target that has run more than a quarter of the original range away invalidates the
                // arc: re-solve rather than fly the old parabola at empty ground.
                if (distanceToTarget() > Math.max(48.0D, plannedRange * 1.25D)) {
                    planned = false;
                }
            }
        }

        // HOMING FRAME: chase, do not dead-reckon.
        //
        // A plain missile flies at the block it was aimed at, which is the right answer to a static
        // target and the wrong answer to a running one. A homing frame re-acquires every ten ticks and
        // re-solves its arc when the prey has moved far enough for the old parabola to miss - so the
        // missile bends toward where the target IS, not where it was.
        if (kind.homing() && --homingCooldown <= 0) {
            homingCooldown = com.shadowclangaming.launcher.ModuleKind.HOMING_RETARGET;
            net.minecraft.world.entity.Entity prey = seek(serverLevel);
            if (prey != null) {
                this.target = prey.blockPosition();
                if (distanceToTarget() > 6.0D) {
                    planned = false;
                }
            }
        }

        // A missile with no target (summoned by hand, or a stray) flies where it is pointing.
        if (target == null) {
            this.target = BlockPos.containing(position().add(travelDirection().scale(2048.0D)));
        }

        if (!planned || plannedRange < 0.0D) {
            plan();
        }

        Vec3 aim = Vec3.atCenterOf(target).subtract(position());
        double distance = aim.length();
        if (distance < 2.0D) {
            detonate();
            return;
        }

        double horizontalRange = Math.sqrt(aim.x * aim.x + aim.z * aim.z);
        Vec3 horizontalDir = horizontalRange < 0.01D
                ? new Vec3(0.0D, 0.0D, 1.0D)
                : new Vec3(aim.x, 0.0D, aim.z).scale(1.0D / horizontalRange);

        // CLUSTER FRAME: break up before impact.
        //
        // Splitting at the last moment gives a spread of craters around the aim point instead of one hole
        // in it. The parent does not detonate - it becomes the pattern.
        if (kind.subMunitions() > 0 && shouldSplit(distance)) {
            splitCluster(serverLevel);
            return;
        }

        if (horizontalRange < TERMINAL_RANGE || (verticalSpeed < 0.0D && distance < 6.0D)) {
            // --- terminal dive: forget the arc, put the nose on the target and go ---
            Vec3 dive = aim.scale(1.0D / distance);
            double diveSpeed = Math.max(0.8D, Math.min(MAX_SPEED * kind.speedMultiplier(),
                    horizontalSpeed * 1.1D));
            setDeltaMovement(dive.scale(diveSpeed));
        } else {
            // --- ballistic flight: horizontal speed is constant, vertical speed obeys gravity ---
            verticalSpeed -= GRAVITY;
            // A sanity cap only: clamping the descent any harder would break the arc's timing and make
            // the missile sail past the target. Impact is detected by clipping along the move vector, so
            // a fast dive cannot tunnel through terrain.
            if (verticalSpeed < -12.0D) {
                verticalSpeed = -12.0D;
            }
            setDeltaMovement(horizontalDir.scale(horizontalSpeed).add(0.0D, verticalSpeed, 0.0D));
        }

        updateRotationFrom(getDeltaMovement());

        // --- the part that was missing in 1.0.0: actually move ---
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
        if (hit.getType() != HitResult.Type.MISS) {
            this.hitTargetOrDeflectSelf(hit);
            if (this.isRemoved()) {
                return;
            }
        }

        Vec3 motion = getDeltaMovement();
        this.setPos(getX() + motion.x, getY() + motion.y, getZ() + motion.z);
        // Catches the case where we ended the step inside a block (fast missiles skip thin geometry).
        this.checkInsideBlocks();
        if (this.isRemoved()) {
            return;
        }

        this.entityData.set(DATA_THROTTLE, (float) Math.min(1.0D, motion.length() / MAX_SPEED));
    }

    /**
     * Solve the arc.
     *
     * Pick an apex above the launch point, derive the vertical speed that reaches it under
     * {@link #GRAVITY}, then split the range over the up-and-down time to get the horizontal speed.
     * Because both halves are solved from the same gravity, the missile arrives at the target's height
     * exactly when it arrives at the target's horizontal position - which is what makes the shot land
     * *on* the aim point at the end of a real arc, instead of passing over it.
     *
     * A tracked target that runs away more than 25% of the original range triggers a fresh solution.
     */
    private void plan() {
        planned = true;
        Vec3 here = position();
        Vec3 aim = target == null ? here : Vec3.atCenterOf(target);
        double horizontalRange = Math.sqrt((aim.x - here.x) * (aim.x - here.x)
                + (aim.z - here.z) * (aim.z - here.z));
        plannedRange = horizontalRange;

        double apex = Mth.clamp(APEX_BASE + horizontalRange * APEX_PER_BLOCK, APEX_BASE, APEX_MAX);
        double rise = Math.max(8.0D, apex - (aim.y - here.y));
        verticalSpeed = Math.sqrt(2.0D * GRAVITY * apex);

        double timeUp = verticalSpeed / GRAVITY;
        double timeDown = Math.sqrt(2.0D * rise / GRAVITY);
        double flightTicks = Math.max(12.0D, timeUp + timeDown);

        // The item's cruise speed sets a floor so a "fast" missile still feels fast, but the arc's own
        // timing wins when the shot is long enough that the floor would make it overshoot.
        horizontalSpeed = Mth.clamp(horizontalRange / flightTicks, MIN_SPEED, MAX_SPEED);
        if (cruiseSpeed > horizontalSpeed && horizontalRange < 64.0D) {
            horizontalSpeed = Math.min(MAX_SPEED, cruiseSpeed);
        }
        // Frame speed. A high-velocity airframe is not a longer missile, it is a faster one: the ceiling
        // scales with it, or the multiplier would be immediately clamped away.
        horizontalSpeed = Math.min(MAX_SPEED * kind.speedMultiplier(),
                horizontalSpeed * kind.speedMultiplier());

        maxAge = (int) Math.min(20 * 60 * 20, Math.max(20 * 20, flightTicks * 2.5D + 400.0D));

        // Launch on the arc, not straight up.
        Vec3 dir = horizontalRange < 0.01D ? Vec3.ZERO
                : new Vec3(aim.x - here.x, 0.0D, aim.z - here.z).normalize();
        setDeltaMovement(dir.scale(horizontalSpeed).add(0.0D, verticalSpeed, 0.0D));
    }

    private double distanceToTarget() {
        return this.target == null ? 0.0D : Vec3.atCenterOf(this.target).distanceTo(position());
    }

    private void updateRotationFrom(Vec3 motion) {
        double horizontal = Math.sqrt(motion.x * motion.x + motion.z * motion.z);
        this.setYRot((float) (Mth.atan2(motion.x, motion.z) * (180.0D / Math.PI)));
        this.setXRot((float) (Mth.atan2(motion.y, horizontal) * (180.0D / Math.PI)) - 90.0F);
    }

    /**
     * Holds the chunk under the missile (and the one it is about to enter) with a PORTAL ticket.
     *
     * Without this a strategic shot at 3,000 blocks leaves the loaded area after a second, the chunk
     * unloads, the entity stops ticking, and the missile hangs in the air forever. Tickets are released
     * on detonation, on death and when the entity unloads.
     */
    private void keepChunkLoaded(ServerLevel level) {
        ChunkPos here = new ChunkPos(blockPosition());
        addTicket(level, here);
        Vec3 ahead = position().add(travelDirection().scale(8.0D));
        addTicket(level, new ChunkPos(BlockPos.containing(ahead)));
        // Prime the chunk the missile is standing in, so the next tick has somewhere to fly into.
        level.getChunkSource().getChunk(here.x, here.z, ChunkStatus.FULL, true);
    }

    private void addTicket(ServerLevel level, ChunkPos pos) {
        if (tickets.add(pos)) {
            level.getChunkSource().addRegionTicket(TicketType.PORTAL, pos, 2, BlockPos.containing(position()));
        }
    }

    private void releaseTickets(ServerLevel level) {
        for (ChunkPos pos : tickets) {
            level.getChunkSource().removeRegionTicket(TicketType.PORTAL, pos, 2, BlockPos.containing(position()));
        }
        tickets.clear();
    }

    private void spawnExhaust() {
        float throttle = throttle();
        Vec3 motion = getDeltaMovement();
        Vec3 back = motion.lengthSqr() > 1.0E-6D ? motion.normalize().scale(-0.45D) : Vec3.ZERO;
        if (throttle > 0.02F) {
            this.level().addParticle(ParticleTypes.FLAME,
                    this.getX() + back.x, this.getY() + back.y, this.getZ() + back.z,
                    back.x * 0.2D, back.y * 0.2D, back.z * 0.2D);
        }
        if (this.random.nextInt(2) == 0) {
            this.level().addParticle(ParticleTypes.CAMPFIRE_SIGNAL_SMOKE,
                    this.getX() + back.x, this.getY() + back.y, this.getZ() + back.z,
                    0.0D, 0.01D, 0.0D);
        }
    }

    // ---------------------------------------------------------------- impact

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        detonate();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        super.onHitEntity(result);
        detonate();
    }

    private void detonate() {
        if (detonated) {
            return;
        }
        detonated = true;

        if (this.level() instanceof ServerLevel serverLevel) {
            releaseTickets(serverLevel);
            if (kind.transport()) {
                // A personnel frame does not detonate. It ARRIVES: everyone aboard is set down on the
                // ground with their fall distance cleared, the hold is unloaded, and that is all.
                deliver(serverLevel);
            } else {
                // Anyone riding gets thrown clear BEFORE the warhead goes off, so riding a missile is a
                // stunt rather than suicide. They get slow falling and a moment of grace: the whole point
                // of riding is to watch the thing land.
                ejectRiders(serverLevel);
                // The warhead decides what "detonate" means: decoy, EMP, antimatter, money...
                warhead.detonate(serverLevel, position(), explosionPower, funding, spell, cargo,
                        sliderRadius, buildSeed);
            }
        }
        this.discard();
    }


    // ---------------------------------------------------------------- frame behaviours

    /**
     * Find something to chase: the Tracker's lock if there is one, else the nearest living thing.
     *
     * Deliberately ignores anything riding the missile - a homing weapon that locks onto its own
     * passenger and dives at the ground is a bug with a body count.
     */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.world.entity.Entity seek(ServerLevel level) {
        if (trackedEntity != null) {
            net.minecraft.world.entity.Entity locked = level.getEntity(trackedEntity);
            if (locked != null && locked.isAlive()) {
                return locked;
            }
        }
        net.minecraft.world.phys.AABB box = getBoundingBox().inflate(
                com.shadowclangaming.launcher.ModuleKind.HOMING_RANGE);
        net.minecraft.world.entity.Entity best = null;
        double bestDistance = Double.MAX_VALUE;
        for (net.minecraft.world.entity.LivingEntity candidate
                : level.getEntitiesOfClass(net.minecraft.world.entity.LivingEntity.class, box)) {
            if (!candidate.isAlive() || candidate.isSpectator()
                    || candidate.isPassengerOfSameVehicle(this)
                    || getPassengers().contains(candidate)) {
                continue;
            }
            if (candidate instanceof net.minecraft.world.entity.player.Player player
                    && (player.isCreative() || player.isSpectator())) {
                continue;
            }
            double distance = candidate.distanceToSqr(this);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = candidate;
            }
        }
        return best;
    }

    /**
     * Should this cluster warhead open up now?
     *
     * Two rules, in order:
     *
     *   1. If the missile carries a SPLIT ALTITUDE, respect it: split when the missile is DESCENDING and its
     *      Y is at or below that height. The descent check matters - a launcher on a mountain would
     *      otherwise split the moment it left the tube, because it started below its own split altitude.
     *   2. Otherwise fall back to the frame's proximity rule, which is what every cluster missile did
     *      before altitudes existed.
     *
     * @param distance current distance to the target, in blocks
     */
    private boolean shouldSplit(double distance) {
        // The rule itself is in warhead/SplitRule so the harness can prove the altitude behaviour without
        // a world: descending-only, and "below the order" never means "still in the tube".
        return com.shadowclangaming.warhead.SplitRule.shouldSplit(
                position().y, verticalSpeed,
                com.shadowclangaming.warhead.MissilePayload.splitAltitudeOf(getItem()),
                distance, com.shadowclangaming.launcher.ModuleKind.CLUSTER_TRIGGER);
    }

    /**
     * Break into sub-munitions.
     *
     * Each child is a STANDARD frame aimed at its own offset point on a ring around the target, carrying a
     * share of the parent's power and money. The kind is forced back to STANDARD before the child can act,
     * which is what stops a cluster frame from splitting again, and again, forever.
     */
    private void splitCluster(ServerLevel level) {
        // The parent remains in the world until explicitly discarded. Without this one-shot guard it reaches
        // shouldSplit() again on its next tick and creates another full set of copies forever.
        if (clusterSplit || !isAlive()) {
            return;
        }
        clusterSplit = true;
        int count = kind.subMunitions();
        com.shadowclangaming.debug.ShadowDebug.log("CLUSTER_SPLIT",
                "parent={} count={} warhead={} pos={} target={} power={} funding={}",
                getUUID(), count, warhead.id(), position(), target, explosionPower, funding);
        if (count <= 0) {
            discard();
            return;
        }
        float power = kind.subMunitionPower(explosionPower);
        long share = funding / count;
        Vec3 at = position();
        Vec3 aim = target == null ? at : Vec3.atCenterOf(target);
        for (int i = 0; i < count; i++) {
            double[] offset = kind.subMunitionOffset(i, count);
            BlockPos childTarget = BlockPos.containing(aim.x + offset[0], aim.y, aim.z + offset[1]);
            MissileEntity child = new MissileEntity(level,
                    at.x + (level.getRandom().nextDouble() - 0.5D) * 1.5D,
                    at.y + (level.getRandom().nextDouble() - 0.5D) * 1.5D,
                    at.z + (level.getRandom().nextDouble() - 0.5D) * 1.5D,
                    childTarget, getItem().copy(), warhead, power,
                    Math.max(1.1D, cruiseSpeed), share, spell,
                    i == 0 ? cargo : java.util.List.of(), trackedEntity, targetDimension,
                    sliderRadius, buildSeed + i * 17L);
            // Forced, not read from the stack: the stack is still the cluster module.
            child.kind = com.shadowclangaming.launcher.ModuleKind.STANDARD;
            child.setDeltaMovement(getDeltaMovement().scale(0.6D));
            level.addFreshEntity(child);
        }
        level.sendParticles(ParticleTypes.CLOUD, at.x, at.y, at.z, 40, 3.0D, 1.0D, 3.0D, 0.08D);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                net.minecraft.sounds.SoundSource.NEUTRAL, 2.0F, 0.7F);
        // Children are now the only live missiles from this launch. Remove the cluster carrier immediately.
        discard();
    }

    /**
     * Set the crew down, safely.
     *
     * "Negates fall damage" is done in the only way that is honest: the passenger is not dropped at all,
     * they are PLACED - teleported onto the ground surface at the impact point, with their fall distance
     * cleared and zero velocity. Slow falling is added on top as belt and braces, because a passenger who
     * is mid-motion when the world ticks them next should still not take the hit.
     */
    private void deliver(ServerLevel level) {
        Vec3 at = position();
        BlockPos landing = landingSpot(level, BlockPos.containing(at));
        Vec3 spot = Vec3.atBottomCenterOf(landing);
        for (net.minecraft.world.entity.Entity passenger : java.util.List.copyOf(getPassengers())) {
            passenger.stopRiding();
            passenger.teleportTo(spot.x, spot.y, spot.z);
            passenger.setDeltaMovement(Vec3.ZERO);
            passenger.fallDistance = 0.0F;
            passenger.resetFallDistance();
            passenger.hurtMarked = true;
            if (passenger instanceof net.minecraft.world.entity.LivingEntity living) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.SLOW_FALLING,
                        com.shadowclangaming.launcher.ModuleKind.TRANSPORT_GRACE, 0, false, false));
            }
            if (passenger instanceof net.minecraft.server.level.ServerPlayer rider) {
                rider.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.shadowclangaming.ride_transport_arrived"), true);
            }
        }
        // The hold comes out with the crew: a transport frame's cargo is luggage, and luggage that
        // vanishes on arrival is not luggage.
        for (ItemStack stack : cargo) {
            if (!stack.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, spot.x, spot.y + 0.5D, spot.z, stack);
            }
        }
        level.sendParticles(ParticleTypes.POOF, at.x, at.y + 0.5D, at.z, 30, 1.5D, 1.0D, 1.5D, 0.05D);
        level.playSound(null, at.x, at.y, at.z, net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_LARGE_BLAST,
                net.minecraft.sounds.SoundSource.NEUTRAL, 1.2F, 1.4F);
    }

    /**
     * The ground surface at or below a point, within a sane depth.
     *
     * A transport missile often comes down on a roof or into the side of a hill; landing the crew inside
     * stone would be worse than the fall damage it was meant to prevent.
     */
    private static BlockPos landingSpot(ServerLevel level, BlockPos start) {
        BlockPos.MutableBlockPos cursor = start.mutable();
        if (!level.getBlockState(cursor).blocksMotion()) {
            for (int i = 0; i < 64 && cursor.getY() > level.getMinBuildHeight(); i++) {
                cursor.move(0, -1, 0);
                if (level.getBlockState(cursor).blocksMotion()) {
                    break;
                }
            }
        }
        return cursor.above();
    }

    /** Throws every passenger up and out of the blast, with slow falling to land on. */
    private void ejectRiders(ServerLevel level) {
        if (getPassengers().isEmpty()) {
            return;
        }
        Vec3 at = position();
        for (net.minecraft.world.entity.Entity passenger : java.util.List.copyOf(getPassengers())) {
            passenger.stopRiding();
            passenger.teleportTo(at.x, at.y + 12.0D, at.z);
            passenger.setDeltaMovement(0.0D, 0.9D, 0.0D);
            passenger.hurtMarked = true;
            if (passenger instanceof net.minecraft.world.entity.LivingEntity living) {
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.SLOW_FALLING, 400, 0, false, true));
                living.addEffect(new net.minecraft.world.effect.MobEffectInstance(
                        net.minecraft.world.effect.MobEffects.FIRE_RESISTANCE, 200, 0, false, false));
            }
            if (passenger instanceof net.minecraft.server.level.ServerPlayer rider) {
                rider.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.shadowclangaming.ride_eject"), true);
            }
        }
    }

    /** Passengers on a missile are not a rendering problem: the entity is the vehicle. */
    @Override
    public boolean canAddPassenger(net.minecraft.world.entity.Entity passenger) {
        return getPassengers().isEmpty();
    }

    @Override
    public void remove(RemovalReason reason) {
        if (!this.level().isClientSide && this.level() instanceof ServerLevel serverLevel) {
            releaseTickets(serverLevel);
        }
        super.remove(reason);
    }

    /** Cargo list, read back from NBT. Capped so a hand-edited save cannot stuff a chest. */
    private static java.util.List<ItemStack> readCargo(CompoundTag tag) {
        net.minecraft.nbt.ListTag list = tag.getList("Cargo", Tag.TAG_COMPOUND);
        java.util.List<ItemStack> stacks = new java.util.ArrayList<>();
        for (int i = 0; i < list.size() && stacks.size() < 54; i++) {
            ItemStack.CODEC.parse(NbtOps.INSTANCE, list.getCompound(i))
                    .result()
                    .filter(stack -> !stack.isEmpty())
                    .ifPresent(stacks::add);
        }
        return java.util.List.copyOf(stacks);
    }

    // ---------------------------------------------------------------- item display

    public ItemStack getItem() {
        return this.entityData.get(DATA_ITEM);
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM, stack);
    }

    // ---------------------------------------------------------------- networking / nbt

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        // 1.21.1: the vanilla packet is enough - it carries id, type, position, rotation and
        // DataTracker values, which is everything a missile needs on spawn.
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distanceSqr) {
        // A strategic missile is often the only thing you can see. Do not cull it early.
        return distanceSqr < 512.0D * 512.0D;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
        warhead = WarheadType.byId(tag.getString("Warhead"));
        setWarheadClient(warhead);
        explosionPower = tag.getFloat("Power");
        cruiseSpeed = Math.max(MIN_SPEED, tag.getDouble("Speed"));
        funding = tag.getLong("Funding");
        if (tag.hasUUID("TrackedEntity")) {
            trackedEntity = tag.getUUID("TrackedEntity");
        }
        if (tag.contains("TargetDimension")) {
            targetDimension = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(tag.getString("TargetDimension")));
        }
        cargo = readCargo(tag);
        sliderRadius = tag.contains("SliderRadius") ? tag.getInt("SliderRadius") : -1;
        buildSeed = tag.getLong("BuildSeed");
        jumped = tag.getBoolean("Jumped");
        clusterSplit = tag.getBoolean("ClusterSplit");
        kind = com.shadowclangaming.launcher.ModuleKind.byId(tag.getString("Module"));
        net.minecraft.nbt.Tag spellTag = tag.get("Spell");
        if (spellTag != null) {
            Spell.CODEC.parse(NbtOps.INSTANCE, spellTag).result().ifPresent(parsed -> this.spell = parsed);
        }
        age = tag.getInt("Age");
        maxAge = tag.contains("MaxAge") ? tag.getInt("MaxAge") : 20 * 60;
        // Silo state: a docked missile must come back docked, or a reload would drop it out of the sky.
        docked = tag.getBoolean("Docked");
        dockPos = tag.contains("DockPos") ? BlockPos.of(tag.getLong("DockPos")) : null;
        if (docked) {
            setNoGravity(true);
            setInvulnerable(true);
            setDeltaMovement(Vec3.ZERO);
        }
        Tag itemTag = tag.get("Item");
        if (itemTag != null) {
            ItemStack.CODEC.parse(NbtOps.INSTANCE, itemTag).result().ifPresent(this::setItem);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        if (target != null) {
            tag.putLong("Target", target.asLong());
        }
        tag.putString("Warhead", warhead.id());
        tag.putFloat("Power", explosionPower);
        tag.putDouble("Speed", cruiseSpeed);
        tag.putLong("Funding", funding);
        if (trackedEntity != null) {
            tag.putUUID("TrackedEntity", trackedEntity);
        }
        if (targetDimension != null) {
            tag.putString("TargetDimension", targetDimension.location().toString());
        }
        net.minecraft.nbt.ListTag cargoTag = new net.minecraft.nbt.ListTag();
        for (ItemStack stack : cargo) {
            ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).result().ifPresent(cargoTag::add);
        }
        tag.put("Cargo", cargoTag);
        tag.putInt("SliderRadius", sliderRadius);
        tag.putLong("BuildSeed", buildSeed);
        tag.putBoolean("Jumped", jumped);
        tag.putBoolean("ClusterSplit", clusterSplit);
        tag.putString("Module", kind.id());
        tag.putBoolean("Docked", docked);
        if (dockPos != null) {
            tag.putLong("DockPos", dockPos.asLong());
        }
        if (spell != null) {
            Spell.CODEC.encodeStart(NbtOps.INSTANCE, spell).result().ifPresent(saved -> tag.put("Spell", saved));
        }
        tag.putInt("Age", age);
        tag.putInt("MaxAge", maxAge);
        ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, getItem())
                .result()
                .ifPresent(saved -> tag.put("Item", saved));
    }
}
