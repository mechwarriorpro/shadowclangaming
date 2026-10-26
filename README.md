# ShadowClanGaming — 1.21.1 NeoForge mod

A working, buildable mod for a Voltz-flavoured **strategic weapons** server: guided missiles, launch
pads, redstone arming, and a **MineChem-inspired element chemistry chain** that ends in nuclear warheads.

Original code, original art, original balance — inspired by mods like ICBM, MineChem, Nuclear Science
and HBM's NTM, but sharing no code with any of them. See `LICENSING.md` for why that's both legal and
the right call.

It runs **standalone**. Create / Aeronautics / Numismatics / NTM are optional (see `MODPACK.md`).

---

## 1. What you can actually do in game

### Fire control (changed per your spec)
1. Place a **Launch Pad** and load a missile into it.
2. **Right-click the pad with the Target Designator** → the designator links to that pad.
3. **Right-click in the air with the designator** → the pad's missile launches at **whatever block you
   are looking at** (up to 256 blocks) and the designator **spends 6,000 FE**.
4. The designator holds 60,000 FE → **10 shots**. Sneak + right-click with an **RTG Pellet** in your
   inventory to add 15,000 FE. It also exposes NeoForge's item energy capability, so any tech mod's
   charger can top it up.
5. Redstone still works: pulse the pad to fire at the pad's stored target (hands-free defences).

The designator shows an energy bar, shot count and the pad it is bound to, in its tooltip.

### Warhead catalogue

| Missile | Warhead | What it does |
|---|---|---|
| Decoy | `DECOY` | **Zero damage.** Full explosion sound and smoke. Pure psychological warfare. |
| EMP | `EMP` | **No block damage.** Kills launch pads (200t cooldown), stalls chemistry machines, drains every designator in 14 blocks flat, blinds and slows players. The counter-battery weapon. |
| SRBM / MRBM / ICBM | `HE` | Standard high explosive at 0.5× / 1.0× / 2.0× power. |
| Incendiary | `INCENDIARY` | Small blast, sets a wide area alight. |
| Cluster | `CLUSTER` | Six bomblets in a ring around the impact. |
| Fission | `FISSION` | Big blast + fire + lingering sickness (nausea/slowness) + glowing, so victims are visible. |
| Fusion | `FUSION` | Bigger blast, blinding flash, fire, more sickness. |
| Numismatic | `NUMISMATIC` | **Blast radius is bought with currency.** See below. |
| Bulwark | `BULWARK` | **No blast at all.** Money buys a containment dome: size and duration both scale with funding. See `docs/WARHEADS.md`. |
| Contagion | `CONTAGION` | Small blast, infectious payload. Nine vanilla symptoms that spread carrier-to-carrier and get worse every jump. See `docs/WARHEADS.md`. |
| Antimatter | `ANTIMATTER` | Two-stage 45-power blast, blinding, fire. The end of the tree. |

Adding a warhead = one enum entry in `warhead/WarheadType.java` + one `SCGItems` line.

### The Numismatic Warhead (your formula)

Fund it **two ways** — both load the same warhead and use the same curve:

1. **Bank Card** (`shadowclangaming:bank_card`) — the primary path. Sneak + right-click in air to pick an
   amount (1,000 → 1,000,000 → *next radius step*), then right-click the pad. The money comes
   straight out of your **EconomyCraft** balance. See `docs/ECONOMY.md` for the API details.
2. **Loading coins** — the optional extra. Right-click the pad with currency; sneak + right-click
   loads the whole stack. Works with no economy plugin at all.

Every coin type is converted to a common unit:

| Currency | Value |
|---|---|
| War Bond (ours, always available) | 100 |
| Numismatics spur / bevel / sprocket / cog / crown / sun | 1 / 8 / 16 / 64 / 512 / 4096 |

**The curve is anchored to your target: radius 4096 costs exactly 256 Suns.**

```
cost = 100 × (radius / 4) ^ k      k = ln(1,048,576 / 100) / ln(4096 / 4) = 1.335614
```

Every doubling of radius now costs **2.524×** more:

| Blast radius | Cost | Suns |
|---|---|---|
| 4 | 100 | 0.02 |
| 8 | 252 | 0.06 |
| 16 | 637 | 0.16 |
| 32 | 1,608 | 0.39 |
| 64 | 4,057 | 0.99 |
| 128 | 10,240 | 2.50 |
| 512 | 65,226 | 15.92 |
| 1024 | 164,619 | 40.19 |
| 2048 | 415,470 | 101.43 |
| **4096** | **1,048,576** | **256.00** |

