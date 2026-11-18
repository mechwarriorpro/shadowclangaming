package com.shadowclangaming.radar;

import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Our radar.
 *
 * Three things it does that the Create: Radars bearing in the next base over does not:
 *
 *   1. It sees further. 256 blocks from one block, up to 4096 by adding dishes around the first one.
 *   2. It holds a track list, not a single nearest hit - every live contact keeps its number between
 *      sweeps, so "/term radar track 3" means the same thing a second later.
 *   3. It is a computer peripheral: a terminal on the same wire reads it, and a cannon director can be
 *      pointed at a track.
 *
 * The sweep is expensive, so it is on demand. Auto-sweep exists for unattended air defence and runs at
 * one sweep per second, never per tick.
 */
public class RadarBlockEntity extends BlockEntity {

    /** Ticks between automatic sweeps. */
    public static final int AUTO_SWEEP_TICKS = 20;
    /** How many contacts the block will remember for display and for other blocks to read. */
    public static final int KEPT_CONTACTS = 64;

    private boolean autoSweep;
    private int arrays = 1;
    private double lastRadius = RadarMath.BASE_RADIUS;
    private long lastSweep;
    private int sweeps;

    private final List<RadarContact> contacts = new ArrayList<>();
    /** Track the controlling computer has locked, 0 = none. Kept on the radar so a lock survives a
     *  returning player, and so a cannon director reading the radar sees the same track the terminal
     *  selected. */
    private int selected;
    private Map<UUID, Integer> trackIds = RadarSweep.newIdMap();
    private String lastNote = "never swept";
    /** GPU contacts from the last computer-issued sweep, reused by auto-sweep. */
    private int lastGpuBonus;

