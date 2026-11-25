# Destroying-Minecraft Fabric→NeoForge Port Audit
Generated vs original zip 1776122720.0

## Summary: why visuals=0, effects=0
- All `destruction/client/custom/*` and `destruction/server/custom/*` are 12-line stubs returning `null` KeyframeAnimation (orig 66-184 lines). No ShaderTimer, no BetterUniforms, no PostShaders binding.
- `KeyframeAnimation` is empty stub; original drives `Keyframe` list with global/local actions, `Easing`, `ShaderTimer`. Without it, `DestructionEvent.tick()` never advances.
- `WorldDestructionEventsComponent` replaced by `SimpleWorldStorage` HashMap (3 fields vs 127-line original with `gravityLerp`, `destructionEventPosition`, `syncLight()` packet sync).
- `ClientDestructionEvents.getForWorld()` returns null; `ServerDestructionEvents.onServerTick` is no-op (orig ticks `ServerDestructionEvent` instances + `BlackHoleDestruction.tick`).
- `SpinningBlockExplosion`, `BlackHoleDestruction`, `PlayZone`, `PlayZoneManager` are empty / List.of(). No block carving.
- `BlockInstanceRenderer`, `ShadowMapRenderer`, `TranslucentRenderer`, `PostShaders` are no-ops (orig manages Veil `ShaderProgram`, `PostPipeline`, `ShadowMap` FBOs).
- `DestroyingMinecraftClient` stubs Veil (`RenderTypeShardRegistry`, `DynamicBufferShard`, `VeilEventPlatform`) + never subscribes to `Veil` `RenderLevelStageEvent` pipelines. Veil shaders in `assets/destroying_minecraft/pinwheel/**` are present on disk but never bound.
- `InitializeComponents`, `PlayerComponent`, `SpinningBlockComponent`, `PhysicsBlockComponent` are stubs; CCA sync removed.
- All `mixin/*` are `@Mixin(Object.class)` empty; `neoforge.mods.toml` mixin config was deleted to stop crash, but that also removes `PlatformCollision`, `NoGravity`, `LayingDown`, `Camerashake`, etc. needed for effects (player lying, zero-G, crack evaporation).
- `DestroyingMinecraft` networking split: `RegisterPayloadHandlersEvent` fixed (525e7ad) but `S2C/InvokeDestructionPacket` etc not ported (Fabric uses `ServerPlayNetworking`, NeoForge uses `CustomPacketPayload`). No packets sent, so client never learns server start.
- `DestroyingMinecraftConfig` lost `MidnightConfig` inheritance; shaderType toggle not wired to anything (orig toggled `ShaderType` → Veil pipeline).
- Build.gradle: `veil-neoforge`, `cardinal-components`, `midnightlib`, `sodium` all `compileOnly` commented out → offline build passes, but runtime has no Veil/CCA if user doesn't supply jars; even if supplied, code never calls them.

