#!/usr/bin/env python3
"""Generate the block / grenade / minecart forms of every warhead.

One source of truth: the warhead roster (parsed straight out of WarheadType.java, so a warhead added in
Java gets its three packaged forms by re-running this script). Everything here is generated, nothing is
hand-maintained:

    explosive_<warhead>     block, blockstate, block model, item model, block texture, loot table
    grenade_<warhead>       item, item model, item texture
    minecart_<warhead>      item, item model, item texture
    + lang keys, + one recipe per form

Run from the mod root:  python3 tools/gen_explosive_packaging.py
"""
import json
import math
import os
import re
import sys
from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
JAVA = os.path.join(ROOT, "src/main/java/com/shadowclangaming")
RES = os.path.join(ROOT, "src/main/resources")
ASSETS = os.path.join(RES, "assets/shadowclangaming")
DATA = os.path.join(RES, "data/shadowclangaming")

SKIP = set()  # every warhead gets all three forms, dud included (its charge simply fizzles)


def warheads():
    """(id, base power) for every warhead, in roster order."""
    text = open(os.path.join(JAVA, "warhead/WarheadType.java"), encoding="utf-8").read()
    found = re.findall(r'^\s{4}[A-Z_]+\("([a-z_0-9]+)",\s*([\d.]+)F\)', text, re.M)
    order = [entry for entry in found if entry[0] not in SKIP]
    if not order:
        sys.exit("no warheads parsed - did the enum formatting change?")
    return [(wid, float(power)) for wid, power in order]


# --------------------------------------------------------------------------- colour

def colour(index, power):
    """A stable colour per warhead: hue spread over the roster, brightness from the power.

    Generated art, not copied art: every texture in this mod is built from rectangles in this file, which
    is what makes the licensing question answerable.
    """
    hue = (index * 0.61803398875) % 1.0           # golden-ratio walk: neighbouring ids never look alike
    light = 0.35 + 0.30 * min(1.0, math.log1p(power) / math.log1p(200.0))
    return hsv_to_rgb(hue, 0.65, light)


def hsv_to_rgb(h, s, v):
    i = int(h * 6.0)
    f = h * 6.0 - i
    p = v * (1.0 - s)
    q = v * (1.0 - s * f)
    t = v * (1.0 - s * (1.0 - f))
    r, g, b = [(v, t, p), (q, v, p), (p, v, t), (p, q, v), (t, p, v), (v, p, q)][i % 6]
    return int(r * 255), int(g * 255), int(b * 255)


def shade(rgb, factor):
    return tuple(max(0, min(255, int(c * factor))) for c in rgb)


# --------------------------------------------------------------------------- textures

def block_texture(path, rgb):
    """A hazard block: dark casing, bright panel, a black stripe across the middle."""
    image = Image.new("RGBA", (16, 16), shade(rgb, 0.55) + (255,))
    draw = ImageDraw.Draw(image)
    draw.rectangle([1, 1, 14, 14], fill=rgb + (255,), outline=shade(rgb, 0.35) + (255,))
    for x in range(1, 15):
        if (x + 1) % 4 < 2:
            draw.point((x, 7), fill=shade(rgb, 0.15) + (255,))
            draw.point((x, 8), fill=shade(rgb, 0.15) + (255,))
    draw.rectangle([6, 3, 9, 4], fill=shade(rgb, 1.35) + (255,))     # fuse cap
    draw.rectangle([7, 2, 8, 2], fill=(40, 40, 40, 255))
    image.save(path)


