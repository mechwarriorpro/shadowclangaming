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
