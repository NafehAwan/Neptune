<script lang="ts">
  import { fly } from "svelte/transition";
  import { downloaded, launchFraction } from "../lib/progress";
  import { app } from "../lib/state.svelte";
  import ProgressBar from "./ProgressBar.svelte";

  /** inline: sits in the page (Home, above the Play bar); otherwise floats in the corner. */
  let { inline = false }: { inline?: boolean } = $props();

  const p = $derived(app.progress);
  const profile = $derived(app.profiles.find((x) => x.id === app.launching));
  const fraction = $derived(p ? launchFraction(p) : null);
  const detail = $derived(p ? downloaded(p, app.speed) ?? (p.total > 1 ? `${p.done.toLocaleString()} of ${p.total.toLocaleString()}` : null) : null);
</script>

{#if p}
  <div class="dl" class:inline role="status" aria-live="polite" transition:fly={{ y: 12, duration: 220 }}>
    <div class="row">
      <span class="spinner"></span>
      <span class="title ellipsis">Getting {profile?.name ?? "Minecraft"} ready</span>
      {#if fraction !== null}<span class="pct mono">{Math.floor(fraction * 100)}%</span>{/if}
    </div>
    <div class="stage ellipsis">{p.stage}</div>
    <ProgressBar value={fraction} />
    {#if detail}<div class="detail mono ellipsis">{detail}</div>{/if}
  </div>
{/if}

<style>
  .dl {
    position: absolute; right: 22px; bottom: 22px; z-index: 30; width: 320px; display: grid; gap: 7px; padding: 14px 16px;
    border-radius: 16px; background: rgba(251, 251, 248, 0.86); border: 1px solid var(--glass-border);
    backdrop-filter: var(--glass-blur); -webkit-backdrop-filter: var(--glass-blur); box-shadow: var(--glass-shadow), 0 18px 50px rgba(30, 60, 40, 0.16);
  }
  .dl.inline { position: static; width: 360px; background: rgba(255, 255, 255, 0.62); }
  .row { display: flex; align-items: center; gap: 9px; }
  .row .spinner { color: var(--accent); width: 13px; height: 13px; }
  .title { font-weight: 600; font-size: 13.5px; flex: 1; min-width: 0; }
  .pct { font-size: 12.5px; font-weight: 600; color: var(--accent-deep); }
  .stage { font-size: 12.5px; color: var(--text); }
  .detail { font-size: 11.5px; color: var(--muted); }
</style>
