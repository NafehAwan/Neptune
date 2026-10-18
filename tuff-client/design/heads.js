// Minecraft-style 8x8 skin heads for the account mockups, drawn to canvases.
// In the real client the head comes from the account's skin (or the game's own
// default skin when there is none); these stand-ins only show the layout.
(function () {
  // palette keys -> colours
  const PAL = {
    h: "#3B2A1E", H: "#2A1D14", s: "#C69C7A", S: "#B5876A", w: "#F2F2F2", e: "#3D5FA8",
    n: "#9C6B50", m: "#6E4A39", g: "#2F6B4F", G: "#24543E", k: "#1B1B1B", b: "#5B3B28", p: "#E7B48F",
  };
  // default head (shown when an account has no skin)
  const DEFAULT = {
    face: ["hhhhhhhh", "hhhhhhhh", "hssssssh", "ssssssss", "swessews", "sssnnsss", "ssmmmmss", "ssmssmss"],
    top: ["hhhhhhhh", "hHhhhhHh", "hhhhhhhh", "hhhHhhhh", "hhhhhhhh", "hHhhhhhh", "hhhhhHhh", "hhhhhhhh"],
    side: ["hhhhhhhh", "hhhhhhhh", "hhhhhhss", "hhhhssss", "hhssssss", "ssssssss", "ssssssss", "ssssssss"],
    back: ["hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hhhhhhhh", "hHhhhhhh", "hhhhhhhh", "hhhhhhhh"],
  };
  // a sample custom skin: dark hair, green hood
  const SAMPLE = {
    face: ["gggggggg", "gkkkkkkg", "gkppppkg", "gppppppg", "gpwkpwkg", "gppnnppg", "GppbbppG", "GGppppGG"],
    top: ["gggggggg", "gGggggGg", "gggggggg", "ggggGggg", "gggggggg", "gGgggggg", "ggggggGg", "gggggggg"],
    side: ["gggggggg", "gggggggg", "ggggggkk", "ggggggpp", "ggggggpp", "ggggggpp", "GGGGGGGp", "GGGGGGGG"],
    back: ["gggggggg", "gggggggg", "gGgggggg", "gggggggg", "ggggGggg", "gggggggg", "GGGGGGGG", "GGGGGGGG"],
  };

  function paint(rows, scale = 1) {
    const c = document.createElement("canvas");
    c.width = c.height = 8 * scale;
    const g = c.getContext("2d");
    rows.forEach((row, y) => [...row].forEach((ch, x) => {
      g.fillStyle = PAL[ch] || "#000";
      g.fillRect(x * scale, y * scale, scale, scale);
    }));
    return c.toDataURL();
  }

  const cache = {};
  function faces(kind) {
    if (!cache[kind]) {
      const src = kind === "default" ? DEFAULT : SAMPLE;
      cache[kind] = { face: paint(src.face), top: paint(src.top), side: paint(src.side), back: paint(src.back) };
    }
    return cache[kind];
  }

  // flat head for chips and lists
  window.HEAD = (kind, size) => `<img src="${faces(kind).face}" width="${size}" height="${size}" style="image-rendering:pixelated;border-radius:${Math.round(size * 0.18)}px;display:block;flex:none">`;

  // 3D head: a CSS cube with all six faces
  window.HEAD3D = (kind, size, yaw, pitch = -18) => {
    const f = faces(kind), half = size / 2;
    const face = (img, tf, shade) => `<div style="position:absolute;inset:0;transform:${tf};backface-visibility:hidden">
      <img src="${img}" style="width:100%;height:100%;image-rendering:pixelated;display:block">
      <div style="position:absolute;inset:0;background:rgba(0,0,0,${shade})"></div></div>`;
    return `<div style="width:${size}px;height:${size}px;perspective:${size * 14}px">
      <div style="position:relative;width:100%;height:100%;transform-style:preserve-3d;transform:rotateX(${pitch}deg) rotateY(${yaw}deg)">
        ${face(f.face, `translateZ(${half}px)`, 0)}
        ${face(f.back, `rotateY(180deg) translateZ(${half}px)`, 0.25)}
        ${face(f.side, `rotateY(90deg) translateZ(${half}px)`, 0.18)}
        ${face(f.side, `rotateY(-90deg) translateZ(${half}px) scaleX(-1)`, 0.18)}
        ${face(f.top, `rotateX(90deg) translateZ(${half}px)`, -0.05)}
        ${face(f.top, `rotateX(-90deg) translateZ(${half}px)`, 0.35)}
      </div></div>`;
  };
})();
