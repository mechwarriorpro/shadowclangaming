# ShadowClanGaming 1.0.0 — install it and test it

This is the test build you asked for. It is a **real, compiled jar** — all 231 Java files were
compiled against a genuine NeoForge 21.1.264 / Minecraft 1.21.1 classpath (no errors, 375 class files), and
all the resource files are packed in. What it has **not** done is run inside a real game client yet, so your
local test is the first launch. Expect to find things; that is what this build is for.

---

## 1. What you need

| Thing | Version | Where |
|---|---|---|
| Minecraft | **1.21.1** | the launcher |
| NeoForge | **21.1.264** (anything in the 21.1.x line works) | <https://neoforged.net/> |
| Java | **21** | <https://adoptium.net/temurin/releases/?version=21> |
| ShadowClanGaming | `ShadowClanGaming 1.0.0.jar` | the file next to this document |

Optional mods — the mod loads fine without any of them and switches the extra features on only when it finds
them:

| Optional mod | Why | Notes |
|---|---|---|
| **Create 6.0.10+**, **Create Big Cannons 5.11.x** | cannon director (`/term cannon`) | if CBC is missing the block still places, it just reports "no cannon mount found" |
| **Create: Radars** | radar data-link module | that mod's licence is custom, so it is driven purely by reflection — no compile dependency, no bundled code |
| **Numismatics** | currency warheads, Bank Card payments | without it, funding is item-load only |
| **AE2** | Storage Bridge | reflective; the bridge card simply reports "no AE2" |
| **ProjectE**, **Create: Aeronautics + Sable**, **Dynamic Electricity** | EMC, ships/subgrids, FE conversion | all optional |

Anything not installed is *detected at runtime*, never hard-required.

---

## 2. Install — step by step

1. **Install NeoForge 1.21.1.** Run the NeoForge installer, choose *Install client* (and *Install server* if you
   are also setting up the server), point it at your Minecraft 1.21.1 installation. Launch the game once from
   the new "neoforge" profile so it creates its folders, then quit.
2. **Open the mods folder.** In the Minecraft launcher: *Installations → neoforge → the folder icon* lands you
   in the instance folder; the folder you want is `mods`. If it does not exist, create it. (Windows:
   `%APPDATA%\.minecraft\mods`. Linux: `~/.minecraft/mods`. macOS: `~/Library/Application Support/minecraft/mods`.)
   If you use CurseForge/Prism/MultiMC, use that launcher's *Open folder → mods* instead.
3. **Copy `ShadowClanGaming 1.0.0.jar` into `mods`.** Keep the space in the name — it does not matter to Java,
   only that the file ends in `.jar`.
4. *(Optional)* drop the optional mods listed above into the same folder. Use the 1.21.1 NeoForge builds only.
5. **Start the game.** *Mods* on the title screen should list **ShadowClanGaming 1.0.0**. If the game crashes
   before that, send me `logs/latest.log` — the first `Caused by:` line is the one that matters.
6. **Make a test world** (creative, cheats on) and open the **ShadowClanGaming** creative tab.

For a **server**: the jar goes in the server's `mods` folder, and every connecting client needs the same jar
(the mod adds blocks and items, so it is not client-optional). Nothing else to configure.

---

## 3. Test checklist — the shortest path through every system

Work down this list; each line is one feature worth confirming. Anything that misbehaves, note the line
number and the log.

**Materials & machines**
1. Creative tab → ore, ingots, 19 vials, machines all present and named (no `item.shadowclangaming.…` raw keys).
2. **Decomposer** + coal → wait ~3 s → right-click empty hand → carbon vials.
3. **Synthesizer** + vials → compound out. Feed the wrong thing and it should refuse, not void items.

**Power & money**
4. **Auto-sell block**: FE in, money out at 100 FE = $1; input scales with the *cube*.
5. **Universal cable**: connects machines, shows throughput, and never truncates past 2.1 G — it is `long`.
6. **Bank Card**: pay a pad from the card; the pad's funding goes up by the exact amount.

**Warheads & launch**
7. **Launch Pad** + a **Designator**: right-click the *pad* to bind, right-click *air* to fire; each shot costs energy.
8. Cycle warheads from the decoy (zero damage) through EMP to antimatter. Radius uncapped; 4096 = **256 Suns** worth of Numismatic coin.
9. Load currency **and** items into the pad; both must raise the radius.
10. Big radius (try 512+) → the crater arrives chunk-by-chunk over a second. That is the multithreaded,
    budgeted blast, not lag.

**Magic**
11. **Mana Capacity** enchant exists; mana does **not** naturally regenerate.
12. **Inscription Table** — the spell is a *sequence of steps* now:
    - press **ADD STEP** on a shape (Self / Touch / Bolt / Beam / Aura / Zone / Rune);
    - click a step row so it is highlighted, then click components — they land in **that** step, and the
      row shows them by name, so you can see the draft you are building;
    - the cost panel reports mana, **capacity demand**, and your own mana / capacity;
    - **INSCRIBE** while holding the rifle (mana, full price) or a spell warhead (money copy).
    There are no spell levels any more: a spell fits if your **mana capacity** can hold one cast of it.
