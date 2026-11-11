// Renders one screen: node shot.mjs out.png "t=groveglass&s=title&demo=1&time=7.6"
import { createRequire } from "module";
import path from "path";
import { fileURLToPath } from "url";
const require = createRequire(import.meta.url);
const { chromium } = require(process.env.PLAYWRIGHT_PATH || "playwright");
const here = path.dirname(fileURLToPath(import.meta.url));
const browser = await chromium.launch({ args: ["--allow-file-access-from-files"] });
const page = await browser.newPage({ viewport: { width: 1280, height: 720 }, deviceScaleFactor: 1.5 });
await page.goto("file://" + path.join(here, "page.html") + "?" + process.argv[3]);
await page.waitForSelector("body[data-ready='1']");
await page.waitForTimeout(150);
await page.screenshot({ path: process.argv[2] });
await browser.close();
