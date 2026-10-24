<script lang="ts">
  import type { Snippet } from "svelte";
  import { fade, scale } from "svelte/transition";
  import Icon from "./Icon.svelte";

  let { title, onclose, width = 460, children, footer }: {
    title: string;
    onclose: () => void;
    width?: number;
    children: Snippet;
    footer?: Snippet;
  } = $props();

  function key(e: KeyboardEvent) {
    if (e.key === "Escape") onclose();
  }
</script>

<svelte:window onkeydown={key} />

<div class="backdrop" transition:fade={{ duration: 150 }} onclick={onclose} role="presentation"></div>
<div class="modal card" style:width="{width}px" role="dialog" aria-modal="true" aria-label={title} transition:scale={{ duration: 200, start: 0.96 }}>
  <header>
    <h2>{title}</h2>
    <button class="btn ghost small icon" onclick={onclose} aria-label="Close"><Icon name="close" size={12} /></button>
  </header>
  <div class="body">{@render children()}</div>
  {#if footer}<footer>{@render footer()}</footer>{/if}
</div>

<style>
  .backdrop { position: fixed; inset: 0; background: rgba(8, 9, 11, 0.6); backdrop-filter: blur(6px) saturate(1.1); z-index: 50; }
  .modal {
    position: fixed; z-index: 51; left: 50%; top: 50%; translate: -50% -50%;
    max-width: calc(100vw - 48px); max-height: calc(100vh - 80px);
    display: flex; flex-direction: column;
    background: var(--glass-strong); backdrop-filter: var(--glass-blur); -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border); border-radius: 20px;
    box-shadow: 0 30px 80px rgba(0, 0, 0, 0.5), inset 0 1px 0 rgba(255, 255, 255, 0.05);
  }
  header { display: flex; align-items: center; justify-content: space-between; padding: 18px 18px 6px 22px; }
  .body { padding: 12px 22px 18px; overflow-y: auto; display: grid; gap: 14px; }
  footer { display: flex; justify-content: flex-end; gap: 8px; padding: 14px 22px; border-top: 1px solid rgba(255, 255, 255, 0.07); }
</style>
