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
      faces[name] = { base, hat, flat: flat(img) };
      resolve();
    };
    img.src = src;
  })));

  const skinOf = (kind) => faces[kind === "sample" ? "explorer" : kind] || faces.default;

  // flat head for chips and lists
  window.HEAD = (kind, size) => `<img src="${skinOf(kind).flat}" width="${size}" height="${size}" style="image-rendering:pixelated;border-radius:${Math.max(2, Math.round(size * 0.12))}px;display:block;flex:none">`;

  function cube(set, size, shade) {
    const h = size / 2;
    const side = (img, tf, dark) => img ? `<div style="position:absolute;left:0;top:0;width:${size}px;height:${size}px;transform:${tf};backface-visibility:hidden">
        <img src="${img}" style="width:100%;height:100%;image-rendering:pixelated;display:block">
        ${shade && dark ? `<div style="position:absolute;inset:0;background:rgba(0,0,0,${dark});-webkit-mask:url(${img}) center/100% 100%;mask:url(${img}) center/100% 100%"></div>` : ""}</div>` : "";
    return side(set.front, `translateZ(${h}px)`, 0)
      + side(set.back, `rotateY(180deg) translateZ(${h}px)`, 0.28)
      + side(set.right, `rotateY(-90deg) translateZ(${h}px)`, 0.16)
      + side(set.left, `rotateY(90deg) translateZ(${h}px)`, 0.16)
      + side(set.top, `rotateX(90deg) translateZ(${h}px)`, 0)
      + side(set.bottom, `rotateX(-90deg) translateZ(${h}px)`, 0.35);
  }

  // 3D head: base cube plus the hat cube around it
  window.HEAD3D = (kind, size, yaw, pitch = -18) => {
    const f = skinOf(kind), hat = size * 1.125, off = (size - hat) / 2;
    return `<div style="width:${size}px;height:${size}px;perspective:${size * 14}px">
      <div style="position:relative;width:${size}px;height:${size}px;transform-style:preserve-3d;transform:rotateX(${pitch}deg) rotateY(${yaw}deg)">
        ${cube(f.base, size, true)}
        <div style="position:absolute;left:${off}px;top:${off}px;width:${hat}px;height:${hat}px;transform-style:preserve-3d">${cube(f.hat, hat, true)}</div>
      </div></div>`;
  };
})();
