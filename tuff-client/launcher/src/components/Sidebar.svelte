<script lang="ts">
  import logo from "../assets/logo.svg";
  import { app, go, type Route } from "../lib/state.svelte";
  import Icon from "./Icon.svelte";

  const items: { route: Route; icon: string; label: string }[] = [
    { route: "home", icon: "home", label: "Home" },
    { route: "library", icon: "library", label: "Library" },
    { route: "browse", icon: "cube", label: "Mods" },
    { route: "servers", icon: "globe", label: "Servers" },
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
  <button class="logo" onclick={() => go("home")} aria-label="Tuff Client home">
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
    padding: 14px 0 16px;
    background: #111311;
    border-right: 1px solid var(--border);
    position: relative; z-index: 5;
  }
  .logo { background: none; border: 0; padding: 0; margin-bottom: 22px; cursor: pointer; image-rendering: pixelated; transition: transform var(--med) var(--ease); }
  .logo:hover { transform: rotate(-6deg) scale(1.06); }
  .logo img { display: block; image-rendering: pixelated; }
  .group { display: grid; gap: 6px; }
  .bottom { margin-top: auto; }
  .nav {
    position: relative; width: 42px; height: 42px;
    display: grid; place-items: center;
    background: transparent; border: 0; border-radius: 8px;
    color: var(--faint); cursor: pointer;
    transition: color var(--fast), background var(--fast);
  }
  .nav:hover { color: var(--text); background: var(--surface); }
  .nav.active { color: var(--accent-hover); background: rgba(217, 130, 75, 0.1); }
  .nav.active::before {
    content: ""; position: absolute; left: -13px; top: 11px; width: 4px; height: 20px;
    background: var(--accent);
    clip-path: polygon(0 0, 50% 0, 50% 2px, 100% 2px, 100% calc(100% - 2px), 50% calc(100% - 2px), 50% 100%, 0 100%);
  }
  .live { position: absolute; top: 8px; right: 8px; width: 6px; height: 6px; background: var(--online); box-shadow: 0 0 6px var(--online); }
  .tip {
    position: absolute; left: 54px; top: 50%; translate: 0 -50%;
    background: var(--surface-3); color: var(--text);
    font: 500 12px var(--font); white-space: nowrap;
    padding: 5px 9px; border-radius: 5px; border: 1px solid var(--border-strong);
    opacity: 0; pointer-events: none; transform: translateX(-4px);
    transition: opacity var(--fast), transform var(--fast) var(--ease);
  }
  .nav:hover:not(.active) .tip { opacity: 1; transform: none; }
</style>
