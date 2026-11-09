# Computers: the terminal, the channel bus, and the hardware

> **Round 6 update.** The Lua layer is now real: an original interpreter, a screen model and an
> OpenComputers-shaped machine API (`component`, `computer`, `event`, `thread`, `gpu`, `term`, `fs`) live in
> `com.shadowclangaming.lua` and `com.shadowclangaming.computer.api`, and are tested by `tools/run_lua_harness.sh`
> (40 assertions) and `tools/run_api_harness.sh` (51 assertions). **`docs/LUA.md` is the document for that
> layer**; this file stays the document for the *hardware and the `/term` command shell* that already ships.
> The paragraph below is kept because it explains why the command shell came first.

**Read this first: this was not OpenComputers, and it is not a clone of it now.**

OpenComputers is a Lua virtual machine, a component network, a filesystem, a robot API and an
in-game IDE. Reimplementing that from scratch is a multi-month project on its own, and shipping a
half-written Lua VM inside this mod would be worse than not shipping one. So round 5 shipped the honest
version of "if possible": **a computer that does the three things your spec actually asked for**, with
a clean command language, no GUI, and no fake promises. Round 6 adds the interpreter, the screen and the
machine API on top of that hardware, in that order.

What you get today:

- a **Terminal** block that runs commands
- a **channel bus** (frequencies) *and* **physical network cables**, so parts can find each other by
  number or by wire
- five **cards** that each unlock one family of commands:
  - **Targeting Card** — list and fire every pad on your channel
  - **Mana Sensor Card** — read current *and* maximum mana for everyone nearby
  - **Network Card** — fetch text from the internet, through an allowlist
  - **Storage Card** — read an AE2 network (or plain containers) via a Storage Bridge
  - **Redstone Card** — read the input signal and drive six independent outputs

What was *not* here at round 5, and is being built now: Lua (done — `docs/LUA.md`), a screen and filesystem
(done), robots, drones, tablets and nanomachines (next), plus the block entity that drives a VM in world.
Redstone I/O and the other cards become Lua components in the same pass.

---

## 1. Using it

Stand next to a terminal and type:

```
/term help
/term status
/term freq 7
/term pads
/term fire 120 70 -340
/term mana
/term net https://example.org/status.txt
```

The terminal is whichever one you are **looking at** (8 blocks), falling back to the nearest within 4
blocks. No "which computer am I on" state, no session to leak. It also works from a command block: it
uses the block's position and the nearest player as the speaker, or logs silently if nobody is near.

Empty-hand right-click prints the last six log lines, so a machine can be read by walking up to it.

## 2. Channels: how a pad and a terminal find each other

```
Frequency Chip (right-click air)      -> step the channel 0..99
Frequency Chip on a Launch Pad        -> that pad joins the channel
Frequency Chip on a Terminal          -> the terminal tunes to it
/term fire x y z                      -> every ready pad on the channel fires
```

Under the hood, `computer/FrequencyRegistry.java` is saved data: channel → the pads on it. A terminal
never scans the world for pads (that would mean chunk-loading a base to answer "what is on channel 7"),
and pads re-register themselves in `onLoad()` after a restart. The chip is the only thing that ever
wrties to it, and a pad is only ever on one channel.

`/term fire` reports exactly how many pads fired and how many were unloaded or empty — a missile that
did not launch is a missile you should know about.

## 3. The Mana Sensor Card

Reads, for every player within 16 blocks:

```
name: current/max (percent%) evil=true|false
```

That is the "nanomachines can read max and current mana" requirement, wired to this mod's actual mana
pool (`ManaApi.current` / `ManaApi.capacity`, which include cast growth and the Mana Capacity
enchantment). There are no nanomachines as a separate item in this mod — the mana pool *is* the
nanomachine layer, and this card is its read-only sensor.

## 4. The Network Card

Fetches a URL and prints the first 12 lines of readable text. It is a *text* fetch: HTML is stripped to
text, no JavaScript, no images, no POST.

