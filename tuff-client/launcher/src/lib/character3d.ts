// The 3D player on Home: studio lighting, a pose, the Glade hoodie, and 3D skin
// layers (each outer-layer pixel is a thin block where Minecraft draws that layer).
import * as THREE from "three";
import type { SkinViewer } from "skinview3d";

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

/** Swaps the flat outer layer for thin blocks, one per opaque pixel, textured with the skin
 *  itself so the colours match the game. Each part's blocks are merged into one mesh, so the
 *  whole layer is six draw calls. Old 64x32 skins have no outer layer and stay flat. */
export async function thinLayers(v: SkinViewer, url: string) {
  const img = await new Promise<HTMLImageElement>((resolve, reject) => {
    const i = new Image(); i.onload = () => resolve(i); i.onerror = reject; i.src = url;
  });
  const skin = v.playerObject.skin as any;
  // last skin's blocks go first, and the flat layer comes back until new blocks replace it
  for (const name of Object.keys(PARTS)) {
    const part = skin[name], holder = part.innerLayer.parent;
    for (const old of holder.children.filter((ch: any) => ch.userData?.layerVoxel)) { holder.remove(old); old.geometry.dispose(); }
    part.outerLayer.visible = true;
  }
  if (img.width !== 64 || img.height !== 64) return;
  const c = document.createElement("canvas"); c.width = 64; c.height = 64;
  const g = c.getContext("2d", { willReadFrequently: true })!; g.drawImage(img, 0, 0);
  const alpha = g.getImageData(0, 0, 64, 64).data;
  const solid = (x: number, y: number) => alpha[(y * 64 + x) * 4 + 3] > 20;

  const slim = skin.modelType === "slim";
  const material = skin.head.outerLayer.material;
  for (const [name, [u, vv, W0, H, D]] of Object.entries(PARTS)) {
    const W = slim && name.endsWith("Arm") ? 3 : W0;
    const T = name === "head" ? 0.75 : 0.55; // how far the layer stands out, in pixels
    const part = skin[name], inner = part.innerLayer, holder = inner.parent, ip = inner.position;
    const shapes = { z: new THREE.BoxGeometry(1, 1, T), x: new THREE.BoxGeometry(T, 1, 1), y: new THREE.BoxGeometry(1, T, 1) };
    const pos: number[] = [], nor: number[] = [], uvs: number[] = [], idx: number[] = [];
    const add = (px: number, py: number, shape: "x" | "y" | "z", x: number, y: number, z: number) => {
      if (!solid(px, py)) return;
      const geo = shapes[shape], p = geo.attributes.position, n = geo.attributes.normal, start = pos.length / 3;
      const cu = (px + 0.5) / 64, cv = 1 - (py + 0.5) / 64;
      for (let k = 0; k < p.count; k++) {
        pos.push(p.getX(k) + ip.x + x, p.getY(k) + ip.y + y, p.getZ(k) + ip.z + z);
        nor.push(n.getX(k), n.getY(k), n.getZ(k));
        uvs.push(cu, cv);
      }
      const index = geo.index!;
      for (let k = 0; k < index.count; k++) idx.push(start + index.getX(k));
    };
    const o = T / 2;
    for (let i = 0; i < W; i++) for (let j = 0; j < H; j++) {
      add(u + D + i, vv + D + j, "z", -W / 2 + i + .5, H / 2 - j - .5, D / 2 + o);          // front
      add(u + 2 * D + W + i, vv + D + j, "z", W / 2 - i - .5, H / 2 - j - .5, -D / 2 - o);  // back
    }
    for (let i = 0; i < D; i++) for (let j = 0; j < H; j++) {
      add(u + i, vv + D + j, "x", -W / 2 - o, H / 2 - j - .5, -D / 2 + i + .5);              // right
      add(u + D + W + i, vv + D + j, "x", W / 2 + o, H / 2 - j - .5, D / 2 - i - .5);        // left
    }
    for (let i = 0; i < W; i++) for (let j = 0; j < D; j++) {
      add(u + D + i, vv + j, "y", -W / 2 + i + .5, H / 2 + o, -D / 2 + j + .5);              // top
      add(u + D + W + i, vv + j, "y", -W / 2 + i + .5, -H / 2 - o, -D / 2 + j + .5);         // bottom
    }
    for (const sh of Object.values(shapes)) sh.dispose();
    part.outerLayer.visible = false;
    if (!idx.length) continue;
    const merged = new THREE.BufferGeometry();
    merged.setAttribute("position", new THREE.Float32BufferAttribute(pos, 3));
    merged.setAttribute("normal", new THREE.Float32BufferAttribute(nor, 3));
    merged.setAttribute("uv", new THREE.Float32BufferAttribute(uvs, 2));
    merged.setIndex(idx);
    const mesh = new THREE.Mesh(merged, material);
    mesh.userData.layerVoxel = true;
    holder.add(mesh);
  }
}

/** The skin with the Glade hoodie (hoodie, hood, headphones, fringe, shoes) drawn over
 *  its outer layer. Old 64x32 skins have no outer layer, so they stay as they are. */
export async function withHoodie(url: string, hoodie: string): Promise<string> {
  const load = (src: string) => new Promise<HTMLImageElement>((resolve, reject) => {
    const i = new Image(); i.onload = () => resolve(i); i.onerror = reject; i.src = src;
  });
  const [skin, over] = await Promise.all([load(url), load(hoodie)]);
  if (skin.width !== 64 || skin.height !== 64) return url;
  const c = document.createElement("canvas"); c.width = 64; c.height = 64;
  const g = c.getContext("2d")!;
  g.drawImage(skin, 0, 0);
  g.drawImage(over, 0, 0);
  return c.toDataURL("image/png");
}
