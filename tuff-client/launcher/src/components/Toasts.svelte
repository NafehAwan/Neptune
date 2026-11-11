<script lang="ts">
  import { flip } from "svelte/animate";
  import { fly } from "svelte/transition";
  import { dismiss, toasts } from "../lib/toast.svelte";
  import Icon from "./Icon.svelte";
</script>

<div class="toasts" aria-live="polite">
  {#each toasts as t (t.id)}
    <div class="toast {t.kind}" animate:flip={{ duration: 200 }} in:fly={{ y: 12, duration: 220 }} out:fly={{ x: 30, duration: 180 }}>
      <span class="bar"></span>
      <p class="selectable">{t.message}</p>
      <button class="btn ghost small icon" onclick={() => dismiss(t.id)} aria-label="Dismiss"><Icon name="close" size={10} /></button>
    </div>
  {/each}
</div>

<style>
  .toasts { position: fixed; right: 18px; bottom: 18px; z-index: 80; display: grid; gap: 8px; width: 340px; }
  .toast {
    display: flex; align-items: flex-start; gap: 10px;
    background: var(--glass-strong); backdrop-filter: var(--glass-blur); -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border); border-radius: 14px;
    padding: 10px 6px 10px 0; overflow: hidden;
    box-shadow: 0 14px 34px rgba(0, 0, 0, 0.4);
  }
  .bar { width: 3px; align-self: stretch; background: var(--muted); flex: none; }
  .success .bar { background: var(--online); }
  .error .bar { background: var(--danger); }
  p { flex: 1; font-size: 15px; padding-top: 4px; }
</style>