**There is no cap on radius and no cap on funding.** Load as much as you like; the only limits are
physical (the world border at 29,999,984 blocks, and 64-bit integer range). Funding is stored as a
`long`, so nothing overflows.

Note this replaces the earlier 1.25×-per-doubling rule — the two are mathematically incompatible
(1.25×/doubling would make 4096 cost 932 units). To retune, change `TARGET_RADIUS` / `TARGET_COST` in
`warhead/Funding.java`; the exponent is derived from them automatically.

### The chemistry loop (the new part)
```
Uranium Ore ──mine──► Raw Uranium
                          │
                    DECOMPOSER  (turn items into element vials)
                          │
              ┌───────────┴───────────┐
        U-238 vials              Li + H vials
              │                       │
        SYNTHESIZER              SYNTHESIZER
              │                       │
      Enriched Uranium          Tritium Cell
              │                       │
              └──────► FISSION / FUSION MISSILE
```

- **Chemical Decomposer** — right-click with an item, wait ~3s, right-click empty-handed to collect
  element vials (a little random, like classic decomposers).
  Works on vanilla items too: coal → carbon, iron ingot → iron, gunpowder → sulfur/nitrogen/potassium,
  sand → silicon/oxygen, diamonds → carbon, and so on.
- **Chemical Synthesizer** — feed it element vials. It buffers them and fires the first recipe it can
  satisfy: 4× U-238 → Enriched Uranium, 2× Li + 1× H → Tritium Cell, 2× U-238 + 1× U-235 →
  Plutonium-239, and S/N/K/C → gunpowder.
- **Element Vials** — one item, one data component, 19 elements. Two carbon vials stack; carbon and
  uranium don't. Fissile elements glint in your inventory.

Chemistry machines are **GUI-less by design** (insert by hand, collect by hand) so the mechanics are
easy to feel and easy to replace with a proper GUI in M2.

---

## 1.5 Performance: how big blasts are handled

Explosions are handled in three tiers by radius:

| Radius | Path | Behaviour |
|---|---|---|
| ≤ 12 | vanilla `explode()` | fast, correct, keeps drops |
| 13–64 | `BlastScheduler` | block-precise, parallel planning, tick-budgeted |
| **> 64** | `StrategicBlasts` | **persistent scar** + bulk section scrubbing (see below) |

`perf/BlastScheduler` for the mid tier:

- **plans the sphere on worker threads** (pure maths, no world access — safe parallelism)
- **clears blocks in whole 16³ sections under a wall-clock budget** (3 ms/tick dedicated, 2 ms when
  the server is hosted on the client), so no single tick freezes
- **skips neighbour updates** (`UPDATE_CLIENTS`), which is vanilla's biggest explosion cost
- keeps blasts ≤12 blocks radius on vanilla `explode()` (already fast, keeps drops/fire/damage)
- falls back to a vanilla explosion if planning fails

**Client-hosted worlds get a gentler profile automatically** (`perf/ServerProfile`): when the server
runs on the player's own machine (single player / open to LAN), the block-clearing budget drops to
2 ms/tick, particles are halved and only sent to players within 96 blocks — so the render loop keeps
its time and FPS doesn't dip while a crater is carved. Tune with `-Dshadowclangaming.blastBudgetMs` and
`-Dshadowclangaming.particleScale`.

**Super-massive blasts (radius 4096) work differently.** 20.2 billion blocks is over an hour of solid
write time and 263,169 chunk columns, which no server can hold in memory. So anything above radius 64
records a **persistent scar**: loaded chunks are scrubbed immediately, and any chunk that loads later
inside the scar is wiped as it enters memory. Blocks are cleared via `SectionScrubber` — palette fills
plus **one relight per section** instead of `setBlock` per block (roughly 10–30× faster).

The GPU cannot accelerate any of this: measured, geometry maths is **1.7%** of a radius-96 blast (16 ms
of ~943 ms), and at radius 4096 the mod stops enumerating blocks entirely — what remains is memory
writes and chunk I/O. GPUs can't write Java heap objects, and dedicated servers don't have one. The
honest GPU path is a shader that renders the effect — visuals only.

Full explanation, tuning knobs and benchmark instructions: **`docs/PERFORMANCE.md`**.

## 1.55 Mana: the magic foundation

One pool per player, and three ways to raise its ceiling:

