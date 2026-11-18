# Mana: the foundation under the spell system

Everything magical in ShadowClanGaming spends, regenerates and grows the same pool. This document covers
the pool itself, the Mana Capacity enchantment, cast growth, and the EMC → mana conversion in the
Auto-Trader. The spell system built on top of it - shapes, components, the inscription table, the
rifle and the spell warhead - is section 9, and the block-built rifle is `docs/BLUEPRINT.md`.

---

## 1. Where maximum mana comes from

```
maximum = 100 (base) + permanent growth from casting + 25 × Mana Capacity enchantment level
```

All three sources stack, and all three are recomputed from scratch on every read.

### a) Base — 100

Everyone starts able to cast something small. A brand new player joins with a **full** pool, so the
first spell is a demo rather than a waiting game.

### b) Growth from casting — permanent

Every successful cast permanently raises the ceiling:

```
growth per cast = 1 + min(9, components in the spell)
```

A simple one-component spell adds 2. A ten-component monster adds 10, and the complexity term is
capped there so a pathological spell cannot hand out unlimited capacity. This is deliberate: the
number a player's mana bar reaches is a **record of what they have actually cast**, not what they have
farmed. Somebody who has never cast a spell has no business holding a large pool.

Growth is stored in the world's saved data and survives death, dimension changes and even removal of
gear. There is no cap.

### c) The Mana Capacity enchantment — dynamic

`shadowclangaming:mana_capacity`, up to **V**, `+25 maximum mana per level` (`+125` at max). It applies to
armour (`#minecraft:enchantable/armor`, slot group `armor`), so it comes out of the enchanting table
like any other enchantment and can be combined on an anvil.

Crucially, the bonus is **not stored** — it is read from what the player is wearing at the moment of
the query. Take the chestplate off and your maximum drops immediately, and any mana above the new
maximum is lost in the same instant. Storing it would let someone wear the gear once and keep the
bonus forever.

---

## 2. There is no natural regeneration

Mana does not come back on its own. Ever. A player standing in an empty field at full sprint gets
nothing, and that is the point: mana is a resource you have to *go somewhere* for. There are exactly
three sources.

### a) Amethyst — very slow, always available

Within **32 blocks** of any amethyst block, budding amethyst or amethyst bud:

```
mana per second = 1 + (sources - 1, capped at 4)
```

So 1/s next to one block, 5/s in the middle of a geode. It is free and permanent and it is also
hopeless for anything bigger than a cantrip, which is exactly its role: the floor, not a strategy.

The scan is cached — it only re-runs when the player has moved more than 16 blocks or 30 seconds have
passed, and it samples a stride-4 grid (4,096 lookups instead of 274,625) and stops at the first few
hits. A geode is far too big to slip through a grid that coarse.

### b) Chalk circles — go back to a place

Draw with chalk, stand in the middle:

| Size | Radius | Mana per second | How to get there |
|---|---|---|---|
| Small | 2.0 blocks | 5 | draw it |
| Medium | 3.5 blocks | 15 | +1 chalk |
| Large | 5.0 blocks | 40 | +2 chalk |
| Grand | 7.0 blocks | 100 | +4 chalk |

The circle is a **place**, not a buff. You have to stand in it, so it can be found, camped and broken —
and if the floor under it is removed, the circle goes with it. The ring is drawn with particles by the
circle's block entity, so one block holds a circle of any size.

### c) The blood pact — anywhere, and it costs everything

See section 6. 500 mana per second, anywhere in the world, including the Nether and the End, with no
circle and no amethyst.

---

## 3. EMC → mana in the Auto-Trader

> "pumping EMC into the auto trader — instead of it selling EMC it uses it to increase the player's
> max mana if full or refills it if not"

Implemented exactly that way, with a strict order of operations:

```
EMC pulled from an adjacent ProjectE storage
        ↓  ÷4
mana units
        ↓  fill the pool first
pool is full?  →  yes: the leftover mana buys permanent capacity, ÷100
                  no:  it refills, and nothing is spent on capacity
```

| Constant | Value |
|---|---|
| EMC per point of mana | **4** |
| Leftover mana per +1 permanent max mana | **100** |
| EMC intake rate cap | **8,192 per tick** (technical, so one machine cannot drain a condenser instantly) |
| EMC that is never converted | the sub-4 remainder is dropped, because we only pull whole units' worth |

So: 400 EMC fills a 100-mana pool from empty, and each +1 permanent max mana costs 400 EMC after that.
The rate cap bounds a single machine to 2,048 mana/tick, which is fast but not instant.

**EMC is never sold for money by this machine.** That is the rule.

### Why "directly adjacent" and not "through a cable"

The Hyperconductor Cable converts EMC to FE on its way through (`docs/POWER.md`), and FE *is*
something the Auto-Trader sells for currency. So the two paths mean different things on purpose:

