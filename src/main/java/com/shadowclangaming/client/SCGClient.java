package com.shadowclangaming.client;

import com.shadowclangaming.ShadowClanGaming;
import com.shadowclangaming.entity.MissileEntity;
import com.shadowclangaming.entity.SpellProjectileEntity;
import com.shadowclangaming.registry.SCGMenus;
import com.shadowclangaming.registry.SCGEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/**
 * Client-only setup.
 *
 * The missile gets its own 3D renderer (MissileRenderer) - a model of boxes, oriented along its flight
 * path. The spell bolt keeps vanilla's "held item spinning" renderer, which is exactly right for a
 * crystal shard in flight and needs no model of its own.
 *
 * Anything in this class must only ever run on the client - that is what
 * value = Dist.CLIENT on the annotation enforces.
 */
@EventBusSubscriber(modid = ShadowClanGaming.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class SCGClient {
    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // So /scg fx status can say whether the raymarcher is running instead of only printing config.
        com.shadowclangaming.command.SCGCommands.volumetricStatus =
                com.shadowclangaming.client.VolumetricFx::statusLine;
        event.registerEntityRenderer(SCGEntities.MISSILE.get(), MissileRenderer::new);
        // A spell bolt renders as the amethyst shard it carries - no custom model needed.
        event.registerEntityRenderer(SCGEntities.SPELL_PROJECTILE.get(),
                context -> new ThrownItemRenderer<SpellProjectileEntity>(context, 1.0F, true));
        // The turret chicken IS a chicken, so vanilla's renderer draws it with no new model.
        event.registerEntityRenderer(SCGEntities.TURRET_CHICKEN.get(),
                net.minecraft.client.renderer.entity.ChickenRenderer::new);
        // Beef Supreme is a cow, so the vanilla cow renderer applies.
        event.registerEntityRenderer(SCGEntities.BEEF_SUPREME.get(),
                net.minecraft.client.renderer.entity.CowRenderer::new);
        // A grenade is an item in flight: vanilla's thrown-item renderer is exactly right.
        event.registerEntityRenderer(SCGEntities.GRENADE.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
        // A warhead minecart IS a minecart - vanilla's renderer draws it, TNT block and all.
        event.registerEntityRenderer(SCGEntities.EXPLOSIVE_MINECART.get(),
                context -> new net.minecraft.client.renderer.entity.MinecartRenderer<
                        com.shadowclangaming.entity.WarheadMinecart>(context,
                        net.minecraft.client.model.geom.ModelLayers.MINECART));
        // Its egg IS a thrown egg for rendering purposes.
        event.registerEntityRenderer(SCGEntities.TURRET_EGG.get(),
                net.minecraft.client.renderer.entity.ThrownItemRenderer::new);
    }

    @SubscribeEvent
    public static void registerBlockEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // Landmines are drawn by us, not by the chunk mesher: the renderer decides between the block
        // they are disguised as and the mine itself, per viewer (Night Vision).
        event.registerBlockEntityRenderer(com.shadowclangaming.registry.SCGBlockEntities.MINE.get(),
                MineRenderer::new);
    }

    /**
     * Drop every post chain when the resource pack changes.
     *
     * A post chain holds compiled GL programs loaded from {@code assets/.../shaders}. On an F3+T reload those
     * files are replaced underneath the running chain, and the old program keeps sampling the old sampler -
     * which shows up as "the shaders stopped working after I reloaded my packs". Closing the chains here makes
     * the next detonation rebuild them from the new resources.
     */
    @SubscribeEvent
    public static void registerReloadListener(
            net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new net.minecraft.server.packs.resources.SimplePreparableReloadListener<Void>() {
            @Override
            protected Void prepare(net.minecraft.server.packs.resources.ResourceManager manager,
                                   net.minecraft.util.profiling.ProfilerFiller profiler) {
                return null;
            }

            @Override
            protected void apply(Void unused,
                                 net.minecraft.server.packs.resources.ResourceManager manager,
                                 net.minecraft.util.profiling.ProfilerFiller profiler) {
                BlastShaderFx.close();
                ApexShaderFx.close();
            }
        });
    }

    /**
     * Gives the magic rifle its custom renderer.
     *
     * A rifle renders as the blocks it was built from, which cannot be expressed as a JSON item model:
     * the shape lives on the ItemStack. NeoForge's client extension event is the hook for that - the
     * item class itself stays free of client imports, so a dedicated server never loads this code.
     */
    @SubscribeEvent
    public static void registerClientExtensions(
            net.neoforged.neoforge.client.extensions.common.RegisterClientExtensionsEvent event) {
        event.registerItem(new net.neoforged.neoforge.client.extensions.common.IClientItemExtensions() {
            private RifleBuildRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    var minecraft = net.minecraft.client.Minecraft.getInstance();
                    renderer = new RifleBuildRenderer(
                            minecraft.getBlockEntityRenderDispatcher(), minecraft.getEntityModels());
                }
                return renderer;
            }
        }, com.shadowclangaming.registry.SCGItems.SPELL_RIFLE.get());
    }

    @SubscribeEvent
    public static void registerScreens(RegisterMenuScreensEvent event) {
        event.register(SCGMenus.INSCRIPTION.get(), InscriptionScreen::new);
        event.register(SCGMenus.BLUEPRINT_TABLE.get(), BlueprintTableScreen::new);
        event.register(SCGMenus.TERMINAL.get(), TerminalScreen::new);
        event.register(SCGMenus.LAUNCH_PAD.get(), LaunchPadScreen::new);
        event.register(SCGMenus.CARGO_MISSILE.get(), CargoMissileScreen::new);
        event.register(SCGMenus.CONTROL_PANEL.get(), ControlPanelScreen::new);
        // The silo's own hold (shift + right-click the pad) and the spatial missile's cell drive.
        event.register(SCGMenus.SILO_HOLD.get(), SiloHoldScreen::new);
        event.register(SCGMenus.SPATIAL_CELLS.get(), SpatialCellScreen::new);
    }

    private SCGClient() {
    }
}
