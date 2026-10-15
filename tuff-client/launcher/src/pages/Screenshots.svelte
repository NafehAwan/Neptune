<script lang="ts">
  import { onMount } from "svelte";
  import { fade, scale } from "svelte/transition";
  import Icon from "../components/Icon.svelte";
  import { api, fileSrc } from "../lib/api";
  import { app } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { Screenshot } from "../lib/types";

  let shots = $state<(Screenshot & { src: string })[]>([]);
  let loaded = $state(false);
  let open = $state<number | null>(null);
  let profileFilter = $state<string>("all");
  const visible = $derived(profileFilter === "all" ? shots : shots.filter((s) => s.profileId === profileFilter));

  onMount(load);

  async function load() {
    const list = await api.listScreenshots();
    shots = await Promise.all(list.map(async (s) => ({ ...s, src: await fileSrc(s.path) })));
    loaded = true;
  }

  async function remove(s: Screenshot) {
    await api.deleteScreenshot(s.path);
    shots = shots.filter((x) => x.path !== s.path);
    open = null;
    toast("Screenshot deleted");
  }

  function key(e: KeyboardEvent) {
    const current = open;
    if (current === null) return;
    if (e.key === "Escape") open = null;
    if (e.key === "ArrowRight") open = Math.min(visible.length - 1, current + 1);
    if (e.key === "ArrowLeft") open = Math.max(0, current - 1);
  }
</script>

<svelte:window onkeydown={key} />

<div class="page">
  <div class="page-head">
    <div>
      <h1>Screenshots</h1>
      <p class="muted">Press F2 in game. Screenshots from every profile show up here.</p>
    </div>
    <select class="input filter" bind:value={profileFilter}>
      <option value="all">All profiles</option>
      {#each app.profiles as p (p.id)}<option value={p.id}>{p.name}</option>{/each}
    </select>
  </div>

  {#if loaded && !visible.length}
    <div class="empty"><p class="pixel">No screenshots yet</p><p>Press F2 while playing to take one.</p></div>
  {:else}
    <div class="grid">
      {#each visible as s, i (s.path)}
        <button class="shot" onclick={() => (open = i)}>
          <img src={s.src} alt="" loading="lazy" />
          <span class="cap">{s.profileName} · {new Date(s.modified * 1000).toLocaleDateString()}</span>
        </button>
      {/each}
    </div>
  {/if}
</div>

{#if open !== null && visible[open]}
  {@const s = visible[open]}
  <div class="lightbox" transition:fade={{ duration: 150 }} onclick={() => (open = null)} role="presentation">
    <img src={s.src} alt="" transition:scale={{ duration: 200, start: 0.97 }} onclick={(e) => e.stopPropagation()} role="presentation" />
    <div class="lb-bar card" onclick={(e) => e.stopPropagation()} role="presentation">
      <span class="muted">{s.profileName} · {new Date(s.modified * 1000).toLocaleString()}</span>
      <button class="btn small" onclick={() => api.openScreenshot(s.path)}><Icon name="folder" size={12} /> Open</button>
      <button class="btn small" onclick={() => { navigator.clipboard.writeText(s.path); toast("Path copied"); }}><Icon name="copy" size={12} /> Copy path</button>
      <button class="btn small danger" onclick={() => remove(s)}><Icon name="trash" size={12} /> Delete</button>
    </div>
  </div>
{/if}

<style>
  .filter { width: 200px; }
  .grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(240px, 1fr)); gap: 12px; }
  .shot { position: relative; padding: 0; border: 1px solid var(--border); border-radius: 9px; overflow: hidden; background: var(--surface); cursor: zoom-in; aspect-ratio: 16 / 9; }
  .shot img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform var(--med) var(--ease); }
  .shot:hover img { transform: scale(1.04); }
  .cap { position: absolute; left: 0; right: 0; bottom: 0; padding: 18px 10px 7px; font-size: 13px; text-align: left; color: var(--text); background: linear-gradient(transparent, rgba(0, 0, 0, 0.75)); opacity: 0; transition: opacity var(--fast); }
  .shot:hover .cap { opacity: 1; }
  .lightbox { position: fixed; inset: 0; z-index: 60; background: rgba(6, 7, 6, 0.9); display: grid; place-items: center; padding: 40px 40px 90px; }
  .lightbox img { max-width: 100%; max-height: 100%; border-radius: 6px; box-shadow: 0 30px 80px rgba(0, 0, 0, 0.6); }
  .lb-bar { position: fixed; bottom: 24px; left: 50%; translate: -50% 0; display: flex; align-items: center; gap: 8px; padding: 8px 8px 8px 14px; font-size: 14.5px; }
</style>
