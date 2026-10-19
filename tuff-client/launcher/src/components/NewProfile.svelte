<script lang="ts">
  import { onMount } from "svelte";
  import { fly } from "svelte/transition";
  import { api } from "../lib/api";
  import { app, refreshProfiles, selectProfile } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { GameVersion, Preset, Profile } from "../lib/types";
  import { gladeRuns } from "../lib/format";
  import Icon from "./Icon.svelte";
  import Modal from "./Modal.svelte";
  import ProfileIcon from "./ProfileIcon.svelte";

  let { onclose, oncustom, oncreated }: { onclose: () => void; oncustom: () => void; oncreated?: (p: Profile) => void } = $props();

  let presets = $state<Preset[]>([]);
  let picked = $state("performance");
  let busy = $state<string | null>(null);
  // presets are Fabric; any Minecraft version Fabric supports
  const gladeVersion = app.info?.tuffMcVersion ?? "26.3";
  let versions = $state<GameVersion[]>([]);
  let version = $state(gladeVersion);
  let unstable = $state(false);
  const shownVersions = $derived(versions.filter((v) => v.stable || unstable || v.version === version));
  const withGlade = (v: string) => gladeRuns(app.info?.gladeMinecraft, "fabric", v);

  onMount(async () => {
    presets = await api.listPresets().catch(() => []);
    versions = await api.presetGameVersions().catch(() => [{ version: gladeVersion, stable: true }]);
    if (!versions.some((v) => v.version === version)) version = versions.find((v) => v.stable)?.version ?? gladeVersion;
  });

  const owned = (id: string) => app.profiles.filter((p) => p.preset === id).length;

  async function create() {
    const preset = presets.find((p) => p.id === picked);
    if (!preset || busy) return;
    busy = preset.id;
    try {
      const r = await api.createPresetProfile(preset.id, version);
      await refreshProfiles();
      await selectProfile(r.profile.id);
      const missing = r.skipped.length ? ` ${r.skipped.length} not available for ${r.profile.gameVersion} yet.` : "";
      toast(`${r.profile.name} is ready with ${r.installed.length} mods and packs.${missing}`, "success", 6000);
      oncreated?.(r.profile);
      onclose();
    } catch (e) {
      toast(e, "error");
    } finally {
      busy = null;
    }
  }
</script>

