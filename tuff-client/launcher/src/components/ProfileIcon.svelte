<script lang="ts">
  import { profileIcon } from "../lib/icons";
  let { name, size = 36 }: { name: string; size?: number } = $props();
</script>

<span class="tile" style:width="{size}px" style:height="{size}px">
  <svg width={size * 0.62} height={size * 0.62} viewBox="0 0 8 8" shape-rendering="crispEdges" aria-hidden="true">
    {@html profileIcon(name)}
  </svg>
</span>

<style>
  .tile {
    display: grid; place-items: center; flex: none;
    background: var(--surface-2);
    border: 1px solid var(--border-strong);
    border-radius: 6px;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.04);
  }
</style>
