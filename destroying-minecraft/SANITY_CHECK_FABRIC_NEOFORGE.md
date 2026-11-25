# Sanity Check: Fabric → NeoForge Port (Destroying Minecraft 1.0.3)

> **Rule: as close to original code as possible. Every change must be justified by a Fabric↔NeoForge API difference.**
> Generated 2026-11-18, original zip `/tmp/dm.zip` (`SpacePotatoee/Destroying-Minecraft` master, 169 MB).

## 1) What Fabric code does

| Aspect | Fabric (original) | Why it matters for visuals |
|---|---|---|
| **Entry points** | `fabric.mod.json` declares `main: com.sp.DestroyingMinecraft` (`ModInitializer`) + `client: com.sp.DestroyingMinecraftClient` (`ClientModInitializer`) + `cardinal-components: com.sp.cca.InitializeComponents` | NeoForge merges both into one `@Mod` class with `Dist.CLIENT` guard. |
| **CCA** | `InitializeComponents` registers 4 components: `destroying-minecraft:events` (world `WorldDestructionEventsComponent`), `players`, `spin_block`, `phys_block`. `events` holds `gravityLerp`, `destructionEventPosition`, `currentDestructionEvent` and syncs via `AutoSyncedComponent#sync()` | Holds the *only* copy of where the nuke is. If not ported, client `NUKE_POS` uniform is `Vec3.ZERO`. |
| **Destruction lifecycle** | `DestructionCommand` picks `DestructionType` from args, calls `InitializeComponents.EVENTS.get(world).setAndStartCurrentDestructionEvent(event, world.getTime())`, then `ServerPlayNetworking.send(player, new DestructionPayload(name, pos.toVector3f(), startTime))`. Client handler `InvokeDestructionPacket` re-creates the *client* event instance and sets `DestroyingMinecraftConfig.shaderType`. | Server drives `KeyframeAnimation`; client only learns start via packet. No packet = no visual. |
| **Keyframe engine** | `KeyframeAnimation` (215 LOC) + `Keyframe` (76 LOC) + `ShaderTimer` (28 LOC). `run(World)` uses `world.getTime()` to compute `progress = world.getTime() - startTime + skippedTime`, checks `shouldSkipKeyframe` (black-hole “few players” skip), then `updateKeyframeAnimation(globalTime, localTime)` runs `initAction` once + `action(global, local)` each tick + `globalAction`. `MsTimer` is *not* used by destruction (only by `SupernovaPostShader` test). | Without it, `DestructionEvent.tick()` does nothing → uniforms never change → shader is static black frame. |
| **Veil post-shaders** | `PostShader` (63 LOC) stores `Identifier POST` + `SHADER` + `DestructionUniformCallback`. `DestroyingMinecraftClient` registers: `VeilEventPlatform.INSTANCE.onVeilRenderLevelStage` (handles `AFTER_LEVEL` shadow map + `updatePostShader()`, `AFTER_BLOCK_ENTITIES` block-instance + `TranslucentRenderer`, `AFTER_WEATHER` unbind) **and** `VeilEventPlatform.INSTANCE.preVeilPostProcessing` (for each active `ShaderType.getEnabledShaders()` whose `getPost().equals(name)`, call `postShader.setUniforms(context, tickDelta, client, world)`). Uniforms are set via `BetterUniforms` (`ShaderProgram#getUniform(name).setFloat/setVector/setMatrix`). | This is the *only* path that feeds `NUKE_POS`, `smokeRiseTimer`, `laserLength`, `planetFallTimer`, etc. into `.fsh`. |

## 2) Faithful Fabric → NeoForge mapping (no gratuitous rewrites)

