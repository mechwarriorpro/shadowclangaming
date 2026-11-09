# ShadowClanGaming — NeoForge Audit (arena/da1d4f8a, 2026-11-08)

**Branch:** `arena/da1d4f8a-shadowclangaming` @ `8e40828` → `fix(cbc+fluids)` (+Sweep CBC counting fix, CBC shader full mapping, SCGFluids registration)  
**Environment:** No JDK 21 toolchain in sandbox (`bootstrap_toolchain.sh` needs `api.adoptium.net` which is not in allowlist). Offline static audit via `tools/sweep.py`, `tools/check_names.py`, and custom Python scans. No `javac`/`gradlew build` possible — NeoForge type resolution not available.

---

## Executive summary

| Check | Result | Notes |
|---|---|---|
| **sweep.py** | **PASS** | 346 java, 2522 json, 171 blocks, 1346 items, 2674 lang, 407 recipes, 11 shader chains, 122 motifs, 10 event subscribers |
| **check_names.py** | **PASS** | 346 files, 1997 model/texture refs resolved, modId consistent `shadowclangaming` in `neoforge.mods.toml`/`@Mod`/assets/data/gradle |
| **SCGFluids registration** | **FIXED (was CRITICAL)** | `FLUID_TYPES`+`FLUIDS` `DeferredRegister`s existed but had **no `register(IEventBus)` method** and `ShadowClanGaming` never called it. Fix: added `register()` and bus hook (see patch below). Without it fluids/buckets/fluid blocks are unregistered at runtime. |
| **CBC integration** | **PASS (with caveat)** | 922 CBC items (122 `cbc_shell_*` + 800 `cbc_enhanced_*_t1-50`) have models/lang/tooltips, 5 shader chains present, `CbcWarheadShellCompat` uses `ModList.isLoaded` reflective bridge. `mods.toml` optional deps remain commented (safe — load order not enforced). |
| **CBC shader mapping** | **PASS** | `CbcShaderFx.triggerForWarhead` now covers 107/107 blast-relevant warheads (122 − 15 excluded). Sets deduplicated; fallback prefix ensures no gaps. No duplicate `case` labels. |
| **Braces/parens/brackets** | **PASS** | Sweep: balanced. Naive regex flagged `Lexer.java`/`ScgDiagnostics.java` false-positive (unclosed string handling). |
| **GLSL** | **PASS** | 62 shaders, no reserved words, no redeclarations, 465 uniforms type/count ok, 96 smoothstep edges 0 reversed, `ColorModulator` not used, proxy box 2.0 radii matches Java. |
| **Game logic guards** | **PASS** | Fuse ownership 0, Mojmap names 0, EMP drain 2 typed + capability fallback, shape authoring 42/42, impact FX before first return, geometry pass unconditional + `CORE_SPHERE` suppressed, authored looks 122, skybox 16000 > 8192 cube. |

**Overall: PASS with 1 critical fix applied.** No remaining `CRITICAL` failures detected statically. Build verification requires JDK+Minecraft classpath (CI `./gradlew build`).

---

## 1. Toolchain & build

- `java -version` / `javac` not present; `tools/jdk.sh` finds nothing in `$SCG_JDK`/`$JAVA_HOME`/`/tmp/jdk-21*`/`~/.cache/scg/jdk`.
- `tools/bootstrap_toolchain.sh` downloads JDK from `api.adoptium.net` — **blocked** by egress allowlist (`github.com`, `pypi.org`, `registry.npmjs.org` only). Cache `~/.cache/scg` empty except `dl/`/`libs/` stubs.
- Consequence: cannot run `tools/check_syntax.sh` (needs `javac`), `tools/run_*_harness.sh` (needs JDK 21), or `./gradlew build`. Offline checks are the only option.

---

## 2. Sweep deep dive

```
core world shaders: 2
fuse ownership: no entity counts a fuse it could lose
mojmap names: no Yarn-only identifiers
soul furnace spawn guard: vetoes spawns inside a burning furnace
sky box: 16000 blocks clears a 8192-block blast cube (max radius 4096)
glsl reserved words: none used as identifiers in 62 shaders
glsl scopes: no redeclarations
color modulator: no mod shader depends on it
shader uniform types: 465 uniforms all use a type Minecraft can parse
shader uniform counts: 465 uniforms all declare a matching count
volumetric proxy box: 2.0 radii
glsl smoothstep edges: 96 checked, 0 reversed
emp drain: typed for 2 of our machines, capability for everything else
shape authoring: 42 fields, 42 setters, both switches in their own form
impact effect: announced before the first return in WarheadType.detonate
geometry pass: unconditional alongside the raymarcher, CORE_SPHERE suppressed
authored warhead looks: 122
java files: 346
json files: 2522
blocks: 171, items: 1346
lang keys: 2674
recipes: 407
shader chains: 11
event subscriber classes: 10
warhead motifs: 122
PASS
```

