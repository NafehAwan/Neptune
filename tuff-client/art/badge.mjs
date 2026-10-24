// Renders the leaf logo (art/glade-leaf.svg) with no background into the
// nametag badge glyph: assets/tuff/textures/font/badge.png (see font/badge.json).
import { createRequire } from "module";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || "playwright");
const here = path.dirname(fileURLToPath(import.meta.url));
// a lighter green than the logo, so it reads on the grey nametag background
const svg = fs.readFileSync(path.join(here, "glade-leaf.svg"), "utf8").replace("#2F6B4F", "#4FB07F");
const out = path.join(here, "../mod/src/main/resources/assets/tuff/textures/font/badge.png");
const browser = await chromium.launch();
const page = await browser.newPage();
await page.setContent("<canvas id=c width=32 height=32></canvas>");
const png = await page.evaluate(async (svg) => {
  const img = new Image();
  img.src = "data:image/svg+xml;base64," + btoa(svg);
  await img.decode();
  const g = document.getElementById("c").getContext("2d");
  g.drawImage(img, 0, 0, 32, 32);
  return document.getElementById("c").toDataURL("image/png").split(",")[1];
}, svg);
fs.writeFileSync(out, Buffer.from(png, "base64"));
await browser.close();
console.log("wrote", out);
