package com.shadowclangaming.registry;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.block.ControlPanelMenu;
import com.shadowclangaming.block.BlueprintTableMenu;
import com.shadowclangaming.block.InscriptionMenu;
import com.shadowclangaming.block.LaunchPadMenu;
import com.shadowclangaming.block.TerminalMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Menu types. A menu is the server-side half of a GUI; the screen is the client-side half.
 *
 * Two shapes of menu live here, and the difference matters:
 *
 *   InscriptionMenu   needs NO data beyond the block position, so it uses the vanilla
 *                     {@code MenuType(MenuSupplier, FeatureFlagSet)} constructor.
 *   everything else   needs the block entity on the server (its live {@code ContainerData}), so it uses
 *                     NeoForge's {@link IMenuTypeExtension#create} - the same thing vanilla's own
 *                     chest menus do with their 3-argument factory. The client half rebuilds the menu
 *                     from the position the server writes into the open-screen buffer.
 *
 * Registering a menu is not enough on its own - the screen has to be bound in SCGClient with
 * RegisterMenuScreensEvent, or pressing the block opens an invisible window.
 */
public final class SCGMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, ShadowClanGaming.MOD_ID);

    public static final DeferredHolder<MenuType<?>, MenuType<InscriptionMenu>> INSCRIPTION =
            MENUS.register("inscription_table", () -> IMenuTypeExtension.create(
                    (containerId, inventory, buffer) -> new InscriptionMenu(containerId, inventory, buffer)));

    public static final DeferredHolder<MenuType<?>, MenuType<TerminalMenu>> TERMINAL =
            MENUS.register("terminal", () -> IMenuTypeExtension.create(TerminalMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<LaunchPadMenu>> LAUNCH_PAD =
            MENUS.register("launch_pad", () -> IMenuTypeExtension.create(LaunchPadMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<ControlPanelMenu>> CONTROL_PANEL =
            MENUS.register("control_panel", () -> IMenuTypeExtension.create(ControlPanelMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<BlueprintTableMenu>> BLUEPRINT_TABLE =
            MENUS.register("blueprint_table", () -> IMenuTypeExtension.create(
                    (containerId, inventory, buffer) ->
                            new BlueprintTableMenu(containerId, inventory, buffer)));

    /** The cargo missile's hold: a chest menu over the missile's payload component. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.shadowclangaming.warhead.CargoMissileMenu>>
            CARGO_MISSILE = MENUS.register("cargo_missile", () -> IMenuTypeExtension.create(
            (containerId, inventory, buffer) ->
                    new com.shadowclangaming.warhead.CargoMissileMenu(containerId, inventory, buffer)));

    /** The silo's own 27-slot hold: shift + right-click the pad with an empty hand. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.shadowclangaming.block.SiloHoldMenu>>
            SILO_HOLD = MENUS.register("silo_hold", () -> IMenuTypeExtension.create(
            (containerId, inventory, buffer) ->
                    new com.shadowclangaming.block.SiloHoldMenu(containerId, inventory, buffer)));

    /** The spatial missile's cell drive: an AE2-style bay grid for spatial storage cells. */
    public static final DeferredHolder<MenuType<?>, MenuType<com.shadowclangaming.warhead.SpatialCellMenu>>
            SPATIAL_CELLS = MENUS.register("spatial_cells", () -> IMenuTypeExtension.create(
            (containerId, inventory, buffer) ->
                    new com.shadowclangaming.warhead.SpatialCellMenu(containerId, inventory, buffer)));

    private SCGMenus() {
    }

    public static void register(IEventBus modEventBus) {
        MENUS.register(modEventBus);
    }
}