def grenade_texture(path, rgb):
    """A can with a lever: rounded body, a pin, and a lighter top."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.ellipse([3, 4, 12, 15], fill=rgb + (255,), outline=shade(rgb, 0.45) + (255,))
    draw.rectangle([5, 1, 10, 4], fill=shade(rgb, 1.25) + (255,))
    draw.rectangle([10, 1, 13, 2], fill=(150, 150, 150, 255))        # lever
    draw.point((6, 2), fill=(60, 60, 60, 255))                       # pin
    draw.rectangle([5, 8, 6, 9], fill=shade(rgb, 1.4) + (255,))      # highlight
    image.save(path)


def minecart_texture(path, rgb):
    """A cart silhouette with a warhead block sitting in it."""
    image = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([4, 3, 11, 8], fill=rgb + (255,), outline=shade(rgb, 0.4) + (255,))
    draw.rectangle([2, 8, 13, 11], fill=(96, 96, 96, 255), outline=(60, 60, 60, 255))
    draw.line([2, 8, 13, 8], fill=(150, 150, 150, 255))
    draw.ellipse([3, 11, 6, 14], fill=(50, 50, 50, 255))
    draw.ellipse([9, 11, 12, 14], fill=(50, 50, 50, 255))
    draw.point((5, 5), fill=shade(rgb, 1.5) + (255,))
    image.save(path)


# --------------------------------------------------------------------------- json helpers

def write_json(path, data):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2)
        handle.write("\n")


def block_assets(wid):
    texture = "shadowclangaming:block/explosive_" + wid
    state = {"variants": {
        "primed=false": {"model": "shadowclangaming:block/explosive_" + wid},
        "primed=true": {"model": "shadowclangaming:block/explosive_" + wid},
    }}
    write_json(os.path.join(ASSETS, "blockstates/explosive_%s.json" % wid), state)
    write_json(os.path.join(ASSETS, "models/block/explosive_%s.json" % wid),
               {"parent": "minecraft:block/cube_all", "textures": {"all": texture}})
    write_json(os.path.join(ASSETS, "models/item/explosive_%s.json" % wid),
               {"parent": "shadowclangaming:block/explosive_" + wid})
    write_json(os.path.join(DATA, "loot_table/blocks/explosive_%s.json" % wid), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "shadowclangaming:explosive_" + wid}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
    })


def item_assets(wid, form, texture_name):
    write_json(os.path.join(ASSETS, "models/item/%s_%s.json" % (form, wid)),
               {"parent": "minecraft:item/generated",
                "textures": {"layer0": "shadowclangaming:item/" + texture_name}})


# --------------------------------------------------------------------------- recipes

# Two warheads have no missile recipe to borrow a signature from: HE is the plain charge, and DUD is the
# empty one. They get hand-picked thematically obvious ingredients instead.
FALLBACK = {"he": "minecraft:tnt", "dud": "minecraft:string"}


def signature_ingredient(wid, cache):
    """The ingredient that distinguishes this warhead's missile, reused for its packaged forms.

    Taken from the missile recipe so the grenade costs roughly what the warhead costs: the same signature
    material, without the airframe. Falls back to the missile itself when a recipe is not shaped enough
    to have an obvious signature.
    """
    if wid in FALLBACK:
        cache[wid] = FALLBACK[wid]
        return cache[wid]
    if not os.path.isfile(os.path.join(DATA, "recipe/%s_missile.json" % wid)):
        # Warheads whose missile has no recipe of its own (or none yet) borrow the missile item itself.
        cache[wid] = "shadowclangaming:%s_missile" % wid
        return cache[wid]
    path = os.path.join(DATA, "recipe/%s_missile.json" % wid)
    frame_words = ("missile", "srb", "mrbm", "icbm", "rocket", "module")
    if os.path.isfile(path):
        data = json.load(open(path, encoding="utf-8"))
        key = data.get("key") or {}
        candidates = []
        for symbol, spec in key.items():
            item = spec.get("item") or spec.get("tag") or ""
            short = item.split(":")[-1]
            if short == "gunpowder" or any(word in short for word in frame_words):
                continue
            candidates.append((symbol, item))
        if candidates:
            candidates.sort()
            cache[wid] = candidates[0][1]
            return cache[wid]
        result = (data.get("result") or {}).get("id")
        if result:
            cache[wid] = result
            return result
    cache[wid] = "shadowclangaming:%s_missile" % wid
    return cache[wid]


def recipes(roster):
    cache = {}
    used = {}
    for wid, _power in roster:
        ingredient = signature_ingredient(wid, cache)
        # Two warheads sharing a signature material would make two recipes with identical inputs, and
        # Minecraft would only ever match the first. Fall back to the missile itself, which is unique.
        if ingredient in used:
            ingredient = "shadowclangaming:%s_missile" % wid
        used[ingredient] = wid

        write_json(os.path.join(DATA, "recipe/grenade_%s.json" % wid), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [
                {"item": "minecraft:gunpowder"},
                {"item": "minecraft:iron_ingot"},
                {"item": ingredient},
            ],
            "result": {"id": "shadowclangaming:grenade_" + wid, "count": 2},
        })
        # Four grenades make the block: the block is the heavy version of the same charge, so the trade
        # is "four throwables become one wall-breaker".
        write_json(os.path.join(DATA, "recipe/explosive_%s.json" % wid), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [{"item": "shadowclangaming:grenade_" + wid} for _ in range(4)],
            "result": {"id": "shadowclangaming:explosive_" + wid, "count": 1},
        })
        # And a minecart is a cart plus a charge.
        write_json(os.path.join(DATA, "recipe/minecart_%s.json" % wid), {
            "type": "minecraft:crafting_shapeless",
            "category": "misc",
            "ingredients": [
                {"item": "minecraft:minecart"},
                {"item": "shadowclangaming:explosive_" + wid},
            ],
            "result": {"id": "shadowclangaming:minecart_" + wid, "count": 1},
        })
    return cache


# --------------------------------------------------------------------------- lang

TITLES = {
    "he": "HE", "emp": "EMP", "decoy": "Decoy", "incendiary": "Incendiary", "cluster": "Cluster",
    "fission": "Fission", "fusion": "Fusion", "antimatter": "Antimatter",
    "numismatic": "Numismatic", "bulwark": "Bulwark", "contagion": "Contagion", "spell": "Spell",
    "cargo": "Cargo", "minefield": "Minefield", "tracking": "Tracking", "constructor": "Constructor",
    "spatial": "Spatial", "clucker": "Clucker", "violator": "Violator", "beef": "Beef Supreme",
    "kinetic": "Kinetic Cataclysm", "supercritical": "Supercritical Phase", "matter_condenser": "Matter Condenser",
    "stellar_swarm": "Stellar Swarm", "transmutation": "Transmutation", "colossal_star": "Colossal Star",
    "steam_rupture": "Steam Rupture", "voltmeter": "Voltmeter", "voodoo": "Voodoo Torment",
    "thermal_entropy": "Thermal Entropy", "mana_overload": "Mana Overload", "draconic_reactor": "Draconic Reactor",
    "sanguine": "Sanguine Sacrifice",
}


def title(wid):
    return TITLES.get(wid, " ".join(part.capitalize() for part in wid.split("_")))


def lang(roster):
    path = os.path.join(ASSETS, "lang/en_us.json")
    data = json.load(open(path, encoding="utf-8"))
    added = 0
    for wid, _power in roster:
        name = title(wid)
        entries = {
            "block.shadowclangaming.explosive_" + wid: "%s Charge" % name,
            "item.shadowclangaming.grenade_" + wid: "%s Grenade" % name,
            "item.shadowclangaming.minecart_" + wid: "%s Minecart" % name,
        }
        for key, value in entries.items():
            if key not in data:
                data[key] = value
                added += 1
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(data, handle, indent=2, ensure_ascii=False)
        handle.write("\n")
    return added, len(data)


def main():
    roster = warheads()
    for directory in ("textures/item", "textures/block", "models/item", "models/block", "blockstates"):
        os.makedirs(os.path.join(ASSETS, directory), exist_ok=True)
    os.makedirs(os.path.join(DATA, "loot_table/blocks"), exist_ok=True)

    for index, (wid, power) in enumerate(roster):
        rgb = colour(index, power)
        block_texture(os.path.join(ASSETS, "textures/block/explosive_%s.png" % wid), rgb)
        grenade_texture(os.path.join(ASSETS, "textures/item/grenade_%s.png" % wid), rgb)
        minecart_texture(os.path.join(ASSETS, "textures/item/minecart_%s.png" % wid), rgb)
        block_assets(wid)
        item_assets(wid, "grenade", "grenade_" + wid)
        item_assets(wid, "minecart", "minecart_" + wid)

    cache = recipes(roster)
    added, total = lang(roster)
    print("warheads: %d" % len(roster))
    print("files: %d blockstates, %d models, %d textures, %d recipes"
          % (len(roster), len(roster) * 3, len(roster) * 3, len(roster) * 3))
    print("lang: +%d keys (%d total)" % (added, total))
    missing = [wid for wid, _ in roster if cache.get(wid) is None]
    if missing:
        print("no signature ingredient for: %s" % ", ".join(missing))
    return 0


if __name__ == "__main__":
    sys.exit(main())
