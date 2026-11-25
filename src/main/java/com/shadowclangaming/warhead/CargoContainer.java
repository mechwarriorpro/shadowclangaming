package com.shadowclangaming.warhead;

import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/**
 * The cargo missile's hold, as a {@link Container} so a chest menu can drive it.
 *
 * WHY THIS REPLACED OFFHAND LOADING. The old path was "right-click the missile with a stack in the other
 * hand". It had two problems:
 *
 *   1. The offhand is a terrible place to put a shopping basket, and you cannot see what is loaded.
 *   2. It produced a real bug: emptying the incoming stack (setCount(0)) and then writing the list back
 *      through the component codec could leave entries that decode as EMPTY, which the menu then showed
 *      as "air". With 27 slots and per-slot writes, an empty slot is simply not written at all, so the
 *      failure mode cannot exist.
 *
 * This container is a thin view: it reads the component, edits a local array, and writes it back on
 * change. Slots that are empty are dropped before the write, which is what keeps the component small and
 * free of phantom entries.
 */
public class CargoContainer implements Container {

    public static final int SIZE = 27;

    private final ItemStack missile;
    private final Runnable onChanged;
    private final List<ItemStack> slots = new ArrayList<>(SIZE);

    public CargoContainer(ItemStack missile, Runnable onChanged) {
        this.missile = missile;
        this.onChanged = onChanged;
        List<ItemStack> stored = MissilePayload.of(missile);
        for (int i = 0; i < SIZE; i++) {
            slots.add(i < stored.size() ? stored.get(i).copy() : ItemStack.EMPTY);
        }
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        return slot >= 0 && slot < SIZE ? slots.get(slot) : ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= SIZE || amount <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = slots.get(slot);
        if (existing.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack taken = existing.split(amount);
        if (existing.isEmpty()) {
            slots.set(slot, ItemStack.EMPTY);
        }
        setChanged();
        return taken;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SIZE) {
            return ItemStack.EMPTY;
        }
        ItemStack existing = slots.get(slot);
        slots.set(slot, ItemStack.EMPTY);
        return existing;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SIZE) {
            return;
        }
        slots.set(slot, stack);
        setChanged();
    }

    @Override
    public int getMaxStackSize() {
        // A cargo hold accepts full stacks: it is a chest, not a demonstration.
        return 64;
    }

    @Override
    public void setChanged() {
        // Write back without any empty or zero-count entries. This is the "inserted items became air"
        // fix: only real stacks are persisted, so nothing can decode as EMPTY later.
        List<ItemStack> payload = new ArrayList<>(SIZE);
        for (ItemStack stack : slots) {
            if (stack != null && !stack.isEmpty() && stack.getCount() > 0) {
                payload.add(stack.copy());
            }
        }
        if (payload.isEmpty()) {
            missile.remove(SCGDataComponents.PAYLOAD.get());
        } else {
            missile.set(SCGDataComponents.PAYLOAD.get(), List.copyOf(payload));
        }
        if (onChanged != null) {
            onChanged.run();
        }
    }

    @Override
    public boolean stillValid(Player player) {
        // The missile is in the player's own hand; if it is gone, the menu must close.
        return player.getMainHandItem() == missile || player.getOffhandItem() == missile;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < SIZE; i++) {
            slots.set(i, ItemStack.EMPTY);
        }
        setChanged();
    }

    /** Everything currently in the hold, for the warhead at launch time. */
    public List<ItemStack> contents() {
        List<ItemStack> out = new ArrayList<>(SIZE);
        for (ItemStack stack : slots) {
            if (!stack.isEmpty()) {
                out.add(stack.copy());
            }
        }
        return out;
    }
}
