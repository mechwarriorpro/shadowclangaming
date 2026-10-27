# Two new warheads: Bulwark and Contagion

Both are normal missiles: load one in a launch pad, bind a designator, fire. Neither reproduces
anyone's code — the mechanics below are designed for this pack.

---

## 1. Bulwark — the money dome

`shadowclangaming:bulwark_missile` · warhead `BULWARK`

A warhead that **destroys nothing**. It lands and the impact point becomes a sphere you cannot cross,
for as long as the money in it lasts. This is the only weapon in the mod that is purely positional:
it does not kill, it denies ground.

### Funding drives both size and time

Same payment paths as the Numismatic warhead — a signed Bank Card, or coins loaded into the pad:

```
scale    = sqrt(funding / 100)
radius   = 8 + round(2 * scale)         blocks   (simulation ceiling 256)
duration = 60 + round(20 * scale)       ticks
```

| Funding | Radius | Duration |
|---|---|---|
| 100 | 10 blocks | 4.0 s |
| 1,000 | 14 blocks | 6.2 s |
| 10,000 | 28 blocks | 13.0 s |
| 100,000 | 71 blocks | 34.6 s |
| 1,000,000 | 208 blocks | 103.0 s |
| 10,000,000 | 256 blocks (ceiling) | 319 s |
| 1,000,000,000 | 256 blocks | 52.8 min |

One Sun (4,096 units) buys a 20-block dome for about 8 seconds. Ten Suns (40,960) buys a 48-block
dome for 23 seconds. The Bank Card's *"next radius step"* tier buys exactly one more block of dome on
this curve, so a click always does something measurable.

### What the dome actually does

* Anything **inside** is hard-clamped just under the shell — you can move freely within it and you
  cannot leave. Velocity pointing outward is cancelled, so walking into it feels like a wall.
* Anything **outside** is pushed back out if it pokes through the shell, including arrows, items and
  mobs.
* It follows the server's saved data, so it survives a restart; `/shadowclangaming field list` shows every
  live dome and how long it has left.

### The honest limits

| Limit | Why |
|---|---|
| Radius saturates at 256 | Every tick the dome walks every entity inside its bounding box. A dome wider than the view distance would mean scanning the whole loaded world 20 times a second. One constant, not a balance decision. |
| Duration does **not** saturate | Pay absurd money, get an absurd dome. Only a technical clamp at 1,000,000 ticks (about 14 hours) prevents integer overflow. |
| Fast movers can overshoot by one tick | Containment is positional clamping, not physics. A missile crossing a tick boundary can appear briefly inside before being ejected. Real collision solving would need a raycast per entity per tick; not worth it. |
| It does not block explosions | It is a barrier for entities, not a blast shield. If you want that, say so — it is a different feature. |

### Tactics it enables

* Seal a cave entrance and drop a squad inside it with conventional warheads.
* Bubble a rival's launch pad so their own missiles cannot leave — the pad is outside, the missile is
  trapped inside.
* Buy 30 seconds of peace around a damaged base while you repair.

---

## 2. Contagion — the plague warhead

`shadowclangaming:contagion_missile` · warhead `CONTAGION`

A small blast (power 4) with an infectious payload. Everything caught in the blast picks up a random
set of symptoms and **becomes a carrier**. Carriers are contagious: once a second they infect
everything within 8 blocks, and every jump makes the disease worse.

### Symptoms

Nine vanilla effects, all "you cannot fight back" rather than direct damage:

> nausea · mining fatigue · slowness · blindness · hunger · poison · darkness · weakness · levitation

Levitation is in the list on purpose.

### It escalates

| Generation | Symptoms | Amplifier | Duration |
|---|---|---|---|
| 0 (the blast) | 1 | I | 10 s |
| 1 | 2 | I | 20 s |
| 2 | 3 | II | 30 s |
| 4 | 5 | III | 50 s |
| 8 | **9 (all of them)** | **V** | 90 s |
| 12 | 9 | V | 130 s |

`count = min(9, 1 + generation)` · `amplifier = min(4, generation / 2)` ·
`duration = min(60000, 200 + 200 * generation)` ticks.

A symptom set is chosen deterministically from the victim's UUID and the generation, so a carrier
keeps the same symptoms between pulses — you cannot re-roll your fate by waiting.

### Why carriers and not a field

A static field is dodgeable: walk out, wait, done. A carrier is a *moving source*, so the counter-play
becomes quarantine — isolate the infected, kill them, or cure them. That is a much more interesting
thing to ask of a server than "stand outside the circle". There is no limit on how far a chain can
travel or how many generations it reaches; a plague that starts in a village and reaches spawn an hour
later is a legitimate outcome.

### Curing

* Effects alone are **not** a cure — a carrier's symptoms reapply on every pulse. Milk buys you
  seconds, not safety.
* `/shadowclangaming plague cure <player>` clears the carrier state and the effects.
* `/shadowclangaming plague clear` cures everyone.
* `/shadowclangaming plague list` shows carrier count, the worst generation out there, and the spread
  parameters.
* Carriers naturally stop being contagious once their duration expires.

### The honest limit

`MAX_CARRIERS = 1024` is a **technical ceiling**, not a balance cap: each carrier costs one AABB query
per second, and a plague loose in a mob farm can otherwise put ten thousand entities in that loop. It
is one constant in `Outbreak` if your server can take more.

---

## Tuning

Every number lives in one place per warhead:

```
warhead/ForceFields.java   BASE_FUNDING, BASE_RADIUS, MAX_SIM_RADIUS, MAX_DURATION_TICKS,
                           radiusForFunding, fundingForRadius, durationTicksForFunding
warhead/Outbreak.java      SPREAD_RADIUS, PULSE_TICKS, MAX_CARRIERS, SYMPTOMS,
                           symptomCountFor, amplifierFor, durationTicksFor
```

Both warheads store their state in the world's saved data (`shadowclangaming_forcefields`,
`shadowclangaming_outbreak`) and tick from a single `ServerTickEvent.Post` listener, so a restart does not
silently disarm a dome or cure a plague.

---

# Round 20: Constructor, Spatial, Mother Clucker, Violator

Four warheads, one of which changes the loading rules for all missiles. Every number below is in code
with a comment saying why; the constructor's sphere arithmetic is additionally checked by
`tools/run_logic_harness.sh` (24 assertions).

## 0. Loading a missile by right-clicking it

**Cargo missiles only** (`MissileItem#acceptsPayload`), and it is a **chest GUI**, not an offhand gesture:
right-click the missile with it in hand and a 27-slot hold opens, with your inventory underneath and
shift-click in both directions. Constructor and spatial warheads take their material from the **launch
pad's hold** instead.

The old offhand path also had a real bug — inserted items could come back as **air**, because emptying the
source stack and then rewriting the component could persist zero-count entries. The GUI writes the
component slot by slot and never persists an empty stack, so that failure mode is gone.

| Action | Result |
|---|---|
| Right-click with a stack in the **other hand** | The whole stack goes into the warhead (up to 27 stacks) |
| Right-click with an **empty** hand | Reports how many of 27 stacks are loaded |
| **Sneak** + right-click with an empty hand | Empties it back into your inventory |
| Drop it into a launch pad | Works exactly as before — both habits are supported |

The payload is a **data component** (`shadowclangaming:payload`), not a block-entity inventory, so it
travels with the item through chests, ender chests and the launcher's slot. A missile carrying anything
drops to a stack size of 1, so two different payloads can never merge.

## 1. Constructor — the missile that builds

`shadowclangaming:constructor_missile` · warhead `CONSTRUCTOR`

Fills a **solid sphere** with the blocks you loaded. It only replaces air, fluids and plants — it never
bulldozes an existing build — and it fills in over several ticks (512 blocks/tick) starting at the
impact point, so a big sphere is something you watch happen rather than a freeze.

### Radius: the nearest perfect sphere

```
blocks in a ball of radius r = every cell (x,y,z) with x²+y²+z² ≤ r²
   r=0 → 1      r=1 → 7      r=2 → 33      r=3 → 123     r=4 → 257
```

The build is the **largest sphere you can fill completely**, and the remainder is **dropped**, not
smeared around the rim. 64 blocks is radius 2 (33 used, 31 dropped) — never a ragged radius 3.

### Blending

Loaded more than one block type? Placement is a **deterministic noise blend** weighted by how many of
each you loaded: a 50/50 mix of stone and mossy cobble looks like static, not a checkerboard, and the
same missile always builds the same way (the seed is derived from the pad position and the launch tick).

### Radius slider instead of "max blast"

For warheads whose radius the player chooses (`WarheadType#usesRadiusSlider`: Constructor and Spatial)
the pad's warhead column shows a **slider** plus an **exact entry box** and an **auto** button:

* slider / exact box → `SetRadiusPayload` → stored on the missile (`shadowclangaming:radius`)
* **auto** clears it (-1) and the payload decides again
* ask for more than you loaded and the build is **refused** with the exact shortfall, and your blocks
  come back as a cargo drop — a half-built shell is worse than an honest failure

## 2. Spatial — the missile that steals ground

`shadowclangaming:spatial_missile` · warhead `SPATIAL`

Load an **AE2 spatial storage cell** (see below), fire. The centre of the stored volume is the impact
point. Stores `radius×2+1` blocks per side, where radius is the slider value (default 8).

Every failure is reported and nothing is destroyed:

| Failure | What happens |
|---|---|
| No spatial cell loaded | Told to load one; nothing happens |
| Cell too small for the volume | Told the cell's per-side size |
| AE2 absent / API unreachable | Told so, and the cell is returned as cargo |

## 3. AE2 cells

Recognised reflectively (`compat`-style, `warhead/Ae2Cells.java`): `StorageCells.isCellHandled` and
`ISpatialStorageCell` when AE2 answers, and an id heuristic when it does not.

**"If you load a cell, the cell is dropped instead of the items."** A Constructor warhead carrying an
AE2 item cell does not unpack it into blocks — the cell lands intact via the cargo path and chat says so.

## 4. Mother Clucker — the missile that keeps giving

`shadowclangaming:clucker_missile` · warhead `CLUCKER`

Spawns **50 turret chickens** on impact. Each is a real `Chicken` (vanilla model, breeding, drops) with
one extra goal: every 1.5 s it lobs an egg at the nearest player or hostile mob within 16 blocks. Its
eggs are `TurretEggEntity`, and **their hatch produces another turret chicken**, not a plain one.
Vanilla's 1/8 hatch chance is kept — a 100 % rate would carpet the world in a minute.

## 5. Violator — the wither worth running from

`shadowclangaming:violator_missile` · warhead `VIOLATOR`

Spawns a wither built to end a base rather than a block:

* **3× health** — 300 → 900, healed to full on arrival.
* **5× skull blast radius and strength** — our skulls are tagged, and `ExplosionEvent.Detonate` cancels
  vanilla's blast and re-runs it at 5× power (one skull leaves a crater, not a hole).
* **64-block reach** — six chunks in every direction.
* **8-skull shotgun volleys** every **0.25 s** (4 ticks), for 5 s out of every 30.
* **Multiple targets at once** — skulls go **round-robin to the nearest eight enemies**; surplus skulls
  fan out in a ±0.10 radian cone.

| Number | Value |
|---|---|
| Burst interval / length | 600 ticks (30 s) / 100 ticks (5 s) |
| Volley cadence | 4 ticks — 25 volleys per burst |
| Skulls per volley | 8 |
| Live skull cap | **192** (tps guard; extra skulls skipped) |
| Range / targets | 64 blocks / up to 8 |

## 6. Beef Supreme — the cow you cannot outrun

`shadowclangaming:beef_missile` · warhead `BEEF`

A cow. Same renderer, same moo. With a boss bar, **5000 health**, **30 damage**, and a ram:

1. **Wind-up — 5 seconds (100 ticks).** It slows to **2 %** of its speed and turns to face its target,
   kicking up **dirt** and **flame** particles under its feet with a ravager roar every second. This is
   the tell: it is slow, it is loud, and it is looking at you.
2. **Charge — up to 1.5 s.** It locks the direction *at the moment the wind-up ends* and dashes at
   **1.15 blocks/tick**, roughly 23 m/s. A sidestep works; standing still does not.
3. **Impact.** **30 damage** plus knockback, once per ram. Connecting ends the charge immediately.
4. **Cooldown — 4 s** before it may ram again.

**It climbs while charging.** Step height is **2.5 blocks** (the vanilla attribute, set on the entity), and
if the dash meets a lip it looks one block ahead and hops it in a single tick rather than losing its
momentum. A fence does not stop a charge; a three-block wall does.

### Dying

Beef Supreme **explodes on death** (power 6) and leaves **three stacks of steak** — 192 cooked beef —
behind. The blast runs *first* and the steak is spawned *afterwards*, because an item entity inside a
blast radius is destroyed; "explodes leaving steak behind" has to mean the steak survives its own
explosion. Vanilla cow loot (raw beef, leather) is suppressed.

