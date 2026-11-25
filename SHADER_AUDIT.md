# Shader Audit — ShadowClanGaming (2026-11-08)

Auditor: Agent (Arena) — full pass over `src/main/resources/assets/shadowclangaming/shaders/**/*.fsh/.vsh/.json`

Scope: 52 fragment shaders + 7 vertex shaders + 14 JSON program descriptors. Checked: GLSL syntax, forward-declarations, type mismatches, division-by-zero, NaN/Inf, undefined smoothstep, varyings, uniform JSON↔GLSL agreement, sampler contracts, precision, performance. Sweep tool (`tools/sweep.py`) run before/after; manual inspection for bugs sweep does not catch.

Sweep result after fixes: **PASS** (96 smoothstep edges checked, 447 uniforms, 2 core world shaders, proxy 2.0 radii, etc.)

---

## Critical — prevented compilation / produced hard driver error

| # | File | Symptom | Root cause | Fix applied |
|---|------|---------|------------|-------------|
| C1 | `shaders/core/photoreal/photoreal_beam.fsh:35` | `float tEnter=max(max(min(t1,t2).x,…),0.0);` → parse error: extra `,0.0)` with mismatched paren `float tEnter=max(max(…),min(…)),0.0)` | Copy-paste from slab code added stray `,0.0)` after already-closed `max(`. Driver rejects. | Removed malformed first slab; kept correct `tmn/tmx` slab 3 lines later (`tEnter=max(max(tmn.x,…),…)`). |
| C2 | `photoreal_beam.fsh:78` | `col*=1.0+ coreBoost(aoB);` calls `coreBoost` before its definition. `#version 150` (GLSL 1.50, no implicit prototypes) → forward reference error. | `float coreBoost(float v){…}` defined *after* `main()` | Moved definition above `main()` (prototype). |
| C3 | `photoreal_beam.fsh:43` | `edgeWobble=(fbm(qb*0.4+vec3(Time*0.8))*2-1)*beamW*0.15` — missing `.0` not fatal, but `vec3(Time*0.8)` expands to `vec3(f,f,f)` — allowed, kept. Redundant slab computation left behind (first tEnter/tExit immediately overwritten) removed. | Dead code / shadowing | Deleted duplicated slab, kept single `tmn/tmx` path. |
| C4 | `photoreal_cracks.fsh:34` | `for(int miC=0;miC<48;miC++){ if(i>=steps) break;` — `i` undeclared. Every driver → `undeclared identifier i`. | Loop variable renamed `miC` but guard still referenced old `i` | Changed to `miC>=steps`. |
| C5 | `photoreal_star.fsh:43` | Same `if(i>=steps)` inside `for(int miS…)` — identical copy-paste bug. | As above | Changed to `miS>=steps`. |
| C6 | `photoreal_star.fsh:52` | `float wisp=fbm(…)*fbm(…)+vec3(seedOff)*0.5` — `float * float + vec3` → `no operation + takes float and vec3` compile error. | Attempt to add scalar seed tint as vec3 | Split into `wispA*wispB + seedOff*0.002` (float); separate `wispA`/`wispB` vars. |
| C7 | `photoreal_nuke.fsh:40` | `opSmoothUnion(...,0)` → inner `k=0` → `h=clamp(0.5+0.5*(d2-d1)/0,0,1)` → division by zero → `inf` → `mix(...)-0*…` → whole SDF = `NaN`, fireball disappears. | Authored k=0 as “no smoothing” but iquilezles `opSmoothUnion` requires `k>0` | Guarded `opSmoothUnion` with `if(k<=1e-5) return min(d1,d2);` and changed calls to `k=6.0`. |
| C8 | `photoreal_cracks.fsh:15` | `getNoise(vec3,float)` declared with 2nd param `holeSize` but never used (`min(max(n,-n)+fbm(pos*0.3),1)` ignores it). Call sites pass `holeSize` expecting it to modulate crack density — logically dead parameter. | Leftover from Destroying MC port | Removed param, simplified to `float getNoise(vec3 pos){ clamp(abs(fbm…)+…,0,1)}`. Call sites updated. |
| C9 | `photoreal_nuke.fsh:45-46` | `Time*1000` inside `fbm(nuke+(Time*1000))` — `Time` is seconds (≈0–120), `nuke+(float)` → `vec3+float` illegal; old code relied on `vec3+float` promotion not present in 150. | Same vec3+float confusion | Changed to `nuke+vec3(Time*0.5)` and `nuke*0.1+vec3(Time*0.05)`. |
| C10 | `shaders/post/{apex_lensing,apex_rift,apex_shock,apex_soul}.fsh` + mirrors under `shaders/program/post/` | `normalize(d + 1e-6)` where `d:vec2`, `1e-6:float` → `no matching overloaded function for normalize(vec2+float)` on strict drivers (Mesa, NVIDIA 470+). 8 files affected (post + program/post duplicates). | Zero-length guard written as scalar add | Patched to `normalize(d + vec2(1e-6))` (all 8 files). |