| EMC path | Result |
|---|---|
| EMC storage placed **directly against** an Auto-Trader | **mana**, then permanent max mana when full — never money |
| EMC storage → Hyperconductor Cable → Auto-Trader | EMC becomes FE, and FE becomes money via the cubic curve |

If you would rather EMC could *never* reach the money path, the fix is to disable the cable's
EMC→FE conversion (one constant, `EMC_PER_FE`) and force all EMC through the trader. Flagged rather
than assumed, because the cable's EMC support was itself a request.

---

## 3b. The inscription table, and the spell blueprint

Not an altar. The inscription table is a desk: you write a spell down, and the writing becomes an item.

```
   book and quill  --[ INSCRIBE ]-->  spell blueprint  -->  into a spell rifle
                                                       -->  into a spell warhead
                                                       -->  or right-click to cast it yourself
```

### The table

One spell, written as a **flat strip of parts**. That is the whole model, and it is the old Ars Magica
2 model: a shape is a delivery, and every part after it belongs to it until the next shape. There is no
eight-slot budget of shapes and no "effects live under a shape you picked first".

```
 inscription table                       MANA 0 / 500
 ┌SHAPE┐┌COMPONENT┐┌MODIFIER┐        the spell, in order
 │ [Se] [To] [Pr] [Be] [Au] [Zo] [Ru] │  [Pr][Li][Fi][Zo][Li][Ra][Ao][Li][..]
 │ [Pr] [Zo] [Ru] ...                 │  [Ru][He]        (scrolls by the row)
 │   the palette, one bookmark at a time│  ┌────────────────────────────┐
 └────────────────────────────────────┘  │ mana cost            1 120 │
                                         │ capacity demand      1 120 │
                                         │ shapes / effects       3 / 7 │
                                         │ you: 500 / 500             │
                                         │ inscribing into: blueprint │
                                         └────────────────────────────┘
```

Read the strip left to right and you have read the spell: a projectile carrying Lightning and Fire, a
zone that re-casts Lightning, Random Teleport and AoE, then a rune carrying Heal.

| Action | Effect |
|---|---|
| Click a **shape** tile | Append a new delivery to the end of the strip |
| **Drag** a shape tile onto the strip | Insert a new delivery at that position |
| Click a **component** tile | Append it to the last delivery |
| **Drag** a component onto the strip | Insert it at that exact position |
| Click a **modifier** tile | Toggle it on the selected delivery (lit = on, shown as dots on the shape chip) |
| Click a chip | Select its delivery - modifiers apply there |
| Drag a chip | Reorder it, within or across deliveries |
| Right-click a chip | Remove it (a shape chip removes its whole delivery) |
| Wheel over the palette / strip | Scroll |
| **Search** box | Filters the active bookmark by part id (`lightning`, `zone`, `wide`) |
| **Name** box + **Reset Name** | Names the spell; the name is written onto the blueprint, rifle or warhead |

### The blueprint

| Held when you press INSCRIBE | Result |
|---|---|
| **Book and quill** | The book is consumed and you get a **spell blueprint** (costs mana + capacity) |
| **Spell blueprint** | Rewritten in place (costs mana + capacity) |
| **Spell rifle** | The spell is bound to the gun (full mana price) |
| **Spell warhead** | A copy is loaded for firing (money fee + capacity) |

A blueprint is a real item, not a wrapper:

- **right-click** casts the spell yourself, at full mana cost, 30-tick cooldown, no gun and no missile
  required - and it cannot be cast if your capacity cannot hold one cast of it;
- **sneak + right-click with a rifle or spell warhead in the other hand** loads it into that item and
  spends the page;
- it stacks to 16, so spells become something you can hand to another player.

### The example that matters

`projectile → zone` with **Lightning Strike**, **Random Teleport** and **AoE** on the zone card is the
"spreading field of lightning" case: the bolt lands, a zone stands for 30 seconds re-running the rest of
the spell from its own centre every 5 seconds. **Legion** makes four zones; **Eternal** makes them last
90 seconds.

### There is no component limit

```
delivery cost = (shape base + Σ 25 × tier²) × (1 + Σ modifier %)
```

No cap on components per step. The pinned cost panel shows the running total before you commit, and
mana capacity is the only gate. The 256-per-step guard in the code exists because the draft is synced
through a flat int array - it is far beyond what any pool can pay for.

### The crash this replaced (round 18)

A spell whose zone step contained another ZONE or RUNE step appended to the zone list while that list
was being iterated, and the next `iterator.remove()` threw `ConcurrentModificationException` in
`SpellZones.tick` and took the server down on the first pulse. `SpellZones`, `SpellStorms` and
`SpellDelays` now all walk their lists by index and mutate in place.


## 4. Commands