| Source | Effect |
|---|---|
| **Base** | 100 to start, full from the first login |
| **Casting** | Every cast permanently adds `1 + min(9, complexity/2)` max mana — the ceiling is a record of what you have actually cast |
| **Mana Capacity enchantment** | Up to V, **+25 max mana per level**, read live from your armour |
| **EMC into an Auto-Trader** | Refills the pool; once it is full, the surplus buys permanent max mana (4 EMC = 1 mana, 100 leftover mana = +1 max) |

**There is no natural regeneration.** Mana comes back from exactly three places: amethyst within 32
blocks (1–5/s, free and slow), standing in a drawn chalk circle (5–100/s by size), or a blood pact
(500/s anywhere — but it expires, needs villagers to feed it, and permanently locks you out of chalk). `/shadowclangaming mana <player>` breaks the ceiling into its three parts.
Details: **`docs/MAGIC.md`**.

## 1.56 Magic: build the gun, write the spell

Two tables and one block:

- **Blueprint Table** — mark a box, build a rifle out of real blocks inside it with an **Emitter** as
  the muzzle, sneak-use the table. The build is consumed and you get one Spell Rifle whose frame tier
  (1–5) and efficiency (60–95 %) come straight from what you built it out of. Dirt gets you a pop gun;
  netherite gets you an instrument. Full detail in `docs/BLUEPRINT.md`.
- **Inscription Table** — a slot-free drafting screen. A spell is a **sequence of steps**: press ADD STEP
  on a shape, click a step to select it, click components to fill it. So "bolt, then a zone, then a rune"
  is three rows you wrote top to bottom. **No reagents, no shape groups, no component cap** — you pay
  mana, and the spell must fit inside your **mana capacity**; there are no spell levels.
- **Zone / Rune steps** — a zone lives **30 s** and **re-casts the rest of the spell from its own position
  every 5 s**; each point of power adds **1 s** of life and takes **0.25 s** off the recast, so a heavy
  gun leaves a longer, faster zone (a 27-emitter build: 56 s, pulsing every second). A rune is a trap
  that fires the rest once when something steps on it.
- **Spell Warhead** — hold one at the inscription table and pay `200 × n²` currency to copy the draft
  into it. It does no damage of its own: at impact it casts that spell, scaled ×3.

94 base effects over five tiers, plus **twenty-eight** per-step **modifiers**, priced the old Ars Magica way at `25 × tier²` mana each, so exotic
magic stays expensive no matter how long your spell is. The five newest: **Random Teleport** (up to 16
blocks, always into a spot something can stand in), **Cluster Strike** (the bolt splits into 5 homing
bolts, each carrying the rest of the spell), **Chronosplit** (15 seconds frozen - no moving, no
attacking, no interacting), **Firestorm**, and its upgrade **Hellstorm** (bigger, hotter, ignores
i-frames, and it will not burn your loot). Then the anime batch: **Starstrike**, **Entangle**,
**Chain Lightning** and **Banish**, plus **Grasp Heart**, **Fallen Down**, **Black Hole**, **Turn
Undead**, **Explosion** and **God Requiem**, and the YGGDRASIL metamagic as modifiers -
**Maximize**, **Triplet**, **Boost**, **Delay** and **Chant**. All of it is in `docs/MAGIC.md` §9.

## 1.57 Guns: procedural loot weapons

Feed **Gun Scrap** to a **Gun Fabricator** and a gun prints, announced in chat. Every gun is rolled
from 26 parts across six slots, four makers, five rarities and five elements, with 25 hand-drawn
silhouette/palette combinations picked through vanilla `custom_model_data` — no renderer needed.

```
damage = 9.0 x maker x product(parts) x rarity      |   rarity also buys extra re-rolls per slot
```

Relics are 2 % of rolls, hit +32 % over scavenged on average, and glow. Ember sets things alight, Arc
does +50 % to anything already burning, Acid poisons, Void withers and heals you. Hitscan, 48 blocks,
real fire rates, real reloads from **Ammo Cells**. Details: **`docs/GUNS.md`**.

## 1.58 Armour: three sets, three prices

**Infamy** (Resistance + Strength, much stronger below a third health — but daylight burns you),
**Sentinel** (Regeneration, and you move slowly), **Raider** (Speed, Jump, Night Vision, thin plate).
Three custom `ArmorMaterial`s, twelve pieces, six generated layer textures, set bonuses that switch off
within two seconds of taking a piece off. Details: **`docs/ARMOR.md`**.

## 1.59 Terminals: the computer layer

Not an OpenComputers clone — a **terminal** you drive with `/term`, a **channel bus** that links it to
launch pads by number, and three cards: **Targeting** (list and fire every pad on your channel),
**Mana Sensor** (current *and* maximum mana for everyone nearby) and **Network** (allowlisted,
sandboxed text fetch — empty allowlist by default, private addresses always refused).

