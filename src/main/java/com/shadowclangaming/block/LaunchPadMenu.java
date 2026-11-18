package com.shadowclangaming.block;

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
 * The launch pad's menu.
 *
 * Sixteen numbers, no slots: tier, missile loaded, funding (a long, split across two ints because
 * {@link ContainerData} only carries ints), the radius that funding buys, the FE buffer (also a long),
 * cargo slots used, the pad's channel, how many ticks until it can fire again, the target, the warhead
 * and whether the pad is wired to a panel.
 *
 * Buttons on the screen are deliberately few and safe (launch, unload, clear cargo, reload the panel
 * link); everything free-form - typing "target 120 70 -300" - is a console line over the payload channel,
 * where the server can parse and refuse it.
 */
public class LaunchPadMenu extends AbstractContainerMenu {

    public static final int BUTTON_LAUNCH = 1;
    public static final int BUTTON_UNLOAD = 2;
    public static final int BUTTON_CLEAR_CARGO = 3;
    public static final int BUTTON_LINK_PANEL = 4;
    public static final int BUTTON_LOAD_FE = 5;
    /** Board the loaded missile / climb back off it. */
    public static final int BUTTON_BOARD = 6;

    public static final int SLOT_TIER = 0;
    public static final int SLOT_HAS_MISSILE = 1;
    public static final int SLOT_FUNDING_LO = 2;
    public static final int SLOT_FUNDING_HI = 3;
    public static final int SLOT_RADIUS = 4;
    public static final int SLOT_ENERGY_LO = 5;
    public static final int SLOT_ENERGY_HI = 6;
    public static final int SLOT_CARGO = 7;
    public static final int SLOT_FREQUENCY = 8;
    public static final int SLOT_COOLDOWN = 9;
    public static final int SLOT_TARGET_X = 10;
    public static final int SLOT_TARGET_Y = 11;
    public static final int SLOT_TARGET_Z = 12;
    public static final int SLOT_WARHEAD = 13;
    public static final int SLOT_INTERDIMENSIONAL = 14;
    public static final int SLOT_HAS_PANEL = 15;
    /** How many of the pad's sixteen feed slots hold currency. */
    public static final int SLOT_FEED = 16;
    /** The radius slider on the loaded missile, or -1 when unset. */
    public static final int SLOT_SLIDER_RADIUS = 17;
    /** How many stacks the loaded missile is carrying. */
    public static final int SLOT_PAYLOAD = 18;
    /** Missile module tier (5 = unlimited / a plain missile). */
    public static final int SLOT_MODULE_TIER = 19;
    /** The split altitude set on the loaded missile, or {@link com.shadowclangaming.warhead.MissilePayload#NO_SPLIT_ALTITUDE}. */
    public static final int SLOT_SPLIT_ALTITUDE = 20;
    public static final int DATA_SIZE = 21;

    private final ContainerData data;
    private final BlockPos pos;
    private final ContainerLevelAccess access;

    public LaunchPadMenu(int containerId, Inventory inventory, @Nullable LaunchPadBlockEntity pad, BlockPos pos) {
        super(SCGMenus.LAUNCH_PAD.get(), containerId);
        this.pos = pos;
        this.access = ContainerLevelAccess.create(inventory.player.level(), pos);
        this.data = pad != null ? pad.data() : new SimpleContainerData(DATA_SIZE);
        addDataSlots(this.data);
    }

