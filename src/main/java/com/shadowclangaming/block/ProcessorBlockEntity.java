package com.shadowclangaming.block;

import com.shadowclangaming.chem.DecompositionTable;
import com.shadowclangaming.chem.Element;
import com.shadowclangaming.chem.SynthesisTable;
import com.shadowclangaming.item.ElementVialItem;
import com.shadowclangaming.registry.SCGBlockEntities;
import com.shadowclangaming.registry.SCGDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The block entity behind both chemistry machines.
 *
 * State machine (deliberately simple, easy to extend):
 *   - idle: pendingOutputs is empty
 *   - working: pendingOutputs holds what will be produced; progress counts up to totalTicks
 *   - blocked: progress reached totalTicks but the output slots are full - it waits
 *
 * Inserting:
 *   DECOMPOSER  - right-click an item that is in {@link DecompositionTable}
 *   SYNTHESIZER - right-click element vials; they go into a buffer, and the first
 *                 {@link SynthesisTable} recipe the buffer satisfies starts crafting
 */
public class ProcessorBlockEntity extends BlockEntity {
    private static final int OUTPUT_SLOTS = 6;
    private static final int DECOMPOSE_TICKS = 60; // 3 seconds

    private final ItemStack[] outputs = new ItemStack[OUTPUT_SLOTS];
    private final Map<Element, Integer> buffer = new EnumMap<>(Element.class);
    private final List<ItemStack> pendingOutputs = new ArrayList<>();
    private int progress;
    private int totalTicks;
    private int empTicks;