`/term fire 120 70 -340` fires the whole channel. Full scope, guards and what a real OpenComputers
successor would need next: **`docs/COMPUTERS.md`**.

## 1.595 Launchers: tiers, modules, funding, three new warheads

A launcher is a **structure**: pad + control panel + two support rods, and it is only as good as its
weakest part (`effective = min(pad, panel, lowest rod)`). **Upgrade Kits** raise each part one tier at a
time, 1→5.

- **Funding is currency *or* FE.** The pad holds 4,000,000 FE and converts it at **1 FE = 1 unit** at
  launch — the same rate the Auto-Trader uses in reverse.
- **Ballistics Calculator** — use on a pad: warhead, currency, FE, **resulting radius**, launcher report.
  Use in the air: the funding → radius table.
- **Cargo missile** — drops a chest of 27 slots of cargo; destroys nothing.
- **Mine layer** — `radius = 32 + funding/250`, and **10% of ground columns** in that radius get a mine
  (≈420 mines at radius 32, hard cap 2,048). Mines are random-tick blocks with **no block entity**, so a
  quiet field costs nothing.
- **Tracking missile + Tracker** — lock an entity by UUID (survives death, logout, restarts), hand the
  lock to a pad, missile homes on it every tick.
- **Interdimensional** — module on the panel **and** a launcher that is level 5 in every part. Targets via
  `/term panel target <dim> <x> <y> <z>`; the missile does one `DimensionTransition` then flies normally.
- **Terminal programs** — `/term prog save|run|list|show|delete` (20 lines, 64 programs), replayed through
  the normal interpreter. `/term panel list|status|arm|disarm|target|fire` talks to control panels.
- **Network cables** link terminal, panels, pads and storage bridges physically (`/term wire` shows the
  walk); a **Storage Card** reads AE2 through a Storage Bridge (reflective, falls back to chests); a
  **Redstone Card** gives six outputs + one input (`/term redstone out|pulse|read|clear`), with pulses
  scheduled on the ticker rather than slept.

Full detail: **`docs/LAUNCHERS.md`**.

## 1.596 Hardware, racks, orbital nodes, radar and cannon fire control

The terminal grew a spec sheet, and the base grew eyes and a gun.

- **CPU and GPU are real items with real numbers.** Four CPU tiers (1/2/4/8 cores, 4–80 FE/t, and an
  orbital latency of 20/10/5/2 ticks) and four GPU tiers (2–16 blast threads, 1–8 radar pipelines).
  Sockets are one-way: a module only goes in if it is better than what is there.
- **Network Rack** — four sockets (2 CPU + 2 GPU), a peripheral on the cable that reports numbers and
  **never runs a command itself**. `/term rack status`.
- **Orbital Processing Node** — a computer above the clouds. `/term node queue 1 radar scan` hands a
  subcommand to it; it runs one every `latencyTicks` while it can see the sky, **as the terminal that
  queued it** (same cards, same channel, same permissions), keeps a saved 64-entry queue, and refuses to
  command other nodes. Recursion depth is hard-capped at 4.
- **`/term cpu`** prints the whole budget — cores, jobs, blast threads, radar pipelines, FE/t, racks,
  nodes — recomputed on every call, so a pulled rack is felt at once.
- **Our Radar** — 256 blocks base, **+128 per extra dish to 4096** (31 dishes), a real **track list** (8 + 4/dish +
  GPU contacts, cap 128) with stable track numbers, threat ranking, closing speed, and a **lock** the
  cannon uses. `/term radar scan | track <n> | lock <n> | auto on|off`.
- **Create: Radars integration** — reflective only: counts the dishes near our antenna and reads their
  track list where that build exposes one (`/term radar create`), and says so plainly when it cannot.
- **Cannon Director** — links to a Create Big Cannons mount, solves the shot
  (`pitch = atan2(height + ½gt², horizontal)`), aims through CBC's public `setYaw`/`setPitch`, fires
  through `onRedstoneUpdate(false,false,true,false,15)` and releases two ticks later — with a two-tick
  **redstone pulse fallback** if that build of CBC has moved on. `/term cannon arm | aim | fire | engage |
  auto`.
- **Unattended air defence** — `cannon engage` sweeps, takes the worst contact, leads it by the shell's
  flight time and fires. Only at missiles and players; everything else is refused with a reason.

