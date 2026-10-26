package com.shadowclangaming.block;

import com.shadowclangaming.compat.economy.Money;
import com.shadowclangaming.compat.projecte.ProjectEEmc;
import com.shadowclangaming.magic.ManaApi;
import com.shadowclangaming.compat.economy.EconomyCraftPrices;
import com.shadowclangaming.economy.ExchangeLedger;
import com.shadowclangaming.economy.PowerExchange;
import com.shadowclangaming.item.BankCardItem;
import com.shadowclangaming.registry.SCGBlockEntities;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.UUID;

/**
 * AUTO-TRADER: a machine that turns things into money and money into things.
 *
 *   items pumped in                      -> sold at the shop's sell price, credited to the card owner
 *   100 FE (then 800, 2,700...)          -> 1 currency unit, using the cube curve in PowerExchange
 *   EMC from a directly adjacent storage -> MANA for the card owner. Never money.
 *   redstone signal                      -> buys whatever is in the template slot
 *
 * THE EMC RULE. EMC plumbed straight into this machine is not sold. It refills the owner's mana, and
 * once that pool is full the surplus buys permanent maximum mana instead - "increase max mana if
 * full, refill it if not", exactly. That is why EMC has to arrive by direct adjacency rather than
 * through a Hyperconductor Cable: the cable converts EMC to FE on the way, and FE *is* something this
 * machine sells. Direct EMC = mana, cable EMC = FE = money.
 *
 * THE CARD IS THE ACCOUNT. The block holds one signed Bank Card; every credit and debit goes to that
 * card's owner. Pull the card and the machine goes dead - which is the point: it is a bank account
 * with a pipe on it, and losing the card is losing the account.
 *
 * No GUI, deliberately. Right-click with an item to sell it, sneak + right-click to set the template,
 * right-click with empty hand to collect output and read the status, sneak + right-click empty to
 * pull the card back out.
 */
public class AutoTraderBlockEntity extends BlockEntity {

    public static final int SLOT_SELL = 0;
    public static final int SLOT_CARD = 1;
    public static final int SLOT_TEMPLATE = 2;
    public static final int SLOT_OUTPUT = 3;
    public static final int SLOT_COUNT = 4;

    /** Internal FE buffer. Sized so the first ~21 units can be paid from a full buffer in one go. */
    public static final int ENERGY_CAPACITY = 4_000_000;
    public static final int ENERGY_RECEIVE = 1_000_000;

    /** EMC consumed per point of mana. */
    public static final long EMC_PER_MANA = 4L;

    /** Leftover mana (i.e. mana the pool could not hold) needed per point of permanent capacity. */
    public static final long MANA_PER_CAPACITY = 100L;

    /** Technical rate cap on EMC intake, so one machine cannot drain a condenser dry in a tick. */
    public static final long EMC_PULL_PER_TICK = 8192L;

    /** How often a held redstone signal repeats its purchase. */
    private static final int BUY_REPEAT_TICKS = 20;

    /** Chat spam control: at most one status line every 5 seconds, per machine. */
    private static final int MESSAGE_COOLDOWN_TICKS = 100;

    private final ItemStack[] slots = new ItemStack[SLOT_COUNT];
    private final EnergyStorage energy = new EnergyStorage(ENERGY_CAPACITY, ENERGY_RECEIVE, ENERGY_CAPACITY);

    private final IItemHandler itemHandler = new TraderItemHandler();

