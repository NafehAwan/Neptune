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
    background: rgba(255, 255, 255, 0.7);
    border: 1px solid rgba(31, 42, 34, 0.08);
    border-radius: 9px;
    box-shadow: 0 2px 6px rgba(30, 60, 40, 0.08), inset 0 1px 0 rgba(255, 255, 255, 0.9);
  }
</style>
