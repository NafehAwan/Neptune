// The 3D player on Home: studio lighting, a pose, and the Glade hoodie.
// The skin's outer layer is drawn the way Minecraft draws it.
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
