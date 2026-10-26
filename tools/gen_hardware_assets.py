#!/usr/bin/env python3
"""Generate the textures for the hardware / radar / cannon round.

Run from the mod root:

    python3 tools/gen_hardware_assets.py

Every texture is 16x16 and drawn from primitives, so the whole set is version-controlled as code rather
than as a pile of binaries nobody can edit. Keep the painters as plain `def`s: chained statements in a
lambda raise a SyntaxError halfway through and silently produce zero files (that cost an hour once).
"""

import os

from PIL import Image, ImageDraw

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BLOCK_TEX = os.path.join(ROOT, "src/main/resources/assets/shadowclangaming/textures/block")
ITEM_TEX = os.path.join(ROOT, "src/main/resources/assets/shadowclangaming/textures/item")

S = 16

CPU_COLOURS = {
    "basic": ((32, 90, 60), (64, 170, 110), (150, 230, 180)),
    "advanced": ((28, 62, 110), (70, 130, 210), (170, 205, 255)),
    "quantum": ((64, 30, 96), (140, 90, 210), (220, 190, 255)),
    "photonic": ((96, 74, 20), (220, 180, 70), (255, 245, 190)),
}

GPU_COLOURS = {
    "basic": ((40, 44, 52), (90, 100, 115), (170, 185, 200)),
    "vector": ((22, 62, 52), (60, 150, 120), (150, 235, 200)),
    "tensor": ((70, 28, 40), (170, 70, 95), (250, 170, 190)),
    "photonic": ((60, 52, 16), (200, 170, 50), (255, 240, 170)),
}


def ensure(path):
    os.makedirs(path, exist_ok=True)


def save(image, folder, name):
    ensure(folder)
    image.save(os.path.join(folder, name + ".png"))


def noise(draw, base, spread=10, step=2):
    """A little deterministic grain so a flat colour does not look like a placeholder."""
    x = 0
    for i in range(0, S, step):
        for j in range(0, S, step):
            delta = ((i * 7 + j * 13 + x * 5) % spread) - spread // 2
            x += 1
            colour = tuple(max(0, min(255, c + delta)) for c in base)
            draw.rectangle([i, j, i + step - 1, j + step - 1], fill=colour)


def frame(draw, colour):
    draw.rectangle([0, 0, S - 1, S - 1], outline=colour)
    draw.rectangle([1, 1, S - 2, S - 2], outline=tuple(max(0, c - 30) for c in colour))


# --------------------------------------------------------------------------- CPU

def cpu_texture(name):
    dark, mid, light = CPU_COLOURS[name]
    image = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    # board
    draw.rounded_rectangle([1, 1, S - 2, S - 2], radius=2, fill=mid, outline=dark)
    # pin rows on all four sides
    for i in range(2, S - 1, 2):
        draw.point((i, 2), fill=light)
        draw.point((i, S - 3), fill=light)
        draw.point((2, i), fill=light)
        draw.point((S - 3, i), fill=light)
    # die
    draw.rectangle([5, 5, S - 6, S - 6], fill=dark)
    draw.rectangle([6, 6, S - 7, S - 7], fill=light)
    draw.rectangle([7, 7, S - 8, S - 8], fill=mid)
    # tier pips, bottom left: one per tier
    pips = ["basic", "advanced", "quantum", "photonic"].index(name) + 1
    for i in range(pips):
        draw.point((3 + i, S - 4), fill=light)
    save(image, ITEM_TEX, "cpu_" + name)


# --------------------------------------------------------------------------- GPU

def gpu_texture(name):
    dark, mid, light = GPU_COLOURS[name]
    image = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rectangle([0, 2, S - 1, S - 4], fill=dark, outline=mid)
    # heatsink fins
    for y in range(4, S - 6, 2):
        draw.line([2, y, S - 3, y], fill=mid)
    # fan
    draw.ellipse([4, 4, S - 5, S - 6], fill=mid, outline=light)
    draw.ellipse([7, 7, S - 8, S - 9], fill=dark)
    for angle in range(0, 360, 90):
        import math
        rad = math.radians(angle)
        x = 8 + int(4 * math.cos(rad))
        y = 7 + int(3 * math.sin(rad))
        draw.point((x, y), fill=light)
    # bracket
    draw.rectangle([0, S - 4, S - 1, S - 3], fill=mid)
    save(image, ITEM_TEX, "gpu_" + name)


# --------------------------------------------------------------------------- cards

def radar_card_texture():
    image = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([1, 2, S - 2, S - 3], radius=1, fill=(28, 70, 44), outline=(90, 200, 130))
    draw.rectangle([3, 4, S - 4, S - 5], fill=(16, 40, 26))
    # three arcs and a sweep
    for radius in (6, 4, 2):
        draw.arc([8 - radius, 11 - radius, 8 + radius, 11 + radius], 180, 360, fill=(120, 240, 160))
    draw.line([8, 11, 11, 7], fill=(200, 255, 210))
    draw.point((8, 11), fill=(255, 255, 255))
    save(image, ITEM_TEX, "radar_card")


