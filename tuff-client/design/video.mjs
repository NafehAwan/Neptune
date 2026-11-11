// Renders the Grove Glass title-screen demo to out/groveglass-demo.mp4.
//   PLAYWRIGHT_PATH=$(npm root -g)/playwright node video.mjs
import { createRequire } from "module";
import fs from "fs";
import path from "path";
import { execFileSync } from "child_process";
import { fileURLToPath } from "url";

const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || "playwright");
const here = path.dirname(fileURLToPath(import.meta.url));
const frames = path.join(here, "out", "frames");
fs.rmSync(frames, { recursive: true, force: true });
fs.mkdirSync(frames, { recursive: true });

const FPS = 30, SECONDS = 11.5;
const browser = await chromium.launch({ args: ["--allow-file-access-from-files"] });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1.5 });
for (let i = 0; i < FPS * SECONDS; i++) {
  const params = new URLSearchParams({ t: "groveglass", s: "title", demo: "1", time: (i / FPS).toFixed(4) });
  await page.goto("file://" + path.join(here, "page.html") + "?" + params);
  await page.waitForSelector("body[data-ready='1']");
  await page.screenshot({ path: path.join(frames, String(i).padStart(4, "0") + ".png") });
}
await browser.close();

const out = path.join(here, "out", "groveglass-demo.mp4");
execFileSync("ffmpeg", ["-y", "-loglevel", "error", "-framerate", String(FPS), "-i", path.join(frames, "%04d.png"),
  "-c:v", "libx264", "-pix_fmt", "yuv420p", "-crf", "20", "-preset", "slow", "-movflags", "+faststart", out]);
fs.rmSync(frames, { recursive: true, force: true });
console.log("wrote", out);
