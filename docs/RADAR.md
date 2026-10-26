# Radar and fire control

This is the round that makes the base *defensible*: a radar with a real track list, a computer that can
read it, and a cannon director that can lay and fire a Create Big Cannons mount at what it finds.

Everything here works with **zero optional mods** except the cannon itself — the radar, the tracks, the
lock, the lead calculation and the director block are all ours. Create: Radars and Create Big Cannons are
read and driven **reflectively**, so neither is a dependency and neither can break our build.

---

## 1. The radar

One block, right-click for a sweep, sneak-right-click to toggle automatic sweeping. The rest of its life
is as a peripheral on a network cable.

| Property | Value |
|---|---|
| Base radius | **256 blocks** |
| Per extra dish | **+128 blocks** (a dish within 4 blocks horizontally, 2 vertically, of the first) |
| Maximum | **4096 blocks** (31 dishes) |
| Track list | `8 + 4 × (dishes − 1) + GPU contacts`, hard cap **128** |
| Contacts kept on the block | 64 |
| Candidate entities per sweep | 4096 (hard cap) |
| Auto-sweep rate | 1 sweep per second, never per tick (it reuses the GPU budget the last computer-issued sweep handed it) |

The radius is deliberately not uncapped. Uncapped radius is the right answer for a *warhead* — that is
the whole point of the Numismatic missile — and the wrong answer for a *sensor*, because a 4096-block
sweep is already a thirty-million-block volume and doing it every tick would be the single most expensive
thing on any server. It is still twice what a Create: Radars bearing manages and it is a block you can
build an array of.

The cap is **4096** and the per-dish step is unchanged at **128**, so reaching the cap takes 31 dishes
(`256 + 128 × 30`). The sweep is a single `AABB` *entity* query rather than a block iteration, so the cost
scales with how many entities are in range (hard-capped at 4096 candidates), not with the volume — which is
why the bigger radius does not cost more per tick than the smaller one did.

### 1.1 What a sweep actually does