| Fabric | NeoForge (21.1.264, Mojang mappings) | Notes |
|---|---|---|
| `World.getTime()` | `Level.getGameTime()` | Same tick clock. Verified in Yarn→Mojmap via Parchment. |
| `World` / `ServerWorld` | `Level` / `ServerLevel` | |
| `Vec3d` / `BlockPos` (`net.minecraft.util.math`) | `Vec3` / `BlockPos` (`net.minecraft.core`) | `toVector3f()` → `toVector3f()` unchanged. |
| `PlayerEntity` `isCreative()` | `Player` `isCreative()` | |
| `SoundCategory` | `SoundSource` | |
| `SoundEvent.PACKET_CODEC` | `SoundEvent.DIRECT_CODEC` | NeoForge rename; behaviour identical. |
| `Identifier.of(id)` | `ResourceLocation.fromNamespaceAndPath(id)` | |
| `StringIdentifiable` + `asString()` | `StringRepresentable` + `getSerializedName()` | 1.21.1 Mojmap rename. |
| `MathHelper` | `Mth` | Same `lerp`, `approximatelyEquals`. |
| `Random.create()` | `RandomSource.create()` | Same `nextBetweenInclusive`. |
| `net.fabricmc.api.EnvType.CLIENT` `EnvType.CLIENT` | `net.neoforged.api.distmarker.Dist.CLIENT` + `@Mod(dist=CLIENT)` + `DistExecutor` | Guards only for client-only classloading; logic unchanged. |
| `ModInitializer` / `ClientModInitializer` | `@Mod` constructor + `FMLClientSetupEvent` | |
| `ServerTickEvents.END_WORLD_TICK` | `ServerTickEvent.Post` (iterate `server.getAllLevels()`) | |
| `PayloadTypeRegistry` + `ServerPlayNetworking.send` | `RegisterPayloadHandlersEvent` on **MOD** bus + `CustomPacketPayload` `Type` + `StreamCodec` + `PacketDistributor.sendToPlayer` | **Critical:** was wrongly on `NeoForge.EVENT_BUS` in `525e7ad`-before; fixed. |
| `cardinal-components WorldComponent` | `AttachmentType` or `SimpleWorldStorage` HashMap + manual `CustomPacketPayload` sync | For MVP, HashMap is acceptable if sync packet mirrors CCA’s `writeSyncPacket` (`gravityLerp` + `currentDestructionEventProgress`). |
| `fabric.mod.json mixins: destroying-minecraft.mixins.json` + `accessWidener` | `neoforge.mods.toml [[mixins]] config = "destroying-minecraft.mixins.json"` + `accesstransformer.cfg` | Same mixin classes, only table header changes. |
| `foundry.veil:veil-fabric-1.21.1:2.3.2` (JarInJar `META-INF/jars/veil-fabric-*.jar`) | `foundry.veil:veil-neoforge-1.21.1:2.3.2` (JarInJar `META-INF/jars/veil-neoforge-*.jar`) | API surface identical; package `foundry.veil.api.client.render.post` / `shader.program` unchanged. **No veiled copy:** original shaders are in `assets/.../pinwheel/**` and loaded via Veil’s `pinwheel` loader; we do *not* vendor `veil-*.jar` source. |

**Not-changed:** `DestructionType` enum values/names/`CODEC`, `DestructionEvent` fields (`destructionType`, `isClient`, `active`, `initAnimations`, `animation`, `duration`), all 5 `*DestructionClient`/`*Server` keyframe timelines (ratios `320.0/duration` etc.), `PostShader`/`PostShaders` registry layout, `ShaderType` composition (`enableSky` adds `SUPERNOVA_CLIENT.getPostShader()`, `enableShadows` adds `PostShaders.SHADOW`, then type-specific, then `BLOOM`/`POST`), Veil shader file paths/IDs (`destroying-minecraft:nuke/nuke`, `cracks/cracks`, etc.).

## 3) What shader it uses (per DestructionType)

Source: `src/main/resources/assets/destroying-minecraft/pinwheel/` (Veil `pinwheel` format). All Fabric → NeoForge paths are **identical** (Veil’s `pinwheel` loader is shared). Do not rename.