Three guards, all deliberate, all in `computer/WebAccess.java`:

| Guard | What it does |
|---|---|
| **Allowlist** | `config/shadowclangaming/web.txt`, one host per line. The file is created **empty**, so the card is inert until a server owner names a host. |
| **No local network** | Any host that resolves to loopback, private, link-local or any-address is refused *even if allowlisted*. No router pages, no cloud metadata endpoints. |
| **Bounded** | 5 second timeout, 200 KB cap, redirects followed, result delivered on the server thread. A slow site can never stall a tick. |

This is the Web Displays idea reduced to something safe to run on a public server: the computer can
read a status page or a wiki line, and cannot be used as a proxy to attack anything.

## 5. What a real OpenComputers successor would look like

If you want to go further, the honest order of work is:

1. **A screen block** — a real GUI with a scrolling log and a command line (the log already lives on the
   block entity, so it is ~200 lines of client code and no new transport).
2. **A tiny scripting layer** — variables and loops over the existing command set, not a full VM.
3. **Component blocks** — reactor monitor, inventory scanner, redstone I/O, each a new card.
4. **Lua** — only if the three above earn it; that is where the months go.

The channel bus, the card model and the command parser are all shaped so step 1 does not require
rewriting them.

## 6. Files

| File | Role |
|---|---|
| `computer/TerminalBlockEntity.java` | Frequency, installed cards, 40-line log |
| `block/TerminalBlock.java` | Card installation, tuning, read-the-log on right-click |
| `computer/TerminalShell.java` | The command interpreter |
| `computer/FrequencyRegistry.java` | Saved-data channel → pads |
| `computer/WebAccess.java` | Allowlisted, sandboxed HTTP text fetch |
| `computer/TerminalModule.java` | The three cards |
| `item/FrequencyChipItem.java` | Channel selector / linker |
| `command/TerminalCommand.java` | `/term <command>` |

---

## 7. Cables: making the parts actually part of one machine

Channels alone are anonymous — two pads on channel 7 answer to a terminal on channel 7 from the other
side of the world. Cables add intent:

```
place Network Cable  ->  right-click with a Frequency Chip to set its channel
```

- A cable **carries a channel**, and two touching cables only join if their channels match. The model
  refuses to draw an arm between cables that will not connect, so what you see is what the network does.
- A **device** joins the wire if it touches a cable directly: terminals, control panels, launch pads and
  storage bridges. Devices do not relay through each other — a cable has to reach the thing.
- `/term wire` prints the walk: cable count, device count, and each device's type and position.
- The walk is bounded (4,096 positions), so a player cannot lag the server by building a cable the
  size of a chunk and asking for a status read.

The cable's shape is a **core plus one arm per connected neighbour**, computed in `getShape` from six
boolean blockstate properties — no multipart JSON to keep in sync and no model loader.

## 8. The Storage Card and AE2

A **Storage Bridge** placed against an ME drive (or a chest) is what the terminal reads.

```
/term storage
  AE2: 18,442 items in 12 kinds
  18442 x Iron Ingot
  ...
```

How the AE2 read works, and what to expect:

| AE2 present | Behaviour |
|---|---|
| Yes, API shape recognised | reads `IGrid → IStorageService → MEStorage → getAvailableStacks(KeyCounter)` |
| Yes, API shape unknown | falls back to containers, and says so in the note |
| No | reads every vanilla container touching the bridge instead |

**It is reflective and optional on purpose.** AE2 is a large dependency with a fast-moving API;
compiling against it would stop this whole mod loading when AE2 updates, for a feature most players
never touch. So: look the methods up by name, cache what works, and return a plain readable line when
nothing does. Nothing here can throw into a tick.

## 9. The Redstone Card

Six independent outputs, one input, all on the terminal block itself:

```
/term redstone read                what is being fed in
/term redstone status              all six outputs
/term redstone out north 15        drive one side
/term redstone pulse west          one second at 15, then off
/term redstone clear
```

