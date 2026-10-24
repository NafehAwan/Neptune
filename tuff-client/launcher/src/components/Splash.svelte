<script lang="ts">
  // Start screen: shown while Glade loads and checks for a launcher update.
  import { fade } from "svelte/transition";
  import banner from "../assets/glade/banner.jpg";
  import logo from "../assets/brand/leaf.png";
  import { windowAction } from "../lib/api";
  import { app } from "../lib/state.svelte";
  import Icon from "./Icon.svelte";
</script>

<div class="splash" out:fade={{ duration: 320 }} data-tauri-drag-region>
  <div class="bg" style:background-image="url({banner})" data-tauri-drag-region></div>
  <div class="win">
    <button onclick={() => windowAction("minimize")} aria-label="Minimize"><Icon name="minimize" size={13} /></button>
    <button class="close" onclick={() => windowAction("close")} aria-label="Close"><Icon name="close" size={13} /></button>
  </div>
  <div class="center" data-tauri-drag-region>
    <img class="logo" src={logo} alt="" width="76" height="76" />
    <h1>GLADE</h1>
    {#if app.bootError}
      <p class="err selectable">Glade couldn't start: {app.bootError}</p>
      <button class="retry" onclick={() => location.reload()}>Try again</button>
    {:else}
      <div class="bar"><i class:done={app.boot === "You're up to date"}></i></div>
      <p class="stage">{app.boot}{app.boot !== "You're up to date" ? "…" : ""}</p>
    {/if}
  </div>
  <p class="ver">v{app.info?.version ?? ""}</p>
</div>

<style>
  .splash { position: fixed; inset: 0; z-index: 80; background: var(--bg); display: grid; place-items: center; }
  .bg { position: absolute; inset: 0; background: center 30% / cover no-repeat; filter: blur(6px) brightness(0.45); transform: scale(1.06); }
  .splash::after { content: ""; position: absolute; inset: 0; background: radial-gradient(ellipse at center, rgba(20, 21, 24, 0.2), rgba(20, 21, 24, 0.85)); pointer-events: none; }
  .center { position: relative; z-index: 1; display: grid; justify-items: center; gap: 14px; }
  .logo { filter: drop-shadow(0 10px 30px rgba(47, 191, 106, 0.45)); animation: float 3s ease-in-out infinite; }
  @keyframes float { 50% { transform: translateY(-6px); } }
  h1 { font: 800 30px var(--brand); letter-spacing: 0.14em; color: #fff; }
  .bar { position: relative; width: 220px; height: 5px; border-radius: 99px; background: rgba(255, 255, 255, 0.1); overflow: hidden; margin-top: 6px; }
  .bar i { position: absolute; top: 0; bottom: 0; width: 40%; border-radius: 99px; background: linear-gradient(90deg, #27c46c, #5dffa8); box-shadow: 0 0 12px rgba(93, 255, 168, 0.6); animation: slide 1.1s ease-in-out infinite; }
  .bar i.done { width: 100%; left: 0; animation: none; transition: width 300ms; }
  @keyframes slide { from { left: -40%; } to { left: 100%; } }
  .stage { font: 500 13px var(--font); color: #b9c2bc; }
  .err { color: var(--danger); font-size: 13px; max-width: 420px; text-align: center; }
  .retry { height: 36px; padding: 0 18px; border: 0; border-radius: 10px; background: var(--accent); color: var(--accent-ink); font: 700 13px var(--font); cursor: pointer; }
  .ver { position: absolute; bottom: 16px; z-index: 1; font: 500 11px var(--font); color: var(--faint); }
  .win { position: absolute; right: 12px; top: 10px; display: flex; gap: 6px; z-index: 2; }
  .win button { width: 38px; height: 34px; border-radius: 9px; border: 0; background: rgba(20, 21, 24, 0.6); color: #cfd2d7; display: grid; place-items: center; cursor: pointer; }
  .win button:hover { background: var(--surface-3); }
  .win .close:hover { background: var(--danger); color: #fff; }
</style>
