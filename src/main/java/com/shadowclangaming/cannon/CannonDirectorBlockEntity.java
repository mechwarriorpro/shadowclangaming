package com.shadowclangaming.cannon;

import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The fire director: the block that turns a track into a gun laying.
 *
 * One director owns one cannon mount. It holds the mount's position, the propellant velocity it assumes,
 * an armed flag and the last thing it said. It aims by asking {@link CannonMountBridge} and it fires by
 * asking it to pull the trigger; if the bridge cannot answer - no CBC, a CBC update that moved a method -
 * it falls back to emitting an actual redstone pulse at the mount, which is how CBC expects vanilla
 * players to fire a cannon anyway.
 *
 * Nobody fires by accident. `armed` starts false, every command that shoots checks it, and the block
 * prints why it refused.
 */
public class CannonDirectorBlockEntity extends BlockEntity {

    /** Ticks the falling-back redstone pulse stays high. Two is enough for a CBC mount and short enough
     *  that it cannot be mistaken for a held lever. */
    public static final int FIRE_PULSE_TICKS = 2;
    /** Ticks between pulling and releasing the bridge trigger. */
    public static final int TRIGGER_RELEASE_TICKS = 2;

    private boolean armed;
    private boolean auto;
    private BlockPos mountPos;
    private double velocity = CannonMath.DEFAULT_VELOCITY;
    private float lastYaw;
    private float lastPitch;
    private String lastResult = "idle";

    /** Redstone fallback: power, face and when it ends. */
    private int pulsePower;
    private Direction pulseFace = Direction.NORTH;
    private long pulseEnd;
    /** Bridge fallback release, if we fired through reflection. */
    private BlockPos releaseMount;
    private long releaseAt;

