# Economy: EconomyCraft price override

**The problem.** EconomyCraft ships **1,816 prices and every one of them is vanilla**
(`minecraft:*`). On a modded server that is a hole in the economy: some mod's ore is worthless while
another mod's trinket is a money printer. For a "money is power" server, an arbitrary hole in the price
table is an arbitrary hole in who wins.

**The fix.** ShadowClanGaming generates a complete price table for **every item from every loaded mod**
and writes it into EconomyCraft's own config file. Since round 21, prices are **calculated from each
item's recipes** — down the whole crafting chain to raw materials — instead of guessed from its name.

**Why that mattered.** The old generator priced by keyword and fell back to a flat **sell 7 / buy 21**
for anything whose name matched no keyword. Machines, components, alloys and half of every tech mod's
item list match no keyword, so ~90 % of the "modded" shop tab cost exactly $21. That is not a price, it
is a hole: buy the $21 machine, craft it down, sell the $300 of ingots inside it.

Now: **an item costs what its cheapest recipe costs per unit, plus labour.** Items with no recipe (ores,
drops, loot) keep the keyword price as the floor the recursion bottoms out on.

---

## 1. What actually runs

On server start (and via command), `compat/economy/EconomyBridge`:

1. Finds EconomyCraft's price file: `config/economycraft/prices.json`.
2. **If it doesn't exist yet**, reads EconomyCraft's own bundled `assets/economycraft/prices.json`
   straight out of the mod jar — so vanilla prices are exactly what EconomyCraft intended, not my
   guesses.
3. Walks the **live item registry** (`BuiltInRegistries.ITEM`) — that's every item from every mod
   that is actually installed.
4. Prices every item **from its recipes** (cheapest recipe, per unit, plus labour — section 2), falling
   back to the hand-set table and then the keyword table for items with no recipe.
5. Writes the file back, **sorted**, with a timestamped `prices.json.bak-<millis>` backup.

It is **idempotent**: run it as often as you like, it only adds what's missing and corrects prices *it*
wrote earlier. Admins always win — a price you edited is never touched (except under `force`).

### Verified against your exact version

I checked `economycraft-neoforge-1.4.2_1.21.1`'s schema (and 1.11.0's — identical):

```json
"minecraft:iron_ingot": {
  "category": "ores",
  "stack": 64,
  "unit_buy": 15,
  "unit_sell": 5
}
```

`unit_buy` / `unit_sell` / `stack` / `category` — that's the whole format. Your build's 1,663/1,816
default entries use exactly this shape, so the generated file drops straight in.

---

## 2. Pricing from recipes

`compat/economy/RecipePricer` builds a graph from **every recipe the server loaded**, using the generic
`Recipe#getIngredients()` / `Recipe#getResultItem()` interface — so vanilla shaped/shapeless, smelting,
blasting, smoking, stonecutting, smithing **and any mod's custom machine recipe** are all covered with no
per-mod code.

`compat/economy/RecipePricing` (no Minecraft imports, covered by 11 harness assertions) does the maths:

```
price(item) = min over its recipes of ( Σ count × price(cheapest option per ingredient) )
                                      ÷ output count
                                      × labour
```

| Rule | Value | Why |
|---|---|---|
| Cheapest recipe wins | — | a player always takes the cheapest route, so the shop must not pay more than that |
| Cheapest option per ingredient | — | a tag of 8 plank types costs what the cheapest plank costs |
| Divide by output count | — | 1 log → 4 planks once made planks cheap; 1 ingot → 9 nuggets is 1/9 of an ingot each |
| Labour: crafting | ×1.25 | a craft is not free |
| Labour: smelting / cooking | ×1.40 | fuel |
| Labour: stonecutting | ×1.20 | |
| Labour: smithing | ×1.50 | |
| Labour: machine (any other recipe type) | ×1.60 | machine time |
| Rounding | **nearest whole unit**, minimum 1 | "to the nearest value"; nothing can be worth 0 |
| Buy price | **sell × 3** | EconomyCraft's own ratio (iron 15/5, diamond 280/84) |
| Recursion depth | 24, cycles broken to the base price | a 1:1 conversion loop in a mod must not hang the server |

