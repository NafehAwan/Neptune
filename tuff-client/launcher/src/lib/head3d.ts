// 3D skin heads. The base 8x8x8 head is textured from the skin, and the outer
// layer is real 3D: every opaque outer-layer pixel is its own block standing off
// the head. One shared WebGL renderer draws every head; each result is copied into
// the caller's 2D canvas, so any number of heads can be on screen.
import * as THREE from "three";

const FACES: Record<string, [number, number]> = {
  px: [16, 8], nx: [0, 8], py: [8, 0], ny: [16, 0], pz: [8, 8], nz: [24, 8],
};
const ORDER = ["px", "nx", "py", "ny", "pz", "nz"]; // BoxGeometry material order

const heads = new Map<string, Promise<THREE.Group>>();
let renderer: THREE.WebGLRenderer | null = null;

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.onload = () => resolve(img);
    img.onerror = reject;
    img.src = src;
  });
}

function faceTexture(img: HTMLImageElement, x: number, y: number, flipY: boolean) {
  const c = document.createElement("canvas");
  c.width = c.height = 8;
  const g = c.getContext("2d")!;
  if (flipY) {
    g.translate(0, 8);
    g.scale(1, -1);
  }
  g.drawImage(img, x, y, 8, 8, 0, 0, 8, 8);
  const tex = new THREE.CanvasTexture(c);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function build(img: HTMLImageElement): THREE.Group {
  const group = new THREE.Group();
  const mats = ORDER.map((k) => new THREE.MeshLambertMaterial({ map: faceTexture(img, FACES[k][0], FACES[k][1], k === "ny") }));
  group.add(new THREE.Mesh(new THREE.BoxGeometry(8, 8, 8), mats));

  // legacy 64x32 skins have no outer layer for the head beyond the hat, which sits at the same place
  const c = document.createElement("canvas");
  c.width = c.height = 64;
  const g = c.getContext("2d")!;
  g.drawImage(img, 0, 0);
  const data = g.getImageData(0, 0, 64, 64).data;

  const voxels: { p: [number, number, number]; axis: string; color: THREE.Color }[] = [];
  for (const k of ORDER) {
    const [fx, fy] = FACES[k];
    for (let v = 0; v < 8; v++) {
      for (let u = 0; u < 8; u++) {
        const sx = fx + 32 + u, sy = fy + (k === "ny" ? 7 - v : v);
        const i = (sy * 64 + sx) * 4;
        if (data[i + 3] < 128) continue;
        const a = u - 3.5, b = v - 3.5, d = 4.5;
        const p: [number, number, number] =
          k === "pz" ? [a, -b, d] : k === "nz" ? [-a, -b, -d] : k === "px" ? [d, -b, -a]
          : k === "nx" ? [-d, -b, a] : k === "py" ? [a, d, b] : [a, -d, -b];
        voxels.push({ p, axis: k[1], color: new THREE.Color(`rgb(${data[i]},${data[i + 1]},${data[i + 2]})`) });
      }
    }
  }
  if (voxels.length) {
    const mesh = new THREE.InstancedMesh(new THREE.BoxGeometry(1, 1, 1), new THREE.MeshLambertMaterial(), voxels.length);
    const m = new THREE.Matrix4(), q = new THREE.Quaternion(), scale = new THREE.Vector3();
    voxels.forEach((vx, i) => {
      scale.set(1, 1, 1);
      m.compose(new THREE.Vector3(...vx.p), q, scale);
      mesh.setMatrixAt(i, m);
      mesh.setColorAt(i, vx.color);
    });
    group.add(mesh);
  }
  return group;
}

function headFor(skin: string) {
  let head = heads.get(skin);
  if (!head) {
    head = loadImage(skin).then(build);
    heads.set(skin, head);
  }
  return head;
}

/** Draws the head for `skin` (a PNG URL or data URL) into `canvas`. Angles are in degrees. */
export async function drawHead(canvas: HTMLCanvasElement, skin: string, yaw: number, pitch: number, distance = 30) {
  const head = await headFor(skin);
  if (!renderer) {
    renderer = new THREE.WebGLRenderer({ alpha: true, antialias: true, preserveDrawingBuffer: true });
    renderer.outputColorSpace = THREE.SRGBColorSpace;
    renderer.setClearColor(0x000000, 0);
  }
  renderer.setSize(canvas.width, canvas.height, false);
  const scene = new THREE.Scene();
  scene.add(new THREE.AmbientLight(0xffffff, 1.6));
  const sun = new THREE.DirectionalLight(0xffffff, 1.9);
  sun.position.set(-4, 8, 10);
  scene.add(sun);
  const spin = new THREE.Group();
  spin.rotation.y = (yaw * Math.PI) / 180;
  spin.add(head.clone());
  const tilt = new THREE.Group();
  tilt.rotation.x = (-pitch * Math.PI) / 180;
  tilt.add(spin);
  scene.add(tilt);
  const cam = new THREE.PerspectiveCamera(24, canvas.width / canvas.height, 1, 200);
  cam.position.set(0, 0, distance);
  cam.lookAt(0, 0, 0);
  renderer.render(scene, cam);
  const g = canvas.getContext("2d");
  if (!g) return;
  g.clearRect(0, 0, canvas.width, canvas.height);
  g.drawImage(renderer.domElement, 0, 0);
}
