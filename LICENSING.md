# Licensing & clean-room policy

You're right, and this file now reflects it: **you cannot copyright a game mechanic.** Silos, radar,
guided missiles, decomposing items into elements, centrifuges enriching uranium — those are *ideas*.
What is protected is the **expression**: source code, textures, models, sounds, names, writing.

So the rule for this project is simple:

> **Re-implement the mechanics from scratch, with our own code, art, names and balance.
> Never copy a line of code or a single pixel.**

That is called **clean-room implementation**, it is completely normal in the modding world, and it is
why this mod can have its own licence (MIT if you want) instead of being dragged into GPL.

---

## 1. What we are inspired by — and what we actually built

| Original mechanic | Where it came from | What ShadowClanGaming does instead |
|---|---|---|
| Decompose items into elements | MineChem | Our own `DecompositionTable`: a plain Java table of (item → element, min–max) yields. Our own element enum, our own balance, our own vial item. |
| Combine elements into compounds | MineChem | Our own `SynthesisTable`: element-count recipes with our own timings. Different machine design (no GUI, insert/collect by hand). |
| Periodic table of elements | MineChem | Our `Element` enum: 19 entries chosen for *this* mod's tech tree, not a full 118-element catalogue. Our own colours, our own tooltips. |
| Silos, launch pads, redstone firing | ICBM | `LaunchPadBlock` + redstone + comparator, our own design and code. |
| Radar, missile warning, anti-ballistic interception | ICBM | Milestone M2, own implementation (entity index + lead-pursuit interceptors). |
| Guided missile flight | ICBM / NTM | `MissileEntity`: our own simple "lean toward target" steering, our own numbers. |
| Nuclear fuel chain (ore → enrichment → warhead) | Nuclear Science / NTM | Our own `SynthesisTable` route: raw uranium → U-238 vials → Enriched Uranium → Fission Missile. |
| Fusion fuel | Nuclear Science | Our own: Lithium + Hydrogen vials → Tritium Cell → Fusion Missile. |
| Deep industry, reactors, radiation, full chemistry | HBM's NTM | **Not reimplemented.** Optional soft-dependency on NTM: Decentralized Edition (`hbm`) if the player wants it. |

Nothing in the list above required looking at another mod's source.

---

## 2. The clean-room rules (stick to these and you're safe)

**DO**

- ✅ Work from *how it plays*: watch videos, read wikis, remember what was fun — then design your own
  implementation. Mechanics and game rules are not copyrightable.
- ✅ Use real-world science for names and numbers. `Uranium-235`, `Tritium`, `SRBM/MRBM/ICBM` are
  real-world terms, nobody owns them.
- ✅ Draw your own 16×16 textures (all art in this repo was generated for this project).
- ✅ Pick your own balance numbers. In fact you *should* — a different difficulty curve is what makes
  your server unique.
- ✅ Depend on other mods through their public API (`ModList.isLoaded("hbm")` + their API classes).
  Linking is not copying, and LGPL explicitly allows it.

**DON'T**

- ❌ Decompile ICBM/NTM/MineChem and paste or "heavily adapt" their classes. That's a derivative work,
  and GPL/LGPL would then apply to your mod.
- ❌ Copy their textures, models, sounds, or configs. (MIT on ICBM's *code* does not hand you its art —
  assets are usually licensed separately.)
- ❌ Reuse their invented names verbatim in a published mod (`MineChem`, `ICBM`, `HBM's Nuclear Tech`,
  `Voltz`, specific item names like their branded warheads). Rename everything. Terms that exist in
  the real world — SRBM, ICBM, centrifuge, uranium — are fine.
- ❌ Ask a contributor who *has* read the original source to write the same feature. Keep the
  implementation authors "clean". (Real clean-room teams keep a documented separation like this.)

**If you ever break the rule** (you copy a file): that file becomes GPL/LGPL, you must publish it, and
you must note it in `NOTICE`. It's recoverable, but it's much easier to not do it.

---

## 3. Consequence for your licence

Because this project contains **no copied code**, you are free to licence it however you like:

- **MIT** — recommended: max adoption, people can build addons for your server.
- **LGPL-3.0** — nice middle ground if you want improvements to come back to you.
- **All Rights Reserved / closed source** — allowed for *your own* code. Note: if you later add an
  optional integration that *links* against NTM:DE (LGPL), that's still fine — linking is not
  derivative. But if you paste NTM code, the pasted parts must be LGPL and published.

The current `LICENSE` file is a placeholder. Pick one before publishing.

---

## 4. Reference table of the mods involved

| Mod | Licence (checked 23 Oct 2026) | Safe to link against | Safe to copy code | Safe to copy art |
|---|---|---|---|---|
| Create | MIT | ✅ | ✅ with attribution | ⚠️ check assets repo |
| Create: Numismatics | LGPL-3.0 | ✅ | ✅ (copied parts stay LGPL) | ❌ |
| Sable | PolyForm Shield 1.0.0 | ✅ | ❌ (non-compete clause) | ❌ |
| Create Aeronautics | Custom | ✅ | ❌ | ❌ |
| Create Big Cannons | Custom | ✅ | ❌ | ❌ |
| HBM's NTM / NTM: NEXT | GPL-3.0 | ✅ | ⚠️ infects your mod | ❌ |
| NTM: Decentralized Edition (1.21.1) | LGPL-3.0 | ✅ | ⚠️ copied files stay LGPL | ❌ |
| ICBM | MIT (**code only**) | ✅ | ✅ with attribution | ❌ assume not |
| MineChem | Check the repo before touching it | ✅ | ❌ (not needed — see above) | ❌ |

We don't copy from any of them, so the table is mostly there so you can say "no" confidently when
someone asks you to "just port the textures".

---

## 5. Project checklist

- [x] Zero hard dependencies — the jar runs alone.
- [x] All code written for this project.
- [x] All textures/models generated for this project (Pillow scripts, no external assets).
- [x] All names either real-world scientific/military terms or invented for this mod.
- [x] Integrations are optional and use runtime mod detection.
- [ ] Before publishing: choose a real licence, add `NOTICE` if you ever copy something.
- [ ] Before publishing a public pack: re-read Sable's PolyForm Shield and the Aeronautics terms —
      those two gate *redistribution*, not your own code.

---

## Third-party provenance

Every reference to someone else's code, licence, API or assets — OpenComputers (MIT), MineOS (MIT), the
optional mods we touch reflectively (AE2, CBC, Create: Radars, Numismatics, EconomyCraft, ProjectE) and the
rule that **OpenOS is never bundled** — is recorded in **`THIRD_PARTY.md`**. If a claim in this file and a
claim in that file ever disagree, that file wins and this one gets fixed.
