// Renders Lucide line icons (ISC license) into white GUI sprites for the Tuff mod,
// at several sizes so the menu can draw each one close to 1:1 on screen.
//
//   npm pack lucide-static@1.58.0 && tar xzf lucide-static-1.58.0.tgz
//   PLAYWRIGHT_PATH=$(npm root -g)/playwright node tuff-client/art/icons.mjs ./package
import { createRequire } from "module";
import fs from "fs";
import path from "path";
import { fileURLToPath } from "url";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || "playwright");
const here = path.dirname(fileURLToPath(import.meta.url));
const lucide = path.resolve(process.argv[2] || "package");
const out = path.join(here, "../mod/src/main/resources/assets/tuff/textures/gui/sprites/lucide");

// sprite name -> [lucide icon, filled?]
const ICONS = {
  gauge: ["gauge"], mouse: ["mouse-pointer-click"], keyboard: ["keyboard"], shirt: ["shirt"],
  pin: ["map-pin"], signal: ["wifi"], potion: ["flask-conical"], clock: ["clock"], globe: ["globe"],
  bolt: ["zap"], chip: ["cpu"], zoom: ["zoom-in"], sun: ["sun"], sprint: ["footprints"],
  crosshair: ["crosshair"], scoreboard: ["list-x"], image: ["image"],
  heart: ["heart"], heart_fill: ["heart", true], move: ["move"], grid: ["layout-grid"], list: ["list"],
  search: ["search"], settings: ["settings"], palette: ["palette"], package: ["package"],
  sliders: ["sliders-horizontal"], close: ["x"], chevron: ["chevron-down"], check: ["check"],
  plus: ["plus"], up: ["arrow-up"], down: ["arrow-down"],
  user: ["user"], user_plus: ["user-plus"], trash: ["trash-2"], play: ["play"], logout: ["log-out"], cloud: ["cloud"],
};
const SIZES = [32, 64, 128];

const browser = await chromium.launch();
const page = await browser.newPage();
await page.setContent("<canvas id=c></canvas>");
for (const size of SIZES) fs.mkdirSync(path.join(out, String(size)), { recursive: true });
for (const [name, [file, filled]] of Object.entries(ICONS)) {
  let svg = fs.readFileSync(path.join(lucide, "icons", `${file}.svg`), "utf8")
    .replace(/<!--.*?-->/s, "")
    .replace('stroke="currentColor"', 'stroke="#ffffff"');
  if (filled) svg = svg.replace('fill="none"', 'fill="#ffffff"');
  for (const size of SIZES) {
    const png = await page.evaluate(async ({ svg, size }) => {
      const img = new Image();
      img.src = "data:image/svg+xml;base64," + btoa(svg);
      await img.decode();
      const c = document.getElementById("c");
      c.width = c.height = size;
      const g = c.getContext("2d");
      g.clearRect(0, 0, size, size);
      g.drawImage(img, 0, 0, size, size);
      return c.toDataURL("image/png").split(",")[1];
    }, { svg, size });
    fs.writeFileSync(path.join(out, String(size), `${name}.png`), Buffer.from(png, "base64"));
  }
}
fs.copyFileSync(path.join(lucide, "LICENSE"), path.join(out, "../../../../Lucide-LICENSE.txt"));
await browser.close();
console.log(`rendered ${Object.keys(ICONS).length} icons x ${SIZES.length} sizes`);
