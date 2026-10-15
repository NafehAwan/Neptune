<script lang="ts">
  import type { Account } from "../lib/types";
  import Icon from "./Icon.svelte";
  let { account, size = 24 }: { account: Account | null; size?: number } = $props();
  let failed = $state(false);
  $effect(() => { account?.id; failed = false; });
</script>

<span class="avatar" style:width="{size}px" style:height="{size}px">
  {#if account && account.kind === "msa" && !failed}
    <img src="https://mc-heads.net/avatar/{account.id}/{size * 2}" alt="" width={size} height={size} onerror={() => (failed = true)} />
  {:else}
    <Icon name="user" size={Math.round(size * 0.6)} />
  {/if}
</span>

<style>
  .avatar { display: grid; place-items: center; flex: none; border-radius: 4px; overflow: hidden; background: var(--surface-3); color: var(--muted); }
  img { image-rendering: pixelated; display: block; }
</style>
