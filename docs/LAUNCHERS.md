# Launchers: tiers, modules, funding, and the calculator

Everything added in this round lives or dies by one question: *how much launcher do you have, and how
much did you pay for it?* This document covers the launcher structure, the funding paths, the three new
warheads, the tracker, and the interdimensional gate.

---

## 1. The launcher structure

A launcher is not one block. It is a **pad** plus a **control panel** plus **support rods**, all within
6 blocks of each other, and it is only as good as its weakest part.

| Part | What it contributes |
|---|---|
| Launch Pad | fires; holds the missile, cargo and FE |
| Control Panel | tier, armed switch, target, modules; the thing terminals talk to |
| Support Rods | **two needed**; the lowest rod tier is the rod contribution |

```
effective level = min(pad tier, panel tier, lowest rod tier)   (rods < 2 → 0)
```

A **complete** launcher additionally requires `pad tier == panel tier` and both rods at that tier. The
report the panel prints looks like:

```
pad 5, panel 5, rods 2x5 -> level 5
```

### Upgrading

**Upgrade Kits** are the only way up. Right-click air to set the kit's tier (1–5), then use it on a pad,
panel or rod. A tier *n* kit only upgrades a tier *n−1* part — the ladder is climbed one rung at a time
and a wrong-tier kit is refused (and not consumed) rather than silently wasted.

## 2. Funding: currency **or** FE

Any funded warhead (Numismatic, Bulwark, Minefield) accepts two payment paths:

| Path | How |
|---|---|
| **Currency** | right-click a loaded pad with coins/war bonds (unchanged) |
| **FE** | pipe FE into the pad; it holds 4,000,000 FE and converts **1 FE = 1 currency unit** at launch |

1 unit per FE is the same rate the Auto-Trader uses in reverse (100 FE = $1, and $1 is 100 units), so a
base wired for power and a base stuffed with cash buy exactly the same missile.

The pad has **no FE output** — it is a capacitor, not a battery — so it cannot be used to launder FE back
into the grid.

## 3. The ballistics calculator

| Use it on… | You get |
|---|---|
| a launch pad | warhead, currency loaded, FE in pad, **resulting radius**, mines (for the mine layer), how much more buys the next block, launcher report |
| the air | a table: funding → radius for the shared blast curve, and radius → funding → mines for the mine layer |

That is the "calculator in the launcher" request, with no GUI: it prints, it does not open a window, and
it works the same for an operator typing and a player right-clicking.

## 4. New warheads

### Cargo missile
Destroys nothing. Lands, finds the first solid ground, and places a chest containing what was loaded.
Loading is done by right-clicking the pad with items while a cargo missile sits on it — 27 slots, one
per right-click, merged into existing stacks where possible. If a chest is already at the landing spot
the cargo merges into it instead of stacking chests.

### Mine layer
```
radius = 32 + funding / 250        (base 32 blocks, +1 block per $250)
each ground column in that radius: 10% chance of a mine
```

"Each block" is read as each ground **column**, not each cubic block: a 32-block *sphere* has 137,000
positions and 10% of them would be 13,000 mines. Columns give 4,225 dice at radius 32 → about 420 mines,
which is a field a server survives and a player can cross.

| Funding | Radius | Dice (columns) | Mines |
|---|---|---|---|
| 0 | 32 | 4,225 | ~422 |
| 2,500 | 42 | 7,225 | ~722 |
| 8,000 | 64 | 16,641 | ~1,664 |
| 32,000 | 128 | 66,049 | 2,048 (capped) |

Hard cap: **2,048 mines per warhead** (`Minefield.MAX_MINES`).

**Mines have no block entity.** They use vanilla random ticks — a field of 420 costs essentially nothing
when nobody is standing in it — and detonate on a random tick if anything alive (or a player stepping on
it) is within 1.4 blocks. Stepping on one is also immediate.

### Tracking missile
Homes onto whatever a **Tracker** locked. The tracker stores a **UUID**, so the lock survives death,
logout, restarts and unloaded chunks:

```
right-click an entity     lock it
right-click a launch pad  hand the lock to the pad
sneak + right-click        clear the lock
```

A missile with a lock re-aims at the entity's current position every tick; if the target dies or leaves,
the missile carries on to the last place it was seen and detonates there — it does not circle forever.

## 5. Interdimensional

Two things are required, and both are checked by `LaunchPadBlockEntity#hasInterdimensionalClearance`:

1. the **Interdimensional Module** installed on the launcher's control panel, and
2. a launcher that is **level 5 in every part** — pad, panel, and both support rods.

Targets are set from the panel's own command surface (`/term panel target minecraft:the_nether 100 64 -200`)
or by a terminal program. On launch the missile calls `changeDimension` with a `DimensionTransition` once,
then flies normally in the new level.

Why so strict: this is the only way in the mod to put a warhead through the floor of the world, and the
three-part gate means it cannot be done by accident or by one lucky find.

## 6. Terminal programs

Saved macros, not a scripting language — 20 lines, 96 chars, 64 programs per world, names `a-z 0-9 _ -`:

```
/term prog save alpha      saves the commands in the log as "alpha"
/term prog run alpha       replays them, in order, through the normal command path
/term prog list
/term prog show alpha
/term prog delete alpha
```

Programs replay through the same interpreter a player types into, so a program can do exactly what a
player can do and nothing more — including being refused when a launcher is disarmed.

## 7. Terminal ↔ control panel

```
/term panel list           panels on this terminal's channel
/term panel status         tiers, armed state, target, modules
/term panel arm | disarm   master switch
/term panel target <x> <y> <z>
/term panel target <dimension> <x> <y> <z>     (needs the module)
/term panel fire           fire the linked pad at the panel's target
```

A panel is "on a channel" when it sits within 6 blocks of a pad registered on that channel — the same
radius the launcher structure uses, so the terminal and the pad can never disagree about which panel is
which.

## 8. Files

| File | Role |
|---|---|
| `launcher/LauncherLevel.java` | The structure scan and the weakest-part rule |
| `launcher/LauncherModule.java` | Modules and the level each requires |
| `launcher/UpgradeKit.java` | One-tier-at-a-time upgrade rule |
| `block/ControlPanelBlock(+Entity).java` | Tier, arm state, target, modules, status lines |
| `block/SupportRodBlock(+Entity).java` | A tier and nothing else |
| `block/MineBlock.java` | Random-tick landmine, no block entity |
| `warhead/Minefield.java` | Radius/funding/mine maths (unit tested) |
| `warhead/CargoDelivery.java` | Chest placement and merging |
| `item/TrackerItem.java` | UUID lock, assign to pad |
| `item/BallisticsCalculatorItem.java` | The calculator |
| `item/UpgradeKitItem.java` | Kit tier selector |
| `computer/ProgramStore.java` | Saved macros |
| `computer/TerminalShell.java` | `prog` and `panel` commands |
