<script lang="ts">
  // The signed-in player in 3D (Steve when the account isn't premium). One viewer is
  // kept for the page; a new skin or hoodie setting just loads a new texture into it.
  import { onDestroy, onMount } from "svelte";
  import hoodieOverlay from "../assets/glade/hoodie.png";
  import { POSES, studioLights, withHoodie } from "../lib/character3d";

  let { skin, hoodie = true, width = 300, height = 300, still = false }: { skin: string; hoodie?: boolean; width?: number; height?: number; still?: boolean } = $props();
  let canvas: HTMLCanvasElement;
  let viewer: import("skinview3d").SkinViewer | null = null;
  let frame = 0;
  let wanted = "";
  let alive = true;

  async function show(base: string, outfit: boolean) {
    const key = `${base}|${outfit}`;
    wanted = key;
    const url = outfit ? await withHoodie(base, hoodieOverlay).catch(() => base) : base;
    if (!viewer || wanted !== key) return; // a newer skin was asked for meanwhile
    await viewer.loadSkin(url, { model: "auto-detect" });
  }

  onMount(async () => {
    const s = await import("skinview3d");
    if (!alive) return;
    const v = new s.SkinViewer({ canvas, width, height });
    v.renderer.setClearColor(0x000000, 0);
    v.camera.position.set(0, 6, 50);
    v.zoom = 0.93; v.fov = 38;
    v.animation = null;
    v.playerObject.rotation.y = 0.55;
    POSES.run(v);
    studioLights(v);
    viewer = v;
    show(skin, hoodie);
    if (!still) frame = requestAnimationFrame(tick);
  });

  // a slow float and breath; skinview3d redraws every frame
  function tick(t: number) {
    if (viewer && !document.hidden) {
      viewer.playerObject.position.y = Math.sin(t / 900) * 0.8;
      viewer.playerObject.skin.head.rotation.x = 0.1 + Math.sin(t / 1300) * 0.04;
    }
    frame = requestAnimationFrame(tick);
  }

  $effect(() => { const s = skin, h = hoodie; if (viewer && s && `${s}|${h}` !== wanted) show(s, h); });
  onDestroy(() => { alive = false; cancelAnimationFrame(frame); viewer?.dispose(); viewer = null; });
</script>

<canvas bind:this={canvas} style:width="{width}px" style:height="{height}px"></canvas>

<style>
  canvas { display: block; }
</style>
