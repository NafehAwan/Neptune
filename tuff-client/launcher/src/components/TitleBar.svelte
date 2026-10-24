<script lang="ts">
  import { api, windowAction } from "../lib/api";
  import { RELEASES } from "../lib/links";
  import AccountMenu from "./AccountMenu.svelte";
  import Icon from "./Icon.svelte";
  import QuickPlay from "./QuickPlay.svelte";
</script>

<header class="titlebar" data-tauri-drag-region>
  <QuickPlay />
  <AccountMenu />
  <div class="controls">
    <button class="ctl" onclick={() => api.openUrl(RELEASES)} aria-label="What's new" title="What's new"><Icon name="mail" size={15} /></button>
    <button class="ctl" onclick={() => windowAction("minimize")} aria-label="Minimize"><Icon name="minimize" size={13} /></button>
    <button class="ctl close" onclick={() => windowAction("close")} aria-label="Close"><Icon name="close" size={13} /></button>
  </div>
</header>

<style>
  .titlebar { position: absolute; top: 0; left: 0; right: 0; height: 56px; z-index: 20; display: flex; align-items: center; gap: 8px; padding: 0 12px 0 0; }
  .controls { display: flex; gap: 8px; }
  .controls .ctl:first-child { margin-right: 6px; }
  .ctl { width: 40px; height: 42px; display: grid; place-items: center; background: var(--surface); border: 1px solid var(--border); border-radius: 11px; color: var(--muted); cursor: pointer; transition: background var(--fast), color var(--fast); }
  .ctl:hover { background: var(--surface-3); color: var(--text); }
  .ctl.close:hover { background: var(--danger); border-color: var(--danger); color: #fff; }
</style>