13. **A worked sequence** — Bolt + fire damage, then Zone, then Rune + ignite. Fire at a wall: the bolt
    flies, the impact opens a 30-second zone that **re-casts the rest of the spell from the zone's own
    position every 5 seconds** (six pulses), and each pulse arms a rune that ignites the first thing that
    walks into it.
    Then build a rifle with several Emitters and cast the same spell: the zone lasts **1 s longer per
    power level** and re-casts **0.25 s sooner per level** — a 27-emitter build leaves a 56-second zone
    pulsing every second.
14. **The five new effects** (all in the component list, pages 4–5):
    - **Random Teleport** (tier 3) — hit a zombie with it; it vanishes and reappears up to 16 blocks away,
      never inside stone.
    - **Cluster Strike** (tier 4, in a **Bolt** step) — fire into a group: the bolt splits into 5 homing
      bolts, each delivering the rest of the spell. With nothing else in the step, each sub-bolt lands
      its own components again.
    - **Chronosplit** (tier 5) — the target freezes for 15 s: it cannot move, break, place, eat, shoot,
      attack or drop anything. Try it on yourself: you cannot break a block until the timer ends.
    - **Firestorm** (tier 4) — a burning area, 6 s, sets the ground alight (and can burn dropped loot).
    - **Hellstorm** (tier 5) — bigger, more damage per pulse, ignores i-frames, and leaves dropped items
      untouched.
    - **Starstrike / Entangle / Chain Lightning / Banish / Sanctuary** and 25 more — the component list is
      now **76 entries**, shown in a scrolling viewport (wheel, page buttons, or drag the scrollbar). The
      cost panel is pinned to the bottom of the window, so it never scrolls off no matter how long the
      spell is.
    - **Overlord / Konosuba batch** — Grasp Heart (executes below 30 % health), Fallen Down, Black Hole,
      Negative Burst, Cry of the Banshee, Hell Flame, Magic Arrow, Flash, Explosion, Turn Undead, God
      Requiem, Purification, Cursed Crystal Prison, Drain Touch, Create Water, Wind Blast, Lurk. The
      component list is now **93 entries**.
    - **Modifiers** — four rows under the spell list: EMPOWERED, WIDE, PIERCING, VAMPIRIC, TWINNED, SWIFT,
      LASTING, SILENT, **MAXIMIZE**, **TRIPLET**, **BOOST**, **DELAY**, **CHANT**. They apply to the
      *selected step* and stack into its cost. Try TRIPLET + MAXIMIZE on a Starstrike, or DELAY on a
      Firestorm to drop it on somebody two seconds after the shot. Volleys now CONVERGE on the point you
      are aiming at, and there are fifteen more modifiers under them: Seeker, Echo, Siphon, Bastion,
      Execute, Cataclysm, Eternal, Legion, Epicentre, Cripple, Dispel, Nova, Hex, Bind and Blood Price
      (the only one that costs health instead of mana).
    - **Rifle bench, rebuilt (round 17)** — the blueprint table is now ONE block with a GUI: drop blocks
      into 27 slots, pick **barrels 1..128** and **strength 1..128**, press **CRAFT**. Materials still set
      tier and efficiency; the dials set rate of fire, damage and mana cost. No corner marking any more.
    - **Spell blueprints (round 18)** — inscribe onto a **book and quill** and the table gives you a
      **Spell Blueprint** item. Load it into a spell rifle or a spell warhead (sneak + right-click with
      that item in your other hand), or right-click the page to cast the spell yourself.
    - **Cargo GUI + Beef Supreme (round 23)** — the cargo missile's hold is now a **chest GUI** (27 slots,
      shift-click both ways) instead of an offhand gesture, which also removes the "inserted items became
      air" bug: empty slots are never persisted. **Beef Supreme** missile: a cow with a boss bar, 5000
      health, 30 damage, and a ram it telegraphs for 5 s with dirt and flame under its feet before
      charging at 1.15 blocks/tick. It steps up 2.5-block lips while charging, and on death it explodes
      and leaves three stacks of steak.
    - **Cargo-only inventory + Violator buff (round 22)** — only the **cargo** missile has an inventory
      now; constructor/spatial read the launch pad's hold. The **Violator** got 64-block range,
      eight-skull shotgun volleys every 0.25 s, and up to eight simultaneous targets.
    - **Recipe-based shop prices (round 21)** — the `/servershop` modded tab no longer prices everything
      at $21. `compat/economy/RecipePricer` walks every recipe the server loaded (any mod's machine
      recipes included) and `RecipePricing` resolves the cheapest recipe per unit plus labour, recursing
      to raw materials. Existing flat $21s are migrated automatically on the next start; hand-edited
      prices are never touched. `/shadowclangaming economy explain <item>` prints where a price came
      from. See `docs/ECONOMY.md` §2–3.
    - **New warheads (round 20)** — **Constructor** (fills a sphere from loaded blocks, nearest perfect
      sphere, excess dropped, noise-blended), **Spatial** (loads the hit area into an AE2 spatial cell),
      **Mother Clucker** (50 turret chickens whose eggs hatch more turret chickens) and **Violator**
      (wither with 3x health and 5x skulls, bursting 5 s in every 30). Cargo/Constructor/Spatial/Clucker
      missiles are now loaded by **right-clicking the missile with items**, and slider-radius warheads
      get a **slider + exact entry** in the pad instead of a computed max. See `docs/WARHEADS.md`.
    - **Bank Card fix (round 19)** — the card no longer says "EconomyCraft API unavailable" on a
      Numismatics server. `compat/economy/Money.java` picks a backend: EconomyCraft if installed, else
      Create: Numismatics' bank (`compat/numismatics/NumismaticsBank.java`, reflective, verified against
      their 1.21.1 source: `Numismatics.BANK` → `getAccount` → `getBalance`/`deposit`/`deduct`).
      A **Numismatics Bank Card** right-clicked on a launch pad also funds the warhead now, billing the
      account the card is bound to (a shared Blaze Banker account pays, not the holder's pocket).
      The Auto-Trader and inscription fees use the same facade, so selling and inscribing pay into
      whichever bank you actually run.
    - **Cable fix (round 19)** — network cables now render and have a hitbox. Two bugs: the models used
      an invalid `"all"` face key (a model that fails to bake renders as nothing), and a 4x4 px element
      with no `uv` sampled the whole mostly-transparent texture. Both cables are regenerated by
      `tools/gen_cable_assets.py`. Cables also connect on placement now (the old test needed the block
      entity, which does not exist yet at that moment) and their cross is a real collision shape.
    - **Radar bridge fix (round 19)** — the Create: Radars integration no longer relies on six guessed
      method names. `radar/TrackScanner.java` finds track lists by shape (any no-arg accessor or field
      whose name looks like tracks, wrapper types drilled into, saved NBT as a last resort), so their
      renames cost nothing. `/term radar bridge verbose` prints what it found.
    - **Search and naming (round 19)** — the table has a **Search** box that filters the active bookmark and
      a **Name** box (with Reset Name) that names the spell. The name travels with the spell onto the
      blueprint, the rifle and the warhead, and shows on their tooltips.
    - **Inscription screen (round 19)** — one flat strip of parts, AM2 style: shape chips are deliveries
      and every chip after one belongs to it. No eight-slot shape budget; drop as many shapes and effects
      as you like, in any order. Three bookmarks (SHAPE / COMPONENT / MODIFIER) over an icon grid; click
      to append, drag to insert, right-click to delete.
    - **Spell crafting is drag and drop (round 17)** — drag palette rows onto step cards, drag chips to
      reorder or move between cards, right-click to delete. No component limit: the pinned cost panel and
      your mana capacity are the only gate. See `docs/MAGIC.md` §3b.
    - **Crash fix (round 18)** — `ConcurrentModificationException` in `SpellZones.tick` when a zone's own
      spell created another zone or rune. `SpellZones`, `SpellStorms` and `SpellDelays` now tick by index
      and mutate in place.
    - **Mana QoL commands** — `/shadowclangaming mana <player> add|remove|set|reset <n>` (op 3), and
      every one of them also runs on yourself with the player left out (`/shadowclangaming mana set 5000`).
      The number you type is MAXIMUM MANA as the HUD shows it, with the 100 base and Mana Capacity gear
      already folded in.
    - **Bad Gas** (tier 3, 225 mana) — a stink that throws. On hit the victim is launched upward AND along
      the direction *it* is facing, so whatever was charging you sails over your shoulder. With
      **Sable: Ragdolls** installed the body becomes a real physics ragdoll (reflective bridge, nothing
      bundled); without it the mob loses its AI for three seconds and tumbles. Nausea and hunger included.
15. **Blueprint Table** — stand *beside* it, point at one corner of where the gun should be, right-click
    (corner A), point at the opposite corner, right-click (corner B — the box size and volume print in
    chat), then sneak + right-click to build. Clicking again moves corner B; sneak + right-click with
    only corner A set clears the box. The marking ray ignores the table itself, so pointing "through"
    it works; both corners can never land on the same block, so a 1×1×1 box can no longer happen.
16. **Spell Rifle** from the build above. **Blood pact**: witch + baby villager + cat, full moon, totem in
    offhand. **Creeping Evil**: hidden debuff, necklace freezes it, holy water + necklace at midday cures it.

**Launcher inventory**
17. The **launch pad** now has a **16-slot feed inventory**. Point a hopper (or an AE2 import bus, or any
    other IItemHandler pipe) at the pad and it accepts **currency** — War Bonds, or Numismatic coins if
    that mod is installed. Everything in it is converted into warhead funding once a second, so a pad can
    be funded automatically and its radius grows on its own. The pad screen shows `feed n / 16 slots`, and
    `feed` on the launch console reports the value sitting in it. Nothing converts until a missile is
    loaded, so you can stock a pad in advance.

**Computers (the deep end — this build bundles the full Lua stack)**
18. Place a **Terminal**, open it, type `help`. Then `/term prog list`, `/term panel`.
19. Compute tiers: install CPU and GPU parts, watch `/term` report the budget go up.
20. **Network rack** + **orbital node**: register parts in the rack, then from the central computer
    `/term node list` and `/term node exec <node> <command>` — the subcommand runs *on the node*.
21. **Radar**: `/term radar` — search radius starts at 256 and grows 128 per dish up to **4096**, tracks up to
    128 contacts. Try `/term radar create` to drop a track, and the Create: Radars data-link module if you have it.
22. **Cannon director**: `/term cannon arm | aim | fire | engage | status` next to a CBC cannon mount.
23. **Lua**: `lua` from the terminal, `print(1+1)`, a `for` loop, `os.time()`, and a program saved with `edit`.

---

## 4. If you need to rebuild it yourself

On a normal machine (8 GB+ RAM is plenty):

```bash
cd voltz_reborn
chmod +x gradlew
./gradlew build          # → build/libs/ShadowClanGaming-1.0.0.jar
```

The first run downloads and decompiles Minecraft — 5–15 minutes, once.

`./gradlew runClient` starts a dev client with the mod already loaded — that is the fastest way to test a change.

A note for whoever rebuilds this: the hand-rolled toolchain recipe (JDK, NFRT, binary patches,
classpath) is written down in `docs/REBUILD.md`, because the Gradle path cannot run
a 1 GB box - vineflower hardcodes a 4 GB heap.

### The fallback that built this jar

The machine that produced `ShadowClanGaming 1.0.0.jar` could not run Gradle's decompile step (it is memory
hungry: the decompiler JVM is launched with `-Xmx4G` and was killed). So the jar was assembled by hand against
a real Minecraft/NeoForge classpath instead of the Gradle IDE setup:

```bash
# 1. NeoForge's binary-patched, human-named Minecraft jar (the classpath that matters)
java -jar binarypatcher.jar --clean <renamed-mc.jar> --output /tmp/patched.jar --apply joined.lzma

# 2. compile every source file against it
CP="/tmp/patched.jar:<renamed-mc.jar>:/tmp/neoforge-universal.jar:<all jars under ~/.gradle/caches/modules-2>"
javac -d /tmp/buildout -cp "$CP" $(find src/main/java -name '*.java')

# 3. add the resources and zip
cp -r src/main/resources/* /tmp/buildout/
jar --create --file build/libs/ShadowClanGaming-1.0.0.jar -C /tmp/buildout .
```

That is exactly what was done here: **0 compile errors, 352 class files, 853 jar entries, 635 lang keys.**
You should not need this path — it exists so you know the jar you are testing is genuinely compiled code and
not a hand-made archive.

---

## 5. Checks that need no game

```bash
python3 tools/sweep.py              # brackets, JSON validity, asset/model references, lang keys, forbidden calls
python3 tools/check_names.py        # nothing old-identity left, ids ↔ translations, jar name, version agreement
bash tools/check_syntax.sh          # parse-only pass over all 171 files
bash tools/run_logic_harness.sh     # 90 assertions: funding maths, radar tracks, cannon ballistics, compute budgets
bash tools/run_lua_harness.sh       # 40 assertions: the Lua interpreter against real programs
bash tools/run_api_harness.sh       # 51 assertions: scheduler, GPU text buffer, component model
```

