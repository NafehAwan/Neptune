// Five Tuff Client design system concepts: colours, type, logos and animated backgrounds.
// Backgrounds are pure functions of time so screenshots can capture any moment.

(function () {
  const TAU = Math.PI * 2;

  // deterministic pseudo-random numbers
  function rng(seed) {
    let s = seed >>> 0;
    return () => {
      s = (s * 1664525 + 1013904223) >>> 0;
      return s / 4294967296;
    };
  }

  // ---------------------------------------------------------------- backgrounds

  function obsidianBg(g, w, h, t) {
    g.fillStyle = "#0A0A0B";
    g.fillRect(0, 0, w, h);
    // spotlight
    const sx = w * (0.62 + 0.08 * Math.sin(t * 0.25)), sy = h * 0.42;
    const glow = g.createRadialGradient(sx, sy, 0, sx, sy, w * 0.55);
    glow.addColorStop(0, "rgba(200,240,60,0.10)");
    glow.addColorStop(0.5, "rgba(255,255,255,0.025)");
    glow.addColorStop(1, "rgba(0,0,0,0)");
    g.fillStyle = glow;
    g.fillRect(0, 0, w, h);
    // dot grid displaced by a slow travelling wave
    const step = Math.round(w / 48);
    for (let y = step / 2; y < h; y += step) {
      for (let x = step / 2; x < w; x += step) {
        const d = Math.hypot(x - sx, y - sy) / w;
        const wave = Math.sin(d * 18 - t * 1.4);
        const r = 1.1 + 1.1 * Math.max(0, wave) * Math.max(0, 1 - d * 1.6);
        const a = 0.08 + 0.28 * Math.max(0, 1 - d * 1.5) * (0.6 + 0.4 * wave);
        g.fillStyle = d < 0.18 && wave > 0.85 ? `rgba(200,240,60,${a + 0.25})` : `rgba(255,255,255,${a})`;
        g.beginPath();
        g.arc(x + wave * 2, y, r, 0, TAU);
        g.fill();
      }
    }
    // vignette
    const v = g.createRadialGradient(w / 2, h / 2, h * 0.3, w / 2, h / 2, w * 0.75);
    v.addColorStop(0, "rgba(0,0,0,0)");
    v.addColorStop(1, "rgba(0,0,0,0.7)");
    g.fillStyle = v;
    g.fillRect(0, 0, w, h);
  }

  function glacierBg(g, w, h, t) {
    const sky = g.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, "#050A16");
    sky.addColorStop(0.6, "#0B1730");
    sky.addColorStop(1, "#14284A");
    g.fillStyle = sky;
    g.fillRect(0, 0, w, h);
    // stars
    const r = rng(7);
    for (let i = 0; i < 160; i++) {
      const x = r() * w, y = r() * h * 0.6, tw = 0.4 + 0.6 * Math.abs(Math.sin(t * 0.8 + i));
      g.fillStyle = `rgba(220,235,255,${0.35 * tw})`;
      g.fillRect(x, y, 1.4, 1.4);
    }
    // aurora ribbons
    // aurora ribbons, drawn small and scaled up so they come out soft
    const sw = Math.ceil(w / 8), shh = Math.ceil(h / 8);
    const small = document.createElement("canvas");
    small.width = sw;
    small.height = shh;
    const a = small.getContext("2d");
    a.globalCompositeOperation = "lighter";
    const bands = [["125,211,252", 0.0, 0.3], ["94,234,212", 1.7, 0.22], ["167,139,250", 3.1, 0.15]];
    for (const [col, ph, alpha] of bands) {
      for (let x = 0; x <= sw; x++) {
        const u = x / sw;
        const y = shh * (0.28 + 0.08 * Math.sin(u * 5 + t * 0.35 + ph) + 0.04 * Math.sin(u * 13 - t * 0.5 + ph));
        const len = shh * (0.22 + 0.1 * Math.sin(u * 7 + t * 0.4 + ph));
        const grad = a.createLinearGradient(0, y - len, 0, y + 3);
        grad.addColorStop(0, `rgba(${col},0)`);
        grad.addColorStop(0.75, `rgba(${col},${alpha * (0.6 + 0.4 * Math.sin(u * 9 + t + ph))})`);
        grad.addColorStop(1, `rgba(${col},0)`);
        a.fillStyle = grad;
        a.fillRect(x, y - len, 1, len + 3);
      }
    }
    g.save();
    g.globalCompositeOperation = "lighter";
    g.imageSmoothingQuality = "high";
    g.drawImage(small, 0, 0, w, h);
    g.restore();
    // low-poly mountains, three layers
    const layers = [["#1B3256", 0.62, 11, 0.04], ["#142847", 0.72, 23, 0.08], ["#0C1A33", 0.84, 41, 0.14]];
    for (const [col, base, seed, par] of layers) {
      const rr = rng(seed);
      g.fillStyle = col;
      g.beginPath();
      g.moveTo(-50, h);
      let x = -50 - (t * 6 * par) % 140;
      while (x < w + 160) {
        g.lineTo(x, h * base - rr() * h * 0.16);
        x += 70 + rr() * 90;
      }
      g.lineTo(w + 160, h);
      g.fill();
      // snow caps hint
      g.strokeStyle = "rgba(220,240,255,0.06)";
      g.lineWidth = 2;
      g.stroke();
    }
    // falling snow
    const s = rng(3);
    for (let i = 0; i < 90; i++) {
      const sp = 14 + s() * 22, x0 = s() * w, y0 = s() * h;
      const y = (y0 + t * sp) % h, x = x0 + Math.sin(t * 0.6 + i) * 12;
      g.fillStyle = `rgba(235,245,255,${0.25 + s() * 0.4})`;
      g.beginPath();
      g.arc(x, y, 0.8 + s() * 1.6, 0, TAU);
      g.fill();
    }
  }

  function emberBg(g, w, h, t) {
    const sky = g.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, "#0E0A0A");
    sky.addColorStop(0.62, "#1E1110");
    sky.addColorStop(1, "#2A1410");
    g.fillStyle = sky;
    g.fillRect(0, 0, w, h);
    // horizon glow
    const hz = h * 0.7;
    const glow = g.createRadialGradient(w * 0.55, hz, 0, w * 0.55, hz, w * 0.6);
    glow.addColorStop(0, `rgba(255,110,70,${0.42 + 0.06 * Math.sin(t * 0.9)})`);
    glow.addColorStop(0.4, "rgba(255,77,109,0.10)");
    glow.addColorStop(1, "rgba(0,0,0,0)");
    g.fillStyle = glow;
    g.fillRect(0, 0, w, h);
    // blocky hills (stepped silhouettes, a nod to the Nether)
    const layers = [["#2B1613", 0.66, 5, 26], ["#1C0F0E", 0.76, 9, 34], ["#120A0A", 0.88, 13, 44]];
    for (const [col, base, seed, step] of layers) {
      const rr = rng(seed);
      g.fillStyle = col;
      g.beginPath();
      g.moveTo(0, h);
      let y = h * base;
      for (let x = 0; x <= w + step; x += step) {
        y = Math.max(h * (base - 0.12), Math.min(h * (base + 0.04), y + (rr() - 0.5) * step * 1.2));
        g.lineTo(x, y);
        g.lineTo(x + step, y);
      }
      g.lineTo(w, h);
      g.fill();
    }
    // rising embers
    g.globalCompositeOperation = "lighter";
    const r = rng(21);
    for (let i = 0; i < 46; i++) {
      const sp = 14 + r() * 30, x0 = r() * w, life = h * (0.5 + r() * 0.5);
      const prog = ((t * sp + r() * life) % life) / life;
      const x = x0 + Math.sin(t * 0.7 + i) * 24 * prog;
      const y = h * 0.95 - prog * life;
      const a = Math.sin(prog * Math.PI) * (0.35 + r() * 0.45);
      const size = 0.8 + r() * 1.6;
      const eg = g.createRadialGradient(x, y, 0, x, y, size * 4);
      eg.addColorStop(0, `rgba(255,170,110,${a})`);
      eg.addColorStop(1, "rgba(255,90,60,0)");
      g.fillStyle = eg;
      g.fillRect(x - size * 4, y - size * 4, size * 8, size * 8);
    }
    g.globalCompositeOperation = "source-over";
    const v = g.createRadialGradient(w / 2, h / 2, h * 0.35, w / 2, h / 2, w * 0.8);
    v.addColorStop(0, "rgba(0,0,0,0)");
    v.addColorStop(1, "rgba(0,0,0,0.6)");
    g.fillStyle = v;
    g.fillRect(0, 0, w, h);
  }

  function prismBg(g, w, h, t) {
    g.fillStyle = "#0E0B1A";
    g.fillRect(0, 0, w, h);
    g.globalCompositeOperation = "lighter";
    const blobs = [
      ["139,92,246", 0.28, 0.35, 0.42, 0.0],
      ["236,72,153", 0.72, 0.6, 0.38, 1.3],
      ["59,130,246", 0.55, 0.15, 0.3, 2.6],
      ["168,85,247", 0.15, 0.85, 0.32, 3.9],
    ];
    for (const [col, bx, by, br, ph] of blobs) {
      const x = w * (bx + 0.08 * Math.sin(t * 0.21 + ph)), y = h * (by + 0.1 * Math.cos(t * 0.17 + ph));
      const rad = w * br * (1 + 0.08 * Math.sin(t * 0.3 + ph));
      const gr = g.createRadialGradient(x, y, 0, x, y, rad);
      gr.addColorStop(0, `rgba(${col},0.42)`);
      gr.addColorStop(1, `rgba(${col},0)`);
      g.fillStyle = gr;
      g.fillRect(0, 0, w, h);
    }
    g.globalCompositeOperation = "source-over";
    // fine grain
    const r = rng(99 + Math.floor(t * 12));
    for (let i = 0; i < 2600; i++) {
      g.fillStyle = `rgba(255,255,255,${r() * 0.04})`;
      g.fillRect(r() * w, r() * h, 1, 1);
    }
    // perspective grid floor, very faint
    g.strokeStyle = "rgba(255,255,255,0.05)";
    g.lineWidth = 1;
    const hz = h * 0.66;
    for (let i = -20; i <= 20; i++) {
      g.beginPath();
      g.moveTo(w / 2 + i * 12, hz);
      g.lineTo(w / 2 + i * w * 0.12, h);
      g.stroke();
    }
    for (let k = 0; k < 9; k++) {
      const p = ((k + (t * 0.25) % 1) / 9) ** 2;
      const y = hz + (h - hz) * p;
      g.beginPath();
      g.moveTo(0, y);
      g.lineTo(w, y);
      g.stroke();
    }
  }

  function groveBg(g, w, h, t) {
    const px = Math.max(3, Math.round(w / 320)); // one "pixel" of the pixel-art scene
    const snap = (v) => Math.round(v / px) * px;

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
    const clouds = [
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
    const hills = [["#C6D6C2", 0.7, 0.05, 3], ["#AFC7AE", 0.76, 0.1, 5]];
    for (const [col, base, par, amp] of hills) {
      g.fillStyle = col;
      for (let x = 0; x < w; x += px) {
        const u = (x + t * 6 * par * 10) / w;
        const y = snap(h * base - (Math.sin(u * 5.3) * 0.5 + Math.sin(u * 11.7) * 0.25 + 0.75) * px * amp * 4);
        g.fillRect(x, y, px, h - y);
      }
    }

    // pixel trees in three layers; crowns sway with the wind, rows higher up swaying more
    const wind = (x, layer) => Math.sin(t * 1.4 + x * 0.013 + layer) * 0.6 + Math.sin(t * 0.55 + x * 0.004) * 0.4;
    const layers = [
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

  // ---------------------------------------------------------------- logos (viewBox 0 0 100 100)

  const L = {
    obsidianA: () => `
      <rect x="6" y="6" width="88" height="88" rx="22" fill="#F4F4F2"/>
      <path d="M26 28h48v13H56.5v34h-13V41H26z" fill="#0A0A0B"/>
      <rect x="62" y="62" width="12" height="12" rx="3" fill="#C8F03C"/>`,
    obsidianB: (fg = "#F4F4F2") => `
      <circle cx="50" cy="50" r="42" fill="none" stroke="${fg}" stroke-width="7"/>
      <path d="M50 26v40a8 8 0 0 0 8 8h6M34 40h30" fill="none" stroke="${fg}" stroke-width="8" stroke-linecap="round"/>
      <circle cx="72" cy="28" r="6" fill="#C8F03C"/>`,
    glacierA: () => `
      <defs><linearGradient id="gla" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#E0F4FF"/><stop offset="1" stop-color="#5BB8F0"/></linearGradient></defs>
      <path d="M50 6l38 22v44L50 94 12 72V28z" fill="url(#gla)"/>
      <path d="M50 6l38 22-38 22-38-22z" fill="#FFFFFF" opacity="0.55"/>
      <path d="M34 30.5h32M50 30.5v20" stroke="#0B1220" stroke-width="7" stroke-linecap="round" opacity="0.85"/>
      <path d="M50 50v44M12 28l38 22 38-22" fill="none" stroke="#0B1220" stroke-opacity="0.18" stroke-width="2"/>`,
    glacierB: () => `
      <defs><linearGradient id="glb" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#BFE9FF"/><stop offset="1" stop-color="#3B9EE0"/></linearGradient></defs>
      <rect x="6" y="6" width="88" height="88" rx="28" fill="url(#glb)"/>
      <path d="M22 44l14-14 14 10 14-10 14 14" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linejoin="round" stroke-linecap="round"/>
      <path d="M50 40v36" stroke="#FFFFFF" stroke-width="8" stroke-linecap="round"/>`,
    emberA: () => `
      <defs><linearGradient id="ema" x1="0" y1="1" x2="0.4" y2="0"><stop offset="0" stop-color="#FF4D6D"/><stop offset="1" stop-color="#FFB25B"/></linearGradient></defs>
      <path d="M50 4c10 18 34 28 34 56a34 34 0 0 1-68 0c0-16 8-24 14-30 0 12 6 16 10 16C40 32 42 18 50 4z" fill="url(#ema)"/>
      <path d="M36 54h28M50 54v26" stroke="#141010" stroke-width="8" stroke-linecap="round"/>`,
    emberB: () => `
      <defs><linearGradient id="emb" x1="0" y1="0" x2="1" y2="0"><stop offset="0" stop-color="#FF7A45"/><stop offset="1" stop-color="#FF4D6D"/></linearGradient></defs>
      <path d="M14 62a36 36 0 0 1 72 0z" fill="url(#emb)"/>
      <rect x="8" y="68" width="84" height="8" rx="4" fill="#FF7A45"/>
      <rect x="20" y="82" width="60" height="7" rx="3.5" fill="#FF4D6D" opacity="0.6"/>
      <path d="M38 46h24M50 46v16" stroke="#141010" stroke-width="7" stroke-linecap="round"/>`,
    prismA: () => `
      <defs><linearGradient id="pra" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#8B5CF6"/><stop offset="1" stop-color="#EC4899"/></linearGradient></defs>
      <path d="M50 8l42 76H8z" fill="url(#pra)"/>
      <path d="M50 8l13 76H37z" fill="#FFFFFF" opacity="0.18"/>
      <path d="M34 50h32M50 50v22" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round"/>`,
    prismB: () => `
      <defs><linearGradient id="prb" x1="0" y1="0" x2="1" y2="1"><stop offset="0" stop-color="#8B5CF6"/><stop offset="1" stop-color="#EC4899"/></linearGradient></defs>
      <rect x="6" y="6" width="88" height="88" rx="24" fill="url(#prb)"/>
      <path d="M28 38l22-12 22 12M28 54l22-12 22 12" fill="none" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round" stroke-linejoin="round"/>
      <path d="M50 48v28" stroke="#FFFFFF" stroke-width="7" stroke-linecap="round"/>`,
    groveA: () => `
      <path d="M50 92C18 76 12 46 22 20c22-2 52 6 60 32 4 16-8 32-32 40z" fill="#2F6B4F"/>
      <path d="M30 34c14 2 28 8 40 18M44 38c-2 12 0 30 8 50" fill="none" stroke="#F6F4EE" stroke-width="6" stroke-linecap="round"/>`,
    groveB: () => `
      <path d="M50 8l38 20v44L50 92 12 72V28z" fill="#8C6A43"/>
      <path d="M50 8l38 20-38 20-38-20z" fill="#5E9A62"/>
      <path d="M12 28l38 20v8L12 36z" fill="#4C8A55"/>
      <path d="M88 28L50 48v8l38-20z" fill="#3F7A4A"/>
      <path d="M50 48v44" stroke="#6E5134" stroke-width="2"/>
      <path d="M22 54l18 9M31 58.5v20" stroke="#F6F4EE" stroke-width="5" stroke-linecap="round"/>`,
  };

  // ---------------------------------------------------------------- themes

  window.THEMES = {
    obsidian: {
      n: 1, name: "Obsidian", tagline: "Monochrome. Precise. One sharp accent.",
      fonts: { ui: "'Geist Sans'", head: "'Geist Sans'", mono: "'Geist Mono'" },
      headWeight: 700, headTracking: "-0.04em", wordmark: "tuff", wordCase: "lower",
      c: { bg: "#0A0A0B", surface: "#111113", surface2: "#18181B", border: "rgba(255,255,255,0.08)", text: "#F4F4F2", muted: "#8A8A90", accent: "#C8F03C", accent2: "#C8F03C", onAccent: "#0A0A0B", glass: "rgba(12,12,14,0.86)" },
      swatches: [["Void", "#0A0A0B"], ["Graphite", "#18181B"], ["Steel", "#8A8A90"], ["Bone", "#F4F4F2"], ["Acid", "#C8F03C"]],
      radius: 8, cardRadius: 10, bg: obsidianBg, logos: [L.obsidianA, L.obsidianB], logoNames: ["Notch", "Orbit t"],
      bgName: "Dot field: a dot grid ripples out from a drifting spotlight",
    },
    glacier: {
      n: 2, name: "Glacier", tagline: "Frosted glass, cold light, calm motion.",
      fonts: { ui: "'Manrope'", head: "'Manrope'", mono: "'Geist Mono'" },
      headWeight: 800, headTracking: "-0.02em", wordmark: "TUFF", wordCase: "upper",
      c: { bg: "#0B1220", surface: "rgba(255,255,255,0.07)", surface2: "rgba(255,255,255,0.12)", border: "rgba(190,230,255,0.18)", text: "#EEF6FF", muted: "#93A9C4", accent: "#7DD3FC", accent2: "#A5B4FC", onAccent: "#06223A", glass: "rgba(16,30,54,0.55)" },
      swatches: [["Abyss", "#0B1220"], ["Deep", "#14284A"], ["Frost", "#93A9C4"], ["Ice", "#7DD3FC"], ["Snow", "#EEF6FF"]],
      radius: 14, cardRadius: 18, bg: glacierBg, logos: [L.glacierA, L.glacierB], logoNames: ["Crystal", "Peak"], glassy: true,
      bgName: "Aurora: ribbons of light sway over snowy low-poly mountains as snow falls",
    },
    ember: {
      n: 3, name: "Ember", tagline: "Warm dark, soft glow, cosy evenings.",
      fonts: { ui: "'Sora'", head: "'Sora'", mono: "'Geist Mono'" },
      headWeight: 600, headTracking: "-0.03em", wordmark: "tuff", wordCase: "lower",
      c: { bg: "#141010", surface: "#1C1615", surface2: "#251D1B", border: "rgba(255,200,170,0.09)", text: "#FBEFE9", muted: "#A8918A", accent: "#FF7A45", accent2: "#FF4D6D", onAccent: "#1A0B08", glass: "rgba(24,18,17,0.88)" },
      swatches: [["Coal", "#141010"], ["Ash", "#251D1B"], ["Smoke", "#A8918A"], ["Ember", "#FF7A45"], ["Rose", "#FF4D6D"]],
      radius: 12, cardRadius: 16, bg: emberBg, logos: [L.emberA, L.emberB], logoNames: ["Flame", "Sunrise"], gradient: true,
      bgName: "Embers: sparks drift up from a glowing, blocky horizon",
    },
    prism: {
      n: 4, name: "Prism", tagline: "Vivid gradients, bold type, clean grid.",
      fonts: { ui: "'Outfit'", head: "'Unbounded'", mono: "'Geist Mono'" },
      headWeight: 700, headTracking: "-0.01em", wordmark: "TUFF", wordCase: "upper",
      c: { bg: "#0E0B1A", surface: "#161226", surface2: "#1F1A33", border: "rgba(200,180,255,0.10)", text: "#F5F3FF", muted: "#9A92B8", accent: "#8B5CF6", accent2: "#EC4899", onAccent: "#FFFFFF", glass: "rgba(20,16,36,0.82)" },
      swatches: [["Ink", "#0E0B1A"], ["Night", "#1F1A33"], ["Haze", "#9A92B8"], ["Violet", "#8B5CF6"], ["Pink", "#EC4899"]],
      radius: 12, cardRadius: 16, bg: prismBg, logos: [L.prismA, L.prismB], logoNames: ["Prism", "Stack"], gradient: true,
      bgName: "Mesh: soft violet, pink and blue blobs morph over a faint grid floor",
    },
    grove: {
      n: 5, name: "Grove", tagline: "Light, natural, unhurried. The calm one.",
      fonts: { ui: "'Plus Jakarta Sans'", head: "'Fraunces'", mono: "'Geist Mono'" },
      headWeight: 600, headTracking: "-0.02em", wordmark: "Tuff", wordCase: "title",
      c: { bg: "#F6F4EE", surface: "#FFFFFF", surface2: "#EFEBE1", border: "rgba(40,50,40,0.10)", text: "#1F2A22", muted: "#6B756C", accent: "#2F6B4F", accent2: "#C9A66B", onAccent: "#FFFFFF", glass: "rgba(250,248,242,0.88)" },
      swatches: [["Paper", "#F6F4EE"], ["Sand", "#E6DCC6"], ["Moss", "#6B756C"], ["Forest", "#2F6B4F"], ["Bark", "#1F2A22"]],
      radius: 10, cardRadius: 14, bg: groveBg, logos: [L.groveA, L.groveB], logoNames: ["Leaf", "Grass block"], light: true,
      bgName: "Grove: a parallax pixel forest under a slow day cycle, clouds drifting",
    },
  };
  // Grove with frosted, see-through panels (the direction picked for refinement)
  window.THEMES.groveglass = Object.assign({}, window.THEMES.grove, {
    n: 6, name: "Grove Glass", tagline: "Grove, with frosted glass and depth. Light shows through.", frost: true,
    c: Object.assign({}, window.THEMES.grove.c, {
      surface: "rgba(255,255,255,0.42)", surface2: "rgba(255,255,255,0.55)", border: "rgba(255,255,255,0.65)",
      glass: "rgba(255,255,255,0.38)",
    }),
    swatches: [["Frost", "#FFFFFF"], ["Paper", "#F6F4EE"], ["Moss", "#6B756C"], ["Forest", "#2F6B4F"], ["Bark", "#1F2A22"]],
    bgName: "Grove: the pixel forest and day cycle stay visible through every frosted panel",
  });
  window.LOGO_SVG = (fn, size, fg) => `<svg viewBox="0 0 100 100" width="${size}" height="${size}" xmlns="http://www.w3.org/2000/svg">${fg ? fn(fg) : fn()}</svg>`;
})();
