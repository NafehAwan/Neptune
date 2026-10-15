<script lang="ts">
  import { uiIcon } from "../lib/icons";
  let { name, size = 16 }: { name: string; size?: number } = $props();
</script>

<svg class="icon" width={size} height={size} viewBox="0 0 12 12" shape-rendering="crispEdges" aria-hidden="true">
  {@html uiIcon(name)}
</svg>

<style>
  .icon { flex: none; display: block; }
</style>
