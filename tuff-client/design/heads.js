// Skin heads cut from real 64x64 Minecraft skins (standard layout), the way the
// client will draw them: the 8x8 face plus the hat layer for flat heads, and all
// six faces of both layers for the 3D head (the hat cube is 1/8 larger).
(function () {
  // head regions in the skin texture: [x, y] of each 8x8 face, base layer; the hat layer is +32 in x
  const UV = { top: [8, 0], bottom: [16, 0], right: [0, 8], front: [8, 8], left: [16, 8], back: [24, 8] };
  const faces = {};

  function cut(img, x, y, flipY = false) {
    const c = document.createElement("canvas");
    c.width = c.height = 8;
    const g = c.getContext("2d");
    if (flipY) {
      g.translate(0, 8);
      g.scale(1, -1);
    }
    g.drawImage(img, x, y, 8, 8, 0, 0, 8, 8);
    // drop a fully empty hat face so it costs nothing to draw
    const px = g.getImageData(0, 0, 8, 8).data;
    let any = false;
    for (let i = 3; i < px.length; i += 4) if (px[i] > 0) any = true;
    return any ? c.toDataURL() : null;
  }

  function flat(img) {
    const c = document.createElement("canvas");
    c.width = c.height = 8;
    const g = c.getContext("2d");
    g.drawImage(img, 8, 8, 8, 8, 0, 0, 8, 8);
    g.drawImage(img, 40, 8, 8, 8, 0, 0, 8, 8);
    return c.toDataURL();
  }

  window.SKINS_READY = Promise.all(Object.entries(window.SKIN_DATA).map(([name, src]) => new Promise((resolve) => {
    const img = new Image();
    img.onload = () => {
      const base = {}, hat = {};
      for (const [k, [x, y]] of Object.entries(UV)) {
        base[k] = cut(img, x, y, k === "bottom");
        hat[k] = cut(img, x + 32, y, k === "bottom");
      }
      faces[name] = { base, hat, flat: flat(img), img };
      resolve();
    };
    img.src = src;
  })));

  const skinOf = (kind) => faces[kind === "sample" ? "explorer" : kind] || faces.default;

  // flat head for chips and lists
  window.HEAD = (kind, size) => `<img src="${skinOf(kind).flat}" width="${size}" height="${size}" style="image-rendering:pixelated;border-radius:${Math.max(2, Math.round(size * 0.12))}px;display:block;flex:none">`;

  // 3D head: a canvas that head3d.js renders with three.js (base head plus voxel hat layer)
  window.HEAD3D = (kind, size, yaw, pitch = -18) =>
    `<canvas data-head3d="${kind === "sample" ? "explorer" : kind}" data-yaw="${yaw}" data-pitch="${pitch}" width="${size * 3}" height="${size * 3}" style="width:${size * 1.5}px;height:${size * 1.5}px;display:block;margin:${-size * 0.25}px"></canvas>`;
  window.SKIN_IMAGE = (kind) => skinOf(kind).img;
})();
