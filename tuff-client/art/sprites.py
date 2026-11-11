"""Builds the Tuff mod's GUI sprites:
  - pixel icons (12x12, white, tinted at draw time) from the launcher's icon set
    plus module icons defined here
  - a smooth anti-aliased rounded rectangle and soft shadow (nine-slice)
Run from the repository root: python3 tuff-client/art/sprites.py
"""
import json
import re
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
SPRITES = ROOT / "mod/src/main/resources/assets/tuff/textures/gui/sprites"

EXTRA = {
    "gauge": ["............", "....####....", "..##....##..", ".#......#.#.", ".#.....#..#.", "#.....#....#",
              "#....##....#", "#....##....#", ".#........#.", "..########..", "............", "............"],
    "mouse": ["...######...", "..#..##..#..", "..#..##..#..", "..#..##..#..", "..########..", "..#......#..",
              "..#......#..", "..#......#..", "..#......#..", "...######...", "............", "............"],
    "keyboard": ["............", "############", "#..........#", "#.##.##.##.#", "#..........#", "#.##.##.##.#",
                 "#..........#", "#..######..#", "#..........#", "############", "............", "............"],
    "pin": ["...######...", "..##....##..", ".##..##..##.", ".#..####..#.", ".##..##..##.", "..##....##..",
            "...##..##...", "....####....", ".....##.....", ".....##.....", "............", "............"],
    "potion": ["....####....", ".....##.....", ".....##.....", "....#..#....", "...#....#...", "..#......#..",
               "..########..", "..########..", "..########..", "...######...", "............", "............"],
    "signal": ["............", "..........##", "..........##", ".......##.##", ".......##.##", "....##.##.##",
               "....##.##.##", ".##.##.##.##", ".##.##.##.##", ".##.##.##.##", "............", "............"],
    "clock": ["...######...", "..#......#..", ".#...##...#.", "#....##....#", "#....##....#", "#....####..#",
              "#....####..#", "#..........#", ".#........#.", "..#......#..", "...######...", "............"],
    "bolt": [".......###..", "......###...", ".....###....", "....###.....", "...#######..", "......###...",
             ".....###....", "....###.....", "...###......", "..##........", "............", "............"],
    "chip": ["..#.#..#.#..", ".##########.", "##........##", ".#..####..#.", "##..####..##", ".#..####..#.",
             "##........##", ".##########.", "..#.#..#.#..", "............", "............", "............"],
    "sun": [".....##.....", ".#...##...#.", "..#......#..", "....####....", "...######...", "##.######.##",
            "##.######.##", "...######...", "....####....", "..#......#..", ".#...##...#.", ".....##....."],
    "sprint": ["............", "............", "#.....#.....", "##....##....", ".##....##...", "..##....##..",
               "..##....##..", ".##....##...", "##....##....", "#.....#.....", "............", "............"],
    "crosshair": [".....##.....", ".....##.....", "............", "............", "............", "##...##...##",
                  "##...##...##", "............", "............", "............", ".....##.....", ".....##....."],
    "list": ["............", "##.########.", "##.########.", "............", "##.########.", "##.########.",
             "............", "##.########.", "##.########.", "............", "............", "............"],
    "image": ["############", "#..........#", "#.......##.#", "#.......##.#", "#..........#", "#...#......#",
              "#..###..#..#", "#.#####.##.#", "############", "............", "............", "............"],
    "brush": [".........###", "........###.", ".......###..", "......###...", ".....###....", "....###.....",
              "...#........", "..###.......", ".####.......", "####........", "###.........", "............"],
    "layout": ["############", "#....#.....#", "#....#.....#", "#....#.....#", "######.....#", "#....#.....#",
               "#....#######", "#....#.....#", "#....#.....#", "############", "............", "............"],
    "power": [".....##.....", "..#..##..#..", ".##..##..##.", "##...##...##", "#....##....#", "#..........#",
              "#..........#", "##........##", ".##......##.", "..########..", "............", "............"],
    "cloud": ["............", "............", "....####....", "...#....#...", "..#......##.", ".##........#",
              "#..........#", "#..........#", ".##########.", "............", "............", "............"],
    "grid": ["#####.#####.", "#####.#####.", "#####.#####.", "#####.#####.", "#####.#####.", "............",
             "#####.#####.", "#####.#####.", "#####.#####.", "#####.#####.", "#####.#####.", "............"],
    "up": ["............", "............", "............", "............", ".....##.....", "....####....",
           "...##..##...", "..##....##..", "............", "............", "............", "............"],
    "sliders": ["............", "..##........", "############", "..##........", "............", ".......##...",
                "############", ".......##...", "............", "....##......", "############", "....##......"],
    "dot": ["............"] * 4 + ["....####....", "...######...", "...######...", "....####...."] + ["............"] * 4,
}


def launcher_icons():
    source = (ROOT / "launcher/src/lib/icons.ts").read_text()
    body = source[source.index("const ui"):source.index("};")]
    icons = {}
    for name, rows in re.findall(r"(\w+): \[(.*?)\],", body, re.S):
        icons[name] = re.findall(r'"([.#]{12})"', rows)
    return {k: v for k, v in icons.items() if len(v) == 12}


def icon(grid, path):
    img = Image.new("RGBA", (12, 12), (0, 0, 0, 0))
    for y, row in enumerate(grid):
        for x, c in enumerate(row):
            if c == "#":
                img.putpixel((x, y), (255, 255, 255, 255))
    img.save(path)


