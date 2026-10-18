<script lang="ts">
  // The animated pixel forest behind the whole launcher. It animates on Home and
  // holds still elsewhere, and stops completely while the window is hidden or
  // unfocused, or when Reduce motion is on.
  //
  // It keeps itself cheap: the scene is pixel art, so it is drawn at CSS pixel
  // density (no retina upscaling needed), and it times its own frames. If a
  // frame costs too much it first draws at a lower resolution, then at 30 fps,
  // so the glass panels above it stay smooth on slow machines.
  import { onDestroy, onMount } from "svelte";
  import { drawGrove } from "../lib/grove";

  let { active = true, still = false }: { active?: boolean; still?: boolean } = $props();
  let canvas: HTMLCanvasElement;
  let frame = 0;
  let running = false;
  let time = 30; // start mid-scene so clouds and birds are already on screen
  let last = 0;
  let focused = true;
  // adaptive quality
  let scale = 1;
  let halfRate = false;
  let skip = false;
  let cost = 0; // smoothed ms per paint

  function paint() {
    const w = canvas.clientWidth, h = canvas.clientHeight;
    const cw = Math.max(1, Math.round(w * scale)), ch = Math.max(1, Math.round(h * scale));
    if (canvas.width !== cw || canvas.height !== ch) {
      canvas.width = cw;
      canvas.height = ch;
    }
    const g = canvas.getContext("2d", { alpha: false })!;
    g.setTransform(scale, 0, 0, scale, 0, 0);
    const t0 = performance.now();
    drawGrove(g, w, h, time);
    cost = cost * 0.9 + (performance.now() - t0) * 0.1;
  }

  function adapt() {
    // step down when a paint takes over half a 60 fps frame, step back up when it's easy again
    if (cost > 8) {
      if (scale > 0.6) scale = Math.max(0.6, scale - 0.2);
      else halfRate = true;
      cost = 4;
    } else if (cost < 2.5 && (halfRate || scale < 1)) {
      if (halfRate) halfRate = false;
      else scale = Math.min(1, scale + 0.2);
      cost = 4;
    }
  }

  const moving = () => active && !still && focused && !document.hidden;

  function tick(now: number) {
    const dt = last ? Math.min(0.1, (now - last) / 1000) : 0;
    last = now;
    if (!moving()) {
      running = false;
      last = 0;
      return;
    }
    time += dt;
    skip = halfRate && !skip;
    if (!skip) {
      paint();
      adapt();
    }
    frame = requestAnimationFrame(tick);
  }

  function start() {
    if (running || !canvas || !moving()) return;
    running = true;
    frame = requestAnimationFrame(tick);
  }

  $effect(() => {
    active;
    still;
    start();
  });

  const onFocus = () => { focused = true; start(); };
  const onBlur = () => (focused = false);
  const onVisible = () => start();
  const onResize = () => paint();

  onMount(() => {
    paint();
    start();
    window.addEventListener("focus", onFocus);
    window.addEventListener("blur", onBlur);
    window.addEventListener("resize", onResize);
    document.addEventListener("visibilitychange", onVisible);
  });
  onDestroy(() => {
    cancelAnimationFrame(frame);
    window.removeEventListener("focus", onFocus);
    window.removeEventListener("blur", onBlur);
    window.removeEventListener("resize", onResize);
    document.removeEventListener("visibilitychange", onVisible);
  });
</script>

<canvas bind:this={canvas} class="grove" aria-hidden="true"></canvas>

<style>
  .grove { position: absolute; inset: 0; width: 100%; height: 100%; display: block; }
</style>