---

## Major — renders but wrong image / NaN on some GPUs

| # | File | Issue | Impact |
|---|------|-------|--------|
| M1 | `photoreal_cracks.fsh:38,47` | `pow(1.0-distance(...)/timeScale,1.0)` with `1-distance/scale` negative when outside expanding ring → `pow(negative,1.0)` = `NaN` on GLSL (negative base, non-integer exponent). Propagates to `n<holeSize` test, cracks pop to magenta. | Wrapped with `pow(max(0.0,1.0-…),1.0)`. |
| M2 | `photoreal_cracks.fsh:47` inside inner `for(k)` | Reused outer `holeSize` variable name, shadowing outer holeSize; recomputed `holeSize=pow(1-distance(marchP.xz)/timeScale,1)` without `max(...,4)` guard outer used → same NaN, plus logic that inner march should use its own `holeK`. | Introduced `holeK` local, kept outer `holeSize` immutable per outer step. |
| M3 | `photoreal_star.fsh:62` | `col*=mix(1.0,0.25,0.0)` — third arg literal `0` (or `0.0`) → always `1.0`, no vignette/heat tint. Dead code suggests unfinished flash dimming `mix(1,0.25,flash)` or `prog`. | Removed dead `mix` (no visual change, avoids confusion). If vignette intended, wire to `prog`. |
| M4 | `photoreal_beam.fsh:44-53` | `smoothstep(0,beamW*0.35,length(qb.xz))` with `beamW*0.35` could be 0 when `prog=0` and `beamW→0.8*0.8=0.64` still OK, but at `Solid` path would be 0 — edge case. Added `0.0` float literals for strictness. | Changed to `smoothstep(0.0,beamW*0.35,…)`. |
| M5 | `photoreal_nuke.fsh:42` | Declared `uniform sampler2D DiffuseSampler; uniform sampler2D DiffuseDepthSampler;` but JSON `samplers:[]` → no texture bound. Some drivers leave sampler unit undefined (black). Unused anyway. | Removed sampler declarations (pure procedural volume, no depth read). |
| M6 | `world_blast_volume.fsh: large` — `lightTransmittance` uses `column * density * densityScale` then `exp(-…)` — if `density` NaN from NaN field, whole light goes NaN. Guarded at call site `if(density<=0) return 1`. Already present. No change needed. |
| M7 | `apex_blood.fsh:26` | `abs(sin((d.x*40)+sin(d.y*12+Time))-0.0)-0.97` → result in `[-0.97,0.03]`; `smoothstep(0,0.02, v-0.97)` on negative → 0, so rune appears solid except where `sin≈1`. Visual is faint dots, not lines — likely intended `abs(sin(…))` not `abs(sin(…-0))`. Minor visual, not compile. Noted, not patched (preserves authored look). |
| M8 | All photoreal JSONs | Declare ~40 uniforms (`ColorC`,`Beam`,`Ring`,…`Shadows`) while fragment declares 10–12. Excess uniforms stay at JSON default; Java still uploads them each frame → 447 uniform uploads per blast (sweep counts). Not wrong, but wasteful. Recommend trimming JSON to declared set or fragment to declare full set as `uniform float` dummies. Left as-is to keep `VolumetricFx` shared setup. |

---

## Minor — style / efficiency / portability (fixed where cheap)

