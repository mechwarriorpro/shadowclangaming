package com.shadowclangaming.block;

import com.shadowclangaming.launcher.LauncherLevel;
import com.shadowclangaming.launcher.LauncherModule;
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
 * The control panel's menu: tier, armed state, target, linked pad, installed modules.
 *
 * The panel is the launcher's brain, so this screen is the launcher's cockpit: arm it, set the target,
 * read the structure report, fire. Typed console lines ("target 100 64 -40", "arm", "launch", "link")
 * go over the payload channel; the buttons cover the things a player presses repeatedly.
 */
public class ControlPanelMenu extends AbstractContainerMenu {

    public static final int BUTTON_TOGGLE_ARMED = 1;
    public static final int BUTTON_LAUNCH = 2;
    public static final int BUTTON_LINK_PAD = 3;
    public static final int BUTTON_TARGET_LOOK = 4;

    public static final int SLOT_TIER = 0;
    public static final int SLOT_ARMED = 1;
    public static final int SLOT_HAS_TARGET = 2;
    public static final int SLOT_TARGET_X = 3;
    public static final int SLOT_TARGET_Y = 4;
    public static final int SLOT_TARGET_Z = 5;
    public static final int SLOT_HAS_PAD = 6;
    public static final int SLOT_PAD_X = 7;
    public static final int SLOT_PAD_Y = 8;
    public static final int SLOT_PAD_Z = 9;
    public static final int SLOT_MODULES = 10;
    public static final int SLOT_DIMENSION = 11;
    public static final int SLOT_MIN_TIER = 12;
    public static final int SLOT_MAX_TIER = 13;
    /** The linked pad's channel, or {@link com.shadowclangaming.computer.Channels#NONE}. */
    public static final int SLOT_PAD_CHANNEL = 14;
    public static final int DATA_SIZE = 15;

    private final ContainerData data;
    private final BlockPos pos;
    private final ContainerLevelAccess access;

    public ControlPanelMenu(int containerId, Inventory inventory, @Nullable ControlPanelBlockEntity panel,
                            BlockPos pos) {
        super(SCGMenus.CONTROL_PANEL.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = panel != null ? panel.data() : new SimpleContainerData(DATA_SIZE);
        addDataSlots(this.data);
    }

    /** {@code MenuType} shape - never used at run time. */
    public ControlPanelMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, BlockPos.ZERO);
    }

    public ControlPanelMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, null, buffer.readBlockPos());
    }

    public BlockPos pos() {
        return pos;
    }

    public int tier() {
        return data.get(SLOT_TIER);
    }

    public boolean armed() {
        return data.get(SLOT_ARMED) != 0;
    }

    public BlockPos target() {
        return data.get(SLOT_HAS_TARGET) == 0 ? null
                : new BlockPos(data.get(SLOT_TARGET_X), data.get(SLOT_TARGET_Y), data.get(SLOT_TARGET_Z));
    }

    public BlockPos linkedPad() {
        return data.get(SLOT_HAS_PAD) == 0 ? null
                : new BlockPos(data.get(SLOT_PAD_X), data.get(SLOT_PAD_Y), data.get(SLOT_PAD_Z));
    }

    public java.util.Set<LauncherModule> modules() {
        java.util.Set<LauncherModule> installed = java.util.EnumSet.noneOf(LauncherModule.class);
        int mask = data.get(SLOT_MODULES);
        for (LauncherModule module : LauncherModule.values()) {
            if ((mask & (1 << module.ordinal())) != 0) {
                installed.add(module);
            }
        }
        return installed;
    }

    public boolean interdimensional() {
        return data.get(SLOT_DIMENSION) != 0;
    }

    /** The linked pad's channel: what a designator would have to be tuned to, to fire this silo. */
    public int padChannel() {
        return data.get(SLOT_PAD_CHANNEL);
    }

    public int minTier() {
        return Math.max(1, data.get(SLOT_MIN_TIER));
    }

    public int maxTier() {
        int max = data.get(SLOT_MAX_TIER);
        return max <= 0 ? LauncherLevel.MAX : max;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, com.shadowclangaming.registry.SCGBlocks.CONTROL_PANEL.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !(level.getBlockEntity(pos) instanceof ControlPanelBlockEntity panel)) {
            return false;
        }
        switch (id) {
            case BUTTON_TOGGLE_ARMED -> panel.setArmed(!panel.armed());
            case BUTTON_LAUNCH -> {
                BlockPos pad = panel.linkedPad();
                if (pad != null && level.getBlockEntity(pad) instanceof LaunchPadBlockEntity launchPad) {
                    BlockPos target = panel.target() != null ? panel.target()
                            : BlockPos.containing(serverPlayer.pick(512.0D, 1.0F, false).getLocation());
                    if (panel.armed()) {
                        launchPad.fireAt(target);
                    }
                }
            }
            case BUTTON_LINK_PAD -> {
                // Look at a pad, press LINK: the panel remembers it. 24 blocks, to reach across a silo.
                var hit = serverPlayer.pick(24.0D, 1.0F, false);
                if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit
                        && level.getBlockEntity(blockHit.getBlockPos()) instanceof LaunchPadBlockEntity launchPad) {
                    panel.linkPad(launchPad.getBlockPos());
                    launchPad.setFrequency(Math.floorMod(panel.getBlockPos().hashCode(), 100));
                }
            }
            case BUTTON_TARGET_LOOK -> {
                var hit = serverPlayer.pick(512.0D, 1.0F, false);
                panel.setTarget(level.dimension(), BlockPos.containing(hit.getLocation()));
            }
            default -> {
                return false;
            }
        }
        return true;
    }
}