The death blast **damages entities but does not break blocks**: a 5000-health boss that deletes a base
when it dies is a griefing tool, not a fight. Flip `breaksBlocksOnDeath()` to `true` if you want it to
carve terrain.

Attributes are real attributes (armour 8, knockback resistance 1, follow range 48), so enchantments,
armour and potions all behave normally against it. It cannot be leashed, cannot drown, and a killed Beef
Supreme restores its own movement speed so nothing leaks.

The ram's numbers and state machine live in `entity/RamTiming.java` — no Minecraft imports — so the
harness checks the 5-second telegraph, the 30 damage, the dash speed and that a charge cannot connect
twice (18 assertions).

---
---

# Round 28: missile modules, tiered range, ridable missiles

## 0. The module is the airframe. The warhead is the cargo.

Up to this round every missile item *was* its warhead: `fission_missile` was a missile that happened to
detonate as a fission bomb. That is a shopping decision — you pick a weapon off a shelf.

A **module** splits the two halves apart:

| The module decides | The warhead decides |
|---|---|
| how far the missile can fly | what happens when it gets there |

**Any warhead fits any module.** A T1 frame carrying an antimatter warhead is a 10,000-block
sledgehammer; a T5 frame carrying a dud is a very expensive way to deliver nothing. Neither choice
locks the other, and that is exactly the point — it turns range into a *build* decision.

Five modules, ids `missile_module_t1` … `missile_module_t5`:

| Module | Range | Blocks | Crafted from |
|---|---|---|---|
| **T1** | 10,000 | the number you asked for | 8 iron + redstone block |
| **T2** | 25,000 | 2.5× the T1 | T1 + 4 gold |
| **T3** | 50,000 | regional | T2 + 4 diamond |
| **T4** | 150,000 | strategic | T3 + 4 enriched uranium |
| **T5** | **infinite** | anywhere in the dimension | T4 + 4 nether star |

A plain missile (an old `mrbm`, `icbm`, `fission_missile`, …) has **no** module and is treated as **T5**:
everything that worked before this round keeps working, unchanged. That default lives in
`ModuleTier.DEFAULT` and is asserted by the harness, because "I added tiers and all my old missiles
stopped reaching" is the one regression that would matter most.

### Fitting

Right-click a module with **a missile in your other hand**: the missile is consumed and its warhead,
funding, spell and payload move onto the module. Right-click with an empty hand to hear what is fitted.
**Sneak + empty hand** pulls the warhead back out as the original missile item (`MissileItem.plainFrom`
scans the registry and skips module items, so you get the real item back, not a copy of the frame).

Funding and payload travel with the warhead, not the frame, because they are properties of *the weapon*
— loading a missile with warhead-specific cargo and then moving that warhead to a bigger frame should
keep the cargo.

### The range gate

`LaunchPadBlockEntity.fireAt` compares the **squared** distance to the target against the tier *before*
it launches. Squared, because a 150,000-block shot squared is 2.25e10 — comfortably inside a `double`,
and no `sqrt` on the hot path. T5 skips the comparison entirely, which also keeps the maths away from
`Long` overflow.

A refusal is **not silent**: the pad remembers the shortfall (`lastRangeRefusal`, in blocks) and the tier
that ran out of leg, and the launcher GUI prints
`Missile Module T1 cannot reach the target - short by 2,431 blocks.` The desyncable alternative — a
player firing, seeing nothing happen, and inventorying it as a bug — was not worth the code it saves.

### Firing an empty module

An empty module is a **DUD**, not a broken item. It launches, it flies, it arrives, and it goes *clunk*:
grey cloud particles, an item-break sound, and a broadcast line. It does no damage and breaks no blocks.
An empty frame is a reconnaissance round you can watch on the radar, a decoy you did not have to craft,
or a very direct way to tell somebody you know where their base is.

## 1. Riding missiles

**Sneak + empty hand** on a loaded pad boards the missile. Sneak + empty hand again climbs off. You do
**not** fire from the pad while riding it — mounting is one gesture and firing is another, and the person
riding is usually not the person who should be pressing the button.

On launch the rider is attached with `startRiding(entity, true)` *before* the missile is removed from the
pad, so there is no tick where the entity is orphaned. Dismount rules:

- `MissileEntity.canAddPassenger` is only true when the missile is **empty**, so a rider cannot be
  stacked on or used as a free teleport for a second player.
- Before the warhead detonates, **`ejectRiders`** runs: the passenger is dismounted, pushed to **+12 Y**,
  and given **slow falling for 400 ticks** and **fire resistance for 200** — with a message telling them
  they were thrown clear.
- Ejection happens **before** `warhead.detonate`, never after, because the alternative is a passenger
  standing in a fission blast.

Riding is the whole ICBM fantasy: aim at a coordinate, climb on, and let go. The slow falling is what
makes it survivable rather than a suicide pact.

## 2. New models — the launcher trio

All three blocks were `cube_bottom_top`: a single textured cube. They now have real geometry, built from
**new element lists and new 16×16 textures**, drawn from the *shape language* of old ICBM — a square deck
with a raised rim, hold-down clamps, hazard striping, a slanted readout hood, a trussed support column —
while being geometrically our own. Nothing here is a copy, an extract, or a recolour of another mod's
asset file; the models are hand-written JSON and the textures were generated pixel by pixel for this mod.

| Block | Model |
|---|---|
| **Silo / launcher pad** | 3-px deck plate, a raised square rim, a central well with **two blast doors and a seam**, four chunky **hold-down clamps** at the corners, hazard banding on the sides |
| **Launcher panel** | console body on a plinth, two side handles, a **slanted screen hood** rotated 22.5° on X, a recessed switch/button strip, indicator lamps |
| **Support rod** | base plate, central column, four **corner struts** with hazard-striped bracing, three **lattice rings** with collars, and a yoke cap |

New textures: `launch_pad_{top,side,bottom,rim,hatch}`, `control_panel_{side,bottom,top,screen,face}`,
`support_rod_{side,top,bottom,brace}`. The three item models also got explicit `display` transforms, since
a parent-less block model has no inventory transform and would render at the wrong size in the hand and
on the ground.

**Everything is solid, unrotated geometry** apart from the one 22.5° hood, and all three blocks stay
`.noOcclusion()` so the pad can be placed against a wall.


---

# Round 29b: the constructor gets its own hold

The constructor warhead was the odd one out: cargo missiles had a chest GUI, the constructor still made you
fill the **launch pad's** hold and set its radius from the pad's slider. That is backwards - the missile is
the thing you carry, so the missile is the thing that should hold the payload.

## What changed

- **`MissileItem.acceptsPayload()` now returns true for CARGO *and* CONSTRUCTOR.** Right-clicking a
  constructor missile opens the same 27-slot chest as a cargo missile. The screen title says
  "Constructor Payload" instead of "cargo hold".
- **A radius slider lives in that screen**, under the player's inventory: the same log scale and the same
  4096 ceiling as the launch pad's slider, because two sliders that disagree about what half-way means
  would be worse than no slider at all.
  - Drag the track, or type an exact number in the box, or press **auto** to clear the override and let
    the payload decide.
  - The readout under the track prints the radius **and what it costs in blocks and stacks**
    (`radius 32 blocks` / `137,257 blocks (2,145 stacks)`), so you can see that a 27-slot hold is a
    radius-7 sphere (1,419 blocks) before you launch.
- **The grid only accepts blocks** (and AE2 item cells, which are handed over whole). A constructor that
  let you fill it with arrows and mob eggs was already skipping them at build time; refusing them in the
  GUI is the honest version of the same rule.
- **The launch pad still works exactly as before**: it merges the missile's own hold with the pad's
  cargo, and the missile's radius override wins over the pad's slider. So a constructor loaded on the
  bench and dropped into a launcher keeps its sphere size.

## How the slider talks to the server

A slider drag is a stream of clicks. Every one of them could be a packet, but vanilla already has a
channel for "a number, about this menu": the container button click
(`ServerboundContainerButtonClickPacket`). So **the button id is the radius**, and -1 (or any
non-positive id) clears the override:

```java
@Override
public boolean clickMenuButton(Player player, int id) {
    if (!constructor || !stillValid(player)) {
        return false;
    }
    setRadius(id <= 0 ? -1 : id);
    return true;
}
```

The menu refuses the click unless the missile is genuinely a constructor in the player's own hand, so a
client cannot use it to write a radius onto something else. The screen only sends when the number actually
changed, so a full drag across the track is a few dozen clicks rather than one per pixel.

## Numbers

`ConstructorBlast.blocksInSphereLong(int)` is new: the int version clamps at `Integer.MAX_VALUE`, which is
right for "can this payload fill the sphere" and wrong for a GUI that wants to print the real number — a
radius-4096 sphere is about **2.9 × 10¹¹** blocks. One loop serves both, so there is still exactly one
definition of a sphere in the mod. The harness checks r0..r4 (1, 7, 33, 123, 257), int/long agreement,
monotonic growth to r256, the 4096 no-overflow case, and that a full 27-stack hold fills a radius-7 sphere
with 309 blocks spare.


---

# Round 30: silo access, a working slider, and the spatial cell drive

Three reported problems, three root causes.

## 1. "None of the sliders work" — the hit box was never where the bar was

The launch pad's radius slider was hand-rolled: `mouseClicked` tested `sliderY..sliderY+6`, and the bar was
drawn by a separate code path at a different y. There was nothing visible to grab, and the clicks went to
the panel. The constructor's slider I added last round had the mirror-image bug: it *drew* fine but
`radius()` read the **missile ItemStack**, and the item in your hand is not a menu slot — so nothing ever
synced the server's value back to the screen and the knob never moved.

Both are fixed the same way:

- **`client/gui/RadiusSlider`** is a real widget (`AbstractSliderButton`), which owns click capture, drag
  capture, arrow keys and the change callback; only the drawing and the log-scale value mapping are ours.
  One callback per actual change, not per pixel dragged. Radius **0 means "auto"** and the label says so.
- **The constructor's radius syncs through a menu data slot** (`DATA_RADIUS`) instead of the item. On the
  server, `setRadius` writes the component *and* the cached value; on the client, the data slot's `set()`
  writes the cache and the screen mirrors it into the widget every frame.
- The pad's informational gauge now uses `RadiusSlider.fractionOf`, so the gauge and the draggable bar
  can never disagree about where 64 blocks sits.

## 2. "I can't access the silo inventory"

The pad has had a real 27-slot cargo inventory since round 1 — hoppers, AE2 import buses and the `cargo`
console command could all fill it — but **no player-facing way in**: the launch console has no slots at
all. So "load the silo" was a console command.

- **Shift + right-click the pad with an empty hand opens a 27-slot "Silo Hold" chest** (`SiloHoldMenu` /
  `SiloHoldScreen`), laid out exactly like the cargo missile's hold.
- It is a *view*, not a copy: every write goes straight through the block entity's new
  `getCargo`/`setCargo`, so a hopper filling the silo while the window is open and a player filling it
  through the window are the same write.
- **Shift + right-click with something in hand still unloads the missile** — the two gestures are told
  apart by whether your hands are empty, not by a new key.
- **Riding moved to a "Board" button** on the launch console. It had to: it was implemented as
  *sneak + empty hand in `useItemOn`*, and Minecraft never calls `useItemOn` for an empty hand — the
  branch was unreachable, which is why boarding never worked. A button is also the honest place for a
  decision that ends with you inside a warhead.

## 3. The spatial missile gets an AE2-style cell drive

The spatial warhead's payload is spatial storage cells — one item each, from a specific mod. Loading them
through the generic 27-slot grid was wrong twice: the grid accepted things the warhead cannot use, and it
looked nothing like the mod whose items it was asking for.

**Right-clicking a spatial missile now opens a drive** (`SpatialCellMenu` / `SpatialCellScreen`):
**10 bays** in two rows of five, each bay accepting **only a spatial storage cell**, one item per bay, with
a status pip per bay (lit = cell, dark = empty, **red = AE2 is not installed at all**). It writes through
the same `payload` component as every other hold, so the pad, the tooltip and the warhead keep reading one
list. A new `MissilePayload.set(...)` writes the list wholesale and drops empties, so the "inserted items
come back as air" class of bug cannot return through this path.

## Gesture table (after this round)

| Target | Gesture | Result |
|---|---|---|
| Launch pad | right-click, empty hand | launch console |
| Launch pad | **shift** + right-click, empty hand | **silo hold (27 slots)** |
| Launch pad | shift + right-click, holding a missile | unload the missile |
| Launch pad | right-click with a missile | load it |
| Launch console | **Board** button | ride / dismount the loaded missile |
| Cargo missile | right-click | 27-slot hold |
| Constructor missile | right-click | 27-slot hold **+ radius slider** |
| Spatial missile | right-click | **10-bay cell drive** |

