# Third-party code and assets

This mod is original work. This file records every place where something else is involved, what its licence
is, whether any of it ships inside our jar, and how the line is kept clean. It exists so that the answer to
"where did this come from?" is never a guess.

**Rules that apply everywhere in this project:**

1. Nothing under a copyleft (GPL/AGPL) licence is ever compiled against or copied. Where interoperability
   with such a mod is useful, the interaction is **reflection only** at run time, and the mod is optional.
2. Nothing is bundled that we do not have the right to bundle. Where an upstream project is MIT and its code
   would be an asset, the licence text and attribution go in this file *before* it goes in the jar.
3. Names, item ids and language keys are ours. Compatibility is achieved through API *shapes* (method names
   that programs call), never by copying someone's names into our registry.

---

## 1. OpenComputers — MIT (reference for the API surface)

- **Upstream:** [MightyPirates/OpenComputers](https://github.com/MightyPirates/OpenComputers) — code MIT
  (Florian "Sangar" Nücke), assets/lang CC0 unless noted.
- **1.21.1 NeoForge ports, all MIT:** `PC-Logix/OpenComputers`, `ThEWiZ76/NeoOpenComputers`,
  `habzg/OpenComputers-Modernized`, `TheMFCraft/OpenComputers-Reworked`, `dev-xiaome/OpenComputers-Neo`.
  **`oc2r` (OC2) is MIT but "OC2: Modern" is GPLv3 — not used, not linked, not copied.**
- **What we do with it:** the *documented API surface* — `component`, `computer`, `event`, `thread`, `gpu`,
  `term`, `fs`, and the component method names — because programs are written against those names and a
  compatible surface is the only way to run MineOS. Behaviour that programs depend on (`event.pull` suspending,
  `fs.open` returning `nil, err`, prefix address lookups, deferred terminal wrap) is matched deliberately.
- **What we do NOT do:** no OpenComputers source is copied into this repository, no LuaJ fork is used, no
  OpenComputers assets, textures, sounds or language keys are included, and no OpenComputers item/block ids
  appear anywhere.
- **Ships in the jar:** nothing from OpenComputers.

## 2. MineOS — MIT

- **Upstream:** [IgorTimofeev/MineOS](https://github.com/IgorTimofeev/MineOS) — MIT, "(c) 2024".
- **What we do with it:** it is the OS this stack is meant to run. It is **installed by the player from its
  own upstream** (`wget` into `/tmp`, then `flash` to the EEPROM), exactly as its documentation says, so the
  user always gets the current release from the author.
- **Ships in the jar:** nothing at this time. If a future version ships a pinned copy for offline servers, it
  will ship as *data* (a zip under `data/shadowclangaming/mineos/`) together with MineOS's `LICENSE` file and the
  upstream commit hash, in a directory that also contains this attribution — never as compiled code.
- **OpenOS is never bundled, installed or offered.** The installer text, the EEPROM image and the shell are
  ours; the OS payload is MineOS or nothing.

## 3. NeoForge / Minecraft — LGPL-2.1 (NeoForge) and Mojang's EULA

Compiled against, not redistributed. Standard for every 1.21.1 mod; the jar contains only our classes and
resources, plus whatever Minecraft/NeoForge the loader supplies at run time.

## 4. Optional mods, all interacted with reflectively

| Mod | Licence | How we touch it |
|---|---|---|
| Applied Energistics 2 | LGPL-3.0 / MIT mix | reflection for the storage bridge; no compile-time dependency |
| Create Big Cannons | MIT | reflection: `setYaw`/`setPitch`, `onRedstoneUpdate`, mount blockstate properties |
| Create: Radars | **Custom** | reflection only; never copied, never bundled |
| Create / Create Aeronautics / Sable | MIT | optional; no hard dependency |
| Numismatics | LGPL-3.0 | coin values read through item tags / reflection only |
| EconomyCraft | **GPL-3.0** | reflection only; never compiled against |
| ProjectE | MIT | plain interfaces implemented by our block entities; no dependency |
| HBM's NTM (`hbm`) | LGPL-3.0 | optional *detection* only, for naming; no code, no items |

## 5. Assets

All textures, models, sounds references and language strings in `src/main/resources` were generated for this
mod (`tools/gen_hardware_assets.py`, `tools/gen_hardware_json.py`) or written by hand. No third-party art is
included. Vanilla textures are referenced, never copied.

## 6. Our own code that could look borrowed

- `src/main/java/com/shadowclangaming/lua/**` — an original interpreter (lexer, parser, compiler, bytecode VM,
  standard library). Not LuaJ, not OpenComputers' `li.cil.oc` Lua code, not a fork of either.
- `src/main/java/com/shadowclangaming/computer/api/**` — an original implementation of a *compatible* API surface,
  as described in §1.
- `docs/LUA.md` §6 lists every behavioural divergence from OpenComputers, so the compatibility claim is
  auditable rather than marketing.

**If you are the author of something referenced here and want the reference changed, open an issue.**
