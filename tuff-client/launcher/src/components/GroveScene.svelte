<script lang="ts">
  // The animated pixel forest behind the whole launcher. It animates on Home and
  // holds still elsewhere, and stops completely while the window is hidden or
  // unfocused, or when Reduce motion is on.
  import { onDestroy, onMount } from "svelte";
  import { drawGrove } from "../lib/grove";

  let { active = true, still = false }: { active?: boolean; still?: boolean } = $props();
  let canvas: HTMLCanvasElement;
  let frame = 0;
  let time = 30; // start mid-scene so clouds and birds are already on screen
  let last = 0;
  let focused = true;

  function paint() {
    const dpr = Math.min(window.devicePixelRatio || 1, 2);
    const w = canvas.clientWidth, h = canvas.clientHeight;
    if (canvas.width !== Math.round(w * dpr) || canvas.height !== Math.round(h * dpr)) {
      canvas.width = Math.round(w * dpr);
      canvas.height = Math.round(h * dpr);
    }
    const g = canvas.getContext("2d")!;
    g.setTransform(dpr, 0, 0, dpr, 0, 0);
    drawGrove(g, w, h, time);
  }

  function tick(now: number) {
    const dt = last ? Math.min(0.1, (now - last) / 1000) : 0;
    last = now;
    if (active && !still && focused && !document.hidden) {
      time += dt;
      paint();
    }
    frame = requestAnimationFrame(tick);
  }

  const onFocus = () => (focused = true);
  const onBlur = () => (focused = false);
  const onResize = () => paint();

  onMount(() => {
    paint();
    frame = requestAnimationFrame(tick);
    window.addEventListener("focus", onFocus);
    window.addEventListener("blur", onBlur);
    window.addEventListener("resize", onResize);
  });
  onDestroy(() => {
    cancelAnimationFrame(frame);
    window.removeEventListener("focus", onFocus);
    window.removeEventListener("blur", onBlur);
    window.removeEventListener("resize", onResize);
  });
</script>

<canvas bind:this={canvas} class="grove" aria-hidden="true"></canvas>

<style>
  .grove { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
</style>
