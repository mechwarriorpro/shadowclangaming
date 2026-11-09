# Power transport: the Hyperconductor Cable

`shadowclangaming:hyperconductor_cable` — a long-based power trunk that speaks every power system in your
pack without a config file, a converter block or a per-mod integration.

Craft: 6 copper ingots, 2 gold ingots, 1 redstone → 8 cables.

---

## 1. The honest problem with "long"

NeoForge's energy interface — `IEnergyStorage` — is **`int`**. Its methods are
`int receiveEnergy(int, boolean)` and `int extractEnergy(int, boolean)`. The maximum it can move in a
single call is **2,147,483,647**. Every FE machine in your pack speaks that interface, which means the
cap applies to all of them, in every mod, forever. No mod can change another mod's interface.

So the cable does two different things at once:

| Layer | Type | Value |
|---|---|---|
| Internal buffer, per cable | `long` | 4,000,000,000 |
| Per-tick transfer budget, per cable | `long` | 1,000,000,000 |
| Cable → cable handoff | `long` | one arithmetic op, no interface |
| Cable → foreign FE machine | `int` **per call**, looped | up to 1 GFE/t total |
| Cable → ProjectE EMC storage | `long` | native, no conversion loss beyond the exchange rate |

**The int limit is a per-call limit, not a throughput limit.** The cable never calls
`receiveEnergy` once; it calls it in a loop, each bite up to `Integer.MAX_VALUE`, until either its
per-tick budget or the destination's headroom runs out.

Measured against a mock machine with the interface's own ceiling (this is the real loop from
`PowerCableBlockEntity`, run under `javac`):

```
machine limited to 10k/call ...... 2,000,000 moved over 200 ticks   (correct: 10k/t)
int-limited machine, one tick .... 1,000,000,000 moved              (1 GFE in one tick)
5 GFE request through int API .... 5,000,000,000 moved in 3 calls   (the int ceiling is 2.1 GFE)
```

That last line is the whole point: asking for 5 billion through an API that only accepts 2.1 billion
per call works fine, because we ask three times.

### Scaling

Throughput is **per cable block**, and each cable has its own budget. A run of 10 cables moves
1 GFE/t each tick *in aggregate* along the run because each hop has its own budget and the balancing
step passes energy along. If you need more than a gigawatt-equivalent, run parallel cables — the
numbers are additive, and there is no artificial network cap anywhere.

---

## 2. What it converts

| System in your pack | How it presents | What the cable does |
|---|---|---|
| **FE / Forge Energy** | `Capabilities.EnergyStorage.BLOCK` | 1 FE = 1 internal unit, looped int calls |
| **Mekanism** | FE capability (its cables and machines expose FE) | connects directly |
| **Ender IO** | Forge/NeoForge Energy conduits | same capability — no special code needed |
| **Extreme Reactors / NuclearCraft / Nuclearscience / Draconic Evolution** | FE capability | connects directly |
| **AE2** | AE's internal AE/A is not a NeoForge capability; its **Energy Acceptor** converts FE → AE | put the cable on the Energy Acceptor |
| **Dynamic Electricity** | a *converter mod*: AC/DC motors move Electrodynamics **Joules** ↔ **FE** at 1 J = 1 FE | there is **no separate "DE power" capability to implement** — put a DE motor between the cable and the Electrodynamics grid and the cable carries the FE side at long scale |
| **ProjectE EMC** | `IEmcStorage` — a **long**-based interface, not FE-shaped | bridged directly by reflection, see below |
| **Create rotation, Mekanism heat/chemicals, Botania mana, etc.** | not electrical | out of scope, and no cable can fix that without a specific converter for each |

### Dynamic Electricity, specifically

Worth stating plainly because it is the one that looks like it needs work and does not: Dynamic
Electricity exists *because* Electrodynamics Joules are not FE. Its whole job is `J ⇄ FE` conversion
through AC and DC motor blocks (1000 J @120V → 1 kFE, and 1 kFE → 950 J going back). Once its motor
has done that, the output is plain FE on a normal capability, which is exactly what this cable
already moves at long scale. Adding "DE support" would have meant reimplementing their motors with
extra loss — strictly worse for you.

### ProjectE EMC

EMC is **not a power network**. It is matter value: it lives on items, in player knowledge, and in a
handful of block entities (EMC Links, condensers, relays). There is no "EMC pipe" in ProjectE. So the
cable does the only thing that can work — it finds those storages itself and moves EMC in and out:

```
16 EMC  ->  1 FE        (into the cable)
 1 EMC  <-  16 FE       (out of the cable)
```

A full round trip therefore loses **256×**. That is deliberate: EMC is farmable with a condenser, so a
fair exchange rate would be a matter printer. Numbers: one diamond is 8,192 EMC → 512 FE → 32 EMC back.
A Klein Star Omega (51.2 M EMC) is worth 3.2 M FE as a one-shot battery.

Reflection, not linkage: ProjectE's EMC interface has been reshaped at least twice
(`extractEmc(long, EmcAction)` / `insertEmc(long, EmcAction)` in modern versions, `provideEMC` /
`acceptEMC(side, long)` in older ones). `compat/projecte/ProjectEEmc` probes for the modern shape,
falls back to the legacy one, and if neither matches it logs one line and turns the EMC channel off —
the cable keeps working as a pure FE device. Nothing in that path can crash the server.

**Consequence worth knowing:** ProjectE's own blocks will not initiate an EMC transfer into this
cable, because they look for their capability on neighbours and we deliberately do not implement
their interface. The cable pulls from them and pushes into them, which covers both directions you
actually need.

---

## 3. Is EMC → FE → money an exploit?

It is the combination to worry about: ProjectE farms EMC, the cable turns EMC into FE, and the
Auto-Trader turns FE into currency. It does not work, for two reasons.

1. **256× round-trip loss** means EMC is a bad battery, not a source.
2. **The Auto-Trader's power curve is cubic per player** (`docs/ECONOMY.md`): the 21st currency unit
   costs 926,100 FE and the 100th costs 100,000,000 FE. Even an infinite EMC farm converts to money at
   a rate that collapses after about twenty units, and the counter is per player, so more cables and
   more traders do not reset it.

If you ever loosen either of those, this is the first place to re-check.

---

## 4. In game

* Cables connect visually to any neighbour that has a recognised power protocol, including other
  cables. They re-check when a neighbour changes, so placing a machine next to a cable connects it.
* Empty hand, right-click a cable: prints buffer / capacity / FE moved last tick, and which protocols
  are live. That readout is how you verify the long-scale numbers in a real world.
* Cables never push into a block they just pulled from, so a battery that both stores and supplies
  cannot ping-pong energy all tick.
* No cable-side cap on distance, network size or stacked throughput. The only limits are the 4 GFE
  per-cable buffer and 1 GFE/t per-cable budget.

### Tuning

Constants live at the top of `PowerCableBlockEntity`, in one place:

```java
public static final long CAPACITY          = 4_000_000_000L;  // per cable
public static final long TRANSFER_PER_TICK = 1_000_000_000L;  // per cable
public static final long EMC_PER_FE        = 16L;             // 16 EMC -> 1 FE
public static final long FE_PER_EMC        = 16L;             // 16 FE -> 1 EMC
```
