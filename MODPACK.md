# The server stack — 1.21.1 / NeoForge

Everything below was checked on **23 Oct 2026** against Modrinth's API / the mods' own manifests.
Versions are *tested-together candidates*, not guarantees — but they all declare 1.21.1 + NeoForge.

Pin exact versions in your pack. "Latest" is how servers break a week before a stream.

---

## 1. Mandatory core

| Mod | Version checked | Notes |
|---|---|---|
| **NeoForge** | `21.1.264` (any 21.1.x) | Java 21 required |
| **Create** | `6.0.10+mc1.21.1` | brings **Flywheel 1.0.x** with it — required by NTM:DE |
| **Sable** | `2.0.6+mc1.21.1` | physics / sub-level library; **required by Aeronautics** |
| **Create Aeronautics** | `1.3.2+mc1.21.1` | the airship/plane physics layer |
| **Create Big Cannons** | `5.11.7` | cannons, shells, propellant. **ShadowClanGaming reads and fires these** through a reflective cannon director — no compile dependency either way |
| **Create: Radars** | `0.4.9.4-1.21.1` (or the Unofficial Port) | modId **`create_radar`**, custom licence. Optional: our radar counts its dishes and reads their track list where that build exposes one (`/term radar create`). Never required |
| **Create: Numismatics** | `1.1.0+neoforge-mc1.21.1` | coins/banking, LGPL-3.0, has an API |
| **NTM: Decentralized Edition** *(optional)* | `1.0.0-beta.x` | modId **`hbm`**, LGPL-3.0. CurseForge (`ntm-decentralized-edition`); Modrinth listing under review. **Beta** — and now **optional**: ShadowClanGaming has its own warheads. Install it only if you want reactors/radiation/heavy industry alongside |
| **ShadowClanGaming** | `0.1.0` | this project — build it yourself (§4). Standalone; needs nothing else |

> **Minimum viable unique server:** NeoForge + ShadowClanGaming only. The radar, the track list, the
> cannon director and the whole computer layer are ours; CBC is only needed for the *gun*, and
> Create: Radars is only needed if you already built one. Add Create/Aeronautics for the
> airship-and-contraption flavour, CBC for cannons, Numismatics for the economy. NTM:DE is the
> optional "deep industry" layer now, not a requirement.

> NTM:DE's manifest hard-requires `flywheel [1.0.0,2.0)` — that ships with Create 6.x, so Create is
> effectively mandatory on this stack. Don't try to run NTM:DE alone.

## 2. Quality of life (pick from these)

| Mod | Version checked | Why |
|---|---|---|
| JEI **or** EMI | `19.69.0.457` / `1.1.24+1.21.1+neoforge` | recipe viewer. **Don't run both** |
| Jade | `15.10.6+neoforge` | what block am I looking at |
| ModernFix | `5.27.26+mc1.21.1` | startup + memory |
| Architectury API | `13.0.11+neoforge` | only if an addon demands it |
| Sophisticated Backpacks | `1.21.1-3.26.14.2222` | hauling missile parts |
| Curios | `9.5.1+1.21.1` | dependency for some addons |
| Sable: Physics Compat | `1.3.0` | tags for modded blocks on contraptions |
| Ponder | `1.21.1-2.4.0+neoforge` | if you write tutorial scenes (M6) |

**Create addons that are known-good on 1.21.1:** Create Connected `1.3.3`, Copycats `3.0.9`,
Create Crafts & Additions `neoforge-1.21.1-1.7.2`, Create Diesel Generators `1.21.1-1.3.15`,
Create Jetpack `5.2.1`.
**CBC addons:** Going Ballistic, Advanced Technologies, CBC Peripherals (all 1.21.1).

## 3. Warnings that will save you a weekend

- **Shaders.** Both Create Aeronautics and NTM:DE list Iris/Oculus rendering problems in their own
  READMEs. For a public server: no shaders, or test every single contraption + machine.
- **NTM:DE is beta.** Its own author says to expect bugs. Never run it on a world you care about
  without backups, and expect things to move between releases.
- **Don't guess optimisation mods.** Several "obvious" ones (e.g. FerriteCore) had no confirmed 1.21.1
  NeoForge build when I checked. Add one at a time, and only from a page that lists 1.21.1 + NeoForge.
- **Two recipe viewers = weird crashes.** Pick one.
- **Aeronautics is NeoForge-only, forever.** No Forge/1.20.1 backport is authorised. This whole stack
  is 1.21.1 NeoForge or nothing.

---

## 4. Building and installing ShadowClanGaming

```bash
cd voltz_reborn
./gradlew build          # needs JDK 21
# → build/libs/shadowclangaming-0.1.0.jar
```

Copy that jar into `mods/` on the **server and every client**. (A client-only jar would desync; there
is nothing client-only in this mod, it ships both sides.)

Dev loop instead of jar-juggling:

```bash
./gradlew runClient     # dev client with the mod
./gradlew runServer     # dev server, --nogui
```

---

