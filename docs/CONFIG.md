# ShadowClanGaming — config

Two files, generated on first launch next to `mods/`:

```
config/shadowclangaming-client.toml     what one player sees
config/shadowclangaming-common.toml     what the world does
```

Both are optional — every key has the default used by the mod, and a missing file behaves exactly like a
default one. Values are read through `config/SCGConfig`, which never throws: a value read before the config
is bound (the first frame of a world load can ask) returns the default.

---

## client — `shadowclangaming-client.toml`

### `[screen_effects]`

| key | default | what it does |
|---|---|---|
| `shaders_enabled` | `true` | Enable styled screen effects. Off keeps particles, sound, and gameplay effects. |
| `post_chains_enabled` | `false` | Experimental full-screen post chains. Default is **false** while the 1.21.1 framebuffer path is under investigation; safe styled HUD effects are used instead. Enable only to test custom chains. |
| `shader_strength` | `1.0` | 0.0–2.0 global multiplier on how hard the chains push. |
| `shader_distance` | `1.0` | 0.25–4.0 multiplier on how far away a blast can still tint your screen. |
| `fallback_shaders` | `true` | If a warhead's own chain will not build, use the generic `blast_fallback` chain. |
| `fallback_overlay` | `true` | If **no** chain will build, draw a HUD flash + vignette instead. This is why the mod cannot end up with an invisible explosion. |
| `flash_enabled` | `true` | The instant white-out. Separate from shaders because it is the one effect some players must be able to turn off. |
| `screen_shake` | `true` | Camera shake on nearby detonations. |
| `shake_strength` | `1.0` | 0.0–3.0 multiplier on the shake. `0` is the same as `screen_shake = false`. |

## common — `shadowclangaming-common.toml`

### `[effects]`

| key | default | what it does |
|---|---|---|
| `send_effect_packets` | `true` | Broadcast detonation effects to nearby clients. Off = no shaders for anyone on this server (particles still spawn, they are normal entities). |
| `effect_range` | `512` | 64–4096 blocks. How far an effect packet reaches beyond the blast radius. |
| `particle_scale` | `1.0` | 0.0–3.0 multiplier on the particles the server spawns. Here rather than in the client file because the server spawns them. |
| `blast_threads` | `0` | Worker threads for block damage. `0` lets the mod size the pool from the CPU count. |

---

## How the screen effects degrade

Every detonation — missile, placeable charge, grenade, minecart, designator strike, kamikaze vest — sends one
`blast_fx` packet carrying a **style id** (see `warhead/BlastStyle`). The safe default is a styled HUD treatment,
which does not modify Minecraft's scene framebuffer. Experimental 1.21.1 post chains are opt-in with
`post_chains_enabled = true`; when enabled the client takes the first step that works:

1. **the style's own chain** — `assets/shadowclangaming/post_effect/blast_void.json` and friends
2. **`blast_fallback`** — if that one would not compile on this driver
3. **HUD overlay** — a colored wash + style motif drawn over the GUI if no chain works

`/scg fx status` prints the styles, the config in force and which chain each style uses; `/scg fx test <style>`
sends yourself one effect so a shader can be checked without detonating anything.

## Debug log

The mod writes a separate diagnostic file at:

```
logs/shadow_debug.log
```

It records startup, every client render frame and every queued effect candidate, selected shader/style,
position/distance/reach/strength/age/lifetime, post-chain resource probes, per-frame process duration/result,
full shader exception traces, warhead/apex starts, cluster splits and blast planner submissions/fallbacks. This
is intentionally high-volume: a busy scene can produce a large file. It rotates at 256 MiB to
`logs/shadow_debug.log.1`; both files are useful. Writes are queued off the render thread. It is best-effort and
must not affect gameplay if the file cannot be written. Attach the newest `shadow_debug.log` when reporting.

## Regenerating the shader files

The nine styles are generated from one template so a typo cannot live in one of nine copies:

```
python3 tools/gen_blast_shaders.py
```

That writes, per style: `post_effect/<name>.json`, `shaders/program/post/<name>.json`,
`shaders/post/<name>.fsh`. `tools/sweep.py` then checks that all three exist for every style declared in
`BlastStyle.java`, and that each program declares the `Strength` and `Time` uniforms the Java side sets.