## File-by-file audit (orig lines vs port lines, status)
| Original | Port | Orig LOC | Port LOC | Status | Notes |
|---|---|---|---|---|---|
| com/sp/DestroyingMinecraft.java | destroying-minecraft/src/main/java/com/sp/DestroyingMinecraft.java | 97 | 105 | OK |  |
| com/sp/DestroyingMinecraftClient.java | destroying-minecraft/src/main/java/com/sp/DestroyingMinecraftClient.java | 296 | 100 | PARTIAL |  |
| com/sp/DestroyingMinecraftDataGenerator.java | destroying-minecraft/src/main/java/com/sp/DestroyingMinecraftDataGenerator.java | 11 | 22 | OK |  |
| com/sp/block/ModBlocks.java | destroying-minecraft/src/main/java/com/sp/block/ModBlocks.java | 35 | 11 | STUB |  |
| com/sp/block/custom/ChairBlock.java | — | 104 | 0 | MISSING |  |
| com/sp/block/custom/PhysicsDoorBlock.java | — | 69 | 0 | MISSING |  |
| com/sp/block/entity/ModBlockEntities.java | destroying-minecraft/src/main/java/com/sp/block/entity/ModBlockEntities.java | 20 | 11 | PARTIAL |  |
| com/sp/block/entity/client/PhysicsDoorBlockRenderer.java | destroying-minecraft/src/main/java/com/sp/block/entity/client/PhysicsDoorBlockRenderer.java | 44 | 10 | STUB |  |
| com/sp/block/entity/custom/PhysicsDoorBlockEntity.java | — | 201 | 0 | MISSING |  |
| com/sp/cca/InitializeComponents.java | destroying-minecraft/src/main/java/com/sp/cca/InitializeComponents.java | 37 | 14 | STUB |  |
| com/sp/cca/custom/entity/PhysicsBlockComponent.java | — | 110 | 0 | MISSING |  |
| com/sp/cca/custom/entity/PlayerComponent.java | destroying-minecraft/src/main/java/com/sp/cca/custom/entity/PlayerComponent.java | 246 | 5 | STUB |  |
| com/sp/cca/custom/entity/SpinningBlockComponent.java | — | 162 | 0 | MISSING |  |
| com/sp/cca/custom/world/WorldDestructionEventsComponent.java | destroying-minecraft/src/main/java/com/sp/cca/custom/world/WorldDestructionEventsComponent.java | 127 | 13 | STUB |  |
| com/sp/collision/BlockOBB.java | — | 342 | 0 | MISSING |  |
| com/sp/command/AddPlayZoneCommand.java | — | 69 | 0 | MISSING |  |
| com/sp/command/DestructionCommand.java | destroying-minecraft/src/main/java/com/sp/command/DestructionCommand.java | 189 | 4 | STUB |  |
| com/sp/command/ModArgumentTypes.java | destroying-minecraft/src/main/java/com/sp/command/ModArgumentTypes.java | 24 | 2 | PARTIAL |  |
| com/sp/command/PlayersCommand.java | destroying-minecraft/src/main/java/com/sp/command/PlayersCommand.java | 96 | 4 | STUB |  |
| com/sp/command/RipPlatformOutCommand.java | destroying-minecraft/src/main/java/com/sp/command/RipPlatformOutCommand.java | 42 | 4 | STUB |  |
| com/sp/component/ModDataComponentTypes.java | destroying-minecraft/src/main/java/com/sp/component/ModDataComponentTypes.java | 24 | 11 | PARTIAL |  |
| com/sp/config/DestroyingMinecraftConfig.java | destroying-minecraft/src/main/java/com/sp/config/DestroyingMinecraftConfig.java | 23 | 6 | PARTIAL |  |
| com/sp/config/ModMenuMenu.java | — | 17 | 0 | MISSING |  |
| com/sp/destruction/DestructionEvent.java | destroying-minecraft/src/main/java/com/sp/destruction/DestructionEvent.java | 99 | 55 | PARTIAL |  |
| com/sp/destruction/DestructionType.java | destroying-minecraft/src/main/java/com/sp/destruction/DestructionType.java | 40 | 23 | PARTIAL |  |
| com/sp/destruction/client/ClientDestructionEvent.java | destroying-minecraft/src/main/java/com/sp/destruction/client/ClientDestructionEvent.java | 46 | 13 | STUB |  |
| com/sp/destruction/client/ClientDestructionEvents.java | destroying-minecraft/src/main/java/com/sp/destruction/client/ClientDestructionEvents.java | 25 | 8 | PARTIAL |  |
| com/sp/destruction/client/custom/BlackHoleDestructionClientPart2.java | — | 86 | 0 | MISSING |  |
| com/sp/destruction/client/custom/LaserDestructionClient.java | destroying-minecraft/src/main/java/com/sp/destruction/client/custom/LaserDestructionClient.java | 179 | 12 | STUB |  |
| com/sp/destruction/client/custom/NukeDestructionClient.java | destroying-minecraft/src/main/java/com/sp/destruction/client/custom/NukeDestructionClient.java | 66 | 12 | STUB |  |
| com/sp/destruction/client/custom/PlanetDestructionClient.java | destroying-minecraft/src/main/java/com/sp/destruction/client/custom/PlanetDestructionClient.java | 135 | 12 | STUB |  |
| com/sp/destruction/client/custom/SupernovaDestructionClient.java | destroying-minecraft/src/main/java/com/sp/destruction/client/custom/SupernovaDestructionClient.java | 184 | 12 | STUB |  |
| com/sp/destruction/server/ServerDestructionEvent.java | destroying-minecraft/src/main/java/com/sp/destruction/server/ServerDestructionEvent.java | 29 | 32 | OK |  |
| com/sp/destruction/server/ServerDestructionEvents.java | destroying-minecraft/src/main/java/com/sp/destruction/server/ServerDestructionEvents.java | 23 | 12 | PARTIAL |  |
| com/sp/destruction/server/custom/BlackHoleDestructionServerPart2.java | — | 86 | 0 | MISSING |  |
| com/sp/destruction/server/custom/LaserDestructionServer.java | destroying-minecraft/src/main/java/com/sp/destruction/server/custom/LaserDestructionServer.java | 92 | 14 | STUB |  |
| com/sp/destruction/server/custom/PlanetDestructionServer.java | destroying-minecraft/src/main/java/com/sp/destruction/server/custom/PlanetDestructionServer.java | 97 | 14 | STUB |  |
| com/sp/destruction/server/custom/SupernovaDestructionServer.java | destroying-minecraft/src/main/java/com/sp/destruction/server/custom/SupernovaDestructionServer.java | 43 | 14 | STUB |  |
| com/sp/entity/ModDamageSources.java | — | 25 | 0 | MISSING |  |
| com/sp/entity/ModEntities.java | destroying-minecraft/src/main/java/com/sp/entity/ModEntities.java | 47 | 16 | STUB |  |
| com/sp/entity/client/model/StarPiercerModel.java | destroying-minecraft/src/main/java/com/sp/entity/client/model/StarPiercerModel.java | 307 | 15 | STUB |  |
| com/sp/entity/client/renderer/BlockPhysicsEntityRenderer.java | destroying-minecraft/src/main/java/com/sp/entity/client/renderer/BlockPhysicsEntityRenderer.java | 212 | 12 | STUB |  |
| com/sp/entity/client/renderer/BlockType.java | — | 126 | 0 | MISSING |  |
| com/sp/entity/client/renderer/MeteorEntityRenderer.java | destroying-minecraft/src/main/java/com/sp/entity/client/renderer/MeteorEntityRenderer.java | 80 | 12 | STUB |  |
| com/sp/entity/client/renderer/SpinningBlockEntityRenderer.java | destroying-minecraft/src/main/java/com/sp/entity/client/renderer/SpinningBlockEntityRenderer.java | 50 | 12 | STUB |  |
| com/sp/entity/client/renderer/StarPiercerEntityRenderer.java | destroying-minecraft/src/main/java/com/sp/entity/client/renderer/StarPiercerEntityRenderer.java | 104 | 12 | STUB |  |
| com/sp/entity/custom/BlockPhysicsEntity.java | — | 390 | 0 | MISSING |  |
| com/sp/entity/custom/MeteorEntity.java | — | 89 | 0 | MISSING |  |
| com/sp/entity/custom/SpinningBlockEntity.java | — | 91 | 0 | MISSING |  |
| com/sp/entity/custom/StarPiercerEntity.java | — | 62 | 0 | MISSING |  |
| com/sp/item/ModItemGroups.java | destroying-minecraft/src/main/java/com/sp/item/ModItemGroups.java | 33 | 28 | OK |  |
| com/sp/item/ModItems.java | destroying-minecraft/src/main/java/com/sp/item/ModItems.java | 28 | 26 | OK |  |
| com/sp/item/ModModelPredicates.java | destroying-minecraft/src/main/java/com/sp/item/ModModelPredicates.java | 15 | 2 | PARTIAL |  |
| com/sp/item/custom/CameraShakeStick.java | destroying-minecraft/src/main/java/com/sp/item/custom/CameraShakeStick.java | 30 | 17 | PARTIAL |  |
| com/sp/item/custom/WalkieTalkie.java | destroying-minecraft/src/main/java/com/sp/item/custom/WalkieTalkie.java | 39 | 15 | STUB |  |
| com/sp/mixin/AddDisconnectCallbackMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/AddDisconnectCallbackMixin.java | 22 | 3 | PARTIAL |  |
| com/sp/mixin/BufferBuilderPositionMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/BufferBuilderPositionMixin.java | 32 | 3 | STUB |  |
| com/sp/mixin/DisableEntityShadowsMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/DisableEntityShadowsMixin.java | 20 | 3 | PARTIAL |  |
| com/sp/mixin/EnableDebugMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/EnableDebugMixin.java | 40 | 3 | STUB |  |
| com/sp/mixin/ForceRenderPlayerMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/ForceRenderPlayerMixin.java | 26 | 3 | PARTIAL |  |
| com/sp/mixin/NoDirectionalBrightnessMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/NoDirectionalBrightnessMixin.java | 18 | 3 | PARTIAL |  |
| com/sp/mixin/PostProcessingManagerAccessor.java | destroying-minecraft/src/main/java/com/sp/mixin/PostProcessingManagerAccessor.java | 15 | 3 | PARTIAL |  |
| com/sp/mixin/SetShadowUniformMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/SetShadowUniformMixin.java | 53 | 3 | STUB |  |
| com/sp/mixin/VeilLevelPerspectiveRendererMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/VeilLevelPerspectiveRendererMixin.java | 24 | 3 | PARTIAL |  |
| com/sp/mixin/WorldRendererAccessor.java | destroying-minecraft/src/main/java/com/sp/mixin/WorldRendererAccessor.java | 38 | 3 | STUB |  |
| com/sp/mixin/compat/flashback/ReplayServerMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/compat/flashback/ReplayServerMixin.java | 31 | 1 | STUB |  |
| com/sp/mixin/compat/sodium/ChunkDestroyingMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/compat/sodium/ChunkDestroyingMixin.java | 28 | 1 | PARTIAL |  |
| com/sp/mixin/compat/sodium/RenderingShadowUniformMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/compat/sodium/RenderingShadowUniformMixin.java | 29 | 1 | PARTIAL |  |
| com/sp/mixin/compat/sodium/clouds/CloudRendererMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/compat/sodium/clouds/CloudRendererMixin.java | 21 | 1 | PARTIAL |  |
| com/sp/mixin/compat/sodium/clouds/WorldRendererMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/compat/sodium/clouds/WorldRendererMixin.java | 135 | 1 | STUB |  |
| com/sp/mixin/camerashake/CameraMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/camerashake/CameraMixin.java | 20 | 1 | PARTIAL |  |
| com/sp/mixin/camerashake/CameraRollMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/camerashake/CameraRollMixin.java | 32 | 1 | STUB |  |
| com/sp/mixin/collision/IsGroundedCheckMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/collision/IsGroundedCheckMixin.java | 42 | 1 | STUB |  |
| com/sp/mixin/collision/NoWalkWhenOnPlatformMixin.java | — | 37 | 0 | MISSING |  |
| com/sp/mixin/collision/PlatformCollisionMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/collision/PlatformCollisionMixin.java | 120 | 1 | STUB |  |
| com/sp/mixin/collision/SneakFixMixinOnPlatforms.java | destroying-minecraft/src/main/java/com/sp/mixin/collision/SneakFixMixinOnPlatforms.java | 41 | 1 | STUB |  |
| com/sp/mixin/cracks/EvaporateWhenInCracksMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/cracks/EvaporateWhenInCracksMixin.java | 28 | 1 | PARTIAL |  |
| com/sp/mixin/cracks/StopRenderingPlayerInCracks.java | destroying-minecraft/src/main/java/com/sp/mixin/cracks/StopRenderingPlayerInCracks.java | 29 | 1 | PARTIAL |  |
| com/sp/mixin/gravity/NoGravityEntityMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/gravity/NoGravityEntityMixin.java | 31 | 1 | STUB |  |
| com/sp/mixin/gravity/NoGravityLivingEntityMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/gravity/NoGravityLivingEntityMixin.java | 59 | 1 | STUB |  |
| com/sp/mixin/gravity/NoGravityParticleMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/gravity/NoGravityParticleMixin.java | 29 | 1 | PARTIAL |  |
| com/sp/mixin/gravity/SlowLimbMovementsMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/gravity/SlowLimbMovementsMixin.java | 27 | 1 | PARTIAL |  |
| com/sp/mixin/layingdown/CameraMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/CameraMixin.java | 50 | 1 | STUB |  |
| com/sp/mixin/layingdown/ClampPlayerYawMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/ClampPlayerYawMixin.java | 52 | 1 | STUB |  |
| com/sp/mixin/layingdown/LyingDownPlayerEntityMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/LyingDownPlayerEntityMixin.java | 125 | 1 | STUB |  |
| com/sp/mixin/layingdown/PreventBlockBreakingMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/PreventBlockBreakingMixin.java | 33 | 1 | STUB |  |
| com/sp/mixin/layingdown/SetLayingDownDirectionMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/SetLayingDownDirectionMixin.java | 32 | 1 | STUB |  |
| com/sp/mixin/layingdown/WakeUpPlayerMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/WakeUpPlayerMixin.java | 32 | 1 | STUB |  |
| com/sp/mixin/layingdown/render/NoHandMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/render/NoHandMixin.java | 26 | 1 | PARTIAL |  |
| com/sp/mixin/layingdown/render/NoNameTagMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/render/NoNameTagMixin.java | 26 | 1 | PARTIAL |  |
| com/sp/mixin/layingdown/render/RenderLyingDownPlayerMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/render/RenderLyingDownPlayerMixin.java | 61 | 1 | STUB |  |
| com/sp/mixin/layingdown/render/RenderSelfMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/layingdown/render/RenderSelfMixin.java | 24 | 1 | PARTIAL |  |
| com/sp/mixin/materialsampler/DynamicBufferProcessorMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/materialsampler/DynamicBufferProcessorMixin.java | 484 | 1 | STUB |  |
| com/sp/mixin/materialsampler/DynamicBufferTypeMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/materialsampler/DynamicBufferTypeMixin.java | 50 | 1 | STUB |  |
| com/sp/mixin/materialsampler/linux/LinuxFixMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/materialsampler/linux/LinuxFixMixin.java | 28 | 1 | PARTIAL |  |
| com/sp/mixin/materialsampler/sodium/SodiumShaderPreProcessorMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/materialsampler/sodium/SodiumShaderPreProcessorMixin.java | 97 | 1 | STUB |  |
| com/sp/mixin/multipledeathvariants/DamageTrackerMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/multipledeathvariants/DamageTrackerMixin.java | 33 | 1 | STUB |  |
| com/sp/mixin/multipledeathvariants/DeathMessageTypeMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/multipledeathvariants/DeathMessageTypeMixin.java | 49 | 1 | STUB |  |
| com/sp/mixin/quasar/ParticleSystemManagerMixin.java | destroying-minecraft/src/main/java/com/sp/mixin/quasar/ParticleSystemManagerMixin.java | 24 | 1 | PARTIAL |  |
| com/sp/mixin/quasar/QuasarFix.java | destroying-minecraft/src/main/java/com/sp/mixin/quasar/QuasarFix.java | 41 | 1 | STUB |  |
| com/sp/mixininterfaces/BlockMaterial.java | — | 9 | 0 | MISSING |  |
| com/sp/mixininterfaces/BufferBuilderPosition.java | — | 7 | 0 | MISSING |  |
| com/sp/mixininterfaces/LayingDownPlayerEntity.java | — | 19 | 0 | MISSING |  |
| com/sp/networking/CustomPayloads.java | — | 206 | 0 | MISSING |  |
| com/sp/networking/InitializePackets.java | — | 42 | 0 | MISSING |  |
| com/sp/networking/ServerPacketManager.java | — | 50 | 0 | MISSING |  |
| com/sp/networking/C2S/UpdatePhysicsDoorPacket.java | — | 34 | 0 | MISSING |  |
| com/sp/networking/S2C/BraamPacket.java | — | 20 | 0 | MISSING |  |
| com/sp/networking/S2C/LavaSpewPacket.java | — | 55 | 0 | MISSING |  |
| com/sp/networking/S2C/InvokeDestructionPacket.java | — | 54 | 0 | MISSING |  |
| com/sp/networking/S2C/PointSBEPacket.java | — | 36 | 0 | MISSING |  |
| com/sp/networking/S2C/ShaderChangePacket.java | — | 18 | 0 | MISSING |  |
| com/sp/networking/S2C/UpdatePlayZonePacket.java | — | 26 | 0 | MISSING |  |
| com/sp/networking/S2C/WaitingRoomPacket.java | — | 33 | 0 | MISSING |  |
| com/sp/networking/callbacks/ClientConnectionEvents.java | — | 20 | 0 | MISSING |  |
| com/sp/render/BlackScreenManager.java | destroying-minecraft/src/main/java/com/sp/render/BlackScreenManager.java | 17 | 2 | PARTIAL |  |
| com/sp/render/ShaderType.java | — | 80 | 0 | MISSING |  |
| com/sp/render/BlockInstanceRenderer.java | destroying-minecraft/src/main/java/com/sp/render/BlockInstanceRenderer.java | 124 | 2 | STUB |  |
| com/sp/render/CustomRenderLayersAndVertexFormats.java | — | 118 | 0 | MISSING |  |
| com/sp/render/PerspectiveRenderer.java | — | 192 | 0 | MISSING |  |
| com/sp/render/PrevUniforms.java | — | 40 | 0 | MISSING |  |
| com/sp/render/SelectionHandler.java | — | 123 | 0 | MISSING |  |
| com/sp/render/ShadowMapRenderer.java | destroying-minecraft/src/main/java/com/sp/render/ShadowMapRenderer.java | 150 | 3 | STUB |  |
| com/sp/render/TranslucentRenderer.java | destroying-minecraft/src/main/java/com/sp/render/TranslucentRenderer.java | 89 | 2 | STUB |  |
| com/sp/render/camerashake/AbstractCameraShakeInstance.java | — | 24 | 0 | MISSING |  |
| com/sp/render/camerashake/CameraShakeManager.java | — | 70 | 0 | MISSING |  |
| com/sp/render/camerashake/custom/CameraShakeInstance.java | — | 29 | 0 | MISSING |  |
| com/sp/render/camerashake/custom/PointCameraShake.java | — | 42 | 0 | MISSING |  |
| com/sp/render/camerashake/custom/SustainedCameraShakeInstance.java | — | 37 | 0 | MISSING |  |
| com/sp/render/gui/HSVColorTextureManager.java | — | 63 | 0 | MISSING |  |
| com/sp/render/gui/hud/DestructionTitleRenderCallback.java | destroying-minecraft/src/main/java/com/sp/render/gui/hud/DestructionTitleRenderCallback.java | 200 | 6 | STUB |  |
| com/sp/render/gui/hud/PlayZoneWarningRenderCallback.java | destroying-minecraft/src/main/java/com/sp/render/gui/hud/PlayZoneWarningRenderCallback.java | 99 | 6 | STUB |  |
| com/sp/render/gui/screen/PhysicsDoorBlockScreen.java | — | 276 | 0 | MISSING |  |
| com/sp/render/gui/screen/widget/HSVColorWidget.java | — | 195 | 0 | MISSING |  |
| com/sp/render/materialsampler/BlockIdMap.java | — | 57 | 0 | MISSING |  |
| com/sp/render/materialsampler/CustomDynamicBuffers.java | — | 9 | 0 | MISSING |  |
| com/sp/render/postshaders/PostShader.java | destroying-minecraft/src/main/java/com/sp/render/postshaders/PostShader.java | 63 | 2 | STUB |  |
| com/sp/render/postshaders/PostShaders.java | destroying-minecraft/src/main/java/com/sp/render/postshaders/PostShaders.java | 28 | 2 | PARTIAL |  |
| com/sp/render/postshaders/custom/BlackHolePostShader.java | — | 41 | 0 | MISSING |  |
| com/sp/render/postshaders/custom/BloomPostShader.java | — | 39 | 0 | MISSING |  |
| com/sp/render/postshaders/custom/CracksPostShader.java | — | 17 | 0 | MISSING |  |
| com/sp/render/postshaders/custom/NukePostShader.java | — | 18 | 0 | MISSING |  |
| com/sp/render/postshaders/custom/PlanetPostShader.java | — | 17 | 0 | MISSING |  |
| com/sp/render/postshaders/custom/PostProcessingPostShader.java | — | 92 | 0 | MISSING |  |
| com/sp/render/postshaders/custom/ShadowPostShader.java | — | 38 | 0 | MISSING |  |
| com/sp/render/postshaders/custom/SupernovaPostShader.java | — | 37 | 0 | MISSING |  |
| com/sp/sounds/ModSounds.java | destroying-minecraft/src/main/java/com/sp/sounds/ModSounds.java | 82 | 11 | STUB |  |
| com/sp/sounds/instances/DoorOpeningLoopSoundInstance.java | — | 38 | 0 | MISSING |  |
| com/sp/sounds/instances/FadingSoundInstance.java | — | 71 | 0 | MISSING |  |
| com/sp/util/BetterUniforms.java | — | 46 | 0 | MISSING |  |
| com/sp/util/MathUtil.java | — | 73 | 0 | MISSING |  |
| com/sp/util/Noise.java | — | 104 | 0 | MISSING |  |
| com/sp/util/RenderUtil.java | — | 141 | 0 | MISSING |  |
| com/sp/util/keyframes/Keyframe.java | — | 77 | 0 | MISSING |  |
| com/sp/util/keyframes/KeyframeAnimation.java | destroying-minecraft/src/main/java/com/sp/util/keyframes/KeyframeAnimation.java | 215 | 14 | STUB |  |
| com/sp/util/tickinstances/client/ClientTickInstances.java | destroying-minecraft/src/main/java/com/sp/util/tickinstances/client/ClientTickInstances.java | 24 | 2 | PARTIAL |  |
| com/sp/util/tickinstances/client/EndClientTickInstances.java | — | 26 | 0 | MISSING |  |
| com/sp/util/tickinstances/client/EndClientWorldTickInstances.java | — | 28 | 0 | MISSING |  |
| com/sp/util/timer/MsTimer.java | — | 43 | 0 | MISSING |  |
| com/sp/util/timer/ShaderTimer.java | — | 28 | 0 | MISSING |  |
| com/sp/world/ModGameRules.java | destroying-minecraft/src/main/java/com/sp/world/ModGameRules.java | 16 | 4 | PARTIAL |  |
| com/sp/world/destructionevent/custom/BlackHoleDestruction.java | destroying-minecraft/src/main/java/com/sp/world/destructionevent/custom/BlackHoleDestruction.java | 350 | 7 | STUB |  |
| com/sp/world/playzone/PlayZone.java | destroying-minecraft/src/main/java/com/sp/world/playzone/PlayZone.java | 41 | 2 | STUB |  |
| com/sp/world/playzone/PlayZoneManager.java | destroying-minecraft/src/main/java/com/sp/world/playzone/PlayZoneManager.java | 59 | 3 | STUB |  |
| com/sp/world/spinningblockexplosion/SpinningBlockExplosion.java | destroying-minecraft/src/main/java/com/sp/world/spinningblockexplosion/SpinningBlockExplosion.java | 44 | 11 | STUB |  |
| com/sp/world/spinningblockexplosion/custom/DirectionalSBE.java | — | 63 | 0 | MISSING |  |
| com/sp/world/spinningblockexplosion/custom/PointSBE.java | — | 90 | 0 | MISSING |  |

