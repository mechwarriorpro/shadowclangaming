# The Lua stack: interpreter, screen and machine API

This is the round-6 foundation: **a Lua interpreter that belongs to this mod**, the **screen model**, and the
**machine API** (`component`, `computer`, `event`, `thread`, `gpu`, `term`, `fs`) that programs are written
against.

Two things are worth stating up front, because they decide everything else in this file.

**1. The interpreter is ours.** It is not LuaJ, not a fork of LuaJ, and it shares no code with
OpenComputers. That matters for two reasons: licence hygiene is then trivial, and — more practically — a
tick-driven Minecraft server needs an interpreter that can *stop in the middle of a loop and be resumed on
the next tick*. LuaJ cannot do that; it runs a script to completion on whatever thread called it. Ours can,
because the machine is a bytecode VM with explicit call frames.

**2. The API shape is deliberately OpenComputers' shape.** `component.list()`, `event.pull()`,
`term.write()`, `fs.open()` returning `nil, err` — those exact behaviours are what MineOS and the wider
library of OC programs are written against. A lookalike that nothing runs on would be pointless. So the
*surface* matches, and where behaviour is load-bearing it is matched exactly (see §6 for the honest list of
divergences).

Nothing in this document requires you to have read the Java. If you want to use it, read §5; if you want to
know how it works, read §3; if you want to know what is missing, read §7.

---

## 1. Status

| Piece | State |
|---|---|
| Lexer, parser, compiler, VM | **done**, 40 assertions in `tools/run_lua_harness.sh` |
| Standard library | **done** (`print`, `type`, `string`, `table`, `math`, `coroutine`, `pcall`, …) |
| Screen buffer (`TextBuffer`), GPU library, terminal | **done**, 51 assertions in `tools/run_api_harness.sh` |
| Machine API: `component`, `computer`, `event`, `thread`, `fs`, `colors` | **done** (same harness) |
| Block entity that drives a VM, persists a disk, sends the screen to clients | next |
| Cards as Lua components (network, redstone, storage, radar, cannon, GPU tier) | next |
| MineOS as an installable payload (`wget` installer, never OpenOS) | next |
| Robots, drones, nanomachines, subgrid support | after that |

Nothing here is compiled by the sandbox (no JDK is kept between sessions), so the two harnesses are the
verification: they compile the real classes with a real JDK 21 and run real Lua programs. `./gradlew build`
on your machine is the first full compile.

Run them yourself:

```bash
bash tools/run_lua_harness.sh    # the language
bash tools/run_api_harness.sh    # the machine API
```

Both print `ALL … TESTS PASS (n assertions)`.

---

## 2. Why a bytecode VM (and what the alternative costs)

A tree-walking interpreter is much less code. It is also impossible to suspend safely: the interpreter's
state lives on the Java call stack, so "stop this script now and continue it in 50 ms" cannot be expressed
without a Java thread per computer — and a server with 60 computers would then have 60 threads fighting over
one tick.

So the pipeline is:

```
source ──Lexer──► tokens ──Parser──► AST ──Emitter──► bytecode ──Vm──► results
```

and the machine (`Vm`) has everything an interpreter would normally keep on the Java stack *in objects*:

- a `Frame` per Lua call: prototype, slot array, upvalues, varargs, value stack, `pc`;
- `Vm.run(budget)` executes at most `budget` instructions, then returns `Status.PAUSED` with the whole call
  stack intact;
- resuming is calling `run` again. That is the entire trick, and it is also how coroutines work: a
  coroutine is a second `Vm` (sharing globals) that is simply not being run right now.

Two design choices inside the machine are worth knowing because they change what scripts can do:

**Locals are boxed.** Every slot is a `Cell`, and the emitter *never reuses a slot number*. A closure
therefore captures the cell, not a copy, which is what makes this work:

```lua
local function counter()
  local n = 0
  return function() n = n + 1 return n end
end
local a, b = counter(), counter()
print(a(), a(), a(), b())   --> 1  2  3  1
```