The three harness scripts find a JDK 21+ automatically (`tools/jdk.sh`); override with
`SCG_JDK=/path/to/jdk-21` if yours lives somewhere unusual.

---

## 6. What is *not* verified

Being straight with you, so you know what you are testing:

- **No launch has happened.** The jar compiles against the real API and every resource reference resolves, but
  registries, block entities and screens have never been instantiated in a live game. First-launch crashes are
  possible and are exactly what this build is meant to shake out.
- **Client screens** (inscription, terminal, panels) are the least-tested surface — if one is blank, that is a
  screen-class bug, not a data bug.
- **Reflective integrations** (CBC, Create: Radars, AE2) are written against the versions pinned in
  `MODPACK.md` and degrade to "not found" messages rather than crashing when the API moved.
- **Balance numbers** are the design targets in `docs/ECONOMY.md` — expect to want them tuned after play.

Send me any crash log or a one-line description of what a block did instead of what it should have, and I will
turn it around in the next build.


---

# Round 28 — missile modules T1–T5, riding, and new launcher models

**Five missile modules.** `missile_module_t1` … `missile_module_t5`. Each is an empty airframe with a
range tier — **T1 10,000 blocks, T2 25,000, T3 50,000, T4 150,000, T5 unlimited** — and **any warhead fits
any module**. Right-click a module with a missile in your other hand to move that missile's warhead onto
the frame; sneak + empty hand to pull it back out. Old plain missiles are treated as T5, so nothing you
already built changes.

**Riding.** Sneak + empty hand on a *loaded* pad boards the missile; sneak + empty hand climbs off. On
detonation the rider is thrown clear (+12 Y, slow falling 400, fire resistance 200) **before** the
warhead goes off.

**Empty modules fire.** A module with nothing fitted launches as a **dud**: cloud particles, a clunk, a
broadcast message, no damage, no terrain.

**New models.** The launcher pad, launcher panel and support rod are no longer textured cubes. New
geometry, new 16×16 textures, ICBM's shape language (raised rim, hold-down clamps, hazard banding,
slanted readout hood, trussed column) drawn from scratch — see `docs/WARHEADS.md` for the shape-by-shape
breakdown.