## Fabric→NeoForge mapping gaps
- `World#getTime()` → `Level#getGameTime()` (done in 1 place, but `KeyframeAnimation.globalAction` still expects Fabric `World` time)
- `World#getPlayers()` → `Level#players()`; `PlayerEntity` → `Player`; `ServerWorld` → `ServerLevel`; `BlockPos` package `net.minecraft.util.math` → `net.minecraft.core`; `SoundCategory`/`Vec3d` mappings.
- `EnvType.CLIENT` guards → `Dist.CLIENT` `@Mod` (partially done, but many client-only classes still lack guards).
- Networking: Fabric `ServerPlayNetworking.send(player, packet)` / `ClientPlayNetworking.registerGlobalReceiver` → NeoForge `RegisterPayloadHandlersEvent` + `IPayloadHandler` + `CustomPacketPayload`. Packets in `networking/S2C/*` not ported.
- CCA: `ComponentRegistry` + `AutoSyncedComponent` + `ComponentKey#get(world).sync()` → NeoForge `AttachmentType` or manual `SimpleChannel` sync (currently HashMap with no sync, so client never gets `destructionEventPosition`, `gravityLerp`).
- Veil: `VeilEventPlatform`, `PostPipeline`, `ShaderProgram#setVector3f` → NeoForge Veil `VeilRenderSystem` / `VeilPostProcessing` (API changed between veil-fabric-2.3.2 and veil-neoforge-2.3.2). Current stubs bypass all.
- `MidnightConfig.init` → NeoForge `ModConfigSpec` (config values never read; `shaderType` enum unused).