Worked examples (using the keyword table's base materials: iron ingot 5, diamond 40, planks 2):

| Item | Recipe | Sell | Buy |
|---|---|---|---|
| iron block | 9 × iron ingot | 45 × 1.25 = 56 | 168 |
| diamond sword | 2 × diamond + 1 × stick | (80 + 1) × 1.25 = 101 | 303 |
| iron nugget | 1 ingot → 9 | (5 ÷ 9) × 1.25 = 1 | 3 |

## 3. Fixing a shop that already has the flat $21s

The first run after updating re-prices them automatically — an entry still at exactly **sell 7 / buy 21**
is treated as the old generator's placeholder and recalculated (genuine raw materials re-price to the
same number, so nothing legitimate moves). Turn that off with
`-Dshadowclangaming.economyMigrateFlat=false`.

Two other safety rules, because a shop is money:

* **Hand edits are never overwritten.** Every price this mod writes is recorded in
  `config/economycraft/shadowclangaming-generated.json`. An entry is only re-priced while it still
  matches what we wrote last time; change it yourself and it is yours forever.
* **`force` re-prices everything**, including entries you edited. That is the big hammer:
  `/shadowclangaming economy generate force`.

## 4. Commands

| Command | What it does |
|---|---|
| `/shadowclangaming economy status` | Path to the price file + whether it exists |
| `/shadowclangaming economy generate` | Add prices for every unpriced item (keeps existing prices) |
| `/shadowclangaming economy generate force` | Re-price **every** entry from its recipes, including hand-edited ones |
| `/shadowclangaming economy explain <item>` | Prints the recipe the price came from, plus buy/sell |

Auto-run is on by default. Disable with:

```
-Dshadowclangaming.economyAutoGenerate=false
```

The whole thing is wrapped in try/catch: if EconomyCraft changes its format, you get **one log line**
and your existing prices stay untouched. It can never crash the server.

---

## 5. How prices are chosen

### Hand-set (authoritative) — `AuthoritativePrices.java`

Everything that matters for the war economy, so a keyword guess can't get it wrong:

| Item | buy | sell |
|---|---|---|
| `shadowclangaming:war_bond` | 100 | 100 |
| `numismatics:spur/bevel/sprocket/cog/crown/sun` | 1 / 8 / 16 / 64 / 512 / 4096 (flat) | same |
| `shadowclangaming:raw_uranium` | 75 | 25 |
| `shadowclangaming:enriched_uranium` | 1,200 | 400 |
| `shadowclangaming:tritium_cell` | 2,700 | 900 |
| `shadowclangaming:decomposer` / `synthesizer` | 3,600 | 1,200 |
| `shadowclangaming:launch_pad` | 2,400 | 800 |
| `shadowclangaming:target_designator` | 4,500 | 1,500 |
| `shadowclangaming:decoy_missile` | 360 | 120 |
| `shadowclangaming:srbm` / `mrbm` / `icbm` | 600 / 1,800 / 4,500 | 200 / 600 / 1,500 |
| `shadowclangaming:emp_missile` | 3,600 | 1,200 |
| `shadowclangaming:fission_missile` | 12,000 | 4,000 |
| `shadowclangaming:fusion_missile` | 36,000 | 12,000 |
| `shadowclangaming:antimatter_missile` | 180,000 | 60,000 |

**Currency is flat** (buy == sell) so coins can never be arbitraged into free money.

### Heuristic — `PriceHeuristic.java`

Ordered rules, most specific first (that ordering is load-bearing: `diamond_sword` must hit *sword*
before *gem*, `diamond_block` must hit *storage block* before *gem* — both were real bugs I caught by
testing against real item ids):

| Rule group | Examples | sell |
|---|---|---|
| armour, then tools | `*_helmet`, `*_sword`, `*_pickaxe` | 26 / 20 × material |
| machines | `*_machine`, `*_generator`, `*_reactor`, `*_centrifuge`, `mechanical_*`, `*_press` | 45–130 |
| components | `*_circuit`, `*_processor`, `*_mechanism` | 40 |
| fuel pellets | `*_pellet`, `*_fuel_rod` | 12 |
| ordnance | `*_missile`, `*_shell`, `*_cartridge`, `*_warhead` | 240 |
| storage blocks | `*_block`, `block_of_*` | 45 × material |
| raw materials | `nugget` 2, `raw_*`/`*_ore`/`chunk` 7, `ingot` 5, `dust` 4, `plate` 6, `rod` 4, `gear` 18, `gem` 40 | — |
| food / wood / misc | — | 1–10 |
| anything else | — | 7 (`modded.<namespace>`) |

