package com.shadowclangaming.spell;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Lingering spell effects: ZONE steps and RUNE steps.
 *
 * ──────────────────── THE RULE, AS SPECIFIED ────────────────────
 *
 *   A ZONE lasts 30 seconds and re-casts the rest of its spell from its own position every 5 seconds -
 *   six pulses. Standing in the wrong zone is therefore not a single hit, it is a beating.
 *
 *   A RUNE is a trap. It waits (5 minutes untriggered), fires the rest of its spell once when a living
 *   thing walks within 1.5 blocks, and is consumed.
 *
 * Both store the spell PLUS the step index they resume at, so "projectile, then zone, then rune" works:
 * the zone holds the step after itself, and the rune it creates holds the step after that.
 *
 * Storage is one SavedData on the overworld, exactly like the blast scars, so zones survive a restart.
 * Ticking is a single pass per server tick from the mod's listener; with no zones or runes alive it costs
 * one list-size check.
 */
public final class SpellZones extends SavedData {

    /** How far a zone looks for victims when it pulses. */
    public static final double ZONE_LOOK_RADIUS = 6.0D;
    /** How close something has to get to set off a rune. */
    public static final double RUNE_TRIGGER_RADIUS = 1.5D;
    /** Hard cap so a griefer cannot fill the world with zones. */
    public static final int MAX_ACTIVE = 512;

    /**
     * One lingering spell. {@code pulseTicks} is ignored for runes.
     *
     * The interval is stored per zone rather than read from a constant, because power changes it: a
     * zone cast at x2.14 power pulses every 3 seconds while a plain zone pulses every 5. It is saved
     * too, so a zone that survives a restart keeps the rhythm it was cast with.
     */
    public record Lingering(boolean rune, ResourceKey<Level> dimension, Vec3 position, Spell spell,
                            int fromStep, float power, UUID caster, double radius,
                            long expiresAt, long nextPulse, int pulseTicks) {

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putBoolean("Rune", rune);
            tag.putString("Dim", dimension.location().toString());
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
            Spell.CODEC.encodeStart(NbtOps.INSTANCE, spell).result()
                    .ifPresent(encoded -> tag.put("Spell", encoded));
            tag.putInt("From", fromStep);
            tag.putFloat("Power", power);
            if (caster != null) {
                tag.putUUID("Caster", caster);
            }
            tag.putDouble("Radius", radius);
            tag.putLong("Expires", expiresAt);
            tag.putLong("NextPulse", nextPulse);
            tag.putInt("PulseTicks", pulseTicks);
            return tag;
        }

