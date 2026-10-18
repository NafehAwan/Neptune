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
    // slow day cycle: warm morning to soft noon
    const k = 0.5 + 0.5 * Math.sin(t * 0.08);
    const sky = g.createLinearGradient(0, 0, 0, h);
    sky.addColorStop(0, `rgb(${Math.round(196 + 20 * k)},${Math.round(222 + 10 * k)},${Math.round(226 + 14 * k)})`);
    sky.addColorStop(1, "#F6F1E4");
    g.fillStyle = sky;
    g.fillRect(0, 0, w, h);
    // sun
    const sx = w * 0.74, sy = h * (0.26 - 0.04 * k);
    g.fillStyle = "rgba(255,236,190,0.9)";
    g.beginPath();
    g.arc(sx, sy, h * 0.07, 0, TAU);
    g.fill();
    const sg = g.createRadialGradient(sx, sy, 0, sx, sy, h * 0.35);
    sg.addColorStop(0, "rgba(255,230,170,0.45)");
    sg.addColorStop(1, "rgba(255,230,170,0)");
    g.fillStyle = sg;
    g.fillRect(0, 0, w, h);
    // pixel clouds
    const px = Math.max(4, Math.round(w / 240));
    // blocky clouds: a long base with a raised middle, like Minecraft's
    const clouds = [[0.12, 0.17, 1.0], [0.5, 0.11, 1.4], [0.86, 0.22, 0.8]];
    g.fillStyle = "rgba(255,255,255,0.9)";
    for (const [cx, cy, sc] of clouds) {
      const cw = px * 30 * sc, ch = px * 4 * sc;
      const x0 = Math.round((((cx * w + t * 10) % (w + cw * 2)) - cw) / px) * px, y0 = Math.round(cy * h / px) * px;
      g.fillRect(x0, y0, cw, ch);
      g.fillRect(x0 + cw * 0.2, y0 - ch * 0.8, cw * 0.45, ch * 0.8);
      g.fillRect(x0 + cw * 0.3, y0 - ch * 1.4, cw * 0.22, ch * 0.6);
    }
    // parallax pixel forest
    const layers = [["#B9CDB9", 0.74, 31, 0.15, 5], ["#93B49A", 0.8, 37, 0.3, 6], ["#5F8F6E", 0.87, 43, 0.6, 8], ["#3E7055", 0.95, 47, 1.0, 10]];
    for (const [col, base, seed, par, size] of layers) {
      const rr = rng(seed);
      const unit = px * size / 4;
      g.fillStyle = col;
      const shift = (t * 8 * par) % (unit * 12);
      g.fillRect(0, h * base, w, h);
      for (let x = -unit * 12 - shift; x < w + unit * 12; x += unit * (6 + Math.floor(rr() * 6))) {
        const th = unit * (4 + Math.floor(rr() * 5));
        const top = h * base - th;
        // blocky tree: trunk + stacked leaf squares
        g.fillRect(Math.round(x + unit * 1.5), Math.round(top + th * 0.5), Math.round(unit), Math.round(th * 0.5));
        g.fillRect(Math.round(x), Math.round(top + unit), Math.round(unit * 4), Math.round(th * 0.45));
        g.fillRect(Math.round(x + unit * 0.5), Math.round(top), Math.round(unit * 3), Math.round(unit * 1.5));
      }
    }
    // soft paper haze at the bottom
    const haze = g.createLinearGradient(0, h * 0.75, 0, h);
    haze.addColorStop(0, "rgba(246,244,238,0)");
    haze.addColorStop(1, "rgba(246,244,238,0.35)");
    g.fillStyle = haze;
    g.fillRect(0, h * 0.75, w, h * 0.25);
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