**Numbers this round:** 355 classes, 876 jar entries, 660 lang keys, 82 recipes, harness **244
assertions**, api 51, lua 40, NAMES PASS, SYNTAX PASS (221 files).


---

# Round 29 — seventeen apex warheads and their shaders

**The roster above antimatter.** World Eater, Stellar Requiem, Reality Breaker, Spacetime Hammer,
Universal Imploder, Ragnarok, World Rune, Abyssal Covenant, Soul Furnace, Arcane Singularity, Quasar Lance,
Starforge, Big Bang Seed, Reality Corruptor, Absolute Zero, Omega, Absolute Annihilation. Every one is
crafted from an **antimatter missile** plus a themed ring of materials, and every one is a **staged
detonation**: a visible charge, a destruction front that travels (inward for the hole weapons, outward for
the shockwave weapons, static for the rune/corruptor weapons), and a release.

**Eight custom post-processing shaders** ship with them — real GLSL, loaded as vanilla `PostChain`s, no
shader pack required: lensing, flash, rift, shock, blood, void, runes, soul. They are graded by distance
and by the weapon's own timeline, and a chain that fails to compile is disabled with one log line rather
than taking the explosion with it.

Full detail, weapon by weapon, plus the shader table and the performance knobs: **`docs/APEX.md`**.

**Numbers this round:** 231 Java files, **375 classes**, 980 jar entries, **1,122,652 bytes**, logic
harness **353 assertions** (was 244), api 51, lua 40, 99 recipes, 716 lang keys.


---

# Round 29b — constructor missile gets a chest GUI and a radius slider

Right-click a **constructor missile** and you now get a **27-slot chest** (same layout as the cargo
missile, titled "Constructor Payload") with a **radius slider underneath it**: drag it, type an exact
number, or press **auto** to derive the size from the payload. The readout tells you what the radius costs
in blocks and stacks. The grid only accepts blocks (and AE2 item cells), because that is all a constructor
can place. Everything the launch pad did before still works, and the missile's own radius now wins over the
pad's slider.

Details in `docs/WARHEADS.md` (Round 29b). Harness is now **368 assertions**.


---

# Round 30 — slider fix, silo inventory, spatial cell drive

- **Sliders now work.** They were hand-drawn bars with hit boxes somewhere else; the constructor's also
  read a value that never synced from the server. Both are now a real `RadiusSlider` widget plus a menu
  data slot. Radius 0 = "auto".
- **Silo inventory is reachable:** *shift + right-click the pad with an empty hand* opens a 27-slot chest
  over the pad's cargo, which hoppers and AE2 have always been able to fill but players could not open.
- **Spatial missile has an AE2-style cell drive:** right-click it for 10 cell bays that accept only
  spatial storage cells, with per-bay status pips (red = AE2 not installed).
- **Riding is a "Board" button** on the launch console — the old sneak + empty-hand gesture could never
  fire, because an empty hand goes to `useWithoutItem`, not `useItemOn`.

Suites: logic 368, api 51, lua 40, sweep, NAMES, SYNTAX (236 files) all PASS.

## Round 31

**Folder:** `/home/user/voltz_reborn` · **Jar:** `/home/user/ShadowClanGaming 1.0.0.jar`
(1,156,828 bytes, 999 entries, 394 classes) · NeoForge 21.1.264 / MC 1.21.1 / Java 21.

What changed, in build terms:

- **New file** `launcher/SiloStructure.java` — pure Java, no Minecraft imports, so it is staged by
  `tools/run_logic_harness.sh` and tested without a game jar. Pattern, panel slots, `Probe`, `Report`.
- `block/LaunchPadBlockEntity` — implements the probe, caches a `Report` (refreshed once a second from
  `tickServer()`), docks a real `MissileEntity` (`spawnDocked` / `discardDocked` / `docked` / `syncDocked`),
  persists `DockedMissile`, and gates `fireAt(...)` behind `structureComplete()`.
- `block/LaunchPadBlock` — ticker also calls `pad.tickServer()`, which keeps the docked entity, the cached
  structure report and the frequency registration current.
- `entity/MissileEntity` — `docked` / `dockPos`, docked early-return in `tick()`, `dock`, `undockAndLaunch`.
- `item/TargetDesignatorItem` — stores `FREQUENCY`, sends `ScgNet.DesignatorFirePayload`, no coordinates.
- `network/ScgNet` — new `playToServer` payload + handler that resolves the channel to its pads.
- `client/MissileRenderer` — docked pose.
- Assets: `textures/item/missile_module_t1..t5.png` (these were missing entirely).
- Lang: 729 keys.

Rebuild:

```bash
bash tools/bootstrap_toolchain.sh
cd /home/user/.cache/scg && CP=$(cat cp.txt) \
  && rm -rf /tmp/buildout /tmp/jarout && mkdir -p /tmp/buildout /tmp/jarout \
  && jdk/bin/javac -nowarn -d /tmp/buildout -cp "$CP" \
       $(find /home/user/voltz_reborn/src/main/java -name '*.java') \
  && cp -r /home/user/voltz_reborn/src/main/resources/* /tmp/jarout/ \
  && cp -r /tmp/buildout/* /tmp/jarout/ \
  && cd /tmp/jarout && jdk/bin/jar --create --file "/home/user/ShadowClanGaming 1.0.0.jar" .
```

