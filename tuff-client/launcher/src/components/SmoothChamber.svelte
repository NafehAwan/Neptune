<script lang="ts">
  import { onMount } from "svelte";
  import floor from "../assets/chamber/floor.png";
  import pillars from "../assets/chamber/pillars.png";
  import spawner from "../assets/chamber/spawner.png";
  import wall from "../assets/chamber/wall.png";

  // The painted Trial Chamber, shared with the in-game title screen. Layers
  // move with GPU-composited transforms; glows, embers and dust go on one
  // canvas. Everything pauses when the window is hidden or unfocused.
  let { lowMotion = false }: { lowMotion?: boolean } = $props();

  const ART_W = 2112, ART_H = 1188, HORIZON = ART_H * 0.7;
  const BULBS = [0.13 * ART_W, 0.87 * ART_W];
  const BULB_Y = HORIZON * 0.55;
  const SPAWNER = 380; // drawn size of the 520px spawner art

  let host: HTMLDivElement;
  let wallEl: HTMLImageElement, floorEl: HTMLImageElement, pillarsEl: HTMLImageElement, spawnerEl: HTMLImageElement;
  let canvas: HTMLCanvasElement;

  onMount(() => {
    const ctx = canvas.getContext("2d")!;
    let w = 0, h = 0, k = 1, dpr = 1;
    let time = 8, last = performance.now(), raf = 0, running = true;
    let mouse = { x: 0, y: 0 }, eased = { x: 0, y: 0 };
    const reduced = matchMedia("(prefers-reduced-motion: reduce)").matches;
    const still = () => lowMotion || reduced;

    type P = { x: number; y: number; vx: number; vy: number; life: number; max: number; size: number };
    const embers: P[] = [];
    const dust: P[] = Array.from({ length: 70 }, () => spawnDust(true));
    function spawnDust(anywhere: boolean): P {
      return {
        x: Math.random() * ART_W, y: anywhere ? Math.random() * ART_H : ART_H * (0.3 + Math.random() * 0.7),
        vx: (Math.random() - 0.4) * 14, vy: -(4 + Math.random() * 12),
        life: anywhere ? Math.random() * 10 : 0, max: 10 + Math.random() * 14, size: Math.random(),
      };
    }

    function resize() {
      w = host.clientWidth; h = host.clientHeight;
      dpr = Math.min(2, devicePixelRatio || 1);
      canvas.width = Math.round(w * dpr); canvas.height = Math.round(h * dpr);
      k = Math.max(w / 1920, h / 1080);
      for (const el of [wallEl, floorEl, pillarsEl]) { el.style.width = `${ART_W * k}px`; el.style.height = `${ART_H * k}px`; }
      spawnerEl.style.width = spawnerEl.style.height = `${SPAWNER * k}px`;
      draw();
    }

    const toScreen = (ax: number, ay: number, drift: number, lift: number, p: number) => [
      w / 2 + (ax - ART_W / 2) * k - drift * p * k,
      h / 2 + (ay - ART_H / 2) * k - lift * p * k,
    ];

    function place(el: HTMLElement, x: number, y: number) {
      el.style.transform = `translate3d(${x}px, ${y}px, 0)`;
    }

    function glow(x: number, y: number, r: number, color: string, alpha: number) {
      const g = ctx.createRadialGradient(x, y, 0, x, y, r);
      g.addColorStop(0, color.replace("A", String(alpha)));
      g.addColorStop(1, color.replace("A", "0"));
      ctx.fillStyle = g;
      ctx.fillRect(x - r, y - r, r * 2, r * 2);
    }

    function draw() {
      if (!w) return;
      eased.x += (mouse.x - eased.x) * 0.05;
      eased.y += (mouse.y - eased.y) * 0.05;
      const drift = Math.sin(time * 0.06) * 34 + eased.x * 26;
      const lift = eased.y * 10;
      const layer = (el: HTMLElement, p: number) =>
        place(el, w / 2 - (ART_W * k) / 2 - drift * p * k, h / 2 - (ART_H * k) / 2 - lift * p * k);
      layer(wallEl, 0.25); layer(floorEl, 1); layer(pillarsEl, 0.6);
      const bob = Math.sin(time * 1.2) * 6;
      const [sx, sy] = toScreen(ART_W / 2, HORIZON - 30 + bob, drift, lift, 0.85);
      place(spawnerEl, sx - (SPAWNER / 2) * k, sy - (SPAWNER * 0.56) * k);

      ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
      ctx.clearRect(0, 0, w, h);
      ctx.globalCompositeOperation = "lighter";
      BULBS.forEach((bx, i) => {
        let f = 0.78 + Math.sin(time * 3 + i * 4.1) * 0.07 + Math.sin(time * 11.3 + i * 2.3) * 0.05;
        if (Math.sin(time * 0.9 + i * 7) > 0.985) f -= 0.3;
        const [x, y] = toScreen(bx, BULB_Y, drift, lift, 0.6);
        glow(x, y, 420 * k, "rgba(255,170,100,A)", 0.32 * f);
        glow(x, y, 120 * k, "rgba(255,230,180,A)", 0.4 * f);
      });
      const pulse = 0.8 + Math.sin(time * 2.1) * 0.12 + Math.sin(time * 7.3) * 0.06;
      glow(sx, sy - 30 * k, 480 * k, "rgba(240,128,64,A)", 0.22 * pulse);
      glow(sx, sy - 36 * k, 150 * k, "rgba(255,210,140,A)", 0.3 * pulse);
      for (const e of embers) {
        const t = e.life / e.max;
        const [x, y] = toScreen(e.x, e.y, drift, lift, 0.85);
        glow(x, y, (5 + 6 * (1 - t)) * k, t < 0.4 ? "rgba(255,226,176,A)" : "rgba(224,122,60,A)", (1 - t) * 0.95);
      }
      for (const d of dust) {
        const a = Math.sin((d.life / d.max) * Math.PI) * 0.3;
        if (a < 0.02) continue;
        const [x, y] = toScreen(d.x, d.y, drift, lift, 0.5 + d.size * 0.4);
        glow(x, y, (2 + d.size * 4) * k, "rgba(237,227,204,A)", a);
      }
      ctx.globalCompositeOperation = "source-over";
    }

    function step(dt: number) {
      time += dt;
      if (embers.length < 40 && Math.random() < dt * 10) {
        embers.push({ x: ART_W / 2 + (Math.random() - 0.5) * 100, y: HORIZON - 90, vx: (Math.random() - 0.5) * 24, vy: -(40 + Math.random() * 70), life: 0, max: 2 + Math.random() * 2.5, size: 0 });
      }
      for (let i = embers.length - 1; i >= 0; i--) {
        const e = embers[i];
        e.life += dt; e.x += (e.vx + Math.sin(time * 2 + i) * 18) * dt; e.y += e.vy * dt;
        if (e.life > e.max) embers.splice(i, 1);
      }
      dust.forEach((d, i) => {
        d.life += dt; d.x += (d.vx + Math.sin(time * 0.4 + i) * 8) * dt; d.y += d.vy * dt;
        if (d.life > d.max) dust[i] = spawnDust(false);
      });
    }

    function frame(now: number) {
      raf = requestAnimationFrame(frame);
      const dt = Math.min(0.05, (now - last) / 1000);
      last = now;
      if (!running || still()) return;
      step(dt);
      draw();
    }

    const onPointer = (e: PointerEvent) => { mouse = { x: (e.clientX / innerWidth) * 2 - 1, y: (e.clientY / innerHeight) * 2 - 1 }; };
    const onVisibility = () => { running = !document.hidden; };
    const onBlur = () => { running = false; };
    const onFocus = () => { running = !document.hidden; };
    const observer = new ResizeObserver(resize);
    observer.observe(host);
    addEventListener("pointermove", onPointer, { passive: true });
    document.addEventListener("visibilitychange", onVisibility);
    addEventListener("blur", onBlur);
    addEventListener("focus", onFocus);
    resize();
    raf = requestAnimationFrame(frame);
    return () => {
      cancelAnimationFrame(raf);
      observer.disconnect();
      removeEventListener("pointermove", onPointer);
      document.removeEventListener("visibilitychange", onVisibility);
      removeEventListener("blur", onBlur);
      removeEventListener("focus", onFocus);
    };
  });
</script>

<div class="scene" bind:this={host} aria-hidden="true">
  <img bind:this={wallEl} src={wall} alt="" class="layer" />
  <img bind:this={floorEl} src={floor} alt="" class="layer" />
  <img bind:this={pillarsEl} src={pillars} alt="" class="layer" />
  <img bind:this={spawnerEl} src={spawner} alt="" class="layer" />
  <canvas bind:this={canvas}></canvas>
  <div class="vignette"></div>
</div>

<style>
  .scene { position: absolute; inset: 0; overflow: hidden; background: #0b0c0b; }
  .layer { position: absolute; left: 0; top: 0; max-width: none; will-change: transform; user-select: none; -webkit-user-drag: none; }
  canvas { position: absolute; inset: 0; width: 100%; height: 100%; pointer-events: none; }
  .vignette {
    position: absolute; inset: 0; pointer-events: none;
    background:
      radial-gradient(120% 90% at 50% 45%, transparent 50%, rgba(8, 9, 8, 0.6) 100%),
      linear-gradient(180deg, rgba(8, 9, 8, 0.55) 0%, transparent 25%, transparent 55%, rgba(14, 15, 14, 0.92) 100%);
  }
</style>
