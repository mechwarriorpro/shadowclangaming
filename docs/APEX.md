# The seventeen apex warheads

Everything above antimatter, in the order they sit on the roster. Each entry is the weapon, what it does
to the world, and the screen effect that sells it.

---

## 0. How they are built (read this first)

**A staged detonation, not a bigger explosion.** Every apex weapon has a timeline: a visible charge, a
destruction front that travels, and a release. That is the whole difference between "a large bang" and a
black hole — you can watch the front close, and the blocks disappear *as it passes*, not all at once.

The data lives in `warhead/apex/Apex.java` (radius, front direction, stage count, stage interval, charge,
shader, particle style) and the curves in `warhead/apex/ApexTimeline.java`. Both are Minecraft-free, so the
logic harness asserts all seventeen of them. The world work is `ApexWarheads`; the scheduling is
`ApexStages`; the non-spherical digging is `ApexCarver`.

```
pad fires -> WarheadType.detonate -> Apex.byId(id) != null -> ApexWarheads.fire
             -> broadcast one ApexFxPacket to nearby clients
             -> ApexStages books a timeline: charge, then stage 0..n-1 spaced intervalTicks apart
             -> each stage: spheres via WarheadType.blast (multithreaded planner, scars),
                            everything else via ApexCarver (masked, budgeted, 4096 blocks/tick)
             -> finish: afterglow, cancel packet, broadcast
```

**Three front directions.** `OUTWARD` grows from the impact (shockwaves, stars); `INWARD` closes toward it
and releases at the end (black holes); `STATIC` is the whole marked volume at once (runes, corruptors).

**The radius is the slider's.** Every weapon takes its radius as a parameter and scales its own effects
against it, so a slider set to 512 gives a 512-block black hole. Nothing in this file is a cap.

**One shader at a time.** Overlapping detonations do not stack full-screen passes; the strongest active
effect wins, weighted by distance and by where it is in its timeline. See §3.

---

## 1. The roster

| # | Weapon | id | Radius | Front | Look |
|---|---|---|---|---|---|
| 1 | **World Eater** | `world_eater` | 96 | inward | black core, lensing, accretion glow |
| 2 | **Stellar Requiem** | `stellar_requiem` | 128 | outward | white flash, blue corona |
| 3 | **Reality Breaker** | `reality_breaker` | 120 | outward | jagged cracks, violet vortex |
| 4 | **Spacetime Hammer** | `spacetime_hammer` | 110 | outward | five refracting rings |
| 5 | **Universal Imploder** | `universal_imploder` | 100 | inward | inward debris, final ring |
| 6 | **Ragnarok** | `ragnarok` | 150 | outward | blood sky, meteors, runes on the horizon |
| 7 | **World Rune** | `world_rune` | 140 | static | glowing sigil, drawn stroke by stroke |
| 8 | **Abyssal Covenant** | `abyssal_covenant` | 90 | static | hundreds of portals, black tendrils |
| 9 | **Soul Furnace** | `soul_furnace` | 80 | inward | growing black-violet sphere, crackling arcs, red sky circles; runs until the mobs run out |
| 10 | **Arcane Singularity** | `arcane_singularity` | 105 | inward | spell circles, violet eruption |
| 11 | **Quasar Lance** | `quasar_lance` | 64 | static | twin jets, a canyon four radii long |
| 12 | **Starforge** | `starforge` | 130 | outward | expanding corona that melts ground |
| 13 | **Big Bang Seed** | `big_bang_seed` | 160 | outward | a point of light becoming a sphere |
| 14 | **Reality Corruptor** | `reality_corruptor` | 95 | static | impossible colour, floating fragments |
| 15 | **Absolute Zero** | `absolute_zero` | 85 | inward | motionless black core, then release |
| 16 | **Omega** | `omega` | 200 | outward | black sky, red corona, annihilation front |
| 17 | **Absolute Annihilation** | `absolute_annihilation` | 256 | outward | seven rotating rings, dimensional fractures |

### What each one does, in world terms

1. **World Eater** — the front closes over 60 ticks, dragging entities in and spiralling loose blocks
   inward along curved streams (`FallingBlockEntity` given tangential velocity). On the last stage the
   void opens: full-power blast plus a solid sphere carve. Blocks inside the sphere are gone; the ones on
   the rim are still falling in.
2. **Stellar Requiem** — a star swells overhead during a 60-tick charge, then three shells open outward,
   each one scorching and shell-carving, the last one blasting at full power.
3. **Reality Breaker** — 24 thin vertical slices are punched through the crust (the "cracks"), then a
   vortex disc carves, then the tear eats the landscape and pulls entities in.