Reusing slots is the classic cause of "my two closures share a variable" bugs; paying a few `Cell`s per
frame is far cheaper than the class of bug.

**Numbers are all `Double`.** No integer subtype. `1/2` is `0.5`, `%` is floored like Lua's, and
`tostring(3.0)` prints `3` (see `Vm.luaToString`). `for i = 1, 10` is exact because the values are small
integers, which is the case that matters.

---

## 3. Inside the machine

### Instruction budget

A computer runs on `Machine.budgetPerTick()` instructions per tick (20,000 by default; the CPU tiers from
round 5 raise it). The block entity calls `vm.run(budget)` once a tick. Consequences you can rely on:

- `while true do end` **cannot** hang the server. It pauses, keeps its state, and gets another slice next
  tick. `tools/harness/LuaHarness.java` asserts exactly this.
- There is **no** `Thread.sleep` anywhere in the mod (the sweep greps for it).
- A script that does real work has to yield — which is why `event.pull` exists and why programs are written
  as state machines rather than loops.

### Waiting is suspending

A native that cannot answer yet throws `Vm.Yield`:

```java
throw new Vm.Yield("key_down", 100L);   // waiting for a named event, 5 s timeout
```

The machine catches it at the block-entity level, remembers what the script wants, and calls
`vm.resumeWith("key_down", "enter")` when the event arrives (or `vm.resumeWith()` on timeout, which makes
the `event.pull` call return nil). The script continues *on the instruction after the call*.

This is the single most important property of the whole design: `event.pull` is not a busy-wait and not a
blocking read. Nothing holds a thread while a computer waits for a button press.

### Errors

`LuaError` carries `source:line: message`. `pcall` catches it — and, crucially, **unwinds the Lua frames**
(`Vm.unwindTo`), because a Java `throw` unwinds the Java stack without telling the VM. Without that, a
rescued script would continue executing inside the half-finished function it was rescued from; the harness
has a regression test for the `pcall(function() error('boom') end)` case.

A failed coroutine is dead (`LuaCoroutine` resets its machine and rethrows), which is what `coroutine.status`
then reports.

---

## 4. The language

### Supported

| Area | Details |
|---|---|
| Values | `nil`, booleans, numbers (`Double`), strings, tables, functions, threads |
| Statements | `local`, assignment (multiple), `if/elseif/else`, `while`, `repeat/until`, `do`, numeric `for`, generic `for`, `break`, `return`, `function`, `local function` |
| Expressions | arithmetic (`+ - * / % ^`), concatenation `..`, comparisons, `and`/`or`/`not`, unary `-`/`#`, indexing `.x` / `[k]`, calls, method calls `o:m()`, table constructors |
| Functions | varargs `...`, closures, multiple returns, multiple assignment from a call, recursion, methods with `self` |
| Sugar | `f"str"` and `f{...}` call sugar, `a.b.c = v`, `local const`/`<const>` attributes parsed and ignored |

`{...}` and `f(g())` expand properly: the last value in a list, a table constructor or an argument list
gives all of its results, including `local a, b = f()`.

### Not supported (by design, not by accident)

| Missing | Why | Workaround |
|---|---|---|
| Metatables / `__index` | one table type, no prototype chains; keeps indexing a single hash lookup | we special-case the one thing programs need: `("x"):upper()` works |
| Lua patterns (`%d`, `.-`) | a full pattern engine is a project; a half one is a bug farm | `string.find`/`gsub` do plain-text search/replace; use `string.sub` |
| `goto` / labels | needs a second pass over the block | restructure with `break` or a helper function |
| `//` integer division, bitwise `& | ~ << >>` | no integer subtype | `math.floor(a / b)` |
| `load`, `loadstring`, `dofile`, `require` of Java | script-injection surface; a datapack could otherwise run arbitrary code | programs are files read with `fs` and run by the shell |
| `io`, `os.execute`, anything that touches the host | a computer must not be able to reach the JVM | `os.clock`/`os.time`-style values come from `computer.uptime()` |