    public RadarBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.RADAR.get(), pos, state);
    }

    // ------------------------------------------------------------------ state

    public boolean autoSweep() {
        return autoSweep;
    }

    public void setAutoSweep(boolean autoSweep) {
        this.autoSweep = autoSweep;
        setChanged();
    }

    public int arrays() {
        return arrays;
    }

    public double radius() {
        return lastRadius;
    }

    public long lastSweep() {
        return lastSweep;
    }

    public int sweeps() {
        return sweeps;
    }

    public String lastNote() {
        return lastNote;
    }

    public List<RadarContact> contacts() {
        return List.copyOf(contacts);
    }

    public RadarContact byTrack(int track) {
        for (RadarContact contact : contacts) {
            if (contact.id() == track) {
                return contact;
            }
        }
        return null;
    }

    /** Worst contact on the scope, i.e. the one an air-defence director should shoot. */
    /** Lock a track, or 0 to release. Returns false if that track is not on the scope. */
    public boolean select(int track) {
        if (track == 0) {
            selected = 0;
            setChanged();
            return true;
        }
        if (byTrack(track) == null) {
            return false;
        }
        selected = track;
        setChanged();
        return true;
    }

    public int selected() {
        return selected;
    }

    /** The locked contact, or null if the lock went stale. */
    public RadarContact selectedContact() {
        return selected == 0 ? null : byTrack(selected);
    }

    public RadarContact highestThreat() {
        RadarContact worst = null;
        int worstScore = -1;
        for (RadarContact contact : contacts) {
            int score = RadarMath.threatOf(contact, lastRadius);
            if (score > worstScore) {
                worstScore = score;
                worst = contact;
            }
        }
        return worst;
    }

    // ------------------------------------------------------------------ sweeping

    /**
     * Sweep once.
     *
     * @param gpuBonus extra contacts granted by the controlling machine's GPUs; pass 0 for a sweep
     *                 nobody asked a computer about
     */
    public List<RadarContact> sweep(ServerLevel level, int gpuBonus) {
        lastGpuBonus = Math.max(0, gpuBonus);
        arrays = countArrays(level);
        lastRadius = RadarMath.radiusFor(arrays);
        int tracks = RadarMath.maxTracks(arrays, gpuBonus);
        List<RadarContact> found = RadarSweep.scan(level, worldPosition, lastRadius, tracks, trackIds);
        contacts.clear();
        for (int i = 0; i < found.size() && i < KEPT_CONTACTS; i++) {
            contacts.add(found.get(i));
        }
        sweeps++;
        lastSweep = level.getGameTime();
        lastNote = "sweep " + sweeps + ": " + contacts.size() + " contact(s), radius "
                + (int) lastRadius + ", " + arrays + " dish(es)"
                + createRadarNote(level);
        setChanged();
        return contacts();
    }

    /** One scan per sweep: the bridge is reflective and we do not want to pay for it twice. */
    private String createRadarNote(ServerLevel level) {
        if (!CreateRadarBridge.installed()) {
            return "";
        }
        int bearings = CreateRadarBridge.bearingCount(level, worldPosition);
        return bearings == 0 ? "" : " (+" + bearings + " create_radar dish(es))";
    }

    /** How many radar blocks are in this array, counting this one. */
    private int countArrays(ServerLevel level) {
        int count = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dx = -RadarMath.ARRAY_RANGE; dx <= RadarMath.ARRAY_RANGE; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                for (int dz = -RadarMath.ARRAY_RANGE; dz <= RadarMath.ARRAY_RANGE; dz++) {
                    cursor.set(worldPosition.getX() + dx, worldPosition.getY() + dy,
                            worldPosition.getZ() + dz);
                    if (level.getBlockEntity(cursor) instanceof RadarBlockEntity) {
                        count++;
                    }
                }
            }
        }
        return RadarMath.clampArrays(count);
    }

    /** Called by the block's ticker. One sweep a second at most. */
    public void tick(ServerLevel level, long now) {
        if (!autoSweep) {
            return;
        }
        if (now - lastSweep < AUTO_SWEEP_TICKS) {
            return;
        }
        sweep(level, lastGpuBonus);
    }

    /** How many contacts a report prints. The scope can hold 64; a chat window cannot. */
    public static final int REPORT_LINES = 16;

    /** One line per contact, used by the block's right-click and by /term radar. */
    public List<String> report() {
        List<String> out = new ArrayList<>();
        out.add("radar: radius " + (int) lastRadius + " (" + arrays + " dish(es)), "
                + contacts.size() + " contact(s)"
                + (autoSweep ? ", auto-sweep on" : ", auto-sweep off"));
        int shown = 0;
        for (RadarContact contact : contacts) {
            if (shown++ >= REPORT_LINES) {
                out.add("  ... " + (contacts.size() - REPORT_LINES) + " more on the scope");
                break;
            }
            out.add(format(contact));
        }
        return out;
    }

    public String format(RadarContact contact) {
        double bearing = contact.bearing(worldPosition.getX() + 0.5D, worldPosition.getZ() + 0.5D);
        double elevation = RadarMath.elevationDegrees(contact.x() - worldPosition.getX(),
                contact.y() - worldPosition.getY(), contact.z() - worldPosition.getZ());
        return String.format(java.util.Locale.ROOT,
                "#%02d %s %s  brg %03.0f  el %+.0f  rng %4.0f  threat %3d  %s",
                contact.id(), contact.kind().id(), contact.name(), bearing, elevation,
                contact.distance(), RadarMath.threatOf(contact, lastRadius),
                contact.closing() > 0.01D ? "closing" : contact.closing() < -0.01D ? "opening" : "steady");
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("AutoSweep", autoSweep);
        tag.putInt("Arrays", arrays);
        tag.putDouble("Radius", lastRadius);
        tag.putLong("LastSweep", lastSweep);
        tag.putInt("Sweeps", sweeps);
        tag.putString("LastNote", lastNote);
        tag.putInt("Selected", selected);

        // Contacts are saved so a chunk unload does not blank an unattended air-defence scope. They are
        // stale on load, which the next sweep fixes; the "stale" flag is what the director checks.
        ListTag list = new ListTag();
        for (RadarContact contact : contacts) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("Id", contact.id());
            entry.putString("Name", contact.name());
            entry.putString("Kind", contact.kind().id());
            entry.putDouble("X", contact.x());
            entry.putDouble("Y", contact.y());
            entry.putDouble("Z", contact.z());
            entry.putDouble("Vx", contact.vx());
            entry.putDouble("Vy", contact.vy());
            entry.putDouble("Vz", contact.vz());
            entry.putDouble("Distance", contact.distance());
            entry.putDouble("Closing", contact.closing());
            list.add(entry);
        }
        tag.put("Contacts", list);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        autoSweep = tag.getBoolean("AutoSweep");
        arrays = Math.max(1, tag.getInt("Arrays"));
        lastRadius = tag.contains("Radius") ? tag.getDouble("Radius") : RadarMath.BASE_RADIUS;
        lastSweep = tag.getLong("LastSweep");
        sweeps = tag.getInt("Sweeps");
        lastNote = tag.getString("LastNote");
        selected = tag.getInt("Selected");
        contacts.clear();
        ListTag list = tag.getList("Contacts", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < KEPT_CONTACTS; i++) {
            CompoundTag entry = list.getCompound(i);
            contacts.add(new RadarContact(entry.getInt("Id"), entry.getString("Name"),
                    kindOf(entry.getString("Kind")), entry.getDouble("X"), entry.getDouble("Y"),
                    entry.getDouble("Z"), entry.getDouble("Vx"), entry.getDouble("Vy"),
                    entry.getDouble("Vz"), entry.getDouble("Distance"), entry.getDouble("Closing")));
        }
    }

    private static ContactKind kindOf(String id) {
        for (ContactKind kind : ContactKind.values()) {
            if (kind.id().equalsIgnoreCase(id)) {
                return kind;
            }
        }
        return ContactKind.OTHER;
    }
}
