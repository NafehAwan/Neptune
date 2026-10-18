// Renders one screen of one design concept: page.html?t=<theme>&s=<screen>&time=<seconds>
// Screens: board, title, menu, launcher, logos (logos ignores t).

const q = new URLSearchParams(location.search);
const T = THEMES[q.get("t") || "obsidian"];
const screen = q.get("s") || "board";
const time = parseFloat(q.get("time") || "3");
const root = document.getElementById("root");

// frosted glass surface used by Grove Glass
const FROST = "background:rgba(255,255,255,0.38);border:1px solid rgba(255,255,255,0.7);backdrop-filter:blur(22px) saturate(1.5);-webkit-backdrop-filter:blur(22px) saturate(1.5);box-shadow:0 20px 60px rgba(30,60,40,0.18), inset 0 1px 0 rgba(255,255,255,0.8)";
const FROST_BTN = "background:rgba(255,255,255,0.45);border:1px solid rgba(255,255,255,0.75);backdrop-filter:blur(14px);-webkit-backdrop-filter:blur(14px)";
const ic = (name, cls = "") => `<span class="ic ${cls}">${ICONS[name] || ""}</span>`;
const logo = (i, size) => LOGO_SVG(T.logos[i], size);

const MODS = [
  ["Zoom", "zoom-in", "Hold C to zoom in", true, "Utility"],
  ["Fullbright", "sun", "See clearly at night", false, "Visual"],
  ["Crosshair", "crosshair", "Your own crosshair", true, "Visual"],
  ["Toggle Sprint", "footprints", "Always sprint", true, "Utility"],
  ["Keystrokes", "keyboard", "Show WASD and clicks", true, "HUD"],
  ["FPS", "gauge", "Frames per second", true, "HUD"],
  ["CPS", "mouse-pointer-click", "Clicks per second", false, "HUD"],
  ["Armor Status", "shirt", "Armor durability", true, "HUD"],
  ["Coordinates", "map-pin", "Position and facing", false, "HUD"],
  ["Ping", "wifi", "Server latency", false, "HUD"],
  ["Potion Effects", "flask-conical", "Active effects", true, "HUD"],
  ["Clock", "clock", "Real-world time", false, "HUD"],
];

function applyTheme() {
  const s = document.documentElement.style;
  const c = T.c;
  for (const [k, v] of Object.entries(c)) s.setProperty("--" + k, v);
  s.setProperty("--ui", T.fonts.ui);
  s.setProperty("--head", T.fonts.head);
  s.setProperty("--mono", T.fonts.mono);
  s.setProperty("--hw", T.headWeight);
  s.setProperty("--ht", T.headTracking);
  s.setProperty("--r", T.radius + "px");
  s.setProperty("--cr", T.cardRadius + "px");
  document.body.classList.add("t-" + Object.keys(THEMES).find((k) => THEMES[k] === T));
  if (T.gradient) document.body.classList.add("grad");
}

function drawBg(canvas, w, h, t) {
  const dpr = 2;
  canvas.width = w * dpr;
  canvas.height = h * dpr;
  const g = canvas.getContext("2d");
  g.scale(dpr, dpr);
  T.bg(g, w, h, t);
}

function wordmark(size, extra = "") {
  const text = T.wordmark;
  const style = `font-size:${size}px;line-height:1;${extra}`;
  if (T === THEMES.prism) return `<span class="head" style="${style};background:linear-gradient(120deg,#C4B5FD,#F9A8D4);-webkit-background-clip:text;color:transparent">${text}</span>`;
  if (T.light) return `<span class="head" style="${style};font-style:italic">${text}</span>`;
  return `<span class="head" style="${style}">${text}</span>`;
}

// ---------------------------------------------------------------- board

