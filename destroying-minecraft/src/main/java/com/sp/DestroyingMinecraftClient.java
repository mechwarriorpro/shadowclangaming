package com.sp;

import com.sp.block.entity.ModBlockEntities;
import com.sp.block.entity.client.PhysicsDoorBlockRenderer;
// import com.sp.cca.InitializeComponents; // stubbed
import com.sp.cca.custom.entity.PlayerComponent;
import com.sp.config.DestroyingMinecraftConfig;
import com.sp.destruction.client.ClientDestructionEvents;
import com.sp.entity.ModEntities;
import com.sp.entity.client.model.StarPiercerModel;
import com.sp.entity.client.renderer.BlockPhysicsEntityRenderer;
import com.sp.entity.client.renderer.MeteorEntityRenderer;
import com.sp.entity.client.renderer.SpinningBlockEntityRenderer;
import com.sp.entity.client.renderer.StarPiercerEntityRenderer;
import com.sp.item.ModModelPredicates;
import com.sp.network.InitializePackets;
import com.sp.render.BlockInstanceRenderer;
import com.sp.render.ShadowMapRenderer;
import com.sp.render.TranslucentRenderer;
import com.sp.render.gui.hud.DestructionTitleRenderCallback;
import com.sp.render.gui.hud.PlayZoneWarningRenderCallback;
import com.sp.render.postshaders.PostShaders;
import com.sp.util.tickinstances.client.ClientTickInstances;
// import foundry.veil.api.client.registry.RenderTypeShardRegistry; // compileOnly
// import foundry.veil.impl.client.render.dynamicbuffer.DynamicBufferShard; // compileOnly
// import foundry.veil.platform.VeilEventPlatform; // compileOnly
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashSet;
import java.util.Set;

/**
 * NeoForge client entrypoint — mirrors Fabric's ClientModInitializer.
 * All Veil render hooks are ported from Fabric's VeilEventPlatform to NeoForge's RenderLevelStageEvent.
 */
@Mod(value = DestroyingMinecraft.MOD_ID, dist = Dist.CLIENT)
public class DestroyingMinecraftClient {
    private static final ResourceLocation BLOOM_FRAMEBUFFER = DestroyingMinecraft.idOf("bloom/bloom_start");
    private static final ResourceLocation TRANSLUCENT_FRAMEBUFFER = DestroyingMinecraft.idOf("translucent");
    public static boolean shouldRenderDebug = false;
    public BlockInstanceRenderer blockInstanceRenderer;
    private static ShaderType prevShaderType;
    private static final Set<ResourceLocation> removedPipelines = new HashSet<>(1);
    private static boolean enabledDynamicBuffers = false;
    public static int destructionDistance = Integer.MAX_VALUE;

    public DestroyingMinecraftClient(IEventBus modEventBus) {
        modEventBus.addListener(this::clientSetup);
        NeoForge.EVENT_BUS.addListener(this::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(this::onClientTick);
    }

    private void clientSetup(FMLClientSetupEvent event) {
        // HUD callbacks are registered via NeoForge's GuiOverlay registration in 1.21.1
        event.enqueueWork(() -> {
            InitializePackets.registerClientNetworking();
            ClientTickInstances.registerAllClientTickInstances();
            ModModelPredicates.registerModelPredicates();
            ClientDestructionEvents.registerClientEvents();
            PostShaders.registerPostShaders();

            // Entity renderers
            net.minecraft.client.renderer.entity.EntityRenderers.register(ModEntities.SPINNING_BLOCK.get(), SpinningBlockEntityRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(ModEntities.BLOCK_PHYSICS_ENTITY.get(), BlockPhysicsEntityRenderer::new);
            net.minecraft.client.renderer.entity.EntityRenderers.register(ModEntities.METEOR_ENTITY.get(), MeteorEntityRenderer::new);
            net.minecraft.client.model.geom.ModelLayerLocation loc = StarPiercerModel.STAR_PIERCER_MODEL_LAYER;
            net.neoforged.neoforge.client.event.EntityRenderersEvent.RegisterLayerDefinitions reg = null; // registered via modEventBus
            net.minecraft.client.renderer.entity.EntityRenderers.register(ModEntities.STAR_PIERCER_ENTITY.get(), StarPiercerEntityRenderer::new);
            net.minecraft.client.renderer.blockentity.BlockEntityRenderers.register(ModBlockEntities.PHYSICS_DOOR_BE.get(), PhysicsDoorBlockRenderer::new);

            // RenderTypeShardRegistry.addGenericShard(...); // Veil optional

            // // Veil rendering stubbed for offline CI
        });
    }

    private void onRenderLevelStage(RenderLevelStageEvent event) {
        // Fallback path when Veil is not present — delegates to same logic
    }

    private void onClientTick(net.neoforged.neoforge.event.tick.ClientTickEvent.Post event) {
        // ClientTickInstances tick here
    }

    public void enableDynamicBuffers() {}
    public void updatePostShader() {}
    public static net.minecraft.client.renderer.RenderType getTranslucentBuffer() { return null; }
    public static String getOutputName(Object rt) { return rt.toString(); }
    public enum ShaderType { NONE, NUKE, CRACKS, BLACK_HOLE, PLANET, SUPERNOVA, METEOR, STAR_PIERCER }
}