        static Lingering load(CompoundTag tag) {
            Spell spell = Spell.CODEC.parse(NbtOps.INSTANCE, tag.get("Spell"))
                    .result().orElse(Spell.empty());
            ResourceKey<Level> dimension = ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION,
                    net.minecraft.resources.ResourceLocation.parse(tag.getString("Dim")));
            return new Lingering(
                    tag.getBoolean("Rune"),
                    dimension,
                    new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                    spell,
                    tag.getInt("From"),
                    tag.getFloat("Power"),
                    tag.contains("Caster") ? tag.getUUID("Caster") : null,
                    tag.getDouble("Radius"),
                    tag.getLong("Expires"),
                    tag.getLong("NextPulse"),
                    // Zones saved before power scaling existed have no PulseTicks: fall back to the
                    // plain interval rather than pulsing every tick.
                    tag.contains("PulseTicks") ? tag.getInt("PulseTicks")
                            : SpellShape.ZONE_PULSE_SECONDS * 20);
        }
    }

    private final List<Lingering> active = new ArrayList<>();

    public static SpellZones get(MinecraftServer server) {
        ServerLevel storage = server.overworld();
        return storage.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SpellZones::new, SpellZones::load),
                "shadowclangaming_spell_zones");
    }

    private static SpellZones load(CompoundTag tag, HolderLookup.Provider registries) {
        SpellZones data = new SpellZones();
        ListTag list = tag.getList("Zones", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.active.add(Lingering.load(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Lingering lingering : active) {
            list.add(lingering.save());
        }
        tag.put("Zones", list);
        return tag;
    }

    // ------------------------------------------------------------------ creation

    /**
     * Leave a zone behind.
     *
     * POWER CHANGES THE SHAPE OF THE ZONE, not just its damage: every power level is one more second of
     * life and a quarter second less between pulses (see {@link SpellShape#zoneSeconds} and
     * {@link SpellShape#zonePulseTicks}). At x1.0 that is the familiar 30 s / 5 s; a level-8 assembly
     * leaves a 38-second zone firing every 3 s, which is twelve pulses instead of six.
     */
    public static void addZone(ServerLevel level, Vec3 position, Spell spell, int fromStep, float power,
                               UUID caster, double radius) {
        addZone(level, position, spell, fromStep, power, caster, radius, 1.0D);
    }

    /** @param durationScale the step's LASTING modifier: 1.0 plain, 1.5 with the modifier */
    public static void addZone(ServerLevel level, Vec3 position, Spell spell, int fromStep, float power,
                               UUID caster, double radius, double durationScale) {
        long now = level.getGameTime();
        int pulseTicks = SpellShape.zonePulseTicks(power);
        add(level, new Lingering(false, level.dimension(), position, spell, fromStep, power, caster,
                radius <= 0.0D ? ZONE_LOOK_RADIUS : radius,
                now + (long) (SpellShape.zoneSeconds(power) * 20L * Math.max(1.0D, durationScale)),
                // First pulse is one pulse-interval out: the zone has just been created by the step
                // that would have applied its components, so pulsing immediately would double-hit.
                now + pulseTicks,
                pulseTicks));
    }

    public static void addRune(ServerLevel level, Vec3 position, Spell spell, int fromStep, float power,
                               UUID caster) {
        addRune(level, position, spell, fromStep, power, caster, 1.0D);
    }

    public static void addRune(ServerLevel level, Vec3 position, Spell spell, int fromStep, float power,
                               UUID caster, double durationScale) {
        long now = level.getGameTime();
        add(level, new Lingering(true, level.dimension(), position, spell, fromStep, power, caster,
                RUNE_TRIGGER_RADIUS,
                now + (long) (SpellShape.RUNE_SECONDS * 20L * Math.max(1.0D, durationScale)), 0L, 0));
    }

    private static void add(ServerLevel level, Lingering lingering) {
        SpellZones data = get(level.getServer());
        if (data.active.size() >= MAX_ACTIVE) {
            // Drop the oldest one rather than refusing to cast: a full world of zones is already a mess.
            data.active.remove(0);
            ShadowClanGaming.LOGGER.warn("Spell zone limit ({}) reached; oldest zone dropped", MAX_ACTIVE);
        }
        data.active.add(lingering);
        data.setDirty();
    }

    /** How many zones and runes are alive right now - used by the perf log and the commands. */
    public static int activeCount(MinecraftServer server) {
        return get(server).active.size();
    }

    // ------------------------------------------------------------------ ticking

    /**
     * Called once per server tick. Expires, pulses and triggers.
     *
     * ──────────────────── THE CRASH THIS FIXES ────────────────────
     *
     * This used to walk the list with an Iterator and call {@code iterator.remove()} to re-queue a
     * pulsed zone. That is fine right up until the spell inside the pulse contains a ZONE or RUNE step
     * of its own - which appends to the very list being iterated - and then the next
     * {@code iterator.remove()} throws ConcurrentModificationException and takes the server down.
     * A "projectile, then a zone that keeps re-casting a zone" spell did exactly that on its first
     * pulse (the reported crash, SpellZones.tick line 260).
     *
     * The fix is to stop using an iterator:
     *
     *   - index-based access, so appends during the pass cannot invalidate anything;
     *   - re-queueing is a {@code set(i, ...)} in place instead of remove-then-append, which keeps the
     *     cast order and cannot grow the list behind our back;
     *   - removal is index-based and steps {@code i} back so nothing is skipped.
     *
     * Entries appended DURING a pass are simply visited later in the same pass - their next pulse is
     * in the future, so they are skipped, which is exactly right.
     */
    public static void tick(MinecraftServer server) {
        SpellZones data = get(server);
        if (data.active.isEmpty()) {
            return;
        }
        boolean changed = false;

        for (int i = 0; i < data.active.size(); i++) {
            Lingering lingering = data.active.get(i);
            ServerLevel level = server.getLevel(lingering.dimension());
            if (level == null) {
                data.active.remove(i);
                i--;
                changed = true;
                continue;
            }
            long now = level.getGameTime();
            if (now >= lingering.expiresAt()) {
                data.active.remove(i);
                i--;
                changed = true;
                continue;
            }

            if (lingering.rune()) {
                if (triggerRune(level, lingering)) {
                    data.active.remove(i);
                    i--;
                    changed = true;
                }
                continue;
            }

            // ZONE: pulse every 5 seconds.
            if (now < lingering.nextPulse()) {
                continue;
            }
            changed = true;
            LivingEntity caster = lingering.caster() == null ? null : findCaster(level, lingering.caster());
            // The zone's OWN components hit everything standing inside it...
            Spell.Step step = stepAt(lingering.spell(), lingering.fromStep() - 1);
            if (step != null && SpellModifier.NOVA.in(step.modifiers())) {
                SpellCasting.detonate(level, caster, lingering.position(), lingering.power());
            }
            if (step != null && !step.components().isEmpty()) {
                List<LivingEntity> victims = level.getEntitiesOfClass(LivingEntity.class,
                        new AABB(lingering.position(), lingering.position())
                                .inflate(lingering.radius()),
                        entity -> entity.isAlive() && (caster == null || entity != caster));
                for (LivingEntity victim : victims) {
                    SpellCasting.applyAll(level, caster, victim, victim.position(),
                            step.components(), lingering.power(), step.modifiers());
                }
            }
            // ...and then the REST of the spell re-runs from the zone's centre, every pulse. Whatever
            // this adds to the list (another zone, a rune, a storm) is appended safely - we are not
            // holding an iterator.
            SpellCasting.run(level, caster, null, lingering.position(), lingering.spell(),
                    lingering.fromStep(), lingering.power(), new Vec3(0.0D, -1.0D, 0.0D));

            level.sendParticles(ParticleTypes.ENCHANT, lingering.position().x,
                    lingering.position().y + 0.4D, lingering.position().z, 24,
                    lingering.radius() * 0.4D, 0.4D, lingering.radius() * 0.4D, 0.03D);

            // Re-queue IN PLACE: same slot, next pulse time.
            data.active.set(i, new Lingering(false, lingering.dimension(), lingering.position(),
                    lingering.spell(), lingering.fromStep(), lingering.power(), lingering.caster(),
                    lingering.radius(), lingering.expiresAt(),
                    now + Math.max(1, lingering.pulseTicks()), lingering.pulseTicks()));
        }

        if (changed) {
            data.setDirty();
        }
    }

    private static boolean triggerRune(ServerLevel level, Lingering rune) {
        List<LivingEntity> walkers = level.getEntitiesOfClass(LivingEntity.class,
                new AABB(rune.position(), rune.position()).inflate(RUNE_TRIGGER_RADIUS),
                LivingEntity::isAlive);
        if (walkers.isEmpty()) {
            return false;
        }
        LivingEntity caster = rune.caster() == null ? null : findCaster(level, rune.caster());
        level.sendParticles(ParticleTypes.WITCH, rune.position().x, rune.position().y + 0.3D,
                rune.position().z, 40, 0.6D, 0.3D, 0.6D, 0.05D);
        level.playSound(null, BlockPos.containing(rune.position()),
                net.minecraft.sounds.SoundEvents.ENCHANTMENT_TABLE_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 1.0F, 1.4F);
        LivingEntity trigger = walkers.get(0);
        // The rune's OWN components land on whatever tripped it - that is the trap going off.
        Spell.Step step = stepAt(rune.spell(), rune.fromStep() - 1);
        if (step != null && !step.components().isEmpty()) {
            SpellCasting.applyAll(level, caster, trigger, trigger.position(), step.components(),
                    rune.power());
        }
        // Then the rest of the sequence runs from the rune's own position.
        SpellCasting.run(level, caster, trigger, rune.position(), rune.spell(), rune.fromStep(),
                rune.power(), new Vec3(0.0D, -1.0D, 0.0D));
        return true;
    }

    private static LivingEntity findCaster(ServerLevel level, UUID caster) {
        var player = level.getServer().getPlayerList().getPlayer(caster);
        return player != null && player.level() == level ? player : null;
    }

    private static Spell.Step stepAt(Spell spell, int index) {
        return index >= 0 && index < spell.steps().size() ? spell.steps().get(index) : null;
    }
}
