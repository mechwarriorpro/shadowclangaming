package com.shadowclangaming.block;

import com.shadowclangaming.compat.projecte.ProjectEEmc;
import com.shadowclangaming.registry.SCGBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.IEnergyStorage;

/**
 * HYPERCONDUCTOR CABLE - long-scale power transfer with automatic protocol conversion.
 *
 * ──────────────────── WHY "LONG" MATTERS ────────────────────
 *
 * NeoForge's energy interface, {@link IEnergyStorage}, is {@code int}: 2,147,483,647 units per call,
 * full stop. That is a hard ceiling in the API every FE machine in the pack speaks, and no mod can
 * change it for anyone else. So playing nicely and moving big numbers needs two different things at
 * once:
 *
 *   1. INTERNALLY, everything is {@code long}. The buffer is 4 GFE, the per-tick transfer budget is
 *      1 GFE, and cable-to-cable handoffs are single long arithmetic - no int anywhere in the spine.
 *
 *   2. AT THE BOUNDARY, we do not call {@code receiveEnergy} once. We call it in a loop, each call
 *      taking up to Integer.MAX_VALUE, until the budget or the peer's headroom runs out. A machine
 *      that accepts 2.1 GFE per call can therefore be filled at ~1 GFE/t, and a cable run between
 *      two cables moves 1 GFE/t in a single long operation.
 *
 * So the int limit is real but it is only ever a per-CALL limit, never a throughput limit. The number
 * that actually binds is {@link #TRANSFER_PER_TICK}.
 *
 * ──────────────────── WHAT IT CONVERTS ────────────────────
 *
 *   FE (NeoForge capability)      1 FE = 1 internal unit         every FE mod: Mekanism, Ender IO,
 *                                                               Extreme Reactors, Nuclearcraft,
 *                                                               Nuclearscience, Draconic, AE2 via
 *                                                               its Energy Acceptor, Create: New Age
 *   Electrodynamics Joules        via Dynamic Electricity         their motors already do J -> FE,
 *                                 so a DE motor next to this cable is all that is needed - there
 *                                 is no separate "DE power" capability to implement
 *   ProjectE EMC                  16 EMC = 1 FE, 16 FE = 1 EMC   long based, moved directly through
 *                                 reflection (see ProjectEEmc)
 *
 * The EMC rates are deliberately reciprocal and lossy: a full round trip loses 256x. EMC is farmable
 * with a condenser, so a fair exchange would be a matter printer. It still cannot be turned into
 * money at a profit, because the Auto-Trader's power-to-currency curve is cubic per player.
 */
public class PowerCableBlockEntity extends BlockEntity {

    /** Internal long buffer, per cable block. 4 GFE. */
    public static final long CAPACITY = 4_000_000_000L;

    /** Long-scale throughput: how much this one cable will move in a tick, all sides combined. */
    public static final long TRANSFER_PER_TICK = 1_000_000_000L;

    /** 16 EMC in = 1 FE out. */
    public static final long EMC_PER_FE = 16L;

    /** 16 FE in = 1 EMC out. Round trip costs 256x - on purpose. */
    public static final long FE_PER_EMC = 16L;

    private long buffer;
    private long lastMoved;

