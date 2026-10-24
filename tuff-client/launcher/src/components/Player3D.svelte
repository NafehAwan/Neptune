<script lang="ts">
  // The signed-in player in 3D (Steve when the account isn't premium).
  import { onDestroy, onMount } from "svelte";
  import hoodieOverlay from "../assets/glade/hoodie.png";
  import { POSES, studioLights, voxelLayers, withHoodie } from "../lib/character3d";

  let { skin, hoodie = true, width = 300, height = 300, still = false }: { skin: string; hoodie?: boolean; width?: number; height?: number; still?: boolean } = $props();
  let canvas: HTMLCanvasElement;
  let viewer: import("skinview3d").SkinViewer | null = null;
  let frame = 0;
  let loaded = "";

  async function build(base: string, withOutfit: boolean) {
    const url = withOutfit ? await withHoodie(base, hoodieOverlay).catch(() => base) : base;
    const s = await import("skinview3d");
    viewer?.dispose();
    const v = new s.SkinViewer({ canvas, width, height });
    viewer = v;
    await v.loadSkin(url, { model: "auto-detect" });
    if (viewer !== v) return;
    v.renderer.setClearColor(0x000000, 0);
    v.camera.position.set(0, 6, 50);
    v.zoom = 0.93; v.fov = 38;
    v.animation = null;
    v.playerObject.rotation.y = 0.55;
    POSES.run(v);
    studioLights(v);
    voxelLayers(v, url, v.playerObject.skin.modelType === "slim").catch(() => {});
  }

  // a slow float and breath; skinview3d redraws every frame
  function tick(t: number) {
    if (viewer && !document.hidden) {
      viewer.playerObject.position.y = Math.sin(t / 900) * 0.8;
      viewer.playerObject.skin.head.rotation.x = 0.1 + Math.sin(t / 1300) * 0.04;
    }
    frame = requestAnimationFrame(tick);
  }

  onMount(() => { if (!still) frame = requestAnimationFrame(tick); });
  $effect(() => { const key = `${skin}|${hoodie}`; if (skin && key !== loaded) { loaded = key; build(skin, hoodie); } });
  onDestroy(() => { cancelAnimationFrame(frame); viewer?.dispose(); viewer = null; });
</script>

<canvas bind:this={canvas} style:width="{width}px" style:height="{height}px"></canvas>

<style>
  canvas { display: block; }
</style>
