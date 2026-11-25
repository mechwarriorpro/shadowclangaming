package com.shadowclangaming.warhead;

import com.shadowclangaming.registry.SCGMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The spatial missile's cell drive.
 *
 * AE2 storage is carried in cells, one item each, and a spatial warhead's payload is exactly that: a
 * handful of spatial cells that get handed to the spatial storage network at impact. Loading them through
 * the generic 27-slot hold was wrong twice over - the grid accepted things a spatial warhead cannot use,
 * and it looked nothing like the mod whose items it was asking for.
 *
 * So this is a drive bay: {@link #BAYS} slots, each one only accepting a spatial storage cell, with the
 * count and the "is AE2 even installed" state reported to the screen. It writes through the same
 * {@code payload} component as every other missile hold, so the launch pad, the tooltip and the warhead
 * all keep reading one list.
 *
 * AE2 itself is optional and is reached reflectively (see {@link Ae2Cells}); with AE2 absent the bays
 * simply refuse everything, which is the honest state for a mod that is not loaded.
 */
public class SpatialCellMenu extends AbstractContainerMenu {

    /** Cell bays. Ten is AE2's own drive: enough to be useful, small enough to read at a glance. */
    public static final int BAYS = 10;
    public static final int COLUMNS = 5;
    public static final int ROWS = BAYS / COLUMNS;

    /** Menu data slot: cells currently loaded. */
    public static final int DATA_LOADED = 0;
    /** Menu data slot: 1 when AE2's spatial storage is available. */
    public static final int DATA_AE2 = 1;
    public static final int DATA_SIZE = 2;

    private final ItemStack missile;
    private final CellBayContainer cells;
    /** Synced values (used count / cells loaded), refreshed by the menu's data slots. */
    private final net.minecraft.world.inventory.ContainerData data;

    public SpatialCellMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, inventory.player.getItemInHand(
                buffer.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND));
    }

    public SpatialCellMenu(int containerId, Inventory inventory, InteractionHand hand) {
        this(containerId, inventory, inventory.player.getItemInHand(hand));
    }

    public SpatialCellMenu(int containerId, Inventory inventory, ItemStack missile) {
        super(SCGMenus.SPATIAL_CELLS.get(), containerId);
        this.missile = missile;
        this.cells = new CellBayContainer(missile, this::broadcastChanges);

        for (int bay = 0; bay < BAYS; bay++) {
            int column = bay % COLUMNS;
            int row = bay / COLUMNS;
            addSlot(new Slot(cells, bay, 26 + column * 26, 26 + row * 26) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    // Only a spatial cell goes in a spatial bay. Anything else is refused rather than
                    // accepted and then silently dropped at impact.
                    return Ae2Cells.isSpatialCell(stack);
                }

                @Override
                public int getMaxStackSize() {
                    // A cell is an item with contents; one per bay, like AE2's own drive.
                    return 1;
                }
            });
        }

        int inventoryTop = 26 + ROWS * 26 + 12;
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
                return switch (index) {
                    case DATA_LOADED -> MissilePayload.count(missile);
                    case DATA_AE2 -> Ae2Cells.spatialAvailable() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Read-only mirror of the missile's own state.
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
        addDataSlots(this.data);
    }

    public ItemStack missile() {
        return missile;
    }

    public int loaded() {
        return data.get(DATA_LOADED);
    }

    public boolean ae2Available() {
        return data.get(DATA_AE2) != 0;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getMainHandItem() == missile || player.getOffhandItem() == missile;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < BAYS) {
            if (!moveItemStackTo(stack, BAYS, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, 0, BAYS, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        cells.setChanged();
    }

    /**
     * The ten bays, as a container over the missile's payload component.
     *
     * Same shape as {@link CargoContainer}: read the component, edit a local array, write back on change,
     * and never persist an empty slot. A cell is never split or merged - one item, one bay.
     */
    public static final class CellBayContainer implements net.minecraft.world.Container {

        private final ItemStack missile;
        private final Runnable onChanged;
        private final java.util.List<ItemStack> bays = new java.util.ArrayList<>(BAYS);

        public CellBayContainer(ItemStack missile, Runnable onChanged) {
            this.missile = missile;
            this.onChanged = onChanged;
            java.util.List<ItemStack> stored = MissilePayload.of(missile);
            for (int i = 0; i < BAYS; i++) {
                bays.add(i < stored.size() ? stored.get(i).copy() : ItemStack.EMPTY);
            }
        }

        @Override
        public int getContainerSize() {
            return BAYS;
        }

        @Override
        public boolean isEmpty() {
            for (ItemStack stack : bays) {
                if (!stack.isEmpty()) {
                    return false;
                }
            }
            return true;
        }

        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < BAYS ? bays.get(slot) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack removeItem(int slot, int amount) {
            ItemStack existing = getItem(slot);
            if (existing.isEmpty() || amount <= 0) {
                return ItemStack.EMPTY;
            }
            ItemStack split = existing.split(amount);
            bays.set(slot, existing.isEmpty() ? ItemStack.EMPTY : existing);
            setChanged();
            return split;
        }

        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            ItemStack existing = getItem(slot);
            bays.set(slot, ItemStack.EMPTY);
            setChanged();
            return existing;
        }

        @Override
        public void setItem(int slot, ItemStack stack) {
            if (slot < 0 || slot >= BAYS) {
                return;
            }
            bays.set(slot, stack == null ? ItemStack.EMPTY : stack);
            setChanged();
        }

        @Override
        public void setChanged() {
            // The component is the storage: write the whole list back, empties dropped.
            java.util.List<ItemStack> out = new java.util.ArrayList<>();
            for (ItemStack stack : bays) {
                if (stack != null && !stack.isEmpty()) {
                    out.add(stack.copy());
                }
            }
            MissilePayload.set(missile, out);
            if (onChanged != null) {
                onChanged.run();
            }
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            for (int i = 0; i < BAYS; i++) {
                bays.set(i, ItemStack.EMPTY);
            }
            setChanged();
        }
    }
}
