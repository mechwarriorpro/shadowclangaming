package com.shadowclangaming.block;

import com.shadowclangaming.blueprint.BlueprintMath;
import com.shadowclangaming.registry.SCGMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The rifle bench's menu: 27 bench slots, the player's inventory, and two dials.
 *
 * The dials are laid over the block entity's fields through {@link ContainerData} rather than being
 * items, so a barrel count cannot be stolen, duplicated or stored in a chest - it is a property of the
 * bench, like a furnace's burn time.
 */
public class BlueprintTableMenu extends AbstractContainerMenu {

    /** Craft the bench into a rifle. */
    public static final int BUTTON_CRAFT = 0;
    /** Barrels: +1, -1, +8, -8, max. */
    public static final int BUTTON_BARRELS = 10;
    /** Strength: +1, -1, +8, -8, max. */
    public static final int BUTTON_STRENGTH = 20;
    private static final int[] STEPS = {1, -1, 8, -8, Integer.MAX_VALUE};

    public static final int DATA_BARRELS = 0;
    public static final int DATA_STRENGTH = 1;
    public static final int DATA_TIER = 2;
    public static final int DATA_EFFICIENCY = 3;
    public static final int DATA_BLOCKS = 4;
    public static final int DATA_VALID = 5;
    private static final int DATA_SIZE = 6;

    private final BlueprintTableBlockEntity table;
    private final Container container;
    private final ContainerData data;

    /** Server side. */
    public BlueprintTableMenu(int containerId, Inventory inventory, BlueprintTableBlockEntity table) {
        super(SCGMenus.BLUEPRINT_TABLE.get(), containerId);
        this.table = table;
        this.container = table;
        this.data = serverData(table);
        layout(inventory);
    }

    /** Client side: a mirror container, because the client's block entity copy must not be edited. */
    public BlueprintTableMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        super(SCGMenus.BLUEPRINT_TABLE.get(), containerId);
        this.table = null;
        this.container = new SimpleContainer(BlueprintTableBlockEntity.SLOTS);
        this.data = new SimpleContainerData(DATA_SIZE);
        layout(inventory);
    }

    private ContainerData serverData(BlueprintTableBlockEntity table) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                BlueprintMath.RifleSpec spec = table.previewSpec();
                return switch (index) {
                    case DATA_BARRELS -> table.barrels();
                    case DATA_STRENGTH -> table.strength();
                    case DATA_TIER -> spec == null ? 0 : spec.tier();
                    case DATA_EFFICIENCY -> spec == null ? 0 : spec.efficiencyPercent();
                    case DATA_BLOCKS -> table.materials().blocks();
                    case DATA_VALID -> spec == null ? 0 : 1;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Read-only: the dials are changed by buttons, which run on the server.
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        };
    }

    private void layout(Inventory inventory) {
        // The bench: 3 x 9.
        for (int row = 0; row < BlueprintTableBlockEntity.ROWS; row++) {
            for (int column = 0; column < BlueprintTableBlockEntity.COLUMNS; column++) {
                int slot = column + row * BlueprintTableBlockEntity.COLUMNS;
                addSlot(new Slot(container, slot, 8 + column * 18, 88 + row * 18));
            }
        }
        // The player's inventory and hotbar.
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 150 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 208));
        }
        addDataSlots(data);
    }

    public ContainerData data() {
        return data;
    }

    public int barrels() {
        return data.get(DATA_BARRELS);
    }

    public int strength() {
        return data.get(DATA_STRENGTH);
    }

    public boolean valid() {
        return data.get(DATA_VALID) == 1;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (table == null) {
            return false;
        }
        if (id == BUTTON_CRAFT) {
            if (player instanceof ServerPlayer serverPlayer) {
                table.craft(serverPlayer);
            }
            broadcastChanges();
            return true;
        }
        for (int i = 0; i < STEPS.length; i++) {
            if (id == BUTTON_BARRELS + i) {
                table.setBarrels(step(table.barrels(), STEPS[i], BlueprintTableBlockEntity.MAX_BARRELS));
                broadcastChanges();
                return true;
            }
            if (id == BUTTON_STRENGTH + i) {
                table.setStrength(step(table.strength(), STEPS[i], BlueprintTableBlockEntity.MAX_STRENGTH));
                broadcastChanges();
                return true;
            }
        }
        return false;
    }

    private static int step(int current, int delta, int max) {
        if (delta == Integer.MAX_VALUE) {
            return max;
        }
        return Math.max(1, Math.min(max, current + delta));
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem()) {
            return result;
        }
        ItemStack stack = slot.getItem();
        result = stack.copy();
        int benchSlots = BlueprintTableBlockEntity.SLOTS;
        if (index < benchSlots) {
            // Bench -> player.
            if (!this.moveItemStackTo(stack, benchSlots, this.slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Player -> bench.
            if (!this.moveItemStackTo(stack, 0, benchSlots, false)) {
                return ItemStack.EMPTY;
            }
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }
}