4. **Spacetime Hammer** — five stages, five rings, one trench per stage with the radius of that ring, each
   with its own blast and its own thunder. Living things are shoved radially at up to 1.6 blocks/tick.
5. **Universal Imploder** — pulls at 1.4× strength while shell-carving inward, then a single pulse at
   1.1× power plus a sphere carve. The debris that falls in is destroyed by the pulse.
6. **Ragnarok** — real fireballs (`LargeFireball`, explosion power 2) rain inside the radius each stage,
   spectral (visual-only) lightning strikes, shells carve and scorch.
7. **World Rune** — a ring-with-radial-ticks glyph is engraved stroke by stroke across the ground over
   four stages, one quarter per stage. When the last stroke lands the marked region detonates.
8. **Abyssal Covenant** — 90+ random-walk tendrils per run, each carving a hole at its end; ~240 portal
   particle columns; entities are pulled in and the surface is trenched.
9. **Soul Furnace** — 12 damage plus wither to everything non-player inside the front, weakness and
   darkness to players. The tally feeds the final blast (up to +150 % power) — a crowded battlefield is a
   bigger explosion than an empty one. **It is not on a stage schedule.** It consumes on ignition, then
   looks for more every 100 ticks; while it finds something it grows (radius ×1.18 per check, to ×3.0) and
   its effect gets longer (+8 ticks per soul, to 1800), and it releases only when a look-around comes back
   empty — or at the 40-check safety ceiling, which exists so a mob farm cannot keep a detonation alive
   forever. See `warhead/apex/SoulFurnacePlan` (numbers, harness-tested) and `SoulFurnaceStages` (the part
   that touches the world).
10. **Arcane Singularity** — same collapse math as the imploder (deliberately: it is the magic version of
    the same idea), with a witch/enchant palette and a violet release.
11. **Quasar Lance** — one long capsule carve, `4 × radius` long and up to 16 blocks wide, in a direction
    that is stable per shot, followed by blasts that blow the canyon walls out. This is the only apex
    weapon that is not centred on the impact.
12. **Starforge** — the star grows shell by shell, scorching as it goes, then the corona release.
13. **Big Bang Seed** — six stages of a sphere of energy growing to the full radius, each stage shell-
    carving, and a solid sphere at the end. The slowest of the outward weapons, and the largest.
14. **Reality Corruptor** — ~22 % of blocks in the volume are replaced (sculk, chorus, crying obsidian,
    shroomlight, magenta terracotta), the centre third is deleted outright, and portal particles leak.
15. **Absolute Zero** — a 100-tick charge in which nothing burns, nothing falls and everything alive is
    held still (slowness 7, dig slowness 7, blindness), then a 1.3× pulse and a sphere carve.
16. **Omega** — the annihilation front expands over five stages shell-carving and shoving; the last stage
    blasts at full power and then pulls everything *back in*.
17. **Absolute Annihilation** — seven stages of cracks, sphere carving and pull, ending in a full-power
    blast, a solid sphere carve and a final portal burst. It is the only weapon that both deletes a
    sphere *and* leaves everything inside it corrupted-looking on the way out.

---

## 2. Riding one

An apex warhead is a missile, so the riding rules are the pad's: sneak + empty hand to board, and the
passenger is ejected (+12 Y, slow falling, fire resistance) **before** the detonation. Riding an Omega to
its target is survivable exactly once per set of slow-falling boots, which is the joke.

---

## 3. The shaders

> **Round 46.** The effects are raymarched now: `shaders/core/world_blast_volume` marches a procedural
> noise field through a cube the size of the blast, driven by `BlastMotif.Volume` - one code path for all
> 38 warheads, no textures, no post chain. And the order is fixed: the effect plays for the whole of the
> fuse and the blast lands on its last frame, instead of a silent wait followed by both at once.
> See `docs/WARHEADS.md` §Round 46.
>
> **Round 45.** The screen chains below are still here, but they are no longer how a warhead is *seen*.
> Every warhead now owns a world-space motif — real geometry anchored to the detonation point, depth-tested
> against the terrain: shells, ground sigils, sky circles, pillars, arcs, tendrils, cages. See
> `warhead/BlastMotif` (the table), `client/BlastGeometry` (the triangles, Minecraft-free and
> harness-asserted) and `client/CinematicFx` (the draw). The apex packets drive it too, which is why the
> Soul Furnace's sphere grows: the server re-announces the weapon with a larger radius and a longer life on
> every successful consumption check, and the client ratchets both so growth never plays backwards.

The signature GLSL post-processing chains are available as an opt-in (`post_chains_enabled = true`), not a
shader pack. The 1.21.1 framebuffer chain is opt-in because one tested client displayed a black frame even when
its chain loaded successfully. The safe default uses `ApexOverlay`—the same apex palette/timeline expressed as
translucent GUI geometry, without writing the scene framebuffer. Vanilla `PostChain` reads three files per effect:

```
assets/shadowclangaming/post_effect/apex_<name>.json          the pass list
assets/shadowclangaming/shaders/program/post/apex_<name>.json the pipeline binding
assets/shadowclangaming/shaders/program/post/apex_<name>.fsh the GLSL (shares namespaced apex_common.vsh)
```

Each chain is two passes: the effect (`main → swap`) and a pass-through copy (`swap → main`, program
`apex_copy`). Every effect takes two uniforms, set from Java once per frame:

| Uniform | Meaning |
|---|---|
| `Strength` | 0..1, how hard the effect is pushing right now |
| `Time` | seconds, for animation |

| Effect | Weapon(s) | What the fragment shader does |
|---|---|---|
| `apex_lensing` | World Eater, Universal Imploder | Radially warps the screen toward the centre, darkens the core, paints a hot accretion ring that breathes with `Time` |
| `apex_flash` | Stellar Requiem, Starforge, Big Bang Seed | Adds a white-blue corona, then mixes the whole frame toward white as `Strength` rises |
| `apex_rift` | Reality Breaker, Arcane Singularity, Absolute Annihilation | Per-channel chromatic split plus angular noise — jagged tears, violet veins, darkened edges |
| `apex_shock` | Spacetime Hammer, Quasar Lance | Moving concentric refraction bands, so the rings on screen bend the world behind them |
| `apex_blood` | Ragnarok, Omega | Red grade with a darkened horizon and a shimmering rune pattern |
| `apex_void` | Abyssal Covenant, Absolute Zero | Desaturate toward violet-black with a heavy vignette |
| `apex_runes` | World Rune | A pulsing sigil ring and glyphs drawn over the world |
| `apex_soul` | Soul Furnace | Green spectral haze pulled toward the centre with a swirl |

**Strength** is distance-faded (squared over `radius + 128`), follows the weapon's own `coreIntensity(age)`
curve, ramps in over four ticks, and fades over the final 20% of that apex's `totalTicks()`. It is capped at
0.68 so a post effect can tint or distort the image but can never turn the whole screen black. Short and long
apex timelines therefore have different visual durations.

**Failure is non-fatal.** A shader that will not compile, a driver that refuses the program, or a missing
resource marks that chain dead, logs one line, and leaves the weapon with its particles and its crater.
Losing a screen effect must never lose an explosion. `ApexShaderFx.declared()`, `loaded()` and `failed()`
exist so a bug report can say which one died.

**Why not Iris/Oculus?** A post chain needs no third-party dependency and works on vanilla clients, with
OptiFine-style shader packs installed, and on a dedicated server's players. Asking people to install a
shader pack to see a warhead's signature effect would have been the wrong trade.

---

## 4. Performance

| Knob | Value | Why |
|---|---|---|
| `ApexCarver.BLOCKS_PER_TICK` | 4096 per job | high enough to look instant, low enough to stay inside a tick |
| `ApexCarver.MAX_POSITIONS` | 1,500,000 | a radius-256 sphere is ~70M positions; the queue is capped, not the radius |
| `ApexCarver.MAX_JOBS` | 24 | a griefer with a chest of missiles cannot fill memory |
| `ApexStages.MAX_PENDING` | 64 | the oldest detonation is finished immediately rather than dropped |
| `ApexVisuals.PARTICLES_PER_TICK` | 48 per effect | a radius-256 weapon spawns no more particles than a radius-32 one; it spreads them wider |
| `ApexVisuals.VISIBLE_RANGE` | 320 blocks (+ radius) | no particles for an effect you cannot see |

Block writes never happen on a worker thread, never touch unloaded chunks, never remove bedrock and never
touch a block entity. Spheres go through the existing `BlastScheduler` (parallel planning, budgeted
application) and, above 64 blocks, `StrategicBlasts` (persistent scars, scrubbed as chunks load).

---

## 5. Tested numbers (logic harness, +109 assertions this round)

- 17 weapons, unique ids, every one reachable from its warhead type, every one with a shader and a style.
- Stage schedules: charge offsets, spacing, "the stage tick never exceeds the last stage".
- Fronts: inward closes and releases at full radius, outward opens and ends at the radius, static never
  moves.
- `coreIntensity` is inside 0..1 for every weapon at every tick from −2 to `totalTicks + 20`.
- Ring ladders: hammer 5, annihilation 7, ordinary 3; first ring 1/n, last ring 1.0.
- Every derived budget (meteors, tendrils, portals, rune strokes, lance length/width, soul value) scales
  with radius **and** is capped.
- Power and radius agree on the mod's own curve (`radius × 0.8`).
