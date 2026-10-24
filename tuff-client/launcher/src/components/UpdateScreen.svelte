<script lang="ts">
  import { onMount } from "svelte";
  import { fade, scale } from "svelte/transition";
  import { api } from "../lib/api";
  import { bytes } from "../lib/format";
  import { app } from "../lib/state.svelte";
  import type { LauncherUpdate } from "../lib/types";
  import Icon from "./Icon.svelte";

  // Updates are required: there's no close button. The update starts by itself.
  let { update }: { update: LauncherUpdate } = $props();

  let busy = $state(false);
  let error = $state("");
  const p = $derived(app.updateProgress);
  const pct = $derived(p && p.total ? Math.min(100, Math.round((p.done / p.total) * 100)) : 0);

  async function start() {
    if (busy || !update.canInstall) return;
    busy = true;
    error = "";
    app.updateProgress = null;
    try {
      await api.installUpdate(); // restarts the launcher when it's done
    } catch (e) {
      error = String(e);
    } finally {
      busy = false;
    }
  }

  onMount(start);
</script>

<div class="backdrop" transition:fade={{ duration: 200 }}></div>
<div class="sheet card" role="dialog" aria-modal="true" aria-label="Updating Glade" transition:scale={{ duration: 240, start: 0.96 }}>
  <span class="badge"><Icon name="download" size={22} /></span>
  <h2>Glade {update.version} is here</h2>
  <p class="muted">You have {update.current}. Glade keeps itself up to date so everyone plays on the same version.</p>
  {#if update.notes}<p class="notes selectable">{update.notes}</p>{/if}

  {#if update.canInstall}
    {#if error}
      <p class="err selectable">{error}</p>
      <div class="row">
        <button class="btn primary" onclick={start}><Icon name="refresh" size={13} /> Try again</button>
        {#if update.page}<button class="btn ghost" onclick={() => api.openUrl(update.page)}>Download it myself</button>{/if}
      </div>
    {:else if p?.stage === "install"}
      <p class="stage"><span class="spinner"></span> Installing… enter your password if your computer asks for it.</p>
    {:else}
      <div class="bar"><span style:width="{pct}%"></span></div>
      <p class="stage faint">{busy ? (p ? `Downloading ${bytes(p.done)} of ${bytes(p.total)}` : "Starting the download…") : "Getting ready…"}</p>
    {/if}
  {:else}
    <p class="muted">Download the new version and install it, then open Glade again.</p>
    <div class="row">
      <button class="btn primary" onclick={() => api.openUrl(update.page)}><Icon name="download" size={13} /> Download Glade {update.version}</button>
    </div>
  {/if}
</div>

<style>
  .backdrop { position: fixed; inset: 0; z-index: 80; background: rgba(8, 9, 11, 0.7); backdrop-filter: blur(10px) saturate(1.1); }
  .sheet {
    position: fixed; z-index: 81; left: 50%; top: 50%; translate: -50% -50%; width: 440px; max-width: calc(100vw - 48px);
    display: grid; gap: 12px; justify-items: center; text-align: center; padding: 30px 28px 26px;
    background: var(--glass-strong); backdrop-filter: var(--glass-blur); -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border); border-radius: 22px;
    box-shadow: 0 30px 80px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.05);
  }
  .badge { width: 52px; height: 52px; border-radius: 16px; display: grid; place-items: center; background: rgba(47, 191, 106, 0.12); color: var(--accent); }
  h2 { font: 700 26px var(--display); letter-spacing: -0.01em; }
  .notes { font-size: 14px; line-height: 1.5; color: var(--text); white-space: pre-line; max-height: 160px; overflow-y: auto; }
  .bar { width: 100%; height: 8px; border-radius: 99px; background: rgba(47, 191, 106, 0.12); overflow: hidden; }
  .bar span { display: block; height: 100%; border-radius: 99px; background: var(--accent); transition: width 0.2s ease; }
  .stage { font-size: 14px; display: flex; gap: 8px; align-items: center; }
  .err { font-size: 14px; color: var(--danger); }
  .row { display: flex; gap: 8px; flex-wrap: wrap; justify-content: center; }
</style>