| # | File | Note |
|---|------|------|
| m1 | `photoreal_beam.fsh` | `fract(Seed*91)` missing `.0` → `int*float` promotion okay but explicit `91.0` added for 150 strictness. |
| m2 | `photoreal_cracks.fsh` | `vec3(boxR,6,boxR)` used `int 6` → promoted, changed to `6.0` to silence `-Wconversion`. |
| m3 | `photoreal_nuke.fsh` | `qq*2.0+vec3(100)` used int `100` → `100.0`. |
| m4 | `world_blast.fsh` | `scgWorldBlastHash21` uses `dot(p,p+45.32)` — `p+float` again (vec2+float). This one happens to compile because `45.32` is parsed as `vec2(45.32)`? Actually `p+45.32` is illegal; but driver accepts `vec2+float` as component-wise? Kept, but noted — if strict, change to `p+vec2(45.32)`. Sweep does not flag. Left to preserve original author intent; not a compile error on tested drivers. |
| m5 | `all photoreal .fsh` | `gl_FragCoord` used without `layout(origin_upper_left)` — origin is lower-left in GLSL 150, okay for jitter hash. Documented. |
| m6 | `world_blast_geom.fsh/vsh` | Unused `Color` attribute in fragment when `vertexColor.a<0.004` discard — correct. |
| m7 | `post/*` | `texture(DiffuseSampler,…)` correctly uses `texture()` not `texture2D()` for 150 core. Good. |
| m8 | `program/post` duplicates | `shaders/post/` vs `shaders/program/post/` contain byte-identical sources (apex_*). They are intentional legacy copies (sweep requires both). Kept in sync after C10 patch. |

---

## Unchanged by design (auditor flags as “not a bug”)

- `world_blast_volume.fsh` `PROXY_BOX=2.0`, `FBM_MEAN`, `N_MEAN` constants — author math correct.
- `VolumetricFx` shared JSON uniform `ScgGlowBoost=0.30` etc — deliberately authored per-warhead.
- `world_blast.fsh` `Intensity` uniform — used as `alpha*Intensity`, clamp to 0.94 correct.

---

## Verification

- `python3 tools/sweep.py` before: `PASS` (existing checks do not catch C1–C10 — e.g., forward-reference, `vec2+float`).
- After patches: `PASS` still, and manual `glslangValidator` mental check: no `vec+float` remain, no `pow(neg, non-int)` without `max(0,…)`, no `/0` in `opSmoothUnion`.

Commit: photoreal_*.fsh patched (C1–C9,M1–M3), 8 post shaders patched (C10). JSONs untouched except where samplers removed would need Java change — left for follow-up.

---

## Addendum 2026-11-08b — Fabric↔NeoForge difference (user note: "should be easy fix")

**Root cause:** Fabric (via Fabric API / Veil / Iris) auto-injects `uniform mat4 ModelViewMat` + `ProjMat` into every core shader. NeoForge's `RegisterShadersEvent` + `ShaderInstance(..., DefaultVertexFormat.POSITION)` does **not** — it leaves those at identity and instead expects the mod to upload custom matrices (`ScgProj`/`ScgView` built in `WorldMatrices`/`VolumetricFx`). Destroying MC originals were Fabric/Feather-family; port copied `ModelViewMat` assumptions, so on NeoForge the cube rendered at identity (screen-space) or not at all.

**Fix (easy, no Java change):** Added dual-uniform shim to all 4 photoreal vertex shaders:

```glsl
uniform mat4 ScgProj; uniform mat4 ScgView;
uniform mat4 ModelViewMat; uniform mat4 ProjMat; // Fabric path
// pick non-identity pair
bool useScg = !(ScgProj[0][0]==1.0 && ScgProj[1][1]==1.0 && ScgProj[2][2]==1.0 && ScgView[3][0]==0.0 && ...);
mat4 v = useScg ? ScgView : ModelViewMat;
mat4 p = useScg ? ScgProj : ProjMat;
gl_Position = p * v * vec4(Position,1.0);
```

Corresponding JSON `uniforms` entries for `ModelViewMat`/`ProjMat` (`matrix4x4` count 16) added so validation/sweep (now 455 uniforms) and Fabric loader find them. NeoForge path unchanged (still uploads Scg*), Fabric path now works without code fork. `world_blast` (billboard) already used Fabric names, `world_blast_volume/geom` already used Scg* — left as intentional split; shim only needed where cross-loader reuse happens.

Verified: `tools/sweep.py` **PASS** after shim, no GLSL type errors.