```
/shadowclangaming mana <player>              # current / max, and the three sources broken out
/shadowclangaming mana <player> reset        # forget the pool: back to 100 base, full
/shadowclangaming mana <player> add <n>      # raise that player's MAXIMUM by n, then fill
/shadowclangaming mana <player> remove <n>   # lower the MAXIMUM by n (floored at the 100 base)
/shadowclangaming mana <player> set <n>      # set the MAXIMUM to exactly n, then fill

# ...and all four also run on yourself, with the player left out:
/shadowclangaming mana
/shadowclangaming mana add 500
/shadowclangaming mana set 5000
/shadowclangaming mana reset
```

Example output:

```
Steve: mana 340 / 475  (base 100 + growth 250 + gear 125)  regen ~6/s
```

---

## 5. Files

| File | What it holds |
|---|---|
| `magic/ManaPool.java` | Per-player saved data: current mana, permanent growth, cast count, lazy-regen timestamp |
| `magic/ManaApi.java` | The single door: capacity, current, spend, refill, addMana, addCapacity, onSpellCast |
| `data/shadowclangaming/enchantment/mana_capacity.json` | The enchantment definition (1.21 data-driven enchantments need no code) |
| `block/AutoTraderBlockEntity.java` | The EMC → mana → capacity conversion |

Nothing here is ticked directly: the pool only does work when something asks it a question, which is
why a hundred idle players cost nothing.

---

## 6. The blood pact

The strongest source in the mod, and a one-way door.

### Activating it

Kill all three of these **within 16 blocks of a blood circle**, on the **same full-moon night**, while
holding a **Totem of Undying in your offhand**:

1. a **witch**
2. a **baby villager**
3. a **cat**

Each kill only counts if the moon is full and the totem is in your offhand at that moment. The totem is
then consumed. Progress is three bits on your player record and goes stale after one night, so you
cannot spread the ritual over a week.

A blood circle is crafted from blackstone, redstone and a wither skeleton skull.

### What it gives

**500 mana per second, anywhere.** Amethyst gives 5. The best chalk circle gives 100. This is not an
upgrade to mana regeneration, it is a replacement for the concept.

### What it costs

- **It expires.** The first pact lasts 20 minutes.
- **It has to be fed.** Sacrifices near a blood circle extend it:

  | Victim | Extension |
  |---|---|
  | adult villager | +10 minutes |
  | **baby villager** | **+30 minutes** |
  | witch or cat | +5 minutes |
  | anything else | +1 minute (works, barely) |

  The ritual wanted babies; so does the upkeep.
- **Chalk is closed forever.** `evil` is a permanent flag. The moment the pact forms, every chalk
  circle in the world stops working for that player, and chalk refuses to draw. There is no penance
  mechanic and no way back — the good route closed when the other one was chosen. `/shadowclangaming mana
  <player>` reports `chalk REFUSED (blood pact)` so there is no ambiguity about what happened.

### Corner cases I had to decide

- **Lapsed pact + sacrifice** restarts from now rather than extending into the past.
- **A pact holder can sacrifice without the totem.** The totem is the price of *forming* the pact, not
  of feeding it.
- **Re-forming** is not needed: any sacrifice at a blood circle extends or restarts an existing pact.
- **Offline players** do not regenerate, but a pact keeps ticking down while they are away, because it
  is a deadline, not a buff timer.

---

## 7. Where the mana comes from, in one table

| Source | Rate | Requirement | Limitation |
|---|---|---|---|
| Amethyst within 32 blocks | 1–5 /s | be near a geode | pitiful |
| Chalk circle | 5–100 /s | stand in the middle | it is a place you must return to |
| Blood pact | 500 /s | ritual + upkeep sacrifices | expires, and kills chalk forever |
| Auto-Trader (EMC) | 4 EMC = 1 mana | ProjectE + adjacency | never money, and needs EMC |

---

## 8. Creeping Evil: the bill for the pact

Taking the blood pact does not just bar you from chalk. It puts a clock on you.

### The effect

A hidden status effect — applied with `visible = false, showIcon = false`, so it **never appears in the
HUD**. You learn about it by losing hearts.

It counts down like any normal effect, except that reaching zero promotes it instead of ending it:

| Level | Duration | Hearts lost |
|---|---|---|
| 1 | 6 hours real time | 1 |
| 2 | 6 hours real time | 2 |
| 3 | 6 hours real time | 3 |
| ... | ... | ... |

6 hours = **432,000 ticks** per level. This is not a fight, it is a mortgage: it will still be there
next week, one heart worse. Level 1 starts the instant the pact forms.

Hearts are removed with a `MAX_HEALTH` attribute modifier with a fixed id, recomputed from scratch
every second. That makes it self-healing — death, dimension changes and other mods touching the
attribute all converge back to the correct value instead of stacking a second penalty. There is a hard
floor of **1 heart**: the curse never outright kills, it just makes everything else likely to.

### Counter 1 — the Holy Necklace (craftable)

Gold ingots, string, an amethyst shard. **Carrying it** (anywhere in the inventory) freezes the curse:
the timer stops, no new level, no more hearts lost.

