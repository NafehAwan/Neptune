<script lang="ts">
  import { onMount } from "svelte";
  import { createChamberScene, type SceneHandle } from "../lib/scene";

  let { lowMotion = false }: { lowMotion?: boolean } = $props();
  let canvas: HTMLCanvasElement;
  let scene: SceneHandle | null = null;

  onMount(() => {
    const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
    scene = createChamberScene(canvas, { lowMotion: lowMotion || reduced });
    return () => scene?.destroy();
  });

  $effect(() => {
    scene?.setLowMotion(lowMotion);
  });
</script>

<div class="scene" aria-hidden="true">
  <canvas bind:this={canvas}></canvas>
  <div class="vignette"></div>
</div>

<style>
  .scene { position: absolute; inset: 0; overflow: hidden; background: #0f110f; }
  canvas { width: 100%; height: 100%; display: block; image-rendering: pixelated; image-rendering: crisp-edges; }
  .vignette {
    position: absolute; inset: 0; pointer-events: none;
    background:
      radial-gradient(120% 90% at 60% 45%, transparent 45%, rgba(10, 11, 10, 0.55) 100%),
      linear-gradient(180deg, rgba(20, 22, 20, 0.35) 0%, transparent 22%, transparent 55%, rgba(20, 22, 20, 0.92) 100%);
  }
</style>