Two multipliers stack on top: **material tier** (wood 0.3 → stone/copper 0.5 → iron/steel 1.0 →
brass 1.0 → gold 1.5 → diamond 3 → netherite 6) and **rarity** (brass/bronze/steel 2× →
uranium/thorium/titanium 3× → plutonium/enriched/antimatter 12×).

Buy is always **3× sell**, mirroring EconomyCraft's own ratios (iron 15/5, diamond 280/84,
nether star 3000/900), so generated entries sit naturally next to vanilla ones.

### Sample output (tested, not hypothetical)

```
minecraft:iron_ingot            ores        buy     15  sell      5
minecraft:diamond_sword         tools       buy    180  sell     60
minecraft:diamond_block         blocks      buy    405  sell    135
create:brass_ingot              ores        buy     30  sell     10
create:mechanical_press         machines    buy    135  sell     45
createbigcannons:ap_shell       military    buy    720  sell    240
hbm:uranium_ingot               ores        buy     45  sell     15
hbm:plutonium_ingot             ores        buy    180  sell     60
hbm:titanium_plate              ores        buy     54  sell     18
somemod:weird_thing             modded.somemod buy  21  sell      7
```

---

## 6. How this feeds "money is power"

```
players earn EC money  ->  buy War Bonds (100 each, flat)
                                   |
                                   v
                    load War Bonds into a Numismatic missile
                                   |
                                   v
              each 100 units of funding = one Funding unit
                                   |
                                   v
                      blast radius (2.524x price per doubling)
```

Because a War Bond is **flat 100 buy / 100 sell**, and one War Bond = 100 Funding units, EC money
converts to blast radius at a fixed, unfakeable rate. Nobody can buy war bonds cheap and sell them
dear, and nobody can turn a nuke into profit by laundering items.

If you'd rather have players pay **directly from their EC balance** instead of carrying coins, that
needs EconomyCraft's API (balance get/spend) rather than its price file — say the word and I'll wire a
"bank card" item to it. Right now the coin-item route works with zero dependency on their API, which is
why I chose it.

---

## 7. Limits you should know

- **Only prices items that exist at runtime.** Removed mods leave stale rows in the file; harmless, they
  just sit there.