- It does **not** reduce the level you already have.
- It blocks **all** mana regeneration while carried — amethyst, chalk circles, the pact, and even the
  Auto-Trader's EMC feed for an online owner. Everything goes through one choke point, so nothing
  sneaks past.
- So the necklace is a choice, not a solution: a smaller heart bar and a stopped clock, or a working
  mana supply and a curse that keeps eating.

### Counter 2 — Holy Water (cleric trade, not craftable)

Cleric villagers sell it: 12 emeralds for 1 at profession level 2, 40 emeralds for 4 at level 4. It is
deliberately a *village* resource, so a server full of cursed players has to keep a cleric alive.

### Breaking the curse

All three at once:

1. you are carrying a **Holy Necklace**
2. it is **midday** — the necklace glows, and that glow is the only warning the window is open
3. you drink **Holy Water** while cursed

Midday is `timeOfDay` 0.25 ± 0.02 — about a **48-second window** once per in-game day. Miss it and the
water is **not consumed**; burning a rare trade because somebody clicked at 11:59 would just be cruel.

On success: the curse is gone, hearts return, the necromantic pact ends, and **chalk circles work
again**. `evil` is cleared, so this is the only way back to the good route.

### Commands

```
/shadowclangaming curse <player>        # level, hours remaining, whether the necklace is freezing it
/shadowclangaming curse cure <player>   # break it outright (ops; restores hearts and chalk)
```

### Files

| File | What it holds |
|---|---|
| `magic/CreepingEvilEffect.java` | The (nearly empty) registered effect, harmful, no per-tick action |
| `magic/CreepingEvil.java` | The ladder, the six-hour timer, hearts, necklace freeze, midday window, cure |
| `registry/SCGMobEffects.java` | Effect registration |
| `item/HolyNecklaceItem.java` · `item/HolyWaterItem.java` | The two counters |
| `magic/ClericTrades.java` | The cleric trade injection |

---

## 9. Spells: a sequence of steps

A spell is an **ordered sequence of steps**. Each step is one *shape* plus the components that fire with
that shape:

```
[ PROJECTILE : fire damage ]  ->  [ ZONE : lingering zone ]  ->  [ RUNE : ignite ]
```

Casting walks the sequence. Each step does its own thing and then hands **the rest of the sequence** to
whatever comes next:

- a **projectile** carries the rest of the spell to whatever it hits — the rest continues from the
  impact point;
- a **zone** parks the rest of the spell in a 30-second area that **re-runs it from the zone's own
  position every 5 seconds**;
- a **rune** parks the rest of the spell as a trap that fires **once** when a living thing walks over it;
- self, touch, beam and aura land their components and then simply continue.

The example above is therefore: a bolt of fire damage that, where it lands, opens a 30-second burning
zone, and each pulse drops a rune that ignites whoever walks into it. Nothing in the data model knows
about that spell being special — it is four list entries and a switch.

**There are no spell levels any more.** A spell is gated by the caster's **mana capacity**: the sum of
its step costs must fit inside the pool the caster actually has. If it does not fit, it cannot be
inscribed and it cannot be fired.

### 9.1 Shapes

The shape decides where a step's components land. It is a component in its own right and it has its own
price, so a self-buff and a projectile with identical components do not cost the same.

| Shape | Mana | Reach | What it does |
|---|---|---|---|
| Self | 10 | — | Lands on the caster, then continues |
| Touch | 30 | 4 blocks | Short reach, entity or block, then continues |
| Beam | 100 | 32 blocks | Instant line, entities checked before blocks, then continues |
| Projectile | 60 | 48 blocks | A bolt that flies; the REST of the spell resumes where it lands |
| Aura | 150 | 6 blocks radius | Everything alive around the point, friends included |
| Zone | 250 | 6 blocks radius | Live for **30 s + 1 s per power level**, re-cast the rest **every 5 s − 0.25 s per level** from its own position |
| Rune | 120 | 1.5 blocks | Trap: fire the rest of the spell **once** when something steps on it |

A zone pulses **six times** at baseline power (5, 10, 15, 20, 25, 30 s — the first pulse is not at t=0,
so casting a zone does not double-hit the moment it appears). A rune waits 5 minutes untriggered, then
expires.

**Power changes the shape of a zone, not just its damage.** Every power level adds **one second** of
life and takes **a quarter second** off the recast (never faster than 1 s — below that it stops being a
zone and becomes a damage bug). Levels are counted above the baseline, the same way the emitter assembly
counts them, so a level-1 build is x1.00 and zone level 0:

| Assembly level | Power | Zone life | Recast | Pulses |
|---|---|---|---|---|
| 1 | x1.00 | 30 s | 5.00 s | 6 |
| 2 | x1.10 | 31 s | 4.75 s | 6 |
| 6 | x1.61 | 35 s | 3.75 s | 9 |
| 8 | x1.95 | 37 s | 3.25 s | 11 |
| 14 | x3.45 | 43 s | 1.75 s | 24 |
| 27 | x11.92 | 56 s | 1.00 s | 56 |