function board() {
  const sw = T.swatches.map(([n, hex]) => {
    const light = parseInt(hex.slice(1, 3), 16) + parseInt(hex.slice(3, 5), 16) + parseInt(hex.slice(5, 7), 16) > 420;
    return `<div class="sw" style="background:${hex};color:${light ? "#111" : "#fff"}"><b>${n}</b><span>${hex}</span></div>`;
  }).join("");
  root.innerHTML = `
  <div class="board">
    <div class="left">
      <canvas id="bgc"></canvas>
      <div class="num">DESIGN 0${T.n}</div>
      <div class="logo-pair">${T.logos.map((_, i) => `<div class="lg">${logo(i, 64)}</div>`).join("")}</div>
      <div class="head bigname">${T.name}</div>
      <div class="tag">${T.tagline}</div>
      <div style="margin-top:auto" class="tag"><b style="color:var(--text)">Background</b><br>${T.bgName}.</div>
      <div class="mono" style="font-size:11px;color:var(--muted)">${T.fonts.ui.replace(/'/g, "")}${T.fonts.head !== T.fonts.ui ? " + " + T.fonts.head.replace(/'/g, "") : ""} · ${T.fonts.mono.replace(/'/g, "")}</div>
    </div>
    <div class="right">
      <div><h4>Colour</h4><div class="swatches">${sw}</div></div>
      <div class="type"><h4>Type</h4>
        <div class="head t1">Play smarter, see clearer.</div>
        <div class="head t2" style="font-weight:600">Mods · Theme · Packs · Settings</div>
        <div class="t3">Body text stays calm and readable. Zoom, keystrokes, armor status and fourteen more mods, all in one clean menu that never gets in the way of the game.</div>
        <div class="t4">FPS 240 · CPS 12 | 9 · XYZ 128 64 -310 · v1.0.0</div>
      </div>
      <div class="comps"><div>
        <h4>Components</h4>
        <div class="group" style="margin-bottom:12px"><span class="btn primary">${ic("play")}Play</span><span class="btn">${ic("settings")}Options</span><span class="btn ghost">Cancel</span></div>
        <div class="group" style="margin-bottom:12px"><span class="chip on">All <span class="n">17</span></span><span class="chip">HUD <span class="n">11</span></span><span class="chip">Utility <span class="n">3</span></span><span class="chip">Visual <span class="n">3</span></span></div>
        <div class="group"><span class="toggle on"></span><span class="toggle"></span><div class="input" style="width:220px">${ic("search")}Search mods</div></div>
      </div><div>
        <h4>Card</h4>
        <div class="minicard">${ic("zoom-in")}<div><div style="font-weight:600">Zoom</div><div class="muted" style="font-size:12px">Hold C to zoom in</div></div><span class="toggle on" style="margin-left:auto"></span></div>
        <div class="minicard" style="margin-top:10px">${ic("keyboard")}<div><div style="font-weight:600">Keystrokes</div><div class="muted" style="font-size:12px">Show WASD and clicks</div></div><span class="toggle" style="margin-left:auto"></span></div>
      </div></div>
    </div>
  </div>`;
  const c = document.getElementById("bgc");
  drawBg(c, 420, 720, time);
}

// ---------------------------------------------------------------- title screen

