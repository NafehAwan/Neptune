// The 3D player on Home: studio lighting, a pose, and "3D skin layers" (every
// pixel of the skin's outer layer becomes its own small block, like the
// 3D Skin Layers mod).
import type { SkinViewer } from "skinview3d";

type Skin = SkinViewer["playerObject"]["skin"];

export const POSES = {
  run(v: SkinViewer) {
    const p = v.playerObject, k = p.skin;
    p.rotation.x = 0.08;
    k.head.rotation.set(0.1, -0.3, 0);
    k.rightArm.rotation.set(0.9, 0, -0.12); k.leftArm.rotation.set(-0.9, 0, 0.12);
    k.rightLeg.rotation.set(-0.8, 0, 0); k.leftLeg.rotation.set(0.8, 0, 0);
  },
};

/** Soft fill, a warm key light from the top left and green/blue rim lights from behind. */
export function studioLights(v: SkinViewer) {
  const Light = v.cameraLight.constructor as new (color: number, intensity: number, distance: number, decay: number) => typeof v.cameraLight;
  v.renderer.toneMapping = 4; // ACES filmic: soft highlights, natural colours
  v.renderer.toneMappingExposure = 1.15;
  v.globalLight.intensity = 1.7;
  v.cameraLight.intensity = 0.7;
  const add = (color: number, intensity: number, x: number, y: number, z: number) => {
    const l = new Light(color, intensity, 0, 1.25);
    l.position.set(x, y, z);
    v.scene.add(l);
  };
  add(0xfff1dc, 650, -30, 34, 40);
  add(0x4dffa0, 1100, 26, 18, -26);
  add(0x9fd8ff, 450, -28, -2, -24);
}

// Where each part's outer layer sits on the 64x64 skin, and the part's size (w, h, d).
const PARTS: Record<string, [number, number, number, number, number]> = {
  head: [32, 0, 8, 8, 8], body: [16, 32, 8, 12, 4],
  rightArm: [40, 32, 4, 12, 4], leftArm: [48, 48, 4, 12, 4],
  rightLeg: [0, 32, 4, 12, 4], leftLeg: [0, 48, 4, 12, 4],
};

/** Replaces the flat outer layer with real blocks. Classic 64x32 skins have no outer layer. */
export async function voxelLayers(v: SkinViewer, url: string, slim: boolean) {
  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const i = new Image(); i.onload = () => resolve(i); i.onerror = reject; i.src = url;
  });
  if (img.width !== 64 || img.height !== 64) return;
  const c = document.createElement("canvas"); c.width = 64; c.height = 64;
  const g = c.getContext("2d", { willReadFrequently: true })!; g.drawImage(img, 0, 0);
  const data = g.getImageData(0, 0, 64, 64).data;
  const at = (x: number, y: number) => { const i = (y * 64 + x) * 4; return data[i + 3] > 20 ? [data[i], data[i + 1], data[i + 2]] : null; };

  const skin: Skin = v.playerObject.skin;
  const base = skin.head.innerLayer as unknown as { constructor: any; geometry: { constructor: any }; material: { constructor: any } };
  const Mesh = base.constructor, Box = base.geometry.constructor, Mat = base.material.constructor;
  const geo = new Box(1.06, 1.06, 1.06);
  const mats = new Map<string, any>();
  const mat = (rgb: number[]) => {
    const key = rgb.join(",");
    let m = mats.get(key);
    if (!m) { m = new Mat(); m.color.setRGB(rgb[0] / 255, rgb[1] / 255, rgb[2] / 255, "srgb"); mats.set(key, m); }
    return m;
  };
  for (const [name, part0] of Object.entries(PARTS)) {
    const [u, w0, W0, H, D] = part0;
    const W = slim && name.endsWith("Arm") ? 3 : W0;
    const vv = w0;
    const part = (skin as any)[name];
    const inner = part.innerLayer, holder = inner.parent, ip = inner.position;
    part.outerLayer.visible = false;
    const add = (rgb: number[] | null, x: number, y: number, z: number) => {
      if (!rgb) return;
      const m = new Mesh(geo, mat(rgb)); m.position.set(ip.x + x, ip.y + y, ip.z + z); holder.add(m);
    };
    const o = 0.5;
    for (let i = 0; i < W; i++) for (let j = 0; j < H; j++) {
      add(at(u + D + i, vv + D + j), -W / 2 + i + .5, H / 2 - j - .5, D / 2 + o);           // front
      add(at(u + 2 * D + W + i, vv + D + j), W / 2 - i - .5, H / 2 - j - .5, -D / 2 - o);   // back
    }
    for (let i = 0; i < D; i++) for (let j = 0; j < H; j++) {
      add(at(u + i, vv + D + j), -W / 2 - o, H / 2 - j - .5, -D / 2 + i + .5);               // right
      add(at(u + D + W + i, vv + D + j), W / 2 + o, H / 2 - j - .5, D / 2 - i - .5);         // left
    }
    for (let i = 0; i < W; i++) for (let j = 0; j < D; j++) {
      add(at(u + D + i, vv + j), -W / 2 + i + .5, H / 2 + o, -D / 2 + j + .5);               // top
      add(at(u + D + W + i, vv + j), -W / 2 + i + .5, -H / 2 - o, D / 2 - j - .5);           // bottom
    }
  }
}