`string.format` supports `%s %d %i %f %g %e %x %X %c %%` **with** width/precision (`%08.2f`), because real
programs format with them.

---

## 5. The machine API

Everything below exists after `ComputerApi.install(vm)`. Types follow Lua: numbers are Doubles, tables are
tables. Functions return what the OC versions return, including the `nil, "reason"` convention.

### `term` — the terminal (what most programs use)

```lua
term.write(...)        -- print at the cursor, wrap at the right margin, scroll at the bottom
term.clear()
term.clearLine()       -- blank from the cursor to the end of the row
term.setCursor(x, y)   -- 1-based
term.getCursor()       -- x, y
term.getSize()         -- columns, rows
term.scroll(n)         -- positive scrolls up
term.setForeground(c)  -- 0xRRGGBB or palette index
term.setBackground(c)
term.getForeground()   -- "0xRRGGBB"
term.getBackground()
```

**Wrapping is deferred.** Writing exactly `columns` characters leaves the cursor one past the right margin
and does *not* scroll until the next character arrives. Eager wrapping would scroll a full line early on the
bottom row, which every full-screen program would show as a visible jump. `term.getCursor()` reports the
clamped column so programs never see a cursor outside the screen.

### `gpu` — exact drawing

```lua
gpu.getResolution()          -- columns, rows (error if no screen bound)
gpu.set(x, y, text[, vertical])   -- no wrapping, clipped at the edges
gpu.get(x, y)                -- one character
gpu.fill(x, y, w, h[, ch])
gpu.copy(x, y, w, h, tx, ty)
gpu.setForeground(c) / setBackground(c) / getForeground() / getBackground()
gpu.getScreen()              -- address or nil
gpu.bind(address)            -- true, or nil + reason
```

A screen is a component (`component.list("screen")`); the GPU card's tier caps the buffer it may drive
(`ScreenComponent.BASIC` 80×25, `ADVANCED` 100×30, `VECTOR` 120×40 — the same tiers as round 5).

### `component`

```lua
component.list([typePrefix])      -- table: address -> type
component.get(addressOrPrefix)    -- proxy table; proxy.method(...) calls the component
component.type(address)
component.methods(address)
component.invoke(address, method, ...)
```

Addresses may be shortened to a unique prefix (`component.get("scr")`), which is how anything hand-typed
refers to a screen.

### `event`

```lua
event.push(name, ...)        -- false (queued for the next pull)
event.pull([name][, timeout])-- name, ...args; nil on timeout; SUSPENDS the script when the queue is empty
event.listen(name, fn)       -- fn(name, ...) runs when the event arrives
event.ignore(name)
event.timer(seconds)         -- queues "timer" after that many ticks
```

Matching is **exact** on the event name (`event.pull("key_down")`), as in OpenComputers. Timers are a due
tick in a list, not a Java thread: a thousand timers cost a thousand comparisons.

### `computer`

`address()`, `uptime()` (seconds), `realTime()` (ms), `freeMemory()`, `totalMemory()`, `pushSignal(name, ...)`,
`pullSignal([timeout])`, `shutdown()`, `reboot()`, `isRunning()`.

### `thread`

```lua
local co = thread.create(function() ... end)
thread.waitForAll(a, b)      -- suspends until every thread is dead
thread.status(co)            -- "new" | "suspended" | "dead"
```

`waitForAll` resumes each thread for one slice per attempt and, if any is still alive, yields — so
`for i = 1, 1000 do end` inside a thread cannot stall the tick either.

### `fs` — the disk

```lua
fs.open(path, mode)   -- handle, or nil, "reason"; mode "r"/"w"/"a"
fs.read(handle[, n])  -- string; nil at end of file
fs.write(handle, s)
fs.close(handle)
fs.seek(handle[, whence][, offset])
fs.exists(path) / fs.isDirectory(path) / fs.size(path) / fs.lastModified(path)
fs.makeDirectory(path) / fs.remove(path) / fs.rename(from, to) / fs.copy(from, to)
fs.getLabel() / fs.setLabel(s) / fs.space()   -- capacity, used
for name in fs.list("/") do ... end           -- iterator, as programs expect
```

