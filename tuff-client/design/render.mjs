// Screenshots every concept screen into out/ (1920x1080 PNGs) and builds an overview.
//   PLAYWRIGHT_PATH=$(npm root -g)/playwright node render.mjs
import { createRequire } from "module";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || "playwright");
const here = path.dirname(fileURLToPath(import.meta.url));
const out = path.join(here, "out");
fs.mkdirSync(out, { recursive: true });

const themes = ["obsidian", "glacier", "ember", "prism", "grove", "groveglass"];
const shots = [];
for (const [i, t] of themes.entries()) {
  const n = String(i + 1).padStart(2, "0");
  for (const s of ["board", "title", "menu", "launcher"]) shots.push([`${n}-${t}-${s}`, { t, s, time: 3 }]);
  for (const time of [0, 4, 8]) shots.push([`${n}-${t}-motion-${time}`, { t, s: "title", time }]);
}
shots.push(["logos", { s: "logos" }]);
// composites use the images above, so they go last
for (const [i, t] of themes.entries()) shots.push([`${String(i + 1).padStart(2, "0")}-${t}-motion`, { t, s: "motion" }]);
shots.push(["00-overview", { s: "overview" }]);

const browser = await chromium.launch({ args: ["--allow-file-access-from-files"] });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1.5 });
for (const [name, params] of shots) {
  const url = "file://" + path.join(here, "page.html") + "?" + new URLSearchParams(params);
  await page.goto(url);
  await page.waitForSelector("body[data-ready='1']");
  await page.waitForTimeout(150);
  // composites only fill the top of the page
  const clip = params.s === "motion" ? { x: 0, y: 0, width: 1280, height: 360 } : params.s === "overview" ? { x: 0, y: 0, width: 1280, height: 560 } : undefined;
  await page.screenshot({ path: path.join(out, name + ".png"), clip });
}
await browser.close();
console.log(`rendered ${shots.length} images to ${out}`);