def rounded(path, size=64, radius=16, blur=0, pad=0):
    scale = 4
    big = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(big).rounded_rectangle(
        (pad * scale, pad * scale, (size - pad) * scale - 1, (size - pad) * scale - 1), radius * scale, fill=255)
    mask = big.resize((size, size), Image.LANCZOS)
    if blur:
        mask = mask.filter(ImageFilter.GaussianBlur(blur))
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    img.putalpha(mask)
    img.save(path)


def ring(path, size=64, radius=16, stroke=1.5):
    """A rounded outline only (like a CSS border), so borders don't stack under see-through fills."""
    scale = 8
    big = Image.new("L", (size * scale, size * scale), 0)
    d = ImageDraw.Draw(big)
    d.rounded_rectangle((0, 0, size * scale - 1, size * scale - 1), radius * scale, fill=255)
    inner = stroke * scale
    d.rounded_rectangle((inner, inner, size * scale - 1 - inner, size * scale - 1 - inner), max(0, (radius - stroke)) * scale, fill=0)
    mask = big.resize((size, size), Image.LANCZOS)
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    img.putalpha(mask)
    img.save(path)


def shadow_ring(path, size=96, radius=20, blur=7, pad=16, lift=5.6):
    """The soft shadow with the panel's own area cut out, so a see-through panel
    shows the scene behind it instead of its shadow (like a CSS box-shadow).
    The panel sits `lift` px above the shadow's centre (Ui draws shadows lower)."""
    scale = 4
    shadow = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(shadow).rounded_rectangle(
        (pad * scale, pad * scale, (size - pad) * scale - 1, (size - pad) * scale - 1), radius * scale, fill=255)
    shadow = shadow.resize((size, size), Image.LANCZOS).filter(ImageFilter.GaussianBlur(blur))
    hole = Image.new("L", (size * scale, size * scale), 0)
    ImageDraw.Draw(hole).rounded_rectangle(
        (pad * scale, (pad - lift) * scale, (size - pad) * scale - 1, (size - pad - lift) * scale - 1), (radius - 2) * scale, fill=255)
    hole = hole.resize((size, size), Image.LANCZOS)
    px_s, px_h = shadow.load(), hole.load()
    out = Image.new("L", (size, size), 0)
    po = out.load()
    for y in range(size):
        for x in range(size):
            po[x, y] = round(px_s[x, y] * (255 - px_h[x, y]) / 255)
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    img.putalpha(out)
    img.save(path)


def color_wheel(path, size=256):
    """Hue around the circle, saturation from the centre out, full brightness.
    The game tints it grey to show other brightness levels."""
    import colorsys
    import math
    sup = size * 4
    img = Image.new("RGBA", (size, size))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            # 4x4 supersampling for a smooth edge
            r = g = b = a = 0.0
            for sy in range(4):
                for sx in range(4):
                    dx = (x + (sx + 0.5) / 4 - 0.5 - c) / c
                    dy = (y + (sy + 0.5) / 4 - 0.5 - c) / c
                    d = math.hypot(dx, dy)
                    if d > 1:
                        continue
                    hue = (math.atan2(-dy, dx) / (2 * math.pi)) % 1.0
                    cr, cg, cb = colorsys.hsv_to_rgb(hue, d, 1.0)
                    r += cr; g += cg; b += cb; a += 1
            if a:
                px[x, y] = (round(r / a * 255), round(g / a * 255), round(b / a * 255), round(a / 16 * 255))
    img.save(path)


def ramp(path, width=256):
    """White, fading in from transparent on the left: tinted for brightness and opacity bars."""
    img = Image.new("RGBA", (width, 4))
    for x in range(width):
        for y in range(4):
            img.putpixel((x, y), (255, 255, 255, round(x / (width - 1) * 255)))
    img.save(path)


def nine_slice(path, size, border):
    meta = {"gui": {"scaling": {"type": "nine_slice", "width": size, "height": size, "border": border, "stretch_inner": True}}}
    Path(str(path) + ".mcmeta").write_text(json.dumps(meta, indent=2) + "\n")


if __name__ == "__main__":
    (SPRITES / "icon").mkdir(parents=True, exist_ok=True)
    icons = {**launcher_icons(), **EXTRA}
    for name, grid in icons.items():
        icon(grid, SPRITES / "icon" / f"{name}.png")
    # One rounded shape per on-screen corner radius (in pixels), drawn 1:1.
    for r in range(1, 41):
        rounded(SPRITES / f"round_{r}.png", size=4 * r, radius=r)
        nine_slice(SPRITES / f"round_{r}.png", 4 * r, r)
        ring(SPRITES / f"ring_{r}.png", size=4 * r, radius=r)
        nine_slice(SPRITES / f"ring_{r}.png", 4 * r, r)
    rounded(SPRITES / "shadow.png", size=96, radius=20, blur=7, pad=16)
    nine_slice(SPRITES / "shadow.png", 96, 32)
    shadow_ring(SPRITES / "shadow_ring.png", size=96, radius=20, blur=7, pad=16)
    nine_slice(SPRITES / "shadow_ring.png", 96, 32)
    textures = SPRITES.parent
    color_wheel(textures / "color_wheel.png")
    ramp(textures / "ramp.png")
    print(f"{len(icons)} icons, shapes written to {SPRITES}")
