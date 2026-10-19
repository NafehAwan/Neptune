<script lang="ts">
  import { onMount } from "svelte";
  import { fade } from "svelte/transition";
  import CrashModal from "./components/CrashModal.svelte";
  import DownloadCard from "./components/DownloadCard.svelte";
  import GroveScene from "./components/GroveScene.svelte";
  import Sidebar from "./components/Sidebar.svelte";
  import TitleBar from "./components/TitleBar.svelte";
  import Toasts from "./components/Toasts.svelte";
  import UpdateScreen from "./components/UpdateScreen.svelte";
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

  // pages glide up and settle, with a touch of overshoot
  function pageIn(_node: Element) {
    return {
      delay: 70,
      duration: 380,
      css: (t: number) => {
        const e = 1 - Math.pow(1 - t, 3);
        return `opacity: ${Math.min(1, t * 1.6)}; transform: translateY(${(1 - e) * 12}px) scale(${0.992 + 0.008 * e});`;
      },
    };
  }
</script>

<svelte:window onkeydown={keys} />

<div class="shell" class:low-motion={app.settings?.lowMotion}>
  <!-- the forest lives behind everything; it animates on Home and rests elsewhere -->
  <GroveScene active={app.route === "home"} still={!!app.settings?.lowMotion} />
  <Sidebar />
  <main class:frost={app.route !== "home"}>
    <TitleBar transparent={app.route === "home"} />
    {#if app.ready}
      {#key app.route}
        <div class="view" in:pageIn out:fade={{ duration: 90 }}>
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
    <!-- Home shows it above the Play bar; every other page in the corner -->
    {#if app.route !== "home"}<DownloadCard />{/if}
  </main>
  {#if app.crashOpen && app.lastExit?.crash}<CrashModal exit={app.lastExit} onclose={() => (app.crashOpen = false)} />{/if}
  <!-- required updates wait until Minecraft is closed -->
  {#if app.update && !app.running && !app.launching && (!import.meta.env.DEV || location.search.includes("update"))}<UpdateScreen update={app.update} />{/if}
  <Toasts />
</div>

<style>
  .shell { position: relative; display: flex; height: 100%; background: var(--bg); }
  main { position: relative; flex: 1; min-width: 0; height: 100%; overflow: hidden; }
  /* on every page but Home the forest sits behind a sheet of frosted glass */
  main.frost::before {
    content: ""; position: absolute; inset: 0;
    background: rgba(246, 244, 238, 0.62);
    backdrop-filter: blur(26px) saturate(1.3); -webkit-backdrop-filter: blur(26px) saturate(1.3);
  }
  .view { position: absolute; inset: 0; }
  .view :global(.page) { padding-top: 64px; }
  .boot { height: 100%; display: grid; place-items: center; color: var(--accent); }
</style>