## Round 31 — silos are built, missiles are entities, designators use frequencies

Three complaints, three structural fixes. All three are about the same thing: a launcher had no presence
in the world.

### 1. A loaded missile is an entity

Dropping a missile into a pad used to leave an `ItemStack` in a block entity — invisible, unshootable,
untargetable. It is now a real `MissileEntity`, docked:

- The pad spawns it on load, keeps its `UUID`, and re-binds to it after a chunk unload or a restart
  (`DockedMissile` in the pad's NBT). If the entity is somehow missing, it is re-created from the stack —
  the item is never lost.
- Docked ticks are zero-motion: no gravity, no ageing, no drift, no detonation, invulnerable. It stands at
  `padPos.above()`, painted upright with the roll slowed to a fifth and **no exhaust plume** — the motor is
  not lit — and sunk 0.35 blocks into the pad's hatch so it reads as standing *in* the silo, not on a plate.
- **Launching reuses that entity.** `undockAndLaunch(...)` clears the dock state, resets the flight plan
  and hands it the target, so the missile you watched sitting in the silo is the one that leaves. Clearing
  the pad's `UUID` *before* `unloadMissile()` is what stops the pad from deleting the missile it just fired.
- Unloading, and breaking the pad, discard the entity. A chunk unload does not (`setRemoved` only discards
  when the block is no longer a pad).

### 2. The launcher is a multiblock

One pad block used to be a complete ICBM installation. Now a silo is a **5×5 frame with a 3×3×8 shaft**:

| part | where | how many |
| --- | --- | --- |
| launch pad | centre | 1 |
| launcher support rod | the radius-2 ring: 4 corners + 4 edge midpoints | **8** |
| control panel | middle of any outside edge, one block further out | **1 of 4 slots** |
| clear shaft | 3×3 columns, 8 blocks above the pad | 72 blocks of nothing |

`launcher/SiloStructure` holds the pattern, the four panel slots and the check; it has **no Minecraft
imports**, so the harness proves the shape and every refusal message without a world. The pad supplies a
`Probe` that reads the nine frame positions plus the shaft, once a second from its ticker and again on
every launch attempt.

A refused launch says exactly what is missing, in order of how a player would fix it:

```
Silo incomplete: 3/8 support rods, no control panel
Silo incomplete: 1 blocks in the shaft
```

The gate is on `fireAt(...)`, so it applies to the console button, the designator, redstone and any future
terminal — there is no second path that skips it.

### 3. Designators are frequency-based, not coordinate-based

The designator used to store a `BlockPos`. That breaks the moment a silo moves, and on a **Sable subgrid**
a silo is a ship: the coordinate would keep pointing at open water. So:

- Right-clicking a pad **copies its channel** into the designator's `FREQUENCY` component. The pad's
  channel comes from a Frequency Chip, as before.
- Firing sends `DesignatorFirePayload(channel, x, y, z)` to the server, which resolves
  `FrequencyRegistry.pads(channel, dimension)` and fires **every** pad on that channel that can shoot. One
  designator now addresses a battery.
- A pad re-registers its **current** position on its channel every tick that position changes, so a moving
  grid stays on-channel with no block update. This is the whole reason for the change.
- The reply is a tally: `Launched 2 of 3 silos on channel 7`, or `No launch: <reason>` naming the actual
  reason (no missile, no panel, out of range by N blocks, reloading, dead channel).

Designators with a legacy `BOUND_PAD` component still work; the channel takes precedence.

### Also in this round

- **The five missile modules had no textures.** They were registered in round 28 and drawn as the
  missing-texture checkerboard. They now have sprites built from one silhouette: an empty bay (the point of
  a module), a collar, and 1–5 tier pips down the booster, with the T5 frame charged.
- Dock pose added to `MissileRenderer` (upright, slow roll, sunk, no plume).

### Suites

logic **392** (+24 silo assertions), api 51, lua 40, sweep 729 keys / 99 recipes, NAMES, SYNTAX (237 files)
— all PASS. Jar: **1,156,828 bytes, 999 entries, 394 classes**.

## Round 31a — the Clucker crash

**Crash:** `NullPointerException: Cannot invoke AttributeSupplier.getValue(Holder) because "this.supplier"
is null` → `TurretChickenEntity.<init>` → `CluckerStrike.release` → the Clucker warhead detonating. Hard
server crash, world unloadable until the offending area was cleared.

**Cause.** `LivingEntity`'s constructor builds its `AttributeMap` from
`DefaultAttributes.getSupplier(type)`, and that table is populated from NeoForge's
`EntityAttributeCreationEvent`. `SCGEntities` registered attributes for Beef Supreme and **not** for the
turret chicken — a living entity with no supplier gets a null one, and the constructor's own
`setHealth(getMaxHealth())` NPEs immediately. So every Clucker impact crashed the server, and so would a
turret egg hatch.

**Fix.** `event.put(TURRET_CHICKEN.get(), Chicken.createAttributes().build())` — the vanilla chicken's
supplier, not hand-written numbers, so a datapack that retunes chickens retunes these too.

**Guard.** `tools/sweep.py` now fails the build if any registered entity type builds a living class (a
list of 11 vanilla bases) without an `event.put(...)` entry. Verified by removing the fix and watching it
report:

```
entity TurretChickenEntity (TURRET_CHICKEN, extends Chicken) has no EntityAttributeCreationEvent entry
- constructing it will NPE and crash the server
```

Every living entity you register in NeoForge needs one of these. It is not optional bookkeeping; it is the
difference between a warhead and a corrupt world.

Suites: logic 392, api 51, lua 40, sweep **729 keys + entity-attribute check**, NAMES, SYNTAX (237) PASS.
Jar: **1,156,828 bytes, 999 entries, 394 classes**.

## Round 31b — the egg crash

**Crash:** `IllegalArgumentException: Empty stacks are not allowed` → `ItemParticleOption.<init>` →
`ThrownEgg.handleEntityEvent` → client packet handler. Fatal on the **client**, unrecoverable.

**Cause.** `TurretChickenEntity#shootAt` did `egg.setItem(ItemStack.EMPTY)`. Vanilla's egg break effect is
entity event 3, and its handler is client-side:

```java
if (id == 3) {
    ItemParticleOption particle = new ItemParticleOption(ParticleTypes.ITEM, this.getItem());
```

`ItemParticleOption` throws on an empty stack. So every turret egg that hit something sent a packet that
killed the game. Vanilla sets `Items.EGG` on a thrown egg for exactly this reason; ours had been cleared.

**Fix.** The egg now always carries `Items.EGG` — set in `shootAt` and again in all three `TurretEggEntity`
constructors, so no future spawn path can reproduce it.

**Guard.** `tools/sweep.py` gained `check_particles()`: any `setItem(ItemStack.EMPTY)` anywhere in the
source fails the build, because vanilla's break/impact effects read `getItem()` on the *client*.

### The general rule this pair of crashes teaches

Anything the client reconstructs from an entity must be non-null and non-empty on the server:

| what | needs | where it explodes |
| --- | --- | --- |
| attribute supplier | `EntityAttributeCreationEvent` entry | server, in the constructor |
| carried item stack | a real stack, never `ItemStack.EMPTY` | client, in the packet handler |

Both are now swept at build time, so neither can ship again.

Suites: logic 392, api 51, lua 40, sweep 729 keys + entity-attribute + particle-stack checks, NAMES,
SYNTAX (237) PASS. Jar: **1,156,828 bytes, 999 entries, 394 classes**.

## Round 31c — turret eggs do 2.5 hearts

Vanilla thrown eggs deal **zero** damage; they only knock back. A turret egg is ammunition, so
`TurretEggEntity` now overrides `onHitEntity` and deals `DAMAGE = 5.0F` (2.5 hearts) on every hit.

Two details worth keeping:

- **`super.onHitEntity` is deliberately not called.** Vanilla's implementation is
  `entity.hurt(..., 0.0F)`; a zero-damage hurt can still open the target's invulnerability window, which
  would then eat part of the real hit in the same tick. One hurt call, with the real number.
- **It skips its owner**, so a chicken standing in its own splash cannot shoot itself in the back.

2.5 hearts is a designed number, not a balance accident: a flock is a real threat to an unarmoured
player and armour genuinely matters, but a single egg never kills anyone who is paying attention.

Jar: **1,156,828 bytes, 999 entries, 394 classes**.

## Round 32 — chicken tactics, and four new missile frames

### The chickens now fight like soldiers, badly

The old turret chicken had one goal: shoot whatever it found inside 16 blocks. It did not come to you, it
did not keep track of you, and it looked wherever physics happened to point it. Three goals change that,
with all the tuning in `entity/ChickenTactics` (pure Java, harness-tested):

| goal | priority | what it does |
| --- | --- | --- |
| `AcquireGoal` | 1 | finds an enemy inside **24 blocks**, holds it as a *mark*, and remembers it for **5 s** after losing sight |
| `StareGoal` | 3 | turns the whole bird to face the mark, every tick, at any range it can see — **35°/tick**, not a snap |
| `ChaseGoal` | 4 | walks toward the mark until it is **5 blocks** away, re-pathing every **15 ticks**, never crowding into sword range |
| `ShootGoal` | 5 | lobs an egg inside **16 blocks** (2.5 hearts per hit) |

The result is the behaviour that was asked for: a chicken notices you from further than it can shoot,
walking out of the treeline while already looking at you, closes to just outside arm's reach, and keeps
firing. Lose it behind a wall and it comes round the corner instead of forgetting you existed. All of this
loses to vanilla's panic goals at priorities 0–2, so a chicken in water still swims and one on fire still
runs.

Scanning is on a 10-tick timer rather than every tick, and re-pathing on 15 — fifty chickens all pathfinding
per tick is how a warhead becomes a server stall.

### Four new missile frames

`launcher/ModuleKind` separates **what a frame is for** from **how far it goes** (`ModuleTier`). Any warhead
fits any frame except the transport; the tier and the kind are independent choices.

| item | frame | tier / range | what it does |
| --- | --- | --- | --- |
| Missile Module — Homing | `HOMING` | T3 / 50,000 | re-acquires every 10 ticks and re-solves its arc, so it bends toward where the target **is**. Uses a Tracker lock if the pad has one, else the nearest living thing within 96 blocks. Ignores its own passengers. |
| Missile Module — Cluster | `CLUSTER` | T4 / 150,000 | breaks into **6 sub-munitions** at 56 blocks out, each aimed at its own point on a 14-block ring. Each carries 60 %/6 of the parent's power and its share of the funding. Children are forced to `STANDARD`, so it cannot split forever. |
| Missile Module — High Velocity | `HIGH_VELOCITY` | T2 / 25,000 | **2.4×** cruise and terminal speed, with the speed ceiling scaled to match so it is not immediately clamped away. The point is that point defence has less time. |
| Missile Module — Transport | `TRANSPORT` | T5 / unlimited | **does not detonate.** On impact it sets every passenger down on the ground surface under the impact point — teleported, `fallDistance` cleared, zero velocity, plus slow falling as belt and braces — and drops the hold as luggage. |

Two design details worth keeping:

- **The transport frame refuses a warhead at the bench**, not at the impact. A player who fits an antimatter
  warhead to a personnel frame and then boards it has misunderstood something, and the crafting table is
  where to say so.
- **"Negates fall damage" is done by never dropping the passenger at all.** They are *placed*, not
  ejected: `teleportTo` the ground surface, `fallDistance = 0`, `resetFallDistance()`. Slow falling on top
  means a mid-motion tick cannot take the hit either.

Recipes are one upgrade path per frame: the plain frame of the matching tier plus a signature ingredient
(ender eye / gunpowder+firework stars / firework rockets / shulker shell).

### Suites

logic **429** (+37 for frames and chicken tactics), api 51, lua 40, sweep **746 keys / 103 recipes**,
NAMES, SYNTAX (239 files) — all PASS. Jar: **1,172,024 bytes, 1,016 entries, 399 classes**.

## Round 33 — every frame in every tier, and who may carry cargo

### 5 kinds x 5 tiers = 25 modules

The four special frames are no longer one-off items. Each kind now exists at **T1 ... T5**, so "what it
does" and "how far it goes" are independent choices:

| kind | T1 | T2 | T3 | T4 | T5 |
| --- | --- | --- | --- | --- | --- |
| standard `missile_module_tN` | 10k | 25k | 50k | 150k | ∞ |
| homing `missile_module_homing_tN` | 10k | 25k | 50k | 150k | ∞ |
| cluster `missile_module_cluster_tN` | 10k | 25k | 50k | 150k | ∞ |
| high velocity `missile_module_high_velocity_tN` | 10k | 25k | 50k | 150k | ∞ |
| transport `missile_module_transport_tN` | 10k | 25k | 50k | 150k | ∞ |

A T5 homing frame is a strategic weapon that follows you; a T1 homing frame is a short-range one that does
the same thing badly. Same for the rest.

They are registered by a loop (`registerModuleKind`) rather than 20 copy-pasted fields — 20 near-identical
fields is 20 chances to paste the wrong tier. Sprite, model and recipe are generated per id too: each one
keeps the kind's nose icon (crosshair / three dots / chevron / canopy) **plus** the tier pips on the booster,
so a T3 cluster and a T5 cluster are the same design with a different ladder stamp.

Recipes: the **standard frame of the matching tier** plus four of the kind's signature ingredient (ender eye
/ gunpowder / firework rocket / shulker shell) and a little iron. So the tier ladder is still climbed on the
plain frames, and the specialisation is a separate, parallel cost.

