package com.shadowclangaming.block;

import com.shadowclangaming.entity.MissileEntity;
import com.shadowclangaming.item.MissileItem;
import com.shadowclangaming.registry.SCGBlockEntities;
import com.shadowclangaming.warhead.WarheadType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import org.jetbrains.annotations.Nullable;

/**
 * One loaded missile, one last target, one cooldown.
 *
 * Two ways to fire:
 *   - redstone pulse      -> fires at the last target set (great for automatic defences)
 *   - bound designator    -> {@link #fireAt(BlockPos)} with whatever the player was looking at
 */
public class LaunchPadBlockEntity extends BlockEntity implements com.shadowclangaming.network.ScgNet.ConsoleHost {
    private static final int RELOAD_TICKS = 100; // 5 seconds

    private ItemStack missile = ItemStack.EMPTY;

    // ------------------------------------------------------------------ the docked missile entity
    //
    // A LOADED MISSILE IS AN ENTITY. It sits in the silo, clamped upright on the pad, visible from outside
    // and targetable by anything that looks for entities - which is what makes a silo look armed instead
    // of looking like a chest with a missile in it. The pad keeps the entity's UUID so the pairing survives
    // a save; the ITEM stack stays the source of truth for warhead/funding/payload, and the entity's
    // display stack is re-synced from it, so there is exactly one place those numbers live.
    //
    // The point of the entity is not bookkeeping, it is the launch: firing re-uses this entity (undock and
    // give it a target) instead of spawning a new one, so what you watched sitting in the silo is what
    // leaves it.
    @org.jetbrains.annotations.Nullable
    private java.util.UUID dockedId;
    @org.jetbrains.annotations.Nullable
    private com.shadowclangaming.entity.MissileEntity dockedEntity;
    /**
     * Ticks we have looked for a saved UUID and not found the entity.
     *
     * A world load does not resurrect entities and block entities in a guaranteed order, so "the UUID does
     * not resolve" is normal for a moment. Only after a second of misses do we conclude the missile entity
     * is genuinely gone and rebuild it - otherwise a reload would leave a duplicate standing in the hatch.
     */
    private int dockedMissTicks;

    /** Cached silo-structure check, refreshed once a second. */
    private com.shadowclangaming.launcher.SiloStructure.Report structure =
            new com.shadowclangaming.launcher.SiloStructure.Report(false, java.util.List.of("not checked"));
    private int structureCooldown;
    /** Last position registered with the frequency registry, so a moving subgrid can re-register. */
    @org.jetbrains.annotations.Nullable
    private net.minecraft.core.GlobalPos registeredAt;
    @Nullable
    private BlockPos target = null;
    /** Channel this pad listens on. -1 = unassigned, which means no terminal can command it. */
    private int frequency = com.shadowclangaming.computer.Channels.NONE;
    private long nextLaunchTick = 0L;

    /** Launcher upgrade level, 1..5. The other parts are checked by LauncherLevel. */
    private int tier = 1;
    /**
     * Who is riding this missile when it launches.
     *
     * Boarding is a separate act from firing, so a player can climb on, look at the target readout, and
     * then press FIRE - or have somebody else fire it, which is the fun version.
     */
    private java.util.UUID riderId;
    /** How far out of range the last refused launch was, and by which tier. Drives the chat message. */
    private long lastRangeRefusal;
    /** Set when a launch was refused because the silo multiblock is incomplete. */
    @org.jetbrains.annotations.Nullable
    private String lastStructureRefusal;
    private int lastRangeTier = 5;

    /** Cargo for a CARGO warhead: what the chest will contain. */
    private final ItemStack[] cargo = new ItemStack[CARGO_SLOTS];
    /** Entity a Tracker locked for this pad, if any. */
    @Nullable
    private java.util.UUID trackedEntity;
    /** Target dimension for an interdimensional launch, set by the control panel. */
    @Nullable
    private net.minecraft.resources.ResourceKey<Level> targetDimension;

    /** FE loaded into the pad, spent as funding on the next launch. 1 FE = 1 currency unit. */
    public static final int ENERGY_CAPACITY = 4_000_000;
    public static final int ENERGY_RECEIVE = 20_000;
    public static final int CARGO_SLOTS = 27;

    // ------------------------------------------------------------------ the feed inventory
    //
    // SIXTEEN SLOTS, and they are a real inventory: hoppers, AE2 import buses and any other mod that
    // speaks IItemHandler can push currency into the pad without a player standing there. Whatever is
    // in them is converted into warhead funding once a second (see tickFeed), at the same rate the
    // player gets by right-clicking a coin onto the pad.
    public static final int FEED_SLOTS = 16;

    private final ItemStack[] feed = new ItemStack[FEED_SLOTS];
    /** Ticks until the next conversion pass. */
    private int feedCooldown;

    /**
     * The capability view. Insert-only in spirit - a hopper or an AE2 bus fills it, and only currency
     * is accepted so a pipe cannot clog the pad with dirt - but extraction is allowed so a player (or
     * a bus pointed the other way) can take coins back out.
     */
    private final net.neoforged.neoforge.items.IItemHandler feedHandler =
            new net.neoforged.neoforge.items.IItemHandler() {
                @Override
                public int getSlots() {
                    return FEED_SLOTS;
                }

                @Override
                public ItemStack getStackInSlot(int slot) {
                    return slot >= 0 && slot < FEED_SLOTS ? feed[slot] : ItemStack.EMPTY;
                }

                @Override
                public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                    if (stack.isEmpty() || slot < 0 || slot >= FEED_SLOTS) {
                        return stack;
                    }
                    if (!com.shadowclangaming.util.CurrencyValues.isCurrency(stack)) {
                        return stack; // not money: refuse it rather than let it sit there doing nothing
                    }
                    return insertInto(slot, stack, simulate);
                }

                @Override
                public ItemStack extractItem(int slot, int amount, boolean simulate) {
                    if (amount <= 0 || slot < 0 || slot >= FEED_SLOTS) {
                        return ItemStack.EMPTY;
                    }
                    ItemStack existing = feed[slot];
                    if (existing.isEmpty()) {
                        return ItemStack.EMPTY;
                    }
                    int moved = Math.min(amount, existing.getCount());
                    ItemStack out = existing.copyWithCount(moved);
                    if (!simulate) {
                        existing.shrink(moved);
                        if (existing.isEmpty()) {
                            feed[slot] = ItemStack.EMPTY;
                        }
                        setChanged();
                    }
                    return out;
                }