- **`stack` is the item's real max stack size**, read from the item itself, not assumed 64.
- **This cannot make an unpriced item *unbuyable*.** If you want certain warheads to be
  craft-only, set `unit_buy: 0` on those entries after generation (the generator won't fight you).
- **EconomyCraft owns the file.** If you regenerate from its jar (`force` + deleted config) you'll bring
  vanilla defaults back but keep all modded entries that already existed. Backups are written every run.
- **Modlist-dependent tuning:** the heuristic gives every mod *a* price. For the handful of items that
  define your server's economy (rare ores, war machines, currency-like items), you want hand-set
  numbers. That's `AuthoritativePrices.java` — one line each.

---

## Paying for a warhead: two paths, both supported

There is **no cap** on funding, so there is also no single "correct" way to pay. Both of these load
the same Numismatic warhead and use the same funding curve.

### Path 1 - Bank Card (primary)

`shadowclangaming:bank_card` spends straight out of the player's **EconomyCraft balance**.

| Action | Result |
|---|---|
| Sneak + right-click in air | Cycle withdrawal tier: 1,000 / 10,000 / 100,000 / 1,000,000 / *next radius step* |
| Right-click a launch pad | Withdraw the selected amount, add it to the loaded Numismatic warhead |

- If the balance is smaller than the selected tier, the click withdraws **everything they have**
  instead of failing, so a poor player still makes progress.
- The *next radius step* tier asks `Funding` for the exact price of one more block of radius, which
  is always the most efficient purchase on the curve.
- Balance and radius are reported in chat every time, and hitting radius ≥ 512 broadcasts the
  strategic warning server-wide.
- The last known balance is cached on the card itself so the tooltip works client-side.

**Why reflection and not a compile dependency.** EconomyCraft is GPL-3.0. `EconomyCraftBalance`
talks to `com.reazip.economycraft.api.v1.*` through method handles and never imports a single class
from it. Consequences: our mod still builds and loads with EconomyCraft absent, we ship no derived
code, and if their API moves we log one warning and the card disables itself instead of crashing the
server. The API surface actually used is tiny and stable:

```
EconomyCraftApi.get(MinecraftServer)      -> api
api.balances()                            -> BalanceApi
balanceApi.getBalance(UUID)
balanceApi.removeMoney(UUID, long)        -> BalanceMutationResult (#successful())
api.formatMoney(long)                     -> "$1,234"
```

Their implementation asserts it is on the server thread (`requireServerThread()`), which item
interaction always is - never call this from a blast worker thread.

### Path 2 - loading items (optional extra)

Coins work exactly as before and are the fallback whenever `EconomyCraftBalance.isAvailable()` is
false. Sneak + right-click loads the **whole stack** rather than one coin:

| Loaded (64 of) | Units | Radius |
|---|---|---|
| Spur | 64 | 2 |
| Bevel | 512 | 13 |
| Sprocket | 1,024 | 22 |
| Cog | 4,096 | 64 |
| Crown | 32,768 | 305 |
| Sun | 262,144 | 1,450 |

Players who would rather hand over physical currency than let a card near their savings keep that
option, and it keeps working on servers where the economy plugin has been swapped out.

---

## The Auto-Trader: items in, money out

`shadowclangaming:auto_trader` is the machine version of the Bank Card. It holds **one signed Bank Card**,
and that card's owner is the account: every credit and every debit lands on their EconomyCraft
balance. Pull the card and the machine is dead.

### Controls (no GUI)

| Input | Result |
|---|---|
| Right-click with an item | Queue it in the **sell** slot |
| Sneak + right-click with an item | Set the **buy template** to that item |
| Right-click with a signed Bank Card | Install the card (this is the account) |
| Right-click, empty hand | Collect purchases + print status |
| Sneak + right-click, empty hand | Eject the card |
| Pipe items into any side | They land in the sell slot |
| Pull items from any side | You get purchases out of the output slot |
| Feed FE into any side | Converted to money |

Unsellable items are **handed back** rather than eaten — right-click empty-handed and they return,
because silently destroying a player's item is never acceptable.

### Money from items

Items sell at whatever EconomyCraft says they are worth (`PriceApi.resolve` → `unitSellPrice`), which
is our generated table from {@link PriceHeuristic}. Whole stack, one tick, credited instantly. Because
buy is 3× sell, an Auto-Trader loop can never be used to launder money against the shop.

### Money from power — the cube

This is the part with your curve in it. The n-th currency unit costs `100 × n³` FE:

| Unit | Marginal FE | Cumulative FE |
|---|---|---|
| 1st | 100 | 100 |
| 2nd | 800 | 900 |
| 3rd | 2,700 | 3,600 |
| 10th | 100,000 | 302,500 |
| 21st | 926,100 | 5,336,100 |
| 100th | 100,000,000 | 2,550,250,000 |
| 256th | 1,677,721,600 | 108,214,681,600 |

Closed form: `total(N) = 100 × (N(N+1)/2)²` — verified against a brute-force sum of the marginals, they
match to the digit.

Three things make this behave:

1. **One unit per tick.** That is what makes "100 FE/t = $1" literally true: the first unit draws
   exactly 100 FE in one tick, then the cost cubes away.
2. **The counter is per player, not per block.** Ten Auto-Traders do not give you ten cheap curves —
   they share one, because the money all lands in the same bank account. Stored in the world's saved
   data, survives death and dimension changes.
3. **It cannot run away.** At 100 kFE/t a player reaches 256 units (a Sun's worth of radius) in about
   15 hours of continuous feed, and after that a single unit costs more than a Mekanism fusion reactor
   will ever produce in a tick. The machine quietly stops being an income source, which is the point:
   it is a leg-up, not a mint.

Item sales are **not** subject to the cube — they are already limited by farming.

### Money into items — redstone

Put one of the target item in the **template** slot (sneak + right-click it), then pulse the machine
with redstone. It buys one unit of the template at the shop's buy price, per pulse, repeating every
second while the signal is held. Purchases stack in the output slot and are pushed into any adjacent
inventory so a silo does not jam.

Combined with a comparator (output signal = FE buffer level) you can build a fully automatic factory:
`ore farm → trader sells → bank balance → trader buys → assembler`.

### Operator tooling

```
/shadowclangaming power <player>          # units converted from FE + next unit price
/shadowclangaming power reset <player>    # clear one counter (returns that player to the cheap curve)
/shadowclangaming power resetall          # clear every counter
```
