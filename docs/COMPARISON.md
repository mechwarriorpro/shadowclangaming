# Side-by-Side: ShadowClanGaming vs Destroying Minecraft (NeoForge)

Both mods now run on **NeoForge 1.21.1 / 21.1.264** so you can see them in the same world, same camera, same shaders — no Fabric/NeoForge split to explain differences away.

## What you're comparing

| Mod | Id | Source | Render path |
|-----|----|--------|-------------|
| **ShadowClanGaming** | `shadowclangaming` 1.0.0 | This repo `src/` | `world_blast_volume.fsh` raymarch + `photoreal_nuke/cracks/star/beam` volumes (NeoForge core shaders, no Veil). 122 warheads, 5 of which are `destroying_nuke/laser/black_hole/planet/supernova` mimics. |
| **Destroying Minecraft (NeoForge port)** | `destroying-minecraft` 1.0.3 | `destroying-minecraft/` (Fabric original by SpacePotatoee/HerrChaos, All Rights Reserved, ported with permission — see THIRD_PARTY.md) | Veil post-pipeline: `pinwheel/shaders/program/nuke/nuke.fsh`, `cracks/cracks.fsh`, `blackhole/black_hole.fsh`, `planet/planet.fsh`, `star_piercer/star_piercer.fsh`, `sky/sky.fsh`, `shadows`, `bloom` — same GLSL, same KeyframeAnimation timelines. |

## Build both jars

```bash
# From repo root, JDK 21 required
./build-both.sh
# or manually:
./gradlew jar
./gradlew :destroying-minecraft:jar
```

Artifacts:
- `build/libs/ShadowClanGaming-1.0.0.jar`
- `destroying-minecraft/build/libs/destroying-minecraft-1.0.3.jar`

Drop both into `mods/` alongside NeoForge 21.1.264. Optional but faithful: Midnigh-tLib, Veil, Cardinal Components (the port bundles Veil via JarInJar like the Fabric original).

## How to compare in-game

1. New creative world, cheats on.
2. Open **ShadowClanGaming** tab → scroll to bottom: `destroying_nuke_missile`, `destroying_laser_missile`, etc. Those are SCG's reimplementations (photoreal shaders, BlastMotif.Volume).
3. Open **Destroying Minecraft** tab → `nuke_trigger`, `laser_trigger`, `black_hole_trigger`, `planet_trigger`, `supernova_trigger`. Those are the port's 1:1 triggers (original DestructionType + original Veil shaders).
4. Place a Launch Pad, fire each side-by-side. Same radius, same origin — use `/destroying-minecraft <type>` or the trigger items for the port, and missiles/designators for SCG.
5. Check: fireball density, mushroom cap, ground cracks (material==2), sky, bloom, black-hole lensing, planet craters, meteor trails, star-piercer bloom. SCG's `photoreal_photoreal_*` were tuned to be *visually* close but not *byte-identical* — the port is the ground truth.

## Notes

- The NeoForge port keeps the original modId `destroying-minecraft` (hyphen is legal in NeoForge `[a-z][a-z0-9_-]{1,63}`) so resource packs still resolve `destroying-minecraft:pinwheel/...`.
- Mixins are declared in `neoforge.mods.toml` via `[[mixins]] config = "destroying-minecraft.mixins.json"` — same list as Fabric, adapted for Mojang mappings.
- Veil on NeoForge is `foundry.veil:veil-neoforge-1.21.1:2.3.2` (Fabric was `veil-fabric`). The jar is JarInJar'd under `META-INF/jars/` like the original.
- All original shaders are vendored verbatim with a credit header (`// Original: SpacePotatoee... ported to NeoForge for comparison`) — no reimplementation drift.