def cannon_card_texture():
    image = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle([1, 2, S - 2, S - 3], radius=1, fill=(74, 26, 20), outline=(200, 90, 70))
    draw.rectangle([3, 4, S - 4, S - 5], fill=(40, 14, 12))
    # barrel, breech, and a shell in flight
    draw.rectangle([4, 9, 12, 11], fill=(180, 180, 190))
    draw.rectangle([3, 8, 5, 12], fill=(120, 120, 130))
    draw.point((13, 7), fill=(255, 220, 120))
    draw.point((14, 6), fill=(255, 160, 60))
    draw.line([6, 6, 11, 6], fill=(220, 90, 70))
    save(image, ITEM_TEX, "cannon_card")


# --------------------------------------------------------------------------- blocks

def block_textures(name, top_painter, side_painter, bottom_painter):
    for suffix, painter in (("top", top_painter), ("side", side_painter), ("bottom", bottom_painter)):
        image = Image.new("RGBA", (S, S), (0, 0, 0, 0))
        draw = ImageDraw.Draw(image)
        painter(draw)
        frame(draw, (40, 44, 48))
        save(image, BLOCK_TEX, name + "_" + suffix)


def rack_side(draw):
    noise(draw, (34, 36, 42))
    for y in (3, 7, 11):
        draw.rectangle([2, y, S - 3, y + 2], fill=(20, 22, 26), outline=(60, 64, 70))
        draw.point((4, y + 1), fill=(90, 220, 140))
    draw.rectangle([2, 13, S - 3, 14], fill=(20, 22, 26))


def rack_top(draw):
    noise(draw, (30, 32, 38))
    for x in (3, 9):
        draw.rectangle([x, 3, x + 4, 12], fill=(18, 20, 24), outline=(70, 75, 85))
        draw.rectangle([x + 1, 5, x + 3, 10], fill=(50, 55, 65))


def rack_bottom(draw):
    noise(draw, (26, 28, 32))


def node_side(draw):
    noise(draw, (30, 54, 78))
    draw.rectangle([2, 2, S - 3, 6], fill=(60, 110, 160), outline=(120, 190, 240))
    for x in range(3, S - 3, 3):
        draw.line([x, 3, x, 5], fill=(140, 200, 245))
    draw.rectangle([3, 9, 6, 12], fill=(20, 30, 44))
    draw.point((4, 10), fill=(120, 255, 200))
    draw.point((5, 11), fill=(120, 255, 200))


def node_top(draw):
    noise(draw, (34, 60, 84))
    draw.ellipse([4, 4, 11, 11], fill=(90, 150, 200), outline=(170, 220, 255))
    draw.ellipse([6, 6, 9, 9], fill=(30, 45, 62))
    for angle in range(0, 360, 90):
        import math
        rad = math.radians(angle)
        draw.point((8 + int(6 * math.cos(rad)), 8 + int(6 * math.sin(rad))), fill=(200, 240, 255))


def node_bottom(draw):
    noise(draw, (24, 34, 44))


def radar_side(draw):
    noise(draw, (30, 44, 34))
    draw.rectangle([2, 2, S - 3, 5], fill=(40, 60, 46))
    draw.ellipse([3, 7, 12, 13], fill=(70, 120, 88), outline=(150, 230, 180))
    draw.arc([3, 7, 12, 13], 200, 340, fill=(210, 255, 225))
    draw.line([8, 10, 12, 8], fill=(230, 255, 235))


def radar_top(draw):
    noise(draw, (28, 40, 32))
    draw.ellipse([2, 2, 13, 13], outline=(120, 220, 160))
    draw.ellipse([5, 5, 10, 10], outline=(180, 255, 200))
    draw.ellipse([7, 7, 8, 8], fill=(240, 255, 245))
    draw.line([8, 8, 13, 4], fill=(230, 255, 235))


def radar_bottom(draw):
    noise(draw, (22, 30, 24))


def director_side(draw):
    noise(draw, (48, 40, 30))
    draw.rectangle([2, 4, S - 3, 9], fill=(30, 26, 20), outline=(120, 100, 70))
    draw.ellipse([4, 5, 8, 8], fill=(180, 160, 90), outline=(240, 220, 130))
    draw.ellipse([9, 5, 13, 8], fill=(180, 160, 90), outline=(240, 220, 130))
    draw.rectangle([3, 11, S - 4, 13], fill=(70, 58, 40))


def director_top(draw):
    noise(draw, (54, 44, 32))
    draw.rectangle([3, 3, S - 4, S - 4], fill=(34, 28, 22), outline=(130, 105, 70))
    draw.line([4, 12, 11, 5], fill=(240, 200, 120))
    draw.line([4, 11, 10, 5], fill=(140, 110, 70))


def director_bottom(draw):
    noise(draw, (30, 26, 20))


def main():
    for name in CPU_COLOURS:
        cpu_texture(name)
    for name in GPU_COLOURS:
        gpu_texture(name)
    radar_card_texture()
    cannon_card_texture()
    block_textures("network_rack", rack_top, rack_side, rack_bottom)
    block_textures("orbital_node", node_top, node_side, node_bottom)
    block_textures("radar", radar_top, radar_side, radar_bottom)
    block_textures("cannon_director", director_top, director_side, director_bottom)

    made = len(os.listdir(BLOCK_TEX)) + len(os.listdir(ITEM_TEX))
    print("wrote hardware textures; texture files now:", made)


if __name__ == "__main__":
    main()