Fix applied this session: `tools/sweep.py:item_ids()` previously only expanded `registerGrenades`/`registerMinecarts` loops → reported **424 items** (undercount by 922 CBC). Now expands `registerCbcWarheadShells` (122) + `registerCbcEnhancedShells` (16 types × 50 = 800) via `CbcShellType.java` parsing → **1346 items**. Previously masked missing-model failures for CBC; now validated.

`check_names.py` still reports **401 items** literal count (does not expand generated loops) but PASSes because `1997` model/texture refs resolve and every `shadowclangaming:<id>` has a translation key. CBC lang coverage verified: `923` lang keys containing `cbc` (922 models + 1 generic). All 122 `cbc_shell_*` and sample `cbc_enhanced_*_t1`/`_t50` models present.

---

## 3. Critical NeoForge error — SCGFluids not on mod bus

**Severity: CRITICAL (runtime missing registry entries). Fixed.**

- `src/main/java/com/shadowclangaming/registry/SCGFluids.java` declares:
  ```java
  DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, MOD_ID);
  DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, MOD_ID);
  ```
  plus 6 `FluidType` + 12 `FlowingFluid` (source/flowing pairs for coolant, molten_unobtainium, molten_yellorium, essentia_flux, sanguine_ichor, mana_essence).

- **Bug:** No `public static void register(IEventBus)` method. `ShadowClanGaming.java` constructor registered 9 other registries (`SCGDataComponents`, `SCGMobEffects`, `SCGArmorMaterials`, `SCGBlocks`, `SCGItems`, `SCGBlockEntities`, `SCGEntities`, `SCGCreativeTabs`, `SCGMobEffects`, `SCGMenus`, `EsperHolder.ATTACHMENTS`) but **not** `SCGFluids`.

- **Effect:** At mod construction none of the 6 fluid types / 12 fluid holders are registered. `BucketItem(SCGFluids.….get())` for 6 bucket items, `BaseFlowingFluid.Properties.bucket/block` suppliers, and `LiquidBlock` fluid references resolve lazily but underlying `Registry` entries are absent → buckets behave as `AIR`, fluid blocks have no fluid state, world-gen/machines using them will NPE or show missing mapping warnings in `latest.log`. Not caught by sweep because fluid block models are in `SKIP_MODELS`.

- **Fix applied (this audit):**
  ```java
  // SCGFluids.java
  public static void register(IEventBus modEventBus) {
      FLUID_TYPES.register(modEventBus);
      FLUIDS.register(modEventBus);
  }
  // ShadowClanGaming.java constructor
  SCGFluids.register(modEventBus); // after SCGItems, before SCGBlockEntities (order irrelevant for DeferredRegister; bus registration is deferred)
  ```
  Verified `sweep PASS` and `check_names PASS` still hold post-fix.

- **Remaining fluid oddity (MINOR):** `SCGItems` has `MOLTEN_NETHER_STAR_BUCKET` (simple item, `registerSimpleItem`) with no corresponding fluid in `SCGFluids`. `SCGFluids` has no `molten_nether_star` fluid type; `SCGItems` has **no** `MOLTEN_NETHER_STAR_BUCKET_FLUID` (fluid bucket). This is intentional — `SmelteryFluid.MOLTEN_NETHER_STAR` is internal tank fluid for alloying, not a world fluid. Documented in `SCGFluids` javadoc ("Remaining propellants … remain compound vials"). Not a crash, but lang/tooltip should clarify distinction.

---

## 4. Event bus & lifecycle

| Class | Annotation | Bus | Dist | Notes |
|---|---|---|---|---|
| `ApexClientEvents` | `@EventBusSubscriber(modid=MOD_ID, bus=GAME, value=CLIENT)` | GAME | CLIENT | ok |
| `BlastClientEvents` | `GAME, CLIENT` | GAME | CLIENT | ok |
| `SCGClient` | `MOD, CLIENT` | MOD | CLIENT | ok (mod lifecycle) |
| `SCGClientColorRegistration` | `MOD, CLIENT` | MOD | CLIENT | ok |
| `WorldBlastShaderRegistration` | `MOD, CLIENT` | MOD | CLIENT | ok |
| `EsperCommands` | `modid="shadowclangaming"` (no bus) | implicit GAME | — | common command registration — ok; `bus = GAME` is default for `RegisterCommandsEvent` |
| `EsperEvents` | `modid="shadowclangaming"` | GAME | — | `PlayerLoggedInEvent` — ok |
| `PolytoolEvents` | `modid=MOD_ID` | GAME | — | `BlockDropsEvent` — ok |
| `ScgNet` | `modid=MOD_ID, bus=MOD` | MOD | — | payload registration on MOD bus — correct |
| `SoulFurnaceSpawnGuard` | `modid=MOD_ID, bus=GAME` | GAME | — | `MobSpawnEvent.PositionCheck` — correct |

