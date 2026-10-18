// The Glade title scene: a parallax pixel forest under a slow-moving sun, with
// drifting clouds, swaying trees, birds and falling leaves. A pure function of
// time, so it can be paused on any frame. Shared with the design mockups.

function rng(seed: number) {
  let s = seed >>> 0;
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0;
    return s / 4294967296;
  };
}

export function drawGrove(g: CanvasRenderingContext2D, w: number, h: number, t: number) {
  const px = Math.max(3, Math.round(w / 320)); // one "pixel" of the pixel-art scene
  const snap = (v: number) => Math.round(v / px) * px;

  // sun travels a slow arc (a full sweep takes about four minutes)
  const sa = 0.18 + ((t * 0.004) % 0.64);
  const sx = w * (0.15 + 0.7 * sa), sy = h * (0.42 - 0.3 * Math.sin(sa * Math.PI));
  const warm = 1 - Math.sin(sa * Math.PI); // lower sun -> warmer light

  const sky = g.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, `rgb(${178 + 18 * warm},${214 - 4 * warm},${232 - 22 * warm})`);
  sky.addColorStop(0.55, `rgb(${226 + 14 * warm},${236 - 6 * warm},${226 - 20 * warm})`);
  sky.addColorStop(1, "#F6F1E4");
  g.fillStyle = sky;
  g.fillRect(0, 0, w, h);

  // soft rays turning slowly around the sun
  g.save();
  g.globalCompositeOperation = "lighter";
  g.translate(sx, sy);
  g.rotate(t * 0.05);
  for (let i = 0; i < 9; i++) {
    g.rotate((Math.PI * 2) / 9);
    const ray = g.createLinearGradient(0, 0, w * 0.5, 0);
    ray.addColorStop(0, `rgba(255,240,200,${0.06 + 0.03 * Math.sin(t * 0.8 + i)})`);
    ray.addColorStop(1, "rgba(255,240,200,0)");
    g.fillStyle = ray;
    g.beginPath();
    g.moveTo(0, 0);
    g.lineTo(w * 0.5, -h * 0.05);
    g.lineTo(w * 0.5, h * 0.05);
    g.fill();
  }
  g.restore();
  const halo = g.createRadialGradient(sx, sy, 0, sx, sy, h * 0.42);
  halo.addColorStop(0, `rgba(255,232,175,${0.55 + 0.05 * Math.sin(t * 1.3)})`);
  halo.addColorStop(1, "rgba(255,232,175,0)");
  g.fillStyle = halo;
  g.fillRect(0, 0, w, h);
  // pixel sun: a square disc with a lighter core, gently pulsing
  const sr = snap(h * (0.055 + 0.003 * Math.sin(t * 1.3)));
  g.fillStyle = "#FFE7A8";
  g.fillRect(snap(sx - sr), snap(sy - sr), sr * 2, sr * 2);
  g.fillStyle = "#FFF4CF";
  g.fillRect(snap(sx - sr * 0.6), snap(sy - sr * 0.6), snap(sr * 1.2), snap(sr * 1.2));

  // blocky clouds at three depths, each drifting at its own speed
  const clouds: [number, number, number, number, number][] = [
    [0.08, 0.13, 1.5, 9, 0.95], [0.46, 0.08, 1.9, 14, 0.95], [0.8, 0.18, 1.2, 11, 0.9],
    [0.25, 0.26, 0.8, 5, 0.6], [0.66, 0.3, 0.7, 4, 0.55], [0.95, 0.24, 0.9, 6, 0.6],
  ];
  for (const [cx, cy, sc, speed, alpha] of clouds) {
    const cw = snap(px * 26 * sc), ch = snap(px * 4 * sc);
    const span = w + cw * 2;
    const x0 = snap((((cx * w + t * speed) % span) + span) % span - cw);
    const y0 = snap(cy * h + Math.sin(t * 0.3 + cx * 9) * px);
    g.fillStyle = `rgba(214,226,232,${alpha * 0.6})`; // underside shade
    g.fillRect(x0 + px, y0 + ch, cw - px * 2, px);
    g.fillStyle = `rgba(255,255,255,${alpha})`;
    g.fillRect(x0, y0, cw, ch);
    g.fillRect(x0 + snap(cw * 0.18), y0 - snap(ch * 0.75), snap(cw * 0.46), snap(ch * 0.75));
    g.fillRect(x0 + snap(cw * 0.3), y0 - snap(ch * 1.35), snap(cw * 0.2), snap(ch * 0.6));
  }

  // birds crossing the sky, wings flapping
  for (let i = 0; i < 3; i++) {
    const span = w + 200, speed = 34 + i * 9;
    const bx = (((t * speed + i * 380) % span) + span) % span - 100;
    const by = h * (0.2 + i * 0.05) + Math.sin(t * 1.5 + i) * 8;
    const up = Math.sin(t * 9 + i * 2) > 0;
    g.fillStyle = "rgba(47,72,58,0.75)";
    const b = Math.max(2, Math.round(px * 0.6));
    g.fillRect(snap(bx), snap(by), b, b);
    g.fillRect(snap(bx) - b, snap(by) + (up ? -b : b), b, b);
    g.fillRect(snap(bx) + b, snap(by) + (up ? -b : b), b, b);
    g.fillRect(snap(bx) - 2 * b, snap(by) + (up ? -2 * b : b), b, b);
    g.fillRect(snap(bx) + 2 * b, snap(by) + (up ? -2 * b : b), b, b);
  }

  // distant rolling hills
  const hills: [string, number, number, number][] = [["#C6D6C2", 0.7, 0.05, 3], ["#AFC7AE", 0.76, 0.1, 5]];
  for (const [col, base, par, amp] of hills) {
    g.fillStyle = col;
    for (let x = 0; x < w; x += px) {
      const u = (x + t * 6 * par * 10) / w;
      const y = snap(h * base - (Math.sin(u * 5.3) * 0.5 + Math.sin(u * 11.7) * 0.25 + 0.75) * px * amp * 4);
      g.fillRect(x, y, px, h - y);
    }
  }

  // pixel trees in three layers; crowns sway with the wind, rows higher up swaying more
  const wind = (x: number, layer: number) => Math.sin(t * 1.4 + x * 0.013 + layer) * 0.6 + Math.sin(t * 0.55 + x * 0.004) * 0.4;
  const layers: [string, string, string, number, number, number, number, number][] = [
    ["#9FBE9F", "#8BAE8C", "#7C6A52", 0.8, 41, 0.25, 3, 0.6],
    ["#6E9C7A", "#5E8C6B", "#6B5640", 0.87, 47, 0.5, 4, 1.0],
    ["#447A5C", "#386B4F", "#594531", 0.95, 53, 1.0, 5, 1.6],
  ];
  layers.forEach(([leaf, leafDark, trunk, base, seed, par, size, sway], li) => {
    const rr = rng(seed);
    const u = px * size / 2;
    g.fillStyle = leaf;
    g.fillRect(0, snap(h * base), w, h);
    const shift = (t * 5 * par) % (u * 40);
    for (let x = -u * 20 - shift; x < w + u * 20; x += u * (7 + Math.floor(rr() * 5))) {
      const th = Math.floor(5 + rr() * 4);
      const ground = snap(h * base);
      const tx = snap(x);
      g.fillStyle = trunk;
      g.fillRect(tx + u * 2, ground - u * 3, u, u * 3);
      // rounded crown: narrow at the top and bottom, lit from the left, shaded on the right
      for (let row = 0; row < th; row++) {
        const width = row === th - 1 ? 3 : row === 0 ? 3 : 5;
        const off = snap(wind(tx, li) * sway * px * (row / th) * 1.5);
        const left = tx + u * (2.5 - width / 2) + off, y = ground - u * 3 - u * (row + 1);
        g.fillStyle = leaf;
        g.fillRect(left, y, u * width, u);
        g.fillStyle = leafDark;
        g.fillRect(left + u * (width - 1), y, u, u);
        if (row === 0) g.fillRect(left, y, u * width, u * 0.5);
        if (row >= th - 2) {
          g.fillStyle = "rgba(255,255,255,0.14)";
          g.fillRect(left, y, u, u);
        }
      }
    }
  });

  // grass tufts along the front edge
  g.fillStyle = "#2F6247";
  for (let x = 0; x < w; x += px * 5) {
    const lean = Math.round(Math.sin(t * 2 + x * 0.05) * 1.2);
    g.fillRect(x + lean * px, snap(h * 0.95) - px * 2, px, px * 2);
    g.fillRect(x + px * 2, snap(h * 0.95) - px, px, px);
  }

  // falling leaves drifting with the wind
  const lr = rng(77);
  for (let i = 0; i < 18; i++) {
    const life = 9 + lr() * 6, phase = lr() * life;
    const p = ((t + phase) % life) / life;
    const x0 = lr() * w * 1.2;
    const x = snap(x0 - p * w * 0.25 + Math.sin(t * 2 + i) * 16);
    const y = snap(h * (0.55 + p * 0.45));
    const flip = Math.sin(t * 4 + i) > 0;
    g.fillStyle = i % 3 === 0 ? "rgba(201,166,107,0.9)" : "rgba(94,154,98,0.9)";
    g.fillRect(x, y, flip ? px * 2 : px, flip ? px : px * 2);
  }

  // light haze at the bottom
  const haze = g.createLinearGradient(0, h * 0.8, 0, h);
  haze.addColorStop(0, "rgba(246,244,238,0)");
  haze.addColorStop(1, "rgba(246,244,238,0.25)");
  g.fillStyle = haze;
  g.fillRect(0, h * 0.8, w, h * 0.2);
}
