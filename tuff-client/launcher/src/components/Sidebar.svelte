<script lang="ts">
  import logo from "../assets/brand/leaf.png";
  import { app, go, type Route } from "../lib/state.svelte";
  import Icon from "./Icon.svelte";

  const items: { route: Route; icon: string; label: string }[] = [
    { route: "home", icon: "home", label: "Home" },
    { route: "library", icon: "library", label: "Library" },
    { route: "browse", icon: "cube", label: "Mods" },
    { route: "servers", icon: "server", label: "Servers" },
    { route: "wardrobe", icon: "shirt", label: "Wardrobe" },
    { route: "screenshots", icon: "camera", label: "Screenshots" },
  ];
  const bottom: { route: Route; icon: string; label: string }[] = [
    { route: "console", icon: "console", label: "Console" },
    { route: "settings", icon: "gear", label: "Settings" },
  ];
</script>

{#snippet item(i: { route: Route; icon: string; label: string }, n: number)}
  <button class="nav" class:active={app.route === i.route} onclick={() => go(i.route)} aria-label={i.label} title="{i.label}  (Ctrl+{n})">
    <Icon name={i.icon} size={18} />
    {#if i.route === "console" && app.running}<span class="live"></span>{/if}
    <span class="tip">{i.label}</span>
  </button>
{/snippet}

<nav class="sidebar">
  <button class="logo" onclick={() => go("home")} aria-label="Glade home">
    <img src={logo} alt="" width="34" height="34" />
  </button>
  <div class="group">
    {#each items as i, n}{@render item(i, n + 1)}{/each}
  </div>
  <div class="group bottom">
    {#each bottom as i, n}{@render item(i, items.length + n + 1)}{/each}
  </div>
</nav>

<style>
  .sidebar {
    width: 68px; flex: none; height: 100%;
    display: flex; flex-direction: column; align-items: center;
    padding: 14px 0;
    background: rgba(255, 255, 255, 0.32);
    backdrop-filter: blur(20px) saturate(1.4); -webkit-backdrop-filter: blur(20px) saturate(1.4);
    border-right: 1px solid rgba(255, 255, 255, 0.6);
    position: relative; z-index: 5;
  }
  .logo { background: none; border: 0; padding: 0; margin-bottom: 14px; cursor: pointer; transition: transform var(--med) var(--ease); }
  .logo:hover { transform: rotate(-6deg) scale(1.06); }
  .logo img { display: block; }
  .group { display: grid; gap: 6px; }
  .bottom { margin-top: auto; }
  .nav {
    position: relative; width: 42px; height: 42px;
    display: grid; place-items: center;
    background: transparent; border: 0; border-radius: 10px;
    color: var(--muted); cursor: pointer;
    transition: color var(--fast), background var(--fast);
  }
  .nav:hover { color: var(--text); background: rgba(255, 255, 255, 0.4); }
  .nav.active { color: var(--text); background: rgba(255, 255, 255, 0.6); }
  .live { position: absolute; top: 8px; right: 8px; width: 7px; height: 7px; border-radius: 50%; background: var(--online); box-shadow: 0 0 6px var(--online); }
  .tip {
    position: absolute; left: 54px; top: 50%; translate: 0 -50%;
    background: rgba(255, 255, 255, 0.92); color: var(--text);
    font: 600 12.5px var(--font); white-space: nowrap;
    padding: 5px 10px; border-radius: 8px; border: 1px solid var(--border); box-shadow: 0 6px 18px rgba(30, 60, 40, 0.12);
    opacity: 0; pointer-events: none; transform: translateX(-4px);
    transition: opacity var(--fast), transform var(--fast) var(--ease);
  }
  .nav:hover:not(.active) .tip { opacity: 1; transform: none; }
</style>
