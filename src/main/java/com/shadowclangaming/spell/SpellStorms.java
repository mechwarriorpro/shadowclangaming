package com.shadowclangaming.spell;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * Firestorm and Hellstorm: a column of fire that keeps burning for a few seconds.
 *
 * ──────────────────── WHAT EACH ONE IS ────────────────────
 *
 *   FIRE_STORM  radius 8   6 s   3 damage per pulse, every 10 ticks, respects the damage cooldown,
 *                                ignites what it touches and sets the ground on fire.
 *
 *   HELLSTORM   radius 14  8 s   7 damage per pulse, every 10 ticks, BYPASSES the damage cooldown,
 *                                and never ignites anything or places fire - so items on the ground
 *                                are not destroyed by it.
 *
 * The cooldown is the difference that matters. Vanilla gives a mob roughly half a second of immunity
 * after any hit, so a firestorm ticking every half second lands about half its pulses. A hellstorm
 * zeroes {@code invulnerableTime} before every hit, which means every pulse lands - the same six
 * seconds of standing in it is roughly four times the damage, on a footprint three times the area.
 *
 * ──────────────────── WHY SAVED DATA ────────────────────
 *
 * A storm outlives the tick that created it and has to survive a restart, exactly like a spell zone,
 * so it lives in a {@link SavedData} and is ticked from the same server-tick listener as
 * {@link SpellZones}. With nothing burning the tick costs one emptiness check.
 */
public final class SpellStorms extends SavedData {

    /** Hard cap on simultaneous storms, so a griefer cannot fill the world with fire. */
    public static final int MAX_ACTIVE = 256;

    /** Ticks between pulses: half a second, which is what makes a hellstorm's i-frame bypass matter. */
    private static final int PULSE_TICKS = 10;

