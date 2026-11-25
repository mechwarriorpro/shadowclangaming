package com.shadowclangaming.block;

import com.shadowclangaming.computer.CpuTier;
import com.shadowclangaming.computer.GpuTier;
import com.shadowclangaming.computer.TerminalBlockEntity;
import com.shadowclangaming.computer.TerminalModule;
import com.shadowclangaming.registry.SCGMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * The terminal's menu: the status rail of the terminal screen.
 *
 * The terminal has no item slots - it is a computer, not a chest. What the screen needs from the server
 * is small and numeric (frequency, CPU tier, GPU tier, which cards are installed, whether auto-engage is
 * on), so it travels as six {@link ContainerData} slots and costs one packet when something changes.
 *
 * The transcript itself does NOT travel through here: it is free-form text, so it goes over the payload
 * channel (see {@code network.ScgNet}) and lands in the client's console queue.
 */
public class TerminalMenu extends AbstractContainerMenu {

    public static final int SLOT_FREQUENCY = 0;
    public static final int SLOT_CPU = 1;
    public static final int SLOT_GPU = 2;
    public static final int SLOT_MODULES = 3;
    public static final int SLOT_AUTO_ENGAGE = 4;
    public static final int SLOT_OUTPUTS = 5;
    public static final int DATA_SIZE = 6;

    private final ContainerData data;
    private final BlockPos pos;
    private final ContainerLevelAccess access;

    /** Server side: opened by the block with the block entity in hand. */
    public TerminalMenu(int containerId, Inventory inventory, @Nullable TerminalBlockEntity terminal,
                        BlockPos pos) {
        super(SCGMenus.TERMINAL.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = terminal != null ? terminal.data() : new SimpleContainerData(DATA_SIZE);
        addDataSlots(this.data);
    }

    /** {@code MenuType} shape (never used at run time - the block and the packet do the opening). */
    public TerminalMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, BlockPos.ZERO);
    }

    /** Client side: rebuilt from the buffer the server wrote when it opened the screen. */
    public TerminalMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, null, buffer.readBlockPos());
    }

    public BlockPos pos() {
        return pos;
    }

    public int frequency() {
        return data.get(SLOT_FREQUENCY);
    }

    public CpuTier cpu() {
        CpuTier[] tiers = CpuTier.values();
        return tiers[Math.floorMod(data.get(SLOT_CPU), tiers.length)];
    }

    public GpuTier gpu() {
        GpuTier[] tiers = GpuTier.values();
        return tiers[Math.floorMod(data.get(SLOT_GPU), tiers.length)];
    }

    public boolean hasModule(TerminalModule module) {
        return (data.get(SLOT_MODULES) & (1 << module.ordinal())) != 0;
    }

    public boolean autoEngage() {
        return data.get(SLOT_AUTO_ENGAGE) != 0;
    }

    public String outputSummary() {
        int mask = data.get(SLOT_OUTPUTS);
        if (mask == 0) {
            return "-";
        }
        StringBuilder text = new StringBuilder();
        net.minecraft.core.Direction[] directions = net.minecraft.core.Direction.values();
        for (int i = 0; i < directions.length && i < 6; i++) {
            if ((mask & (1 << i)) != 0) {
                text.append(directions[i].getName().charAt(0));
            }
        }
        return text.toString();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, com.shadowclangaming.registry.SCGBlocks.TERMINAL.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        // The terminal screen sends everything as a command line; the only button-shaped action it has is
        // "clear the log", which is safe and local to the block.
        if (id == 0 && player instanceof ServerPlayer && player.level() instanceof net.minecraft.server.level.ServerLevel level
                && level.getBlockEntity(pos) instanceof TerminalBlockEntity terminal) {
            terminal.clearLog();
            return true;
        }
        return false;
    }
}