- No `@EventBusSubscriber` without `modId` (former false-positive on `VolumetricFx.java`: the string `EventBusSubscriber` appears inside a **comment** `"…@EventBusSubscriber} with nothing to subscribe…"` — class itself is **not** annotated; rendering is via `BlastClientEvents`/`CinematicFx` game-bus listeners — correct per sweep `event subscriber classes: 10`).
- `Dist.CLIENT` guard present on all client renderers; no `Minecraft.getInstance()` leak into common code (0 hits).
- 4 `ServerTickEvent.Post` listeners in `ShadowClanGaming` — intentional split: blast scheduler, interdimensional transit, persistent effects (ForceFields/Outbreak/SpellZones), mana/armor ticks. Not duplicative; each has distinct budget/guard.

---

## 5. Registry & asset consistency

- **Blocks:** 50 `DeferredBlock` in `SCGBlocks`, 171 total block IDs including `explosive_<warhead>` generated loop (WarheadType × packable). All 171 have blockstate + model (SKIP_MODELS: chalk/blood circles, cable/fluid blocks) + loot table. 26 `BlockEntityType`s in `SCGBlockEntities` all reference existing `SCGBlocks` constants (checked 26/26).
- **Items:** 138 literal `DeferredItem` + 20 `missile_module_*_t1-5` via `registerModuleKind("homing","cluster","high_velocity","transport")` + 122 grenade + 122 minecart + 122 `cbc_shell_*` + 800 `cbc_enhanced_*` = reported 1346 (171 block items extra via `registerSimpleBlockItem` counted separately). No literal duplicate IDs.
- **Entities:** 10 registered (`missile`, `grenade`, projectile etc.). `SCGEntities` attribute suppliers validated by sweep `EntityAttributeCreationEvent` check.
- **Fluids:** 6 fluids post-fix now bus-registered; 6 `DeferredBlock<LiquidBlock>` fluid blocks (`FLUID_COOLANT` etc.) reference correct `SCGBlocks` ↔ `SCGFluids` suppliers via lazy `Supplier` (no eager self-ref NPE).
- **Recipes:** 407. Sweep validates shaped key/pattern and result `shadowclangaming:<id>` exists in known registry literals + generated items (including CBC, grenade/minecart, missile modules). Manual rescan confirms 0 bad results once `registerModuleKind` expansion included.

---

## 6. CBC (Create Big Cannons) integration

- **Items:** `CbcWarheadShellItem` (122 warheads as `cbc_shell_<id>`) + `CbcEnhancedShellItem` (14 types × 50 tiers). 922 models + lang keys present (verified `ls models/item/cbc*` 922, `jq lang | grep cbc` 923).
- **Compat:** `CbcWarheadShellCompat.register()` guarded by `ModList.get().isLoaded("createbigcannons")` via reflection (`AbstractCannonProjectile` spawn + explosion intercept, tag entity, replace `power 8` with `WarheadType.detonate`). Manual fire path `fireWarhead(ServerLevel, ...)` via Cannon Director `CANNON_DIRECTOR` block entity. No hard `createbigcannons` import at class-load (soft-dep safe).
- **Shader:** 5 exotic types `plasma/gravitational/vortex/disintegration/singularity` reimplemented (not vendored). Files: `post_effect/cbc_*.json` + `shaders/program/post/cbc_*.json/.fsh` + `shaders/post/cbc_*.fsh`. `CbcShellType.hasShader()` gate, `CbcShaderFx.trigger(level,pos,type,tier)` → `CbcPostFx.push(shader,tier/50,duration)` on both dedicated-server broadcast and integrated-server direct push. Sweep `shader chains: 11` (6 apex/world + 5 CBC) validated: program vertex/fragment namespaced `shadowclangaming:`, source files exist, `Strength`/`Time` uniforms present.
- **Mapping fix:** Previously only 15 warheads mapped (hand-picked plasma/teleport names). Now `triggerForWarhead` excludes 15 non-blast (`dud/decoy/emp/bulwark/cargo/minefield/constructor/spatial/clucker/violator/beef/numismatic/spell/contagion/tracking`) and maps 107 relevant via deduplicated `Set.of` (36 plasma / 20 grav / 11 vortex / 15 disintegration / 25 singularity) + prefix fallback. Verified `mapped 107 / relevant 107 / uncovered 0 / extra 0`.
- **Tiers:** `CbcShellTier` T1–T50 (original Mk.I–V preserved, T6–T50 extended). `damageScale 1.00→3.18`, `blastScale 1.00→2.57` (capped `blastPowerMult 3.0`), `velocityMult 1.0→1.245`. Attribution in `THIRD_PARTY.md` + per-item tooltip ("CBC Enhanced Shells by arginou57 — extended to T50 with permission") + per-shader header.
- **Caveat:** `neoforge.mods.toml` optional deps for `create`/`createbigcannons` remain commented. With reflection guard the jar loads regardless, but load-order `AFTER` not enforced — if player installs CBC, no guarantee CBC registers before us. Recommend uncommenting:
  ```toml
  [[dependencies.shadowclangaming]]
  modId = "createbigcannons"
  type = "optional"
  versionRange = "*"
  ordering = "AFTER"
  side = "BOTH"
  ```

