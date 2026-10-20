// Renders trailer.html frame by frame (60 fps, 1080p) and muxes trailer.wav:
//   python3 audio.py && node render.mjs <out.mp4>   (serve this folder on :8765 first)
import { createRequire } from "module";
import { spawn } from "child_process";
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PW_PATH || "playwright");
const out = process.argv[2] || "glade-trailer.mp4", FPS = 60, DUR = 13;
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1920, height: 1080 } });
await page.goto("http://localhost:8765/trailer.html");
await page.evaluate(() => window.ready);
const ff = spawn("ffmpeg", ["-y", "-loglevel", "error", "-f", "image2pipe", "-framerate", String(FPS), "-i", "-", "-i", "trailer.wav",
  "-c:v", "libx264", "-preset", "slow", "-crf", "16", "-pix_fmt", "yuv420p", "-c:a", "aac", "-b:a", "256k", "-shortest", "-movflags", "+faststart", out],
  { stdio: ["pipe", "inherit", "inherit"] });
for (let f = 0; f < FPS * DUR; f++) {
  const data = await page.evaluate((t) => { window.draw(t); return document.getElementById("c").toDataURL("image/jpeg", 0.95).split(",")[1]; }, f / FPS);
  if (!ff.stdin.write(Buffer.from(data, "base64"))) await new Promise((r) => ff.stdin.once("drain", r));
}
ff.stdin.end();
await new Promise((r) => ff.on("close", r));
await browser.close();
console.log("wrote", out);
