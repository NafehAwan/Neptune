<script lang="ts">
  import { onMount } from "svelte";
  import { fade } from "svelte/transition";
  import Sidebar from "./components/Sidebar.svelte";
  import TitleBar from "./components/TitleBar.svelte";
  import Toasts from "./components/Toasts.svelte";
  import { app, go, init, play, selectedProfile, type Route } from "./lib/state.svelte";
  import { toast } from "./lib/toast.svelte";
  import Browse from "./pages/Browse.svelte";
  import Console from "./pages/Console.svelte";
  import Home from "./pages/Home.svelte";
  import Library from "./pages/Library.svelte";
  import Screenshots from "./pages/Screenshots.svelte";
  import Servers from "./pages/Servers.svelte";
  import Settings from "./pages/Settings.svelte";
  import Wardrobe from "./pages/Wardrobe.svelte";

  const order: Route[] = ["home", "library", "browse", "servers", "wardrobe", "screenshots", "console", "settings"];

  onMount(() => {
    init().catch((e) => toast(e, "error"));
  });

  function keys(e: KeyboardEvent) {
    const target = e.target as HTMLElement;
    const typing = target.closest("input, textarea, select, [contenteditable]");
    if ((e.ctrlKey || e.metaKey) && /^[1-8]$/.test(e.key)) {
      e.preventDefault();
      go(order[Number(e.key) - 1]);
    } else if ((e.ctrlKey || e.metaKey) && e.key === "Enter" && !typing) {
      const p = selectedProfile();
      if (p) play(p.id);
    }
  }
</script>

<svelte:window onkeydown={keys} />

<div class="shell" class:low-motion={app.settings?.lowMotion}>
  <Sidebar />
  <main>
    <TitleBar transparent={app.route === "home"} />
    {#if app.ready}
      {#key app.route}
        <div class="view" in:fade={{ duration: 180, delay: 60 }} out:fade={{ duration: 80 }}>
          {#if app.route === "home"}<Home />
          {:else if app.route === "library"}<Library />
          {:else if app.route === "browse"}<Browse />
          {:else if app.route === "servers"}<Servers />
          {:else if app.route === "wardrobe"}<Wardrobe />
          {:else if app.route === "screenshots"}<Screenshots />
          {:else if app.route === "console"}<Console />
          {:else}<Settings />{/if}
        </div>
      {/key}
    {:else}
      <div class="boot"><span class="spinner"></span></div>
    {/if}
  </main>
  <Toasts />
</div>

<style>
  .shell { display: flex; height: 100%; }
  main { position: relative; flex: 1; min-width: 0; height: 100%; overflow: hidden; }
  .view { position: absolute; inset: 0; }
  .view :global(.page) { padding-top: 64px; }
  .boot { height: 100%; display: grid; place-items: center; color: var(--accent); }
</style>
