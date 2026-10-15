// The animated Trial Chamber behind the home screen.
//
// Everything is drawn at a low internal resolution (about 220px tall) and
// scaled up with nearest-neighbour filtering. That gives the pixel-art look
// and keeps each frame to roughly 100k pixels, so the scene costs almost
// nothing. Static layers are pre-rendered once per resize; per frame we only
// blit them with parallax, redraw the floor, the spawner, lights and particles.

type RNG = () => number;
function mulberry32(seed: number): RNG {
  return () => {
    seed = (seed + 0x6d2b79f5) | 0;
    let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

const PAL = {
  mortar: "#22251f",
  brick: ["#3a3d36", "#40443c", "#474b42", "#4e5248"],
  brickHi: "#575b51",
  brickLo: "#33362f",
  pillar: ["#5a5e54", "#686c61", "#4b4e46", "#3b3e37"],
  copper: "#d9824b",
  copperHi: "#f0a877",
  copperLo: "#9c5530",
  copperDark: "#5a3420",
  oxid: "#4f8f7f",
  oxidLo: "#36685c",
  recess: "#141613",
  frame: "#2b2e2a",
  frameHi: "#4a4e46",
};

interface Bulb { x: number; y: number; phase: number }
interface Particle { x: number; y: number; vx: number; vy: number; life: number; max: number; depth: number; tw: number }

export interface SceneOptions { lowMotion: boolean }
export interface SceneHandle { destroy(): void; setLowMotion(v: boolean): void }

const CAM_AMPLITUDE = 70;
const PARALLAX = { wall: 0.22, pillars: 0.55, spawner: 0.8, floor: 1 };

function makeCanvas(w: number, h: number) {
  const c = document.createElement("canvas");
  c.width = Math.max(1, Math.ceil(w));
  c.height = Math.max(1, Math.ceil(h));
  return [c, c.getContext("2d")!] as const;
}

function buildWall(w: number, horizon: number, rng: RNG) {
  const [c, g] = makeCanvas(w, horizon);
  g.fillStyle = PAL.mortar;
  g.fillRect(0, 0, w, horizon);
  const bw = 16, bh = 8;
  for (let row = 0; row * bh < horizon; row++) {
    const off = (row % 2) * (bw / 2);
    for (let x = -off; x < w; x += bw) {
      g.fillStyle = PAL.brick[Math.floor(rng() * PAL.brick.length)];
      g.fillRect(x + 1, row * bh + 1, bw - 1, bh - 1);
      g.fillStyle = PAL.brickHi;
      g.fillRect(x + 1, row * bh + 1, bw - 2, 1);
      for (let k = 0; k < 4; k++) {
        g.fillStyle = rng() < 0.5 ? PAL.brickHi : PAL.brickLo;
        g.fillRect(x + 1 + Math.floor(rng() * (bw - 1)), row * bh + 2 + Math.floor(rng() * (bh - 2)), 1, 1);
      }
    }
  }
  // Arched alcoves with oxidised copper grates.
  for (let x = 46; x < w - 40; x += 152) {
    const aw = 46, ah = 74, ay = horizon - ah - 12;
    const steps = [9, 6, 4, 3, 2, 1, 1];
    g.fillStyle = PAL.frameHi;
    for (let i = 0; i < ah + 2; i++) {
      const inset = (steps[i] ?? 0) - 1;
      g.fillRect(x + inset, ay + i - 1, aw - inset * 2, 1);
    }
    g.fillStyle = PAL.recess;
    for (let i = 0; i < ah; i++) {
      const inset = steps[i] ?? 0;
      g.fillRect(x + inset, ay + i, aw - inset * 2, 1);
    }
    for (let bx = x + 5; bx < x + aw - 3; bx += 6) {
      g.fillStyle = PAL.oxidLo;
      g.fillRect(bx + 1, ay + 7, 1, ah - 7);
      g.fillStyle = PAL.oxid;
      g.fillRect(bx, ay + 7, 1, ah - 7);
    }
    for (let by = ay + 16; by < ay + ah; by += 12) {
      g.fillStyle = PAL.oxid;
      g.fillRect(x + 2, by, aw - 4, 1);
    }
    g.fillStyle = PAL.copperDark;
    g.fillRect(x - 2, ay + ah, aw + 4, 3);
    g.fillStyle = PAL.copperLo;
    g.fillRect(x - 2, ay + ah, aw + 4, 1);
  }
  // Ceiling shadow and depth haze.
  const shade = g.createLinearGradient(0, 0, 0, horizon);
  shade.addColorStop(0, "rgba(9,10,9,0.92)");
  shade.addColorStop(0.35, "rgba(9,10,9,0.55)");
  shade.addColorStop(0.8, "rgba(14,15,13,0.28)");
  shade.addColorStop(1, "rgba(14,15,13,0.4)");
  g.fillStyle = shade;
  g.fillRect(0, 0, w, horizon);
  return c;
}

function buildPillars(w: number, horizon: number, rng: RNG) {
  const [c, g] = makeCanvas(w, horizon);
  const bulbs: Bulb[] = [];
  const pw = 24;
  for (let x = 20; x < w; x += 176) {
    g.fillStyle = PAL.pillar[0];
    g.fillRect(x, 0, pw, horizon);
    g.fillStyle = PAL.pillar[1];
    g.fillRect(x, 0, 2, horizon);
    g.fillStyle = PAL.pillar[2];
    g.fillRect(x + pw - 4, 0, 4, horizon);
    g.fillStyle = PAL.pillar[3];
    g.fillRect(x + pw - 1, 0, 1, horizon);
    for (let y = 0; y < horizon; y += 18) {
      g.fillStyle = PAL.pillar[3];
      g.fillRect(x, y, pw, 1);
      g.fillStyle = PAL.pillar[1];
      g.fillRect(x, y + 1, pw - 1, 1);
      // chiseled tuff motif
      g.fillStyle = PAL.pillar[2];
      g.fillRect(x + 6, y + 5, pw - 12, 1);
      g.fillRect(x + 6, y + 13, pw - 12, 1);
      g.fillRect(x + 6, y + 5, 1, 9);
      g.fillRect(x + pw - 7, y + 5, 1, 9);
      g.fillRect(x + 10, y + 9, 4, 1);
      for (let k = 0; k < 5; k++) {
        g.fillStyle = rng() < 0.5 ? PAL.pillar[1] : PAL.pillar[3];
        g.fillRect(x + 2 + Math.floor(rng() * (pw - 6)), y + 2 + Math.floor(rng() * 15), 1, 1);
      }
    }
    // copper bulb fixture
    const bx = x + pw / 2, by = Math.round(horizon * 0.6);
    g.fillStyle = PAL.copperDark;
    g.fillRect(bx - 7, by - 7, 14, 14);
    g.fillStyle = PAL.copperLo;
    g.fillRect(bx - 6, by - 6, 12, 12);
    g.fillStyle = PAL.copper;
    g.fillRect(bx - 6, by - 6, 11, 1);
    g.fillRect(bx - 6, by - 6, 1, 11);
    bulbs.push({ x: bx, y: by, phase: rng() * 100 });
    // plinth
    g.fillStyle = PAL.pillar[1];
    g.fillRect(x - 4, horizon - 10, pw + 8, 10);
    g.fillStyle = PAL.pillar[0];
    g.fillRect(x - 4, horizon - 9, pw + 8, 1);
    g.fillStyle = PAL.pillar[3];
    g.fillRect(x - 4, horizon - 1, pw + 8, 1);
  }
  // fade pillar tops into the ceiling darkness
  const shade = g.createLinearGradient(0, 0, 0, horizon);
  shade.addColorStop(0, "rgba(9,10,9,0.9)");
  shade.addColorStop(0.3, "rgba(9,10,9,0.25)");
  shade.addColorStop(1, "rgba(9,10,9,0)");
  g.globalCompositeOperation = "source-atop";
  g.fillStyle = shade;
  g.fillRect(0, 0, w, horizon);
  return { canvas: c, bulbs };
}

function hexRgb(hex: string): [number, number, number] {
  const n = parseInt(hex.slice(1), 16);
  return [(n >> 16) & 255, (n >> 8) & 255, n & 255];
}

const FLOOR_A = hexRgb("#3d4039");
const FLOOR_B = hexRgb("#373a33");
const SEAM = hexRgb("#25281f");
const FOG = hexRgb("#151714");

function drawFloor(img: ImageData, w: number, rows: number, camX: number, glowX: number, glow: number) {
  const d = img.data;
  for (let y = 0; y < rows; y++) {
    const z = 340 / (y + 6); // distance: large at the horizon, small near the camera
    const wz = z * 1.8;
    const fz = wz / 10 - Math.floor(wz / 10);
    const near = y / rows;
    const light = 0.5 + near * 0.55;
    const fog = Math.max(0, 1 - y / 14);
    const scale = z * 0.11;
    for (let x = 0; x < w; x++) {
      const wx = (x - w / 2) * scale + camX * 0.45;
      const fx = wx / 10 - Math.floor(wx / 10);
      const seam = fx < 0.07 || fz < 0.09;
      const checker = (Math.floor(wx / 10) + Math.floor(wz / 10)) & 1;
      const base = seam ? SEAM : checker ? FLOOR_A : FLOOR_B;
      const dx = (x - glowX) / 90;
      const dy = (rows - y) / 26 - 0.4;
      const warm = Math.max(0, 1 - dx * dx - dy * dy * 0.25) * glow;
      let r = base[0] * light + warm * 70;
      let g = base[1] * light + warm * 34;
      let b = base[2] * light + warm * 10;
      r = r + (FOG[0] - r) * fog;
      g = g + (FOG[1] - g) * fog;
      b = b + (FOG[2] - b) * fog;
      const i = (y * w + x) * 4;
      d[i] = r; d[i + 1] = g; d[i + 2] = b; d[i + 3] = 255;
    }
  }
}

type V3 = [number, number, number];

function drawSpawner(g: CanvasRenderingContext2D, cx: number, cy: number, size: number, angle: number, t: number) {
  const s = size / 2;
  const verts: V3[] = [
    [-s, -s, -s], [s, -s, -s], [s, s, -s], [-s, s, -s],
    [-s, -s, s], [s, -s, s], [s, s, s], [-s, s, s],
  ];
  const tilt = -0.42;
  const ca = Math.cos(angle), sa = Math.sin(angle), ct = Math.cos(tilt), st = Math.sin(tilt);
  const proj = verts.map(([x, y, z]) => {
    const x1 = x * ca - z * sa;
    const z1 = x * sa + z * ca;
    const y2 = y * ct - z1 * st;
    const z2 = y * st + z1 * ct;
    const f = 160 / (160 + z2);
    return { x: cx + x1 * f, y: cy + y2 * f, z: z2 };
  });
  const faces = [
    [0, 1, 2, 3], [5, 4, 7, 6], [4, 0, 3, 7], [1, 5, 6, 2], [4, 5, 1, 0], [3, 2, 6, 7],
  ];
  const drawn = faces
    .map((f) => {
      const [a, b, c] = f.map((i) => proj[i]);
      const cross = (b.x - a.x) * (c.y - a.y) - (b.y - a.y) * (c.x - a.x);
      const depth = f.reduce((sum, i) => sum + proj[i].z, 0) / 4;
      return { f, front: cross < 0, depth };
    })
    .sort((a, b) => b.depth - a.depth);

  const cage = (f: number[], color: string, bars: string) => {
    const p = f.map((i) => proj[i]);
    g.strokeStyle = bars;
    g.lineWidth = 1;
    for (let k = 1; k < 4; k++) {
      const u = k / 4;
      const ax = p[0].x + (p[1].x - p[0].x) * u, ay = p[0].y + (p[1].y - p[0].y) * u;
      const bx = p[3].x + (p[2].x - p[3].x) * u, by = p[3].y + (p[2].y - p[3].y) * u;
      g.beginPath(); g.moveTo(ax, ay); g.lineTo(bx, by); g.stroke();
      const cx2 = p[0].x + (p[3].x - p[0].x) * u, cy2 = p[0].y + (p[3].y - p[0].y) * u;
      const dx2 = p[1].x + (p[2].x - p[1].x) * u, dy2 = p[1].y + (p[2].y - p[1].y) * u;
      g.beginPath(); g.moveTo(cx2, cy2); g.lineTo(dx2, dy2); g.stroke();
    }
    g.strokeStyle = color;
    g.lineWidth = 2;
    g.beginPath();
    g.moveTo(p[0].x, p[0].y);
    for (let i = 1; i < 4; i++) g.lineTo(p[i].x, p[i].y);
    g.closePath();
    g.stroke();
  };

  for (const face of drawn) if (!face.front) cage(face.f, "#2a2d29", "#33362f");

  // The flame core sits between the back and front of the cage.
  const flicker = 0.75 + Math.sin(t * 9.1) * 0.12 + Math.sin(t * 23.7) * 0.08;
  const glow = g.createRadialGradient(cx, cy, 0, cx, cy, size * 0.55);
  glow.addColorStop(0, `rgba(255,214,150,${0.95 * flicker})`);
  glow.addColorStop(0.35, `rgba(240,140,70,${0.6 * flicker})`);
  glow.addColorStop(1, "rgba(217,110,50,0)");
  g.fillStyle = glow;
  g.fillRect(cx - size, cy - size, size * 2, size * 2);
  const flame = ["#ffe7a8", "#f0a877", "#d9824b"];
  for (let i = 0; i < 14; i++) {
    const fx = cx + Math.round(Math.sin(t * 5 + i * 1.7) * (4 - i * 0.2));
    const fy = cy + 5 - i;
    g.fillStyle = flame[Math.min(2, Math.floor(i / 5))];
    g.fillRect(fx - 1, fy, 2 + (i < 6 ? 1 : 0), 1);
  }

  for (const face of drawn) if (face.front) cage(face.f, "#73776c", "#555950");
}

export function createChamberScene(canvas: HTMLCanvasElement, options: SceneOptions): SceneHandle {
  const ctx = canvas.getContext("2d", { alpha: false })!;
  let lowMotion = options.lowMotion;
  let W = 0, H = 0, horizon = 0;
  let wall: HTMLCanvasElement | null = null;
  let pillars: { canvas: HTMLCanvasElement; bulbs: Bulb[] } | null = null;
  let floorImg: ImageData | null = null;
  let dust: Particle[] = [];
  const embers: Particle[] = [];
  let raf = 0;
  let last = 0;
  let time = 12;
  let running = true;
  let pointer = { x: 0, y: 0 }, eased = { x: 0, y: 0 };
  const rng = mulberry32(1734);

  function resize() {
    const rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return;
    const scale = Math.max(2, Math.round(rect.height / 230));
    W = Math.ceil(rect.width / scale);
    H = Math.ceil(rect.height / scale);
    canvas.width = W;
    canvas.height = H;
    horizon = Math.round(H * 0.72);
    const r = mulberry32(42);
    wall = buildWall(W + CAM_AMPLITUDE * 2 * PARALLAX.wall + 40, horizon, r);
    pillars = buildPillars(W + CAM_AMPLITUDE * 2 * PARALLAX.pillars + 200, horizon, r);
    floorImg = ctx.createImageData(W, H - horizon);
    dust = Array.from({ length: Math.round((W * H) / 1400) }, () => spawnDust(true));
    draw();
  }

  function spawnDust(anywhere: boolean): Particle {
    return {
      x: rng() * W,
      y: anywhere ? rng() * H : H + 2,
      vx: (rng() - 0.3) * 2.2,
      vy: -(0.6 + rng() * 2),
      life: 0,
      max: 8 + rng() * 14,
      depth: 0.3 + rng() * 0.9,
      tw: rng() * 10,
    };
  }

  function spawnEmber(x: number, y: number): Particle {
    return { x: x + (rng() - 0.5) * 10, y, vx: (rng() - 0.5) * 3, vy: -(6 + rng() * 12), life: 0, max: 1.4 + rng() * 2, depth: 1, tw: rng() * 10 };
  }

  function draw() {
    if (!wall || !pillars || !floorImg || !W) return;
    const t = time;
    eased.x += (pointer.x - eased.x) * 0.04;
    eased.y += (pointer.y - eased.y) * 0.04;
    const cam = Math.sin(t * 0.07) * CAM_AMPLITUDE + eased.x * 10;
    const bob = Math.sin(t * 0.11) * 1.5 + eased.y * 2;

    ctx.drawImage(wall, Math.round(-(cam + CAM_AMPLITUDE) * PARALLAX.wall - 20), Math.round(bob * 0.3));
    const px = Math.round(-(cam + CAM_AMPLITUDE) * PARALLAX.pillars - 100);
    ctx.drawImage(pillars.canvas, px, Math.round(bob * 0.6));

    const spawnerX = Math.round(W * 0.7 - cam * PARALLAX.spawner);
    const pulse = 0.85 + Math.sin(t * 2.1) * 0.1 + Math.sin(t * 7.3) * 0.05;
    drawFloor(floorImg, W, H - horizon, cam, spawnerX, pulse);
    ctx.putImageData(floorImg, 0, horizon);

    // Copper bulbs: lit cores plus additive glow.
    ctx.globalCompositeOperation = "lighter";
    for (const b of pillars.bulbs) {
      const x = b.x + px, y = b.y + bob * 0.6;
      if (x < -60 || x > W + 60) continue;
      const f = 0.82 + Math.sin(t * 3 + b.phase) * 0.06 + Math.sin(t * 11.3 + b.phase * 2) * 0.05 + (Math.sin(t * 0.9 + b.phase) > 0.97 ? -0.25 : 0);
      const halo = ctx.createRadialGradient(x, y, 0, x, y, 54);
      halo.addColorStop(0, `rgba(240,150,90,${0.32 * f})`);
      halo.addColorStop(1, "rgba(240,150,90,0)");
      ctx.fillStyle = halo;
      ctx.fillRect(x - 54, y - 54, 108, 108);
    }
    ctx.globalCompositeOperation = "source-over";
    for (const b of pillars.bulbs) {
      const x = b.x + px, y = Math.round(b.y + bob * 0.6);
      if (x < -10 || x > W + 10) continue;
      const f = Math.sin(t * 11.3 + b.phase * 2) > 0.6 ? "#fff1c9" : "#ffe1a0";
      ctx.fillStyle = PAL.copperHi;
      ctx.fillRect(x - 4, y - 4, 8, 8);
      ctx.fillStyle = f;
      ctx.fillRect(x - 3, y - 3, 6, 6);
    }

    // Spawner on its pedestal.
    const size = Math.max(30, Math.round(H * 0.19));
    const baseY = horizon + Math.round((H - horizon) * 0.32);
    const pw = size + 16;
    ctx.fillStyle = "rgba(0,0,0,0.35)";
    ctx.fillRect(spawnerX - pw / 2 - 4, baseY + 1, pw + 8, 4);
    ctx.fillStyle = PAL.pillar[2];
    ctx.fillRect(spawnerX - pw / 2, baseY - 12, pw, 12);
    ctx.fillStyle = PAL.pillar[1];
    ctx.fillRect(spawnerX - pw / 2, baseY - 12, pw, 2);
    ctx.fillStyle = PAL.pillar[3];
    ctx.fillRect(spawnerX - pw / 2 + 4, baseY - 8, pw - 8, 1);
    const cy = baseY - 12 - size * 0.62 + Math.sin(t * 1.3) * 1.5;
    ctx.globalCompositeOperation = "lighter";
    const aura = ctx.createRadialGradient(spawnerX, cy, 0, spawnerX, cy, size * 2.4);
    aura.addColorStop(0, `rgba(230,120,60,${0.28 * pulse})`);
    aura.addColorStop(1, "rgba(230,120,60,0)");
    ctx.fillStyle = aura;
    ctx.fillRect(spawnerX - size * 2.4, cy - size * 2.4, size * 4.8, size * 4.8);
    ctx.globalCompositeOperation = "source-over";
    drawSpawner(ctx, spawnerX, cy, size, t * 0.55, t);

    // Embers rising from the spawner.
    ctx.globalCompositeOperation = "lighter";
    for (const e of embers) {
      const k = e.life / e.max;
      const a = (1 - k) * 0.9;
      ctx.fillStyle = k < 0.4 ? `rgba(255,214,150,${a})` : `rgba(217,130,75,${a})`;
      ctx.fillRect(Math.round(e.x - cam * PARALLAX.spawner), Math.round(e.y), 1, 1);
    }
    // Dust motes catching the light.
    for (const p of dust) {
      const k = p.life / p.max;
      const a = Math.sin(k * Math.PI) * (0.18 + 0.2 * Math.sin(t * 2 + p.tw)) * p.depth;
      if (a <= 0.01) continue;
      ctx.fillStyle = `rgba(232,222,200,${a})`;
      const x = ((p.x - cam * p.depth * 0.6) % W + W) % W;
      ctx.fillRect(Math.round(x), Math.round(p.y), p.depth > 1 ? 2 : 1, 1);
    }
    ctx.globalCompositeOperation = "source-over";
  }

  function step(dt: number) {
    time += dt;
    const spawnerWorldX = W * 0.7;
    const size = Math.max(30, Math.round(H * 0.19));
    const coreY = horizon + Math.round((H - horizon) * 0.32) - 12 - size * 0.62;
    if (embers.length < 34 && rng() < dt * 14) embers.push(spawnEmber(spawnerWorldX, coreY));
    for (let i = embers.length - 1; i >= 0; i--) {
      const e = embers[i];
      e.life += dt;
      e.x += (e.vx + Math.sin(time * 3 + e.tw) * 4) * dt;
      e.y += e.vy * dt;
      if (e.life > e.max) embers.splice(i, 1);
    }
    for (let i = 0; i < dust.length; i++) {
      const p = dust[i];
      p.life += dt;
      p.x += (p.vx + Math.sin(time * 0.5 + p.tw) * 1.5) * dt;
      p.y += p.vy * dt * 0.6;
      if (p.life > p.max || p.y < -2) dust[i] = spawnDust(false);
    }
  }

  function frame(now: number) {
    raf = requestAnimationFrame(frame);
    if (!running) { last = now; return; }
    const elapsed = now - last;
    if (elapsed < 15) return; // cap at ~60fps on high refresh displays
    const dt = Math.min(0.05, elapsed / 1000);
    last = now;
    step(dt);
    draw();
  }

  const visibility = () => { running = !document.hidden && !lowMotion; };
  const onBlur = () => { running = false; };
  const onFocus = () => { running = !document.hidden && !lowMotion; };
  const onPointer = (e: PointerEvent) => {
    pointer = { x: (e.clientX / innerWidth) * 2 - 1, y: (e.clientY / innerHeight) * 2 - 1 };
  };
  const observer = new ResizeObserver(() => resize());
  observer.observe(canvas);
  document.addEventListener("visibilitychange", visibility);
  window.addEventListener("blur", onBlur);
  window.addEventListener("focus", onFocus);
  window.addEventListener("pointermove", onPointer, { passive: true });
  resize();
  running = !lowMotion && !document.hidden;
  raf = requestAnimationFrame(frame);

  return {
    destroy() {
      cancelAnimationFrame(raf);
      observer.disconnect();
      document.removeEventListener("visibilitychange", visibility);
      window.removeEventListener("blur", onBlur);
      window.removeEventListener("focus", onFocus);
      window.removeEventListener("pointermove", onPointer);
    },
    setLowMotion(v: boolean) {
      lowMotion = v;
      running = !v && !document.hidden;
      draw();
    },
  };
}