    public ProcessorBlockEntity(BlockPos pos, BlockState state) {
        super(SCGBlockEntities.PROCESSOR.get(), pos, state);
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            outputs[i] = ItemStack.EMPTY;
        }
    }

    public ProcessorKind kind() {
        if (getBlockState().getBlock() instanceof SynthesizerBlock) {
            return ProcessorKind.SYNTHESIZER;
        }
        return ProcessorKind.DECOMPOSER;
    }

    // ------------------------------------------------------------------ interaction

    /** @return true if the stack was accepted (caller then shrinks it). */
    public boolean insert(ItemStack stack, Player player) {
        if (!pendingOutputs.isEmpty()) {
            return false; // still working
        }
        if (kind() == ProcessorKind.DECOMPOSER) {
            if (!DecompositionTable.isDecomposable(stack)) {
                return false;
            }
            pendingOutputs.addAll(DecompositionTable.decompose(stack, level.getRandom()));
            totalTicks = DECOMPOSE_TICKS;
            progress = 0;
        } else {
            Element element = stack.get(SCGDataComponents.ELEMENT.get());
            if (element == null || !(stack.getItem() instanceof ElementVialItem)) {
                return false;
            }
            buffer.merge(element, 1, Integer::sum);

            SynthesisTable.Synthesis match = SynthesisTable.findMatch(buffer);
            if (match != null) {
                for (Map.Entry<Element, Integer> requirement : match.inputs().entrySet()) {
                    buffer.merge(requirement.getKey(), -requirement.getValue(), Integer::sum);
                }
                pendingOutputs.add(match.result().copy());
                totalTicks = match.ticks();
                progress = 0;
            }
        }
        setChanged();
        return true;
    }

    /** @return true if the player got something. */
    public boolean collect(Player player) {
        boolean any = false;
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            ItemStack stack = outputs[i];
            if (!stack.isEmpty()) {
                any = true;
                if (!player.getInventory().add(stack)) {
                    player.drop(stack, false);
                }
                outputs[i] = ItemStack.EMPTY;
            }
        }
        // Vials that never found a recipe can be taken back out too.
        for (Map.Entry<Element, Integer> entry : new ArrayList<>(buffer.entrySet())) {
            int count = entry.getValue();
            if (count <= 0) {
                continue;
            }
            any = true;
            ItemStack vials = ElementVialItem.create(entry.getKey(), count);
            if (!player.getInventory().add(vials)) {
                player.drop(vials, false);
            }
            buffer.remove(entry.getKey());
        }
        if (any) {
            setChanged();
        }
        return any;
    }

    public int comparatorSignal() {
        int filled = 0;
        for (ItemStack stack : outputs) {
            if (!stack.isEmpty()) {
                filled++;
            }
        }
        return Math.min(15, (filled * 15) / OUTPUT_SLOTS);
    }

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack stack : outputs) {
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, stack);
            }
        }
        for (Map.Entry<Element, Integer> entry : buffer.entrySet()) {
            if (entry.getValue() > 0) {
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                        ElementVialItem.create(entry.getKey(), entry.getValue()));
            }
        }
    }

    // ------------------------------------------------------------------ ticking

    /** EMP knockout: the machine stalls for a while. */
    public void applyEmp(int ticks) {
        empTicks = Math.max(empTicks, ticks);
        setChanged();
    }

    public boolean isEmped() {
        return empTicks > 0;
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, ProcessorBlockEntity processor) {
        if (processor.empTicks > 0) {
            processor.empTicks--;
            return;
        }
        if (processor.pendingOutputs.isEmpty()) {
            // A synthesizer with a satisfied recipe should start working even if nothing was inserted
            // this tick (e.g. the player fed it the last vial earlier).
            if (processor.kind() == ProcessorKind.SYNTHESIZER) {
                SynthesisTable.Synthesis match = SynthesisTable.findMatch(processor.buffer);
                if (match != null) {
                    for (Map.Entry<Element, Integer> requirement : match.inputs().entrySet()) {
                        processor.buffer.merge(requirement.getKey(), -requirement.getValue(), Integer::sum);
                    }
                    processor.pendingOutputs.add(match.result().copy());
                    processor.totalTicks = match.ticks();
                    processor.progress = 0;
                    processor.setChanged();
                }
            }
            return;
        }

        processor.progress++;
        if (processor.progress < processor.totalTicks) {
            return;
        }

        if (processor.pushToOutputs()) {
            processor.pendingOutputs.clear();
            processor.progress = 0;
            processor.totalTicks = 0;
            processor.setChanged();
        }
        // else: outputs are full, keep waiting (progress stays at totalTicks)
    }

    /** @return true if every pending stack fitted into the output slots. */
    private boolean pushToOutputs() {
        // Work on copies so a partial failure doesn't duplicate items.
        ItemStack[] working = new ItemStack[OUTPUT_SLOTS];
        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            working[i] = outputs[i].copy();
        }

        for (ItemStack pending : pendingOutputs) {
            int remaining = pending.getCount();
            // 1. top up matching stacks
            for (int i = 0; i < OUTPUT_SLOTS && remaining > 0; i++) {
                ItemStack slot = working[i];
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, pending)) {
                    int space = slot.getMaxStackSize() - slot.getCount();
                    int moved = Math.min(space, remaining);
                    slot.grow(moved);
                    remaining -= moved;
                }
            }
            // 2. use empty slots
            for (int i = 0; i < OUTPUT_SLOTS && remaining > 0; i++) {
                if (working[i].isEmpty()) {
                    int moved = Math.min(pending.getMaxStackSize(), remaining);
                    working[i] = pending.copyWithCount(moved);
                    remaining -= moved;
                }
            }
            if (remaining > 0) {
                return false; // not enough room; nothing is committed
            }
        }

        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            outputs[i] = working[i];
        }
        return true;
    }

    // ------------------------------------------------------------------ save / load

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        ListTag outputList = new ListTag();
        for (ItemStack stack : outputs) {
            if (!stack.isEmpty()) {
                outputList.add(ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).getOrThrow());
            }
        }
        tag.put("Outputs", outputList);

        ListTag pendingList = new ListTag();
        for (ItemStack stack : pendingOutputs) {
            pendingList.add(ItemStack.CODEC.encodeStart(NbtOps.INSTANCE, stack).getOrThrow());
        }
        tag.put("Pending", pendingList);

        CompoundTag bufferTag = new CompoundTag();
        for (Map.Entry<Element, Integer> entry : buffer.entrySet()) {
            if (entry.getValue() > 0) {
                bufferTag.putInt(entry.getKey().getSerializedName(), entry.getValue());
            }
        }
        tag.put("Buffer", bufferTag);

        tag.putInt("Progress", progress);
        tag.putInt("Total", totalTicks);
        tag.putInt("Emp", empTicks);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        for (int i = 0; i < OUTPUT_SLOTS; i++) {
            outputs[i] = ItemStack.EMPTY;
        }
        ListTag outputList = tag.getList("Outputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < outputList.size() && i < OUTPUT_SLOTS; i++) {
            int slot = i;
            ItemStack.CODEC.parse(NbtOps.INSTANCE, outputList.get(i))
                    .result()
                    .ifPresent(stack -> outputs[slot] = stack);
        }

        pendingOutputs.clear();
        ListTag pendingList = tag.getList("Pending", Tag.TAG_COMPOUND);
        for (int i = 0; i < pendingList.size(); i++) {
            ItemStack.CODEC.parse(NbtOps.INSTANCE, pendingList.get(i))
                    .result()
                    .ifPresent(pendingOutputs::add);
        }

        buffer.clear();
        CompoundTag bufferTag = tag.getCompound("Buffer");
        for (Element element : Element.values()) {
            int count = bufferTag.getInt(element.getSerializedName());
            if (count > 0) {
                buffer.put(element, count);
            }
        }

        progress = tag.getInt("Progress");
        totalTicks = tag.getInt("Total");
        empTicks = tag.getInt("Emp");
    }
}