Test without launching the game:

```bash
bash tools/run_logic_harness.sh   # 392 assertions, includes the silo shape/refusal tests
```

### Round 31a — hotfix

**Jar:** `/home/user/ShadowClanGaming 1.0.0.jar` (1,156,828 bytes, 999 entries, 394 classes).

- `registry/SCGEntities` — the turret chicken was registered without an `EntityAttributeCreationEvent`
  entry, so `new TurretChickenEntity(...)` NPE'd in `LivingEntity`'s constructor and crashed the server on
  every Clucker warhead impact. Fixed with `event.put(TURRET_CHICKEN.get(), Chicken.createAttributes()
  .build())`.
- `tools/sweep.py` — new `check_entities()`: every registered entity whose factory builds a `LivingEntity`
  subclass must have an `event.put(...)` attribute entry. The sweep now fails on this class of bug.

### Round 31b — hotfix

**Jar:** `/home/user/ShadowClanGaming 1.0.0.jar` (1,156,828 bytes, 999 entries, 394 classes).

- `entity/TurretChickenEntity#shootAt` cleared the egg's item stack; vanilla's client-side break effect
  builds an `ItemParticleOption` from it and throws on an empty stack, crashing the game. The egg now
  carries `Items.EGG`, as vanilla's does, and the `TurretEggEntity` constructors set it too.
- `tools/sweep.py` — new `check_particles()`: `setItem(ItemStack.EMPTY)` anywhere is now a build failure.

### Round 31c

**Jar:** `/home/user/ShadowClanGaming 1.0.0.jar` (1,156,828 bytes, 999 entries, 394 classes).

- `entity/TurretEggEntity` — `DAMAGE = 5.0F` (2.5 hearts) per hit via an `onHitEntity` override that
  skips vanilla's zero-damage `hurt` call and ignores its own owner.

### Round 32

**Jar:** `/home/user/ShadowClanGaming 1.0.0.jar` (1,172,024 bytes, 1,016 entries, 399 classes).

New files:

- `launcher/ModuleKind.java` — the five frames as data (speed multiplier, sub-munition count, transport
  flag, cluster spread/power constants). Pure Java, so the harness can prove the balance relationships.
- `entity/ChickenTactics.java` — the chicken decision table (sight 24, fire 16, keep-distance 5, memory
  100 ticks, re-path 15, stare 35°/tick). Pure Java, tested.

Changed:

- `entity/TurretChickenEntity` — adds `AcquireGoal` (1), `StareGoal` (3), `ChaseGoal` (4); `ShootGoal`
  (5) now fires at the mark. Mark held across ticks with a last-seen stamp.
- `entity/MissileEntity` — `ModuleKind kind` field read from the stack on spawn and on launch (NBT key
  `Module`); homing re-acquisition, cluster split, transport delivery, per-frame speed scaling; new
  `seek` / `splitCluster` / `deliver` / `landingSpot` helpers.
- `item/MissileModuleItem` — carries a `ModuleKind`, `kindOf(Stack)`, kind tooltip lines, and refuses a
  warhead on a transport frame.
- `registry/SCGItems` + `SCGCreativeTabs` — the four new items; `block/LaunchPadBlockEntity` re-points the
  docked entity at the pad's stack before launch so the frame is always the one in the silo.
- Assets: four new 16×16 sprites + item models + shaped recipes.

### Round 33

**Jar:** `/home/user/ShadowClanGaming 1.0.0.jar` (1,192,628 bytes, 1,064 entries, 399 classes).

- `registry/SCGItems` — the four special frames are now families of five (`registerModuleKind(prefix, kind)`
  → `missile_module_<kind>_t1..t5`), exposed as `MISSILE_MODULE_SPECIALS` for the creative tab. The old
  single-tier ids (`missile_module_homing` etc.) are gone; use `_t1 ... _t5`.
- `launcher/ModuleKind` — `allowsWarhead(String id)` / `carriesCargo()`: transport takes none, cluster
  refuses cargo (six landings would duplicate the hold), everything else fits everything.
- `item/MissileModuleItem` — refuses a warhead the frame will not carry, with the reason.
- Assets/recipes: 20 new sprites + models + shaped recipes (standard frame of the same tier + the kind's
  signature ingredient).
- Tools: `sweep.py` and `check_names.py` now expand loop-registered id families, and only match a literal
  id when it is the whole first argument.

### Round 34

**Jar:** `/home/user/ShadowClanGaming 1.0.0.jar` (1,206,667 bytes, 1,080 entries, 404 classes).

New files:

- `warhead/KamikazePlan.java` — every number for both items (tier cap 2, salvo cap 12, aim scatter, throw
  velocity, TNT fuse), pure Java and harness-tested.
- `warhead/Kamikaze.java` — the death routine (blast → missiles → thrown explosives) and the
  `LivingDeathEvent` hook, which only fires when the vest is in the **chest slot**.