function titleScreen() {
  root.innerHTML = `<canvas class="bg" id="bgc"></canvas><div class="title-ui" id="ui"></div>`;
  drawBg(document.getElementById("bgc"), 1280, 720, time);
  const ui = document.getElementById("ui");
  const top = `<div class="topbar"><div class="acct"><div class="avatar">N</div>NafehAwan<span class="dot"></span></div>
    <div class="iconbtns"><div class="iconbtn">${ic("bell")}</div><div class="iconbtn">${ic("settings")}</div><div class="iconbtn">${ic("log-out")}</div></div></div>`;
  const foot = `<div class="foot"><span class="mono">Tuff Client 1.0 · Minecraft 26.3</span><span>Not an official Minecraft product</span></div>`;
  const key = Object.keys(THEMES).find((k) => THEMES[k] === T);

  if (key === "obsidian") {
    ui.innerHTML = top + `
      <div style="position:absolute;left:84px;top:150px">
        <div style="width:58px;height:58px">${logo(0, 58)}</div>
        <div style="margin-top:22px">${wordmark(132, "letter-spacing:-0.06em")}</div>
        <div class="mono" style="margin-top:8px;color:var(--muted);font-size:12px;letter-spacing:0.2em">CLIENT FOR MINECRAFT 26.3</div>
        <div style="margin-top:44px;display:flex;flex-direction:column;gap:4px;width:300px">
          ${["Singleplayer", "Multiplayer", "Tuff Mods", "Options", "Quit"].map((l, i) => `
            <div style="display:flex;align-items:center;justify-content:space-between;height:40px;padding:0 14px;border-radius:8px;font-size:15px;font-weight:${i === 0 ? 600 : 500};
              ${i === 0 ? "background:rgba(255,255,255,0.06);color:var(--text);box-shadow:inset 2px 0 0 var(--accent)" : "color:var(--muted)"}">
              ${l}<span class="mono" style="font-size:11px;opacity:0.6">${i === 0 ? "↵" : "0" + (i + 1)}</span></div>`).join("")}
        </div>
      </div>` + foot;
  } else if (key === "glacier") {
    ui.innerHTML = top + `
      <div style="position:absolute;left:50%;top:50%;transform:translate(-50%,-46%);width:380px;padding:34px 30px 28px;border-radius:24px;
        background:rgba(18,34,62,0.42);border:1px solid rgba(190,230,255,0.22);backdrop-filter:blur(18px);text-align:center;box-shadow:0 30px 80px rgba(0,0,0,0.35)">
        <div style="width:64px;height:64px;margin:0 auto">${logo(0, 64)}</div>
        <div style="margin-top:14px">${wordmark(46, "letter-spacing:0.18em")}</div>
        <div style="color:var(--muted);font-size:12.5px;margin-top:6px;letter-spacing:0.05em">Minecraft 26.3</div>
        <div style="display:flex;flex-direction:column;gap:10px;margin-top:26px">
          <span class="btn primary" style="height:44px;border-radius:14px;font-size:14.5px">${ic("user")}Singleplayer</span>
          <span class="btn" style="height:44px;border-radius:14px;font-size:14.5px;background:rgba(255,255,255,0.08)">${ic("globe")}Multiplayer</span>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
            <span class="btn" style="height:42px;border-radius:14px;background:rgba(255,255,255,0.08)">${ic("layout-grid")}Mods</span>
            <span class="btn" style="height:42px;border-radius:14px;background:rgba(255,255,255,0.08)">${ic("settings")}Options</span>
          </div>
        </div>
      </div>` + foot;
  } else if (key === "ember") {
    ui.innerHTML = top + `
      <div style="position:absolute;left:72px;bottom:96px">
        <div style="display:flex;align-items:center;gap:16px"><div style="width:54px;height:54px">${logo(0, 54)}</div>
        ${wordmark(84)}</div>
        <div style="color:var(--muted);font-size:15px;margin-top:12px;font-weight:300">Good evening, Nafeh. Pick up where you left off.</div>
        <div style="display:flex;gap:10px;margin-top:28px">
          <span class="btn primary" style="height:48px;padding:0 26px;border-radius:14px;font-size:15px">${ic("play")}Singleplayer</span>
          <span class="btn" style="height:48px;padding:0 22px;border-radius:14px;font-size:15px;background:rgba(37,29,27,0.85)">${ic("globe")}Multiplayer</span>
          <span class="btn" style="height:48px;padding:0 22px;border-radius:14px;font-size:15px;background:rgba(37,29,27,0.85)">${ic("layout-grid")}Mods</span>
          <span class="btn" style="height:48px;width:48px;padding:0;border-radius:14px;background:rgba(37,29,27,0.85)">${ic("settings")}</span>
        </div>
      </div>` + foot;
  } else if (key === "prism") {
    ui.innerHTML = top + `
      <div style="position:absolute;left:0;right:0;top:170px;text-align:center">
        <div style="width:60px;height:60px;margin:0 auto 18px">${logo(1, 60)}</div>
        ${wordmark(104, "letter-spacing:0.04em")}
        <div style="margin-top:12px;font-size:13px;letter-spacing:0.4em;color:var(--muted);font-weight:500">CLIENT</div>
        <div style="display:flex;gap:12px;justify-content:center;margin-top:46px">
          <span class="btn primary" style="height:50px;padding:0 34px;border-radius:999px;font-size:15px;box-shadow:0 10px 40px rgba(139,92,246,0.45)">${ic("play")}Play</span>
          <span class="btn" style="height:50px;padding:0 28px;border-radius:999px;font-size:15px;background:rgba(31,26,51,0.8)">${ic("globe")}Servers</span>
          <span class="btn" style="height:50px;padding:0 28px;border-radius:999px;font-size:15px;background:rgba(31,26,51,0.8)">${ic("layout-grid")}Mods</span>
          <span class="btn" style="height:50px;width:50px;padding:0;border-radius:999px;background:rgba(31,26,51,0.8)">${ic("settings")}</span>
        </div>
      </div>` + foot;
  } else {
    ui.innerHTML = top + `
      <div style="position:absolute;left:50%;top:${T.frost ? 92 : 116}px;transform:translateX(-50%);text-align:center;width:${T.frost ? 420 : 520}px;${T.frost ? FROST + ";border-radius:28px;padding:30px 40px 34px" : ""}">
        <div style="width:56px;height:56px;margin:0 auto 6px">${logo(0, 56)}</div>
        ${wordmark(96, "color:#1F2A22;display:block;padding-bottom:14px")}
        <div style="font-size:14px;color:#47524A;margin-top:4px">A calmer way to play.</div>
        <div style="display:flex;flex-direction:column;gap:10px;width:300px;margin:34px auto 0">
          <span class="btn primary" style="height:46px;border-radius:12px;font-size:15px">${ic("play")}Singleplayer</span>
          <span class="btn" style="height:46px;border-radius:12px;font-size:15px;${T.frost ? FROST_BTN : "background:rgba(255,255,255,0.88)"}">${ic("globe")}Multiplayer</span>
          <div style="display:grid;grid-template-columns:1fr 1fr;gap:10px">
            <span class="btn" style="height:42px;border-radius:12px;${T.frost ? FROST_BTN : "background:rgba(255,255,255,0.88)"}">${ic("layout-grid")}Mods</span>
            <span class="btn" style="height:42px;border-radius:12px;${T.frost ? FROST_BTN : "background:rgba(255,255,255,0.88)"}">${ic("settings")}Options</span>
          </div>
        </div>
      </div>` + foot.replace('class="foot"', 'class="foot" style="color:rgba(255,255,255,0.85)"');
  }
}

