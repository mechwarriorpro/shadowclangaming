package com.shadowclangaming.entity;

import com.shadowclangaming.registry.SCGEntities;
import com.shadowclangaming.spell.Spell;
import com.shadowclangaming.spell.SpellCasting;
import com.shadowclangaming.spell.SpellComponent;
import com.shadowclangaming.spell.SpellShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/**
 * A spell in flight.
 *
 * Deliberately dumb: it flies in a straight line, and whatever it touches first gets the whole
 * component list. It does not explode, home in, or do anything clever - the *spell* is the content,
 * the projectile is just the postman.
 *
 * The spell itself is NOT synced to the client. Only the display item is, which is all the renderer
 * needs; the client never casts anything.
 */
public class SpellProjectileEntity extends Projectile implements ItemSupplier {

    private static final EntityDataAccessor<ItemStack> DATA_ITEM =
            SynchedEntityData.defineId(SpellProjectileEntity.class, EntityDataSerializers.ITEM_STACK);

    private static final int MAX_LIFETIME = 20 * 5;

    private Spell spell = Spell.blank(SpellShape.PROJECTILE);
    private int age;
    /** The firing assembly's power multiplier. Carried so the impact counts for what fired it. */
    private float power = 1.0F;
    /** Which step of the spell continues when this bolt lands. Set by the sequence interpreter. */
    private int continueAt;
    /**
     * The components that fire ON impact - the projectile step's own list.
     *
     * Kept separate from the rest of the spell: the bolt's components happen where it lands, and then
     * {@link #continueAt} takes over with whatever steps follow.
     */
    private List<SpellComponent> impactComponents = List.of();
    /**
     * The FIRING STEP'S modifiers, carried to the impact.
     *
     * This used to be dropped on the floor: a bolt fired from a Maximized step applied its components
     * with no modifiers at all, so the most expensive part of the build did nothing. It now travels
     * with the bolt, which is why a Maximized Fallen Down bolt lands for 1.35x.
     */
    private int impactModifiers;
    /**
     * The cluster this bolt belongs to: -1 means "not a cluster", anything else is the entity id this
     * bolt is homing onto. A cluster strike's main bolt splits into five of these where it lands.
     */
    private int homingEntityId = -1;
    /** Cluster bolts are smaller and faster than the bolt that threw them. */
    private static final double CLUSTER_HOMING_SPEED = 0.9D;
    /** How many bolts a cluster strike splits into. */
    public static final int CLUSTER_COUNT = 5;
    /** How far a cluster strike looks for targets to send its sub-bolts at. */
    private static final double CLUSTER_SEEK_RADIUS = 24.0D;

    public SpellProjectileEntity(EntityType<? extends SpellProjectileEntity> type, Level level) {
        super(type, level);
    }

    /** Client-side constructor used by the spawn packet. */
    public SpellProjectileEntity(Level level, double x, double y, double z) {
        this(SCGEntities.SPELL_PROJECTILE.get(), level);
        this.setPos(x, y, z);
    }

    /** Fire a spell. Called from {@link SpellCasting#castFromRifle}. */
    public static void launch(ServerLevel level, LivingEntity caster, Spell spell, Vec3 from, Vec3 look) {
        launch(level, caster, spell, from, look, 1.0F);
    }

    /** @param power the emitter assembly's multiplier, applied when the bolt lands */
    public static void launch(ServerLevel level, LivingEntity caster, Spell spell, Vec3 from, Vec3 look,
                              float power) {
        launch(level, caster, spell, from, look, power, spell.stepCount(), List.of(), 1.6D);
    }

    /**
     * Launch a bolt that carries the REST of a spell sequence.
     *
     * @param continueAt         the step index the spell resumes at when the bolt lands
     * @param impactComponents   the projectile step's own components, applied at the impact point
     */
    public static void launch(ServerLevel level, LivingEntity caster, Spell spell, Vec3 from, Vec3 look,
                              float power, int continueAt,
                              List<SpellComponent> impactComponents) {
        launch(level, caster, spell, from, look, power, continueAt, impactComponents, 0, 1.6D, -1);
    }

    /** @param speed blocks per tick - 1.6 is a normal bolt, 3.2 with the SWIFT modifier */
    public static void launch(ServerLevel level, LivingEntity caster, Spell spell, Vec3 from, Vec3 look,
                              float power, int continueAt,
                              List<SpellComponent> impactComponents, double speed) {
        spawn(level, caster, spell, from, look, power, continueAt, impactComponents, 0, -1, speed);
    }

    /**
     * Full form: the step's modifiers travel with the bolt, and the bolt can be told what to home on.
     *
     * @param impactModifiers  the firing step's modifier mask, applied when the bolt lands
     * @param homingEntityId   entity id to steer toward, or -1 for a straight flier (SEEKER passes one)
     */
    public static void launch(ServerLevel level, LivingEntity caster, Spell spell, Vec3 from, Vec3 look,
                              float power, int continueAt, List<SpellComponent> impactComponents,
                              int impactModifiers, double speed, int homingEntityId) {
        spawn(level, caster, spell, from, look, power, continueAt, impactComponents,
                impactModifiers, homingEntityId, speed);
    }