- `warhead/KamikazeTags.java` + `data/c/tags/item/explosives.json` — the common `c:explosives` tag
  (TNT, TNT minecart, end crystal) so other mods' explosives count too.
- `item/KamikazeVestItem.java`, `item/HandLauncherItem.java`.
- `registry/SCGArmorMaterials.KAMIKAZE` — leather-grade on purpose.

Changed: `SCGItems` (two items), `SCGCreativeTabs`, `ShadowClanGaming` (death listener),
assets (two sprites, armour layer `kamikaze_layer_1.png`, two recipes), lang (777 keys).

### Round 35

**Jar:** `/home/user/ShadowClanGaming 1.0.0.jar` (1,399,753 bytes, 1,551 entries, 417 classes).

New Java:

- `warhead/ExplosivePackaging.java` — `Form {BLOCK, GRENADE, MINECART}` with prefix / power scale / fuse /
  impact flag, and `packable()`. No Minecraft imports, harness-tested.
- `warhead/BarragePlan.java` — the carpet spacing proof and centre-out ring walk.
- `computer/BarragePlanner.java` — launcher records, the grid, the round-robin, shortfall/surplus maths.
- `block/ExplosiveBlock.java` — primed state, redstone/right-click trigger, scheduled fuse.
- `item/ExplosiveGrenadeItem.java`, `entity/GrenadeEntity.java` — thrown warheads.
- `item/WarheadMinecartItem.java`, `entity/WarheadMinecart.java` — impact-fused warhead carts.
- `src/main/resources/assets/shadowclangaming/terminal/barrage.lua` — the example program's maths in Lua.

Changed:

- `SCGBlocks.EXPLOSIVE_BLOCKS` — loop-registers `explosive_<warhead>` for the whole roster.
- `SCGItems.EXPLOSIVE_GRENADES` / `EXPLOSIVE_MINECARTS` / `EXPLOSIVE_BLOCK_ITEMS` — loop-registered.
- `SCGEntities` (+`grenade`, +`explosive_minecart`), `SCGClient` (thrown-item + minecart renderers),
  `SCGDataComponents` (+`vest_payload`), `SCGCreativeTabs` (three new loops).
- `warhead/Kamikaze` — `payloadOf()` / `load()`; the blast now uses the loaded warhead and scales the
  clearing radius. `item/KamikazeVestItem` — loading, clearing, payload tooltip.
- `computer/TerminalShell` — `barrage` and `script` commands, help lines. `computer/ProgramStore` —
  `EXAMPLES` + `installExamples()`.
- `lua/Stdlib` — `%%` no longer consumes an argument (found by the example program).
- Tools: `tools/gen_explosive_packaging.py` (new generator: 114 textures, 111 models, 38 blockstates,
  38 loot tables, 111 recipes, lang), `sweep.py` and `check_names.py` taught the packaged families.

Rebuild order:

    python3 tools/gen_explosive_packaging.py     # only when WarheadType's roster changes
    bash tools/run_logic_harness.sh              # 504 assertions
    bash tools/run_api_harness.sh                # 51
    bash tools/run_lua_harness.sh                # 41
    python3 tools/sweep.py && python3 tools/check_names.py
    bash tools/check_syntax.sh

### Round 36

**Jar:** `/home/user/ShadowClanGaming 1.0.0.jar` (1,407,976 bytes, 1,554 entries, 420 classes).

New Java:

- `computer/Channels.java` — signed channel rules: `NONE` sentinel, ±999,999 range, `sanitise` / `parse` /
  `step` / `describe`. The fix for "tuned to -193 but the designator says untuned".
- `warhead/SplitRule.java` — when a cluster warhead opens (altitude vs proximity), pure Java.
- `client/gui/ConsoleLayout.java` — rectangles for both console screens, overlap-proven by the harness.

Changed:

- `item/TargetDesignatorItem` — presence-based `hasChannel`, plus tuning from a cable or terminal.
- `item/FrequencyChipItem` — signed channels, +1 / sneak -1 stepping, no `floorMod` fold.
- `block/LaunchPadBlockEntity` — `Channels.NONE` default, `hasChannel()`, `split` command, split + channel
  in `status`, data slot 20 for the split altitude.
- `block/LaunchPadMenu` (slot 20, `DATA_SIZE` 21), `block/ControlPanelMenu` (pad channel slot 14),
  `block/ControlPanelBlockEntity` (forwards `freq` / `split` to its pad).
- `computer/NetworkCableBlockEntity`, `computer/NetworkCableBlock`, `computer/TerminalShell`,
  `computer/TerminalBlockEntity` — signed channels throughout.
- `entity/MissileEntity` — splits on the altitude rule when one is set.
- `warhead/MissilePayload` — `splitAltitudeOf` / `setSplitAltitude` / `NO_SPLIT_ALTITUDE`.
- `registry/SCGDataComponents` — `split_altitude`.
- `client/LaunchPadScreen` — rebuilt on `ConsoleLayout` (320x180) with channel / split / target X-Y-Z fields;
  `client/ControlPanelScreen` — rebuilt on `ConsoleLayout` (300x168), dead branch removed, pad readout added.
- `tools/run_logic_harness.sh` — stages `Channels`, `SplitRule` and `ConsoleLayout`.