1. One bounding-box entity query (`AABB` inflated to the array's radius).
2. Drop item entities — a radar that reports dropped cobblestone is not a radar.
3. Classify: **missile** (anything of ours flying), **player**, **vehicle** (minecart/boat),
   **monster** (any `Mob`), **other**.
4. Compute distance and **closing speed** (radial component of velocity: positive = coming at you).
5. `RadarMath.select` filters by range, sorts by threat, keeps the best N.

Every contact keeps a **stable track number** allocated on first sight and remembered per entity UUID, so
`/term radar track 3` means the same thing two sweeps later.

### 1.2 Threat

```
threat = kindBase + 20 × (1 − distance/radius) + (10 if closing)      clamped 0..100
kindBase: missile 80 · player 50 · vehicle 40 · monster 30 · other 10
```

A missile at the edge of the scope outranks a monster at your feet, which is the correct answer for an
air-defence radar and the reason `cannon engage` can be trusted unattended.

### 1.3 Commands (Radar Card)

```
/term radar                 sweep, then print the scope
/term radar scan            same, spelled out
/term radar list            the cached scope, no sweep
/term radar status          radius, dishes, tracks, auto-sweep, sweeps run
/term radar track 3         one contact in full: bearing, elevation, range, threat, closing
/term radar lock 3          hand track 3 to the cannon director     (lock 0 releases)
/term radar auto on|off     sweep once a second
/term radar bridge          what Create: Radars says about itself here
/term radar create          read whatever Create: Radars will admit to
```

Bearing is compass-style: **north is 0, clockwise**, so `brg 090` is east. Elevation is degrees above the
horizon. Both are computed against the antenna's block centre, so the numbers a player reads are the
numbers the gun uses.

---

## 2. Create: Radars integration

Mod id `create_radar`. Our radar counts dishes near it and reports them, and — where that build exposes
one — reads their track list too.

What the bridge does, in order:

1. `ModList.get().isLoaded("create_radar")` — one cached check, one log line if absent.
2. Scan 8 blocks around our antenna for block entities whose class name mentions "Radar".
3. Try, newest names first: `getTrackedPositions`, `getTrackedEntities`, `getContacts`, `getTargets`,
   `getTargetPositions`, `getDetectedPositions`.
4. Accept whatever shape comes back: `Vec3`, `BlockPos`, `Entity`, a `Collection`/`Map` of any of those, or
   a saved `CompoundTag` with a `Pos` list.
5. If nothing answers, still report the dish count, because "linked, no readable track list on this build"
   is a useful answer and a crash is not.

Why not just depend on them: their licence is custom, and the 0.4.x line replaced bearing linking with a
Network Controller and a Data Link item. A compile dependency would mean our mod refuses to load beside
theirs the day they rename a method. Reflection costs one try/catch block and never takes the server down.

**Their radar stays their radar.** Ours is the one with the long range and the track list; theirs is the
one you already built, and now it feeds ours.

---

## 3. The cannon director

One director owns **one** Create Big Cannons mount. It holds the mount position, the propellant velocity
it assumes, an armed flag and the last thing it said.

| Property | Value |
|---|---|
| Mount link range | 6 blocks |
| Default muzzle velocity | 6.0 blocks/tick (clamp 1.0 – 32.0) |
| Pitch limits | −10° … +45° (CBC's own travel) |
| Trigger release | 2 ticks after firing |
| Fallback pulse | 2 ticks of redstone on the face looking at the mount |

### 3.1 The maths

A cannon does not point at a target, it points above one:

```
t      = horizontal / velocity        (ticks of flight)
drop   = 0.5 × 0.05 × t²              (blocks fallen)
pitch  = atan2(height + drop, horizontal)
yaw    = atan2(−dx, dz)               Minecraft convention: 0 = south, 90 = west
```

At 100 blocks and 6 blocks/tick a shell is in the air 16.7 ticks and falls 6.9 blocks; the barrel lifts
**3.97°**. `CannonMath` is asserted by `tools/run_logic_harness.sh` so those numbers cannot drift.

Firing at a moving contact uses the same flight time to **lead** the target:
`aim = position + velocity × flightTicks`.

### 3.2 How it fires

Two paths, in order:

1. **The CBC bridge.** `CannonMountBlockEntity` in 5.x has public `setYaw(float)`, `setPitch(float)`,
   `getYawOffset(float)`, `getDisplayPitch()`, `isRunning()` and
   `onRedstoneUpdate(boolean, boolean, boolean, boolean, int)`. We aim with the first two and fire by
   calling the last one with `(false, false, true, false, 15)` — assembly flags equal so CBC never
   reassembles, fire flag raised so it shoots exactly once — then releasing it two ticks later with
   `(false, false, false, true, 0)` so the next shot registers a change. If the accessors have moved, we
   fall back to the fields `cannonYaw` / `cannonPitch`.
2. **A real redstone pulse.** If the bridge cannot answer, the director block becomes a redstone source for
   two ticks on the face that looks at the mount — the mechanism CBC documents for vanilla players.

Nothing here blocks the server thread, and nothing fires without the director being **armed**.

### 3.3 Commands (Cannon Card)

```
/term cannon list                   directors on the wire, arm state, mount, last result
/term cannon status [n]             mount, lay, bridge answer, live mount read-back
/term cannon link [n]               link a director to its nearest mount
/term cannon arm|disarm [n]
/term cannon velocity 6.5           what the propellant does
/term cannon aim 400 70 -120        lay the gun
/term cannon fire 400 70 -120       lay and shoot
/term cannon fire                   shoot at the locked radar track, with lead
/term cannon engage                 sweep, take the worst contact, lead it, shoot
/term cannon auto on|off            let the terminal engage every 2 seconds while armed
```

`engage` will only take a **missile** or a **player** — anything else is reported and refused, so an
unattended battery does not empty itself into a wandering cow. `auto` runs from the terminal's own ticker
(`AUTO_ENGAGE_TICKS = 40`) and only writes to the log when a shot actually happened.

---

## 4. Building a battery

```
                    ┌──────────── radar (on a tower, sky above)
                    │
   cable ───────────┼──────── rack (cpu + gpu: pipelines widen the track list)
                    │
                    ├──────── terminal (radar card + cannon card)
                    │
                    ├──────── cannon director ──── CBC cannon mount ──── cannon
                    │
                    └──────── orbital node (high up, on the same cable)
```

1. Place the **radar** on a tower. Add dishes around it for range: 256 → 4096 across 31 dishes (128 blocks each).
2. Run **network cable** (one channel — set it with a Frequency Chip) between radar, rack, terminal and
   director.
3. Fit the terminal with a **radar card** and a **cannon card**; put a CPU and GPUs in the **rack**.
4. Right-click the **director** while sneaking to link it to the mount next to it.
5. `/term radar scan` — check the scope, then `/term radar lock <n>`.
6. `/term cannon arm` then `/term cannon fire`, or `/term cannon auto on` and walk away.

The terminal prints why it refused, every time. That is the design rule for this whole layer: a base that
does not shoot should always be able to tell you which link in the chain is broken.

---

## 5. Performance and limits

| Thing | Bound |
|---|---|
| Sweep | 1 per command, or 1 per second with auto-sweep |
| Entities examined per sweep | 4096 |
| Contacts kept | 64 on the block, 128 in a select |
| Cable walk per command | 4096 positions, depth 256 (`NetworkGraph`) |
| Subcommand recursion | depth 4 (`TerminalShell.MAX_DEPTH`) |
| Orbital queue | 64 entries per node |
| Unattended engage | every 40 ticks, only while armed |
| CBC access | reflection only, every call wrapped; one miss disables the bridge, never the tick |

Nothing in this round runs per tick except: the terminal's pulse timer, the director's two-tick trigger
release, and one radar sweep per second if a player turned it on.

---

## 6. Files

```
computer/CpuTier.java                cpu tiers and the socket rule
computer/GpuTier.java                gpu tiers
computer/ComputeBudget.java          the only place the numbers are added up
computer/HardwareCommands.java       /term cpu, /term rack, /term node
computer/NetworkRackBlockEntity.java four sockets, reports numbers, runs nothing
computer/OrbitalNodeBlockEntity.java sky access, latency, saved queue
computer/Subcommand.java             one queued command, NBT-round-trippable
radar/ContactKind.java               missile / player / vehicle / monster / other
radar/RadarContact.java              a contact as numbers, no Entity reference
radar/RadarMath.java                 radius, tracks, bearing, band, threat, selection
radar/RadarSweep.java                world → contacts, once per command
radar/RadarBlockEntity.java          array counting, track ids, lock, persistence
radar/RadarCommands.java             /term radar
radar/CreateRadarBridge.java         optional, reflective
cannon/CannonMath.java               yaw, drop, pitch, lead
cannon/CannonMountBridge.java        optional, reflective, with a redstone fallback
cannon/CannonDirectorBlockEntity.java mount link, lay, trigger release
cannon/CannonCommands.java           /term cannon, engage, auto-engage
block/{Radar,NetworkRack,OrbitalNode,CannonDirector}Block.java
```

Verification for this round (no jar is ever produced in the build sandbox):

```
python3 tools/sweep.py                    # 144 java / 274 json / 474 lang keys
bash tools/run_logic_harness.sh           # 86 assertions, ALL TESTS PASS (JDK 21)
```
