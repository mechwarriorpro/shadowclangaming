package com.shadowclangaming.warhead;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * CONTAGION warhead: a plague that gets worse as it spreads.
 *
 * The blast itself is modest. The payload is the point: everything caught in it picks up a random
 * set of symptoms, becomes a carrier, and passes it on to anything nearby. Every jump up the chain
 * adds a symptom, raises the amplifier and lengthens the timer - so a plague you ignore for a minute
 * comes back with nine symptoms at full strength.
 *
 *   generation 0 (blast)   1 symptom,  10 seconds
 *   generation 1           2 symptoms, 20 seconds
 *   generation 4           5 symptoms, 50 seconds, amplifier II
 *   generation 8           9 symptoms, 90 seconds, amplifier IV   <- the whole list
 *
 * SYMPTOMS (all vanilla, all "you cannot fight back" rather than direct damage):
 *   nausea, mining fatigue, slowness, blindness, hunger, poison, darkness, weakness, levitation
 *
 * Levitation is in the list on purpose. A plague that occasionally makes a squad float helplessly
 * upward is exactly the kind of thing this mod is for.
 *
 * WHY CARRIERS AND NOT A FIELD. A field would be static and dodgeable: walk out, wait. A carrier is
 * a moving source, so the correct counter-play is quarantine - isolate the infected, cure them, kill
 * them. That is a far more interesting thing to ask of a server than "stand outside the circle".
 *
 * COSTS. Each carrier pulses once a second and does one AABB query, so the system is O(carriers) per
 * second, not per tick. {@link #MAX_CARRIERS} is a technical ceiling so a plague in a mob farm cannot
 * take the server down; it is not a balance cap.
 */
public final class Outbreak extends SavedData {

    private static final String FILE_ID = "shadowclangaming_outbreak";

    /** How far a carrier reaches, in blocks. */
    public static final double SPREAD_RADIUS = 8.0D;

    /** Carrier pulse interval; also the effect refresh interval. */
    public static final int PULSE_TICKS = 20;

    /** Technical ceiling on simultaneous carriers. */
    public static final int MAX_CARRIERS = 1024;

    /** Effect duration never exceeds this (technical bound, ~50 minutes). */
    public static final int MAX_EFFECT_TICKS = 60_000;

    private static final List<Holder<MobEffect>> SYMPTOMS = List.of(
            MobEffects.CONFUSION,
            MobEffects.DIG_SLOWDOWN,
            MobEffects.MOVEMENT_SLOWDOWN,
            MobEffects.BLINDNESS,
            MobEffects.HUNGER,
            MobEffects.POISON,
            MobEffects.DARKNESS,
            MobEffects.WEAKNESS,
            MobEffects.LEVITATION);

    public static List<Holder<MobEffect>> symptoms() {
        return SYMPTOMS;
    }

    /** A single infected entity. Mutable: it moves, and it grows. */
    public static final class Carrier {
        private int generation;
        private long expiresAt;
        private long nextPulse;
        private String dimension;
        private double x;
        private double y;
        private double z;
        private boolean announced;

        Carrier(int generation, long expiresAt, long nextPulse, String dimension, Vec3 pos) {
            this.generation = generation;
            this.expiresAt = expiresAt;
            this.nextPulse = nextPulse;
            this.dimension = dimension;
            this.x = pos.x;
            this.y = pos.y;
            this.z = pos.z;
        }

        public int generation() {
            return generation;
        }

        public long expiresAt() {
            return expiresAt;
        }
    }

    private final Map<UUID, Carrier> carriers = new HashMap<>();

    public static Outbreak get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(Outbreak::new, Outbreak::load),
                FILE_ID);
    }

    public static Outbreak load(CompoundTag tag, HolderLookup.Provider registries) {
        Outbreak data = new Outbreak();
        ListTag list = tag.getList("Carriers", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            try {
                UUID id = UUID.fromString(entry.getString("Id"));
                Carrier carrier = new Carrier(
                        entry.getInt("Generation"),
                        entry.getLong("Expires"),
                        entry.getLong("NextPulse"),
                        entry.getString("Dimension"),
                        new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")));
                data.carriers.put(id, carrier);
            } catch (IllegalArgumentException ignored) {
                // corrupt entry: skip it rather than refusing to load the world
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        carriers.forEach((id, carrier) -> {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", id.toString());
            entry.putInt("Generation", carrier.generation);
            entry.putLong("Expires", carrier.expiresAt);
            entry.putLong("NextPulse", carrier.nextPulse);
            entry.putString("Dimension", carrier.dimension);
            entry.putDouble("X", carrier.x);
            entry.putDouble("Y", carrier.y);
            entry.putDouble("Z", carrier.z);
            list.add(entry);
        });
        tag.put("Carriers", list);
        return tag;
    }

    // ------------------------------------------------------------------ infection maths

    public static int symptomCountFor(int generation) {
        return Math.min(SYMPTOMS.size(), 1 + Math.max(0, generation));
    }

    public static int amplifierFor(int generation) {
        return Math.min(4, Math.max(0, generation) / 2);
    }

    public static int durationTicksFor(int generation) {
        long raw = 200L + 200L * Math.max(0, generation);
        return (int) Math.min(MAX_EFFECT_TICKS, raw);
    }

    // ------------------------------------------------------------------ seeding

    /**
     * @return how many entities were infected by the blast itself.
     */
    public static int infect(ServerLevel level, Vec3 pos, double radius) {
        Outbreak data = get(level);
        long now = level.getGameTime();
        AABB box = AABB.ofSize(pos, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        int infected = 0;

        for (Entity entity : level.getEntities((Entity) null, box, e -> e instanceof LivingEntity && e.isAlive())) {
            if (!(entity instanceof LivingEntity living)) {
                continue;
            }
            if (living.position().distanceToSqr(pos) > radius * radius) {
                continue;
            }
            if (data.carriers.size() >= MAX_CARRIERS) {
                break;
            }
            if (data.carriers.containsKey(living.getUUID())) {
                continue;
            }
            data.add(level, living, 0, now);
            infected++;
        }
        if (infected > 0) {
            data.setDirty();
        }
        return infected;
    }

    private void add(ServerLevel level, LivingEntity target, int generation, long now) {
        Carrier carrier = new Carrier(
                generation,
                now + durationTicksFor(generation) + 100L,
                now,
                level.dimension().location().toString(),
                target.position());
        carriers.put(target.getUUID(), carrier);
        applySymptoms(target, generation);
        if (target instanceof ServerPlayer player) {
            player.displayClientMessage(Component.translatable("message.shadowclangaming.plague_infected",
                    generation, symptomCountFor(generation)), true);
        }
    }

    /** Deterministic per entity + generation, so a carrier keeps the same symptoms between pulses. */
    public static void applySymptoms(LivingEntity entity, int generation) {
        List<Holder<MobEffect>> pool = new ArrayList<>(SYMPTOMS);
        long seed = entity.getUUID().getMostSignificantBits()
                ^ (entity.getUUID().getLeastSignificantBits() * 31L)
                ^ (generation * 0x9E3779B97F4A7C15L);
        Collections.shuffle(pool, new Random(seed));

        int count = symptomCountFor(generation);
        int amplifier = amplifierFor(generation);
        int duration = durationTicksFor(generation);
        for (int i = 0; i < count; i++) {
            entity.addEffect(new MobEffectInstance(pool.get(i), duration, amplifier));
        }
    }

    // ------------------------------------------------------------------ the spread

    public static void tick(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        Outbreak data = get(overworld);
        if (data.carriers.isEmpty()) {
            return;
        }
        long now = overworld.getGameTime();
        boolean dirty = false;

        Iterator<Map.Entry<UUID, Carrier>> iterator = data.carriers.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, Carrier> entry = iterator.next();
            Carrier carrier = entry.getValue();

            if (carrier.expiresAt <= now) {
                iterator.remove();
                dirty = true;
                continue;
            }
            if (carrier.nextPulse > now) {
                continue;
            }
            carrier.nextPulse = now + PULSE_TICKS;

            LivingEntity host = resolve(server, entry.getKey(), carrier);
            if (host == null) {
                continue; // unloaded or dead: keep the record, it may come back
            }
            carrier.dimension = host.level().dimension().location().toString();
            carrier.x = host.getX();
            carrier.y = host.getY();
            carrier.z = host.getZ();

            applySymptoms(host, carrier.generation);
            data.spread(server, host, carrier, now);
            dirty = true;
        }
        if (dirty) {
            data.setDirty();
        }
    }

    private void spread(MinecraftServer server, LivingEntity host, Carrier carrier, long now) {
        ServerLevel level = (ServerLevel) host.level();
        int nextGeneration = carrier.generation + 1;
        AABB box = host.getBoundingBox().inflate(SPREAD_RADIUS);

        for (Entity entity : level.getEntities((Entity) null, box,
                e -> e instanceof LivingEntity && e.isAlive() && e != host)) {
            if (carriers.size() >= MAX_CARRIERS) {
                return;
            }
            if (entity.distanceToSqr(host) > SPREAD_RADIUS * SPREAD_RADIUS) {
                continue;
            }
            Carrier existing = carriers.get(entity.getUUID());
            if (existing != null && existing.generation >= nextGeneration) {
                continue; // already knows this strain, or a nastier one
            }
            add(level, (LivingEntity) entity, nextGeneration, now);
        }
    }

    private static LivingEntity resolve(MinecraftServer server, UUID id, Carrier carrier) {
        ServerPlayer player = server.getPlayerList().getPlayer(id);
        if (player != null) {
            return player;
        }
        ServerLevel level = levelOf(server, carrier.dimension);
        if (level == null) {
            return null;
        }
        AABB box = AABB.ofSize(new Vec3(carrier.x, carrier.y, carrier.z), 48.0D, 48.0D, 48.0D);
        for (Entity entity : level.getEntities((Entity) null, box, e -> e.getUUID().equals(id))) {
            if (entity instanceof LivingEntity living) {
                return living;
            }
        }
        return null;
    }

    private static ServerLevel levelOf(MinecraftServer server, String dimension) {
        try {
            ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(dimension));
            return server.getLevel(key);
        } catch (Exception ignored) {
            return null;
        }
    }

    // ------------------------------------------------------------------ admin / cure

    public int count() {
        return carriers.size();
    }

    public int highestGeneration() {
        int max = 0;
        for (Carrier carrier : carriers.values()) {
            max = Math.max(max, carrier.generation);
        }
        return max;
    }

    /** @return true if the player was a carrier and is now cured. */
    public boolean cure(UUID id) {
        boolean removed = carriers.remove(id) != null;
        if (removed) {
            setDirty();
        }
        return removed;
    }

    public int clear() {
        int removed = carriers.size();
        carriers.clear();
        setDirty();
        return removed;
    }
}