                @Override
                public int getSlotLimit(int slot) {
                    return 64;
                }

                @Override
                public boolean isItemValid(int slot, ItemStack stack) {
                    return com.shadowclangaming.util.CurrencyValues.isCurrency(stack);
                }
            };

    /** Shared by the handler and by anything else that wants to put money in (a hopper, a command). */
    private ItemStack insertInto(int slot, ItemStack stack, boolean simulate) {
        ItemStack existing = feed[slot];
        if (existing.isEmpty()) {
            int moved = Math.min(stack.getCount(), 64);
            if (!simulate) {
                feed[slot] = stack.copyWithCount(moved);
                setChanged();
            }
            return moved >= stack.getCount() ? ItemStack.EMPTY
                    : stack.copyWithCount(stack.getCount() - moved);
        }
        if (!ItemStack.isSameItemSameComponents(existing, stack)) {
            return stack;
        }
        int room = 64 - existing.getCount();
        if (room <= 0) {
            return stack;
        }
        int moved = Math.min(room, stack.getCount());
        if (!simulate) {
            existing.grow(moved);
            setChanged();
        }
        return moved >= stack.getCount() ? ItemStack.EMPTY
                : stack.copyWithCount(stack.getCount() - moved);
    }

    public net.neoforged.neoforge.items.IItemHandler feed() {
        return feedHandler;
    }

    /** How many of the sixteen slots hold something - shown on the pad screen. */
    public int feedSlotsUsed() {
        int used = 0;
        for (ItemStack stack : feed) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Total currency VALUE sitting in the feed, not the item count. */
    public long feedValue() {
        long value = 0L;
        for (ItemStack stack : feed) {
            value += com.shadowclangaming.util.CurrencyValues.valueOf(stack) * stack.getCount();
        }
        return value;
    }

    /**
     * Convert feed currency into warhead funding. Called once a second from the block ticker.
     *
     * Nothing is converted until a missile is loaded: coins sitting in an empty pad are stock, not
     * funding, and a player should be able to pre-load a pad without the money evaporating into a
     * warhead that is not there yet.
     */
    public void tickFeed() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (--feedCooldown > 0) {
            return;
        }
        feedCooldown = 20;
        if (missile.isEmpty() || !(missile.getItem() instanceof MissileItem)) {
            return;
        }
        // Cap the work per pass: one stack a second is plenty and keeps a full pad from being a spike.
        int budget = 64;
        long gained = 0L;
        for (int slot = 0; slot < FEED_SLOTS && budget > 0; slot++) {
            ItemStack stack = feed[slot];
            if (stack.isEmpty()) {
                continue;
            }
            long unit = com.shadowclangaming.util.CurrencyValues.valueOf(stack);
            if (unit <= 0L) {
                continue;
            }
            int take = (int) Math.min(stack.getCount(), budget);
            stack.shrink(take);
            if (stack.isEmpty()) {
                feed[slot] = ItemStack.EMPTY;
            }
            budget -= take;
            gained += unit * take;
        }
        if (gained > 0L) {
            MissileItem.setFunding(missile, MissileItem.fundingOf(missile) + gained);
            setChanged();
        }
    }

    /**
     * The buffer. maxExtract is the full capacity, not zero, and that matters twice over:
     * {@link #fundFromEnergy} spends it by calling extractEnergy - with maxExtract 0 NeoForge returns 0
     * and the FE is never consumed, so every launch paid out funding for energy it kept, which is free
     * money on repeat - and an EMP empties it. Nothing outside this class can pull from it anyway: the
     * capability this block advertises is {@link #energyView}, which is receive-only by design.
     */
    private final EnergyStorage energy =
            new EnergyStorage(ENERGY_CAPACITY, ENERGY_RECEIVE, ENERGY_CAPACITY);
    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return energy.receiveEnergy(toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0; // value goes up the pipe, never back down it
        }

        @Override
        public int getEnergyStored() {
            return energy.getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            return energy.getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    };

    public IEnergyStorage energy() {
        return energyView;
    }

    /**
     * EMP: empty the buffer outright.
     *
     * <p>The capability this machine advertises returns 0 from {@code extractEnergy}, so an EMP cannot
     * drain it the way it drains another mod's machine - it has to come in this way instead.
     *
     * @return the FE that was in the buffer
     */
    public int empDrain() {
        int drained = energy.extractEnergy(Integer.MAX_VALUE, false);
        if (drained > 0) {
            setChanged();
        }
        return drained;
    }

    public LaunchPadBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.LAUNCH_PAD.get(), pos, state);
        for (int i = 0; i < CARGO_SLOTS; i++) {
            cargo[i] = ItemStack.EMPTY;
        }
        for (int i = 0; i < FEED_SLOTS; i++) {
            feed[i] = ItemStack.EMPTY;
        }
    }

    // ---------------------------------------------------------------- launcher tiers and modules

    // ------------------------------------------------------------------ console host

    /**
     * The pad's own command line, as typed into the launch console screen.
     *
     * Verbs:
     *   status              the whole pad, in a few lines
     *   target x y z        set the aim point (any coordinate may be relative with ~)
     *   launch              fire at the target (or straight ahead if there is none)
     *   fund                turn stored FE into warhead funding now
     *   unload              pop the missile back into the player's inventory
     *   cargo clear         empty the cargo bay
     *   freq n              set the pad's channel
     */
    @Override
    public java.util.List<String> runConsoleCommand(net.minecraft.server.level.ServerLevel level,
                                                    net.minecraft.server.level.ServerPlayer player,
                                                    String line) {
        String[] parts = line.trim().split("\\s+");
        String verb = parts[0].toLowerCase(java.util.Locale.ROOT);
        java.util.List<String> out = new java.util.ArrayList<>();
        switch (verb) {
            case "status" -> {
                out.add("-- pad " + worldPosition.getX() + " " + worldPosition.getY() + " "
                        + worldPosition.getZ() + " --");
                out.add("tier " + tier + ", channel "
                        + com.shadowclangaming.computer.Channels.describe(frequency));
                out.add("missile " + (hasMissile() ? getMissile().getHoverName().getString() : "none"));
                if (hasMissile() && getMissile().getItem() instanceof MissileItem item) {
                    long funds = MissileItem.fundingOf(getMissile());
                    out.add("warhead " + item.warhead().id() + ", funding " + funds
                            + ", radius " + item.warhead().radiusForFunding(funds));
                }
                out.add("energy " + energy.getEnergyStored() + " / " + energy.getMaxEnergyStored() + " FE");
                out.add("cargo " + cargoSlotsUsed() + " slots");
                out.add("target " + (target == null ? "none" : target.getX() + " " + target.getY()
                        + " " + target.getZ()));
                int split = hasMissile()
                        ? com.shadowclangaming.warhead.MissilePayload.splitAltitudeOf(missile)
                        : com.shadowclangaming.warhead.MissilePayload.NO_SPLIT_ALTITUDE;
                out.add("split altitude " + (split == com.shadowclangaming.warhead.MissilePayload.NO_SPLIT_ALTITUDE
                        ? "auto" : "y " + split));
            }
            case "target" -> {
                if (parts.length < 4) {
                    out.add("! usage: target <x> <y> <z>");
                    break;
                }
                try {
                    BlockPos aim = new BlockPos(
                            coordinate(parts[1], player.getX()),
                            coordinate(parts[2], player.getY()),
                            coordinate(parts[3], player.getZ()));
                    setTarget(aim);
                    out.add("target set to " + aim.getX() + " " + aim.getY() + " " + aim.getZ());
                } catch (NumberFormatException error) {
                    out.add("! target expects three numbers");
                }
            }
            case "launch", "fire" -> {
                BlockPos aim = target != null ? target
                        : BlockPos.containing(player.pick(512.0D, 1.0F, false).getLocation());
                out.add(fireAt(aim) ? "launching at " + aim.getX() + " " + aim.getY() + " " + aim.getZ()
                        : "! cannot launch: " + (hasMissile() ? "still reloading" : "no missile"));
            }
            case "fund" -> {
                long gained = fundFromEnergy();
                out.add(gained > 0 ? "converted " + gained + " FE into funding"
                        : "! nothing to convert (no FE, or the warhead takes no funding)");
            }
            case "feed" -> {
                out.add("feed " + feedSlotsUsed() + "/" + FEED_SLOTS + " slots, worth " + feedValue()
                        + " currency");
                out.add(hasMissile()
                        ? "converting 1 stack/second into funding on the loaded warhead"
                        : "! no missile loaded - nothing converts until one is");
            }
            case "unload" -> {
                ItemStack removed = unloadMissile();
                if (removed.isEmpty()) {
                    out.add("! nothing loaded");
                } else if (!player.getInventory().add(removed)) {
                    player.drop(removed, false);
                    out.add("inventory full: dropped " + removed.getHoverName().getString());
                } else {
                    out.add("unloaded " + removed.getHoverName().getString());
                }
            }
            case "cargo" -> {
                if (parts.length > 1 && parts[1].equalsIgnoreCase("clear")) {
                    clearCargo();
                    out.add("cargo bay emptied");
                } else {
                    out.add("cargo holds " + cargoSlotsUsed() + " stacks");
                }
            }
            case "split" -> {
                if (!hasMissile()) {
                    out.add("! no missile loaded - split altitude lives on the missile");
                    break;
                }
                if (parts.length < 2) {
                    int altitude = com.shadowclangaming.warhead.MissilePayload.splitAltitudeOf(missile);
                    out.add(altitude == com.shadowclangaming.warhead.MissilePayload.NO_SPLIT_ALTITUDE
                            ? "split altitude: auto (frame proximity rule)"
                            : "split altitude: y " + altitude);
                    break;
                }
                if (parts[1].equalsIgnoreCase("auto") || parts[1].equalsIgnoreCase("off")) {
                    com.shadowclangaming.warhead.MissilePayload.setSplitAltitude(missile,
                            com.shadowclangaming.warhead.MissilePayload.NO_SPLIT_ALTITUDE);
                    setChanged();
                    out.add("split altitude cleared: the frame decides when to open");
                    break;
                }
                try {
                    int altitude = coordinate(parts[1], player.getY());
                    com.shadowclangaming.warhead.MissilePayload.setSplitAltitude(missile, altitude);
                    setChanged();
                    boolean cluster = missile.getItem() instanceof com.shadowclangaming.item.MissileModuleItem
                            && ((com.shadowclangaming.item.MissileModuleItem) missile.getItem()).kind()
                                    .subMunitions() > 0;
                    out.add("split altitude set to y " + altitude
                            + (cluster ? "" : " (this missile does not break up - ignored until a cluster frame is loaded)"));
                } catch (NumberFormatException error) {
                    out.add("! split expects a Y level, or 'auto'");
                }
            }
            case "freq", "channel" -> {
                if (parts.length < 2) {
                    out.add("channel is " + frequency);
                    break;
                }
                int wanted = com.shadowclangaming.computer.Channels.parse(parts[1]);
                if (wanted == com.shadowclangaming.computer.Channels.NONE && !parts[1].equals("none")) {
                    out.add("! freq expects a whole number (any sign), or 'none' to clear");
                    break;
                }
                setFrequency(wanted);
                out.add("channel set to " + com.shadowclangaming.computer.Channels.describe(frequency));
            }
            default -> out.add("! unknown command '" + verb
                    + "' - try status, target, launch, fund, unload, cargo, freq, split");
        }
        return out;
    }

    /** "12" or "~5" or "~-5" - the same relative syntax a teleport uses, because players already know it. */
    private static int coordinate(String text, double origin) {
        if (text.startsWith("~")) {
            String rest = text.substring(1);
            return (int) Math.floor(origin + (rest.isEmpty() ? 0.0D : Double.parseDouble(rest)));
        }
        return (int) Math.floor(Double.parseDouble(text));
    }

    // ------------------------------------------------------------------ menu view

    /** Live numbers for the launch pad screen. See TerminalBlockEntity#data for the same pattern. */
    public net.minecraft.world.inventory.ContainerData data() {
        return new net.minecraft.world.inventory.ContainerData() {
            @Override
            public int get(int index) {
                long funding = missile.isEmpty() || !(missile.getItem() instanceof MissileItem item)
                        ? 0L
                        : MissileItem.fundingOf(missile);
                int radius = missile.isEmpty() || !(missile.getItem() instanceof MissileItem item)
                        ? 0
                        : item.warhead().radiusForFunding(funding);
                return switch (index) {
                    case 0 -> tier;
                    case 1 -> hasMissile() ? 1 : 0;
                    case 2 -> (int) (funding & 0xFFFFFFFFL);
                    case 3 -> (int) (funding >>> 32);
                    case 4 -> radius;
                    case 5 -> energy.getEnergyStored();
                    case 6 -> energy.getMaxEnergyStored();
                    case 7 -> cargoSlotsUsed();
                    case 8 -> frequency;
                    case 9 -> (int) Math.max(0L, nextLaunchTick - (level == null ? 0L : level.getGameTime()));
                    case 10 -> target == null ? Integer.MIN_VALUE : target.getX();
                    case 11 -> target == null ? Integer.MIN_VALUE : target.getY();
                    case 12 -> target == null ? Integer.MIN_VALUE : target.getZ();
                    // 13: the warhead. A module reports what is FITTED to it (DUD when empty), which is
                    // what the screen should draw - the frame is not the weapon.
                    case 13 -> missile.isEmpty() ? -1
                            : missile.getItem() instanceof com.shadowclangaming.item.MissileModuleItem
                            ? com.shadowclangaming.item.MissileModuleItem.warheadOf(missile).ordinal()
                            : missile.getItem() instanceof MissileItem item ? item.warhead().ordinal() : -1;
                    // 19: module tier, or 5 for a plain missile (treated as unlimited).
                    case 19 -> missile.getItem() instanceof com.shadowclangaming.item.MissileModuleItem module
                            ? module.tier().tier() : 5;
                    case 14 -> targetDimension == null ? 0 : 1;
                    case 15 -> panel() != null ? 1 : 0;
                    case 16 -> feedSlotsUsed();
                    // 17: the radius slider on the loaded missile (-1 = not set).
                    case 17 -> missile.isEmpty() ? -1
                            : com.shadowclangaming.warhead.MissilePayload.radiusOf(missile);
                    // 18: how many stacks the missile is carrying.
                    case 18 -> missile.isEmpty() ? 0
                            : com.shadowclangaming.warhead.MissilePayload.count(missile);
                    // 20: the split altitude set on the missile, or the "auto" sentinel.
                    case 20 -> missile.isEmpty()
                            ? com.shadowclangaming.warhead.MissilePayload.NO_SPLIT_ALTITUDE
                            : com.shadowclangaming.warhead.MissilePayload.splitAltitudeOf(missile);
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // read-only
            }

            @Override
            public int getCount() {
                return 21;
            }
        };
    }

    public int tier() {
        return tier;
    }

    public void setTier(int tier) {
        this.tier = Math.max(1, Math.min(com.shadowclangaming.launcher.LauncherLevel.MAX, tier));
        setChanged();
    }

    /** The control panel wired to this pad, or null. Searched on demand, never cached. */
    @Nullable
    public ControlPanelBlockEntity panel() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        for (BlockPos cursor : BlockPos.betweenClosed(
                worldPosition.offset(-com.shadowclangaming.launcher.LauncherLevel.STRUCTURE_RADIUS, -6,
                        -com.shadowclangaming.launcher.LauncherLevel.STRUCTURE_RADIUS),
                worldPosition.offset(com.shadowclangaming.launcher.LauncherLevel.STRUCTURE_RADIUS, 6,
                        com.shadowclangaming.launcher.LauncherLevel.STRUCTURE_RADIUS))) {
            if (serverLevel.getBlockEntity(cursor) instanceof ControlPanelBlockEntity panel) {
                return panel;
            }
        }
        return null;
    }

    /** Full structure report: pad tier vs panel vs rods. */
    public com.shadowclangaming.launcher.LauncherLevel.Report report() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return new com.shadowclangaming.launcher.LauncherLevel.Report(tier, 0, 0, 0, 0, false);
        }
        return com.shadowclangaming.launcher.LauncherLevel.scan(serverLevel, worldPosition, tier);
    }

    /**
     * May this pad launch into another dimension? Only with the module on the linked panel AND a
     * launcher that is level 5 in every part - pad, panel and both rods. That is the whole gate, in
     * one place, so the panel, the terminal and the pad can never disagree about it.
     */
    public boolean hasInterdimensionalClearance(ControlPanelBlockEntity panel,
                                                com.shadowclangaming.launcher.LauncherLevel.Report report) {
        return panel != null
                && panel.has(com.shadowclangaming.launcher.LauncherModule.INTERDIMENSIONAL)
                && com.shadowclangaming.launcher.LauncherLevel.meets(report,
                com.shadowclangaming.launcher.LauncherModule.INTERDIMENSIONAL.requiredLevel());
    }

    public void setTrackedEntity(@Nullable java.util.UUID entity) {
        this.trackedEntity = entity;
        setChanged();
    }

    @Nullable
    public java.util.UUID trackedEntity() {
        return trackedEntity;
    }

    public void setTargetDimension(@Nullable net.minecraft.resources.ResourceKey<Level> dimension) {
        this.targetDimension = dimension;
        setChanged();
    }

    // ---------------------------------------------------------------- cargo

    public boolean loadCargo(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        for (int i = 0; i < CARGO_SLOTS; i++) {
            if (cargo[i].isEmpty()) {
                cargo[i] = stack.copy();
                setChanged();
                return true;
            }
            if (ItemStack.isSameItemSameComponents(cargo[i], stack)
                    && cargo[i].getCount() < cargo[i].getMaxStackSize()) {
                int moved = Math.min(stack.getCount(),
                        cargo[i].getMaxStackSize() - cargo[i].getCount());
                cargo[i].grow(moved);
                stack.shrink(moved);
                setChanged();
                return true;
            }
        }
        return false;
    }

    public java.util.List<ItemStack> cargoList() {
        java.util.List<ItemStack> list = new java.util.ArrayList<>();
        for (ItemStack stack : cargo) {
            if (!stack.isEmpty()) {
                list.add(stack.copy());
            }
        }
        return list;
    }

    /**
     * Direct slot access for the silo hold GUI.
     *
     * The pad's cargo was already a real 27-slot inventory - hoppers and AE2 buses could write into it -
     * but there was no way for a PLAYER to open it: the launch console has no slots at all, and the only
     * other paths were the {@code cargo} console command and shift-clicking a missile to unload it. These
     * two accessors are what let a chest menu sit on top of it.
     */
    public ItemStack getCargo(int slot) {
        return slot >= 0 && slot < CARGO_SLOTS ? cargo[slot] : ItemStack.EMPTY;
    }

    public void setCargo(int slot, ItemStack stack) {
        if (slot < 0 || slot >= CARGO_SLOTS) {
            return;
        }
        cargo[slot] = stack == null ? ItemStack.EMPTY : stack;
        setChanged();
    }

    public int cargoSlotsUsed() {
        int used = 0;
        for (ItemStack stack : cargo) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    public void clearCargo() {
        for (int i = 0; i < CARGO_SLOTS; i++) {
            cargo[i] = ItemStack.EMPTY;
        }
        setChanged();
    }

    // ---------------------------------------------------------------- energy funding

    /**
     * Converts stored FE into funding on the loaded warhead, at 1 currency unit per FE - the same rate
     * the Auto-Trader uses in reverse (100 FE = $1, and $1 is 100 units).
     *
     * @return units actually added
     */
    public long fundFromEnergy() {
        if (!energyAcceptsFunding()) {
            return 0L;
        }
        long units = energy.getEnergyStored();
        if (units <= 0L) {
            return 0L;
        }
        long capped = Math.min(units, Integer.MAX_VALUE);
        if (!addFunding(capped)) {
            return 0L;
        }
        energy.extractEnergy((int) capped, false);
        return capped;
    }

    /** Can the loaded warhead take FE as funding at all? */
    public boolean energyAcceptsFunding() {
        return !missile.isEmpty() && missile.getItem() instanceof MissileItem item
                && item.warhead().acceptsFunding();
    }

    // ---------------------------------------------------------------- missiles

    public boolean hasMissile() {
        return !missile.isEmpty();
    }

    public ItemStack getMissile() {
        return missile;
    }

    public boolean loadMissile(ItemStack stack) {
        if (hasMissile()) {
            return false;
        }
        missile = stack.copyWithCount(1);
        spawnDocked();
        setChanged();
        return true;
    }

    public ItemStack unloadMissile() {
        ItemStack out = missile;
        missile = ItemStack.EMPTY;
        discardDocked();
        setChanged();
        return out;
    }

    /**
     * Put the loaded missile into the world as a docked entity.
     *
     * Server side only, and idempotent: if the entity already exists it is re-synced rather than
     * duplicated. The entity carries a COPY of the item stack purely for display and for the flight that
     * follows - the pad's stack is the one everything reads.
     */
    public void spawnDocked() {
        if (level == null || level.isClientSide || missile.isEmpty()) {
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (docked() != null) {
            dockedMissTicks = 0;
            syncDocked();
            return;
        }
        if (dockedId != null && ++dockedMissTicks < 20) {
            // Still looking for the one we saved. Do not create a second one yet.
            return;
        }
        dockedMissTicks = 0;
        dockedId = null;
        com.shadowclangaming.entity.MissileEntity entity = new com.shadowclangaming.entity.MissileEntity(
                serverLevel,
                worldPosition.getX() + 0.5D, worldPosition.getY() + 1.0D, worldPosition.getZ() + 0.5D,
                worldPosition.above(64), missile.copyWithCount(1), WarheadOf(missile), 0.0F,
                0.75D, 0L, null, java.util.List.of(), null, null, -1, 0L);
        entity.dock(worldPosition);
        serverLevel.addFreshEntity(entity);
        dockedEntity = entity;
        dockedId = entity.getUUID();
    }

    /** The warhead on a stack, counting a fitted module. */
    private static com.shadowclangaming.warhead.WarheadType WarheadOf(ItemStack stack) {
        if (stack.getItem() instanceof com.shadowclangaming.item.MissileModuleItem) {
            return com.shadowclangaming.item.MissileModuleItem.warheadOf(stack);
        }
        return stack.getItem() instanceof MissileItem item ? item.warhead()
                : com.shadowclangaming.warhead.WarheadType.HE;
    }

    /** Remove the docked entity from the world (unloading, breaking the pad, launching). */
    public void discardDocked() {
        com.shadowclangaming.entity.MissileEntity entity = docked();
        if (entity != null) {
            entity.discard();
        }
        dockedEntity = null;
        dockedId = null;
    }

    /** The docked entity, resolved lazily from its UUID after a reload. */
    @org.jetbrains.annotations.Nullable
    public com.shadowclangaming.entity.MissileEntity docked() {
        if (dockedId == null) {
            return null;
        }
        if (dockedEntity != null && !dockedEntity.isRemoved() && dockedEntity.isDocked()) {
            return dockedEntity;
        }
        if (level instanceof ServerLevel serverLevel) {
            net.minecraft.world.entity.Entity found = serverLevel.getEntity(dockedId);
            if (found instanceof com.shadowclangaming.entity.MissileEntity entity
                    && entity.isDocked() && entity.dockedAt(worldPosition)) {
                dockedEntity = entity;
                return entity;
            }
        }
        return null;
    }

    /** Push the pad's item data onto the docked entity, so what you see is what will fire. */
    public void syncDocked() {
        com.shadowclangaming.entity.MissileEntity entity = docked();
        if (entity != null && !missile.isEmpty()) {
            entity.setItem(missile.copyWithCount(1));
        }
    }

    /** Called once a tick from the block: keeps the entity, the structure and the channel honest. */
    public void tickServer() {
        if (level == null || level.isClientSide) {
            return;
        }
        if (!missile.isEmpty()) {
            spawnDocked();
        }
        if (--structureCooldown <= 0) {
            structureCooldown = 20;
            structure = inspectStructure();
        }
        // A Sable subgrid moves its blocks without a block update: re-register the channel so a
        // frequency-based designator keeps pointing at this pad wherever the grid has drifted to.
        if (com.shadowclangaming.computer.Channels.isSet(frequency)) {
            net.minecraft.core.GlobalPos now =
                    net.minecraft.core.GlobalPos.of(level.dimension(), worldPosition);
            if (!now.equals(registeredAt)) {
                com.shadowclangaming.computer.FrequencyRegistry.get(level.getServer())
                        .bind(frequency, now);
                registeredAt = now;
            }
        }
    }

    // ------------------------------------------------------------------ the silo structure

    /** The last structure check, for the GUI and the launch gate. */
    public com.shadowclangaming.launcher.SiloStructure.Report structure() {
        return structure;
    }

    /** True when the multiblock around this pad is complete. */
    public boolean structureComplete() {
        return structure.complete();
    }

    /**
     * Read the blocks around the pad and report what is missing.
     *
     * Nine block reads for the frame plus one pass over the shaft, once a second - cheap enough to run on
     * any tick, and it means a silo that gets damaged in a raid stops firing without the player having to
     * re-place the pad.
     */
    public com.shadowclangaming.launcher.SiloStructure.Report inspectStructure() {
        if (level == null) {
            return structure;
        }
        return com.shadowclangaming.launcher.SiloStructure.inspect(
                new com.shadowclangaming.launcher.SiloStructure.Probe() {
                    @Override
                    public boolean matches(int dx, int dy, int dz,
                                           com.shadowclangaming.launcher.SiloStructure.Part part) {
                        BlockPos at = worldPosition.offset(dx, dy, dz);
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
                        return switch (part) {
                            case PAD -> state.getBlock() instanceof com.shadowclangaming.block.LaunchPadBlock;
                            case ROD -> state.getBlock()
                                    instanceof com.shadowclangaming.block.SupportRodBlock;
                            case PANEL -> state.getBlock()
                                    instanceof com.shadowclangaming.block.ControlPanelBlock;
                        };
                    }

                    @Override
                    public boolean shaftClear(int dx, int dy, int dz) {
                        BlockPos at = worldPosition.offset(dx, dy, dz);
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(at);
                        return state.getCollisionShape(level, at).isEmpty();
                    }
                });
    }

    /**
     * Adds currency to the loaded Numismatic warhead.
     *
     * @return true if the currency was accepted.
     */
    public boolean addFunding(long amount) {
        if (missile.isEmpty() || !(missile.getItem() instanceof MissileItem item)
                || !item.warhead().acceptsFunding()) {
            return false;
        }
        MissileItem.setFunding(missile, MissileItem.fundingOf(missile) + amount);
        setChanged();
        return true;
    }

    /** Put a player on the missile. Called from the block when they sneak-right-click the pad. */
    public boolean board(net.minecraft.world.entity.player.Player player) {
        if (!hasMissile()) {
            return false;
        }
        riderId = player.getUUID();
        setChanged();
        return true;
    }

    public void disembark() {
        riderId = null;
        setChanged();
    }

    public boolean hasRider() {
        return riderId != null;
    }

    /** Why the last launch was refused by the silo structure, or null if it was not. */
    @org.jetbrains.annotations.Nullable
    public String lastStructureRefusal() {
        return lastStructureRefusal;
    }

    /** How far out of range the last launch attempt was (0 when it was in range). */
    public long lastRangeRefusal() {
        return lastRangeRefusal;
    }

    public int lastRangeTier() {
        return lastRangeTier;
    }

    /**
     * Set the radius slider on the loaded missile.
     *
     * The value lives on the ITEM, not the pad, so it travels with the missile: pull it out, put it in
     * another silo, and the radius you chose is still there. -1 clears the override.
     *
     * Range is 0..4096 to match the radar's cap; a constructor warhead refuses a radius it cannot fill
     * and says how many blocks are short, so a big number is not a way to cheat the payload.
     */
    public void setSliderRadius(int radius) {
        if (missile.isEmpty() || !(missile.getItem() instanceof MissileItem)) {
            return;
        }
        int clamped = Math.max(-1, Math.min(4096, radius));
        com.shadowclangaming.warhead.MissilePayload.setRadius(missile, clamped);
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void setTarget(BlockPos target) {
        this.target = target;
        setChanged();
    }

    @Nullable
    public BlockPos getTarget() {
        return target;
    }

    public boolean isReady() {
        return hasMissile() && target != null;
    }

    /** Called by the block when it receives redstone. */
    public void tryLaunch() {
        if (target != null) {
            fireAt(target);
        }
    }

    /**
     * Fires the loaded missile at {@code targetPos}.
     *
     * @return true if a missile actually left the pad (false = empty, cooling down, or client side).
     */
    public boolean fireAt(BlockPos targetPos) {
        Level level = this.level;
        if (!(level instanceof ServerLevel serverLevel)) {
            return false;
        }
        if (missile.isEmpty() || !(missile.getItem() instanceof MissileItem missileItem)) {
            return false;
        }
        if (serverLevel.getGameTime() < nextLaunchTick) {
            return false;
        }

        // A pad on its own is a hole in the ground. The silo has to be built: eight support rods, a
        // control panel, and a clear shaft. This is the whole reason the pad is a multiblock, and the
        // refusal says what is missing rather than just doing nothing.
        if (!structureComplete()) {
            structure = inspectStructure();
            lastStructureRefusal = structure.summary();
            return false;
        }
        lastStructureRefusal = null;

        // RANGE: a module's tier decides how far this shot may go. Plain missiles are treated as T5 so
        // nothing that worked before starts failing.
        com.shadowclangaming.launcher.ModuleTier tier =
                missile.getItem() instanceof com.shadowclangaming.item.MissileModuleItem module
                        ? module.tier() : com.shadowclangaming.launcher.ModuleTier.DEFAULT;
        double distanceSqr = Vec3.atCenterOf(worldPosition).distanceToSqr(Vec3.atCenterOf(targetPos));
        if (!tier.canReach(distanceSqr)) {
            lastRangeRefusal = tier.overBy(distanceSqr);
            lastRangeTier = tier.tier();
            return false;
        }
        lastRangeRefusal = 0L;

        this.target = targetPos;
        nextLaunchTick = serverLevel.getGameTime() + RELOAD_TICKS;

        // FE funding: any charge in the pad is converted into warhead funding before the missile
        // leaves, so a "currency or FE" warhead never needs coins if the base is wired.
        fundFromEnergy();

        // Interdimensional: only a level 5 launcher with the module on its panel may aim elsewhere.
        net.minecraft.resources.ResourceKey<Level> dimension = null;
        ControlPanelBlockEntity panel = panel();
        if (targetDimension != null) {
            boolean allowed = hasInterdimensionalClearance(panel, report())
                    && !targetDimension.equals(serverLevel.dimension());
            if (allowed) {
                dimension = targetDimension;
            }
        }

        // Payload = whatever was loaded into the missile itself (right-clicking it) plus anything
        // posted into the pad's hold. The missile's own hold is the primary path now; the pad's slots
        // stay for muscle memory and for automation.
        java.util.List<ItemStack> payload = new java.util.ArrayList<>(
                com.shadowclangaming.warhead.MissilePayload.of(missile));
        for (ItemStack stack : cargoList()) {
            if (payload.size() >= com.shadowclangaming.warhead.CargoDelivery.MAX_STACKS) {
                break;
            }
            payload.add(stack);
        }

        // The radius slider, if the player moved it. -1 means "let the warhead's own rule decide".
        int sliderRadius = com.shadowclangaming.warhead.MissilePayload.radiusOf(missile);
        long seed = worldPosition.asLong() * 31L + serverLevel.getGameTime();

        // Which warhead is actually fitted: a module reads its component, a plain missile its item.
        com.shadowclangaming.warhead.WarheadType warhead =
                missile.getItem() instanceof com.shadowclangaming.item.MissileModuleItem
                        ? com.shadowclangaming.item.MissileModuleItem.warheadOf(missile)
                        : missileItem.warhead();

        // THE MISSILE THAT LEAVES IS THE MISSILE THAT WAS SITTING THERE. It is already an entity, already
        // visible, already clamped in the pad; launching clears the dock state and hands it a target. No
        // new entity, no delete-and-recreate, and the item stack it carries is the one the pad has been
        // reading, so the flight cannot disagree with the silo read-out.
        MissileEntity entity = docked();
        if (entity == null) {
            // No docked entity (a legacy save, or something removed it): make one now rather than
            // silently failing to launch.
            spawnDocked();
            entity = docked();
        }
        if (entity == null) {
            Vec3 spawn = Vec3.atBottomCenterOf(worldPosition.above());
            entity = new MissileEntity(serverLevel, spawn.x, spawn.y, spawn.z, targetPos,
                    missile.copyWithCount(1), warhead, missileItem.powerFor(missile),
                    missileItem.cruiseSpeed(), MissileItem.fundingOf(missile),
                    MissileItem.spellOf(missile), payload, trackedEntity, dimension, sliderRadius, seed);
            serverLevel.addFreshEntity(entity);
        } else {
            // Re-point the entity at the pad's stack first: the pad holds the authority, and the frame
            // kind (homing/cluster/high-velocity/transport) is read from that stack on launch.
            entity.setItem(missile.copyWithCount(1));
            entity.undockAndLaunch(targetPos, warhead, missileItem.powerFor(missile),
                    missileItem.cruiseSpeed(), MissileItem.fundingOf(missile),
                    MissileItem.spellOf(missile), payload, trackedEntity, dimension,
                    sliderRadius, seed);
        }
        // The pad no longer owns it: it is in flight. Clearing the UUID first stops discardDocked() from
        // deleting the missile we just launched.
        dockedEntity = null;
        dockedId = null;

        // RIDING: if somebody boarded this pad, they ride the missile down.
        if (riderId != null) {
            net.minecraft.server.level.ServerPlayer rider =
                    serverLevel.getServer().getPlayerList().getPlayer(riderId);
            if (rider != null && rider.isAlive()) {
                rider.startRiding(entity, true);
                rider.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.shadowclangaming.riding_missile"), true);
            }
            riderId = null;
        }

        unloadMissile();
        clearCargo();
        trackedEntity = null;
        this.targetDimension = null;
        return true;
    }

    /** EMP knockout: pads go silent for a while. */
    /** The channel a terminal must be tuned to in order to fire this pad. */
    public int frequency() {
        return frequency;
    }

    public void setFrequency(int frequency) {
        int sanitised = com.shadowclangaming.computer.Channels.sanitise(frequency);
        if (sanitised == this.frequency) {
            return;
        }
        int previous = this.frequency;
        this.frequency = sanitised;
        setChanged();
        // REGISTER IMMEDIATELY, not on the next tick. The designator and the terminal both resolve a channel
        // through FrequencyRegistry, so a pad that only registers on its own tick is invisible to a designator
        // used in the same second it was tuned - which is exactly how a player tests it.
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            var registry = com.shadowclangaming.computer.FrequencyRegistry.get(serverLevel.getServer());
            net.minecraft.core.GlobalPos self =
                    net.minecraft.core.GlobalPos.of(serverLevel.dimension(), worldPosition);
            if (com.shadowclangaming.computer.Channels.isSet(previous)) {
                // A pad is only ever on ONE channel: leaving the old entry behind means two channels claim
                // the same silo and a designator can fire a pad that was re-tuned away.
                registry.unbind(self);
            }
            if (com.shadowclangaming.computer.Channels.isSet(sanitised)) {
                registry.bind(sanitised, self);
                registeredAt = self;
            } else {
                registeredAt = null;
            }
        }
    }

    /** Has this pad ever been tuned? Unset is not "-1": channels may be negative (see Channels). */
    public boolean hasChannel() {
        return com.shadowclangaming.computer.Channels.isSet(frequency);
    }

    /**
     * Breaking the pad takes the missile with it.
     *
     * The entity is not a decoration that can be left behind: it belongs to this pad, and a pad that is
     * gone cannot dock it. setRemoved runs for chunk unload as well, so the check is on whether the block
     * is still a pad - unloading a chunk must NOT eat the missile.
     */
    @Override
    public void setRemoved() {
        if (level != null && !level.isClientSide
                && !(level.getBlockState(worldPosition).getBlock()
                instanceof com.shadowclangaming.block.LaunchPadBlock)) {
            discardDocked();
        }
        super.setRemoved();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        // Re-register after a restart: the channel map is saved data, but the pad's own field is the
        // authority, so whichever side is missing gets filled in here.
        if (com.shadowclangaming.computer.Channels.isSet(frequency)
                && level instanceof ServerLevel serverLevel) {
            com.shadowclangaming.computer.FrequencyRegistry.get(serverLevel.getServer())
                    .bind(frequency, net.minecraft.core.GlobalPos.of(level.dimension(), worldPosition));
            registeredAt = net.minecraft.core.GlobalPos.of(level.dimension(), worldPosition);
        }
        // A missile that was in the tube when the world was saved is back in the tube when it loads.
        if (!missile.isEmpty()) {
            spawnDocked();
        }
    }

    public void applyEmp(int ticks) {
        Level level = this.level;
        if (level == null) {
            return;
        }
        nextLaunchTick = Math.max(nextLaunchTick, level.getGameTime() + ticks);
        setChanged();
    }

    // ---------------------------------------------------------------- save / load

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!missile.isEmpty()) {
            ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, missile)
                    .result()
                    .ifPresent(saved -> tag.put("Missile", saved));
        }
        tag.putInt("Frequency", frequency);
        tag.putInt("Tier", tier);
        // The docked missile entity's identity, so the pad and the missile in it re-find each other after
        // a restart. The item stack above is still the payload authority; this only restores the pairing.
        if (dockedId != null) {
            tag.putUUID("DockedMissile", dockedId);
        }
        if (riderId != null) {
            tag.putUUID("Rider", riderId);
        }
        if (trackedEntity != null) {
            tag.putUUID("TrackedEntity", trackedEntity);
        }
        if (targetDimension != null) {
            tag.putString("TargetDimension", targetDimension.location().toString());
        }
        tag.putInt("Energy", energy.getEnergyStored());
        riderId = tag.contains("Rider") ? tag.getUUID("Rider") : null;
        net.minecraft.nbt.ListTag feedTag = new net.minecraft.nbt.ListTag();
        for (ItemStack stack : feed) {
            ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).result().ifPresent(feedTag::add);
        }
        tag.put("Feed", feedTag);
        net.minecraft.nbt.ListTag cargoTag = new net.minecraft.nbt.ListTag();
        for (ItemStack stack : cargo) {
            ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).result().ifPresent(cargoTag::add);
        }
        tag.put("Cargo", cargoTag);
        if (target != null) {
            tag.putLong("Target", target.asLong());
        }
        tag.putLong("NextLaunch", nextLaunchTick);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        missile = ItemStack.EMPTY;
        Tag saved = tag.get("Missile");
        if (saved != null) {
            ItemStack.CODEC.parse(NbtOps.INSTANCE, saved).result().ifPresent(stack -> missile = stack);
        }
        target = tag.contains("Target") ? BlockPos.of(tag.getLong("Target")) : null;
        dockedId = tag.hasUUID("DockedMissile") ? tag.getUUID("DockedMissile") : null;
        dockedEntity = null;
        frequency = tag.getInt("Frequency");
        tier = Math.max(1, Math.min(com.shadowclangaming.launcher.LauncherLevel.MAX, tag.getInt("Tier") == 0
                ? 1 : tag.getInt("Tier")));
        trackedEntity = tag.hasUUID("TrackedEntity") ? tag.getUUID("TrackedEntity") : null;
        targetDimension = tag.contains("TargetDimension")
                ? net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION,
                net.minecraft.resources.ResourceLocation.parse(tag.getString("TargetDimension")))
                : null;
        energy.receiveEnergy(tag.getInt("Energy"), false);
        for (int i = 0; i < CARGO_SLOTS; i++) {
            cargo[i] = ItemStack.EMPTY;
        }
        net.minecraft.nbt.ListTag cargoTag = tag.getList("Cargo", Tag.TAG_COMPOUND);
        net.minecraft.nbt.ListTag feedTag = tag.getList("Feed", Tag.TAG_COMPOUND);
        for (int i = 0; i < FEED_SLOTS; i++) {
            feed[i] = ItemStack.EMPTY;
        }
        int feedLoaded = Math.min(feedTag.size(), FEED_SLOTS);
        for (int i = 0; i < feedLoaded; i++) {
            int slot = i;
            ItemStack.CODEC.parse(NbtOps.INSTANCE, feedTag.get(i)).result()
                    .ifPresent(stack -> feed[slot] = stack);
        }
        int loaded = Math.min(cargoTag.size(), CARGO_SLOTS);
        for (int i = 0; i < loaded; i++) {
            int slot = i;
            ItemStack.CODEC.parse(NbtOps.INSTANCE, cargoTag.getCompound(i))
                    .result()
                    .ifPresent(stack -> cargo[slot] = stack);
        }
        nextLaunchTick = tag.getLong("NextLaunch");
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("HasMissile", hasMissile());
        tag.putInt("Frequency", frequency);
        tag.putInt("Tier", tier);
        if (riderId != null) {
            tag.putUUID("Rider", riderId);
        }
        if (trackedEntity != null) {
            tag.putUUID("TrackedEntity", trackedEntity);
        }
        if (targetDimension != null) {
            tag.putString("TargetDimension", targetDimension.location().toString());
        }
        tag.putInt("Energy", energy.getEnergyStored());
        net.minecraft.nbt.ListTag cargoTag = new net.minecraft.nbt.ListTag();
        for (ItemStack stack : cargo) {
            ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).result().ifPresent(cargoTag::add);
        }
        tag.put("Cargo", cargoTag);
        if (target != null) {
            tag.putLong("Target", target.asLong());
        }
        return tag;
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