The interval is stored per zone (and saved with it), so a zone keeps the rhythm it was cast with even
across a restart, and two zones from different guns can pulse at different rates side by side.

Zones and runes are stored in saved data (`spell/SpellZones.java`), so they survive a restart. Each one
holds its own copy of the spell plus **the step index it resumes at**, which is why nesting works: the
zone holds the step after the zone, the rune it creates holds the step after the rune.

### 9.2 Components — the base effects

94 base effects across five tiers. Tier is what you pay for, not power: this is the old Ars Magica
mana rule, kept because it makes exotic magic genuinely expensive.

```
component cost = 25 × tier²
tier 1 =  25     tier 2 = 100     tier 3 = 225     tier 4 = 400     tier 5 = 625
```

| Tier | Components |
|---|---|
| 1 | Physical Damage, Fire Damage, Frost Damage, Heal, Haste, Jump Boost, Slow Falling, Night Vision, Water Breathing, Slowness, Knockback, Ignite, Extinguish, Conjure Light, Life Tap |
| 2 | Lightning Damage, Regeneration, Absorption, Resistance, Strength, Speed, Weakness, Poison, Blindness, Nausea, Hunger, Pull, Dig |
| 3 | Magic Damage, Lifesteal, Levitation, Invisibility, Darkness, Blink, Dispel, **Random Teleport** |
| 4 | Wither, Lightning Strike, Lingering Zone, **Cluster Strike**, **Firestorm** |
| 5 | Gravity Well, Wither Storm, Annihilate, **Chronosplit**, **Hellstorm**, **Storm Call**, **Thaumic Rift**, **Soul Harvest**, **Banish** |

**There is no cap on how many components a spell has.** Each step can hold up to 32 (that is the size of
the synced GUI array per step, not a balance limit) and a spell can hold up to 8 steps, so the ceiling is
256 components per spell — and the only real limits are mana and mana capacity.

### 9.2b The five newer effects, in detail

**Random Teleport** *(tier 3, 225)* — the thing you hit is thrown **up to 16 blocks** in a random
compass direction with some vertical spread, never less than 4 (a teleport that moves them one block is
a taunt). Up to 24 candidate landing spots are tested and the first legal one wins: two blocks of air
with something solid underneath, within eight blocks of the target height. If nothing legal is found the
spell does nothing at all rather than suffocating the victim inside stone.

**Cluster Strike** *(tier 4, 400)* — only does something in a **projectile** step. The bolt becomes a
delivery vehicle: where it lands it splits into **five** sub-bolts. The thing it hit is target one, and
up to four more are taken from living things within 24 blocks, nearest first. Each sub-bolt **homes**
(0.9 blocks/tick) and carries **a full copy of the rest of the spell**, so a cluster strike delivers the
remaining sequence five times. Spare bolts are fanned evenly around the impact direction instead of
being wasted. The cluster component is stripped from the sub-bolts, so a cluster cannot recursively
cluster.

**Chronosplit** *(tier 5, 625)* — the target is **frozen for 15 seconds (300 ticks)** and cannot do
anything at all:

| Blocked | How |
|---|---|
| walking, jumping, being knocked around | −100 % movement speed modifier **and** delta movement zeroed every tick |
| breaking or placing blocks | `PlayerInteractEvent.LeftClickBlock` / `RightClickBlock` cancelled |
| using items, eating, shooting, throwing | `RightClickItem` and `LivingEntityUseItemEvent.Start` cancelled |
| interacting with entities, attacking | `EntityInteract` cancelled, and damage *from* a frozen entity is refused |
| dropping items | `ItemTossEvent` cancelled |
| mob AI: pathing, targets, swings | `Mob#setNoAi(true)` while frozen, restored when the effect ends |

It is a real registered mob effect (`shadowclangaming:chronosplit`), so it shows in the HUD, survives a
restart, and cannot be shortened by anything short of milk or `/effect clear`.

**Firestorm** *(tier 4, 400)* — a burning area of radius **8** that lives **6 seconds** and pulses every
10 ticks for **3 damage** per pulse. It respects the vanilla damage cooldown, so roughly every other
pulse lands, and it sets the ground alight — which is also why it can destroy the loot of what it kills.

**Hellstorm** *(tier 5, 625)* — the upgrade. Radius **14** (nearly twice), **8 seconds**, **7 damage**
per pulse, and it **bypasses i-frames**: `invulnerableTime` is cleared before every hit, so every pulse
lands instead of every other one. Standing in it is roughly four times the damage of a firestorm on
three times the footprint. What it does **not** do is burn: it never places fire blocks, it clears
`remainingFireTicks` on everything it touches, and it puts out any item already burning inside the
radius. **Hellstorm does not destroy items.**

Both storms scale with the rifle's power multiplier, and both are saved data, so a storm left burning
when the server stops is still burning when it starts again.

### 9.2b2 Overlord and Konosuba