Full detail, including the exact formulas, the reflective contracts and the performance bounds:
**`docs/RADAR.md`** and **`docs/COMPUTERS.md` §11–§14**.

## 1.597 The Lua stack: an interpreter, a screen and a machine API

The computer layer gets a real scripting language, and it is ours: a lexer, parser, compiler and **bytecode
VM** written for this mod (`com.shadowclangaming.lua`, zero Minecraft imports). Not LuaJ, not a fork — which is
what makes it safe to run on a ticking server.

- **It gives the tick back.** `vm.run(budget)` executes at most N instructions and returns with the whole
  call stack intact, so `while true do end` pauses instead of hanging the server. There is no
  `Thread.sleep` anywhere in the mod.
- **Waiting suspends.** `event.pull("key_down")` throws a `Yield`; the machine remembers it and resumes the
  script when the event arrives. That is why an OS can run on 20 ticks a second.
- **Coroutines** are the same save/restore, reused (`coroutine.create/yield/resume`, `thread.create`).
- **Closures work properly:** every local is boxed in a `Cell` and the compiler never reuses a slot number,
  so two closures over the same variable do not interfere.
- **The language**: if/elseif/else, while, repeat, numeric and generic `for`, functions, varargs, multiple
  returns, method calls, table constructors, `string.format` with widths. Out of scope on purpose:
  metatables, `goto`, Lua patterns, `load`, `io` (see `docs/LUA.md` §4 for the table and the reasons).
- **The API is the OpenComputers shape**, because that is what programs are written against:
  `component`, `computer`, `event`, `thread`, `gpu`, `term`, `fs`, `colors`. Screen model included
  (`TextBuffer` + GPU tiers 80×25 / 100×30 / 120×40), with **deferred wrapping** so the bottom row does not
  jump a line early.
- **Filesystem safety is tested, not assumed:** paths normalise in one place, so `/../secret` is false and
  `/etc/../etc/motd` is `/etc/motd`.

Two harnesses prove it on a bare JVM — 40 assertions on the language, 51 on the API:

```bash
bash tools/run_lua_harness.sh
bash tools/run_api_harness.sh
```

Full detail, including the honest divergence list and the roadmap to MineOS: **`docs/LUA.md`**. Provenance
and what may ever be bundled: **`THIRD_PARTY.md`**.

## 1.6 The economy: money is power

| Piece | What it does |
|---|---|
| **Bank Card** | Signed on first use. Pays for Numismatic warheads straight out of an EconomyCraft balance. |
| **Auto-Trader** | Holds a card. Items piped in are sold, FE piped in is converted at `100 × n³` FE per unit, redstone buys a templated item. |
| **Price table** | Every item in every loaded mod priced by heuristic + hand-set overrides, written to `config/economycraft/prices.json`. |
| **Hyperconductor Cable** | Long-based power trunk: 1 GFE/t per cable, auto-converts FE, Joules (via Dynamic Electricity) and ProjectE EMC. |

The cable is the physical end of "money is power": it stores 4 GFE and moves 1 GFE per tick using
`long`, looping the int-only FE interface rather than being limited by it. Details and the measured
numbers: **`docs/POWER.md`**.

The FE curve is deliberately a cube: the first unit costs 100 FE (one tick of a weak generator), the
100th costs 100,000,000 FE. Counters are per player, so building more machines does not buy more
cheap units. Full details: **`docs/ECONOMY.md`**.

## 2. Contents