<Modal title="New profile" {onclose} width={760}>
  <p class="muted lead">Start from a Glade preset or build your own. Every profile gets its own folder, so worlds, mods and settings never mix.</p>

  <div class="grid">
    {#each presets as p, i (p.id)}
      <button class="preset" class:on={picked === p.id} onclick={() => (picked = p.id)} disabled={!!busy}
        in:fly={{ y: 10, duration: 320, delay: 40 * i }}>
        <div class="top">
          <span class="tile"><ProfileIcon name={p.icon} size={40} /></span>
          <span class="name">{p.name}</span>
          {#if picked === p.id}<span class="check"><Icon name="check" size={14} /></span>{/if}
        </div>
        <p class="tag">{p.tagline}</p>
        <ul class="hl">{#each p.highlights as h}<li>{h}</li>{/each}</ul>
        <div class="meta mono">
          {p.mods.length} mods{#if p.shaders.length} · shaders{/if}{#if p.resourcepacks.length} · resource pack{/if}
          {#if owned(p.id)}<span class="have"> · you have {owned(p.id)}</span>{/if}
        </div>
      </button>
    {/each}
    <button class="preset custom" onclick={() => { onclose(); oncustom(); }} disabled={!!busy}
      in:fly={{ y: 10, duration: 320, delay: 40 * presets.length }}>
      <div class="top">
        <span class="tile plus"><Icon name="plus" size={18} /></span>
        <span class="name">Custom</span>
      </div>
      <p class="tag">Any Minecraft version with Vanilla, Fabric, Quilt, Forge or NeoForge. Pick your own mods from the Mods page afterwards.</p>
    </button>
  </div>


  {#snippet footer()}
    <div class="vpick">
      <label class="vfield">
        <span>Minecraft</span>
        <select class="input" bind:value={version} disabled={!!busy || !versions.length} title="Minecraft version (Fabric)">
          {#if !versions.length}<option value={version}>{version}</option>{/if}
          {#each shownVersions as v (v.version)}<option value={v.version}>{v.version}{v.stable ? "" : " (unstable)"}{withGlade(v.version) ? " · Glade" : ""}</option>{/each}
        </select>
      </label>
      <label class="unstable"><input type="checkbox" bind:checked={unstable} disabled={!!busy} /> Unstable</label>
      <p class="vnote">
        {#if withGlade(version)}Fabric · includes the Glade mod, installed and checked before every launch{:else}Fabric · Glade's in-game mod isn't available for {version} yet, so it gets the mods only; mods without a {version} build are skipped{/if}
      </p>
    </div>
    <button class="btn ghost" onclick={onclose} disabled={!!busy}>Cancel</button>
    <button class="btn primary" onclick={create} disabled={!!busy || !presets.length}>
      {#if busy}<span class="spinner"></span> Installing mods…{:else}<Icon name="spark" size={14} /> Create {presets.find((p) => p.id === picked)?.name ?? ""}{/if}
    </button>
  {/snippet}
</Modal>

<style>
  .lead { font-size: 13.5px; margin-top: -4px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 12px; }
  .preset {
    position: relative; display: flex; flex-direction: column; gap: 8px; padding: 14px; text-align: left; cursor: pointer;
    border-radius: 16px; background: rgba(255, 255, 255, 0.45); border: 1px solid rgba(255, 255, 255, 0.75);
    color: var(--text); font: inherit;
    transition: transform var(--med) var(--spring), background var(--fast), box-shadow var(--med), border-color var(--fast);
  }
  .preset:hover:not(:disabled) { transform: translateY(-2px); background: rgba(255, 255, 255, 0.7); box-shadow: 0 12px 28px rgba(30, 60, 40, 0.12); }
  .preset:active:not(:disabled) { transform: translateY(0) scale(0.985); }
  .preset.on { background: rgba(255, 255, 255, 0.82); border-color: var(--accent); box-shadow: 0 0 0 3px rgba(47, 107, 79, 0.14), 0 12px 28px rgba(30, 60, 40, 0.12); }
  .preset:disabled { cursor: default; }
  .top { display: flex; align-items: center; gap: 10px; }
  .tile { display: grid; place-items: center; }
  .tile.plus { width: 40px; height: 40px; border-radius: 10px; background: rgba(47, 107, 79, 0.12); color: var(--accent); }
  .name { font: italic 600 20px var(--display); letter-spacing: -0.01em; }
  .check { margin-left: auto; width: 22px; height: 22px; border-radius: 50%; display: grid; place-items: center; background: var(--accent); color: #fff; }
  .tag { font-size: 12.5px; color: var(--muted); line-height: 1.45; }
  .hl { list-style: none; margin: 0; padding: 0; display: flex; flex-wrap: wrap; gap: 4px; }
  .hl li { font-size: 11px; font-weight: 600; padding: 3px 8px; border-radius: 999px; background: rgba(47, 107, 79, 0.1); color: var(--accent-deep); }
  .meta { margin-top: auto; font-size: 11px; color: var(--muted); }
  .have { color: var(--accent); }
  .custom { border-style: dashed; border-color: rgba(47, 107, 79, 0.35); }
  .vpick { margin-right: auto; display: grid; grid-template-columns: auto auto; align-items: center; gap: 4px 12px; max-width: 420px; }
  .vfield { display: flex; align-items: center; gap: 8px; font-size: 13px; font-weight: 600; color: var(--muted); }
  .vfield select { width: 190px; height: 36px; }
  .unstable { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--muted); }
  .unstable input { accent-color: var(--accent); }
  .vnote { grid-column: 1 / -1; font-size: 11.5px; color: var(--muted); line-height: 1.35; }
</style>