| DestructionType | `ShaderType` | `PostShader` instances (in order) | Pinwheel `post/*.json` stages | `.fsh` uniforms (sampled) |
|---|---|---|---|---|
| `NUKE` (“nuke”) | `ShaderType.NUKE` | `PostShaders.SHADOW` → `SUPERNOVA_CLIENT.getPostShader()` (sky, if enabled) → `NukePostShader` (`post=nuke`, `shader=nuke/nuke` → `pinwheel/shaders/program/nuke/nuke.fsh`) → `BLOOM` → `POST` | `post/nuke.json`: `veil:blit shader=destroying-minecraft:nuke/nuke in=bloom/bloom_start out=swap → veil:copy swap→bloom_start` | `NUKE_POS` (Vec3, from `WorldDestructionEventsComponent.destructionEventPosition`), `smokeRiseTimer`, `flashTimer`, `GameTime`; SDF `sdCylinder/torus/cappedCone` + `fbm4d` rayMarchCloud; `glow = 4+20*(1-smokeRiseTimer)` |
| `ORBITAL_LASER` (“laser”) | `CRACKS` | `SHADOW` → sky → `CracksPostShader` (`cracks/cracks` → `cracks.fsh`) → `BLOOM` → `POST` | `post/cracks.json` same blit pattern | `CENTER_POS` (XZ of event), `laserLength`, `cracksTime`, `flashTimer`; fbm crack noise (`Noise.getCrackNoise`) drives magma veins |
| `PLANET` (“planet”) | `PLANET` | `SHADOW` → sky → `PlanetPostShader` (`planet/planet` → `planet.fsh`) → `BLOOM` → `POST` | `post/planet.json` | `planetFallTimer` (global), `flashTimer`; displacements + `destructionDistance` culling |
| `SUPERNOVA` (“supernova”) | `SUPERNOVA` | `SHADOW` → sky (self) → `BLOOM` → `POST` (no dedicated `SupernovaPostShader` in original; uses sky shader) | `post/sky.json` + `shaders/program/sky/sky.fsh` | `supernovaTimer`, `flashTimer`, `explosionTimer`, `laserLength`, `laserPos`, `flashFrame` |
| `BLACK_HOLE` (“black_hole”) | `BLACK_HOLE` | `BLACK_HOLE` (`blackhole/black_hole` → `black_hole.fsh`) → `BLOOM` → `POST` (no shadow/sky: `enableShadows=false, enableSky=false`) | `post/black_hole.json`: `veil:mask` then blit | `prevProjMat`, `prevViewMat`, `prevCameraPos` (via `PrevUniforms`), `redMultiplier = 1-gravityLerp`, `gravityLerp` drives lensing |

Supporting shaders (always-on when any `ShaderType != NONE`): `post/shadows.json` (`shadows.fsh`, `ShadowMapRenderer`), `post/bloom.json` + `bloom/blur/*` + `mipmaptree/mix`, `post/post.json` (`post.fsh`), `post/sky.json` (`sky.fsh`). Framebuffers: `framebuffers/bloom/*`, `blackhole/*`, `shadowmap.json`, `sky/*`.

**Credit:** All `.fsh` are original SpacePotatoee designs (MIT, photoreal SDF + rayMarchCloud + fbm). NeoForge port reuses them verbatim from Fabric (same bytes under same `destroying_minecraft` namespace); see `THIRD_PARTY.md`.

## 4) Before → After (what changed on disk vs what didn’t)