### Round 37

New Java:

- `config/SCGConfig.java` — client + common `ModConfigSpec`, with defensive accessors.
- `warhead/BlastStyle.java` — nine styles, warhead→style mapping, duration curve (pure Java).
- `warhead/BlastFx.java` — `emit(ServerLevel, WarheadType, Vec3, power)`, called by `WarheadType.detonate`.
- `client/ClientBlastQueue.java`, `client/BlastShaderFx.java`, `client/BlastOverlay.java`,
  `client/BlastClientEvents.java` — the client half: queue, post chains with fallback, HUD fallback, shake.
- `tools/gen_blast_shaders.py` — writes 9 × (post effect + program + GLSL).

Changed:

- `network/ScgNet` — `BlastFxPayload` + `sendBlastFx` (`PacketDistributor.sendToPlayersNear`) + handler.
- `warhead/WarheadType` — one `BlastFx.emit` call covers every delivery form.
- `block/LaunchPadBlock` — the designator branch delegates to the item instead of binding coordinates.
- `block/LaunchPadBlockEntity` — `setFrequency` binds the registry immediately and unbinds the old channel.
- `item/TargetDesignatorItem` — adopts the designator's channel onto a channel-less pad.
- `network/ScgNet.handleDesignatorFire` — fires at the designator's target instead of requiring a pre-set one.
- `client/ApexClientEvents` — draws the blast chain after the apex chain, and only when apex is idle.
- `client/SCGClient` — drops all post chains on resource reload.
- `command/SCGCommands` — `/scg fx status`, `/scg fx test <style>`.
- `perf/FxBudget` — `particle_scale` from config multiplies the server profile.
- `tools/sweep.py` — `check_shaders()`: every style must have all three files, valid JSON, and the uniforms
  the Java sets.

### Round 38

- `entity/MissileEntity.splitCluster` now sets a persisted one-shot guard before creating children and discards the cluster carrier after spawning. The parent previously remained alive at the split point and spawned a fresh salvo every tick.

### Round 39 — crash-log fixes

- `client/ApexShaderFx` now prefixes `Apex.shader()` suffixes with `apex_` when resolving post-effect JSON.
  `tools/sweep.py` checks all apex suffixes map to existing resources.
- `perf/BlastScheduler` handles submit-vs-shutdown races with a vanilla fallback instead of allowing
  `RejectedExecutionException` to crash the server; `ShadowClanGaming` clears staged apex work at stop and
  reopens the worker at the next server start.

### Round 40 — 1.21.1 shader program resources

- All post-program `vertex`/`fragment` ids are fully namespaced (`shadowclangaming:post/...`). In 1.21.1 an
  unqualified shader id resolves under `minecraft`, not the effect's namespace.
- Vertex/fragment source files are present under `assets/shadowclangaming/shaders/program/post/`, the folder
  `EffectInstance` resolves. The blast generator writes there, and `tools/sweep.py` checks every program ref.

### Round 41 — shader visibility and timing

- Corrected all reversed-edge GLSL `smoothstep` calls (undefined GLSL behavior, observed as black frames), and
  softened the apex rift/void dark grades.
- `ApexShaderFx.strengthOf` now applies a short attack and a per-apex lifetime tail, capped below full-screen
  blackout. The queue lifetime remains each apex's own `totalTicks()`.
- `tools/sweep.py` rejects reversed numeric `smoothstep` edges in all program fragments.

### Round 42 — signature shader motifs and debug log

- `tools/gen_blast_shaders.py` emits distinct motif code per style (shock ring, embers, radiation pulse, EMP
  scanline, toxic crawl, frost crystal, void inversion, coin sparkle, fallback ring), not only a palette change.
- Apex shader strength has timeline-specific ramp/fade and a 0.68 cap; reversed `smoothstep` edges were
  normalized. `tools/sweep.py` checks all shader programs/source paths and reversed-edge calls.
- Added `debug/ShadowDebug.java`, which writes `logs/shadow_debug.log` with bounded 4 MiB rotation and
  reproduction-focused events: warhead/apex, cluster split, FX receive, chain load/failure, blast planning.

### Round 43 — no more default black framebuffer

- Added `screen_effects.post_chains_enabled`, default `false`. Custom post chains are now experimental opt-in;
  the default screen treatment uses safe GUI geometry and never writes to `minecraft:main`.
- Added `client/ApexOverlay` plus apex style/timeline HUD fallback. `BlastOverlay` now draws a moving ring and
  style motif (embers/scanlines/crawl/frost/glints) using one `RenderGuiEvent.Post` callback.
- `shadow_debug.log` now includes apex FX packet receipt and `APEX_FRAME` age/lifetime/progress/strength/mode
  samples, to diagnose future timing or visibility reports.

### Round 44 — frame-by-frame shader diagnostics

- `ShadowDebug` now queues full-rate client rendering diagnostics off-thread and rotates at 256 MiB.
- Apex/blast renderers log every frame and every candidate's distance, reach, age/lifetime, progress and
  computed strength; post chains log resource probes, uniforms, framebuffer size, duration and success/failure.
  Exceptions include complete stack traces. No sampling interval is applied.
