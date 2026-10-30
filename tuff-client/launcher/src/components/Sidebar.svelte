<script lang="ts">
  import logo from "../assets/brand/leaf.png";
  import { app, go, type Route } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import Icon from "./Icon.svelte";

  type Item = { route: Route | "store"; icon: string; label: string };
  const items: Item[] = [
    { route: "home", icon: "gamepad", label: "Play" },
    { route: "browse", icon: "cube", label: "Mods" },
    { route: "servers", icon: "server", label: "Servers" },
    { route: "library", icon: "grid", label: "Profiles" },
    { route: "store", icon: "cart", label: "Store (coming soon)" },
  ];
  const bottom: Item[] = [
    { route: "wardrobe", icon: "shirt", label: "Wardrobe" },
    { route: "screenshots", icon: "camera", label: "Screenshots" },
    { route: "console", icon: "console", label: "Console" },
    { route: "settings", icon: "gear", label: "Settings" },
  ];
  function open(i: Item) {
    if (i.route === "store") toast("The Glade Store is coming soon");
    else go(i.route);
  }
</script>

{#snippet item(i: Item)}
  <button class="nav" class:active={app.route === i.route} onclick={() => open(i)} aria-label={i.label}>
    <Icon name={i.icon} size={22} />
    {#if i.route === "console" && app.running}<span class="live"></span>{/if}
    <span class="tip">{i.label}</span>
  </button>
{/snippet}

<nav class="sidebar" data-tauri-drag-region>
  <button class="logo" onclick={() => go("home")} aria-label="Glade home">
    <img src={logo} alt="" width="32" height="32" />
  </button>
  <div class="group">
    {#each items as i}{@render item(i)}{/each}
  </div>
  <div class="group bottom">
    {#each bottom as i}{@render item(i)}{/each}
    <span class="ver">v{app.info?.version ?? ""}</span>
  </div>
</nav>

<style>
  .sidebar { width: 64px; flex: none; height: 100%; display: flex; flex-direction: column; align-items: center; padding: 12px 0 10px; position: relative; z-index: 5; }
  .logo { background: none; border: 0; padding: 0; margin: 2px 0 18px; cursor: pointer; transition: transform var(--med) var(--ease); }
  .logo:hover { transform: rotate(-6deg) scale(1.06); }
  .logo img { display: block; }
  .group { display: grid; gap: 4px; justify-items: center; }
  .bottom { margin-top: auto; }
  .bottom .nav { width: 40px; height: 38px; }
  .nav { position: relative; width: 44px; height: 44px; display: grid; place-items: center; background: transparent; border: 0; border-radius: 11px; color: var(--muted); cursor: pointer; transition: color var(--fast), background var(--fast); }
  .nav:hover { color: var(--text); background: var(--surface); }
  .nav.active { color: #fff; background: var(--surface-3); }
  .nav.active::before { content: ""; position: absolute; left: -10px; top: 12px; bottom: 12px; width: 3px; border-radius: 0 3px 3px 0; background: var(--accent); }
  .live { position: absolute; top: 9px; right: 9px; width: 7px; height: 7px; border-radius: 50%; background: var(--online); box-shadow: 0 0 6px var(--online); }
  .ver { font: 500 10px var(--font); color: var(--faint); margin-top: 6px; }
  .tip { position: absolute; left: 54px; top: 50%; translate: 0 -50%; background: #0d0e10; color: var(--text); font: 600 12px var(--font); white-space: nowrap; padding: 5px 10px; border-radius: 8px; border: 1px solid var(--border-strong); box-shadow: 0 8px 20px rgba(0, 0, 0, 0.45); opacity: 0; pointer-events: none; transform: translateX(-4px); transition: opacity var(--fast), transform var(--fast) var(--ease); }
  .nav:hover:not(.active) .tip { opacity: 1; transform: none; }
</style>