    /** Receive-only view of the buffer: nothing may pull power back out of the machine. */
    private final IEnergyStorage energyView = new IEnergyStorage() {
        @Override
        public int receiveEnergy(int toReceive, boolean simulate) {
            return energy.receiveEnergy(toReceive, simulate);
        }

        @Override
        public int extractEnergy(int toExtract, boolean simulate) {
            return 0;
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

    private boolean lastPowered;
    private int buyCooldown;
    private int pushCooldown;
    private long lastMessageTick = -MESSAGE_COOLDOWN_TICKS;

    public AutoTraderBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.AUTO_TRADER.get(), pos, state);
        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = ItemStack.EMPTY;
        }
    }

    public IItemHandler items() {
        return itemHandler;
    }

    public IEnergyStorage energy() {
        return energyView;
    }

    public ItemStack getSlot(int slot) {
        return slots[slot];
    }

    public void setSlot(int slot, ItemStack stack) {
        slots[slot] = stack;
        setChanged();
    }

    // ------------------------------------------------------------------ the tick

    public static void serverTick(Level level, BlockPos pos, BlockState state, AutoTraderBlockEntity trader) {
        if (level instanceof ServerLevel serverLevel) {
            trader.tick(serverLevel, pos);
        }
    }

    private void tick(ServerLevel level, BlockPos pos) {
        MinecraftServer server = level.getServer();

        // 1. Who owns this machine?
        UUID owner = ownerId();
        if (owner == null) {
            lastPowered = false;
            return;
        }

        // 2. Redstone: buy on the rising edge, then repeat while the signal is held high.
        boolean powered = level.hasNeighborSignal(pos);
        boolean rising = powered && !lastPowered;
        lastPowered = powered;

        if (rising) {
            buyCooldown = 1; // buy on this very tick
        } else if (powered) {
            buyCooldown--;
        } else {
            buyCooldown = 0;
        }

        if (powered && buyCooldown <= 0) {
            buy(level, server, owner);
            buyCooldown = BUY_REPEAT_TICKS;
        }

        // 3. Sell whatever an item pipe pushed in.
        sellItems(level, server, owner);

        // 4. Convert FE into money, one unit at a time.
        convertPower(level, server, owner);

        // 4b. EMC in -> mana (and then permanent max mana). Never money.
        convertEmcToMana(level, server, owner);

        // 5. Keep the output slot from silting up.
        if (--pushCooldown <= 0) {
            pushCooldown = 8;
            pushOutput(level, pos);
        }
    }

    // ------------------------------------------------------------------ selling items

    private void sellItems(ServerLevel level, MinecraftServer server, UUID owner) {
        ItemStack sell = slots[SLOT_SELL];
        if (sell.isEmpty()) {
            return;
        }
        long unitPrice = EconomyCraftPrices.sellValueOf(server, sell);
        if (unitPrice <= 0L) {
            return; // unsellable: leave it sitting there so the player can take it back
        }
        long total = unitPrice * (long) sell.getCount();
        if (Money.credit(server, owner, total)) {
            slots[SLOT_SELL] = ItemStack.EMPTY;
            setChanged();
            report(level, Component.translatable("message.shadowclangaming.trader_sold",
                    sell.getCount(), sell.getHoverName(),
                    Money.format(server, total),
                    Money.format(server, Money.balance(server, owner))));
        } else if (Money.available()) {
            // The bank's own balance ceiling. Nothing we can do, so say so once in a while.
            report(level, Component.translatable("message.shadowclangaming.trader_capped"));
        }
    }

    // ------------------------------------------------------------------ power into money

    private void convertPower(ServerLevel level, MinecraftServer server, UUID owner) {
        ExchangeLedger ledger = ExchangeLedger.get(level);
        long unitsSoFar = ledger.unitsCredited(owner);
        long need = PowerExchange.feForNextUnit(unitsSoFar);

        if (need > Integer.MAX_VALUE || need > energy.getEnergyStored()) {
            return; // not enough power buffered for the next unit yet
        }
        int toDraw = (int) need;
        if (energy.extractEnergy(toDraw, true) < toDraw) {
            return;
        }

        energy.extractEnergy(toDraw, false);
        if (Money.credit(server, owner, 1L)) {
            ledger.addUnits(owner, 1L);
            setChanged();
            if (unitsSoFar % 10 == 0) {
                report(level, Component.translatable("message.shadowclangaming.trader_power",
                        Money.format(server, 1L),
                        String.format("%,d", PowerExchange.feForNextUnit(unitsSoFar + 1L))));
            }
        } else {
            // Give the power back rather than eating it when the transaction failed.
            energy.receiveEnergy(toDraw, false);
        }
    }

    // ------------------------------------------------------------------ EMC into mana

    /**
     * "Instead of selling EMC, use it for mana: refill the pool if it is not full, otherwise raise
     * the maximum."
     *
     * Ordering matters and is deliberate: the mana pool absorbs first, and only the bits it physically
     * cannot hold get spent on capacity. A player sitting at full mana therefore converts an EMC feed
     * straight into growth, which is the only path in the mod that raises max mana without casting.
     */
    private void convertEmcToMana(ServerLevel level, MinecraftServer server, UUID owner) {
        if (!ProjectEEmc.isAvailable()) {
            return;
        }
        // Only pull whole units' worth, so we never eat EMC we cannot convert.
        long budget = (EMC_PULL_PER_TICK / EMC_PER_MANA) * EMC_PER_MANA;
        long gained = 0L;

        for (Direction direction : Direction.values()) {
            if (budget < EMC_PER_MANA) {
                break;
            }
            BlockEntity neighbour = level.getBlockEntity(worldPosition.relative(direction));
            if (!ProjectEEmc.isEmcStorage(neighbour)) {
                continue;
            }
            long moved = ProjectEEmc.extractEmc(neighbour, budget, direction.getOpposite());
            if (moved > 0L) {
                gained += (moved / EMC_PER_MANA) * EMC_PER_MANA;
                budget -= moved;
            }
        }
        if (gained < EMC_PER_MANA) {
            return;
        }

        // A player carrying the Holy Necklace regenerates nothing, and that includes machine funding.
        var onlineOwner = server.getPlayerList().getPlayer(owner);
        if (onlineOwner != null && com.shadowclangaming.magic.CreepingEvil.carriesNecklace(onlineOwner)) {
            return;
        }

        long manaUnits = gained / EMC_PER_MANA;
        long leftover = ManaApi.addManaFor(server, owner, manaUnits);
        long capacityGained = 0L;
        if (leftover > 0L) {
            capacityGained = leftover / MANA_PER_CAPACITY;
            if (capacityGained > 0L) {
                ManaApi.addCapacity(server, owner, capacityGained);
            }
        }

        Component message = capacityGained > 0L
                ? Component.translatable("message.shadowclangaming.trader_mana_capacity_gain",
                String.format("%,d", manaUnits),
                String.format("%,d", ManaApi.currentFor(server, owner)),
                String.format("%,d", ManaApi.capacityFor(server, owner)),
                String.format("%,d", capacityGained))
                : Component.translatable("message.shadowclangaming.trader_mana",
                String.format("%,d", manaUnits),
                String.format("%,d", ManaApi.currentFor(server, owner)),
                String.format("%,d", ManaApi.capacityFor(server, owner)));
        report(level, message);
        setChanged();
    }

    // ------------------------------------------------------------------ money into items

    private void buy(ServerLevel level, MinecraftServer server, UUID owner) {
        ItemStack template = slots[SLOT_TEMPLATE];
        if (template.isEmpty()) {
            report(level, Component.translatable("message.shadowclangaming.trader_no_template"));
            return;
        }
        long price = EconomyCraftPrices.buyValueOf(server, template);
        if (price <= 0L) {
            report(level, Component.translatable("message.shadowclangaming.trader_no_price"));
            return;
        }
        ItemStack purchase = template.copyWithCount(1);
        if (!canAcceptOutput(purchase)) {
            report(level, Component.translatable("message.shadowclangaming.trader_output_full"));
            return;
        }

        if (!Money.spend(server, owner, price)) {
            report(level, Money.available()
                    ? Component.translatable("message.shadowclangaming.trader_no_funds",
                    Money.format(server, price))
                    : Component.translatable("message.shadowclangaming.card_no_api"));
            return;
        }

        insertOutput(purchase);
        setChanged();
        report(level, Component.translatable("message.shadowclangaming.trader_bought",
                purchase.getHoverName(), Money.format(server, price),
                Money.format(server, Money.balance(server, owner))));
    }

    private boolean canAcceptOutput(ItemStack stack) {
        ItemStack out = slots[SLOT_OUTPUT];
        if (out.isEmpty()) {
            return true;
        }
        if (!ItemStack.isSameItemSameComponents(out, stack)) {
            return false;
        }
        return out.getCount() + stack.getCount() <= out.getMaxStackSize();
    }

    private void insertOutput(ItemStack stack) {
        ItemStack out = slots[SLOT_OUTPUT];
        if (out.isEmpty()) {
            slots[SLOT_OUTPUT] = stack;
        } else {
            out.grow(stack.getCount());
        }
    }

    /** Gently shoves finished purchases into neighbouring inventories so silos do not jam. */
    private void pushOutput(ServerLevel level, BlockPos pos) {
        ItemStack out = slots[SLOT_OUTPUT];
        if (out.isEmpty()) {
            return;
        }
        for (Direction direction : Direction.values()) {
            IItemHandler neighbour = level.getCapability(Capabilities.ItemHandler.BLOCK,
                    pos.relative(direction), direction.getOpposite());
            if (neighbour == null) {
                continue;
            }
            ItemStack moved = out.copyWithCount(1);
            for (int slot = 0; slot < neighbour.getSlots(); slot++) {
                ItemStack remaining = neighbour.insertItem(slot, moved, false);
                if (remaining.getCount() != moved.getCount()) {
                    out.shrink(moved.getCount() - remaining.getCount());
                    if (out.isEmpty()) {
                        slots[SLOT_OUTPUT] = ItemStack.EMPTY;
                    }
                    setChanged();
                    return;
                }
            }
        }
    }

    // ------------------------------------------------------------------ ownership / status

    /** @return the UUID of the signed Bank Card in the card slot, or null if there is none. */
    public UUID ownerId() {
        if (!(slots[SLOT_CARD].getItem() instanceof BankCardItem)) {
            return null;
        }
        String raw = slots[SLOT_CARD].get(SCGDataComponents.CARD_OWNER.get());
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String ownerName() {
        UUID owner = ownerId();
        return owner == null ? "" : owner.toString();
    }

    private void report(ServerLevel level, Component message) {
        long now = level.getGameTime();
        if (now - lastMessageTick < MESSAGE_COOLDOWN_TICKS) {
            return;
        }
        lastMessageTick = now;
        // The machine has no screen, so it talks to whoever is nearby - that is the whole UI.
        for (var player : level.players()) {
            if (player.blockPosition().distSqr(worldPosition) <= 64.0D) {
                player.displayClientMessage(message, true);
            }
        }
    }

    // ------------------------------------------------------------------ item handler

    private final class TraderItemHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return SLOT_COUNT;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < SLOT_COUNT ? slots[slot] : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot < 0 || slot >= SLOT_COUNT) {
                return stack;
            }
            if (slot == SLOT_OUTPUT) {
                return stack; // output is for the machine and for extraction only
            }
            if (slot == SLOT_CARD && !(stack.getItem() instanceof BankCardItem)) {
                return stack;
            }
            ItemStack existing = slots[slot];
            int limit = slot == SLOT_TEMPLATE ? 1 : Math.min(stack.getMaxStackSize(), 64);

            if (existing.isEmpty()) {
                int moved = Math.min(stack.getCount(), limit);
                if (!simulate) {
                    slots[slot] = stack.copyWithCount(moved);
                    setChanged();
                }
                return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
            }
            if (!ItemStack.isSameItemSameComponents(existing, stack)) {
                return stack;
            }
            int room = limit - existing.getCount();
            if (room <= 0) {
                return stack;
            }
            int moved = Math.min(room, stack.getCount());
            if (!simulate) {
                existing.grow(moved);
                setChanged();
            }
            return moved >= stack.getCount() ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - moved);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (amount <= 0 || slot != SLOT_OUTPUT && slot != SLOT_CARD) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = slots[slot];
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int taken = Math.min(amount, existing.getCount());
            ItemStack result = existing.copyWithCount(taken);
            if (!simulate) {
                existing.shrink(taken);
                if (existing.isEmpty()) {
                    slots[slot] = ItemStack.EMPTY;
                }
                setChanged();
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot == SLOT_TEMPLATE ? 1 : 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            if (slot == SLOT_OUTPUT) {
                return false;
            }
            return slot != SLOT_CARD || stack.getItem() instanceof BankCardItem;
        }
    }

    // ------------------------------------------------------------------ saving

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        ListTag list = new ListTag();
        for (ItemStack stack : slots) {
            list.add(stack.isEmpty()
                    ? new CompoundTag()
                    : ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).getOrThrow());
        }
        tag.put("Slots", list);
        tag.putInt("Energy", energy.getEnergyStored());
        tag.putBoolean("Powered", lastPowered);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        for (int i = 0; i < SLOT_COUNT; i++) {
            slots[i] = ItemStack.EMPTY;
        }
        ListTag list = tag.getList("Slots", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && i < SLOT_COUNT; i++) {
            if (list.getCompound(i).isEmpty()) {
                continue;
            }
            int slot = i;
            ItemStack.CODEC.parse(NbtOps.INSTANCE, list.get(i))
                    .result()
                    .ifPresent(stack -> slots[slot] = stack);
        }

        // Refill through receiveEnergy so the value can never exceed the capacity.
        int stored = tag.getInt("Energy");
        while (stored > 0) {
            int accepted = energy.receiveEnergy(stored, false);
            if (accepted <= 0) {
                break;
            }
            stored -= accepted;
        }
        lastPowered = tag.getBoolean("Powered");
    }

    /** Called on break: contents must not vanish. */
    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack);
            }
        }
    }
}
