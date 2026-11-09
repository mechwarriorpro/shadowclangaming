# Guns: a loot weapon system, built from scratch

A remake of the old "guns drop with random parts and a rarity" mod, written from zero: our own makers,
our own parts, our own art, our own numbers. Nothing is copied - the *idea* of procedurally generated
loot weapons is a genre convention, and the implementation here is ours.

---

## 1. The loop

```
iron nuggets + redstone  ->  Gun Scrap
iron + redstone          ->  Ammo Cells
Gun Scrap + Fabricator   ->  one rolled gun (announced in chat)
```

Keep feeding the fabricator scrap until something good prints. That is the entire economy of the
system: scrap is cheap, and the good roll is rare.

## 2. What a gun is

Six slots, one part each, plus a maker, a rarity and an element:

| Slot | What it changes | Parts |
|---|---|---|
| Barrel | damage up, accuracy up, rate down | smooth, rifled, sawn, long, split |
| Body | rate of fire | standard, light, heavy, automatic, overcharged |
| Grip | handling / accuracy | plain, wrapped, angled, vented |
| Stock | recoil control | none, folding, solid, counterweight |
| Sight | accuracy | iron, dot, scope, thermal |
| Magazine | capacity | short, standard, extended, drum |

**26 parts × 4 makers × 5 rarities × 5 elements × 5 silhouettes** before the six slot combinations are
counted. The generator is in `gun/GunForge.java` and it is pure logic - it takes an RNG and returns a
`GunStats` record, no Minecraft objects involved, which is why it can be unit tested outside the game.

### The rule that makes it fair

Every number on the tooltip is the product of things you can read:

```
damage   = 9.0 x maker.damageBias  x product(part.damageMult)   x rarity.statMultiplier
fireRate = 2.2 x maker.rateBias    x product(part.rateMult)      clamped 0.4 .. 9.0 /s
accuracy = 0.80 x maker.accuracyBias x product(part.accuracyMult) clamped 0.25 .. 0.995
magazine = 18  x maker.magazineBias x product(part.magazineMult) clamped 3 .. 300
```

There is no hidden bonus. A gun that hits hard shows you the barrel that did it.

## 3. Rarity

| Rarity | Stat multiplier | Extra part re-rolls | Element chance |
|---|---|---|---|
| Scavenged | ×1.00 | 0 | 0 % |
| Fielded | ×1.08 | 1 | 25 % |
| Tuned | ×1.18 | 2 | 50 % |
| Prototype | ×1.32 | 3 | 75 % |
| Relic | ×1.50 | 4 | 100 % |

"Extra re-rolls" is the important column: a Relic had **four extra tries at every one of the six
slots**, which is why it feels hand-built.

Roll frequencies (measured over 200,000 rolls): 35 % scavenged, 33 % fielded, 20 % tuned, 10 %
prototype, **2 % relic**.

## 4. Makers

| Maker | Damage | Rate | Accuracy | Magazine |
|---|---|---|---|---|
| Vulcan Ordnance | ×1.35 | ×0.80 | ×0.90 | ×0.85 |
| Kestrel Arms | ×0.75 | ×1.45 | ×0.82 | ×1.15 |
| Halcyon Precision | ×1.05 | ×1.05 | ×1.15 | ×1.00 |
| Drumlin Works | ×0.95 | ×1.15 | ×0.95 | ×1.55 |

## 5. Elements

| Element | On hit |
|---|---|
| Kinetic | plain damage + a shove |
| Ember | sets the target alight (100 ticks) |
| Arc | **+50 % damage to anything already burning**, sparks |
| Acid | poison |
| Void | wither, and heals the shooter for 20 % of the hit |

Ember + Arc is the combo the system is built around, and it takes two guns or one very lucky roll.

## 6. Firing

- **Hitscan**, not a projectile entity. At six shots a second, a bullet entity per shot is hundreds of
  ticking entities in a firefight; a ray costs nothing and lands instantly.
- Spread is a cone whose width is `(1 - accuracy) × 0.14 rad`, so 99.5 % accuracy is a laser and 25 %
  is a shotgun.
- 48 block range, entities checked before blocks.
- Cooldown is `20 / fireRate` ticks, so rate of fire is literal.
- Ammo lives in the `GUN_AMMO` component: it survives chests and death. Reloading consumes one **Ammo
  Cell** from anywhere in the inventory and costs `reloadTicks`, which scales with magazine size.

## 7. Art

25 item textures: **5 silhouettes × 5 element palettes**. The right one is picked by vanilla's
`custom_model_data` (`style × 5 + element + 1`) against 25 overrides in `models/item/gun.json` - so
there is no custom renderer, no model loader, and the client needs nothing but the JSON. Relics also
carry `isFoil`, so a legendary glows in a chest.

## 8. Tests

`GunForge` and the five enums were compiled on JDK 21 against minimal stubs of the three Minecraft
types they touch, and the generator was run 200,000 times. Assertions that pass:

- every gun has exactly 6 parts, one per slot
- damage, rate, accuracy, magazine, reload all inside their clamps
- `modelVariant()` always lands in 1..25
- names are always three words
- same seed ⇒ identical gun (determinism)
- mean relic damage (20.2) > 1.3 × mean scavenged damage (12.3)

## 9. Files

| File | Role |
|---|---|
| `gun/GunPart.java` | 26 parts, six slots, pure multipliers |
| `gun/GunRarity.java` | 5 rarities, roll odds, re-roll counts |
| `gun/GunMaker.java` | 4 makers and their biases |
| `gun/GunElement.java` | 5 elements and their colours |
| `gun/GunStats.java` | The immutable gun value + codecs |
| `gun/GunForge.java` | The generator and the naming |
| `gun/GunShooting.java` | Ray, spread, damage, element, tracer |
| `item/GunItem.java` | Firing, ammo, reload, tooltip, model variant |
| `block/FabricatorBlock.java` | Scrap in, roll out |
