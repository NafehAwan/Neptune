<script lang="ts">
  // A 3D skin head. With `spin`, it turns slowly (paused while the window is hidden).
  import { onDestroy } from "svelte";
  import { drawHead } from "../lib/head3d";

  let { skin, size = 24, yaw = -32, pitch = -20, spin = false }: { skin: string; size?: number; yaw?: number; pitch?: number; spin?: boolean } = $props();
  let canvas: HTMLCanvasElement;
  let frame = 0;
  let start = performance.now();

  // big spinning heads get a little more room around them than the small fixed ones
  const distance = $derived(spin ? 37 : 30);

  function draw(angle: number) {
    if (canvas) drawHead(canvas, skin, angle, pitch, distance);
  }

  // 30 fps is plenty for a slow turn
  let last = 0;
  function loop(now: number) {
    frame = requestAnimationFrame(loop);
    if (document.hidden || now - last < 33) return;
    last = now;
    draw(yaw + ((now - start) / 1000) * 40);
  }

  $effect(() => {
    skin; size; yaw; pitch;
    cancelAnimationFrame(frame);
    if (spin && !document.querySelector(".low-motion")) {
      start = performance.now();
      frame = requestAnimationFrame(loop);
    } else {
      draw(yaw);
    }
  });

  onDestroy(() => cancelAnimationFrame(frame));
</script>

<canvas bind:this={canvas} width={size * 2} height={size * 2} style:width="{size}px" style:height="{size}px"></canvas>

<style>
  canvas { display: block; flex: none; }
</style>