// ---------------------------------------------------------------- mod menu

function modsMenu() {
  const key = Object.keys(THEMES).find((k) => THEMES[k] === T);
  root.innerHTML = `<div class="game" style="${T.frost ? "filter:blur(1.5px) brightness(0.95)" : ""}"></div><div class="scrim" style="${T.frost ? "background:rgba(255,255,255,0.06)" : T.light ? "background:rgba(246,244,238,0.25)" : ""}"></div><div id="ui" style="position:absolute;inset:0"></div>`;
  const ui = document.getElementById("ui");
  const chips = (onIdx = 0) => ["All", "HUD", "Utility", "Visual", "Favorites"].map((c, i) => `<span class="chip ${i === onIdx ? "on" : ""}">${c}${i < 4 ? ` <span class="n">${[17, 11, 3, 3][i]}</span>` : ""}</span>`).join("");

  if (key === "obsidian") {
    // split: category list left, flat tiles right
    const cats = [["All mods", 17, "layers"], ["HUD", 11, "monitor"], ["Utility", 3, "zap"], ["Visual", 3, "sparkles"], ["Favorites", 2, "heart"]];
    ui.innerHTML = `<div style="position:absolute;left:110px;top:70px;width:1060px;height:580px;border-radius:12px;background:rgba(10,10,11,0.94);border:1px solid var(--border);display:grid;grid-template-columns:220px 1fr;overflow:hidden">
      <div style="border-right:1px solid var(--border);padding:22px 14px;display:flex;flex-direction:column;gap:2px">
        <div style="display:flex;align-items:center;gap:10px;padding:0 8px 18px"><div style="width:26px;height:26px">${logo(0, 26)}</div>${wordmark(22)}</div>
        ${cats.map(([n, c, i], k) => `<div style="display:flex;align-items:center;gap:10px;height:36px;padding:0 10px;border-radius:6px;${k === 0 ? "background:rgba(255,255,255,0.06);color:var(--text)" : "color:var(--muted)"}">${ic(i)}<span style="font-weight:500">${n}</span><span class="mono" style="margin-left:auto;font-size:11px;opacity:.6">${c}</span></div>`).join("")}
        <div style="margin-top:auto;padding:0 10px;color:var(--muted);font-size:12px;display:flex;flex-direction:column;gap:12px">
          <div style="display:flex;gap:10px;align-items:center">${ic("palette")}Theme</div><div style="display:flex;gap:10px;align-items:center">${ic("package")}Packs</div><div style="display:flex;gap:10px;align-items:center">${ic("move")}Edit HUD</div>
        </div>
      </div>
      <div style="padding:22px 24px;display:flex;flex-direction:column;gap:18px">
        <div style="display:flex;align-items:center;gap:14px"><div class="head" style="font-size:24px">All mods</div><span class="mono muted" style="font-size:12px">9 of 17 on</span>
          <div class="input" style="margin-left:auto;width:260px">${ic("search")}Search<span class="mono" style="margin-left:auto;font-size:10.5px;border:1px solid var(--border);padding:1px 6px;border-radius:4px">/</span></div></div>
        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:1px;background:var(--border);border:1px solid var(--border);border-radius:10px;overflow:hidden">
          ${MODS.map(([n, i, d, on]) => `<div style="background:#0E0E10;height:124px;padding:16px;display:flex;flex-direction:column">
            <div style="display:flex;justify-content:space-between;align-items:start"><span class="ic" style="width:22px;height:22px;color:${on ? "var(--text)" : "#4A4A50"}">${ICONS[i]}</span>
            <span style="width:28px;height:16px;border-radius:4px;background:${on ? "var(--accent)" : "#232327"};position:relative"><span style="position:absolute;top:3px;${on ? "right:3px" : "left:3px"};width:10px;height:10px;border-radius:2px;background:${on ? "#0A0A0B" : "#5A5A60"}"></span></span></div>
            <div style="margin-top:auto;font-weight:600;font-size:13.5px;color:${on ? "var(--text)" : "#9A9AA0"}">${n}</div><div class="muted" style="font-size:11.5px;margin-top:2px">${d}</div></div>`).join("")}
        </div>
      </div></div>`;
  } else if (key === "glacier") {
    ui.innerHTML = `<div style="position:absolute;left:150px;top:64px;width:980px;height:592px;border-radius:28px;background:rgba(14,28,52,0.55);border:1px solid rgba(190,230,255,0.2);backdrop-filter:blur(24px);padding:24px 26px;box-shadow:0 40px 100px rgba(0,0,0,0.4)">
      <div style="display:flex;align-items:center;gap:14px">
        <div style="width:30px;height:30px">${logo(0, 30)}</div><div class="head" style="font-size:20px;letter-spacing:0.12em">MODS</div>
        <div style="display:flex;gap:4px;margin-left:24px;padding:4px;border-radius:999px;background:rgba(255,255,255,0.06)">${chips(0)}</div>
        <div class="input" style="margin-left:auto;width:220px;border-radius:999px;background:rgba(255,255,255,0.06)">${ic("search")}Search</div>
        <div class="iconbtn" style="background:rgba(255,255,255,0.06)">${ic("x")}</div>
      </div>
      <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-top:22px">
        ${MODS.map(([n, i, d, on]) => `<div style="height:142px;border-radius:20px;background:${on ? "rgba(125,211,252,0.12)" : "rgba(255,255,255,0.05)"};border:1px solid ${on ? "rgba(125,211,252,0.35)" : "rgba(255,255,255,0.08)"};padding:16px;display:flex;flex-direction:column">
          <div style="display:flex;justify-content:space-between"><div style="width:40px;height:40px;border-radius:12px;background:${on ? "rgba(125,211,252,0.18)" : "rgba(255,255,255,0.06)"};display:grid;place-items:center;color:${on ? "var(--accent)" : "var(--muted)"}">${ic(i)}</div><span class="toggle ${on ? "on" : ""}"></span></div>
          <div style="margin-top:auto;font-weight:700;font-size:14px">${n}</div><div class="muted" style="font-size:12px;margin-top:3px">${d}</div></div>`).join("")}
      </div></div>`;
  } else if (key === "ember") {
    const side = [["layout-grid", 1], ["palette", 0], ["package", 0], ["settings", 0], ["move", 0]];
    ui.innerHTML = `<div style="position:absolute;left:120px;top:66px;width:1040px;height:588px;display:flex;gap:12px">
      <div style="width:64px;border-radius:20px;background:rgba(20,16,16,0.92);border:1px solid var(--border);display:flex;flex-direction:column;align-items:center;padding:14px 0;gap:8px">
        <div style="width:34px;height:34px;margin-bottom:10px">${logo(0, 34)}</div>
        ${side.map(([i, on]) => `<div style="width:42px;height:42px;border-radius:14px;display:grid;place-items:center;${on ? "background:linear-gradient(135deg,#FF7A45,#FF4D6D);color:#1A0B08" : "color:var(--muted)"}">${ic(i)}</div>`).join("")}
      </div>
      <div style="flex:1;border-radius:24px;background:rgba(20,16,16,0.92);border:1px solid var(--border);padding:22px 24px">
        <div style="display:flex;align-items:center;gap:10px"><div class="head" style="font-size:26px">Mods</div><span class="muted" style="font-size:13px;margin-left:6px">9 enabled</span>
          <div class="input" style="margin-left:auto;width:240px;border-radius:12px">${ic("search")}Search mods</div></div>
        <div style="display:flex;gap:4px;margin-top:14px">${chips(0)}</div>
        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:12px;margin-top:16px">
          ${MODS.map(([n, i, d, on]) => `<div style="height:126px;border-radius:16px;background:${on ? "linear-gradient(160deg,rgba(255,122,69,0.16),rgba(37,29,27,0.9) 60%)" : "#1C1615"};border:1px solid ${on ? "rgba(255,122,69,0.3)" : "var(--border)"};padding:14px;display:flex;flex-direction:column">
            <div style="display:flex;justify-content:space-between;align-items:center"><span class="ic" style="width:24px;height:24px;color:${on ? "#FFB08A" : "#6E5A54"}">${ICONS[i]}</span><span class="toggle ${on ? "on" : ""}"></span></div>
            <div style="margin-top:auto;font-weight:600;font-size:14px">${n}</div><div class="muted" style="font-size:12px;margin-top:2px">${d}</div></div>`).join("")}
        </div></div></div>`;
  } else if (key === "prism") {
    ui.innerHTML = `<div style="position:absolute;left:120px;top:56px;width:1040px;height:608px;border-radius:22px;background:rgba(14,11,26,0.9);border:1px solid var(--border);overflow:hidden">
      <div style="height:64px;display:flex;align-items:center;gap:18px;padding:0 22px;border-bottom:1px solid var(--border);background:linear-gradient(90deg,rgba(139,92,246,0.12),rgba(236,72,153,0.06))">
        <div style="width:30px;height:30px">${logo(1, 30)}</div>${wordmark(20, "letter-spacing:0.06em")}
        <div style="display:flex;gap:6px;margin-left:20px">${["Mods", "Theme", "Packs", "Settings", "HUD"].map((x, k) => `<span style="height:34px;padding:0 16px;border-radius:999px;display:inline-flex;align-items:center;font-weight:600;${k === 0 ? "background:linear-gradient(120deg,#8B5CF6,#EC4899);color:#fff" : "color:var(--muted)"}">${x}</span>`).join("")}</div>
        <div class="input" style="margin-left:auto;width:220px;border-radius:999px">${ic("search")}Search mods</div>
      </div>
      <div style="padding:18px 22px">
        <div style="display:flex;gap:4px">${chips(0)}</div>
        <div style="display:grid;grid-template-columns:repeat(4,1fr);gap:14px;margin-top:16px">
          ${MODS.map(([n, i, d, on]) => `<div style="height:130px;border-radius:16px;padding:1px;background:${on ? "linear-gradient(135deg,#8B5CF6,#EC4899)" : "var(--border)"}"><div style="height:100%;border-radius:15px;background:#161226;padding:14px;display:flex;flex-direction:column;align-items:center;text-align:center">
            <span class="ic" style="width:30px;height:30px;margin-top:6px;color:${on ? "#E9D5FF" : "#5B5478"}">${ICONS[i]}</span>
            <div style="margin-top:10px;font-weight:600;font-size:14px">${n}</div>
            <div style="margin-top:auto;font:600 10.5px var(--ui);letter-spacing:0.12em;color:${on ? "#F0ABFC" : "var(--muted)"}">${on ? "● ENABLED" : "○ DISABLED"}</div></div></div>`).join("")}
        </div></div></div>`;
  } else {
    const fr = !!T.frost;
    ui.innerHTML = `<div style="position:absolute;left:170px;top:58px;width:940px;height:604px;border-radius:${fr ? 26 : 20}px;${fr ? FROST.replace("0.38", "0.52") : "background:#FBFAF6;border:1px solid var(--border);box-shadow:0 30px 80px rgba(30,40,30,0.25)"};padding:26px 30px;color:var(--text)">
      <div style="display:flex;align-items:center;gap:14px">
        <div style="width:32px;height:32px">${logo(0, 32)}</div><div class="head" style="font-size:30px;font-style:italic">Mods</div>
        <div style="display:flex;gap:4px;margin-left:18px">${chips(0).replace(/chip on/g, `chip on" style="background:${fr ? "rgba(255,255,255,0.7)" : "#E9F1EA"};color:#2F6B4F`)}</div>
        <div class="input" style="margin-left:auto;width:230px;background:${fr ? "rgba(255,255,255,0.5)" : "#F1EEE6"};border-color:${fr ? "rgba(255,255,255,0.8)" : "transparent"}">${ic("search")}Search mods</div>
      </div>
      <div style="display:grid;grid-template-columns:1fr 1fr;column-gap:26px;margin-top:20px">
        ${MODS.map(([n, i, d, on]) => `<div style="display:flex;align-items:center;gap:14px;height:66px;border-bottom:1px solid ${fr ? "rgba(255,255,255,0.6)" : "var(--border)"}">
          <div style="width:40px;height:40px;border-radius:12px;background:${on ? (fr ? "rgba(228,239,230,0.85)" : "#E4EFE6") : (fr ? "rgba(255,255,255,0.5)" : "#F1EEE6")};display:grid;place-items:center;color:${on ? "#2F6B4F" : "#9AA39B"}">${ic(i)}</div>
          <div><div style="font-weight:600;font-size:14px">${n}</div><div class="muted" style="font-size:12px">${d}</div></div>
          <span class="toggle ${on ? "on" : ""}" style="margin-left:auto;${on ? "" : fr ? "background:rgba(255,255,255,0.6);border-color:rgba(255,255,255,0.8)" : "background:#E7E3D9"}"></span></div>`).join("")}
      </div></div>`;
  }
}

// ---------------------------------------------------------------- launcher

function launcher() {
  const key = Object.keys(THEMES).find((k) => THEMES[k] === T);
  const navs = ["house", "library", "box", "server", "shirt", "camera"];
  root.innerHTML = (T.frost ? `<canvas class="bg" id="bgfull"></canvas>` : "") + `<div class="app" style="${T.frost ? "background:transparent" : ""}">
    <div class="side" style="${T.frost ? "background:rgba(255,255,255,0.32);backdrop-filter:blur(20px) saturate(1.4);border-right:1px solid rgba(255,255,255,0.6)" : ""}"><div class="lg">${logo(key === "prism" ? 1 : 0, 34)}</div>
      ${navs.map((n, i) => `<div class="nav ${i === 0 ? "on" : ""}">${ic(n)}</div>`).join("")}
      <div style="margin-top:auto" class="nav">${ic("terminal")}</div><div class="nav">${ic("settings")}</div></div>
    <div class="main">
      <div class="hero">${T.frost ? "" : `<canvas id="bgc"></canvas>`}</div>
      <div class="winbar"><div class="acct" style="height:34px"><div class="avatar" style="width:26px;height:26px">N</div>NafehAwan<span class="mono muted" style="font-size:11px;margin-left:4px">Microsoft</span>${ic("chevron-down")}</div></div>
      <div style="position:absolute;left:44px;top:${key === "prism" || key === "glacier" || T.light ? 200 : 230}px;${key === "prism" || key === "glacier" || T.light ? "right:340px;text-align:center" : ""}">
        ${key === "obsidian" ? `<div class="mono" style="color:var(--accent);font-size:12px;letter-spacing:0.2em;margin-bottom:12px">● READY TO PLAY</div>` : ""}
        ${wordmark(key === "obsidian" ? 110 : 88, T.light ? "color:#1F2A22" : "")}
        <div style="margin-top:12px;font-size:15px;color:${T.light ? "#3A463D" : "var(--muted)"}">Good evening, <b style="color:${T.light ? "#1F2A22" : "var(--text)"}">NafehAwan</b></div>
      </div>
      <div class="news">
        <div class="newscard"><div class="k">What's new</div><div class="tt">Tuff 1.0: new mod menu</div><div class="ss">Favorites, live HUD previews and a faster title screen.</div></div>
        <div class="newscard"><div class="k">Quick join</div>
          ${[["Hypixel", "48K"], ["Wynncraft", "12K"], ["Local test", "off"]].map(([n, p]) => `<div class="srv">${ic("globe")}${n}<span class="p">${p}</span></div>`).join("")}</div>
      </div>
      <div class="playbar" style="left:calc(50% - 150px)">
        <div class="prof"><div class="pi">${ic("box")}</div><div><div style="font-weight:600">Tuff · Fabric 26.3</div><div class="muted" style="font-size:12px">14h 21m played</div></div>${ic("chevron-down", "muted")}</div>
        <span class="btn primary play">${ic("play")}Play</span>
      </div>
    </div></div>`;
  if (T.frost) drawBg(document.getElementById("bgfull"), 1280, 720, time);
  else drawBg(document.getElementById("bgc"), 1212, 720, time);
}

// ---------------------------------------------------------------- logos sheet

function logos() {
  document.body.style.background = "#101012";
  const cells = [];
  for (const [k, th] of Object.entries(THEMES)) {
    th.logos.forEach((fn, i) => {
      if (th.frost) return;
      const lightStage = k === "grove";
      cells.push(`<div class="lcell">
        <div class="stage" style="background:${lightStage ? "#F6F4EE" : th.c.bg}">${LOGO_SVG(fn, 96)}</div>
        <div class="stage" style="height:70px;background:${lightStage ? "#1F2A22" : "#F2F2F0"};display:flex;gap:18px;justify-content:center;align-items:center">
          ${LOGO_SVG(fn, 40, k === "obsidian" ? "#0A0A0B" : undefined)}${LOGO_SVG(fn, 24, k === "obsidian" ? "#0A0A0B" : undefined)}${LOGO_SVG(fn, 16, k === "obsidian" ? "#0A0A0B" : undefined)}</div>
        <div class="meta"><span><b>${th.n}${"AB"[i]}</b> · ${th.logoNames[i]}</span><span style="color:#8A8A90">${th.name}</span></div></div>`);
    });
  }
  // order: A row then B row
  const a = cells.filter((_, i) => i % 2 === 0), b = cells.filter((_, i) => i % 2 === 1);
  root.innerHTML = `<div class="logos"><h1>Tuff logo concepts</h1><div class="sub">Ten marks, two per design. Shown large, then at 40, 24 and 16 px on the opposite background. Any logo can pair with any design.</div>
    <div class="lgrid">${a.join("")}${b.join("")}</div></div>`;
}

function overview() {
  document.body.style.background = "#0D0D0F";
  const rows = [["title", "Title screen"], ["menu", "Mod menu"], ["launcher", "Launcher"]];
  const keys = Object.keys(THEMES);
  root.innerHTML = `<div style="position:absolute;inset:0;padding:22px 26px;font-family:'Geist Sans';color:#EEE">
    <div style="display:flex;align-items:baseline;gap:14px"><div style="font-size:24px;font-weight:700;letter-spacing:-0.03em">Tuff Client: 5 design systems + Grove Glass</div><div style="color:#8A8A90;font-size:13px">Pick one by number. Each has its own colours, fonts, logo and animated background.</div></div>
    <div style="display:grid;grid-template-columns:90px repeat(${keys.length},1fr);gap:10px;margin-top:16px;align-items:center">
      <div></div>${keys.map((k) => `<div style="display:flex;align-items:center;gap:8px">${LOGO_SVG(THEMES[k].logos[0], 22, k === "obsidian" ? undefined : undefined)}<b style="font-size:14px">${THEMES[k].n} · ${THEMES[k].name}</b></div>`).join("")}
      ${rows.map(([s, label]) => `<div style="color:#8A8A90;font-size:12px">${label}</div>` + keys.map((k, i) => `<img src="out/0${i + 1}-${k}-${s}.png" style="width:100%;border-radius:8px;border:1px solid rgba(255,255,255,0.08);display:block">`).join("")).join("")}
      <div style="color:#8A8A90;font-size:12px">Fonts</div>${keys.map((k) => `<div style="font-size:11.5px;color:#B4B4BA">${THEMES[k].fonts.ui.replace(/'/g, "")}${THEMES[k].fonts.head !== THEMES[k].fonts.ui ? " + " + THEMES[k].fonts.head.replace(/'/g, "") : ""}</div>`).join("")}
      <div style="color:#8A8A90;font-size:12px">Palette</div>${keys.map((k) => `<div style="display:flex;gap:4px">${THEMES[k].swatches.map(([, h]) => `<span style="flex:1;height:16px;border-radius:4px;background:${h};border:1px solid rgba(255,255,255,0.1)"></span>`).join("")}</div>`).join("")}
    </div></div>`;
}

function motion() {
  const key = Object.keys(THEMES).find((k) => THEMES[k] === T);
  document.body.style.background = "#0D0D0F";
  root.innerHTML = `<div style="position:absolute;inset:0;padding:30px;font-family:'Geist Sans';color:#EEE">
    <div style="font-size:22px;font-weight:700;letter-spacing:-0.03em">${T.n} · ${T.name}: animated background</div>
    <div style="color:#8A8A90;font-size:13px;margin-top:4px">${T.bgName}. Three moments, 4 seconds apart.</div>
    <div style="display:grid;grid-template-columns:repeat(3,1fr);gap:12px;margin-top:22px">
      ${[0, 4, 8].map((s) => `<div><img src="out/0${T.n}-${key}-motion-${s}.png" style="width:100%;border-radius:10px;display:block"><div class="mono" style="color:#8A8A90;font-size:11px;margin-top:6px">t = ${s}s</div></div>`).join("")}
    </div></div>`;
}

applyTheme();
({ board, title: titleScreen, menu: modsMenu, launcher, logos, overview, motion })[screen]();
document.fonts.ready.then(() => { document.body.dataset.ready = "1"; });
