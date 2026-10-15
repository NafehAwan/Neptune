// Renders chamber.html into PNG layers for the launcher and the mod.
//   node tuff-client/art/render.mjs            (needs Playwright + Chromium)
import { writeFileSync, mkdirSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import { createRequire } from "node:module";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || "playwright");
const here = dirname(fileURLToPath(import.meta.url));
const page = pathToFileURL(join(here, "chamber.html")).href;
const targets = [
  join(here, "..", "mod", "src", "main", "resources", "assets", "tuff", "textures", "gui", "title"),
  join(here, "..", "launcher", "src", "assets", "chamber"),
];
const layers = ["wall", "pillars", "floor", "spawner", "glow", "particle", "emblem", "preview"];

const browser = await chromium.launch();
const tab = await browser.newPage();
for (const layer of layers) {
  await tab.goto(`${page}?layer=${layer}`);
  await tab.waitForFunction(() => window.done === true);
  const data = await tab.evaluate(() => document.getElementById("c").toDataURL("image/png"));
  const png = Buffer.from(data.split(",")[1], "base64");
  for (const dir of layer === "preview" ? [here] : targets) {
    mkdirSync(dir, { recursive: true });
    writeFileSync(join(dir, `${layer}.png`), png);
  }
  console.log(layer, png.length);
}
await browser.close();
