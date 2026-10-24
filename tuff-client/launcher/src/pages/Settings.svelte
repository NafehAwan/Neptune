<script lang="ts">
  import Icon from "../components/Icon.svelte";
  import { api, pickJava } from "../lib/api";
  import { app, checkForUpdates, updateSettings } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { OnLaunch } from "../lib/types";

  const s = $derived(app.settings!);
  const maxMemory = $derived(Math.max(2048, Math.floor(((app.info?.totalMemoryMb ?? 8192) - 1024) / 512) * 512));
  let clientId = $state(app.settings?.msClientId ?? "");
  let discordId = $state(app.settings?.discordAppId ?? "");

  async function saveClientId() {
    await updateSettings({ msClientId: clientId.trim() || null });
    app.info = await api.appInfo();
    toast(clientId.trim() ? "Microsoft client ID saved" : "Using the built-in client ID", "success");
  }

  async function saveDiscord() {
    await updateSettings({ discordAppId: discordId.trim() || null });
    app.info = await api.appInfo();
    toast("Discord app ID saved", "success");
  }

  async function chooseJava() {
    const path = await pickJava();
    if (path) await updateSettings({ javaPath: path });
  }

  const launchOptions: { id: OnLaunch; label: string }[] = [
    { id: "minimize", label: "Minimize" },
    { id: "hide", label: "Hide" },
    { id: "keep", label: "Stay open" },
  ];
</script>

