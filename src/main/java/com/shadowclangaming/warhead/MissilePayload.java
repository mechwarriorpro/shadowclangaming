package com.shadowclangaming.warhead;

import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.ArrayList;
import java.util.List;

/**
 * What is inside a missile, and how it gets there.
 *
 * THE PLAYER'S RULE
 *   "items loaded via right-clicking missile, same with cargo missile, then it can be loaded into the
 *    launcher and fired"
 *
 * So the missile item is its own container. Right-click with a stack in hand and it goes in; right-click
 * with an empty hand and it reports what it is carrying; sneak + right-click empties it back out.
 *
 * WHY A COMPONENT AND NOT A MENU. A missile is a small object that travels through inventories, chests
 * and eventually the launcher's slot. A block-entity-backed inventory would lose its contents the first
 * time it was picked up. A data component travels with the item by definition, survives a save, and can
 * be read by the pad without any extra plumbing.
 *
 * AE2 CELLS ARE KEPT WHOLE. The user's rule is "load in an AE2 cell for items, the cell will be dropped
 * instead of items". A cell is stored like any other stack, but the warheads that unpack payloads ask
 * {@link Ae2Cells#containsCell} first and hand the cell over intact rather than reading its contents.
 */
public final class MissilePayload {

    /** 27 stacks: a single chest's worth, and about as much as a warhead should hold. */
    public static final int MAX_STACKS = 27;

    private MissilePayload() {
    }

    /** Everything loaded into this missile, in load order. Never null. */
    public static List<ItemStack> of(ItemStack missile) {
        List<ItemStack> payload = missile.get(SCGDataComponents.PAYLOAD.get());
        return payload == null ? List.of() : payload;
    }

    /**
     * Replace the whole payload.
     *
     * Used by the spatial missile's cell drive, which owns a fixed bay grid and writes the list back
     * wholesale. Empties are dropped here rather than at each call site, so the component can never hold
     * an entry that decodes as air - the bug the cargo GUI was built to kill.
     */
    public static void set(ItemStack missile, List<ItemStack> stacks) {
        List<ItemStack> clean = new ArrayList<>(MAX_STACKS);
        for (ItemStack stack : stacks) {
            if (stack != null && !stack.isEmpty() && clean.size() < MAX_STACKS) {
                clean.add(stack.copy());
            }
        }
        if (clean.isEmpty()) {
            missile.remove(SCGDataComponents.PAYLOAD.get());
        } else {
            missile.set(SCGDataComponents.PAYLOAD.get(), List.copyOf(clean));
        }
    }

    /** How many stacks are loaded. */
    public static int count(ItemStack missile) {
        return of(missile).size();
    }

    /** The total number of ITEMS loaded - what a constructor warhead measures its sphere in. */
    public static long blockCount(ItemStack missile) {
        long total = 0L;
        for (ItemStack stack : of(missile)) {
            if (!stack.isEmpty() && stack.getItem() instanceof net.minecraft.world.item.BlockItem) {
                total += stack.getCount();
            }
        }
        return total;
    }

    /** Is there an AE2 cell in there? */
    public static boolean holdsCell(ItemStack missile) {
        return Ae2Cells.containsCell(of(missile));
    }

    /**
     * Load one stack into the missile.
     *
     * @return how many items were actually moved (0 when the payload is full)
     */
    public static int load(ItemStack missile, ItemStack incoming) {
        if (incoming.isEmpty()) {
            return 0;
        }
        List<ItemStack> payload = new ArrayList<>(of(missile));
        // Top up an identical stack first, so a bag of cobble does not fill the hold with 64s.
        for (ItemStack existing : payload) {
            if (ItemStack.isSameItemSameComponents(existing, incoming)
                    && existing.getCount() < existing.getMaxStackSize()) {
                int room = existing.getMaxStackSize() - existing.getCount();
                int moved = Math.min(room, incoming.getCount());
                existing.grow(moved);
                incoming.shrink(moved);
                if (!incoming.isEmpty()) {
                    continue;
                }
                missile.set(SCGDataComponents.PAYLOAD.get(), List.copyOf(payload));
                return moved;
            }
        }
        if (payload.size() >= MAX_STACKS || incoming.isEmpty()) {
            missile.set(SCGDataComponents.PAYLOAD.get(), List.copyOf(payload));
            return 0;
        }
        int moved = incoming.getCount();
        payload.add(incoming.copy());
        incoming.setCount(0);
        missile.set(SCGDataComponents.PAYLOAD.get(), List.copyOf(payload));
        return moved;
    }

    /** Empty the missile and return what was inside. */
    public static List<ItemStack> clear(ItemStack missile) {
        List<ItemStack> payload = new ArrayList<>(of(missile));
        missile.remove(SCGDataComponents.PAYLOAD.get());
        return payload;
    }

    /** The radius slider value, or -1 when unset. */
    public static int radiusOf(ItemStack missile) {
        Integer radius = missile.get(SCGDataComponents.RADIUS.get());
        return radius == null ? -1 : radius;
    }

    public static void setRadius(ItemStack missile, int radius) {
        missile.set(SCGDataComponents.RADIUS.get(), Math.max(-1, radius));
    }

    /**
     * The altitude a cluster warhead should break at, or {@link #NO_SPLIT_ALTITUDE} when unset (the frame
     * then splits on its own proximity rule).
     */
    public static final int NO_SPLIT_ALTITUDE = SplitRule.AUTO;

    public static int splitAltitudeOf(ItemStack missile) {
        Integer altitude = missile.get(SCGDataComponents.SPLIT_ALTITUDE.get());
        return altitude == null ? NO_SPLIT_ALTITUDE : altitude;
    }

    public static void setSplitAltitude(ItemStack missile, int altitude) {
        if (altitude == NO_SPLIT_ALTITUDE) {
            missile.remove(SCGDataComponents.SPLIT_ALTITUDE.get());
        } else {
            missile.set(SCGDataComponents.SPLIT_ALTITUDE.get(), altitude);
        }
    }

    /**
     * Show the payload in the tooltip, and make the missile refuse to stack once loaded.
     *
     * The stack-size rule matters: two "identical" missiles with different payloads must never merge,
     * and the component comparison already handles that, but a singular count makes the difference
     * visible in the inventory.
     */
    public static void applyStacking(ItemStack missile) {
        if (!of(missile).isEmpty()) {
            missile.set(DataComponents.MAX_STACK_SIZE, 1);
        }
    }

    /** Read a legacy ItemContainerContents payload, if an old save has one. */
    public static List<ItemStack> fromContainer(ItemStack missile) {
        ItemContainerContents contents = missile.get(DataComponents.CONTAINER);
        if (contents == null) {
            return List.of();
        }
        List<ItemStack> out = new ArrayList<>();
        contents.nonEmptyItems().forEach(out::add);
        return out;
    }
}
