package com.shadowclangaming.warhead;

import com.shadowclangaming.registry.SCGMenus;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The cargo missile's hold, as a chest GUI.
 *
 * "I don't like the cargo loading system — instead of offhand loading, can we make it so you open a
 * chest GUI and insert it."
 *
 * So this is a plain 27-slot container in the middle of a chest layout: the missile stays in your hand,
 * the grid is its hold, and your inventory is underneath. Shift-clicking works in both directions, so
 * filling a cargo missile is the same muscle memory as filling a chest.
 *
 * The menu does NOT own the items. It reads and writes the missile item's {@code payload} component
 * through {@link CargoContainer}, which means the hold survives being dropped, chested, or loaded into a
 * launcher, and a menu that closes badly cannot duplicate anything.
 */
public class CargoMissileMenu extends AbstractContainerMenu {

    /** How the server resolved the missile. Clients only ever need the hand. */
    public static final int ROWS = 3;
    public static final int COLUMNS = 9;

    /** Menu data slot: the constructor radius override, or -1 for "derive it from the payload". */
    public static final int DATA_RADIUS = 0;
    /** Menu data slot: 1 when this missile is a constructor, so the screen knows to draw a slider. */
    public static final int DATA_IS_CONSTRUCTOR = 1;
    public static final int DATA_SIZE = 2;

    /** Largest radius the slider may be set to. Matches the launch pad's own slider. */
    public static final int MAX_RADIUS = 4096;

    private final Inventory playerInventory;
    private final ItemStack missile;
    private final boolean constructor;
    /**
     * The radius as the CLIENT last heard it.
     *
     * This is the bug that made the constructor slider look dead: {@link #radius()} read the missile
     * ItemStack, which on a client is a different object from the one the server writes to, and the item
     * in your hand is not a menu slot - so nothing ever synced it back. The data slot is the sync path;
     * this field is its local copy on both sides.
     */
    private int cachedRadius;
    @Nullable
    private final CargoContainer cargo;

    /** Client-side constructor: an empty local container, replaced by the synced slots. */
    public CargoMissileMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, inventory.player.getItemInHand(readHand(buffer)));
    }

    /** Server-side constructor: the real missile, from the hand the player used. */
    public CargoMissileMenu(int containerId, Inventory inventory, InteractionHand hand) {
        this(containerId, inventory, inventory.player.getItemInHand(hand));
    }

    public CargoMissileMenu(int containerId, Inventory inventory, ItemStack missile) {
        super(SCGMenus.CARGO_MISSILE.get(), containerId);
        this.playerInventory = inventory;
        this.missile = missile;
        this.constructor = warheadOf(missile) == WarheadType.CONSTRUCTOR;
        this.cachedRadius = MissilePayload.radiusOf(missile);
        boolean holds = warheadOf(missile) == WarheadType.CARGO || constructor;
        this.cargo = holds ? new CargoContainer(missile, this::broadcastChanges) : null;

        if (this.cargo != null) {
            for (int row = 0; row < ROWS; row++) {
                for (int column = 0; column < COLUMNS; column++) {
                    int index = column + row * COLUMNS;
                    addSlot(new Slot(this.cargo, index,
                            8 + column * 18, 18 + row * 18) {
                        @Override
                        public boolean mayPlace(ItemStack stack) {
                            // A cargo missile does not carry other missiles; everything else fits.
                            if (stack.getItem() instanceof com.shadowclangaming.item.MissileItem) {
                                return false;
                            }
                            if (!constructor) {
                                return true;
                            }
                            // A constructor can only build with blocks (or hand an AE2 cell over
                            // whole), so the grid refuses sand, arrows and mob eggs instead of
                            // letting the player fill 27 slots with things the placer will skip.
                            return stack.getItem() instanceof net.minecraft.world.item.BlockItem
                                    || com.shadowclangaming.warhead.Ae2Cells.isItemCell(stack);
                        }
                    });
                }
            }
        } else {
            // Not a cargo missile (someone opened it by other means): an inert grid keeps the layout
            // valid without letting anything be created or destroyed.
            SimpleContainer empty = new SimpleContainer(ROWS * COLUMNS);
            for (int index = 0; index < ROWS * COLUMNS; index++) {
                addSlot(new Slot(empty, index, 8 + (index % COLUMNS) * 18, 18 + (index / COLUMNS) * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return false;
                    }
                });
            }
        }

        // Two ints the screen reads: the radius override and whether to show a slider at all.
        addDataSlots(new net.minecraft.world.inventory.ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_RADIUS -> cachedRadius;
                    case DATA_IS_CONSTRUCTOR -> constructor ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {
                // Reached on the client when the server's value arrives.
                if (index == DATA_RADIUS) {
                    cachedRadius = value;
                }
            }

            @Override
            public int getCount() {
                return DATA_SIZE;
            }
        });

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
    }

    private static InteractionHand readHand(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
    }

    /** The missile whose hold this is. */
    public ItemStack missile() {
        return missile;
    }

    /** True when this hold belongs to a constructor missile, which also gets a radius slider. */
    public boolean isConstructor() {
        return constructor;
    }

    /** The radius override the missile is carrying, or -1 for "derive it from the payload". */
    public int radius() {
        return cachedRadius;
    }

    /** Server-side: write a new radius override onto the missile and re-sync it to the screen. */
    public void setRadius(int radius) {
        cachedRadius = Math.max(-1, Math.min(MAX_RADIUS, radius));
        MissilePayload.setRadius(missile, cachedRadius);
        broadcastChanges();
    }

    /** The warhead a stack carries, counting a fitted module's component. */
    public static WarheadType warheadOf(ItemStack stack) {
        if (stack.getItem()
                instanceof com.shadowclangaming.item.MissileModuleItem) {
            return com.shadowclangaming.item.MissileModuleItem.warheadOf(stack);
        }
        return stack.getItem() instanceof com.shadowclangaming.item.MissileItem item
                ? item.warhead() : WarheadType.HE;
    }

    /** How many of the 27 slots are in use, for the title line. */
    public int usedSlots() {
        return cargo == null ? 0 : MissilePayload.count(missile);
    }

    /**
     * The radius slider's wire.
     *
     * A slider is a stream of clicks, and every one of them would be a packet if it had its own payload.
     * Vanilla already has the right channel for "a number, about this menu": the container button click.
     * So the button id IS the radius, and -1 (or anything non-positive) clears the override back to
     * "derive it from the payload".
     *
     * The id is checked against the menu's own missile rather than trusted: a client can send any int.
     */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!constructor || !stillValid(player)) {
            return false;
        }
        setRadius(id <= 0 ? -1 : id);
        return true;
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

        int holdSlots = ROWS * COLUMNS;
        if (index < holdSlots) {
            // Hold -> inventory.
            if (!moveItemStackTo(stack, holdSlots, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else {
            // Inventory -> hold, but not if the hold is already full of that kind of thing.
            if (!moveItemStackTo(stack, 0, holdSlots, false)) {
                return ItemStack.EMPTY;
            }
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
        if (cargo != null) {
            // Final flush: the component is already written per slot, but this guarantees the state on
            // close even if the last edit arrived in the same tick as the close packet.
            cargo.setChanged();
        }
    }

    public Inventory playerInventory() {
        return playerInventory;
    }
}
