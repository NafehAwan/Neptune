"""Generates the Tuff Client logo (a 16x16 pixel-art tuff block with a copper
T) as PNG app icons and an SVG for the UI. Run from tuff-client/launcher:

    python3 scripts/make_icons.py
"""
from pathlib import Path
from PIL import Image

N = 16
OUTLINE = (24, 27, 23)
STONE = [(63, 66, 57), (80, 83, 74), (100, 103, 93), (122, 125, 114)]
COPPER, HIGHLIGHT, SHADOW = (217, 130, 75), (240, 168, 119), (156, 85, 48)


def stone(x, y):
    # Deterministic speckle so the texture never changes between runs.
    h = (x * 73856093) ^ (y * 19349663) ^ 0x5F3759DF
    h = (h ^ (h >> 13)) * 1274126177 & 0xFFFFFFFF
    level = (h >> 7) % 7
    shade = [0, 1, 1, 2, 2, 2, 3][level]
    # light from the top-left: brighten the top rows, darken the bottom
    if y <= 2 or x <= 1:
        shade = min(3, shade + 1)
    if y >= 13 or x >= 14:
        shade = max(0, shade - 1)
    return STONE[shade]


def in_t(x, y):
    return (3 <= y <= 5 and 3 <= x <= 12) or (6 <= y <= 12 and 6 <= x <= 9)


def grid():
    g = [[None] * N for _ in range(N)]
    for y in range(N):
        for x in range(N):
            if x in (0, N - 1) or y in (0, N - 1):
                corner = x in (0, N - 1) and y in (0, N - 1)
                g[y][x] = None if corner else OUTLINE
            elif in_t(x, y):
                top_left = not in_t(x, y - 1) or not in_t(x - 1, y)
                bottom_right = not in_t(x, y + 1) or not in_t(x + 1, y)
                g[y][x] = HIGHLIGHT if top_left else SHADOW if bottom_right else COPPER
            elif in_t(x - 1, y - 1):
                g[y][x] = STONE[0]  # drop shadow under the inlay
            else:
                g[y][x] = stone(x, y)
    return g


def png(g, size, path):
    img = Image.new("RGBA", (N, N), (0, 0, 0, 0))
    for y, row in enumerate(g):
        for x, c in enumerate(row):
            if c:
                img.putpixel((x, y), c + (255,))
    img.resize((size, size), Image.NEAREST).save(path)


def svg(g, path):
    rects = []
    for y, row in enumerate(g):
        x = 0
        while x < N:
            c = row[x]
            if c is None:
                x += 1
                continue
            run = 1
            while x + run < N and row[x + run] == c:
                run += 1
            rects.append(f'<rect x="{x}" y="{y}" width="{run}" height="1" fill="#{c[0]:02x}{c[1]:02x}{c[2]:02x}"/>')
            x += run
    path.write_text(
        f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {N} {N}" shape-rendering="crispEdges">'
        + "".join(rects) + "</svg>\n"
    )


if __name__ == "__main__":
    g = grid()
    icons = Path("src-tauri/icons")
    icons.mkdir(parents=True, exist_ok=True)
    for size, name in [(32, "32x32.png"), (128, "128x128.png"), (256, "128x128@2x.png"), (512, "icon.png")]:
        png(g, size, icons / name)
    svg(g, Path("src/assets/logo.svg"))
    Path("public").mkdir(exist_ok=True)
    png(g, 64, Path("public/favicon.png"))
    print("icons written")
