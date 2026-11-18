#!/usr/bin/env python3
"""Generate the blockstates, models, item models, loot tables and recipes for the hardware round.

    python3 tools/gen_hardware_json.py

Everything here is plain data, and it is generated so that names stay in lockstep with the Java registry.
If you rename a block, change it in one place in this file and re-run rather than hunting through JSON.
"""

import json
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
ASSETS = os.path.join(ROOT, "src/main/resources/assets/shadowclangaming")
DATA = os.path.join(ROOT, "src/main/resources/data/shadowclangaming")

BLOCKS = ["network_rack", "orbital_node", "radar", "cannon_director"]

# id -> texture name (item models only; block items use the block model)
PLAIN_ITEMS = {
    "cpu_basic": "cpu_basic",
    "cpu_advanced": "cpu_advanced",
    "cpu_quantum": "cpu_quantum",
    "cpu_photonic": "cpu_photonic",
    "gpu_basic": "gpu_basic",
    "gpu_vector": "gpu_vector",
    "gpu_tensor": "gpu_tensor",
    "gpu_photonic": "gpu_photonic",
    "radar_card": "radar_card",
    "cannon_card": "cannon_card",
}


def write(path, payload):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as handle:
        json.dump(payload, handle, indent=2)
        handle.write("\n")


def block_assets(name):
    write(os.path.join(ASSETS, "blockstates", name + ".json"),
          {"variants": {"": {"model": "shadowclangaming:block/" + name}}})
    write(os.path.join(ASSETS, "models/block", name + ".json"), {
        "parent": "minecraft:block/cube_bottom_top",
        "textures": {
            "top": "shadowclangaming:block/%s_top" % name,
            "bottom": "shadowclangaming:block/%s_bottom" % name,
            "side": "shadowclangaming:block/%s_side" % name,
        },
    })
    write(os.path.join(ASSETS, "models/item", name + ".json"),
          {"parent": "shadowclangaming:block/" + name})
    write(os.path.join(DATA, "loot_table/blocks", name + ".json"), {
        "type": "minecraft:block",
        "pools": [{
            "rolls": 1,
            "bonus_rolls": 0,
            "entries": [{"type": "minecraft:item", "name": "shadowclangaming:" + name}],
            "conditions": [{"condition": "minecraft:survives_explosion"}],
        }],
        "random_sequence": "shadowclangaming:blocks/" + name,
    })


def item_model(item_id, texture):
    write(os.path.join(ASSETS, "models/item", item_id + ".json"), {
        "parent": "minecraft:item/generated",
        "textures": {"layer0": "shadowclangaming:item/" + texture},
    })


def shaped(name, pattern, key, count=1):
    write(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shaped",
        "category": "misc",
        "pattern": pattern,
        "key": {symbol: {"item": item} for symbol, item in key.items()},
        "result": {"id": "shadowclangaming:" + name, "count": count},
    })


def shapeless(name, ingredients, count=1):
    write(os.path.join(DATA, "recipe", name + ".json"), {
        "type": "minecraft:crafting_shapeless",
        "category": "misc",
        "ingredients": [{"item": item} for item in ingredients],
        "result": {"id": "shadowclangaming:" + name, "count": count},
    })


RECIPES = {
    "cpu_basic": (["CGC", "RIR", "CGC"],
                  {"C": "minecraft:copper_ingot", "G": "minecraft:gold_nugget",
                   "R": "minecraft:redstone", "I": "minecraft:iron_ingot"}),
    "gpu_basic": (["CGC", "RGR", "CGC"],
                  {"C": "minecraft:copper_ingot", "G": "minecraft:glass_pane",
                   "R": "minecraft:redstone"}),
}

UPGRADES = {
    # name        previous module          extra ingredients
    "cpu_advanced": ("shadowclangaming:cpu_basic",
                     ["minecraft:gold_ingot", "minecraft:gold_ingot", "minecraft:diamond",
                      "minecraft:redstone_block"]),
    "cpu_quantum": ("shadowclangaming:cpu_advanced",
                    ["minecraft:amethyst_shard", "minecraft:amethyst_shard",
                     "minecraft:ender_pearl", "minecraft:diamond_block"]),
    "cpu_photonic": ("shadowclangaming:cpu_quantum",
                     ["minecraft:netherite_ingot", "minecraft:amethyst_shard",
                      "minecraft:glowstone", "minecraft:diamond_block"]),
    "gpu_vector": ("shadowclangaming:gpu_basic",
                   ["minecraft:amethyst_shard", "minecraft:amethyst_shard", "minecraft:diamond"]),
    "gpu_tensor": ("shadowclangaming:gpu_vector",
                   ["minecraft:ender_pearl", "minecraft:diamond", "minecraft:glowstone"]),
    "gpu_photonic": ("shadowclangaming:gpu_tensor",
                     ["minecraft:nether_star", "minecraft:amethyst_shard", "minecraft:glowstone"]),
}


def main():
    for name in BLOCKS:
        block_assets(name)
    for item_id, texture in PLAIN_ITEMS.items():
        item_model(item_id, texture)

    # --- hardware: base modules
    shaped("cpu_basic", *RECIPES["cpu_basic"])
    shaped("gpu_basic", *RECIPES["gpu_basic"])
    # --- hardware: upgrades are shapeless so the pattern never implies a fake grid
    for name, (previous, extras) in UPGRADES.items():
        shapeless(name, [previous] + extras)
    # --- cards
    shaped("radar_card", ["PAP", "ARA", "PAP"],
           {"P": "minecraft:paper", "A": "minecraft:amethyst_shard", "R": "minecraft:redstone"})
    shaped("cannon_card", ["PIP", "RGR", "PIP"],
           {"P": "minecraft:paper", "I": "minecraft:iron_ingot", "R": "minecraft:redstone",
            "G": "minecraft:gunpowder"})
    # --- blocks
    shaped("network_rack", ["ICI", "CRC", "ICI"],
           {"I": "minecraft:iron_block", "C": "minecraft:copper_block",
            "R": "minecraft:redstone_block"})
    shaped("orbital_node", ["GAG", "SES", "IRI"],
           {"G": "minecraft:glass", "A": "minecraft:amethyst_block",
            "S": "minecraft:amethyst_shard", "E": "minecraft:ender_eye",
            "I": "minecraft:iron_block", "R": "minecraft:redstone_block"})
    shaped("radar", ["ISI", "SAS", "IRI"],
           {"I": "minecraft:iron_ingot", "S": "minecraft:amethyst_shard",
            "A": "minecraft:amethyst_block", "R": "minecraft:redstone"})
    shaped("cannon_director", ["ICI", "SGS", "IRI"],
           {"I": "minecraft:iron_ingot", "C": "minecraft:copper_ingot",
            "S": "minecraft:spyglass", "G": "minecraft:gunpowder",
            "R": "minecraft:redstone"})
    print("hardware JSON written")


if __name__ == "__main__":
    main()
