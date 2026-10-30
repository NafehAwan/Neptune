<script lang="ts">
  import { uiIcon } from "../lib/icons";
  import { LUCIDE } from "../lib/lucide";
  let { name, size = 16 }: { name: string; size?: number } = $props();
  const line = $derived(LUCIDE[name]);
</script>

{#if line}
  <!-- Lucide line icons, as in the Grove Glass mockups -->
  <svg class="icon" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">
    {@html line}
  </svg>
{:else}
  <svg class="icon" width={size} height={size} viewBox="0 0 12 12" shape-rendering="crispEdges" aria-hidden="true">
    {@html uiIcon(name)}
  </svg>
{/if}

<style>
  .icon { flex: none; display: block; }
</style>