## What must be re-implemented to get visuals/effects back
1. Restore `KeyframeAnimation`/`Keyframe`/`ShaderTimer`/`Easing`/`BetterUniforms` logic (215+76 lines).
2. Restore `WorldDestructionEventsComponent` full state (`BlockPos eventPos`, `gravityLerp`, `currentDestructionEvent`, `syncLight()` via packet) + packet sync layer.
3. Port `destruction/server/custom/*` keyframe timelines (Laser 92L, Planet 97L, BlackHolePart2 86L, etc.) with NeoForge mappings.
4. Port `destruction/client/custom/*` shader uniform binding (`NUKE_POS`, `smokeRiseTimer`, `flashTimer`, etc.) + Veil `PostShaders` pipeline registration (`nuke.json`, `black_hole.json`, `planet.json`, `cracks.json`, etc. already in assets but never loaded).
5. Re-wire `ClientDestructionEvents`/`ServerDestructionEvents` tick to actually tick events via `SimpleWorldStorage`/Attachments.
6. Re-implement or properly remove mixins: `PlatformCollision`, `IsGrounded`, `NoGravity*`, `EvaporateWhenInCracks`, `LayingDown*`, `CameraShake` — either port to NeoForge mixins with correct `net.minecraft` targets or disable features gracefully (currently both stubbed and deleted, so no platform collision, no crack damage, etc.).
7. Port networking packets (`InvokeDestructionPacket`, `LavaSpewPacket`, `BraamPacket`, etc.) to NeoForge `CustomPacketPayload` codecs.
8. Re-enable Veil/CCA dependencies in `build.gradle` (remove `compileOnly` stubs) or provide runtime Jar-in-Jar + compile against them for CI with `mavenLocal` cache.