```
voltz_reborn/
├── build.gradle                  ← NeoForge 21.1.264 / MC 1.21.1 / Java 21
├── gradlew, verify.sh            ← build scripts
├── .github/workflows/build.yml   ← free jar builds on GitHub
├── README.md  DESIGN.md  MODPACK.md  LICENSING.md
└── src/main/
    ├── java/com/shadowclangaming/
    │   ├── ShadowClanGaming.java
    │   ├── warhead/
    │   │   ├── WarheadType.java       every warhead's blast, fire, EMP, decoy behaviour
    │   │   ├── ForceFields.java       Bulwark domes (saved data, funding-scaled)
    │   │   ├── Outbreak.java          Contagion plague (carriers, generations, spread)
    │   │   └── Funding.java           the uncapped radius -> funding curve (4096 = 256 Suns)
    │   ├── magic/
    │   │   ├── ManaPool.java          per-player mana (saved data, no natural regen)
    │   │   ├── ManaApi.java           capacity sources, spend, cast growth, curse state
    │   │   ├── ManaSources.java       amethyst proximity + pact payout (1/s pass)
    │   │   ├── Sacrifices.java        the full-moon ritual (witch + baby villager + cat)
    │   │   ├── CreepingEvil.java      the hidden curse: levels, hearts, the midday cure
    │   │   └── ClericTrades.java      Holy Water, from cleric villagers only
    │   ├── spell/                ← THE SPELL SYSTEM
    │   │   ├── SpellShape.java        self / touch / projectile / beam / aura / zone / rune
    │   │   ├── SpellComponent.java    94 base effects, 5 tiers, 25 x tier^2 mana
    │   │   ├── SpellModifier.java     28 per-step modifiers: empowered, maximize, legion, ...
    │   │   ├── SpellDelays.java       the delayed-cast queue (DELAY / CHANT)
    │   │   ├── Spell.java             ordered step list (shape + components), all the maths
    │   │   ├── SpellCasting.java      the sequence interpreter: traces, applies, hands off
    │   │   ├── SpellZones.java        live zones and runes: the 5 s pulse, the rune trigger
    │   │   ├── SpellStorms.java       firestorm / hellstorm burning areas
    │   │   └── Chronosplit.java       15 s freeze + its interaction locks
    │   │   ├── BlueprintTableMenu.java one-block rifle bench: 27 slots + barrel/strength dials
    │   ├── blueprint/
    │   │   └── BlueprintMath.java     the build -> rifle maths (pure, unit-tested)
    │   ├── gun/                  ← PROCEDURAL WEAPONS
    │   │   ├── GunPart.java           26 parts, six slots, pure multipliers
    │   │   ├── GunRarity.java         5 rarities: multipliers, re-rolls, element odds
    │   │   ├── GunMaker.java          4 makers and their biases
    │   │   ├── GunElement.java        5 elements and their on-hit behaviour
    │   │   ├── GunStats.java          the immutable rolled gun + codecs
    │   │   ├── GunForge.java          the generator and the naming (unit-tested)
    │   │   └── GunShooting.java       ray, spread, damage, tracer
    │   ├── armor/
    │   │   ├── ArmorSet.java          3 sets, bonuses, the Infamy sunlight price
    │   │   └── ArmorSets.java         the once-a-second pass
    │   ├── launcher/
    │   │   ├── LauncherLevel.java     the structure scan and weakest-part rule
    │   │   ├── LauncherModule.java    modules and the level each needs
    │   │   └── UpgradeKit.java        one-tier-at-a-time upgrade rule
    │   ├── lua/                  ← THE INTERPRETER (no Minecraft imports, harness-testable)
    │   │   ├── Lexer.java            tokens, all Lua literals
    │   │   ├── Parser.java          recursive descent -> Ast
    │   │   ├── Emitter.java         Ast -> bytecode; slots never reused, upvalues on demand
    │   │   ├── Vm.java              stack machine with explicit frames, budgets and suspension
    │   │   ├── Stdlib.java          string/table/math/coroutine, pcall with frame unwinding
    │   │   └── Lua.java             the only entry point (newVm/load/run)
    │   ├── computer/api/         ← THE MACHINE API (also Minecraft-free)
    │   │   ├── ComputerApi.java     installs component/computer/event/thread/gpu/term/fs/colors
    │   │   ├── TextBuffer.java      the screen grid: clip, fill, scroll, colours
    │   │   ├── TermLibrary.java     cursor, deferred wrap, scrolling
    │   │   ├── GpuLibrary.java      exact drawing, gpu.bind, palette
    │   │   ├── FileSystem.java      path rules in one place; MemoryFileSystem for tests
    │   │   └── ComponentRegistry.java  address (and unique-prefix) lookup
    │   ├── computer/
    │   │   ├── TerminalBlockEntity.java  frequency, cards, log
    │   │   ├── TerminalShell.java     the command interpreter
    │   │   ├── FrequencyRegistry.java channel -> pads (saved data)
    │   │   ├── TerminalModule.java    the three cards
    │   │   └── WebAccess.java         allowlisted, sandboxed HTTP text fetch
    │   ├── economy/
    │   │   ├── PowerExchange.java     100 FE x n^3 -> money, closed form included
    │   │   └── ExchangeLedger.java    per-player power-to-money counters (saved data)
    │   ├── compat/projecte/      ← ProjectE EMC bridge (long, reflection, method probing)
    │   ├── compat/economy/       ← EconomyCraft bridge (reflection, no GPL linkage)
    │   │                             EconomyCraftBalance, EconomyCraftPrices,
    │   │                             EconomyBridge, PriceHeuristic, AuthoritativePrices
    │   ├── chem/                 ← THE PERIODIC TABLE
    │   │   ├── Element.java           19 elements, codecs, colours, fissile/fusion flags
    │   │   ├── DecompositionTable.java item → (element, min, max)
    │   │   └── SynthesisTable.java    element counts → item
    │   ├── registry/             SCGItems, SCGBlocks, SCGBlockEntities, SCGEntities, SCGMenus,
    │   │                          SCGArmorMaterials,
    │   │                          SCGDataComponents (ELEMENT, SPELL, RIFLE_TIER, ...),
    │   │                          SCGMobEffects, SCGCreativeTabs
    │   ├── item/                 MissileItem, SpellRifleItem, TargetDesignatorItem,
    │   │                         ElementVialItem, BankCardItem, TraderBlockItem,
    │   │                         ChalkItem, HolyNecklaceItem, HolyWaterItem
    │   ├── block/                LaunchPadBlock(+BE), ProcessorBlock(+BE),
    │   │                          DecomposerBlock, SynthesizerBlock,
    │   │                          AutoTraderBlock(+BE)  ← items/FE in, money out
    │   │                          PowerCableBlock(+BE)  ← long-scale power trunk
    │   │                          CircleBlock / ChalkCircleBlock / BloodCircleBlock(+BE)
    │   │                          BlueprintTableBlock(+BE) ← build -> rifle
    │   │                          InscriptionTableBlock(+BE) + InscriptionMenu
    │   │                          EmitterBlock
    │   ├── entity/               MissileEntity, SpellProjectileEntity
    │   ├── client/               SCGClient, InscriptionScreen
    │   └── util/SCGCompat.java    optional-mod detection (create, aeronautics,
    │                             createbigcannons, numismatics, hbm)
    └── resources/
        ├── META-INF/neoforge.mods.toml
        ├── assets/shadowclangaming/    models, blockstates, 16×16 textures, en_us.json
        └── data/shadowclangaming/      recipes, loot tables, ore worldgen + biome modifier
```

