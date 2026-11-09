"""
Generates the cable block models + textures.

Two defects this script exists to prevent:

  1. A model element's `faces` may only name north/south/east/west/up/down. Writing
     {"faces": {"all": ...}} makes the model fail to bake, and a block with an unbaked model
     renders as nothing at all - which reads exactly like "the cable has no texture".
  2. A 4x4 px element with no `uv` samples the WHOLE 16x16 texture for each face. If the texture is
     a cable drawn on a transparent background, each face is then 90% transparent and the cable
     disappears. So every face here pins uv to a 4x4 corner, and the textures are fully opaque.
"""
import json
import os
from PIL import Image, ImageDraw

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources",
                    "assets", "shadowclangaming")
MODELS = os.path.join(ROOT, "models", "block")
TEX = os.path.join(ROOT, "textures", "block")

FACES = ("north", "south", "east", "west", "up", "down")


def write_model(name, element, texture):
    """One 6-faced element, uv pinned 1:1 to the texture's 4x4 corner."""
    faces = {face: {"texture": texture, "uv": [0, 0, 4, 4]} for face in FACES}
    model = {
        "render_type": "minecraft:cutout",
        "textures": {"particle": texture, "cable": texture},
        "elements": [{"from": element[0], "to": element[1], "faces": faces}],
    }
    with open(os.path.join(MODELS, name + ".json"), "w") as handle:
        json.dump(model, handle, indent=2)
        handle.write("\n")


# core (4x4x4) and one 6-long arm per direction, matching NetworkCableBlock's VoxelShapes
CORE = ([6, 6, 6], [10, 10, 10])
ARMS = {
    "up": ([6, 10, 6], [10, 16, 10]),
    "down": ([6, 0, 6], [10, 6, 10]),
    "north": ([6, 6, 0], [10, 10, 6]),
    "south": ([6, 6, 10], [10, 10, 16]),
    "east": ([10, 6, 6], [16, 10, 10]),
    "west": ([0, 6, 6], [6, 10, 10]),
}


def braided(base, light, dark):
    """A 16x16 fully opaque cable skin; its top-left 4x4 is what the block faces sample."""
    image = Image.new("RGBA", (16, 16), base + (255,))
    draw = ImageDraw.Draw(image)
    for y in range(16):
        for x in range(16):
            if (x + y) % 4 == 0:
                draw.point((x, y), dark + (255,))
            elif (x - y) % 4 == 0:
                draw.point((x, y), light + (255,))
    # The sampled corner gets a bright core stripe so a bare cable stub still reads as a cable.
    for i in range(4):
        draw.point((i, 1), light + (255,))
        draw.point((1, i), light + (255,))
    return image


for name, element in [("network_cable_core", CORE)] + [
        (f"network_cable_arm_{k}", v) for k, v in ARMS.items()]:
    write_model(name, element, "shadowclangaming:block/network_cable")

# The FE power cable shares the geometry, so give it the same treatment (it had the transparent
# texture problem even though its faces were valid).
for name, element in [("cable_core", CORE)] + [(f"cable_arm_{k}", v) for k, v in ARMS.items()]:
    write_model(name, element, "shadowclangaming:block/cable")

braided((26, 30, 38), (94, 214, 214), (12, 14, 18)).save(
    os.path.join(TEX, "network_cable.png"))
braided((54, 32, 18), (236, 150, 62), (28, 16, 10)).save(
    os.path.join(TEX, "cable.png"))

print("cable models: 14, textures: 2")