    /**
     * The one place a bolt is actually built. {@link #launch} and the cluster split both come through
     * here so a sub-bolt cannot accidentally be configured differently from a fired one.
     *
     * @param homingEntityId entity id this bolt steers toward, or -1 for a straight flier
     * @param speed          blocks per tick
     */
    private static SpellProjectileEntity spawn(ServerLevel level, LivingEntity caster, Spell spell, Vec3 from,
                                               Vec3 direction, float power, int continueAt,
                                               List<SpellComponent> components,
                                               int impactModifiers, int homingEntityId, double speed) {
        SpellProjectileEntity projectile = new SpellProjectileEntity(level, from.x, from.y, from.z);
        projectile.spell = spell;
        projectile.power = Math.max(0.1F, power);
        projectile.continueAt = Math.max(0, continueAt);
        projectile.impactComponents = components == null ? List.of() : List.copyOf(components);
        projectile.impactModifiers = impactModifiers;
        projectile.homingEntityId = homingEntityId;
        projectile.setOwner(caster);
        Vec3 unit = direction.lengthSqr() < 1.0E-6D ? new Vec3(0.0D, -1.0D, 0.0D) : direction.normalize();
        projectile.setDeltaMovement(unit.scale(speed));
        projectile.setItem(new ItemStack(Items.AMETHYST_SHARD));
        level.addFreshEntity(projectile);
        return projectile;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_ITEM, ItemStack.EMPTY);
    }

    @Override
    public void tick() {
        super.tick();

        if (++age > MAX_LIFETIME) {
            if (!this.level().isClientSide) {
                impact(this.position());
            }
            return;
        }

        if (this.level().isClientSide) {
            this.level().addParticle(ParticleTypes.END_ROD, getX(), getY(), getZ(), 0.0D, 0.0D, 0.0D);
            return;
        }

        // A cluster bolt steers: if its target is still alive it flies straight at it, otherwise it
        // keeps the line it was launched on and hits whatever is in the way.
        if (homingEntityId >= 0) {
            var homingTarget = this.level().getEntity(homingEntityId);
            if (homingTarget instanceof LivingEntity living && living.isAlive()) {
                Vec3 steer = living.getBoundingBox().getCenter().subtract(position());
                if (steer.lengthSqr() > 0.01D) {
                    setDeltaMovement(steer.normalize().scale(CLUSTER_HOMING_SPEED));
                }
            } else {
                homingEntityId = -1;
            }
        }

        Vec3 from = position();
        Vec3 to = from.add(getDeltaMovement());

        // Anything alive in the way? Nearest wins.
        LivingEntity victim = null;
        double nearest = Double.MAX_VALUE;
        for (LivingEntity candidate : this.level().getEntitiesOfClass(LivingEntity.class,
                new AABB(from, to).inflate(0.4D),
                entity -> entity != getOwner() && entity.isAlive() && entity.isPickable())) {
            var clip = candidate.getBoundingBox().inflate(0.3D).clip(from, to);
            if (clip.isPresent() && from.distanceToSqr(clip.get()) < nearest) {
                nearest = from.distanceToSqr(clip.get());
                victim = candidate;
            }
        }

        BlockHitResult blockHit = this.level().clip(new ClipContext(from, to, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, this));
        boolean blockHitSoon = blockHit.getType() != HitResult.Type.MISS;

        if (victim != null && (!blockHitSoon
                || from.distanceToSqr(blockHit.getLocation()) >= nearest)) {
            impact(victim.getBoundingBox().getCenter(), victim);
            return;
        }
        if (blockHitSoon) {
            impact(blockHit.getLocation());
            return;
        }

        this.setPos(to.x, to.y, to.z);
    }

    private void impact(Vec3 point) {
        impact(point, null);
    }

    private void impact(Vec3 point, LivingEntity victim) {
        if (this.level() instanceof ServerLevel serverLevel) {
            LivingEntity caster = getOwner() instanceof LivingEntity living ? living : null;
            if (homingEntityId < 0 && impactComponents.contains(SpellComponent.CLUSTER_STRIKE)) {
                // Cluster strike: this bolt is the delivery vehicle, not the payload. It splits into
                // five bolts here, each carrying a full copy of the rest of the spell.
                splitIntoCluster(serverLevel, caster, point, victim);
                serverLevel.sendParticles(ParticleTypes.CRIT, point.x, point.y, point.z, 60,
                        0.6D, 0.6D, 0.6D, 0.4D);
                serverLevel.playSound(null, BlockPos.containing(point), net.minecraft.sounds.SoundEvents.FIREWORK_ROCKET_BLAST,
                        net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.4F);
                this.discard();
                return;
            }
            // NOVA detonates where the bolt lands, before the payload resolves.
            if (com.shadowclangaming.spell.SpellModifier.NOVA.in(impactModifiers)) {
                SpellCasting.detonate(serverLevel, caster, point, power);
            }
            if (!impactComponents.isEmpty()) {
                SpellCasting.applyAll(serverLevel, caster, victim, point, impactComponents, power,
                        impactModifiers);
            }
            // ...and the rest of the sequence continues from here: a bolt that lands on a zone step
            // leaves a zone, one that lands on a rune step arms a rune.
            SpellCasting.run(serverLevel, caster, victim, point, spell, continueAt, power,
                    new Vec3(0.0D, -1.0D, 0.0D));
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    point.x, point.y, point.z, 24, 0.3D, 0.3D, 0.3D, 0.06D);
            serverLevel.sendParticles(ParticleTypes.ENCHANT,
                    point.x, point.y, point.z, 20, 0.4D, 0.4D, 0.4D, 0.05D);
            serverLevel.playSound(null, BlockPos.containing(point), net.minecraft.sounds.SoundEvents.AMETHYST_BLOCK_BREAK,
                    net.minecraft.sounds.SoundSource.PLAYERS, 1.0F, 1.2F);
        }
        this.discard();
    }

    /**
     * Turn one bolt into five.
     *
     * The entity this bolt hit is target number one; up to four more are taken from whatever living
     * things are within 24 blocks, nearest first. Every sub-bolt carries the rest of the sequence
     * ({@link #continueAt}) and this step's components minus CLUSTER_STRIKE itself - otherwise the
     * first sub-bolt to land would split again, and a five-bolt cluster would become an infinite one.
     *
     * When there are fewer targets than bolts, the spare bolts are fanned out around the impact
     * direction instead of being wasted: they will hit whatever is behind the fight.
     */
    private void splitIntoCluster(ServerLevel level, LivingEntity caster, Vec3 point, LivingEntity victim) {
        List<SpellComponent> carried = new java.util.ArrayList<>(impactComponents);
        carried.remove(SpellComponent.CLUSTER_STRIKE);

        List<LivingEntity> targets = new java.util.ArrayList<>();
        if (victim != null && victim.isAlive()) {
            targets.add(victim);
        }
        List<LivingEntity> nearby = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(point, point).inflate(CLUSTER_SEEK_RADIUS),
                entity -> entity.isAlive() && entity != caster && entity != victim && entity.isPickable());
        nearby.sort(java.util.Comparator.comparingDouble(entity -> entity.distanceToSqr(point)));
        for (LivingEntity candidate : nearby) {
            if (targets.size() >= CLUSTER_COUNT) {
                break;
            }
            targets.add(candidate);
        }

        Vec3 forward = getDeltaMovement().lengthSqr() < 1.0E-6D
                ? new Vec3(0.0D, -1.0D, 0.0D)
                : getDeltaMovement().normalize();
        for (int i = 0; i < CLUSTER_COUNT; i++) {
            if (i < targets.size()) {
                LivingEntity target = targets.get(i);
                Vec3 aim = target.getBoundingBox().getCenter().subtract(point);
                spawn(level, caster, spell, point, aim, power, continueAt, carried,
                        impactModifiers, target.getId(), CLUSTER_HOMING_SPEED);
            } else {
                // Fan the spares out evenly around the direction the main bolt was travelling.
                double spread = (Math.PI * 2.0D / CLUSTER_COUNT) * i;
                Vec3 direction = forward.yRot((float) spread).add(0.0D, -0.15D, 0.0D);
                spawn(level, caster, spell, point, direction, power, continueAt, carried,
                        impactModifiers, -1, CLUSTER_HOMING_SPEED);
            }
        }
    }

    public void setSpell(Spell spell) {
        this.spell = spell;
    }

    public Spell spell() {
        return spell;
    }

    @Override
    public ItemStack getItem() {
        return this.entityData.get(DATA_ITEM);
    }

    public void setItem(ItemStack stack) {
        this.entityData.set(DATA_ITEM, stack);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket(ServerEntity serverEntity) {
        return new ClientboundAddEntityPacket(this, serverEntity);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        age = tag.getInt("Age");
        homingEntityId = tag.contains("Homing") ? tag.getInt("Homing") : -1;
        impactModifiers = tag.getInt("ImpactModifiers");
        Tag spellTag = tag.get("Spell");
        if (spellTag != null) {
            Spell.CODEC.parse(NbtOps.INSTANCE, spellTag).result().ifPresent(this::setSpell);
        }
        Tag itemTag = tag.get("Item");
        if (itemTag != null) {
            ItemStack.CODEC.parse(NbtOps.INSTANCE, itemTag).result().ifPresent(this::setItem);
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("Age", age);
        tag.putInt("Homing", homingEntityId);
        tag.putInt("ImpactModifiers", impactModifiers);
        Spell.CODEC.encodeStart(NbtOps.INSTANCE, spell).result().ifPresent(saved -> tag.put("Spell", saved));
        ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, getItem()).result().ifPresent(saved -> tag.put("Item", saved));
    }
}