### Warhead compatibility

All frames take all warheads, with two rules — both enforced **at the bench**, not at the impact:

| frame | warheads it accepts |
| --- | --- |
| standard, homing, high velocity | **everything**, including cargo |
| cluster | everything **except cargo** |
| transport | **nothing** — it carries crew |

The cargo restriction exists to close a duplication hole: a Cargo warhead delivers the missile's hold to
wherever it lands, and a cluster frame lands six times. Six copies of the same crate is item duplication
with extra steps. Cargo is therefore legal only on the frames that arrive exactly once.

`ModuleKind.allowsWarhead(id)` is the single source of truth (compared by id string, so the class stays
pure Java and harness-testable), `MissileModuleItem` refuses the fit with a translated reason, and a
cluster split still only gives the hold to its first sub-munition as a second line of defence.

### Suites

logic **441** (+12: the full warhead-compatibility matrix), api 51, lua 40, sweep **763 keys / 119
recipes / 121 items**, NAMES (101 items, all translated), SYNTAX (239 files) — all PASS.
Jar: **1,192,628 bytes, 1,064 entries, 399 classes**.

## Round 34 — the kamikaze vest and the hand launcher

Two "one person, one missile" items, at opposite ends of the same idea: one fires ordnance, the other
*is* ordnance.

### Hand Launcher

A shoulder tube that fires **T1 and T2 missiles straight out of your inventory**.

- Right-click fires the first hand-launchable missile in your inventory at whatever you are looking at
  (256-block ray, aimed at the far end of the look vector if you hit nothing).
- **T1 and T2 only.** `KamikazePlan.HAND_LAUNCHER_MAX_TIER = 2`. Plain (pre-module) missiles count as
  `ModuleTier.DEFAULT`, which is T5 — so they are **not** hand-launchable. They are silo hardware.
- 30-tick cooldown, 96 durability, no GUI and no magazine: there is no loading state to get wrong and no
  way to be "holding the wrong missile".
- The missile spawns a step ahead of the eye so it cannot clip its own launch tube.

### Kamikaze Vest

A chestplate that pays out exactly once: **on death**.

1. **Detonates** — HE on the warhead scale, power 30 (an HE warhead is 8–16), plus an emitter blast.
2. **Launches every missile you were carrying**, in **random directions** — random bearing, random
   48–176 block distance, random pitch. It is not a targeting system, it is a tantrum; some go into the
   sky, some go into whatever is nearest, none are aimed at the person who killed you.
   Capped at `MAX_SALVO = 12` so a prepared bomber is terrifying but the server survives it.
3. **Lights and throws every explosive** — TNT becomes a live `PrimedTnt` with a 30–70 tick fuse, thrown
   outward and upward so the pile *scatters* instead of all cooking off inside the crater the vest just
   made. Anything else tagged `c:explosives` (TNT minecarts, end crystals, plus anything a mod or datapack
   adds) is thrown clear as an item.
4. Carried ordnance is **consumed before death drops are generated**, so a bomber does not leave a loot
   pile of live weapons sitting in their own crater.

The armour itself is *deliberately bad*: leather-grade protection, no toughness, no knockback resistance.
It must not be a strictly better chestplate that also explodes, or everyone would wear it forever. The item
is a trade — a real chestplate slot for a bomb you can only use once.

`warhead/KamikazePlan` holds every number (tier cap, salvo cap, aim scatter, throw velocity, fuse) with no
Minecraft imports, so the harness proves the tier gate, the salvo cap, that the aim directions really are
scattered over the compass, and that explosives are thrown outward.

### Suites

logic **462** (+21 for the vest and the launcher), api 51, lua 40, sweep **777 keys / 121 recipes /
123 items**, NAMES, SYNTAX (244 files) — all PASS.
Jar: **1,206,667 bytes, 1,080 entries, 404 classes**.

---

## Round 35 — packaged warheads, payload-aware vest, carpet barrage

### Every warhead in three more delivery forms

A silo missile is not the only way to deliver a warhead. Every one of the **38** warheads now also exists
as a **block**, a **grenade**, and a **minecart explosive** — 114 new ids, all generated from
`WarheadType.values()`, never hand-listed.

