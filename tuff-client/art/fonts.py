"""Builds the mod's fonts from the exact files the Grove Glass mockups use
(the @fontsource packages in design/node_modules): Plus Jakarta Sans for text,
Fraunces italic for the wordmark and Geist Mono for small labels. Writes one
font JSON per oversample level.

usage: (cd tuff-client/design && npm ci) && python3 tuff-client/art/fonts.py

Two families of sizes:
- GUI fonts (body, semibold, ...) are sized in GUI units, for screens drawn at
  the normal GUI scale (HUD editor).
- Mockup fonts (ui13sb, head96, ...) are sized in mockup pixels: the title
  screen, account menu and mod menu are laid out in the mockups' 1280x720 space
  and scaled to the window, so a 13px label in the mockup is the font "ui13sb".
"""
import json
import pathlib

from fontTools.ttLib import TTFont

here = pathlib.Path(__file__).resolve().parent
src = here.parent / "design/node_modules/@fontsource"
font_dir = here.parent / "mod/src/main/resources/assets/tuff/font"

STATIC = {
    "jakarta-regular": "plus-jakarta-sans/files/plus-jakarta-sans-latin-400-normal.woff",
    "jakarta-medium": "plus-jakarta-sans/files/plus-jakarta-sans-latin-500-normal.woff",
    "jakarta-semibold": "plus-jakarta-sans/files/plus-jakarta-sans-latin-600-normal.woff",
    "jakarta-bold": "plus-jakarta-sans/files/plus-jakarta-sans-latin-700-normal.woff",
    "jakarta-extrabold": "plus-jakarta-sans/files/plus-jakarta-sans-latin-800-normal.woff",
    "fraunces-semibold-italic": "fraunces/files/fraunces-latin-600-italic.woff",
    "geistmono-regular": "geist-mono/files/geist-mono-latin-400-normal.woff",
    "geistmono-medium": "geist-mono/files/geist-mono-latin-500-normal.woff",
}

# name -> (file, size)
FONTS = {
    # GUI units
    "body": ("jakarta-regular", 9),
    "medium": ("jakarta-medium", 9),
    "semibold": ("jakarta-semibold", 9.5),
    "bold": ("jakarta-bold", 10.5),
    "title": ("jakarta-bold", 16),
    "hero": ("jakarta-extrabold", 26),
    "small": ("jakarta-medium", 7.5),
    "caps": ("jakarta-bold", 8),
    "tiny": ("jakarta-semibold", 6),
    "name": ("jakarta-semibold", 8),
    # mockup pixels
    "ui11sb": ("jakarta-semibold", 11),
    "ui11_5": ("jakarta-regular", 11.5),
    "ui12": ("jakarta-regular", 12),
    "ui12_5m": ("jakarta-medium", 12.5),
    "ui12_5": ("jakarta-regular", 12.5),
    "ui13sb": ("jakarta-semibold", 13),
    "ui13_5sb": ("jakarta-semibold", 13.5),
    "ui14": ("jakarta-regular", 14),
    "ui14sb": ("jakarta-semibold", 14),
    "ui15sb": ("jakarta-semibold", 15),
    "ui16b": ("jakarta-bold", 16),
    "head96": ("fraunces-semibold-italic", 96),
    "head30": ("fraunces-semibold-italic", 30),
    "head22": ("fraunces-semibold-italic", 22),
    "mono10_5": ("geistmono-regular", 10.5),
    "mono10_5m": ("geistmono-medium", 10.5),
    "mono11m": ("geistmono-medium", 11),
    "mono11_5": ("geistmono-regular", 11.5),
}

for old in list(font_dir.glob("*.ttf")) + list(font_dir.glob("*.json")) + list(font_dir.glob("*-LICENSE.txt")):
    old.unlink()

for name, file in STATIC.items():
    font = TTFont(src / file)
    font.flavor = None
    font.save(font_dir / f"{name}.ttf")

for name, (file, size) in FONTS.items():
    for level in range(1, 7):
        data = {"providers": [
            {"type": "ttf", "file": f"tuff:{file}.ttf", "size": size, "oversample": level, "shift": [0, 0]},
            {"type": "reference", "id": "minecraft:default"},
        ]}
        (font_dir / f"{name}_{level}.json").write_text(json.dumps(data, indent=2) + "\n")

for family, folder in [("PlusJakartaSans", "plus-jakarta-sans"), ("Fraunces", "fraunces"), ("GeistMono", "geist-mono")]:
    (font_dir / f"{family}-LICENSE.txt").write_text((src / folder / "LICENSE").read_text())