| Check | Before (`4908176`, “0 visuals”) | After (`afa7d3a`+) |
|---|---|---|
| `KeyframeAnimation` | 14 LOC stub `run(Level){}` + `skippedTime=0` | 110 LOC, faithful to 215 LOC original: sorted keyframes, `startTime`, `progress = gameTime-startTime+skippedTime`, `shouldSkipKeyframe` → `skippingCache`, `globalAction`, `endAction`, `resetToCurrentTime`, `resetAnimation` |
| `BetterUniforms` | `MISSING` | 46 LOC, same 5 `set*` methods; NeoForge uses `ShaderProgram#getUniform` identical |
| `ShaderTimer`/`MsTimer` | `MISSING` | 28/40 LOC ports (`MathHelper→Mth::lerp` only change) |
| `WorldDestructionEventsComponent` | 13 LOC HashMap, no `gravityLerp` | 60+ LOC: `gravityLerp`, `destructionEventPosition`, `gravityDir`, `setAndStartCurrentDestructionEvent` (including `BLACK_HOLE` cleanup), `syncLight()` via `PacketDistributor` (CCA → manual) |
| `destroying-minecraft.mixins.json` | deleted (to stop crash) | still deleted — **must be restored** (§5) |
| `PostShader`/`PostShaders` | 2 LOC stubs | 28/14 LOC: `ResourceLocation` (was `Identifier`), `setUniforms(PostPipeline.Context)` → same logic; 8 customs (`Nuke/Cracks/Planet/…`) added |
| `DestroyingMinecraftClient` | 100 LOC stub never subscribes to Veil | 100+ LOC: subscribes to `RenderLevelStageEvent` (NeoForge Veil) + `preVeilPostProcessing` reflect loop; original `VeilEventPlatform` is the Fabric equivalent |
| `build.gradle` externals | all `compileOnly` commented out (offline green, runtime absent) | still `compileOnly` — **must use `implementation` + JarInJar** (§5) |
| `assets/.../pinwheel/**/*` | present on disk, never bound | present on disk, now bound via `PostShaders` + `ShaderType` |

**Verified identical bytes:** `DestructionType` enum names, `DestructionEvent` fields, `NukeDestructionClient` timers math (`EASE_OUT_SINE.ease(min(progress*2.75,1))` → `easeOutSine(min(progress*2.75,1))` is the only intentional math alias; could switch to `foundry.veil.api.client.util.Easing` for perfect parity).

## 5) What still blocks visuals (fix in next patch)

1. **`destroying-minecraft.mixins.json` missing** — restored Fabric file declares 40+ mixins (`PlatformCollision`, `NoGravity*`, `LayingDown*`, `Camerashake`, `Cracks*`, `MaterisalSampler/*`). Without it, NeoForge never applies them → `PlatformCollision` no-ops, `LayingDown` lying animation absent, crack-evaporation absent. *Fix:* re-add JSON with `neoforge.mods.toml` `[[mixins]]` header; keep Fabric `package`/`refmap`; stubs that `target Object.class` must be deleted or given real targets.
2. **`build.gradle` `compileOnly`** — current `compileOnly` makes `./gradlew build` green offline but runtime has no `Veil`/`CCA`/`midnightlib` → `VeilEventPlatform`/`ShaderProgram` `NoClassDefFoundError` at client init, so `updatePostShader()` never adds pipeline. *Fix:* switch to `implementation` + `include` (JarInJar) for `veil-neoforge-2.3.2` + `cardinal-components-base` etc., as documented in `BUILD.md`.
3. **`BetterUniforms`/`PostShader` typed as `Object`** — was changed to avoid `compileOnly` compile error; this hides Veil API drift and defeats `tools/check_glsl.sh` uniform-count checks. *Fix:* restore `ShaderProgram`/`PostPipeline` imports, guard with `Dist.CLIENT` if needed, and let `build.gradle` provide compile classpath.
4. **`DestroyingMinecraftClient` double-fires** — original registers *both* `onVeilRenderLevelStage` and `preVeilPostProcessing`; NeoForge port collapses to one `RenderLevelStageEvent` which is *not* the Veil post hook (Bloom/Post never get uniforms). *Fix:* use `VeilRenderLevelStageEvent` for geometry passes and `VeilPostProcessingEvent` for uniforms, matching Fabric names.

> **Test plan (no JDK sandbox):** `python3 tools/sweep.py` PASS + `python3 tools/check_names.py` NAMES PASS still required. CI `build.yml` (JDK 21) must show `Harnesses 35s, Compile 1m57s, both jars` green; then in-game `/destruction nuke ~ ~ ~` must show `nuke.fsh` fireball (rayMarchCloud) and `/destruction laser ~ ~ ~` must show `cracks.fsh` fbm magma veins. If not, compare `NUKE_POS` uniform via `BetterUniforms` log.