{#snippet toggle(label: string, hint: string, checked: boolean, onchange: (v: boolean) => void)}
  <div class="setting">
    <div class="grow"><p class="label">{label}</p><p class="hint">{hint}</p></div>
    <label class="switch"><input type="checkbox" {checked} onchange={(e) => onchange(e.currentTarget.checked)} /><span></span></label>
  </div>
{/snippet}

<div class="page">
  <div class="page-head">
    <div><h1>Settings</h1><p class="muted">Changes save automatically.</p></div>
  </div>

  <div class="sections">
    <section class="card rise" style="--i: 0">
      <h3>Launcher</h3>
      <div class="setting">
        <div class="grow"><p class="label">When the game starts</p><p class="hint">What the launcher window does while you play. It comes back when the game closes.</p></div>
        <div class="seg">
          {#each launchOptions as o}<button class:on={s.onLaunch === o.id} onclick={() => updateSettings({ onLaunch: o.id })}>{o.label}</button>{/each}
        </div>
      </div>
      {@render toggle("Glade hoodie", "Dress your character on Home in the Glade hoodie and headphones, over any skin.", s.gladeHoodie, (v) => updateSettings({ gladeHoodie: v }))}
      {@render toggle("Reduce motion", "Stops the character animation and shortens transitions.", s.lowMotion, (v) => updateSettings({ lowMotion: v }))}
      {@render toggle("Show snapshots", "List snapshot versions when creating profiles.", s.showSnapshots, (v) => updateSettings({ showSnapshots: v }))}
    </section>

    <section class="card rise" style="--i: 1">
      <h3>Game</h3>
      <div class="setting col">
        <div class="row"><p class="label grow">Default memory for new profiles</p><span class="mono val">{(s.defaultMemoryMb / 1024).toFixed(1)} GB</span></div>
        <input type="range" min="1024" max={maxMemory} step="512" value={s.defaultMemoryMb} onchange={(e) => updateSettings({ defaultMemoryMb: Number(e.currentTarget.value) })} />
        <p class="hint">Your system has {((app.info?.totalMemoryMb ?? 0) / 1024).toFixed(1)} GB. Glade suggests {((app.info?.suggestedMemoryMb ?? 0) / 1024).toFixed(1)} GB.</p>
      </div>
      <div class="setting">
        <div class="grow"><p class="label">Java</p><p class="hint">{s.javaPath ?? "Automatic: Glade downloads the exact Java each version needs."}</p></div>
        <div class="row">
          <button class="btn small" onclick={chooseJava}>Browse</button>
          {#if s.javaPath}<button class="btn small ghost" onclick={() => updateSettings({ javaPath: null })}>Use automatic</button>{/if}
        </div>
      </div>
      <div class="setting">
        <div class="grow"><p class="label">Parallel downloads</p><p class="hint">Lower this on slow or unstable connections.</p></div>
        <select class="input narrow" value={s.concurrentDownloads} onchange={(e) => updateSettings({ concurrentDownloads: Number(e.currentTarget.value) })}>
          {#each [4, 8, 16, 24, 32] as n}<option value={n}>{n}</option>{/each}
        </select>
      </div>
    </section>

    <section class="card rise" style="--i: 2">
      <h3>Accounts</h3>
      <div class="setting col">
        <p class="label">Microsoft client ID</p>
        <p class="hint">
          Leave this empty: Glade signs in the same way as the official Minecraft launcher.
          Only paste a client ID if you have your own Azure app that Mojang has approved.
        </p>
        <div class="row">
          <input class="input mono grow" bind:value={clientId} placeholder="Built-in (recommended)" />
          <button class="btn small" onclick={saveClientId}>Save</button>
        </div>
        <button class="btn ghost small linkbtn" onclick={() => api.openUrl("https://learn.microsoft.com/entra/identity-platform/quickstart-register-app")}>
          <Icon name="link" size={10} /> How to register an app
        </button>
      </div>
    </section>

    <section class="card rise" style="--i: 3">
      <h3>Discord</h3>
      {@render toggle("Rich Presence", "Show what you're playing on your Discord profile.", s.discordRpc, (v) => updateSettings({ discordRpc: v }))}
      <div class="setting col">
        <p class="label">Discord application ID</p>
        <p class="hint">{app.info?.discordConfigured ? "Set. Paste a different ID to override." : "Create an application at discord.com/developers and paste its ID."}</p>
        <div class="row">
          <input class="input mono grow" bind:value={discordId} placeholder="123456789012345678" />
          <button class="btn small" onclick={saveDiscord}>Save</button>
        </div>
      </div>
    </section>

    <section class="card rise" style="--i: 4">
      <h3>Storage</h3>
      <div class="setting">
        <div class="grow"><p class="label">Data folder</p><p class="hint mono selectable">{app.info?.dataDir}</p></div>
        <button class="btn small" onclick={() => api.openFolder()}><Icon name="folder" size={12} /> Open</button>
      </div>
    </section>

    <section class="card about rise" style="--i: 5">
      <h3>About</h3>
      <p><span class="pixel brand">Glade</span> <span class="faint">v{app.info?.version}</span>
        <button class="btn small" onclick={() => checkForUpdates(true)}><Icon name="refresh" size={12} /> Check for updates</button></p>
      <p class="hint">Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.</p>
      <p class="hint keys">Shortcuts: <kbd>Ctrl</kbd>+<kbd>1</kbd>–<kbd>8</kbd> switch pages · <kbd>Ctrl</kbd>+<kbd>Enter</kbd> play</p>
    </section>
  </div>
</div>

<style>
  .sections { display: grid; gap: 14px; max-width: 760px; }
  section { padding: 18px 20px 8px; }
  section h3 { margin-bottom: 6px; }
  .setting { display: flex; align-items: center; gap: 18px; padding: 12px 0; border-bottom: 1px solid var(--border); }
  .setting:last-child { border-bottom: 0; }
  .setting.col { display: grid; gap: 8px; }
  .label { font-weight: 600; font-size: 15.5px; }
  .hint { font-size: 14px; color: var(--muted); margin-top: 2px; }
  .val { color: var(--accent-hover); }
  .narrow { width: 90px; }
  .linkbtn { justify-self: start; padding-left: 0; }
  .about { padding-bottom: 18px; display: grid; gap: 6px; }
  .brand { color: var(--accent-hover); }
  kbd { font: 12.5px var(--mono); padding: 1px 5px; border-radius: 4px; background: var(--surface-3); border: 1px solid var(--border-strong); }
</style>
