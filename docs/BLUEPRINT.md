# The rifle bench: throw the blocks in, set the dials, press craft

**Round 17 replaced the corner-marking build with a single-block bench.** The idea survives - a rifle is
still worth what you made it from, and the materials still decide its tier and efficiency - but the
workflow is now an inventory and two dials instead of two corners of a real build.

| Action | Effect |
|---|---|
| Right-click the table | Opens the **bench GUI** |
| Put blocks in the 27 slots | They are the frame: their material value sets **tier** and **efficiency** |
| At least one **Emitter** in the slots | It is the muzzle - the bench will not build without one |
| **BARRELS** dial `1..128` | -5 % cooldown, +2.5 % mana, +2 % power per barrel |
| **STRENGTH** dial `1..128` | +3 % power, +2 % mana, +1 % cooldown per point |
| **CRAFT** | Consumes every block on the bench and hands you the rifle |

The readout above the slots shows exactly what the current pair of dials would produce **before** the
materials are spent - tier, efficiency, power multiplier, mana multiplier and ticks per shot.

At 128 barrels and 128 strength a rifle fires for **17x power** at roughly **80x mana** per shot. That
is a deliberate trade, not a cap: the mana bill is the balance.

The old corner-marking flow is documented below for history; its methods are kept as no-ops so an old
client packet cannot crash a server, and the block now simply opens the GUI.

---

## 1. Controls

No extra wand item, no GUI — the table itself is the tool, and it talks back in chat.

| Action | Effect |
|---|---|
| Right-click | Mark **corner A** on the block you are **looking at** (up to 32 blocks away) |
| Right-click again | Mark **corner B** on a different block, and print the box size and volume |
| Right-click again | **Move corner B** — keep clicking until the box is the one you want |
| **Sneak + right-click** | Scan the box and build the rifle |
| **Sneak + right-click** with only corner A set | Clear the box and start again |

Corners persist in the block entity's saved data, so marking one and coming back tomorrow works.

### Why this took a rewrite

The first version had three faults that all pointed the same way — a 1×1×1 box you could never escape:

1. **The corner was the block you were right-clicking.** `player.pick()` from the table returns *the
   table*, because that is what your crosshair is on. So both corners landed on the table's own position.
2. **Aiming at air fell back to the block at your feet**, silently. So the "no target" case quietly marked
   the floor you were standing on.
3. **Holding right-click repeats the interaction every 4 ticks** in vanilla, so one held click marked
   corner A and then immediately marked corner B on the same block.

All three are fixed:

- The marking ray **ignores the table**: you stand beside it and point at your build, and the corner is
  the block under the crosshair. Aim at nothing and it says so instead of inventing a corner.