    /** {@code MenuType} shape - never used at run time. */
    public LaunchPadMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, null, BlockPos.ZERO);
    }

    public LaunchPadMenu(int containerId, Inventory inventory, FriendlyByteBuf buffer) {
        this(containerId, inventory, null, buffer.readBlockPos());
    }

    public BlockPos pos() {
        return pos;
    }

    public int tier() {
        return data.get(SLOT_TIER);
    }

    public boolean hasMissile() {
        return data.get(SLOT_HAS_MISSILE) != 0;
    }

    public long funding() {
        return (Integer.toUnsignedLong(data.get(SLOT_FUNDING_LO)))
                | ((long) data.get(SLOT_FUNDING_HI) << 32);
    }

    /** The player-set radius, or -1 when the warhead's own rule decides. */
    public int sliderRadius() {
        return data.get(SLOT_SLIDER_RADIUS);
    }

    /** The loaded module's tier, or 5 for an unlimited plain missile. */
    public int moduleTier() {
        return data.get(SLOT_MODULE_TIER);
    }

    /** How many stacks the missile in the pad is carrying. */
    public int payloadStacks() {
        return data.get(SLOT_PAYLOAD);
    }

    public int radius() {
        return data.get(SLOT_RADIUS);
    }

    public long energy() {
        return (Integer.toUnsignedLong(data.get(SLOT_ENERGY_LO)))
                | ((long) data.get(SLOT_ENERGY_HI) << 32);
    }

    public int cargoSlots() {
        return data.get(SLOT_CARGO);
    }

    public int frequency() {
        return data.get(SLOT_FREQUENCY);
    }

    public int cooldown() {
        return data.get(SLOT_COOLDOWN);
    }

    /** The pad's target, or null when it has none. */
    public BlockPos target() {
        int x = data.get(SLOT_TARGET_X);
        return x == Integer.MIN_VALUE ? null
                : new BlockPos(x, data.get(SLOT_TARGET_Y), data.get(SLOT_TARGET_Z));
    }

    /** Ordinal of the loaded missile's warhead, or -1 for "nothing loaded". */
    public int warhead() {
        return data.get(SLOT_WARHEAD);
    }

    public boolean interdimensional() {
        return data.get(SLOT_INTERDIMENSIONAL) != 0;
    }

    /** 0..16: how many feed slots are loaded. Hoppers and AE2 buses fill them. */
    public int feedSlots() {
        return data.get(SLOT_FEED);
    }

    /** The altitude the loaded missile will break up at, or NO_SPLIT_ALTITUDE for the frame's own rule. */
    public int splitAltitude() {
        return data.get(SLOT_SPLIT_ALTITUDE);
    }

    public boolean hasPanel() {
        return data.get(SLOT_HAS_PANEL) != 0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, com.shadowclangaming.registry.SCGBlocks.LAUNCH_PAD.get());
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (!(player instanceof ServerPlayer serverPlayer)
                || !(player.level() instanceof net.minecraft.server.level.ServerLevel level)
                || !(level.getBlockEntity(pos) instanceof LaunchPadBlockEntity pad)) {
            return false;
        }
        switch (id) {
            case BUTTON_LAUNCH -> {
                // Launch at the pad's own target; if it has none, aim where the player is looking.
                BlockPos target = pad.getTarget();
                if (target == null) {
                    target = BlockPos.containing(serverPlayer.pick(512.0D, 1.0F, false).getLocation());
                }
                boolean away = pad.fireAt(target);
                if (!away && pad.lastStructureRefusal() != null) {
                    // The silo is not built: name exactly what is missing, in the player's language.
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.shadowclangaming.silo_incomplete",
                            pad.lastStructureRefusal()), false);
                } else if (!away && pad.lastRangeRefusal() > 0L) {
                    // Out of range: say exactly how far short, and which tier ran out of leg.
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.shadowclangaming.out_of_range",
                            pad.lastRangeTier(), String.format("%,d", pad.lastRangeRefusal())), false);
                }
                return true;
            }
            case BUTTON_BOARD -> {
                // Riding a missile is a decision, so it is a button and not a hidden gesture: the player
                // gets to read the target and the warhead before committing.
                if (!pad.hasMissile()) {
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.shadowclangaming.designator_no_missile"), true);
                    return true;
                }
                if (pad.hasRider()) {
                    pad.disembark();
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.shadowclangaming.ride_off"), true);
                } else if (pad.board(serverPlayer)) {
                    serverPlayer.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.shadowclangaming.ride_on"), true);
                }
                return true;
            }
            case BUTTON_UNLOAD -> {
                ItemStack removed = pad.unloadMissile();
                if (!removed.isEmpty() && !player.getInventory().add(removed)) {
                    player.drop(removed, false);
                }
                return true;
            }
            case BUTTON_CLEAR_CARGO -> {
                pad.clearCargo();
                return true;
            }
            case BUTTON_LINK_PANEL -> {
                ControlPanelBlockEntity panel = pad.panel();
                if (panel != null) {
                    panel.linkPad(pos);
                }
                return true;
            }
            case BUTTON_LOAD_FE -> {
                // Turns stored FE into warhead funding now, so the screen shows the new radius.
                pad.fundFromEnergy();
                return true;
            }
            default -> {
                return false;
            }
        }
    }
}