    /** The FE face of the cable: clamped ints in, long sums out. */
    private final IEnergyStorage feView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            if (toReceive <= 0) {
                return 0;
            }
            long room = CAPACITY - buffer;
            int accepted = (int) Math.min(Math.min(room, Integer.MAX_VALUE), toReceive);
            if (accepted > 0 && !simulate) {
                buffer += accepted;
                setChanged();
            }
            return accepted;
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            if (toExtract <= 0) {
                return 0;
            }
            int removed = (int) Math.min(Math.min(buffer, Integer.MAX_VALUE), toExtract);
            if (removed > 0 && !simulate) {
                buffer -= removed;
                setChanged();
            }
            return removed;
        }

        @Override
        public int getEnergyStored() {
            return (int) Math.min(buffer, Integer.MAX_VALUE);
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public PowerCableBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.POWER_CABLE.get(), pos, state);
    }

    public IEnergyStorage feView() {
        return feView;
    }

    public long stored() {
        return buffer;
    }

    /** Direct long transfer, used by neighbouring cables. No capability, no int, no round trip. */
    public long acceptLong(long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long accepted = Math.min(amount, CAPACITY - buffer);
        if (accepted > 0L) {
            buffer += accepted;
            setChanged();
        }
        return accepted;
    }

    public long extractLong(long amount) {
        if (amount <= 0L) {
            return 0L;
        }
        long removed = Math.min(amount, buffer);
        if (removed > 0L) {
            buffer -= removed;
            setChanged();
        }
        return removed;
    }

    // ------------------------------------------------------------------ tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, PowerCableBlockEntity cable) {
        if (level instanceof ServerLevel serverLevel) {
            cable.tick(serverLevel, pos);
        }
    }

    private void tick(ServerLevel level, BlockPos pos) {
        long budget = TRANSFER_PER_TICK;

        // 1. PULL from anything that has power to give.
        boolean[] pulledFrom = new boolean[Direction.values().length];
        for (Direction direction : Direction.values()) {
            if (budget <= 0L) {
                break;
            }
            long moved = pull(level, pos.relative(direction), direction.getOpposite(), budget);
            if (moved > 0L) {
                pulledFrom[direction.ordinal()] = true;
            }
            budget -= moved;
        }

        // 2. PUSH into anything that wants power. Never back into what we just drained - a battery
        //    that both gives and takes would otherwise ping-pong the same energy all tick.
        for (Direction direction : Direction.values()) {
            if (budget <= 0L || buffer <= 0L) {
                break;
            }
            if (pulledFrom[direction.ordinal()]) {
                continue;
            }
            budget -= push(level, pos.relative(direction), direction.getOpposite(), budget);
        }

        // 3. Balance with neighbouring cables so a run behaves like one long trunk.
        for (Direction direction : Direction.values()) {
            if (budget <= 0L) {
                break;
            }
            BlockPos neighbourPos = pos.relative(direction);
            if (level.getBlockEntity(neighbourPos) instanceof PowerCableBlockEntity other) {
                long difference = buffer - other.buffer;
                if (difference > 1L) {
                    long move = Math.min(difference / 2L, budget);
                    move = other.acceptLong(move);
                    if (move > 0L) {
                        extractLong(move);
                        budget -= move;
                    }
                }
            }
        }

        lastMoved = TRANSFER_PER_TICK - budget;
    }

    // ------------------------------------------------------------------ foreign endpoints

    private long pull(ServerLevel level, BlockPos pos, Direction side, long budget) {
        long moved = 0L;

        // --- FE ---
        IEnergyStorage fe = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        if (fe != null && fe.canExtract()) {
            moved += pullFe(fe, budget - moved);
        }

        // --- EMC ---
        moved += pullEmc(level, pos, side, budget - moved);
        return moved;
    }

    /**
     * Drain an FE store in Integer.MAX_VALUE bites. This is the whole trick: the interface is int,
     * our loop is not.
     */
    private long pullFe(IEnergyStorage source, long budget) {
        long room = CAPACITY - buffer;
        long wanted = Math.min(budget, room);
        long moved = 0L;
        while (wanted > 0L) {
            int bite = (int) Math.min(wanted, Integer.MAX_VALUE);
            int got = source.extractEnergy(bite, false);
            if (got <= 0) {
                break;
            }
            buffer += got;
            moved += got;
            wanted -= got;
            if (got < bite) {
                break; // source is empty or rate-limited; it will refill for next tick
            }
        }
        if (moved > 0L) {
            setChanged();
        }
        return moved;
    }

    private long pullEmc(ServerLevel level, BlockPos pos, Direction side, long budget) {
        if (budget <= 0L || !ProjectEEmc.isAvailable()) {
            return 0L;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!ProjectEEmc.isEmcStorage(be)) {
            return 0L;
        }
        long room = CAPACITY - buffer;
        if (room <= 0L) {
            return 0L;
        }
        long feWanted = Math.min(budget, room);
        long emcAsked = saturatedMultiply(feWanted, EMC_PER_FE);
        long emcMoved = ProjectEEmc.extractEmc(be, emcAsked, side);
        if (emcMoved <= 0L) {
            return 0L;
        }
        long feGained = emcMoved / EMC_PER_FE;
        if (feGained <= 0L) {
            return 0L; // not even one whole unit: keep the EMC where it is
        }
        buffer += feGained;
        setChanged();
        return feGained;
    }

    private long push(ServerLevel level, BlockPos pos, Direction side, long budget) {
        long moved = 0L;

        IEnergyStorage fe = level.getCapability(Capabilities.EnergyStorage.BLOCK, pos, side);
        if (fe != null && fe.canReceive()) {
            moved += pushFe(fe, budget - moved);
        }
        moved += pushEmc(level, pos, side, budget - moved);
        return moved;
    }

    private long pushFe(IEnergyStorage target, long budget) {
        long wanted = Math.min(budget, buffer);
        long moved = 0L;
        while (wanted > 0L) {
            int bite = (int) Math.min(wanted, Integer.MAX_VALUE);
            int accepted = target.receiveEnergy(bite, false);
            if (accepted <= 0) {
                break;
            }
            buffer -= accepted;
            moved += accepted;
            wanted -= accepted;
            if (accepted < bite) {
                break; // target is full or rate-limited
            }
        }
        if (moved > 0L) {
            setChanged();
        }
        return moved;
    }

    private long pushEmc(ServerLevel level, BlockPos pos, Direction side, long budget) {
        if (budget <= 0L || buffer < FE_PER_EMC || !ProjectEEmc.isAvailable()) {
            return 0L;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!ProjectEEmc.isEmcStorage(be)) {
            return 0L;
        }
        long emcRoom = ProjectEEmc.roomFor(be);
        if (emcRoom <= 0L) {
            return 0L;
        }
        long feSpendable = Math.min(budget, buffer);
        long emcWanted = Math.min(emcRoom, feSpendable / FE_PER_EMC);
        if (emcWanted <= 0L) {
            return 0L;
        }
        long emcInserted = ProjectEEmc.insertEmc(be, emcWanted, side);
        if (emcInserted <= 0L) {
            return 0L;
        }
        long feSpent = saturatedMultiply(emcInserted, FE_PER_EMC);
        feSpent = Math.min(feSpent, buffer);
        buffer -= feSpent;
        setChanged();
        return feSpent;
    }

    private static long saturatedMultiply(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    /** FE-equivalent throughput moved last tick. Surfaced in the cable's chat status. */
    public long lastMoved() {
        return lastMoved;
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putLong("Buffer", buffer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer = Math.max(0L, Math.min(CAPACITY, tag.getLong("Buffer")));
    }
}