    public CannonDirectorBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.CANNON_DIRECTOR.get(), pos, state);
    }

    // ------------------------------------------------------------------ wiring

    /** Link to the nearest mount. Called on right-click and by /term cannon link. */
    public String link(ServerLevel level) {
        List<CannonMountBridge.Mount> mounts =
                CannonMountBridge.findMounts(level, worldPosition, CannonMountBridge.LINK_RANGE);
        if (mounts.isEmpty()) {
            lastResult = CannonMountBridge.installed()
                    ? "no cannon mount within " + CannonMountBridge.LINK_RANGE + " blocks"
                    : "Create Big Cannons is not installed";
            setChanged();
            return lastResult;
        }
        CannonMountBridge.Mount best = mounts.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (CannonMountBridge.Mount mount : mounts) {
            double distance = mount.pos().distSqr(worldPosition);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = mount;
            }
        }
        this.mountPos = best.pos();
        this.lastResult = "linked " + best.describe();
        setChanged();
        return lastResult;
    }

    public void unlink() {
        mountPos = null;
        lastResult = "unlinked";
        setChanged();
    }

    public BlockPos mountPos() {
        return mountPos;
    }

    public boolean armed() {
        return armed;
    }

    public void setArmed(boolean armed) {
        this.armed = armed;
        setChanged();
    }

    public boolean auto() {
        return auto;
    }

    public void setAuto(boolean auto) {
        this.auto = auto;
        setChanged();
    }

    public double velocity() {
        return velocity;
    }

    public void setVelocity(double velocity) {
        this.velocity = CannonMath.clampVelocity(velocity);
        setChanged();
    }

    public String lastResult() {
        return lastResult;
    }

    public float lastYaw() {
        return lastYaw;
    }

    public float lastPitch() {
        return lastPitch;
    }

    // ------------------------------------------------------------------ aiming and firing

    /** Lay the gun on a point. Does not fire. */
    public String aimAt(ServerLevel level, double x, double y, double z) {
        if (mountPos == null) {
            lastResult = "no mount linked (right-click with the mount nearby, or /term cannon link)";
            setChanged();
            return lastResult;
        }
        // The barrel sits about a block above the mount block; using the block centre instead puts every
        // shot a little high, which is the kind of error nobody blames on the maths.
        double originX = worldPosition.getX() + 0.5D;
        double originY = mountPos.getY() + 1.5D;
        double originZ = worldPosition.getZ() + 0.5D;

        CannonMath.Aim aim = CannonMath.solve(x - originX, y - originY, z - originZ, velocity);
        this.lastYaw = aim.yaw();
        this.lastPitch = aim.pitch();

        if (!CannonMountBridge.installed()) {
            lastResult = "aimed " + aim.describe() + " (CBC not installed: nothing to lay)";
            setChanged();
            return lastResult;
        }
        boolean ok = CannonMountBridge.aim(level, mountPos, aim.yaw(), aim.pitch());
        lastResult = (ok ? "aimed " : "aim refused, wanted ") + aim.describe();
        setChanged();
        return lastResult;
    }

    /** Aim at a point and pull the trigger. */
    public String fireAt(ServerLevel level, double x, double y, double z) {
        if (!armed) {
            lastResult = "director is disarmed";
            setChanged();
            return lastResult;
        }
        String aim = aimAt(level, x, y, z);
        if (!aim.startsWith("aimed") || !CannonMountBridge.installed()) {
            // No mount, or no CBC to lay: report the lay and stop. A redstone pulse at a cannon that
            // does not exist is not a shot.
            return aim;
        }
        return fire(level);
    }

    /** Pull the trigger on the current lay. */
    public String fire(ServerLevel level) {
        if (!armed) {
            lastResult = "director is disarmed";
            setChanged();
            return lastResult;
        }
        if (mountPos == null) {
            lastResult = "no mount linked";
            setChanged();
            return lastResult;
        }
        long now = level.getGameTime();
        if (CannonMountBridge.installed() && CannonMountBridge.fire(level, mountPos)) {
            this.releaseMount = mountPos;
            this.releaseAt = now + TRIGGER_RELEASE_TICKS;
            lastResult = "fired via CBC bridge, yaw " + String.format(java.util.Locale.ROOT, "%.1f", lastYaw)
                    + " pitch " + String.format(java.util.Locale.ROOT, "%.1f", lastPitch);
            setChanged();
            return lastResult;
        }

        // Fallback: a real redstone pulse on the face that looks at the mount.
        Direction face = faceToward(mountPos);
        this.pulseFace = face;
        this.pulsePower = 15;
        this.pulseEnd = now + FIRE_PULSE_TICKS;
        if (level != null) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
        lastResult = "fired via redstone pulse (" + face.getName() + ")";
        setChanged();
        return lastResult;
    }

    /** Every tick, from the block. Releases the trigger, ends pulses, tells neighbours. */
    public void tick(ServerLevel level, long now) {
        if (releaseMount != null && now >= releaseAt) {
            CannonMountBridge.release(level, releaseMount);
            releaseMount = null;
            releaseAt = 0L;
        }
        if (pulsePower > 0 && now >= pulseEnd) {
            pulsePower = 0;
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
            setChanged();
        }
    }

    /** Redstone power this director emits toward a face. */
    public int powerFor(Direction direction) {
        return pulsePower > 0 && direction == pulseFace ? pulsePower : 0;
    }

    private Direction faceToward(BlockPos target) {
        int dx = target.getX() - worldPosition.getX();
        int dy = target.getY() - worldPosition.getY();
        int dz = target.getZ() - worldPosition.getZ();
        if (Math.abs(dx) >= Math.abs(dy) && Math.abs(dx) >= Math.abs(dz)) {
            return dx >= 0 ? Direction.EAST : Direction.WEST;
        }
        if (Math.abs(dy) >= Math.abs(dz)) {
            return dy >= 0 ? Direction.UP : Direction.DOWN;
        }
        return dz >= 0 ? Direction.SOUTH : Direction.NORTH;
    }

    // ------------------------------------------------------------------ display

    public List<String> status() {
        List<String> out = new ArrayList<>();
        out.add("director at " + worldPosition.toShortString() + ": " + (armed ? "ARMED" : "disarmed")
                + (auto ? ", auto" : "") + ", velocity "
                + String.format(java.util.Locale.ROOT, "%.1f", velocity) + " b/t");
        out.add("  mount: " + (mountPos == null ? "none" : mountPos.toShortString())
                + "  lay: yaw " + String.format(java.util.Locale.ROOT, "%.1f", lastYaw)
                + " pitch " + String.format(java.util.Locale.ROOT, "%.1f", lastPitch));
        out.add("  last: " + lastResult);
        if (mountPos != null && level instanceof ServerLevel serverLevel) {
            BlockEntity mount = serverLevel.getBlockEntity(mountPos);
            if (mount != null && CannonMountBridge.isMount(mount)) {
                out.add("  " + CannonMountBridge.read(mount).describe());
            }
        }
        if (level instanceof ServerLevel serverLevel) {
            out.add("  " + CannonMountBridge.describe(serverLevel, worldPosition));
        }
        return out;
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("Armed", armed);
        tag.putBoolean("Auto", auto);
        tag.putDouble("Velocity", velocity);
        tag.putFloat("LastYaw", lastYaw);
        tag.putFloat("LastPitch", lastPitch);
        tag.putString("LastResult", lastResult);
        if (mountPos != null) {
            tag.putLong("Mount", mountPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        armed = tag.getBoolean("Armed");
        auto = tag.getBoolean("Auto");
        velocity = tag.contains("Velocity") ? tag.getDouble("Velocity") : CannonMath.DEFAULT_VELOCITY;
        lastYaw = tag.getFloat("LastYaw");
        lastPitch = tag.getFloat("LastPitch");
        lastResult = tag.getString("LastResult");
        mountPos = tag.contains("Mount") ? BlockPos.of(tag.getLong("Mount")) : null;
    }
}