Because a spell list is a love letter. Priced on the same `25 × tier²` rule and picked so each one has
a job the existing 76 do not do:

| Tier | Overlord | Konosuba |
|---|---|---|
| 1 | Magic Arrow, Flash | — |
| 2 | — | Create Water, Wind Blast, Lurk |
| 3 | — | Drain Touch |
| 4 | Hell Flame, Cry of the Banshee | Cursed Crystal Prison, Turn Undead, Purification |
| 5 | Grasp Heart, Fallen Down, Negative Burst, Black Hole | Explosion, God Requiem |

- **Grasp Heart** — above 30 % health it is heavy damage and weakness; at or below 30 % the heart is
  crushed and the target dies. The execution window is what keeps it a finisher instead of a delete
  button.
- **Fallen Down** — the heaviest single-target spell in the mod: 18 damage, a lightning bolt, fire in a
  crater.
- **Black Hole** — everything within 8 blocks is dragged in at 0.85 blocks/tick and ground up.
- **Turn Undead** — 6 damage to the living, **24** to the undead, and it sets the weak ones alight.
- **Explosion** — Megumin's: 26 damage to everything within 12 blocks, a blast with no block damage, and
  the caster is winded for five seconds afterwards. No other component costs as much.
- **Drain Touch** — health *and* mana move from target to caster (80 % of damage dealt as health, 40×
  that in mana). Against a player this is the rudest spell in the mod.

### 9.2b3 Bad Gas

Tier 3, 225 mana, and the only component that is mostly a joke at the victim's expense.

On hit: 2 damage, nausea, hunger - and then the throw. The victim is launched **upward** (1.25 +
0.35 per point of power) and **along whichever direction it is facing** (1.6 per point of power), not
away from the caster. That direction is the point: a zombie shambling toward you is flung over your
shoulder and out the back of the fight, while something running away is thrown further away.

The pair of forces is what makes it read as a body rather than a knockback: both are applied at once,
so the arc is steep and long instead of a shove along the ground.

**Ragdoll.** With [Sable: Ragdolls](https://modrinth.com/mod/sable-ragdolls) installed the victim is
handed to `dev.leo.sableplayerragdoll.api.RagdollAPI` - players through `launch(Player, velocity)`,
mobs through `launchMob(level, mob, linearVelocity)` - and becomes a real Rapier-physics ragdoll
carrying that velocity. The bridge (`compat/sable/SableRagdolls.java`) is **reflective**, like AE2 and
Create: that mod is All Rights Reserved, so nothing of it is compiled against or bundled, and the wiki
does not state the velocity type, so the bridge inspects the parameter it actually finds and builds a
`Vec3`, `Vector3f` or `Vector3d` to match. Any failure returns false and the spell still works.

**Without Sable** the fallback is the closest vanilla gets to a ragdoll: the velocity is applied
directly and the mob's AI is switched off for three seconds (`spell/FlungBodies.java`, ticked from the
server tick). It stops steering, stops deciding, and tumbles. The queue is memory-only - a restart
mid-fling wakes the mob up with its AI intact, which is the safe failure.

### 9.2c The second school

Thirty more components, from the rest of the Ars Magica 2 list and from Blood Magic, Thaumcraft and
D&D, priced on exactly the same `25 × tier²` rule so they sit alongside the originals:

| Tier | New components |
|---|---|
| 1 | Spark, Chill, Feather Fall, Satiate |
| 2 | **Entangle**, Searing Light, Magic Shield, Mind Fog, Silence |
| 3 | Frost Nova, Necrotic Drain, Disarm, Arcane Ward, Warp Step, Blood Sacrifice, Detonate |
| 4 | **Starstrike**, Holy Smite, Blood Lance, Chain Lightning, Gravity Flip, Time Slow, Flight, Sanctuary, Plague Bearer |
| 5 | Storm Call, Thaumic Rift, Soul Harvest, Banish |

The ones worth knowing:

- **Starstrike** — a star lands on the point: 12 damage, a lightning bolt, and fire in a small crater.
- **Entangle** — roots the target for six seconds (Slowness V, momentum zeroed). It can still fight; it
  cannot leave.
- **Chain Lightning** — the target plus up to four more living things within 8 blocks.
- **Blood Lance** — damage scales with how badly hurt the caster already is, and costs a heart.
- **Banish** — throws a *hostile* up to 64 blocks away. Players are immune by design.
- **Sanctuary** — heals and strips every harmful effect in the area; the anti-necromancer button.

### 9.2d Modifiers

A component says *what* happens; a **modifier** says *how*. Modifiers are per STEP, stack
multiplicatively into that step's cost, and are stored as one bitmask int:

```
step cost = (shape base + Σ components) × (1 + Σ modifier percentages)
```

