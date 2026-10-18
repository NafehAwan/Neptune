"""Builds the launcher's app icons and favicon from the smooth Tuff emblem
(rendered by tuff-client/art/render.mjs). Run from tuff-client/launcher:

    python3 scripts/make_icons.py
"""
from pathlib import Path
from PIL import Image

emblem = Image.open("src/assets/chamber/emblem.png").convert("RGBA")
icons = Path("src-tauri/icons")
icons.mkdir(parents=True, exist_ok=True)
for size, name in [(32, "32x32.png"), (128, "128x128.png"), (256, "128x128@2x.png"), (512, "icon.png")]:
    emblem.resize((size, size), Image.LANCZOS).save(icons / name)
Path("public").mkdir(exist_ok=True)
emblem.resize((64, 64), Image.LANCZOS).save("public/favicon.png")
print("icons written")
