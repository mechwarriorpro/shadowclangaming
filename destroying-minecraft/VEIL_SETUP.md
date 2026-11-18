# Veil NeoForge Setup (how shaders run)

## Why visuals were 0

Fabric bundled `veil-fabric-1.21.1-2.3.2.jar` as JarInJar (`META-INF/jars/veil-fabric-*.jar`, declared in `fabric.mod.json#jars`).
The port commented out all deps as `compileOnly` with “offline CI stub” — so `VeilEventPlatform`/`ShaderProgram`/`PostPipeline`
never existed at runtime, `UpdatePostShader()` never registered a pipeline, and `setUniforms()` was never called.

## What Veil does (Fabric vs NeoForge — same API)

Veil 2.3.2 exposes identical Java API on both loaders:

- `foundry.veil.api.client.render.post.PostPipeline` + `Context` (holds `ShaderProgram`s)
- `foundry.veil.api.client.render.shader.program.ShaderProgram` + `getUniform(name)` → `ShaderUniform#setFloat/setVector/setMatrix`
- `foundry.veil.api.client.util.Easing` (used in `NukeDestructionClient`)
- `foundry.veil.platform.VeilEventPlatform` (Fabric) → `net.neoforged.neoforge.client.event.RenderLevelStageEvent`
  on NeoForge, but Veil-NF still hooks `VeilEventPlatform` internally.

Our Java **does not change** shader code: `nuke.fsh`, `cracks.fsh`, `planet.fsh`, `black_hole.fsh`, etc.
are the *same bytes* as Fabric, loaded via Veil’s `pinwheel` loader from `assets/destroying-minecraft/pinwheel/**`.

## NeoForge build fix (what to run in CI with network)

```gradle
// destroying-minecraft/build.gradle
repositories {
    maven { url = "https://maven.neoforged.net/releases" } // Veil NeoForge host
}
dependencies {
    // Replace the current compileOnly stubs with:
    implementation "foundry.veil:veil-neoforge-1.21.1:2.3.2"
    include "foundry.veil:veil-neoforge-1.21.1:2.3.2" // JarInJar like Fabric did
    implementation "gg.moonflower:molang-compiler:3.1.1.19"
    implementation "io.github.ocelot:glsl-processor:0.2.3"
    implementation "org.ladysnake.cardinal-components-api:cardinal-components-base:6.1.2"
    implementation "org.ladysnake.cardinal-components-api:cardinal-components-entity:6.1.2"
    implementation "org.ladysnake.cardinal-components-api:cardinal-components-world:6.1.2"
    implementation "eu.midnightdust:midnightlib:1.6.9+1.21-neoforge"
}
```

Local sandbox keeps `compileOnly` stubs so `tools/sweep.py` (no network/JDK) stays green;
CI `build.yml` (JDK 21 + network) resolves maven deps and builds both `ShadowClanGaming-*.jar`
*and* `destroying-minecraft-1.0.3.jar` (each with its own `neoforge.mods.toml` + Veil JarInJar).

## How to verify

1. `./gradlew :destroying-minecraft:build` should produce `destroying-minecraft/build/libs/destroying-minecraft-1.0.3.jar`
   containing `META-INF/jars/veil-neoforge-*.jar` and `assets/destroying-minecraft/pinwheel/post/nuke.json`.
2. In-game: `/destruction nuke ~ ~ ~` → `nuke.fsh` SDF fireball (rayMarchCloud) must appear;
   `/destruction laser ~ ~ ~` → `cracks.fsh` magma veins;
   log should show `BetterUniforms.setVector3f NUKE_POS` (or absence = Veil not on classpath).