| Modifier | Cost | Effect |
|---|---|---|
| Empowered | +60 % | damage and healing ×1.5 |
| Wide | +70 % | areas ×1.6 (aura, zone, nova, chain range, firestorm) |
| Piercing | +45 % | damage ignores armour and clears the victim's damage cooldown |
| Vampiric | +80 % | the caster heals 35 % of damage the step deals |
| Twinned | +100 % | the step resolves **twice** |
| Swift | +25 % | projectiles fly at double speed |
| Lasting | +65 % | zones and runes last 50 % longer |
| Silent | +10 % | no sounds, a quarter of the particles |
| **Maximize** | +90 % | damage and healing ×1.35, every time (the YGGDRASIL metamagic) |
| **Triplet** | +180 % | the step resolves **three** times (beats Twinned). Bolts fan out, zones are parked apart |
| **Boost** | +50 % | ×1.25 damage and ×1.25 duration |
| **Delay** | +40 % | the step lands **3 seconds** after the cast |
| **Chant** | **−25 %** | 25 % cheaper, but the step lands 2 seconds late |
| **Seeker** | +200 % | bolts steer onto the nearest living thing within 24 blocks |
| **Echo** | +160 % | the step fires again at half power, two seconds later |
| **Siphon** | +170 % | 6 mana back per point of damage dealt |
| **Bastion** | +190 % | absorption worth half the damage dealt |
| **Execute** | +240 % | ×1.6 everywhere, ×2.25 below 35 % health |
| **Cataclysm** | +400 % | ×2.0 damage **and** ×1.5 area |
| **Eternal** | +300 % | zones and runes last ×3 |
| **Legion** | +450 % | the step resolves **four** times (beats Triplet) |
| **Epicentre** | +220 % | area ×2.2 (beats Wide) |
| **Cripple** | +210 % | weakness, slowness and mining fatigue for 8 s |
| **Dispel** | +130 % | strips beneficial effects **before** the damage |
| **Nova** | +330 % | a block-safe detonation, radius 2 + 2 per power |
| **Hex** | +240 % | a random heavy curse (wither / poison / blind / nausea / levitation) |
| **Bind** | +300 % | rooted for 3 s: no walking, no jumping |
| **Blood Price** | **−70 %** | you pay 25 % of maximum health instead |

**Repeats converge.** Twinned, Triplet and Legion used to be silently swallowed by projectile and zone
steps, because those shapes hand the rest of the spell off and return. They now run the repeat loop
first. A volley launches its bolts from origins fanned sideways (0.85 blocks apart) but aims every one
of them at the same point down the sightline - the entity you are pointing at, else the first block hit,
else the end of the shape's range - so the shots close into a fan instead of a shotgun spray. Zones and
runes are parked 1.5 blocks apart. Stacking them on one point would be a lie - vanilla invulnerability
only lets one equal hit land per tick - so a Triplet volley is worth 180 % because it covers three
places, and Piercing is what turns it into triple damage on a single target.

Delay and Chant are the only modifiers that change *when* rather than *what*, so they run through a
queue (`spell/SpellDelays.java`): the step is snapshotted, the cast returns immediately, and the queue
re-enters the interpreter a second or three later from the server tick — never by sleeping on the
server thread. The queue is memory-only on purpose: a pending two-second cast is part of one player's
action, not world state, and firing it five minutes later after a restart would be a worse bug than
losing it.

**Modifiers travel with the bolt.** Until round 16 a projectile step applied its impact components
with no modifiers at all, which meant the most expensive part of a build evaporated the moment the
shape was `PROJECTILE`. The mask now rides on `SpellProjectileEntity` (`ImpactModifiers` in NBT) and is
applied at the impact point, and the same is true of a zone's own step on every pulse.

**Fifteen more, and they are expensive on purpose.** The first eight modifiers made a spell bigger;
the deep arcana change what the spell *is*. Echo beats invulnerability by being a second event rather
than a second hit. Blood Price is the only negative price in the system and the only modifier that can
kill the caster, which is what makes a 70 % discount cost something real.

The interaction that matters: **Empowered + Twinned + Piercing** on a Starstrike step is 3.05× the
mana for 1.5× damage, twice, with no armour and no i-frames. That is the trade this system is built to
offer — the numbers are all in the cost panel before you press Inscribe.

### 9.3 What a spell costs

```
mana cost      = Σ over steps ( shape base + Σ(25 × tier² in that step) )
capacity need  = the same number: a spell must fit inside your mana capacity to be inscribed or fired
copy fee       = 200 × n²              (n = total components in the whole spell, currency)
```

Worked examples, computed from the shipped constants:

| Spell | Steps | Components | Mana = capacity | Copy fee |
|---|---|---|---|---|
| Fire damage, projectile | 1 | 1 | 85 | 200 |
| Fire bolt, then an empty zone | 2 | 1 | 335 | 200 |
| Bolt → zone → rune (ignite) | 3 | 2 | 480 | 800 |
| 3 × tier 2, beam | 1 | 3 | 400 | 1,800 |
| 5 × tier 5, aura | 1 | 5 | 3,275 | 5,000 |
| 4 bolt steps + 4 zone steps, 32 × tier 1 each | 8 | 256 | 7,640 | 13,107,200 |