Paths are normalised in exactly one place (`FileSystem.Paths`): absolute, no `.`/`..` escape, trailing
slashes collapsed. `fs.exists("/../secret")` is `false` and `"/etc/../etc/motd"` is `/etc/motd` — asserted in
the harness, because a filesystem escape in a modded Minecraft server is a real exploit, not a curiosity.

Writes flush on every `fs.write`, so breaking a computer mid-script does not lose the last line. Handles are
capped at 16 open files.

### `colors`

`colors.white` … `colors.black` (0–15), plus the usual aliases. Colours are accepted three ways:

```lua
term.setForeground(colors.red)   -- palette index
term.setForeground(0xFF0000)     -- packed RGB
term.setForeground("0xFF0000")   -- the string form real programs use
```

Unparseable colours fall back to the previous colour rather than raising: a typo should not kill a running
program.

---

## 6. Divergences from OpenComputers (complete list)

| Thing | OpenComputers | Here |
|---|---|---|
| `string.find`/`gsub` with patterns | full Lua patterns | literal text only |
| Metatables | yes | no; strings answer the `string` library |
| `load`, `require` of Java, `io`, `os.execute` | partially present | absent (security) |
| `event.pull` name matching | exact | exact (same) |
| Timers | Java scheduler | due-tick list, checked once per tick |
| `gpu.bitblt`, viewports, multi-screen buffers | yes | not yet (`gpu.copy` covers the common case) |
| `term.read` (line input) | yes | programs read `key_down`/`touch` with `event.pull`; `term.read` lands with the keyboard component |
| `component.doc`/`fields` | full generated docs | `nil, "no documentation"` |
| Precision of `fs.size` on directories | bytes | sum of children |
| `computer.realTime` | ms since world start | uptime × 50 (no wall clock on a script thread) |

If you are porting a program and it uses a pattern match or `term.read`, that is where it will break — and
those two are the roadmap items with the highest value, in that order.

---

## 7. What is next, in order

1. **The block entity** (`computer/LuaComputerBlockEntity`): owns the `Vm`, the `ComputerApi`, the disk
   `SavedData`, and a changed-frame packet for the screen. It calls `api.tickTimers()`, `vm.run(budget)` and
   handles `Vm.Yield`/`PowerRequest`.
2. **Cards as components**: network card (`modem_message` events), redstone card (the six outputs from
   round 5), storage card (AE2/containers), plus the round-5 radar and cannon bridges so a *program* can do
   what `/term radar` does today.
3. **Shell and MineOS payload.** A tiny built-in shell (so a fresh computer is usable) and then MineOS is
   installed by the player the way it is meant to be:
   ```lua
   wget -f https://raw.githubusercontent.com/IgorTimofeev/MineOS/master/Installer/BIOS.lua /tmp/bios.lua
   flash -q /tmp/bios.lua && reboot
   ```
   The `wget`/`flash` commands and the EEPROM are part of *our* shell; MineOS itself is never bundled as a
   jar resource. **OpenOS is never bundled or installed.** See `THIRD_PARTY.md`.
4. **Robots, drones, tablets, nanomachines**, then subgrid support for every computer block (the round-1
   cable/subgrid work already gives us the hook).

---

## 8. Licences

- `src/main/java/com/shadowclangaming/lua/**` — **original code**, written for this mod. Not LuaJ, not Lua,
  not OpenComputers. No third-party licence applies.
- `src/main/java/com/shadowclangaming/computer/api/**` — **original code** implementing an API *surface* that is
  compatible with OpenComputers' documented API (MIT). API compatibility is not copying; the method names are
  the interface programs call, and those have to match or nothing runs.
- OpenComputers (MIT) and MineOS (MIT) provenance, plus what we will and will not bundle, is tracked in
  `THIRD_PARTY.md`.
