package com.shadowclangaming.radar;

import com.shadowclangaming.entity.MissileEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Turning a world into contacts.
 *
 * A sweep is a single bounding-box query and then a very cheap classification pass. It is never run per
 * tick: {@link RadarBlockEntity} runs it when somebody asks, or at most once a second when auto-sweep is
 * on, because a 4096-block box is more than thirty million blocks of volume and doing that twenty times a
 * second would be the single most expensive thing on the server. The query is one AABB entity lookup, not a
 * block iteration, so the cost scales with the number of entities (capped by {@link #MAX_CANDIDATES}) and
 * not with the volume.
 *
 * Item entities are skipped on purpose. A radar that reports every dropped cobblestone is not a radar.
 */
public final class RadarSweep {

    /** Hard cap on entities one sweep will even look at, so a mob farm cannot flatten the TPS. */
    public static final int MAX_CANDIDATES = 4096;

    private RadarSweep() {
    }

    public static List<RadarContact> scan(ServerLevel level, BlockPos antenna, double radius, int maxTracks,
                                         Map<UUID, Integer> idMap) {
        double r = Math.max(1.0D, radius);
        AABB box = new AABB(antenna).inflate(r, Math.min(r, level.getHeight() + 64.0D), r);
        Vec3 centre = Vec3.atCenterOf(antenna);

        List<Entity> entities = level.getEntitiesOfClass(Entity.class, box,
                entity -> entity != null && !(entity instanceof ItemEntity) && entity.isAlive());
        if (entities.size() > MAX_CANDIDATES) {
            entities = entities.subList(0, MAX_CANDIDATES);
        }

        List<RadarContact> contacts = new ArrayList<>(entities.size());
        for (Entity entity : entities) {
            Vec3 at = entity.position();
            Vec3 delta = at.subtract(centre);
            double distance = delta.length();
            if (distance > r) {
                continue;
            }
            Vec3 velocity = entity.getDeltaMovement();
            double closing = distance <= 0.0001D ? 0.0D : -delta.dot(velocity) / distance;
            contacts.add(new RadarContact(idFor(entity, idMap), displayName(entity), kindOf(entity),
                    at.x, at.y, at.z, velocity.x, velocity.y, velocity.z, distance, closing));
        }
        return RadarMath.select(contacts, radius, maxTracks);
    }

    /** Stable per-entity track number, allocated on first sight. */
    private static int idFor(Entity entity, Map<UUID, Integer> idMap) {
        if (idMap == null) {
            return Math.abs(entity.getUUID().hashCode() % 900) + 1;
        }
        Integer existing = idMap.get(entity.getUUID());
        if (existing != null) {
            return existing;
        }
        int next = idMap.size() + 1;
        if (idMap.size() > 4096) {
            idMap.clear();
        }
        idMap.put(entity.getUUID(), next);
        return next;
    }

    public static ContactKind kindOf(Entity entity) {
        if (entity instanceof MissileEntity) {
            return ContactKind.MISSILE;
        }
        if (entity instanceof Player) {
            return ContactKind.PLAYER;
        }
        if (entity instanceof AbstractMinecart || entity instanceof Boat) {
            return ContactKind.VEHICLE;
        }
        if (entity instanceof Mob) {
            return ContactKind.MONSTER;
        }
        if (entity instanceof LivingEntity) {
            return ContactKind.OTHER;
        }
        return ContactKind.OTHER;
    }

    private static String displayName(Entity entity) {
        var name = entity.getDisplayName();
        String text = name == null ? entity.getType().toString() : name.getString();
        return text.length() > 40 ? text.substring(0, 40) : text;
    }

    /** Fresh id map. Exists so callers do not have to write {@code new HashMap<>()} in a field. */
    public static Map<UUID, Integer> newIdMap() {
        return new HashMap<>();
    }
}
