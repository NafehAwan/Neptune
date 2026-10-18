<script lang="ts">
  import { windowAction } from "../lib/api";
  import AccountMenu from "./AccountMenu.svelte";
  import Icon from "./Icon.svelte";
  let { transparent = false }: { transparent?: boolean } = $props();
</script>

<header class="titlebar" class:transparent data-tauri-drag-region>
  <div class="spacer" data-tauri-drag-region></div>
  <AccountMenu />
  <div class="controls">
    <button class="ctl" onclick={() => windowAction("minimize")} aria-label="Minimize"><Icon name="minimize" size={12} /></button>
    <button class="ctl" onclick={() => windowAction("maximize")} aria-label="Maximize"><Icon name="maximize" size={12} /></button>
    <button class="ctl close" onclick={() => windowAction("close")} aria-label="Close"><Icon name="close" size={12} /></button>
  </div>
</header>

<style>
  .titlebar {
    position: absolute; top: 0; left: 0; right: 0; height: 48px; z-index: 20;
    display: flex; align-items: center; gap: 10px; padding: 0 8px 0 20px;
    background: transparent;
  }
  .titlebar.transparent { background: transparent; }
  .spacer { flex: 1; height: 100%; }
  .controls { display: flex; gap: 2px; margin-left: 6px; }
  .ctl {
    width: 34px; height: 30px; display: grid; place-items: center;
    background: transparent; border: 0; border-radius: 6px; color: var(--muted); cursor: pointer;
    transition: background var(--fast), color var(--fast);
  }
  .ctl:hover { background: rgba(255, 255, 255, 0.7); color: var(--text); }
  .ctl.close:hover { background: var(--danger); color: white; }
</style>
