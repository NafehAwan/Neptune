"""Builds the mod's fonts: static Plus Jakarta Sans and Fraunces instances cut
from the Google Fonts variable files, plus one font JSON per oversample level.

usage: python3 fonts.py <dir with the variable TTFs>
"""
import json
import pathlib
import sys

from fontTools.ttLib import TTFont
from fontTools.varLib import instancer

src = pathlib.Path(sys.argv[1])
font_dir = pathlib.Path(__file__).resolve().parent.parent / "mod/src/main/resources/assets/tuff/font"

STATIC = {
    "jakarta-regular": ("plusjakartasans_PlusJakartaSans[wght].ttf", {"wght": 400}),
    "jakarta-medium": ("plusjakartasans_PlusJakartaSans[wght].ttf", {"wght": 500}),
    "jakarta-semibold": ("plusjakartasans_PlusJakartaSans[wght].ttf", {"wght": 600}),
    "jakarta-bold": ("plusjakartasans_PlusJakartaSans[wght].ttf", {"wght": 700}),
    "jakarta-extrabold": ("plusjakartasans_PlusJakartaSans[wght].ttf", {"wght": 800}),
    "fraunces-semibold-italic": ("fraunces_Fraunces-Italic[SOFT,WONK,opsz,wght].ttf", {"wght": 600, "opsz": 72, "SOFT": 0, "WONK": 1}),
}

# name -> (file, size); shift keeps the baseline where the screens expect it
FONTS = {
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
    "display": ("fraunces-semibold-italic", 30),
    "displaysm": ("fraunces-semibold-italic", 14),
}

for old in font_dir.glob("poppins-*.ttf"):
    old.unlink()
(font_dir / "Poppins-LICENSE.txt").unlink(missing_ok=True)

for name, (file, axes) in STATIC.items():
    font = TTFont(src / file)
    static = instancer.instantiateVariableFont(font, axes)
    static.save(font_dir / f"{name}.ttf")

for name, (file, size) in FONTS.items():
    for level in range(1, 7):
        data = {"providers": [
            {"type": "ttf", "file": f"tuff:{file}.ttf", "size": size, "oversample": level, "shift": [0, 0]},
            {"type": "reference", "id": "minecraft:default"},
        ]}
        (font_dir / f"{name}_{level}.json").write_text(json.dumps(data, indent=2) + "\n")

(font_dir / "PlusJakartaSans-LICENSE.txt").write_text((src / "plusjakartasans_OFL.txt").read_text())
(font_dir / "Fraunces-LICENSE.txt").write_text((src / "fraunces_OFL.txt").read_text())
