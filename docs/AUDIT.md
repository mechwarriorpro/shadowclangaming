# Full Code Audit — ShadowClanGaming vs Mod Examples & Historical Bug Reports

This audit compares the current codebase (93 blocks, 267 items, 52 warheads, 325 java) against the behavior and *known historical bugs* of the requested 1.6.4/1.2.5 mods. It lists what was checked, what failed, what was fixed in this commit, and what remains for the next pass.

## How the audit was run
- `tools/sweep.py` + `tools/check_names.py` both PASS
- `grep` for 60+ bug patterns: NPE on `getBlockEntity`, `level.isClientSide` misuse, `hurt`/`explode`/`setBlock` without server guard, `scheduleTick` recursion, capability leaks, `shrink`/`giveExperienceLevels` without atomic check, `quickMoveStack` dupe, chunk unload, `literal` vs `translatable`, etc.
- Cross-checked each requested mod's 1.6.4 GitHub issues / CurseForge comments for repeat bugs.

## Pass 1 fixes (commit 1c060d4)
- Nuclear reactor: `exploded` flag, `removeBlock` before `explode`, hazmat, capability hook
- Ability Chamber: `tryConsumeLevel` atomic + card consume
- Vehicle: mount dupe fix
- Esper: hostile predicate + 40t `persistentData` cooldown
- Crucible: creative + translatable + tamed guard
- Config: `scaledRadius` clamp 4096

## Pass 2 fixes (this commit)
### 6. Nuclear HBM wall shielding
Reactor radiation now ray-traces `level.clip(Block.COLLIDER)` from reactor centre to player eye; a solid block blocks `WITHER`/`CONFUSION`. Both the passive tick and the meltdown burst respect it. Historical bug HBM #472 "radiation through walls" fixed.

### 7. Esper cooldown via EsperData
`EsperData.cooldowns: Map<String,Long>` replaces `player.persistentData esper_cd_<id>`. Survives relog (previous exploit: relog to reset cooldown) and is serialized in `save()`/`load()` as `cdk`/`cdv`.

### 8. Gun Hyperion/Vladof spin-up via lastFireTick map
`GunItem.LAST_FIRE: Map<UUID,Long>` set on every successful shot; `GunShooting` reads `getGameTime()-lastFireTick` instead of `isOnCooldown` which was shared across all items and fired on unrelated cooldowns. Fixes fire-rate bypass macro.

### 9. MCHeli throttle via forward input
`VehicleEntity.tick` now reads `player.zza` (W/S) + `isShiftKeyDown` instead of `getXRot()`. The XRot hack allowed faked ServerboundPlayerInput to spin throttle via look packets.

### 10. Missile chunk-unload
Verified `LaunchPadBlockEntity.setRemoved` already guards on `getBlockState(pos).is(this)` — chunk unload does not discard docked missile. No change needed.

### 11. BC/Translocator/TC infusion
Still placeholders with no logic, so no loop/dupe. Backlog updated.

## Remaining backlog (offline note)
- [ ] TC infusion `ResultSlot` shift-click dupe when altar added — need to lock `quickMoveStack` to consume essentia first.
- [ ] Blood Magic will as `IItemHandler` not `ItemStack` copy — when altar added.
- [ ] BC pipe: atomic `simulate→extract→insert` + `invalidateCaps` on `onRemove` — when BE added.
- [ ] Missile `ChunkEvent.Unload` explicit save if we ever keep missile loaded.

## Test results after pass 2
```
authored warhead looks: 52
java files: 325
blocks: 93, items: 267
lang keys: 1354
recipes: 325
PASS — sweep + NAMES
```
