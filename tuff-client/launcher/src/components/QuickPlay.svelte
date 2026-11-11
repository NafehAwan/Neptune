<script lang="ts">
  import { onMount } from "svelte";
  import { compact } from "../lib/format";
  import { QUICK_SERVERS, loadQuickPlay, pings } from "../lib/quickplay.svelte";
  import { app, play, selectedProfile } from "../lib/state.svelte";
  import Icon from "./Icon.svelte";

  onMount(loadQuickPlay);
  const busy = $derived(app.launching !== null || app.running !== null);

  function join(address: string) {
    const p = selectedProfile();
    if (p && !busy) play(p.id, address);
  }
</script>

<div class="qp" data-tauri-drag-region>
  <span class="label"><Icon name="grid" size={14} /> QUICK PLAY</span>
  {#each QUICK_SERVERS as s (s.address)}
    {@const ping = pings[s.address]}
    <button class="srv" onclick={() => join(s.address)} disabled={busy || !selectedProfile()} aria-label="Join {s.name}">
      {#if ping && ping !== "offline" && ping.favicon}
        <img src={ping.favicon} alt="" width="30" height="30" />
      {:else}
        <span class="letter" class:off={ping === "offline"}>{s.name[0]}</span>
      {/if}
      <span class="tip">
        <b>{s.name}</b>
        {#if ping === "offline"}<small class="gone">Offline</small>
        {:else if ping}<small>● {compact(ping.online)} online</small>
        {:else}<small class="gone">{s.address}</small>{/if}
      </span>
    </button>
  {/each}
  <div class="fill" data-tauri-drag-region></div>
</div>

<style>
  .qp { flex: 1; min-width: 0; height: 42px; display: flex; align-items: center; gap: 4px; padding: 0 8px 0 14px; border-radius: 11px; background: var(--surface); border: 1px solid var(--border); }
  .label { display: flex; align-items: center; gap: 8px; font: 800 10.5px var(--font); letter-spacing: 0.06em; color: var(--text); padding-right: 14px; margin-right: 6px; height: 22px; border-right: 1px solid var(--border-strong); white-space: nowrap; }
  .fill { flex: 1; height: 100%; }
  .srv { position: relative; width: 46px; height: 34px; border-radius: 8px; border: 0; background: transparent; display: grid; place-items: center; cursor: pointer; flex: none; transition: background var(--fast), box-shadow var(--fast); }
  .srv:hover:not(:disabled) { background: var(--surface-3); box-shadow: inset 0 -2px 0 var(--accent); }
  .srv:disabled { cursor: default; opacity: 0.6; }
  .srv img { width: 28px; height: 28px; border-radius: 6px; image-rendering: auto; }
  .letter { width: 28px; height: 28px; border-radius: 7px; display: grid; place-items: center; font: 800 14px var(--font); color: #fff; background: linear-gradient(160deg, #3a3f47, #262a30); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.12); }
  .letter.off { opacity: 0.45; }
  .tip { position: absolute; top: calc(100% + 10px); left: 50%; transform: translateX(-50%) translateY(-4px); z-index: 30; display: flex; gap: 8px; align-items: center; white-space: nowrap;
    padding: 6px 10px; border-radius: 8px; background: #0d0e10; border: 1px solid var(--border-strong); box-shadow: 0 10px 24px rgba(0, 0, 0, 0.45); font: 600 12px var(--font); color: var(--text);
    opacity: 0; pointer-events: none; transition: opacity var(--fast), transform var(--fast) var(--ease); }
  .tip small { color: var(--online); font: 600 11px var(--font); }
  .tip small.gone { color: var(--muted); }
  .srv:hover .tip { opacity: 1; transform: translateX(-50%); }
</style>