    /** One burning area. */
    public record Storm(ResourceKey<Level> dimension, Vec3 position, double radius, UUID caster,
                        float damage, int durationTicks, long expiresAt, long nextPulse,
                        boolean bypassIframes, boolean ignites) {

        CompoundTag save() {
            CompoundTag tag = new CompoundTag();
            tag.putString("Dim", dimension.location().toString());
            tag.putDouble("X", position.x);
            tag.putDouble("Y", position.y);
            tag.putDouble("Z", position.z);
            tag.putDouble("Radius", radius);
            if (caster != null) {
                tag.putUUID("Caster", caster);
            }
            tag.putFloat("Damage", damage);
            tag.putInt("Duration", durationTicks);
            tag.putLong("Expires", expiresAt);
            tag.putLong("NextPulse", nextPulse);
            tag.putBoolean("Bypass", bypassIframes);
            tag.putBoolean("Ignites", ignites);
            return tag;
        }

        static Storm load(CompoundTag tag) {
            ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.parse(tag.getString("Dim")));
            UUID caster = tag.hasUUID("Caster") ? tag.getUUID("Caster") : null;
            return new Storm(dimension,
                    new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                    tag.getDouble("Radius"), caster, tag.getFloat("Damage"), tag.getInt("Duration"),
                    tag.getLong("Expires"), tag.getLong("NextPulse"),
                    tag.getBoolean("Bypass"), tag.getBoolean("Ignites"));
        }
    }

    private final List<Storm> active = new ArrayList<>();

    public static SpellStorms get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SpellStorms::new, SpellStorms::load),
                "shadowclangaming_spell_storms");
    }

    private static SpellStorms load(CompoundTag tag, HolderLookup.Provider registries) {
        SpellStorms data = new SpellStorms();
        ListTag list = tag.getList("Storms", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            try {
                data.active.add(Storm.load(list.getCompound(i)));
            } catch (RuntimeException ignored) {
                // A storm written by an older build with a dimension that no longer exists: drop it.
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Storm storm : active) {
            list.add(storm.save());
        }
        tag.put("Storms", list);
        return tag;
    }

    // ------------------------------------------------------------------ creating

    /**
     * A firestorm: the ordinary one. Respects the damage cooldown and burns the ground.
     *
     * @param radius      how far from the centre it reaches
     * @param damage      damage per pulse, already scaled by the caster's power
     * @param durationTicks how long it burns for
     */
    public static void addFireStorm(ServerLevel level, Vec3 position, double radius, float damage,
                                    int durationTicks, LivingEntity caster) {
        add(level, position, radius, damage, durationTicks, caster, false, true);
    }

    /**
     * A hellstorm: wider, hotter, ignores i-frames, and specifically does NOT set fire to anything so
     * that dropped items are never destroyed by it.
     */
    public static void addHellStorm(ServerLevel level, Vec3 position, double radius, float damage,
                                    int durationTicks, LivingEntity caster) {
        add(level, position, radius, damage, durationTicks, caster, true, false);
    }

    private static void add(ServerLevel level, Vec3 position, double radius, float damage, int durationTicks,
                            LivingEntity caster, boolean bypassIframes, boolean ignites) {
        SpellStorms data = get(level.getServer());
        if (data.active.size() >= MAX_ACTIVE) {
            data.active.remove(0);
            ShadowClanGaming.LOGGER.warn("Spell storm limit ({}) reached; oldest storm dropped", MAX_ACTIVE);
        }
        long now = level.getGameTime();
        data.active.add(new Storm(level.dimension(), position, radius, caster == null ? null : caster.getUUID(),
                damage, durationTicks, now + durationTicks, now, bypassIframes, ignites));
        data.setDirty();
        level.playSound(null, BlockPos.containing(position), SoundEvents.FIRECHARGE_USE,
                SoundSource.PLAYERS, 1.2F, bypassIframes ? 0.6F : 1.0F);
    }

    public static int activeCount(MinecraftServer server) {
        return get(server).active.size();
    }

    // ------------------------------------------------------------------ ticking

    /**
     * Called once per server tick, from the same listener as {@link SpellZones#tick}.
     *
     * Same hazard as the zone tick, same fix: a storm's pulse can cast a spell that starts ANOTHER
     * storm (Firestorm inside a zone, Hellstorm from a warhead), so this walks by index and mutates in
     * place instead of holding an Iterator.
     */
    public static void tick(MinecraftServer server) {
        SpellStorms data = get(server);
        if (data.active.isEmpty()) {
            return;
        }
        boolean changed = false;

        for (int i = 0; i < data.active.size(); i++) {
            Storm storm = data.active.get(i);
            ServerLevel level = server.getLevel(storm.dimension());
            if (level == null) {
                data.active.remove(i);
                i--;
                changed = true;
                continue;
            }
            long now = level.getGameTime();
            if (now >= storm.expiresAt()) {
                data.active.remove(i);
                i--;
                changed = true;
                continue;
            }
            if (now < storm.nextPulse()) {
                continue;
            }

            pulse(level, storm);
            changed = true;
            data.active.set(i, new Storm(storm.dimension(), storm.position(), storm.radius(),
                    storm.caster(), storm.damage(), storm.durationTicks(), storm.expiresAt(),
                    now + PULSE_TICKS, storm.bypassIframes(), storm.ignites()));
        }

        if (changed) {
            data.setDirty();
        }
    }

    private static void pulse(ServerLevel level, Storm storm) {
        LivingEntity caster = storm.caster() == null ? null
                : level.getServer().getPlayerList().getPlayer(storm.caster());
        // A firestorm is fire. A hellstorm is *heat*: it is dealt as magic so that nothing it kills is
        // left burning, which is what keeps the dropped loot intact.
        DamageSource source = storm.ignites()
                ? (caster == null ? level.damageSources().onFire()
                        : level.damageSources().indirectMagic(caster, caster))
                : (caster == null ? level.damageSources().magic()
                        : level.damageSources().indirectMagic(caster, caster));

        AABB area = new AABB(storm.position(), storm.position()).inflate(storm.radius());
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, area,
                entity -> entity.isAlive() && entity != caster)) {
            if (storm.bypassIframes()) {
                // The i-frame window is the fight: clearing it is what "bypasses i-frames" means.
                victim.invulnerableTime = 0;
                victim.hurtTime = 0;
            }
            victim.hurt(source, storm.damage());
            if (storm.ignites()) {
                victim.setRemainingFireTicks(Math.max(victim.getRemainingFireTicks(), 100));
            } else {
                // Hellstorm is hot but clean: nothing keeps burning once the pulse is over, so a death
                // inside it leaves the dropped loot intact.
                victim.setRemainingFireTicks(0);
            }
        }

        if (storm.ignites()) {
            // The ordinary storm sets the ground alight. This is also why it can destroy items: fire
            // burns loot on the floor, which is the thing the hellstorm exists to avoid.
            igniteGround(level, storm.position(), (int) Math.min(6.0D, Math.round(storm.radius() * 0.5D)));
        } else {
            // ...and the hellstorm actively puts out anything already burning inside its footprint.
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area, Entity::isAlive)) {
                item.setRemainingFireTicks(0);
                item.clearFire();
            }
        }

        level.sendParticles(storm.bypassIframes() ? ParticleTypes.SOUL_FIRE_FLAME : ParticleTypes.FLAME,
                storm.position().x, storm.position().y + 0.6D, storm.position().z, 40,
                storm.radius() * 0.35D, 0.5D, storm.radius() * 0.35D, 0.02D);
        level.sendParticles(ParticleTypes.LAVA, storm.position().x, storm.position().y + 0.2D,
                storm.position().z, 6, storm.radius() * 0.3D, 0.2D, storm.radius() * 0.3D, 0.0D);
    }

    /** A ring of fire on the ground, capped in radius so a huge sphere does not become a chunk-melter. */
    private static void igniteGround(ServerLevel level, Vec3 centre, int radius) {
        BlockPos middle = BlockPos.containing(centre);
        for (BlockPos pos : BlockPos.betweenClosed(middle.offset(-radius, -1, -radius),
                middle.offset(radius, 1, radius))) {
            if (level.random.nextFloat() > 0.35F) {
                continue;
            }
            if (level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.below()).isSolidRender(level, pos.below())) {
                level.setBlockAndUpdate(pos, net.minecraft.world.level.block.Blocks.FIRE.defaultBlockState());
            }
        }
    }

    private SpellStorms() {
    }
}
