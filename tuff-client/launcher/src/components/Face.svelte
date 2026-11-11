<script lang="ts">
  // A flat Minecraft face (front of the head plus the hat layer) from a 64x64 skin.
  let { skin, size = 30 }: { skin: string; size?: number } = $props();
  const scale = $derived(size / 8);
</script>

<span class="face" style:width="{size}px" style:height="{size}px"
  style:background-image="url({skin})" style:background-size="{64 * scale}px" style:background-position="{-8 * scale}px {-8 * scale}px">
  <span style:background-image="url({skin})" style:background-size="{64 * scale}px" style:background-position="{-40 * scale}px {-8 * scale}px"></span>
</span>

<style>
  .face { position: relative; display: block; flex: none; border-radius: 6px; image-rendering: pixelated; overflow: hidden; }
  .face span { position: absolute; inset: 0; image-rendering: pixelated; }
</style>