---

## 7. Shaders & rendering

- **Core shaders:** `world_blast.json` (raymarched volume) + `world_blast_geom.json` (cinematic geometry) + `world_blast_volume.json` (volumetric proxy). Checks: `Position+UV0+Color` / `Position+Color` attributes, `discard`/`shock=`/`turbulence`/`Progress` in fragment, `AFTER_PARTICLES` + `BufferUploader.drawWithShader` in `VolumetricFx`/`CinematicFx`.
- **CBC shaders:** Each has `Strength`/`Time` uniforms set every frame from `CbcPostFx` queue; no `ColorModulator` leakage (sweep confirms 0). `smoothstep(edge0,edge1,x)` edges 96 checked 0 reversed (sweep). `glslangValidator` not installed in sandbox but uniform-count/type checks pass (465 uniforms).
- **Overlay:** `CbcPostFx` posts into `GameRenderer` post chain (not GUI overlay). `BlastClientEvents.onRenderLevel` draws `VolumetricFx` + `CinematicFx` unconditionally (sweep `geometry pass` check) with `CORE_SPHERE` suppression — correct double-draw avoidance.

---

## 8. Known non-issues / historical failures resolved

| Prior failure (from session memory) | Status |
|---|---|
| Gradle: `WarheadType Holder/MobEffect/Particle` | **Resolved.** `WarheadType.java` no longer imports `Holder`; uses `MobEffects.*` holders correctly (verified 0 `Holder` imports). |
| `SCGFluids self-ref` | **Resolved.** Fluid props use `Supplier<Block>/<Item>` lambdas (`SCGFluids::coolantBlock`), not eager `get()`. |
| `SCGItems.MOLTEN_NETHER_STAR_BUCKET` | **Resolved.** Simple bucket placeholder retained; fluid bucket naming split `MOLTEN_*_BUCKET` vs `MOLTEN_*_BUCKET_FLUID` intentional per smeltery internal vs world fluid. |
| `SmelteryMelting`, `EsperSkill` | **Resolved.** `SmelteryMelting.java` removed (replaced by `SmelteryMeltingRecipe`); `EsperSkill` no longer has missing imports. |
| `smoothstep reversed 0.42>=0.40` | **Fixed** via `1.0 - smoothstep(edge1,edge0,x)` rewrite. |

---

## 9. Remaining recommendations (non-blocking)

1. **Uncomment optional deps** in `neoforge.mods.toml` for `createbigcannons` (and `create` if used) — cosmetic load-order fix.
2. **`check_names.py` CBC undercount:** Update `tools/check_names.py` to expand `registerCbc*` loops like `sweep.py` now does, so its `401 items` matches sweep’s `1346`.
3. **Fluid lang:** Add `fluid_type.shadowclangaming.*` lang entries if missing (currently `fluid.shadowclangaming.*` + `block.*` present; both work but `FluidType` descriptionId is `fluid_type` in newer NeoForge — verify in-game tooltip).
4. **CI:** Restore `glslang-tools` + JDK 21 in CI image to re-enable `check_glsl.sh` / `check_syntax.sh` / `verify.sh` full chain. Offline audit cannot replace `gradlew build` type-checking.

---

## 10. Patches applied this audit

- `src/main/java/com/shadowclangaming/registry/SCGFluids.java` — added `register(IEventBus)` wiring both `FLUID_TYPES` and `FLUIDS`.
- `src/main/java/com/shadowclangaming/ShadowClanGaming.java` — call `SCGFluids.register(modEventBus)`.
- `tools/sweep.py` — fixed CBC item counting (prior session, included here for completeness).
- `src/main/java/com/shadowclangaming/compat/cbc/CbcShaderFx.java` — full warhead→shader mapping (prior session).

No lang/json/model changes required; all existing assets validated.

---

*Generated offline via `tools/sweep.py` + `tools/check_names.py` + custom scans. For full NeoForge type errors, run `./gradlew build` on a JDK 21 host with Minecraft dependencies cached.*