- The corner is computed **on the client**, which is the only side that knows where the camera is
  pointing *right now* (the server's copy of your rotation is at least one packet old), and shipped to
  the server in `BlueprintCornerPayload`. The server validates that the table exists, that you are
  standing near it, and that the corner is within 32 blocks of you before storing it.
- Clicks on the same block within 8 ticks are collapsed into one, and **a corner can never be marked on
  the block that already holds the other corner** — a box with no volume is never created. Instead you
  get "that is the same block as corner A".

Every click also re-states the current box, so there is never any doubt about what is marked.

### The shortest working build

Stand in front of the table, look at the ground block where you want the box to start, right-click, look
at the opposite ground block, right-click. Then place **at least 8 solid blocks** and **exactly one
Emitter** anywhere inside that box, and sneak-right-click the table.

```
   [x][x][x]     8+ solid blocks, any layout
   [x][x][x]     exactly one of them replaced by an Emitter
   [x][E]        (the Emitter is the muzzle; it is not counted as material)
```

The table itself is never consumed, even if the box encloses it.

## 2. What a valid build is

Checked *before* a single block is removed:

| Rule | Value |
|---|---|
| Maximum box | 32 × 32 × 32 = 32,768 blocks per side-volume |
| Minimum material | 8 non-air blocks |
| Emitters inside the box | **one or more** — each is a muzzle/barrel |
| Everything else | consumed, nothing is refunded or dropped |

Refusals are chat messages, and a refusal costs nothing:
`blueprint_too_large`, `blueprint_too_small`, `blueprint_no_emitter`, `blueprint_no_corners`.
Every refusal also prints what was actually counted (`blueprint_counts`), plus a hint about which rule
failed, because "not enough material" on its own does not tell you anything.

## 3. Scoring: what the blocks are worth

Every consumed block is scored by registry path (`BlueprintMath.materialValue`). Anything not listed —
including every modded block — is worth **1**, so a strange mod cannot break the table.

| Block | Value | Block | Value |
|---|---|---|---|
| Stone, deepslate, dirt, planks | 1 | Glass, bricks | 2 |
| Quartz block | 3 | Redstone block | 4 |
| Copper block | 5 | Lapis block | 6 |
| Obsidian, sculk | 6, 4 | Iron block, amethyst block | 8 |
| Gold block | 10 | Sculk catalyst | 10 |
| Crying obsidian | 12 | Emerald block | 18 |
| Diamond block, ancient debris | 20 | **Netherite block** | **40** |

Two numbers come out:

```
frame tier  = clamp(1 + score / 60,      1 .. 5)
efficiency  = clamp(60 + score / 8,     60 .. 95)   percentage of spell cost you actually pay
```

So the same rifle shape gets better the more real material you put inside it — and a box of dirt is a
box of dirt.

### Worked examples (from the unit tests)

| Build | Blocks | Score | Tier | Efficiency |
|---|---|---|---|---|
| Small dirt frame | 20 | 20 | 1 | 62 % |
| Same frame, iron blocks | 20 | 160 | 3 | 80 % |
| Netherite instrument | 400 | 12,000 | 5 | 95 % (capped) |

## 4. The Emitter

A block of its own, craftable (amethyst / iron / copper), light level 7 with a drifting particle so it
can be found at night. It is inert on its own and is **not** a weapon: its only job is to be the one
thing inside a blueprint volume that says "this build points somewhere". That requirement is what stops
a player from marking an arbitrary box and calling it a gun.

## 5. After the build

- The whole box is set to air, including the emitter.
- One `spell_rifle` item is created with `RIFLE_TIER` and `RIFLE_EFFICIENCY` baked in as data
  components, handed to the player, or dropped on the table if their inventory is full.
- Both corners are cleared, so the next build starts immediately.
- A beacon sound and a chat line report block count, tier and efficiency.

Then take the rifle to the **inscription table** (`docs/MAGIC.md` §9.4), write a spell onto it, and
fire it.

## 6. Why it is built this way

- **Scan cost is bounded.** The volume is checked against `MAX_VOLUME` before the block loop runs, so a
  bad corner pair cannot stall the server.
- **Consumption is total.** No drops means no free recycling of a build you regret, and no way to
  launder cheap blocks into a rifle and get them back.
- **The maths is testable.** `BlueprintMath` imports nothing from Minecraft, so `volume`,
  `materialValue`, `validate` and `spec` are unit-tested outside the game. Those tests pass on JDK 21.

## 7. Files

| File | Role |
|---|---|
| `blueprint/BlueprintMath.java` | Volume, material table, validation, tier/efficiency maths (pure) |
| `block/BlueprintTableBlock.java` | Controls: right-click marks, sneak-right-click builds |
| `block/BlueprintTableBlockEntity.java` | Corners, scan, consume, produce |
| `block/EmitterBlock.java` | The muzzle marker block |
| `item/SpellRifleItem.java` | The thing you get out of it |


---

## 5. The gun looks like the gun you built

The table records every block it consumes — position and block state — and that list travels with the
rifle as a data component (`rifle_build`). The item is then drawn as those blocks, scaled to fit and
**autocentred** on the build's own midpoint:

* build it from netherite and it is a black metal gun; build it from planks and it is a plank gun;
* the emitters appear exactly where you placed them, because they are part of the shape;
* the shape is stored relative to the build centre, so it does not matter which corner of the marked box
  you built in;
* a build larger than `BuildShape.MAX_PARTS` (96 blocks) keeps the first 96 in scan order — plenty for any
  real gun silhouette, and it keeps a 32³ cube from being shipped to every client.

Rifles that were never built (creative, `/give`, an old save) fall back to a small iron-and-emitter
placeholder shape, so they still look like a gun rather than a missing texture.

## 6. Multiple emitters: the minigun rule

Emitters are barrels, and barrels cycle. Every shot leaves from the next emitter in the list, and the
rotation index is stored on the rifle (`rifle_barrel`), so the cycle survives being dropped or traded.

| Emitters | Cooldown (base 12) | Mana |
|---|---|---|
| 1 | 12 ticks | ×1.000 |
| 2 | 11 ticks | ×1.025 |
| 4 | 10 ticks | ×1.077 |
| 10 | 8 ticks | ×1.249 |
| 64 | 2 ticks (floor) | ×4.61 |

The formulas are `cooldown = round(12 × 0.95^(n−1))` with a floor of `MIN_COOLDOWN_TICKS = 2`, and
`mana = 1.025^(n−1)`. A minigun therefore trades mana efficiency for rate of fire: ten barrels fire 33%
faster and cost 25% more per shot. Both numbers are on the tooltip, and both are asserted in
`tools/run_logic_harness.sh`.


## 7. Touching emitters: assembly levels

Emitters that **touch** form one assembly, and the assembly's **level is how many emitters are in it**:

| Build | Level |
|---|---|
| a single emitter | 1 |
| 5 emitters in a row | 5 |
| a 3x3 plate | 9 |
| a 3x3x3 cube (27) | 27 |

"Touching" includes faces, edges and corners — all 26 neighbours — because that is what stacking emitters
into a solid block means. Clusters are found by flood fill, so two separate piles in the same gun are two
assemblies and firing from one does not borrow the other's power. Firing rotates through the barrels, so
the shot you get depends on which assembly that barrel belongs to.

| Effect | Formula | Level 27 (3x3x3) |
|---|---|---|
| damage / heal / push | `1.10^(level-1)` | **×11.9** |
| mana cost | `1.05^(level-1)` | **×3.56** |

Power and price multiply, so a cube assembly is a genuinely different weapon: 12 times the punch for
3.5 times the mana. The totals stack with the rifle-wide barrel surcharge:

```
shot = raw × frame_efficiency × 1.025^(emitters-1) × 1.05^(level-1)
```

Everything is on the tooltip (assembly level, power, assembly mana, final cost) and asserted in
`tools/run_logic_harness.sh` — including that a 3x3x3 really is level 27 and that a level-1 single-emitter
shot costs exactly what it used to.
