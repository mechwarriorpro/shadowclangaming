package com.shadowclangaming.block;

import com.shadowclangaming.registry.SCGMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The silo's own 27-slot hold, as a chest GUI.
 *
 * WHY THIS EXISTS. The launch pad has always had a real cargo inventory - hoppers, AE2 import buses and
 * the {@code cargo} console command could all put things in it - but the launch console itself has no
 * slots, so a player standing at the silo had no way to *see* or *touch* that inventory. "Load the silo"
 * was a console command, which is a bad answer for a container.
 *
 * Opened with shift + right-click on the pad (empty hand). The layout is the same 3x9 chest as the cargo
 * missile, down to the slot coordinates, because it is the same idea: a hold you fill with things the
 * warhead will spend.
 *
 * The menu is a thin view over the block entity - every write goes straight through
 * {@link LaunchPadBlockEntity#setCargo} and calls {@code setChanged()}, so the pad's saved data stays
 * correct even if the player closes the window by walking away.
 */
public class SiloHoldMenu extends AbstractContainerMenu {

    public static final int ROWS = 3;
    public static final int COLUMNS = 9;
    public static final int SIZE = ROWS * COLUMNS;

    /** Menu data slot: how many of the 27 slots are occupied. */
    public static final int DATA_USED = 0;
    public static final int DATA_SIZE = 1;

    private final Inventory playerInventory;
    @Nullable
    private final LaunchPadBlockEntity pad;
    private final BlockPos pos;
    /** Synced values (used count / cells loaded), refreshed by the menu's data slots. */
    private final net.minecraft.world.inventory.ContainerData data;

    /** Client-side constructor: the block entity is resolved from the synced position. */
    public SiloHoldMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, buffer.readBlockPos());
    }

    private SiloHoldMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory,
                inventory.player.level().getBlockEntity(pos) instanceof LaunchPadBlockEntity pad
                        ? pad : null,
                pos);
    }

    public SiloHoldMenu(int containerId, Inventory inventory, LaunchPadBlockEntity pad, BlockPos pos) {
        super(SCGMenus.SILO_HOLD.get(), containerId);
        this.playerInventory = inventory;
        this.pad = pad;
        this.pos = pos;

        if (pad != null) {
            addSlots(new PadHold(pad));
        } else {
            // The pad went away between the click and the open: an inert grid keeps the layout valid
            // without letting a player create or destroy anything.
            addSlots(new SimpleContainer(SIZE));
        }

        int inventoryTop = 18 + ROWS * 18 + 14;
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, inventoryTop + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, inventoryTop + 58));
        }

        this.data = new net.minecraft.world.inventory.ContainerData() {
            @Override
            public int get(int index) {
                return index == DATA_USED ? (pad == null ? 0 : pad.cargoSlotsUsed()) : 0;
            }

            @Override
            public void set(int index, int value) {
                // Read-only: the count comes from the pad, nothing writes it from the client.
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
        addDataSlots(this.data);
    }

    private void addSlots(Container container) {
        for (int row = 0; row < ROWS; row++) {
            for (int column = 0; column < COLUMNS; column++) {
                int index = column + row * COLUMNS;
                addSlot(new Slot(container, index, 8 + column * 18, 18 + row * 18));
            }
        }
    }

    /** How many of the 27 slots are occupied, for the title line. */
    public int usedSlots() {
        return data.get(DATA_USED);
    }

    public BlockPos pos() {
        return pos;
    }

    public Inventory playerInventory() {
        return playerInventory;
    }

    /** Standing near the silo, with the silo still there. */
    public static final double REACH = 8.0D;

    @Override
    public boolean stillValid(Player player) {
        return pad != null && !pad.isRemoved()
                && player.blockPosition().distSqr(pos) <= REACH * REACH;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < SIZE) {
            if (!moveItemStackTo(stack, SIZE, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, SIZE, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    /**
     * A {@link Container} view over the pad's cargo array.
     *
     * Deliberately not a copy: the pad is the single source of truth, so a hopper filling the silo while
     * the window is open and a player filling it through the window are the same write.
     */
    private static final class PadHold implements Container {
        private final LaunchPadBlockEntity pad;

        PadHold(LaunchPadBlockEntity pad) {
            this.pad = pad;
        }

        @Override
        public int getContainerSize() {
            return SIZE;
        }

        @Override
        public boolean isEmpty() {
            return pad.cargoSlotsUsed() == 0;
        }

        @Override
        public ItemStack getItem(int slot) {
            return pad.getCargo(slot);
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack existing = pad.getCargo(slot);
            if (existing.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack split = existing.split(amount);
            pad.setCargo(slot, existing.isEmpty() ? ItemStack.EMPTY : existing);
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack existing = pad.getCargo(slot);
            pad.setCargo(slot, ItemStack.EMPTY);
            return existing;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            // Slots are a view; write straight through, empties included.
            pad.setCargo(slot, stack);
        }

        @Override
        public void setChanged() {
            pad.setChanged();
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            pad.clearCargo();
        }
    }
}