| Form | id | power | fuse | trigger |
|---|---|---|---|---|
| Block | `explosive_<warhead>` | ×1.00 (the warhead's own number) | 80 ticks (4 s) | redstone or right-click |
| Grenade | `grenade_<warhead>` | ×0.35 | 40 ticks (2 s) | thrown, on impact/timer |
| Minecart | `minecart_<warhead>` | ×1.15 | 0 | **impact only** |

`power = max(0.5, warheadPower × scale)`, so even the DUD (power 0) still fizzes rather than failing
silently. **DUD is included**: "every warhead has all three forms" is a rule a player can remember;
"every warhead except one" is not. A dud charge is a training prop.

- **Block** — `block/ExplosiveBlock`. The warhead lives in a field on the block, not in a blockstate:
  a 38-value state property nothing can switch on cheaply buys nothing. Redstone **or** a right-click
  primes it (no flint-and-steel hunt per variant); once primed it follows its own scheduled fuse, so a
  row of them behaves like a fuse train even when the first blast destroys the neighbours.
- **Grenade** — `item/ExplosiveGrenadeItem` + `entity/GrenadeEntity` (a `ThrowableItemProjectile`, i.e.
  vanilla's snowball machinery: gravity, drag, collision and the thrown-item renderer for free). The
  warhead id is stamped into the stack's `module_warhead` component as well as being a field on the item,
  so the entity and the item can never disagree about what is about to go off.
- **Minecart** — `entity/WarheadMinecart extends MinecartTNT`. Rails, physics, activator-rail priming and
  the renderer are all vanilla; only `explode()` is overridden, which is the whole feature. The warhead is
  saved in the entity's NBT, so a cart picked up and re-placed is still the same weapon.
  `item/WarheadMinecartItem` reimplements vanilla's rail placement because `MinecartItem` hard-codes the
  vanilla cart in a static factory — a subclass cannot change what it spawns.

Recipes are derived from each warhead's **missile** recipe: the same signature material, minus the
airframe. `grenade_x` = gunpowder + iron + signature → 2; `explosive_x` = 4 × `grenade_x` (four throwables
become one wall-breaker); `minecart_x` = minecart + `explosive_x`. Two warheads with no missile recipe of
their own get hand-picked ingredients (HE → TNT, DUD → string), and any signature collision falls back to
the missile item so no two recipes ever share inputs (Minecraft would only match the first).

`warhead/ExplosivePackaging` holds the prefixes, scales, fuses and the power clamp with no Minecraft
imports, so the harness proves all 114 ids are distinct and the scales are the documented ones.

### The vest inherits its payload

Previously the kamikaze vest always detonated as a flat `VEST_POWER 30` HE charge. Now:

- **Right-click a placed charge block with the vest in hand** → the block is consumed, the warhead id is
  written into the vest's new `vest_payload` data component, and the suit detonates as *that* warhead.
- **Sneak-right-click** with a loaded vest empties it back to the built-in charge.
- The tooltip shows the loaded warhead; the clearing radius scales with the payload
  (`VEST_CLEARING × max(1, power / VEST_POWER)`) so a loaded antimatter suit is visibly worse.
- The payload survives death, storage and dimension travel, because it is a data component and not a
  field: `Kamikaze.payloadOf(wearer)` reads the chest slot, `Kamikaze.load(stack, warhead)` writes it.

### Carpet barrage — the example terminal program

`barrage <x> <z> [extent]` makes **every armed panel on the terminal's channel fire one missile into its
own cell of a grid** sized from the launchers' blast radius.

The arithmetic (`warhead/BarragePlan`, `computer/BarragePlanner`, both Minecraft-free): put shots on a
square grid spaced `s` apart; the worst-covered point of a cell is its corner, `s/√2` from the nearest
shot. Exact cover is therefore `s = r·√2`; pull the shots 25 % closer for the overlap the brief asked for,
so **`s = r·√2·0.75`** (r = 10 → 10.6 blocks). Spacing always follows the **smallest** warhead in the
group, never the average, so a small warhead cannot leave a hole next to a big one.

- **One pad fires one missile**, so the carpet is built out of *launchers*: N launchers make the largest
  square grid that fits (1, 4, 9, 16 …), centred outward, spare launchers reported rather than doubled up
  on an already-covered crater.
- Empty, disarmed or structurally broken launchers are **skipped and counted**, never fired.
- Aim points get the terrain height at the target column, so the missiles come down on the surface rather
  than into a hillside.
- Asking for more extent than the grid covers is reported as a shortfall with the number of silos it would
  need — not silently under-delivered.

The example program itself ships two ways:

- **Bundled terminal programs** (`computer/ProgramStore.EXAMPLES`, installed on the first terminal command
  and never overwriting an edit): `barrage` (arm → carpet the area → report), `barrage96` (the same pinned
  to a 96-block square) and `inspect` (pads → panel list → status). `prog show barrage`, `prog run barrage`.
- **`assets/shadowclangaming/terminal/barrage.lua`** — the same maths written in Lua (`spacing`, `plan`,
  `distribute`) for players who want to lift it into their own script. `script barrage` prints it to the
  terminal. Writing it found a real bug: our Lua `string.format` consumed an argument for `%%`, shifting
  every later value — fixed in `lua/Stdlib`, with a harness test.

### Suites

logic **504** (+42 for packaging and barrage), api 51, lua **41** (+1 for `%%`),
sweep **898 keys / 235 recipes / 199 items / 60 blocks**, NAMES, SYNTAX (252 files) — all PASS.
Jar: **1,399,753 bytes, 1,551 entries, 417 classes**.

---

## Round 36 — signed channels, custom settings, and consoles that fit

### "Designator is not tuned to a channel" — the -193 bug

The designator stored its channel as a plain `int` and treated **`channel < 0` as "unbound"**. Channels
were also clamped to 0..99 by the chip and the pad's `freq` command, so the only "negative" that could ever
exist was the sentinel. Tune a pad to -193 and the designator, reading a perfectly good channel, concluded
it had never been paired.

Fixed properly, not patched around:

- **`computer/Channels`** (pure Java, harness-tested) owns the rules: `NONE = Integer.MIN_VALUE` is the only
  "unset" value, real channels are **signed** in the range ±999,999, and everything that enters the system
  goes through `sanitise` once.
- The pad, cable and terminal store `Channels.NONE` when unset and expose `hasChannel()`; every
  `frequency < 0` test in the mod is gone.
- `TargetDesignatorItem.hasChannel(stack)` decides "tuned" by the **component's presence**, never by its
  sign. A designator saved with -193 in an existing world now works immediately — the save was always fine,
  only the reading was wrong.
- The Frequency Chip no longer folds values through `floorMod` (which turned -193 into 7). It now steps
  **+1 on right-click, -1 on sneak-right-click**, wrapping over the whole signed range.
- `freq <n>` accepts any sign on the pad, the panel (forwarded to its pad) and the terminal.
- A designator can now also be tuned by right-clicking a **cable** or a **terminal**, so an exact channel —
  negative included — can be set anywhere there is wire, not only at a pad.

### Custom frequency, coordinates and cluster split altitude

New typed fields on the launch pad console. Each one submits the same console verb a player would type, so
the two paths cannot drift and the server keeps the authority:

| Field | Verb | Notes |
|---|---|---|
| channel | `freq <n>` | any sign, committed as you type |
| split y | `split <y>` / `split auto` | stored **on the missile**, so it survives unloading and re-loading |
| target x / y / z | `target <x> <y> <z>` | also on the "set target" button, and ENTER in any field |

**Split altitude** is the new one. A cluster warhead used to open at a fixed
`ModuleKind.CLUSTER_TRIGGER` (56 blocks from the aim point). It now carries a `split_altitude` data
component: when set, the missile opens when it is **descending at or below that Y**. "Descending" is load
bearing — a silo in the mountains starts below its own split altitude, and without that check the missile
would break up in the tube. The rule lives in `warhead/SplitRule` (no Minecraft imports) so the harness
proves both cases, and `warhead/MissilePayload` reads/writes the component.

### The consoles: broken, and too big

Two real layout faults, both now impossible by construction:

1. **Too big.** The pad window was 350x214 and the panel 360x210. A 1366x768 client at GUI scale 4 gives a
   341x192 canvas - both windows hung off the bottom of the screen. They are now **320x180** and
   **300x168**.
2. **Overlapping boxes.** The console strip was drawn on top of the silo panel, and the settings widgets
   landed on the buttons. Positions now come from **`client/gui/ConsoleLayout`**, a Minecraft-free class of
   rectangles, and `LogicHarness` asserts that every box is inside its window, that no two top-level boxes
   overlap, that every child sits inside its parent, and that none of the six settings fields overlap each
   other. A layout mistake is a red test, not a bug report.

Also fixed in the panel screen: the "none installed" module branch tested a helper that always returned the
module count, so it could never fire (deleted), the module rack drew 2 columns into a 58 px box that could
not hold them (now fits), and the panel gained a pad readout showing the linked silo's channel so a player
can see what a designator has to be tuned to.

### Suites

logic **617** (+113: channels, split rule, layout), api 51, lua 41,
sweep **898 keys / 235 recipes / 199 items / 60 blocks**, NAMES, SYNTAX (255 files) — all PASS.
Jar: **1,407,976 bytes, 1,554 entries, 420 classes**.

---

## Round 37 — silos that actually link, and shaders on every warhead

### "Silos still do not link to the designator"

Three separate faults, all of which had to be fixed for a designator to fire a silo:

1. **The pad ate the right-click.** `LaunchPadBlock.useItemOn` had its own designator branch that wrote the
   pad's **coordinates** into the designator and returned success — so `TargetDesignatorItem.useOn` (the code
   that copies the pad's **frequency**) never ran. The designator was left with no channel at all, and every
   right-click in the air answered "not tuned to a channel" no matter how the pad had been tuned. The branch
   now delegates to the item, exactly like the Bank Card branch next to it: one implementation of "tune the
   designator", and it lives on the item.
2. **A pad only registered on its own tick.** `FrequencyRegistry` (which the designator and the terminal
   resolve a channel through) was only updated by a Frequency Chip, or by the pad's once-a-second tick. A pad
   tuned from the console or the new channel field was therefore invisible to a designator used in the same
   second — which is exactly how a player tests it. `setFrequency` now binds immediately, and **unbinds the old
   channel** so a re-tuned silo cannot stay claimed by two channels.
3. **The pad demanded a target it was about to be given.** The fire handler refused any pad whose
   `isReady()` was false, and `isReady()` means "has a missile *and* a target". A freshly built silo has never
   been aimed, so every designator shot was refused with "no missile or no target lock" — which reads as "not
   linked". The handler now requires only a missile and takes the target **from the designator**.

Pairing is also nicer: tune the designator (chip, cable, terminal or console) and tap a silo that has no
channel — the designator writes its channel onto the pad and pairs. No Frequency Chip required for a lone silo.

### Shaders for every warhead, not just the apex weapons

"One thing to have a huge boom; another to make it stylish." The seventeen apex weapons already owned a post
chain; every other warhead had particles and a sound. Now every detonation does, through one call site:
`WarheadType.detonate` → `BlastFx.emit` → one `blast_fx` packet → the client's style chain.

**Nine styles** (`warhead/BlastStyle`, pure Java and harness-tested), mapped by *meaning* rather than
thirty-eight bespoke GLSL files — thirty-eight shaders is thirty-eight chances for one to fail on a driver:

| style | warheads | look |
|---|---|---|
| `flash` | he, dud, decoy, cluster, cargo, constructor, spatial | white-hot core, hard shockwave |
| `fire` | incendiary, ragnarok, soul_furnace, world_rune, starforge, beef, violator, clucker | orange core, embers |
| `rad` | fission, fusion, spell | sickly green wash that lingers |
| `emp` | emp, tracking, minefield | blue, thin, heavy chromatic split |
| `toxic` | contagion | crawling green vignette |
| `frost` | absolute_zero, bulwark | cyan frost creeping from the edges |
| `void` | antimatter, omega, world_eater, the imploders, the big apex-tier names | the light goes the wrong way |
| `coin` | numismatic | gold, because the blast is made of money |
| `fallback` | anything unknown | the chain of last resort |

Each chain runs as a vanilla **post chain** (no Iris/OptiFine needed, and it works *under* a shader pack):
`assets/shadowclangaming/post_effect/blast_*.json` → `shaders/program/post/blast_*.json` →
`shaders/post/blast_*.fsh`. The generator (`tools/gen_blast_shaders.py`) writes all three files per style from
one template, so a typo cannot live in one of nine near-identical shaders.

**Three degradation steps**, because a screen effect must never be able to break a frame:

1. the style's own chain
2. `blast_fallback` — if that style's shader will not compile
3. `client/BlastOverlay` — a HUD flash + vignette in plain quads, if no chain works at all

Only one full-screen pass ever runs per frame: an apex weapon owns the screen when it is active, otherwise the
blast chain does. Screen shake rides the same strength value. Resource-pack reloads (F3+T) drop and rebuild the
chains, so a pack swap cannot leave a dead sampler behind.

`/scg fx status` reports styles, config and which step each is on; `/scg fx test void` fires one effect at you.

### Config

`config/shadowclangaming-client.toml` (shaders, strength, distance, fallbacks, flash, shake) and
`config/shadowclangaming-common.toml` (effect packets, range, particle scale, blast threads). Every key has the
default the mod was tuned with, and a missing file behaves like a default one. Full table in `docs/CONFIG.md`.

### Suites

logic **711** (+94: blast styles), api 51, lua 41,
sweep **899 keys / 235 recipes / 199 items / 60 blocks / 9 shader chains**, NAMES, SYNTAX 262 — all PASS.

## Round 38 — cluster missiles duplicating sub-munitions

**Root cause:** `splitCluster` spawned the children and returned to `tick`, but never discarded the parent carrier. It stayed alive at the split point, met `shouldSplit()` again on every next tick, and spawned another set each time. This was an actual unbounded entity/item duplication bug, not an intended cluster spread.

**Fix:** set a persisted one-shot `clusterSplit` guard before spawning anything, then discard the parent immediately once all six children are added. Even if the parent is ticked again or saved/reloaded in the split window, the guard prevents a second salvo. Children still have `ModuleKind.STANDARD`, so none can split again; only the first child receives any cargo list as an additional duplicate guard.

## Round 39 — crash log: apex shader paths and planner shutdown race

Thanks for the log. It exposed two independent ShadowClanGaming bugs (not Sable):

- `Apex.shader()` deliberately stores suffixes (`runes`, `shock`, `void`), while the assets are named
  `apex_runes.json`, `apex_shock.json`, `apex_void.json`. `ApexShaderFx` used the suffix directly and asked
  Minecraft for `post_effect/runes.json`, which the exception proves does not exist. It now adds the `apex_`
  prefix; the sweep verifies every distinct suffix in the apex roster has its resource file.
- The fatal exception is `RejectedExecutionException` from `BlastScheduler.submit`: the server-stop listener
  had already terminated the shared worker pool while a staged apex weapon was still ticking and submitting
  later stages. The scheduler now detects shutdown and uses the server-thread vanilla explosion fallback;
  it also catches the narrow race where shutdown happens between the check and submission. Pending staged
  apex timelines are cleared during `ServerStoppingEvent`, and the planner is recreated on the next
  `ServerStartingEvent` (important for integrated worlds opened sequentially in one JVM).

The six `Quasar Lance detonating` messages are consistent with a cluster carrier splitting into six
sub-munitions, each carrying that warhead. They are separate intended submunition impacts, not an infinite
copy loop; the earlier Round 38 parent-discard fix stops the carrier from making another salvo. The supplied
stack trace does **not** show a Quasar-specific failure: it shows a planner executor already terminated during
server shutdown.

### Round 40 — shader source namespace and 1.21.1 lookup

The next client log narrowed the remaining apex-chain failure: the effect JSON and program JSON were now found,
but `EffectInstance` resolved the unqualified vertex id `post/apex_common` as **`minecraft:`**, then looked
for `minecraft:shaders/program/post/apex_common.vsh`. Our vertex and fragment sources were under
`assets/shadowclangaming/shaders/post/`, not the 1.21.1 program source directory. That is why the prior fix
advanced the error from missing `post_effect/rift.json` to missing `apex_common.vsh` without making the chain
load.

All program shader IDs are now fully qualified as `shadowclangaming:post/...`, and the `.vsh`/`.fsh` source
files are also present where 1.21.1 actually looks: `assets/shadowclangaming/shaders/program/post/`. The blast
shader generator emits the correct namespace and source location too. `tools/sweep.py` validates every shader
program's namespace and that its referenced vertex + fragment source exists in this exact directory, as well
as checking the apex suffix→effect file mapping.

## Round 41 — shader was loading but blacking out the screen

The latest report followed a successful resource load. Inspection found undefined GLSL usage: multiple apex and
blast fragments called `smoothstep(edge0, edge1, x)` with `edge0 > edge1`. GLSL leaves that case undefined;
some drivers return NaNs/black pixels, matching the full-screen blackout. Every reversed edge is now expressed as
`1.0 - smoothstep(low, high, x)`; apex rift/void darkening is also reduced so it cannot overwhelm the scene.

Apex shader strength now has a per-entry attack and tail driven by that apex's own `age / lifetime` (where
lifetime is its `Apex.totalTicks()`), in addition to its staged timeline curve. The shader ramps in, stays
visible through the weapon's own timeline, and fades over its final 20%; a short Quasar timeline and a long
Omega timeline therefore do not hold the same static filter. Strength is capped at 0.68 so no post effect can
black out the full image. `tools/sweep.py` now rejects reversed numeric `smoothstep` edges in every shipped
program fragment.

## Round 42 — match the shader signatures to their descriptions; `shadow_debug.log`

### The shader signatures are now genuinely distinct

The earlier nine blast files had different tints and chromatic-split amounts, but shared the same core/vignette
shader body. That did not fulfill the written signatures. The generator now gives each style a separate visual
motif in addition to its palette:

- `flash`: outward-moving white shock ring
- `fire`: animated ember flecks rising through the frame
- `rad`: pulsing green radiation waves / grade
- `emp`: blue scanlines plus the strongest channel split
- `toxic`: crawling, angular green edge haze
- `frost`: icy crystalline edge facets
- `void`: restrained radial wave with an inverted-colour/violet accent (not a black filter)
- `coin`: animated gold sparkles
- `fallback`: neutral moving ring

Apex signatures retain their separate motifs: lensing/accretion ring, white-blue corona, jagged rift, refracting
rings, blood horizon, void, rune glyphs and soul vortex. Apex overlays use their own total timeline duration,
with a four-tick attack, 20% tail fade and a 0.68 max strength cap. Shader strength can tint/distort the image;
it may not black out the display. The undefined reverse-edge `smoothstep` cases were replaced by defined
`1.0 - smoothstep(low, high, x)` ramps. The asset sweep catches that regression.

### Debug log for test reports

Every game launch writes **`logs/shadow_debug.log`** (rotated at 4 MiB to `shadow_debug.log.1`). It records
warhead/style/radius/lifetime, apex id/shader/timeline values, cluster parent/count/warhead, incoming FX packet
metadata, shader chain load/failure, and blast-planner submissions/fallbacks. Filesystem errors are best-effort
and never affect gameplay. See `docs/CONFIG.md`.

## Round 43 — black-screen containment and complete debug trace

The player's `shadow_debug.log` proves that the apex packet arrived and Minecraft constructed the `apex_rift`
chain; it did **not** prove that the chain produced valid pixels. Since it still blanked the entire screen, custom
PostChains are now explicitly **opt-in** (`screen_effects.post_chains_enabled = false` by default). The client
uses safe, styled HUD geometry instead: it never writes to `minecraft:main`, keeps the 3D scene untouched, and
preserves each blast/apex palette and motion cue. Set the config to `true` only to experiment with the custom
framebuffer chains; if they blacken the screen, set it back to `false`.

Apex safe overlays use the weapon style palette, timeline progress, moving frame/brackets and signature
accents (rune, quasar crosshair, apocalypse horizon). Blast safe overlays use the colored wash and expanding
ring plus style motifs (fire embers, EMP scanlines, toxic crawl, frost facets, coin glints, void seam).
`RenderGuiEvent.Post` draws them once at the end of the GUI, rather than once per GUI layer.

Debug output now records received apex FX packets and an `APEX_FRAME` sample every ten ticks with id, age,
lifetime, progress, strength and whether post chains are enabled. Existing entries include shader load/failure
and blast queue events. This should distinguish "packet never arrived", "effect outside reach", "queue expired"
and "post chain made bad pixels" in the next report.

## Round 44 — full-rate shader trace

`shadow_debug.log` now samples **every render frame**, with no interval/throttling: active queue entries and
selection decisions, camera/effect coordinates, distance/reach, progress/strength, config mode, post-chain
uniform inputs, framebuffer dimensions, process begin/end and elapsed microseconds, plus resource probes for
effect JSON/program JSON/vertex/fragment source. Shader exceptions are written with their full stack trace.
Writes are queued on a daemon file-writer thread to avoid stalling the render thread. Log rotation is raised to
256 MiB, preserving the previous file as `.1`; the trace can be large by design.

## Round 45 — every warhead gets its own world-space event, its own fuse, and a Soul Furnace that eats until it runs out

Three complaints, one round. "The effects are all the same small sphere with sparkles in it." "The fuse has
nothing to do with the warhead." "The Soul Furnace goes off on a schedule whether there is anything left to
eat or not." All three were correct, and all three were structural rather than tuning, so all three are
fixed below the surface.

### 1. The effects are geometry now, not a sprite

The previous world renderer drew **one camera-facing quad per detonation** through a fragment shader. From
any angle, at any distance, for any warhead, that is the same disc - which is exactly what was reported. It
was not a small explosion; it was a billboard.

`warhead/BlastMotif` is the new source of truth: **38 warheads × 12 layer kinds × 17 themes**, one row per
warhead, in pure Java with no Minecraft imports. A row is a list of layers, each with its own window in the
effect's life, its size as a fraction of the blast radius, its colour, its count and its spin:

| layer | what it draws in the world |
|---|---|
| `CORE_SPHERE` | a UV sphere shell that swells from the centre - the fireball proper |
| `SHOCKWAVE` | a thin ring racing outward along the ground: the pressure wave |
| `GROUND_CIRCLE` | a slowly turning sigil lying flat on the terrain, with radial strokes |
| `SKY_CIRCLE` | a giant circle hanging above the impact, facing down |
| `BEAM` | a vertical pillar of light (a quasar jet is three radii tall; a candle is not) |
| `ARC` | jagged crackling arcs: world-space lightning, thick polylines, not a texture |
| `EMBERS` | a rising column of motes: embers, ash, feathers, fallout |
| `SHARDS` | rotating crystalline shards: crystal, debris, coin |
| `RINGS_STACK` | concentric tilted rings: a cage, a resonance ladder, a spell array |
| `TENDRILS` | spiralling tendrils crawling outward along the ground |
| `SCAN_SHELL` | a wireframe sphere sweeping outward: an EMP front, a constructor's cage |
| `GLYPH_RING` | a ring of rune strokes drawn around the impact |

`client/BlastGeometry` builds those shapes as triangles on the CPU (and is Minecraft-free, so the harness
asserts their size and bounds), and `client/CinematicFx` submits them at `AFTER_PARTICLES`, depth-tested
against the terrain, with no depth write and no cull. Walk around one and it changes shape the way a real
object does; a hill occludes it; a window frames it. It is **not** a HUD pass and **not** a post chain -
both of those were tried in earlier rounds and both were rejected.

Three levels of redundancy, because a visual that fails silently is how this got rejected once already:

1. `shaders/core/world_blast_geom` - a two-line fragment shader (vertex colour out) that has no sampler,
   no noise and no branch to fail on an odd driver;
2. vanilla `rendertype_position_color`, if the mod shader does not register;
3. `CinematicFx.tickParticles` - world particles driven from the *same* motif table, which need no shader
   at all and always run, budgeted by distance and by `particle_scale`.

Distinct is a claim the code can now defend: the harness builds a signature string for every warhead's layer
list and fails if two warheads share one.

### 2. Durations are long, and they vary by warhead

`BlastMotif.Motif#durationTicks(power)` = `baseTicks + log1p(power) × 12`, clamped to **40..1200 ticks**
(two seconds to one minute). `baseTicks` is per-warhead: a dud is 50, HE is 130, Incendiary 300, Fission 460,
Ragnarok 660, Big Bang Seed 760, Absolute Annihilation 900, and the Soul Furnace 900 before it has eaten
anything. The style-level curve in `BlastStyle.ticksFor` keeps the same shape and now shares the same
ceiling, but the motif is authoritative: it is what the packet carries, what the client draws for, and what
the fuse is made from.

### 3. Fuses are the effect

`ExplosivePackaging.fuseTicks(warheadId, basePower, form)` derives the fuse from the warhead's own effect
duration **at the power that form delivers**, then takes that form's share and clamps it:

| form | share of the effect | clamp | trigger |
|---|---|---|---|
| block | 100% | 40..900 ticks | redstone or right-click |
| grenade | 35% | 15..320 ticks | impact lights it, the timer finishes it |
| minecart | 50% | 20..450 ticks | activator rail, collision or destruction lights it |

The minecart is the one form that does not start its clock from zero. Vanilla's TNT cart owns its own
private 80-tick fuse, so this cart takes the detonation call vanilla makes when that runs out and starts
*our* clock there instead of going off; until our clock expires, every later call is swallowed. Total cook
is therefore four seconds of priming plus the warhead's fuse - four seconds every cart has, and a second
half that differs per warhead, which is the part that was fixed.

The fixed 80/40/0 behaviour is gone. A grenade no longer detonates the instant it touches anything -
`GrenadeEntity` now sticks, sparks and counts down. `WarheadMinecart` keeps vanilla's private 80-tick fuse
at -1 (by overriding `primeMinecart` without calling super) and runs its own timer, so a rail-delivered
Absolute Annihilation cooks for as long as an Absolute Annihilation takes. Primed charges smoke and spark
(`ExplosiveBlock.animateTick`), grenade and minecart tooltips quote the fuse in seconds, and
`/shadowclangaming fx fuses` prints the whole table straight out of the code so it cannot go stale.

### 4. The Soul Furnace eats until it runs out

It is the one apex weapon whose length is a property of the battlefield, so it no longer books a stage
schedule. `warhead/apex/SoulFurnacePlan` (pure numbers, harness-tested) and `warhead/apex/SoulFurnaceStages`
(the part that touches the world) implement exactly the rule that was asked for:

1. On ignition it consumes everything in range immediately.
2. Every **100 ticks** (five seconds) it looks for more.
3. Mobs found: consume them, grow the sphere, extend the effect, report the tally, go back to 2.
4. No mobs found: terminal blast, scaled by everything it ate.

Consumed count drives all three of size, time and power:

| consumed | sphere radius | effect lifetime | terminal power |
|---|---|---|---|
| 0 | ×1.00 | 240 ticks (12 s) | ×1.00 |
| 5 | ×1.90 | 280 ticks | ×1.10 |
| 20 | ×3.00 (capped) | 400 ticks | ×1.40 |
| 75+ | ×3.00 | 840+ ticks | ×2.50 (capped) |

The visual is the one that was specified: a **growing sphere** in near-black violet (`CORE_SPHERE`),
**black and purple lightning crackling around it** (`ARC`, twenty bolts re-rolled as it grows), and **giant
red magic circles in the sky** (`SKY_CIRCLE`, 1.6 radii across, 24 strokes, plus a red ground sigil). The
server re-announces the weapon with its new radius and its new lifetime on every successful check, and the
client ratchets both (`radius` and `visualProgress` only ever increase) so eating never makes it shrink.

There is one ceiling, and it is not a design flourish: `MAX_CHECKS = 40` (two hundred seconds). A mob farm
inside the radius would otherwise keep a detonation alive forever, scanning for entities every five seconds
until somebody restarted the world. Past the ceiling the furnace releases what it has, and says so in the
log. `MIN_CHECKS = 2` is the other side of the same coin: a furnace dropped on bare rock still shows its
growth before it lets go, rather than detonating on the very first look-around.

### 5. Warheads that are not bombs still are not bombs

`Motif#finaleScale()` is `0` for the eleven warheads whose description ends somewhere other than a crater:
dud, decoy, EMP, Bulwark, Spell, Cargo, Minefield, Constructor, Spatial, Violator, Beef. They get a
spectacle - Bulwark a cage of rings, Cargo a descending column, Constructor a wireframe of the sphere it is
building - and no blast was added to any of them. The harness asserts both directions: those eleven have no
finale, and HE, Incendiary, Fission, Fusion, Antimatter, Numismatic, Soul Furnace, Omega and Absolute
Annihilation do.

### 6. Reading it back in game

```
/shadowclangaming fx status    config, packets, ranges, the style table
/shadowclangaming fx motifs    all 38 motifs: theme, base duration, layer count, finale
/shadowclangaming fx fuses     all 38 warheads × 3 forms, in seconds
/shadowclangaming fx test void one effect at you, no warhead required
```

`shadow_debug.log` gained `CINEMATIC_FRAME` (active effects, which shader is in use, whether registration
failed) and `CINEMATIC_DRAW` (effects, vertices, mod-or-vanilla program) alongside the existing
`BLAST_FX`, `FX_PACKET`, `SOUL_FURNACE_START`, `SOUL_FURNACE_CHECK` and `SOUL_FURNACE_END` entries.

### 7. What was verified, and what was not

Machine-checked in this round: `tools/sweep.py` (assets, lang, recipes, shader chains, the new geometry
program, the motif roster and the fuse wiring), `tools/check_names.py`, the logic harness (four new suites:
motifs, fuses, the furnace schedule, the geometry), the API and Lua harnesses, and a full `gradlew build`
against NeoForge 21.1.264 in CI.

**Not** verified: any of it inside a running 1.21.1 client. The geometry is depth-tested world triangles and
the shader is two lines, which is as close to certain as this gets without a game, but the honest statement
is that the jar compiles, packages and passes its own suites, and has not been seen in a world.

## Round 46 — the effects are raymarched on the GPU now, and the blast lands when the effect ends

Two corrections, both structural. "I'm looking for custom GPU shader effects and procedural
volumetric-style rendering with huge effects and stunning visuals" — round 45 drew real triangles, but
triangles with a flat colour per vertex are not a volumetric fireball. And the fuse model was backwards:
a charge burned silently for its whole fuse and then produced the blast *and* the effect together, so the
long fuse was a wait with nothing in it.

### 1. A real volumetric effect

`shaders/core/world_blast_volume` is a GPU program in three files. `client/VolumetricFx` draws a cube the
size of the blast around the detonation point and hands the frame to the fragment shader, which marches a
procedural density field through that cube:

```
for each step along the ray:
    warp the sample point by two cheap fbm fields
    n     = fbm(warped)                       the turbulence
    body  = a turbulent ball that hollows out as it ages
          | a pillar of light, for the warheads that end in one
          | a shockwave ring racing out along the ground
          | the giant circle overhead, banded and turning
          | radial streaks thrown out along the blast's own spokes
    emit  = palette(hot core -> mid -> cool edge), darkened for the void and soul themes
    accumulate emission, attenuate transmittance, stop when it goes opaque
```

There is not a texture in it. No sampler, no sprite, no billboard, no noise map: the fireball is a
function evaluated per pixel, which is why it holds up from any angle, at any distance, and at any size —
a grenade and a two-kilometre cataclysm are the same maths with different numbers.

It is drawn at `AFTER_PARTICLES`, **depth-tested against the terrain** with no depth write, so a hill
occludes it, a doorway frames it, and standing inside one puts you inside the fireball rather than
clipping it away. The shader works out for itself whether the camera is outside the proxy cube or inside
it (`gl_FrontFacing` plus a containment test) and shades each pixel exactly once either way.

**One code path serves all thirty-eight warheads.** Nothing in the GLSL branches per warhead, so there is
nothing to fall out of sync. What differs arrives as uniforms, and they are derived, not authored:
`warhead/BlastMotif.Volume` takes the shape numbers from the warhead's theme, the palette from the
motif's own layer colours, the motion from how fast its layers turn, and a per-warhead offset into the
noise field from its id — so two weapons of the same theme do not produce the same fireball in a
different colour.

| theme | pillar | ring | sky circle | turbulence | rise | dark core |
|---|---|---|---|---|---|---|
| ordnance | – | 0.75 | – | 1.00 | 0.30 | – |
| inferno | 0.05 | 0.55 | – | 1.15 | 0.85 | – |
| nuclear | 0.20 | 0.95 | – | 0.90 | 1.00 | – |
| arcane | 0.35 | 0.35 | 1.00 | 0.45 | 0.10 | 0.15 |
| stellar | 1.00 | 0.40 | 0.55 | 0.70 | 0.25 | – |
| cataclysm | 0.45 | 1.00 | 0.70 | 1.35 | 0.45 | 0.10 |
| void | 0.25 | 0.20 | 0.30 | 0.55 | −0.30 | 1.00 |
| soul | 0.30 | 0.30 | 1.00 | 0.50 | 0.30 | 0.70 |

Brightness follows the warhead too: `glow = 0.35 + finaleScale × 0.55`, so the eleven warheads that are
not bombs sit at the base value and a Ragnarok is nearly twice as bright as an HE charge. The logic
harness asserts that all thirty-eight parameter sets are distinct — if two warheads ever shared one, they
would be literally the same effect, and the build fails.

Three levels of redundancy, because a visual that fails silently is how this got rejected twice:

1. the volumetric raymarcher;
2. `client/CinematicFx` — the same motifs as CPU triangles, drawn if the program will not compile;
3. `CinematicFx.tickParticles` — world particles from the same table, which need no shader at all.

### 2. The effect is the fuse

The order used to be: silent fuse → blast + effect together. It is now: **effect → blast**, on one clock.

| form | when the charge effect starts | how long it plays | what happens at the end |
|---|---|---|---|
| placed block | the tick it is primed | its fuse | the warhead detonates |
| grenade | the tick it lands and sticks | its fuse | the warhead detonates |
| minecart | the tick it is lit | its fuse | the warhead detonates |
| missile impact | the tick it lands | the same warhead's block-form fuse | the warhead detonates |

The anticipation is not a new number. It is `ExplosivePackaging.fuseTicks` — the same per-warhead table
the fuses already read — so a Big Bang Seed that lands from orbit cooks for exactly as long as a Big Bang
Seed you put on the ground yourself, and a dud is over in 2.8 seconds because there is nothing to charge.
`warhead/ChargeSequence` holds the armed-but-not-yet-detonated warheads for the forms that arrive armed,
ticking them on the server next to the other staged weapons and discarding them on shutdown.

The two halves look different, because they are different things. A **charge** converges: the sphere
swells towards full size, the turbulence pulls inward, the palette pulses, and the particles fall in from
the blast radius towards the point where the warhead is going to go off, harder as the fuse runs down. An
**impact** does the reverse and throws everything outward with an upward plume. On the client this is one
packet field (`phase`), one motif with two layer sets (`chargeSpecs()` is the impact's own layers
re-staged: same shapes, same colours, staggered in, 55 % of the size, spinning the other way), and one
renderer.

Warheads that were never going to explode — the eleven with `finaleScale == 0` — play their effect and
then simply stop. A Cargo round lands, plays out, and delivers.

### 3. What was verified, and what was not

Machine-checked: `tools/sweep.py` (assets, lang, recipes, shader chains, motif roster, fuse derivation,
**the new core-shader joins**, **the new charge wiring**), `tools/check_names.py`, the logic harness (six
new suites across the last two rounds: motifs, fuses, the furnace schedule, the geometry, the volumetric
parameters, the charge phase), the API and Lua harnesses, and a full `gradlew build` against
NeoForge 21.1.264 in CI.

The core-shader check is worth a line of its own. It joins the program JSON to the GLSL to the Java that
drives it: every attribute the JSON declares must exist in the vertex shader, every varying must be read
by the fragment shader, and **every uniform name the Java looks up must exist in the program**. It caught
`ColorModulator` missing from the volumetric JSON on its first run — a uniform that is not declared is a
uniform that stays at zero, and every pixel would have been multiplied by zero. Invisible effects, no
error, no log line.

**Not** verified: any of it inside a running 1.21.1 client. The shader is 243 lines of GLSL that has
never been compiled by a driver here, the matrices are built from first principles rather than borrowed
from the pose stack, and the volumetric cost on a real GPU is unmeasured — `Steps` is lowered for distant
blasts and at most four volumes are marched in a frame, but that is arithmetic, not a frame time. What can
be said honestly is that it compiles, the joins are checked, and the fallbacks mean a machine that cannot
run the raymarcher still gets the warhead.

---

## Round 48 — thirty-eight different weapons, and an EMP that empties the grid

Two pieces of feedback, both fair. *"They all look almost identical and I want way more spectacular and
flair."* And: *"re-read the unique effects and make sure they are all implemented."*

They were the same complaint. Round 46 built one raymarched fireball and parameterised it over seventeen
themes — beam, ring, turbulence, rise, palette. Thirty-eight warheads therefore differed by a palette and
a handful of floats on the *same* field, which is exactly how they read: the same explosion in
thirty-eight colours. No amount of extra floats on one field fixes that. A World Eater does not need a
different fireball, it needs a black hole.

### 1. The field is a composition, not a preset

`world_blast_volume.fsh` grew from 243 lines to 431 by adding **fifteen structural primitives**,
each a separate branch of the density field with its own 0..1 weight, summed:

| feature | what it adds | who asks for it |
| --- | --- | --- |
| `Hole` | an event horizon that *removes* density, with an accretion disc and a hot rim | World Eater, Antimatter, Absolute Zero, Universal Imploder, Omega, Soul Furnace, Absolute Annihilation |
| `Star` | a blinding core with a spiky corona | Stellar Requiem, Starforge, Fusion, Big Bang Seed, Absolute Annihilation |
| `Rift` | a noise-displaced vertical tear, bright at the edges | Reality Breaker, Reality Corruptor, Spatial, Abyssal Covenant |
| `Waves` | three concentric shockwave shells on staggered clocks | Spacetime Hammer, Omega, HE, Fusion, Fission, Antimatter |
| `Meteors` | five streaks falling in from above, hashed per warhead | Ragnarok |
| `Lightning` | ridged-noise bolts radiating from the core, flickering at 23 Hz | Ragnarok, Soul Furnace, EMP, Absolute Zero, Violator |
| `Glyphs` | spokes and bands cut into the ground | World Rune, Arcane Singularity, Spell, Bulwark, Constructor |
| `SkyRing` | rings turning overhead | Ragnarok, Soul Furnace, Arcane Singularity, Spell, Tracking |
| `Portals` | six scattered dark discs with bright rims | Abyssal Covenant, Minefield, Spatial |
| `Jet` | a narrow pillar up to 2.7 radii with a bow shock | Quasar Lance, Cargo, Constructor |
| `Coins` | seven flat gold discs tumbling on a spiral | Numismatic, Beef |
| `Smoke` | heavy low-lying puff that also dims the emission | Dud, Decoy, Contagion, Violator, Beef, Clucker |
| `SubCharges` | six sub-spheres, each with its own clock | Cluster, Minefield |
| `Flash` | one hard white kick in the first 16 % of the detonation | Fusion, Antimatter, Big Bang Seed, Omega, Absolute Annihilation |
| `Dome` | a hollow faceted shell, flat on the bottom | Bulwark, Constructor |
| `Embers` | a rising fire column | Incendiary, Starforge, Fission, Ragnarok, Clucker, Beef |

### 2. Every warhead is authored, none is derived

`BlastMotif.Volume.author` is a switch with **one explicit case per warhead**, written against the
javadoc on the matching `WarheadType` constant. The theme supplies a baseline so a new warhead still
works before anybody authors it; the case then says what that warhead actually is:

```java
case "ragnarok"         -> shape.meteors(1.0F).lightning(0.9F).skyRing(0.9F).waves(0.7F)
                                 .embers(0.6F).glow(1.75F);
case "world_eater"      -> shape.hole(1.0F).swirlUp(1.0F).dark(1.0F).implode(1.0F)
                                 .glow(1.9F).smoke(0.0F).waves(0.35F).rise(-0.4F);
case "quasar_lance"     -> shape.jet(1.0F).waves(0.7F).streaks(0.6F).glow(1.8F);
case "numismatic"       -> shape.coins(1.0F).ring(0.6F).rise(0.7F);
```

Brightness went up with it: the authored apex glows are 1.5–1.95 against a derived 0.90–1.06, the proxy
cube grew from 3.0 to 3.4 radii so meteors and jets are not clipped, and the fireball hollows out as it
ages instead of staying a solid ball.

### 3. What stops it regressing

Three new machine checks, each injected with its own failure and confirmed to catch it before shipping:

* **`warheadFeatureTests()`** in the logic harness holds a floor per warhead for its documented
  signature feature — `ragnarok → meteors ≥ 0.9, lightning ≥ 0.8, skyRing ≥ 0.8` — so dropping a row
  says which warhead lost which feature. It also compares every same-theme pair's feature vector and
  fails below an L1 distance of 0.35; the closest pair now is Reality Breaker / Reality Corruptor at
  0.50.
* **`sweep.py`** insists every warhead has a row in `Volume.author`. A warhead that silently falls
  through to the default keeps only its theme baseline — precisely how thirty-eight warheads end up
  looking like one another.
* **`sweep.py`** joins the program JSON to the Java in *both* directions now. The old check caught a
  uniform the Java asked for that the program did not declare; the new one catches the reverse, which is
  worse: a uniform nobody sets keeps its JSON default of 0, silently, so a forgotten feature weight
  costs that warhead the one thing its description asked for. Removing the `Meteors` push was verified
  to fail the build.

Two more, found while writing the shader rather than by testing it:

* **`check_glsl_defined()`** — `smoothstep(edge0, edge1, x)` is undefined when `edge0 > edge1`, and
  drivers disagree: some ramp backwards, some return NaN. The Reality Breaker's tear had
  `smoothstep(0.16, 0.05, …)`. That is one warhead rendering as garbage on one graphics card and fine on
  the next, with no error anywhere. All 46 smoothstep calls are now scanned.
* **`check_proxy_box()`** — the fragment shader and `VolumetricFx` each define the proxy half-extent and
  nothing connected them. Move one and every blast grows a visible box edge or gets clipped.

### 4. The EMP drains the grid

`WarheadType.empPulse` now empties the FE of every machine inside its 14-block radius, and reports what
it took to the players who were standing there (`message.shadowclangaming.emp_drained`, plus an
`EMP_DRAIN` debug line).

The route matters. It goes through `Capabilities.EnergyStorage.BLOCK`, so a Create dynamo, an AE2
charger or anything else in the modpack is drained exactly like one of ours — every face is tried,
because a machine that only exposes its storage on one side is not unusual, and the extraction loops
until the storage says there is nothing left, because some implementations cap a single call at their
own transfer rate and would otherwise leave a big buffer mostly full.

**But the capability is not enough for our own machines.** `LaunchPadBlockEntity` and
`AutoTraderBlockEntity` advertise a deliberately receive-only `IEnergyStorage` — `extractEnergy` returns
0 so nothing can pull power back out of a pad — which means a capability-only EMP would have drained
every other mod's machine and none of ours. Those two are drained through their own `empDrain()`
instead, and `check_emp_drain()` holds both routes in place.

### 5. A bug that fell out of verifying #4

`LaunchPadBlockEntity` built its buffer as `new EnergyStorage(ENERGY_CAPACITY, ENERGY_RECEIVE, 0)`.
`fundFromEnergy()` converts stored FE into missile funding at 1 unit per FE and then spends it with
`energy.extractEnergy(capped, false)` — and with `maxExtract` at 0, NeoForge hands back 0 and the buffer
is never reduced. The launch paid out funding for energy it kept, every launch, for free, since the
round that added it. `maxExtract` is now the full capacity, which fixes that and is also what makes
`empDrain()` able to empty the pad. Nothing outside the class gains access: the capability the block
registers is still the receive-only view.

### 6. What was verified, and what was not

Machine-checked: `tools/sweep.py` (all five new checks negative-tested), `tools/check_names.py`, the
logic harness, the API and Lua harnesses, and a full `gradlew build` against NeoForge 21.1.264 in CI.

**Not** verified in a running client: the fifteen new primitives have never been compiled by a driver
here, and how they *look* is not something a sweep can tell you. The feature weights are arithmetic that
says a Ragnarok has meteors at 1.0; whether those meteors read as meteors is a judgement only the game
can make.

---

## Round 49 — solid, not transparent; the endgame weapons were detonating silently

Three reports from play-testing Round 48: the sphere is transparent, there still are not enough effects,
and there is no blast when the effect ends. All three were real, and the third was a genuine bug in
something I had reported as met.

### 1. No blast when the effect ends — the seventeen apex warheads never announced their detonation

`WarheadType.detonate` handed the seventeen endgame weapons to `ApexWarheads.fire` and `return`ed.
`BlastFx.emit` — the call that sends the impact effect to clients — sat *below* that branch. So the World
Eater, Ragnarok, the Quasar Lance, the Soul Furnace and the other thirteen played their charge effect for
the whole fuse and then did their damage with no impact effect at all. The comment above the line even
said "announces itself to the nearby clients **first**" while the code did it second.

It compiles, it runs, the warhead destroys the landscape, and nothing appears in any log. The weapons
most worth watching were the only ones with nothing to watch — which is also why Round 48's new
primitives looked like they had done nothing.

The emit now sits above the hand-off, sized to the radius the weapon will actually carve (`sliderRadius`
when the pad's slider is set, so a 512-block black hole no longer draws as a 96-block one).
`check_impact_fx_reachable()` fails the build if `BlastFx.emit` ever ends up below the first `return` in
that method again; it was verified to fire on the old ordering.

### 2. Transparent, because the composite was additive-only

```glsl
fragColor = vec4(acc, 1.0) * ColorModulator;                      // alpha pinned to 1
RenderSystem.blendFunc(SRC_ALPHA, ONE);                           // result = acc*1 + dst*1
```

`SRC_ALPHA, ONE` **adds** the effect on top of the framebuffer and never attenuates what was already
there. No density, no march length, no opacity could ever hide the terrain — the volume was
mathematically incapable of occluding anything. And `transmittance`, the accumulated opacity the march
had just computed, was thrown away on the next line.

Now premultiplied: `fragColor = vec4(acc, 1.0 - transmittance)` under `ONE, ONE_MINUS_SRC_ALPHA`. Where
the march has been attenuated to nothing the core genuinely hides the world behind it; the thin outer
skirt still glows over it. Three supporting changes:

* The fireball no longer carves its own interior out. It used to subtract up to 0.9 from the centre as
  the effect aged, leaving a hollow bubble — and a hollow bubble under an additive blend is exactly
  "transparent". The interior is dense for the whole life of the effect; only the *surface* is noisy,
  and it breaks apart at the end rather than hollowing.
* `densityScale` 9.0 → 2.2. At 9.0 the core saturated on the first march step, so the fireball rendered
  as a flat silhouette with no depth inside it. At 2.2 the core reaches opacity over two or three steps
  and the skirt over five or six, which is what gives it internal structure.
* The discard test is `opacity < 0.004 && dot(acc, acc) < 0.00002`, not brightness alone. A World Eater's
  core is deliberately black: almost no emission, completely opaque. Discarding on brightness would have
  punched a hole in the middle of the one effect that is supposed to hide the world.
* A new `Strength` uniform scales **density**, not just glow. With premultiplied alpha the opacity comes
  from accumulation, so dimming the colour alone would have left a solid black ball hanging in the world
  after the fire had burnt out.

### 3. Seven more primitives, so the ground and the air are part of it

Fifteen became twenty-two: `Cap` (mushroom stem and churning cap), `Debris` (shrapnel with streaks,
impact phase only), `Dust` (a dim ground-hugging ring rolling outward — dirt, not fire), `Vortex` (a
three-armed funnel), `Cracks` (glowing veins shot through the ground, pulsing outward), `Sparks`
(twinkling points filling the volume) and `Halo` (a wide soft bloom, which is what makes a blast read as
enormous from a distance rather than as a ball of a known size).

Every theme now has a baseline of ground and air business, so no warhead is a bare sphere, and 35
warheads author over it — `fission` gets a 0.95 mushroom cap, `world_rune` gets cracks at 1.0,
`soul_furnace` a 1.0 vortex, `absolute_annihilation` dust, debris, cracks, halo and cap together.
`world_blast_volume.fsh` is 532 lines and the program declares 47 uniforms.

### 4. What was verified, and what was not

Machine-checked: `tools/sweep.py` including the new `check_impact_fx_reachable` (negative-tested against
the old ordering), all 47 uniforms joined JSON ↔ GLSL ↔ Java in both directions, 62 `smoothstep` edges
scanned, `tools/check_names.py`, the logic harness, the API and Lua harnesses, and `gradlew build`
against NeoForge 21.1.264 in CI. Locally, the authored feature table was re-simulated: no floor
violations, and the closest same-theme pair *widened* from 0.50 to 0.70.

**Not** verified: a frame of it. Whether the core now reads as solid rather than glassy, and whether
seven more primitives reads as "much more", are both judgements only the game can make.

---

## Round 50 — why the blast was invisible, and the space physics

"Still no blast." Round 49 fixed a real bug — the seventeen apex warheads genuinely never sent an impact
packet — and it still did not fix the symptom, because there was a second, worse one underneath it.

### 1. The impact faded as `exp(-3t)` across its whole lifetime

```java
float attack = Math.min(1.0F, age / 3.0F);
float decay = (float) Math.exp(-3.0D * t);      // t = age / lifetime
return attack * decay;
```

That curve was written when an impact was a one-second flash. These are now cinematics that run for tens
of seconds, and the same formula over a 24.6-second World Eater gives:

| age | fraction of life | brightness |
| --- | --- | --- |
| 5 ticks | 0.01 | 0.97 |
| 123 ticks | 0.25 | 0.47 |
| 246 ticks | 0.50 | **0.22** |
| 369 ticks | 0.75 | 0.11 |
| 492 ticks | 1.00 | 0.05 |

A two-kilometre warhead popped for about a second and then spent twenty-four seconds invisible. Two
things made it worse, one of them mine:

* Round 49's `Strength` uniform scales **density**, not just glow — which is what makes the core opaque —
  so the fade now thinned the volume as well as dimming it. The blast evaporated.
* The distance falloff was squared. A 469-block blast has a 589-block reach, so standing at the edge of
  your own detonation gave `0.20² = 0.04`, under `VolumetricFx`'s own `strength <= 0.04` skip. The closer
  you were to a kilometre-wide explosion, the more likely it was to not be drawn at all.

The apex queue immediately below this code already had the right shape, with a comment explaining why:
*"a linear fade made a ninety-second Soul Furnace invisible for most of its own run."* The blast queue
never got it.

Both curves now live in `BlastMotif` as pure functions so the harness can assert them instead of
somebody re-deriving them by eye from a screen recording:

* `impactEnvelope(t)` — full strength for the first 75 % of the effect, smoothstep out of the last 25 %.
* `distanceFalloff(d, reach)` — `0.30 + 0.70 * proximity`, linear, with a floor. Standing at the edge of
  the 469-block blast now gives 0.44 instead of 0.04.

`impactEnvelopeTests()` pins the regression by name: *a blast is still at full strength halfway through
its own effect*, plus monotonicity, the endpoints, and that the falloff never drops under the renderer's
skip threshold.

### 2. The space physics, written from what the phenomena actually look like

Twenty-two primitives became twenty-nine. The seven new ones are not decoration — each one is the reason
the real thing reads the way it does:

| feature | the physics it is standing in for |
| --- | --- |
| `Disc` | A relativistic accretion disc with **Doppler beaming**: the side swinging toward the camera is beamed brighter *and* hotter, the receding side dim and red, so the ring is lopsided rather than a flat washer. This is the single most recognisable feature of a real black hole image. |
| `Lens` | The **photon ring** — light that orbited the horizon before escaping — bent over the top and under the bottom of the shadow. It is why a black hole reads as a ring and not a ball. |
| `Granule` | **Photospheric convection cells** boiling across a star's surface, so a star is a patchwork of bright upwellings rather than a smooth ball of light. |
| `Prominence` | Loops of plasma anchored at the surface and arcing back down. |
| `Shell` | A supernova front with **Rayleigh–Taylor fingers**: dense ejecta mushrooming outward through the shocked gas, so the shell is ragged by construction instead of a smooth bubble. |
| `Nebula` | Two octaves of noise *multiplied* together, which is what produces long filaments — single-octave noise reads as fog, not as gas. |
| `Cone` | The **Mach cone** a relativistic jet drags behind it, with standing rings where the flow re-compresses. This is what separates a quasar jet from a column of light. |

Authored onto the warheads whose descriptions are astrophysics. A World Eater is now `hole + disc + lens
+ nebula + vortex + cracks + sparks + halo + waves` — nine features, with a beamed lopsided disc and a
photon ring around the shadow. Absolute Annihilation runs sixteen. The program declares 54 uniforms and
`world_blast_volume.fsh` is 638 lines.

### 3. What was verified, and what was not

Machine-checked: `tools/sweep.py`, all 54 uniforms joined JSON ↔ GLSL ↔ Java in both directions, 77
`smoothstep` edges scanned for the undefined `edge0 > edge1` case, both `pow` calls guarded against a
negative base, `check_shape_authoring` (39 fields, 39 setters), the logic harness with the new
`impactEnvelopeTests`, the API and Lua harnesses, and `gradlew build` against NeoForge 21.1.264 in CI.
Locally re-simulated the authored table: no floor violations, 48 same-theme pairs all clear, closest now
0.90.

**Not** verified: a frame. And note that Round 49's diagnosis was incomplete — I found a real bug, fixed
it, and told you it was the cause. It was *a* cause. This one is measured rather than inferred: the
brightness curve is printed above at six points in the effect's life, and the new one is pinned by an
assertion.

---

## Round 51 — the grenade was counting a fuse on an entity that no longer existed

"I'm using grenades for testing." That was the missing fact. Three rounds of "no blast" and the answer
was in the delivery form, not the shader.

### 1. No explosive did anything, because a grenade's fuse never ticked

```java
protected void onHit(HitResult result) {
    super.onHit(result);      // <- vanilla's onHitBlock ends by calling discard()
    ...
    fuse = fuseTicks();       // armed
}

public void tick() {
    ...
    if (fuse > 0) { fuse--; return; }   // never ran again
    detonate();                          // never reached
}
```

A `ThrowableItemProjectile` that lands on a block is discarded by vanilla inside `super.onHit`. The
grenade armed, announced its charge effect, and was removed from the world — so `tick()` was never
called again, the countdown stopped at whatever it was armed with, and `detonate()` never ran. **The
grenade played its entire charge effect and then nothing happened.** Nothing in any log said so, because
the code was correct; it was just attached to an entity that had already been removed.

Every other form was fine, which is why this survived: the block uses `scheduleTick`, the missile uses
`ChargeSequence`, the minecart deliberately keeps itself alive and documents why. The grenade was the
odd one out — and the one being thrown.

The fuse now belongs to `ChargeSequence`, a server-side list ticked from `ShadowClanGaming` that does
not care whether the entity that started it still exists. `ChargeSequence.start` gained an overload
taking an explicit tick count, so a grenade cooks for the grenade-form fuse rather than the block-form
one. `check_fuse_ownership()` fails the build if any entity under `entity/` decrements a fuse field in
its own `tick()` again; negative-tested against the old grenade.

### 2. A real skybox for the stellar warheads

`Sky` is not a feature inside the blast volume — it is a **second volume**, drawn first:

* The cube is `SkyBox` radii across instead of 3.4, capped at 3000 blocks. A grenade gets a 384-block
  dome over it; a kilometre-wide cataclysm gets none, because at that point it *is* the sky.
* The vertex shader parks it on the far plane (`gl_Position.z = gl_Position.w`) with the depth test
  still on, so terrain, hills and buildings occlude it exactly as they should. Without that, a box that
  size is a wall of nebula in your face.
* Its field is evaluated from **direction only**. A skybox is a dome at infinity: sampling it by
  distance makes the nebula drift as you walk, which is the tell of a fake one. Two octaves multiplied
  for cloud, a galactic band for structure, and stars as hash cells in direction space so they stay
  pinned to the sky.

The marched box and the drawn cube have to be the same size or the march samples space the geometry does
not cover — a hard box edge on the sky, on big warheads only, which are the hardest to test. Both are
now derived from `BlastMotif.skyBoxRadii`, where the half-extent is computed first and divided back out,
so they match by construction rather than by luck. `skyBoxTests()` asserts the invariant across radii
from 1 to 4096. An earlier version of this floored the uniform at `PROXY_BOX + 1`, which silently broke
it above radius 600 — caught by simulating the numbers locally before pushing.

19 warheads get a skybox: the stellar ones at 0.9–1.0, Ragnarok at 0.85 because a blood-red sky is
literally in its description, and the rest of the endgame weapons at 0.4–0.7.

### 3. Massive magic circles

`Circle` is three concentric rings counter-rotating on the ground — each with rune teeth around the rim,
a six-lobed inscribed figure inside, and spokes — plus the same construction again overhead, larger,
thinner, and turning the opposite way so the two never lock together. World Rune and Arcane Singularity
run it at 1.0, Spell 0.9, Soul Furnace 0.8. Nine warheads have them.

That takes the shader to 31 features and 58 uniforms.

### 4. What was verified, and what was not

Machine-checked: `tools/sweep.py` with the new `check_fuse_ownership` (negative-tested), all 58 uniforms
joined JSON ↔ GLSL ↔ Java in both directions, every `smoothstep` edge scanned, every `pow` guarded
against a negative base, 41 fields / 41 setters, the logic harness with `skyBoxTests` and
`impactEnvelopeTests`, the API and Lua harnesses, and `gradlew build` against NeoForge 21.1.264 in CI.
Locally re-simulated the authored table: no floor violations, 48 same-theme pairs clear, closest 1.20.

**Not** verified: a frame. But this round's headline fix is not a visual judgement — it is a control-flow
fact about which object owns a timer, and it is now enforced by a check rather than by inspection.
