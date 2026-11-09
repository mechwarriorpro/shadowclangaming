# ShadowClanGaming — design & feasibility

*Written 23 Oct 2026. All version/licence facts below were checked against Modrinth's API, GitHub and
the NeoForge 21.1.264 sources on that date.*

---

## 1. The honest starting point

You wanted: **Create Aeronautics + Create Big Cannons + Create: Numismatics + HBM's NTM + ICBM + Nuclear Science.**

Reality check on 1.21.1 / NeoForge:

| Target | Status Oct 2026 | What it means for you |
|---|---|---|
| **Create Aeronautics** | ✅ `1.3.2+mc1.21.1` — NeoForge **only** (won't ever be on Forge ≤1.20.1). Requires **Create 6.x** + **Sable 2.x** (its physics/sub-level library). | Free, no work needed. Read Sable's API for anything you want to work on contraptions. |
| **Create Big Cannons** | ✅ `5.11.7`, plus healthy addon scene (Going Ballistic, Advanced Technologies, Peripherals). | Free. Integration target for M3. |
| **Create: Numismatics** | ✅ `1.1.0+neoforge-mc1.21.1`, **LGPL-3.0** with a real API. | Free. Economy glue in M5. |
| **HBM's NTM** | ⚠️ Original is 1.7.10/GPL-3.0. For 1.21.1 there is **NTM: Decentralized Edition** — a backport of NTM: NEXT (from MC 26.2) to 1.21.1 NeoForge, LGPL-3.0, **already ships Aeronautics/Sable support** (machines on contraptions, missiles launched from airships, radar tracking, ABM interception). Alternative: *HBM's Nuclear Tech: Neo Edition* (incomplete, author says "not survival ready"). | **Do not rebuild this.** Depend on it. |
| **ICBM (Voltz)** | ❌ 1.12.2 only (`BuiltBrokenModding/ICBM-2`, **MIT**). | MIT means a port is legal — but see LICENSING.md: code ≠ assets. Nobody has ported it. **This is open space.** |
| **Nuclear Science (Voltz)** | ❌ 1.6/1.7.10, no public source found. | Ideas only. **Open space.** |

### Direction update (after your feedback)

You chose to **own the mechanics outright instead of borrowing code**: reimplement what you liked
from ICBM / MineChem / Nuclear Science, and treat HBM's NTM as an optional neighbour rather than a
dependency. That's the right call for three reasons: it's legally cleaner, it keeps the tech tree
tuned to *your* server, and it means the mod stands alone instead of dying when a beta port breaks.

Concretely, the mod now contains its own **element chemistry chain** (MineChem-inspired, our code):

```
Uranium Ore → Raw Uranium → Decomposer → U-238 vials → Synthesizer → Enriched Uranium → Fission Missile
                                        → Li + H vials → Tritium Cell → Fusion Missile
                                        → U-238 + U-235 → Plutonium-239 (implosion warheads, M1)
```

Implemented in `chem/`: `Element` (19 entries), `DecompositionTable`, `SynthesisTable`, plus the
`DecomposerBlock`/`SynthesizerBlock` machines that share one block entity.

### So what's actually unique?

Two things, and they're both *interaction*, not content:

1. **The Voltz arms race loop** — silos, incoming detection, interception. NTM gives you warheads but
   its ABM/radar is *NTM-only*. Nobody has "missile vs missile" as a first-class, mod-agnostic system
   on 1.21.1 where a **Create Big Cannons** flak battery, an **Aeronautics** interceptor airship and a
   silo SAM all participate in the same defence network.
2. **Cannons and physics vehicles as delivery systems** — a Numismatics-funded war where the payload
   arrives by airship, gets shot at by CBC cannons, and the wreckage lands on a Sable contraption.

That's the mod. Not "NTM again", not "ICBM again" — the **fire-control and interception layer** that
ties the stack together.

---

## 2. Strategy: hybrid, soft-dependency first

Rules the project follows:

1. **Zero hard dependencies.** The jar must load and be playable with nothing else installed. Every
   integration is optional and detected at runtime (`util/SCGCompat`).
2. **Never copy code from NTM / ICBM.** Depend on NTM:DE through its public API; re-implement ICBM
   *mechanics* from scratch with your own art and names (see LICENSING.md).
3. **Integrations live in their own package** (`com.shadowclangaming.compat.createbigcannons`, etc.) and
   are only touched inside a `SCGCompat.x()` guard, so a missing mod can never crash the game.
4. **Data-driven payloads.** Missile stats belong in JSON datapacks, not hardcoded, so server owners
   can rebalance without recompiling. (Milestone M1.)

---

## 3. Architecture

```
                    ┌──────────────────────────────┐
                    │  Fire Control (M2/M3)        │
                    │  radar contacts, targets,    │
                    │  threat list, IFF            │
                    └───────┬──────────────┬───────┘
        reads missile index │              │ commands
                    ┌───────▼──────┐  ┌────▼─────────────┐
                    │ MissileEntity│  │ LaunchPadBlock /  │
                    │ (guided)     │  │ Silo (M1)         │
                    └───────┬──────┘  └────┬─────────────┘
        optional            │              │ optional
        ┌───────────────────▼──┐   ┌───────▼──────────────────┐
        │ NTM:DE — nuke warhead│   │ CBC — flak/cannon shells │
        │ radiation, ABM       │   │ Aeronautics — hardpoints │
        └──────────────────────┘   └──────────────────────────┘
```

Core pieces already in the project:

- **`MissileEntity`** — projectile with a target `BlockPos`, a steering tick (`current + (wanted-current)*0.15`),
  detonation on proximity/impact/timeout. It stores its own `ItemStack` in synched data so one entity
  type renders as SRBM/MRBM/ICBM.
- **`LaunchPadBlock` + `LaunchPadBlockEntity`** — one stored missile, one target, redstone trigger,
  10-second reload, comparator output, drops contents when broken.
- **`TargetDesignatorItem`** — stores a `BlockPos` in a **data component** (the 1.20.5+ replacement for
  NBT — this is the single biggest API shift since the last generation of tutorials).
- **`SCGCompat`** — mod-id checks for `create`, `aeronautics`, `createbigcannons`, `numismatics`, `hbm`.
- **`chem/Element` + tables** — the periodic table and the two conversion tables. Pure Java, no Minecraft
  registries, so it's trivially unit-testable and cheap to convert to datapacks later.
- **`ProcessorBlockEntity`** — one block entity running both chemistry machines: output slots, element
  buffer, progress, "outputs full" blocking, save/load, comparator output.

### A note on radar performance

Do **not** call `level.getEntities()` every tick per radar block. Build one server-side index
(`ServerTickEvent.Post`) that keeps a list of live missiles, rebuilt every 5–10 ticks, and have radars
query that. With Aeronautics contraptions + NTM machines on a busy server, naive entity scans are the
difference between 20 TPS and 8 TPS.

---

## 4. Milestones

Each is shippable on its own. Do them in order; each one is playable.

### M0 — scaffold ✅ (this project)
Guided missile, launch pad, designator, recipes, textures, creative tab.

### M0.5 — fire control & warheads ✅ (done in this pass)
- [x] **Remote designator**: bind to a pad (right-click pad), fire at the looked-at block
      (right-click air), 256-block range, 6,000 FE per shot, 60,000 FE buffer, RTG Pellet recharging,
      full NeoForge item-energy capability so external chargers work.
- [x] **Warhead catalogue**: decoy, EMP, HE (×3 sizes), incendiary, cluster, fission, fusion,
      antimatter, numismatic. All data lives in `WarheadType` - one enum entry per warhead.
- [x] **EMP as counter-battery**: no block damage, but pads cool down, machines stall, designators
      drain flat, players blinded. This is the first mechanic that makes the *network* matter.
- [x] **Numismatic warhead**: currency in, blast radius out, using the 1.25×-per-doubling curve
      (`Funding.java`). Works with Create: Numismatics coins or with our own War Bonds.

### M0.6 — explosion performance ✅ (done in this pass)
- [x] **`BlastScheduler`**: parallel geometry planning on a worker pool, tick-budgeted application.
- [x] **Whole-section clearing** with interior sections skipping all distance tests.
- [x] **Neighbour updates suppressed**, entity damage + knockback applied manually for scheduled blasts.
- [x] **`FxBudget`** particle scaling - the only genuine client-GPU lever.
- [x] **Documented why GPU offload / parallel block writes are impossible** (`docs/PERFORMANCE.md`).

### M0.7 — continent-scale blasts ✅ (done in this pass)
- [x] **Tiered blast paths**: vanilla ≤12, block-precise ≤64, strategic scar above that.
- [x] **`SectionScrubber`**: palette-level section clears + one relight per section (10-30x faster writes).
- [x] **`StrategicBlasts`**: persistent scar saved in world data; loaded chunks scrubbed immediately,
      unloaded chunks wiped on load via `ChunkEvent.Load`.
- [x] `Funding.MAX_RADIUS` raised to 4096, with measured cost tables in `docs/PERFORMANCE.md`.

### M1 — payloads and silos
- [ ] Warhead variants: HE, incendiary, cluster, EMP, chemical, nuclear (nuclear = huge blast + coarse
      fallout effect; if NTM:DE is present, delegate to its radiation system instead).
- [ ] `MissileType` as a **JSON datapack registry** (`data/shadowclangaming/missile_type/*.json`): speed,
      turn rate, blast power, model, warhead, cost. Use `Registry`/`Codec` — 1.21's data-driven registries
      make this clean, and it's the single best "unique server" feature: your server defines its own arsenal.
- [ ] Silo multiblock (3×3 + door) with a countdown, siren sound, smoke plume and a launch gantry.
- [ ] Chunk-loading rules: a missile must **not** force-load chunks forever. Ticket the chunks it flies
      through for a limited time, or missiles will either stall or leak.

### M2 — chemistry depth (MineChem-inspired, still our code)
- [ ] **Decomposer GUI** (menu + screen + item slots) — the current right-click loop is a prototype.
- [ ] **Per-element vial tints**: one texture, tinted by `Element.colour()` via a custom item model /
      `ItemColor`. Instant visual payoff, ~1 evening of work.
- [ ] Convert `DecompositionTable` / `SynthesisTable` to **real recipe types** (`data/*/recipe/`), so
      server owners add chemistry via datapack. This is the big one for "my server is unique".
- [ ] Machine upgrades: speed/efficiency modules, or Create-powered (rotation input) versions.
- [ ] Isotope separation as its own machine (centrifuge cascade) rather than a flat recipe — this is
      where the Nuclear Science *feel* actually comes from.
- [ ] Element side effects: fissile vials emit weak radiation; a "criticality" event if you hoard too
      many in one container. Cheap drama, big server stories.

### M2b — radar and interception (the Voltz signature)
- [ ] **Radar block**: radius, sweep animation, emits redstone/computer signal with contact count.
- [ ] **InterceptorEntity**: same base as `MissileEntity` but targets *entities*; simple lead-pursuit
      (`target.pos + target.velocity * timeToIntercept`).
- [ ] **Defence controller**: links radars → launchers, IFF by owner UUID (a player's own missiles don't
      get intercepted), threat priority, salvo size, ammo accounting.
- [ ] Balance: an ICBM should be killable by 2 interceptors, not 1, or defence is trivially dominant.

### M3 — Create Big Cannons integration
- [ ] Fire-control block that computes a **ballistic solution** (fixed-point iteration on gravity + drag)
      and aims a nearby CBC cannon — the cannon does the shooting, your mod does the maths.
- [ ] CBC shells damaging your missiles (flak): interceptors aren't the only answer.
- [ ] *Risk*: CBC's projectile/aim API is not a formal API. Check `CreateBigCannonsAPI` in their repo
      before promising this; worst case a small, well-documented mixin.

### M4 — Aeronautics / Sable integration
- [ ] Missiles launched **from a moving contraption** (bomber airships). This is where the difficulty is:
      inside a Sable sub-level you're in *local* coordinates, so a launch must convert local → world
      space including the contraption's rotation, and inherited velocity must be added to the missile's
      initial velocity or bombs will fall "backwards".
- [ ] Hardpoint block that attaches to a sub-level and holds N missiles.
- [ ] NTM:DE compat: if loaded, offer "NTM warhead" payload that calls its explosion/radiation handling
      so your nukes and theirs behave consistently (and their ABM can shoot yours down).

### M5 — Numismatics economy
- [ ] Silos, radars and interceptors have **upkeep** (paid in Numismatics coins via a chest/account).
- [ ] **War contracts**: a block/bounty that pays for verified strikes, and pays *more* for interceptions,
      so defence is a career, not a chore.
- [ ] Everything server-configurable — this is the "unique server" part: your economy numbers, your meta.

### M6 — polish
- [ ] Ponder scenes (Create's tutorial system) for the pad, radar, controller.
- [ ] JEI/EMI pages for missile stats.
- [ ] Config file (NeoForge `ModConfigSpec`): blast multipliers, interception difficulty, chunk-ticket limits.

---

## 5. Known technical risks

| Risk | Why it bites | Mitigation |
|---|---|---|
| **Sable sub-level coordinates** | Entities on contraptions live in a different coordinate frame; "target = block pos" math silently breaks. | Start with world-anchored launches only. Add `SableCompat` behind `SCGCompat.aeronautics()`, with a debug command to print both frames. |
| **CBC has no stable API** | Cannons/projectiles are internal classes. | Prefer the "aim the cannon via redstone/mechanism" route; mixin only as last resort and pin the CBC version. |
| **NTM:DE is a young port** | Not on Modrinth yet (CurseForge only, GitHub beta). API/ids may change. | Soft-dep + reflection-free guards, and don't ship a hard version range. Test with each release. |
| **Currency curve is flat** | 1.25× per doubling means cost grows far slower than area, so late-game money trivialises defence. | `Funding.COST_EXPONENT` is one line. Consider 1.5–2.0 for a live server, or gate big warheads behind *materials* (tritium, plutonium) instead of money. |
| **Radius 4096 is a server-killer by design** | 263,169 chunk columns; a scar that wide makes the world unplayable to explore and will look like corruption to players. | Keep `Funding.MAX_RADIUS` at 96-256 for a live server. 4096 exists and works, but it is a "end the map" button, not a weapon. |
| **Scars are permanent** | There is no in-game way to remove a scar; a 4096 scar wipes every chunk that loads, forever. | Add a `/shadowclangaming scar clear` operator command before you let players buy one. |
| **No drops on huge blasts** | Scheduled blasts remove blocks without drops (500k item entities would be worse than the explosion). Players may expect their stuff back. | Add a capped, merged drop table (e.g. max 512 merged stacks) if survival players complain. |
| **Floating sand/gravel after a strike** | `UPDATE_CLIENTS` skips neighbour updates. | One flag to change in `applySection`; costs a cascade, so make it opt-in if TPS matters. |
| **Antimatter at 45 power** | A single detonation is a serious TPS spike, and 96-radius numismatic blasts are worse. | Cap `MAX_RADIUS` far lower (24–32) for multiplayer; queue detonations (one per tick) in `WarheadType` before you ship. |
| **Explosion lag** | 16-power blasts × many missiles = tick spikes. | Cap concurrent detonations per tick; use `ExplosionInteraction.TNT` not block-drop mode; consider a scheduled blast queue. |
| **Entity count** | Missiles + contraptions + machines. | Radar index (above), `clientTrackingRange` tuned (12 is plenty), despawn logic, and a hard cap of live missiles per player. |
| **Chemistry tables sprawl** | 300 hand-written decomposition entries becomes unmaintainable and unbalanceable. | Keep the tables small and intentional (they're a tech tree, not a wiki). Move to datapacks at M2 and let the community balance for you. |
| **Element enum vs datapack registry** | An enum is simple but not extensible; players *will* ask for custom isotopes. | Fine for v1. Converting to a `Registry` with `Codec` is a contained refactor because all access goes through `Element` statics. |
| **Server/client desync** | Guided movement computed only on the server looks jittery if `updateInterval` is high. | Already handled: `updateInterval(2)` on the entity type. Don't be tempted to set 20. |

---

## 6. What I'd cut

Being blunt, since scope kills mods:

- **Do not reimplement NTM's reactors/chemistry.** Months of work, and NTM:DE already exists with
  Aeronautics support. The "Nuclear Science" *feel* you remember (isotopes, enrichment, meltdown fear)
  is 80% NTM gameplay — depend on it.
- **Skip ICBM's silo camouflage/defence-block list.** Pick the 3 mechanics that actually created
  drama on Voltz servers: **silos you can find and bomb, radar that warns you, missiles that get shot
  down.** Everything else is decoration.
- **Don't build a modpack from scratch.** `MODPACK.md` lists a working 1.21.1 stack you can copy.
