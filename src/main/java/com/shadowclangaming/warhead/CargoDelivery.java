package com.shadowclangaming.warhead;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.List;

/**
 * The cargo missile's delivery.
 *
 * On impact it finds the nearest solid ground and puts a chest on it containing exactly what was
 * loaded. It destroys nothing - a cargo missile is a courier, not a weapon, and a courier that blows
 * up its own parcel is useless.
 *
 * If the ground is occupied the chest goes on top; if a chest is already there the items are merged
 * into it rather than dropping a second chest in the same spot.
 */
public final class CargoDelivery {

    /** How many stacks a cargo warhead can carry. One double chest's worth. */
    public static final int MAX_STACKS = 54;

    private CargoDelivery() {
    }

    /**
     * @return how many stacks were delivered
     */
    public static int deliver(ServerLevel level, BlockPos impact, List<ItemStack> cargo) {
        if (cargo.isEmpty()) {
            return 0;
        }
        BlockPos landing = findLanding(level, impact);
        BlockState existing = level.getBlockState(landing);

        if (existing.getBlock() instanceof ChestBlock
                && level.getBlockEntity(landing) instanceof ChestBlockEntity chest) {
            int delivered = 0;
            for (ItemStack stack : cargo) {
                for (int slot = 0; slot < chest.getContainerSize() && !stack.isEmpty(); slot++) {
                    ItemStack inSlot = chest.getItem(slot);
                    if (inSlot.isEmpty()) {
                        chest.setItem(slot, stack.copy());
                        stack.setCount(0);
                        delivered++;
                    } else if (ItemStack.isSameItemSameComponents(inSlot, stack)
                            && inSlot.getCount() < inSlot.getMaxStackSize()) {
                        int room = inSlot.getMaxStackSize() - inSlot.getCount();
                        int moved = Math.min(room, stack.getCount());
                        inSlot.grow(moved);
                        stack.shrink(moved);
                    }
                }
                if (!stack.isEmpty()) {
                    level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                            landing.getX() + 0.5D, landing.getY() + 1.0D, landing.getZ() + 0.5D, stack));
                }
            }
            chest.setChanged();
            return delivered;
        }

        level.setBlockAndUpdate(landing, Blocks.CHEST.defaultBlockState());
        if (level.getBlockEntity(landing) instanceof ChestBlockEntity chest) {
            int slot = 0;
            for (ItemStack stack : cargo) {
                if (slot >= chest.getContainerSize()) {
                    level.addFreshEntity(new net.minecraft.world.entity.item.ItemEntity(level,
                            landing.getX() + 0.5D, landing.getY() + 1.0D, landing.getZ() + 0.5D, stack.copy()));
                    continue;
                }
                chest.setItem(slot++, stack.copy());
            }
            chest.setChanged();
        }
        return cargo.size();
    }

    /** First solid block at or below the impact that has a free block above it. */
    private static BlockPos findLanding(ServerLevel level, BlockPos impact) {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int down = 0; down <= 12; down++) {
            cursor.set(impact.getX(), impact.getY() - down, impact.getZ());
            if (!level.getBlockState(cursor).isAir()
                    && level.getBlockState(cursor.above()).isAir()) {
                return cursor.above().immutable();
            }
        }
        // Nothing solid below (over the void or the ocean): drop it where it landed.
        return impact;
    }

    /** Small helper for the tooltip: how full cargo is. */
    public static int usedSlots(List<ItemStack> cargo) {
        int used = 0;
        for (ItemStack stack : cargo) {
            if (!stack.isEmpty()) {
                used++;
            }
        }
        return used;
    }

    /** Kept so the chest always faces a sensible way if it is ever placed with a facing. */
    public static final Direction DEFAULT_FACING = Direction.NORTH;
}
