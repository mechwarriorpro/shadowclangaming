# Third-party inspirations

## Destroying MC (Fabric 1.21.1, Modrinth mDD42Ylx)
Source: https://github.com/SpacePotatoee/Destroying-Minecraft (archived 2026-04-13, MIT All Rights Reserved on Modrinth, authored by Herr_Chaos/SpacePotato)
Used: photorealistic volume techniques — nuke fireball SDF composition (cylinder+torus+cone with fbm distortion, rayMarchCloud), cracks fbm hole/magma, planet displacement, meteor trails, star_piercer bloom/beam. All shaders reimplemented from description for NeoForge (no Veil buffer copy, Center/Radius/Progress uniforms, original GLSL).

## Mahou Tsukai (NeoForge 1.21.1, CurseForge 342543, stepcros)
Source: https://www.curseforge.com/minecraft/mc-mods/mahou-tsukai — mahoujin_casting.png HD rune layers, rotatable tintable circles. Reimplemented as 512² procedural PNGs (generic+10 per-warhead tints) + MahouCircleRenderer counter-rotating glyph rings.

Both inspirations are not vendored; originals remain under their authors' licenses. This mod ships original code that achieves the same visual result.

## CBC Enhanced Shells [Create Big Cannons] (NeoForge 1.21.1, CurseForge 1489732, arginou57)
Source: https://www.curseforge.com/minecraft/mc-mods/cbc-enchanced-shells-create-big-cannons — All Rights Reserved
Used: With explicit permission from arginou57 (CurseForge DM 2026-11-09):
- **All warheads as CBC cannon ammunition**: every WarheadType now exists as `cbc_shell_<warhead>` — 122 items that can be loaded into Create Big Cannons mounts and detonate as the full warhead (not just power-8 HE). Implemented via reflective compat (`compat/cbc/CbcWarheadShellCompat`) so the jar loads without CBC; when CBC is present, projectile impact is intercepted and replaced with `WarheadType.detonate()`.
- **T50 extension**: Original 9 standard shell types (HE, AP, AP Shot, Solid Shot, Shrapnel, Smoke, Fluid, Mortar Stone, Grape Shot) + 5 shader exotics (Plasma, Gravitational, Vortex, Disintegration, Singularity) × Mk.I–V (5 tiers) extended to T1–T50 (50 tiers, 14×50 = 700 items as `cbc_enhanced_<type>_t<1-50>`). Linear scaling, 3× blast cap preserved for balance, colour tints follow original palette (orange→gold→white). Originals remain Mk.I–V orange→gold; T6–T50 use same progression.
- **Shader effects**: The 5 exotic post-processing shaders (volumetric plasma raymarching, spacetime grid lensing, helical vortex + chromatic aberration, pixel-dissolve hex grid, black-hole lensing + accretion disk) are **original GLSL reimplemented from the described techniques**, not copied files. Each shader is `shaders/post/cbc_<type>.fsh` + `shaders/program/post/cbc_<type>.fsh/.json`, intensity = `tier/50` (T1=2% → T50=100%), duration 3–7s. Credit header in every file.
- **Powder charges**: 5 upgraded powder charges with increased muzzle velocity are acknowledged; our T50 velocity scaling is `1.0 + (tier-1)*0.005` via `CbcShellTier.velocityMult()`.

Attribution is in every item tooltip ("CBC Enhanced Shells by arginou57 — extended to T50 with permission") and in shader file headers. No files from the original mod are vendored; this mod ships original code and GLSL that achieves the same visual result, with permission to reuse the technique and extend the tier system.

## Push tweak 2026-11-08 — sweeping beams & crack trails
Beams (photoreal_beam) now sweep across terrain rather than sitting as a static pillar, leaving a continuous trail of scorched_ground overlay blocks with the photoreal_cracks shader underneath. Trail lifetime 2–3 min (2400–3600 ticks) via scheduled block tick, then reverts; other warheads (nuke/cracks/impact families) leave sparse ground cracks on exposed top surfaces via GroundCrackTrail where visually sensible. Still visual-only use of Destroying MC techniques, per author permission; world destruction remains vanilla/scheduled.