The last row is not a joke: it fits inside a well-developed mana capacity and it will fire. That is the
direct consequence of removing spell levels — the pool, not a ladder, is the gate.

### 9.4 The inscription table

Right-click it and you get a drafting screen. The draft is visible, top to bottom:

```
ADD STEP   [Self][Touch][Bolt][Beam][Aura][Zone][Rune]
┌ components ───────────────┐ ┌ the spell ───────────────────────────┐
│ Physical damage           │ │ 1  PROJECTILE  Fire damage      [x]  │
│ Fire damage               │ │ 2  ZONE        Lingering zone   [x]  │
│ ...                       │ │ 3  RUNE        Ignite           [x]  │
└───────────────────────────┘ │ mana 540   capacity demand 540       │
[<][>] [Undo][Clear][Reset]   │ [ INSCRIBE ]                         │
```

- Clicking a component appends it to the **selected step** (the highlighted one).
- "ADD STEP" appends a **new step** of that shape after the last one.
- The **[x]** on a row removes that step.
- The cost panel shows the running mana cost, the **capacity demand**, and your own mana / capacity, so
  you can see before you press the button whether the spell will fit.

Server side the draft lives in the block entity and is synced as a flat array of data slots: step count,
selected step, one header per step (`count << 8 | shapeOrdinal`), then the components per step. The menu
pushes changed slots itself (`ClientboundContainerSetDataPacket`), which is what makes the draft appear
instantly instead of only on reopen. Every button press is a menu button id handled by `clickMenuButton`
on the server, so the client cannot add anything the server did not agree to.

- **Rifle held** → the spell is bound to the gun. Costs **full mana price** and requires
  `capacity >= capacityDemand()`.
- **Spell warhead held** → a **copy** is loaded into it. Costs `200 × n²` currency (EconomyCraft, or War
  Bonds at 100 each if the economy mod is absent) plus the same capacity requirement.

### 9.5 The rifle

The rifle is **not craftable**. It is built — see `docs/BLUEPRINT.md`. From the build it inherits:

| Stat | Range | Meaning |
|---|---|---|
| Efficiency | 60–95 % | What fraction of the mana cost it charges per shot |
| Emitters | — | Each extra emitter is −5 % cooldown and +2.5 % mana on the shot |

Firing: right-click, cooldown, mana spent **before** the cast and refunded if the cast produced nothing.
Every shot grows the cast-growth term (§1b), so a wizard's ceiling still tracks what they have actually
cast.

### 9.6 The spell warhead

`WarheadType.SPELL` — no blast of its own. At impact it casts the loaded spell **at the impact point**,
scaled ×3. Because the warhead has no caster, the sequence starts from the impact point with no self
step to fall back on — a warhead whose spell begins with a ZONE therefore leaves a 30-second hazard
behind rather than hitting once. A warhead with no spell inscribed is a dud and announces itself as one.

Copying is quadratic on purpose (`200 × n²`): doubling the length of a spell quadruples the price of
every copy, which is what stops "twelve components × forty missiles" being free.

### 9.7 Files

| File | What it holds |
|---|---|
| `spell/SpellShape.java` | The seven shapes, their base costs, zone/rune lifetimes |
| `spell/SpellComponent.java` | The 94 base effects, tiers, `25 × tier²` |
| `spell/SpellModifier.java` | The thirteen modifiers, their cost percentages and every multiplier |
| `spell/SpellDelays.java` | The delay queue behind DELAY and CHANT |
| `spell/FlungBodies.java` | Mobs thrown by BAD GAS waiting to get their AI back |
| `compat/sable/SableRagdolls.java` | Reflective bridge to Sable: Ragdolls (optional) |
| `spell/SpellStorms.java` | Firestorm / Hellstorm burning areas, the 10-tick pulse and the i-frame bypass |
| `spell/Chronosplit.java` · `spell/ChronosplitEffect.java` | The 15-second freeze and its interaction locks |
| `spell/Spell.java` | The step list, codecs, mana / capacity / copy maths |
| `spell/SpellCasting.java` | The sequence interpreter: traces, applies, hands off to zones/runes |
| `spell/SpellZones.java` | Saved data for live zones and runes; the 5-second pulse, the rune trigger |
| `entity/SpellProjectileEntity.java` | The bolt, which carries the rest of the sequence to its impact |
| `item/SpellRifleItem.java` | Firing, cost, efficiency and capacity checks, tooltip |
| `block/InscriptionTableBlock(+Entity).java` | The drafting table, the step draft, the synced array |
| `block/InscriptionMenu.java` | Button ids, explicit slot sync, inscribing, copy fees |
| `client/InscriptionScreen.java` | Step list, shape tabs, paged component grid, cost panel |
| `registry/SCGMenus.java` | Menu type registration (screen bound in `SCGClient`) |