---

## 3. Run it

Install **JDK 21** ([Adoptium](https://adoptium.net/temurin/releases/?version=21)), then:

```bash
cd voltz_reborn
./gradlew runClient          # Windows: gradlew.bat runClient
```

First run downloads Minecraft + NeoForge and decompiles them: 5–15 minutes, once. After that, seconds.

Build the actual jar:

```bash
./gradlew build      # → build/libs/ShadowClanGaming-1.0.0.jar
# or
./verify.sh
```

Five checks that need no build and no game (all work with just a JDK 21; the harness scripts find one
on their own via `tools/jdk.sh`):

```bash
python3 tools/sweep.py             # brackets, JSON, assets, lang keys, forbidden calls
bash tools/run_logic_harness.sh    # the pure maths: budgets, radar, cannons — 90 assertions
bash tools/run_lua_harness.sh      # the Lua interpreter, 40 assertions
bash tools/run_api_harness.sh      # scheduler / GPU / component model, 51 assertions
```

Drop that jar in `mods/` on the server **and** every client.

### Fast test checklist

| Test | Expected |
|---|---|
| Creative → ShadowClanGaming tab | Launch Pad, Designator, 5 missiles, 2 machines, ore, 3 materials, 19 vials |
| Decomposer + coal | ~3s later, right-click empty hand → carbon vials |
| Decomposer + iron ingot | iron vials |
| Synthesizer + 4× U-238 vials | Enriched Uranium pops out |
| Synthesizer + 2 Li + 1 H | Tritium Cell |
| Load pad, set target, power with redstone | missile flies and explodes |
| New world, dig down | uranium ore generates between y=-48 and y=24 |
| Terminal + `/term cpu` | reports `cpu basic`, `gpu none`, 1 core, 0 pipelines |
| Right-click a rack with a CPU, then a GPU | sockets fill; `/term rack status` agrees |
| Orbital node on a tower + `/term node queue 1 status` | runs within a few seconds, result in `/term node status 1` |
| Orbital node in a basement | refuses: "cannot see the sky - its queue will not run" |
| Radar + `/term radar scan` | prints radius 256, any players/mobs nearby with bearing and threat |
| 31 dishes around the radar | `/term radar status` now says radius 4096 |
| Director next to a CBC mount: sneak-right-click | "linked mount ... yaw ... pitch ..." |
| `/term cannon arm` then `/term cannon fire 400 70 -120` | barrel lays, cannon fires; `/term` prints the aim solution |
| Create: Radars installed, `/term radar create` | counts the dishes, and says plainly if their track list is unreadable |

---

## 4. How to extend it

### Add an element (2 minutes)
1. One line in `chem/Element.java` — follow the column layout (`name, symbol, atomicNo, mass, colour, fissile, fusionFuel`).
2. One line in `assets/shadowclangaming/lang/en_us.json`: `"element.shadowclangaming.<name>": "Display Name"`.
3. Optional: use it in `DecompositionTable` (what drops it) and `SynthesisTable` (what consumes it).

The creative tab fills itself from the enum — nothing else to update.

### Add a decomposition (1 line)
```java
add(Items.NETHERITE_SCRAP, y(Element.AU, 1, 2), y(Element.C, 2, 4));
```

### Add a synthesis recipe (1 block)
```java
add(synthesis(200, new ItemStack(SCGItems.ENRICHED_URANIUM.get()),
        Element.U238, 4));
```

### Add a whole new machine
Copy `ProcessorBlock` + `ProcessorBlockEntity` — they already handle ticking, save/load, comparator
output, dropping contents when broken, and the insert/collect loop.

### Talk to another mod
Use `util/SCGCompat`. Put integration code in its own class and only call it inside a
`SCGCompat.x()` check, or the game crashes with `NoClassDefFoundError` when that mod isn't installed.

---

## 5. Troubleshooting

| Symptom | Fix |
|---|---|
| Mod not in the mod list | `neoforge.mods.toml` `modId` must equal `ShadowClanGaming.MOD_ID` (`shadowclangaming`) |
| `Unsupported class file major version` | Java 11/17 in use — install JDK 21 and set `JAVA_HOME` |
| Crash on world load mentioning a registry | A registered object's `register(modEventBus)` call is missing in `ShadowClanGaming` |
| Purple/black texture | Path must be exactly `assets/shadowclangaming/textures/item/<name>.png` |
| Vials all look the same colour | Expected: vanilla can't recolour one texture per element. `ElementVialItem.getName()` gives each its own name; per-element tints are on the M2 list (needs a custom item model) |
| Machine does nothing | Decomposer needs a decomposable item; Synthesizer needs *vials*, not raw items |
| Designator says "not enough energy" | Charge it: sneak + right-click with an RTG Pellet |
| Designator does nothing when right-clicked | It must be linked to a pad first (right-click the pad), and the pad needs a missile loaded |
| Numismatic missile does nothing | Load currency into the pad after loading the missile; 0 funding = 1-block radius |
| Right-clicking a pad with a designator won't fire | By design: right-clicking a *block* binds, right-clicking *air* fires |
| `gradlew: Permission denied` | `chmod +x gradlew` |
| Gradle OOM on a small machine | Use the GitHub Actions build, or `org.gradle.jvmargs=-Xmx2G` |
| Server freezes for seconds on a big strike | Lower `MAX_TICKS_PER_BLAST`, lower `MAX_RADIUS` in `Funding.java`, or raise `simulation-distance` cost awareness — see `docs/PERFORMANCE.md` |
| Crater appears in chunks/rings over a second | Working as intended: the blast is budgeted across ticks, nearest sections first |
| Sand/gravel floats above a crater | Neighbour updates are suppressed by design; touch it and it falls |

Logs: `run/logs/latest.log` in dev, `logs/latest.log` on a server. The first `Caused by:` is the real error.

---

## 6. Roadmap

`DESIGN.md` has the detail. Short version:

- **M1** — warheads as data (JSON), silo multiblock with countdown, radiation-lite
- **M1.5 (done)** — mana without regen, the blood pact, Creeping Evil, spells, block-built rifles
- **M1.6 (done)** — procedural guns, armour sets, the terminal/channel bus/web card
- **M1.7 (done)** — launcher tiers, modules, cargo/minefield/tracking warheads, FE funding, calculator,
  terminal programs and panel control
- **M1.8 (done)** — storage/redstone cards, AE2 reading through a bridge, network cables
- **M1.9 (done)** — CPU/GPU tiers, network racks, orbital processing nodes and subcommands, our radar
  (256–4096 blocks, multi-target tracks), Create: Radars bridge, CBC cannon director with lead
- **M2** — decomposer GUI, per-element vial tints, datapack-driven chemistry tables
- **M3 (core done in M1.9)** — radar + tracking; next: ABM interception of *incoming* missiles
- **M4 (cannon done in M1.9)** — Aeronautics airship launches, cannon autoloaders
- **M5** — Numismatics upkeep + war contracts
- **M6** — JEI/EMI pages, Ponder tutorials, configs