- Outputs are stored on the block entity, so a terminal that was holding a door open **keeps it open
  through a restart**. That is the behaviour an operator expects from a control system.
- `getSignal` **and** `getDirectSignal` are both implemented, and every change calls
  `updateNeighborsAt`, or a repeater downstream would never wake up.
- **Pulses are scheduled, never slept.** The terminal has a ticker that drops the output when the pulse
  expires; blocking the server thread for a second would freeze the world. (That mistake is banned in
  this codebase — the sweep greps for `Thread.sleep` in any tick path.)
- Reading your own output is deliberately not automatic: a computer that can read what it drives latches
  itself into a loop, and a redstone loop inside a tick handler is how servers die.

## 10. Programs and panels

Unchanged from the launcher round, and worth repeating here because it is what makes the cards useful:

```
/term prog save alpha        /term panel status
/term prog run alpha         /term panel arm | disarm
/term prog list              /term panel target [dimension] <x> <y> <z>
/term prog show alpha        /term panel fire
```

## 11. CPU and GPU: the machine gets a spec sheet

Until this round the terminal was one machine with one speed. Now it has **sockets**, and every
capability it has is measured against what is in them.

### 11.1 CPU tiers

| Tier | Cores | Clock | Jobs | Draw | Latency | Where it fits |
|---|---|---|---|---|---|---|
| `basic` | 1 | 800 MHz | 1 | 4 FE/t | 20 t | the CPU that ships in the terminal |
| `advanced` | 2 | 1600 MHz | 2 | 12 FE/t | 10 t | terminal or rack |
| `quantum` | 4 | 3200 MHz | 4 | 32 FE/t | 5 t | rack only |
| `photonic` | 8 | 6400 MHz | 8 | 80 FE/t | 2 t | rack only |

- **Cores** are the visible bragging number and feed the total.
- **Jobs** is how many subcommands may be in flight (queued to nodes, or running) at once.
- **Latency** is how many ticks an orbital node takes per queued subcommand — this is the only place the
  CPU makes something *fast* rather than *wide*, and it is why a PHOTONIC node in orbit is worth the
  netherite.
- **Sockets are one-way, like cards.** A module only goes in if it is better than what is there
  (`CpuTier.betterThan`), so a player cannot downgrade a base by accident and cannot pull a card out
  mid-raid.

### 11.2 GPU tiers

| Tier | Units | Blast threads | Radar pipelines | Draw |
|---|---|---|---|---|
| `off` | 0 | 0 | 0 | 0 FE/t |
| `basic` | 64 | 2 | 1 | 8 FE/t |
| `vector` | 256 | 4 | 2 | 24 FE/t |
| `tensor` | 1024 | 8 | 4 | 72 FE/t |
| `photonic` | 4096 | 16 | 8 | 200 FE/t |

A GPU does exactly two things here, and both are things the computer genuinely could not do before:

1. **Blast threads** — the blast scheduler's worker budget goes up (capped at 32).
2. **Radar pipelines** — each pipeline is **4 more contacts** a linked radar may hold per sweep.

There is no shader path and no client rendering, because this is a server-side mod with no screen. A card
that promised graphics would be a lie; this one promises throughput and delivers it in `/term cpu`.

### 11.3 How the numbers add up

One class, `ComputeBudget`, one formula:

```
cores        = terminal CPU cores + rack CPU cores + 2 × orbital nodes
jobs         = terminal CPU jobs  + rack CPU jobs  + 1 × orbital nodes      (cap 32)
blastThreads = GPU threads from every rack and the terminal                 (cap 32)
pipelines    = GPU pipelines + half the orbital nodes                       (cap 32)
radarBonus   = pipelines × 4 contacts
queueCap     = max(8, jobs × 4)
fePerTick    = sum of every module's draw
```

A worked example, because this is the number players will argue about:

```
terminal   QUANTUM cpu (4 cores / 4 jobs / 32 FE) + TENSOR gpu (8 threads / 4 pipelines / 72 FE)
rack #1    PHOTONIC cpu (8 / 8 / 80) + VECTOR gpu (4 threads / 2 pipelines / 24)
          -> 12 cores, 12 jobs, 12 blast threads, 6 pipelines, 208 FE/t
          -> radar bonus 24 contacts, queue capacity 48
```

`/term cpu` prints exactly that, recomputed on every call — pull a rack out of the wall and the next
command already knows.

### 11.4 Fitting hardware

There is no GUI, for the same reason the terminal has none. Right-click the block with the module:

| Block | CPU sockets | GPU sockets |
|---|---|---|
| Terminal | 1 | 1 |
| Network rack | 2 | 2 |
| Orbital processing node | 1 | 0 |

Upgrade chains are shapeless recipes (`previous module + parts → next module`), so the crafting grid never
implies a fake layout.

## 12. Network racks

A rack is a **peripheral that holds parts and reports numbers**. It never runs a command itself, so
nothing can fire a missile without a terminal — the paper trail always points at one block a player had
to be standing next to.

```
/term rack status        every socket in every rack on the wire
/term rack count         how many racks this machine can reach
```

Racks are found on the cable network first (walk a cable, find racks), and within 8 blocks only if the
wire finds none — the same rule the storage bridge uses, so "why is this rack not counted" has one answer:
it is not on the wire, or the cable is carrying another channel.

## 13. Orbital processing nodes and subcommands

The node is the round's one genuinely new capability: **run this subcommand somewhere else**.

```
/term node list                      nodes on the wire, queue depth, uplink state
/term node status [n]                queue, cpu, latency, last result
/term node exec   <n> <subcommand>   run it there, right now
/term node queue  <n> <subcommand>   hand it work and forget about it
/term node flush  <n>                drop its queue
```

Rules, all visible from the block itself:

- **Sky access.** `level.canSeeSky(pos.above())` or the queue is held with a printed reason. Put it on a
  tower, not in a basement.
- **Latency, not blocking.** One queued subcommand per `cpu.latencyTicks()`; the queue is drained by the
  block's ticker. Nothing sleeps.
- **It runs as the terminal that queued it.** Same cards, same channel, same identity — the node's own
  position is used for `wire` and `redstone read`, but authority never moves. A node cannot fire a pad the
  terminal could not already fire.
- **It cannot command nodes.** `node ...` subcommands are refused from a node, and the interpreter itself
  has a hard recursion depth of 4 (`TerminalShell.MAX_DEPTH`) so a program that queues itself cannot
  become an infinite loop.
- **The queue is saved.** 64 entries, results mirrored into the terminal's own log so a player sees orbit
  work from the terminal they typed at.

Turning one on, end to end:

```
1. craft an Orbital Processing Node, place it on a tower with open sky
2. run a network cable from it to the terminal (same channel)
3. /term cpu                          -> "orbital nodes: 1"
4. /term node list                    -> "node 1 at ... cpu basic, 0 queued"
5. /term node queue 1 radar scan      -> sweeps the radar, off the terminal's tick
6. /term node status 1                -> "[done] radar scan -> ..."
```

## 14. Radar and cannon commands

Two more cards, because these are the two things that touch the world in ways you want a deliberate
decision for:

```
radar card     /term radar scan | list | status | track <n> | lock <n> | auto on|off | bridge | create
cannon card    /term cannon list | status | link | arm | disarm | velocity <v> | aim <x y z> |
                            fire [x y z] | engage | auto on|off
```

Both are documented properly, with the sensor maths and the CBC integration detail, in **`docs/RADAR.md`**.
What matters here is that they close the loop this mod has been building towards since M1:

```
mine -> refine -> warhead -> pad -> computer -> radar -> cannon -> intercept
```

and that every link in that chain is a block you can walk up to and read.