## 5. Server configuration

### RAM and JVM flags

This stack is genuinely heavy: Create contraptions + Sable physics + NTM machines.

- **Minimum** 6 GB heap for ~10 players, **8–10 GB** for 20+, plus the OS.
- Clients: give the launcher **6 GB**.

`user_jvm_args.txt` (NeoForge server):

```
-Xms8G
-Xmx8G
-XX:+UseG1GC
-XX:+ParallelRefProcEnabled
-XX:MaxGCPauseMillis=200
-XX:+UnlockExperimentalVMOptions
-XX:+DisableExplicitGC
-XX:+AlwaysPreTouch
-XX:G1NewSizePercent=30
-XX:G1MaxNewSizePercent=40
-XX:G1HeapRegionSize=8M
-XX:G1ReservePercent=20
-XX:G1HeapWastePercent=5
-XX:G1MixedGCCountTarget=4
-XX:InitiatingHeapOccupancyPercent=15
-XX:G1MixedGCLiveThresholdPercent=90
-XX:G1RSetUpdatingPauseTimePercent=5
-XX:SurvivorRatio=32
-XX:+PerfDisableSharedMem
-XX:MaxTenuringThreshold=1
```

### `server.properties` (the ones that matter here)

```properties
view-distance=8
simulation-distance=6
sync-chunk-writes=false
network-compression-threshold=256
max-tick-time=-1
enable-command-block=true
```

- **`simulation-distance=6`** is the important one: Sable contraptions and NTM machines ticking in
  chunks nobody can see is the #1 source of lag on this kind of pack.
- **`max-tick-time=-1`** stops the server from watchdog-killing itself during a big missile strike
  (you still get watchdog logs via other means if you need them).
- Pre-generate your world (`chunky` or similar) before opening it. Aeronautics + worldgen spikes hurt.

### Backups

Hourly world backups, retained for a week. Not a joke: NTM warheads, falling contraptions and beta
mods are three separate ways to lose a world.

---

## 6. Recommended roll-out order

Do not install all of this at once and then debug the pile.

1. **NeoForge + Create only.** Confirm you can join.
2. **+ Sable + Aeronautics.** Build one small airship. Confirm it flies.
3. **+ Create Big Cannons.** Fire one cannon.
4. **+ NTM:DE.** Place one machine, make sure the world still loads after a restart.
5. **+ Numismatics.**
6. **+ ShadowClanGaming jar.** Creative tab appears, launch pad fires an SRBM at a target.
7. Only then add JEI/Jade/QoL and the Create addons, one at a time.

If step *n* breaks, you know exactly which mod did it.

---

## 7. Making it a distributable pack

- **Modrinth pack:** easiest — `packwiz` or the Modrinth app's pack export. Modrinth requires every
  mod in the pack to be hosted there, so **NTM:DE (CurseForge-only for now) blocks a pure Modrinth
  pack** until its listing is approved.
- **CurseForge pack:** handles everything on the list, including NTM:DE. Remember CurseForge packs
  must not bundle jars — reference them.
- **Self-hosted server:** just a zip of `mods/` + NeoForge installer + this doc. Simplest for a
  private server, and completely legal as long as you don't publicly redistribute the other authors'
  jars as your own product (see `LICENSING.md`).

---

## Known conflicts in your submitted modlist (386 mods)

Three entries will stop the game at the loading screen or cause silent breakage. Fix these before
debugging anything else:

| Problem | Evidence | Fix |
|---|---|---|
| **Duplicate `titanium`** | `titanium-1.21-4.0.40.jar` **and** `titanium-1.21-4.0.50.jar` | Keep **4.0.50**, delete 4.0.40. Two jars with mod id `titanium` abort loading. |
| **Duplicate Create Aeronautics** | `create-aeronautics-bundled-1.21.1-1.0.3.jar` **and** `...-1.3.0.jar` | Keep **1.3.0** (matches Sable 2.0.6), delete 1.0.3. |
| **HBM NTM Neo 1.0.1 installed** | `HBMNTM-Neo-1.0.1.jar` (mod id `hbm`) | Fine to keep — ShadowClanGaming only reads `hbm:*` item ids for pricing and never links against it. Just be aware its own nukes and ours will not interact. |

Other things worth knowing about that pack:

- **EconomyCraft 1.4.2** is the version our Bank Card targets; its `api.v1` package is present and
  used via reflection, so a later 1.11.x upgrade will keep working.
- **Create 6.0.10 + CBC 5.11.7 + ~10 CBC addons**: `createbigcannons:*_shell` / `_cartridge` land in
  the `military` price band (240 sell), so selling captured ammunition is a real income stream but
  never out-earns a Sun.
- **Draconic Evolution**: `awakened_*` is priced at a full tier above plain draconium (480 vs 60
  sell), because ProjectE-style conversion loops would otherwise let players launder it.
- **Mystical Agriculture** essences are deliberately cheap (8 sell) — they are infinitely farmable,
  so pricing them high would be an infinite money glitch.
