// Renders the Glade leaf logo (design concept 5A) to PNGs for the launcher and the mod.
//   PLAYWRIGHT_PATH=$(npm root -g)/playwright node tuff-client/art/logo.mjs
import { createRequire } from "module";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || "playwright");
const root = path.join(path.dirname(fileURLToPath(import.meta.url)), "..");

const LEAF = `<path d="M50 92C18 76 12 46 22 20c22-2 52 6 60 32 4 16-8 32-32 40z" fill="#2F6B4F"/>
  <path d="M30 34c14 2 28 8 40 18M44 38c-2 12 0 30 8 50" fill="none" stroke="#F6F4EE" stroke-width="6" stroke-linecap="round"/>`;
// the bare mark, and an app icon: the mark on a soft paper tile
const MARK = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">${LEAF}</svg>`;
const TILE = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 100 100">
  <defs><linearGradient id="p" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#FBFAF6"/><stop offset="1" stop-color="#E9EFE7"/></linearGradient></defs>
  <rect x="4" y="4" width="92" height="92" rx="22" fill="url(#p)"/>
  <g transform="translate(17 15) scale(0.66)">${LEAF}</g></svg>`;

fs.writeFileSync(path.join(root, "art/glade-leaf.svg"), MARK + "\n");
fs.writeFileSync(path.join(root, "art/glade-icon.svg"), TILE + "\n");

const outputs = [
  [MARK, 512, "launcher/src/assets/brand/leaf.png"],
  [MARK, 512, "mod/src/main/resources/assets/tuff/textures/gui/title/emblem.png"],
  [TILE, 512, "mod/src/main/resources/assets/tuff/icon.png"],
  [TILE, 512, "launcher/src-tauri/icons/icon.png"],
  [TILE, 256, "launcher/src-tauri/icons/128x128@2x.png"],
  [TILE, 128, "launcher/src-tauri/icons/128x128.png"],
  [TILE, 32, "launcher/src-tauri/icons/32x32.png"],
  [TILE, 64, "launcher/public/favicon.png"],
];
const browser = await chromium.launch();
const page = await browser.newPage();
for (const [svg, size, out] of outputs) {
  await page.setViewportSize({ width: size, height: size });
  await page.setContent(`<html><body style="margin:0;background:transparent">${svg.replace("<svg ", `<svg width="${size}" height="${size}" `)}</body></html>`);
  fs.mkdirSync(path.dirname(path.join(root, out)), { recursive: true });
  await page.screenshot({ path: path.join(root, out), omitBackground: true });
}
await browser.close();
console.log(`wrote ${outputs.length} images`);
