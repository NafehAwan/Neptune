// Renders 3D skin heads with three.js: the 8x8x8 base head textured from the skin,
// and the hat layer as real voxels (every opaque outer-layer pixel is its own little
// block standing off the head), like the "3D skin layers" look in game.
(function () {
  const T = window.THREE;
  // face regions: [x, y] in the skin, base layer; hat layer is +32 in x
  const FACES = {
    px: [16, 8], nx: [0, 8], py: [8, 0], ny: [16, 0], pz: [8, 8], nz: [24, 8],
  };
  const ORDER = ["px", "nx", "py", "ny", "pz", "nz"]; // BoxGeometry material order

  function pixels(img) {
    const c = document.createElement("canvas");
    c.width = c.height = 64;
    const g = c.getContext("2d");
    g.drawImage(img, 0, 0);
    return g.getImageData(0, 0, 64, 64).data;
  }

  function faceTexture(img, x, y, flipY) {
    const c = document.createElement("canvas");
    c.width = c.height = 8;
    const g = c.getContext("2d");
    if (flipY) {
      g.translate(0, 8);
      g.scale(1, -1);
    }
    g.drawImage(img, x, y, 8, 8, 0, 0, 8, 8);
    const tex = new T.CanvasTexture(c);
    tex.magFilter = T.NearestFilter;
    tex.minFilter = T.NearestFilter;
    tex.colorSpace = T.SRGBColorSpace;
    return tex;
  }

  function buildHead(img) {
    const group = new T.Group();
    const mats = ORDER.map((k) => new T.MeshLambertMaterial({ map: faceTexture(img, FACES[k][0], FACES[k][1], k === "ny") }));
    group.add(new T.Mesh(new T.BoxGeometry(8, 8, 8), mats));

    const data = pixels(img);
    const voxels = [];
    for (const k of ORDER) {
      const [fx, fy] = FACES[k];
      for (let v = 0; v < 8; v++) {
        for (let u = 0; u < 8; u++) {
          const sx = fx + 32 + u, sy = fy + (k === "ny" ? 7 - v : v);
          const i = (sy * 64 + sx) * 4;
          if (data[i + 3] < 128) continue;
          let p;
          // one block per pixel, standing half a pixel off the head like 3D skin layers
          const s = 1.0, a = (u - 3.5) * s, c = (v - 3.5) * s, d = 4.5;
          if (k === "pz") p = [a, -c, d];
          else if (k === "nz") p = [-a, -c, -d];
          else if (k === "px") p = [d, -c, -a];
          else if (k === "nx") p = [-d, -c, a];
          else if (k === "py") p = [a, d, c];
          else p = [a, -d, -c];
          voxels.push({ p, axis: k[1], color: new T.Color(`rgb(${data[i]},${data[i + 1]},${data[i + 2]})`) });
        }
      }
    }
    if (voxels.length) {
      const box = new T.BoxGeometry(1, 1, 1);
      const mesh = new T.InstancedMesh(box, new T.MeshLambertMaterial(), voxels.length);
      const m = new T.Matrix4(), q = new T.Quaternion(), scale = new T.Vector3();
      voxels.forEach((vx, i) => {
        const s = 1, depth = 1;
        scale.set(vx.axis === "x" ? depth : s, vx.axis === "y" ? depth : s, vx.axis === "z" ? depth : s);
        m.compose(new T.Vector3(...vx.p), q, scale);
        mesh.setMatrixAt(i, m);
        mesh.setColorAt(i, vx.color);
      });
      group.add(mesh);
    }
    return group;
  }

  const heads = {};
  let renderer;

  window.HEADS3D_MOUNT = () => {
    const canvases = document.querySelectorAll("canvas[data-head3d]");
    canvases.forEach((cv) => {
      const kind = cv.dataset.head3d;
      if (!heads[kind]) heads[kind] = buildHead(SKIN_IMAGE(kind));
      const r = new T.WebGLRenderer({ canvas: cv, alpha: true, antialias: true, preserveDrawingBuffer: true });
      r.outputColorSpace = T.SRGBColorSpace;
      const scene = new T.Scene();
      scene.add(new T.AmbientLight(0xffffff, 1.6));
      const sun = new T.DirectionalLight(0xffffff, 1.9);
      sun.position.set(-4, 8, 10);
      scene.add(sun);
      const head = heads[kind].clone();
      head.rotation.order = "XYZ";
      head.rotation.x = (-(+cv.dataset.pitch) * Math.PI) / 180;
      head.rotation.y = ((+cv.dataset.yaw) * Math.PI) / 180;
      // rotate around the vertical axis first, then tilt towards the camera
      const holder = new T.Group();
      const spin = new T.Group();
      spin.rotation.y = ((+cv.dataset.yaw) * Math.PI) / 180;
      head.rotation.set(0, 0, 0);
      spin.add(head);
      holder.rotation.x = ((-(+cv.dataset.pitch)) * Math.PI) / 180;
      holder.add(spin);
      scene.add(holder);
      const cam = new T.PerspectiveCamera(24, 1, 1, 200);
      cam.position.set(0, 0, 37);
      cam.lookAt(0, 0, 0);
      r.render(scene, cam);
    });
  };
})();
