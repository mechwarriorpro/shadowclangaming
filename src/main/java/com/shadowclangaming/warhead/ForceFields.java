package com.shadowclangaming.warhead;

import com.shadowclangaming.ShadowClanGaming;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * BULWARK warhead: a temporary forcefield dome, bought with money at the moment of impact.
 *
 * This is the one warhead that destroys nothing. It arrives, and then the impact point becomes a
 * place you cannot leave and cannot enter, until either the money runs out of time or somebody
 * shells it. Size and duration both come from the funding loaded into the missile (Bank Card or
 * coins):
 *
 *      scale = sqrt(funding / 100)
 *      radius   = 8 + 2 * scale          blocks
 *      duration = 60 + 20 * scale        ticks
 *
 *      100 units  ->  radius 10,   4 seconds
 *      10,000     ->  radius 28,  13 seconds
 *      1,000,000  ->  radius 208, 103 seconds
 *
 * ──────────────────── HONEST BOUNDS ────────────────────
 *
 * The radius formula is square-rooted, so it grows slowly and politely. Duration keeps growing with
 * no balance cap at all - pay a fortune and the dome stays up for hours. Radius does saturate, at
 * {@link #MAX_SIM_RADIUS}: not for balance reasons, but because every tick this system walks every
 * entity inside the sphere's bounding box, and a dome larger than the view distance would mean
 * scanning the whole loaded world 20 times a second. That constant is one line if you want it bigger
 * and know what it costs.
 *
 * Containment is deliberately simple: entities INSIDE are hard-clamped to just under the shell,
 * entities OUTSIDE are shoved back out if they poke through. A very fast mover (an antimatter missile
 * at full speed) can therefore cross a tick boundary and end up briefly inside before being ejected.
 * Real collision solving for a sphere would need a physics pass we do not have, and the brief
 * overshoot is not worth the per-entity raycasts.
 */
public final class ForceFields extends SavedData {

    private static final String FILE_ID = "shadowclangaming_forcefields";

    /** Funding that buys the baseline dome. */
    public static final long BASE_FUNDING = 100L;
    public static final int BASE_RADIUS = 8;

    /** Simulation ceiling - see the class comment. Not a balance cap. */
    public static final int MAX_SIM_RADIUS = 256;

    /** Technical clamp so a silly funding number cannot overflow into a negative duration. */
    public static final int MAX_DURATION_TICKS = 1_000_000;

    /** One deployed dome. */
    public static final class Field {
        private final Vec3 centre;
        private final int radius;
        private final long funding;
        private final long expiresAt;
        private final String dimension;

        Field(Vec3 centre, int radius, long funding, long expiresAt, String dimension) {
            this.centre = centre;
            this.radius = radius;
            this.funding = funding;
            this.expiresAt = expiresAt;
            this.dimension = dimension;
        }

        public Vec3 centre() {
            return centre;
        }

        public int radius() {
            return radius;
        }

        public long funding() {
            return funding;
        }

        public long expiresAt() {
            return expiresAt;
        }

        public String dimension() {
            return dimension;
        }
    }

    private final List<Field> fields = new ArrayList<>();

    public static ForceFields get(ServerLevel level) {
        ServerLevel storageLevel = level.getServer().overworld();
        return storageLevel.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ForceFields::new, ForceFields::load),
                FILE_ID);
    }

    public static ForceFields load(CompoundTag tag, HolderLookup.Provider registries) {
        ForceFields data = new ForceFields();
        ListTag list = tag.getList("Fields", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            data.fields.add(new Field(
                    new Vec3(entry.getDouble("X"), entry.getDouble("Y"), entry.getDouble("Z")),
                    entry.getInt("Radius"),
                    entry.getLong("Funding"),
                    entry.getLong("Expires"),
                    entry.getString("Dimension")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Field field : fields) {
            CompoundTag entry = new CompoundTag();
            entry.putDouble("X", field.centre().x);
            entry.putDouble("Y", field.centre().y);
            entry.putDouble("Z", field.centre().z);
            entry.putInt("Radius", field.radius());
            entry.putLong("Funding", field.funding());
            entry.putLong("Expires", field.expiresAt());
            entry.putString("Dimension", field.dimension());
            list.add(entry);
        }
        tag.put("Fields", list);
        return tag;
    }

    // ------------------------------------------------------------------ the maths

    public static double scaleFor(long funding) {
        return Math.sqrt(Math.max(0L, funding) / (double) BASE_FUNDING);
    }

    public static int radiusForFunding(long funding) {
        long raw = BASE_RADIUS + Math.round(2.0D * scaleFor(funding));
        return (int) Math.min(MAX_SIM_RADIUS, Math.max(BASE_RADIUS, raw));
    }

    /**
     * Inverse of {@link #radiusForFunding}: the cheapest funding that reaches at least this radius.
     * Used by the Bank Card's "next step" tier so one click always buys one more block of dome.
     */
    public static long fundingForRadius(int radius) {
        if (radius <= BASE_RADIUS) {
            return BASE_FUNDING;
        }
        double scale = (radius - BASE_RADIUS) / 2.0D;
        return Math.round(BASE_FUNDING * scale * scale);
    }

    public static int durationTicksForFunding(long funding) {
        long raw = 60L + Math.round(20.0D * scaleFor(funding));
        return (int) Math.min(MAX_DURATION_TICKS, Math.max(20L, raw));
    }

    // ------------------------------------------------------------------ deploy / tick

    /** @return the field that was created. */
    public static Field deploy(ServerLevel level, Vec3 pos, long funding) {
        ForceFields data = get(level);
        int radius = radiusForFunding(funding);
        int duration = durationTicksForFunding(funding);
        Field field = new Field(pos, radius, funding,
                level.getGameTime() + duration,
                level.dimension().location().toString());
        data.fields.add(field);
        data.setDirty();

        // A containment dome is loud on purpose: nobody should be surprised by a wall of light.
        Component message = Component.translatable("message.shadowclangaming.field_deployed",
                radius, String.format("%.1f", duration / 20.0D));
        for (var player : level.getServer().getPlayerList().getPlayers()) {
            if (player.level() == level && player.position().distanceToSqr(pos) < 256.0D * 256.0D) {
                player.displayClientMessage(message, false);
            }
        }
        ShadowClanGaming.LOGGER.info("Bulwark field at {} radius {} for {} ticks (funding {})",
                BlockPos.containing(pos), radius, duration, funding);
        return field;
    }

    public static void tick(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            ForceFields data = get(level);
            if (data.fields.isEmpty()) {
                continue;
            }
            long now = level.getGameTime();
            String dimension = level.dimension().location().toString();

            if (data.fields.removeIf(field -> field.dimension().equals(dimension) && field.expiresAt() <= now)) {
                data.setDirty();
            }

            boolean drawShell = now % 10L == 0L;
            for (Field field : data.fields) {
                if (!field.dimension().equals(dimension)) {
                    continue;
                }
                contain(level, field);
                if (drawShell) {
                    shell(level, field);
                }
            }
        }
    }

    /** Clamp entities to the shell. Cheap: one AABB query, no raycasts. */
    private static void contain(ServerLevel level, Field field) {
        double radius = field.radius();
        Vec3 centre = field.centre();
        AABB box = AABB.ofSize(centre, radius * 2.0D + 4.0D, radius * 2.0D + 4.0D, radius * 2.0D + 4.0D);

        double insideLimit = radius - 0.35D;
        double outsideLimit = radius + 1.5D;

        for (Entity entity : level.getEntities((Entity) null, box, Entity::isAlive)) {
            Vec3 offset = entity.position().subtract(centre);
            double distance = offset.length();
            if (distance < 1.0E-4D) {
                continue;
            }
            if (distance <= insideLimit) {
                continue;
            }
            Vec3 normal = offset.scale(1.0D / distance);
            if (distance < radius) {
                // Inside but pressed against the shell: hold it there.
                Vec3 target = centre.add(normal.scale(insideLimit));
                entity.teleportTo(target.x, target.y, target.z);
                killOutwardMotion(entity, normal);
            } else if (distance < outsideLimit) {
                // Just outside and poking through: push it back out.
                Vec3 target = centre.add(normal.scale(outsideLimit));
                entity.teleportTo(target.x, target.y, target.z);
                killInwardMotion(entity, normal);
            }
        }
    }

    private static void killOutwardMotion(Entity entity, Vec3 normal) {
        Vec3 motion = entity.getDeltaMovement();
        double outward = motion.dot(normal);
        if (outward > 0.0D) {
            entity.setDeltaMovement(motion.subtract(normal.scale(outward)));
            entity.hurtMarked = true;
        }
    }

    private static void killInwardMotion(Entity entity, Vec3 normal) {
        Vec3 motion = entity.getDeltaMovement();
        double inward = motion.dot(normal);
        if (inward < 0.0D) {
            entity.setDeltaMovement(motion.subtract(normal.scale(inward)));
            entity.hurtMarked = true;
        }
    }

    /** A sparse shell of particles. Points are sampled on a Fibonacci sphere so it looks even. */
    private static void shell(ServerLevel level, Field field) {
        int points = (int) Math.min(28, 8 + field.radius() / 4);
        double radius = field.radius();
        Vec3 centre = field.centre();
        double golden = Math.PI * (3.0D - Math.sqrt(5.0D));
        for (int i = 0; i < points; i++) {
            double y = 1.0D - (i / (double) (points - 1)) * 2.0D;
            double ring = Math.sqrt(Math.max(0.0D, 1.0D - y * y));
            double theta = golden * i;
            Vec3 point = centre.add(Math.cos(theta) * ring * radius, y * radius, Math.sin(theta) * ring * radius);
            com.shadowclangaming.perf.FxBudget.send(level, ParticleTypes.END_ROD, point, 1, 0.0, 0.0, 0.0, 0.0);
        }
    }

    // ------------------------------------------------------------------ admin

    public int count() {
        return fields.size();
    }

    public List<Field> all() {
        return fields;
    }

    public void clear() {
        fields.clear();
        setDirty();
    }
}
