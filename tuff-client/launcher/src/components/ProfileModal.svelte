<script lang="ts">
  import { onMount, untrack } from "svelte";
  import { api, pickJava } from "../lib/api";
  import { LOADERS, loaderName } from "../lib/format";
  import { profileIconNames } from "../lib/icons";
  import { app, refreshProfiles } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { LoaderVersion, Profile, VersionEntry } from "../lib/types";
  import Modal from "./Modal.svelte";
  import ProfileIcon from "./ProfileIcon.svelte";

  let { profile = null, onclose, onsaved }: { profile?: Profile | null; onclose: () => void; onsaved?: (p: Profile) => void } = $props();

  const blank = (): Profile => ({
    id: "", name: "", icon: "grass", gameVersion: "", loader: "fabric", loaderVersion: null,
    memoryMb: app.settings?.defaultMemoryMb ?? 4096, jvmArgs: "", javaPath: null, resolution: null,
    created: 0, lastPlayed: null, playTimeSecs: 0, isTuff: false,
  });
  // The editor works on a copy taken when it opens.
  const initial = untrack(() => profile);
  let draft = $state<Profile>(initial ? structuredClone($state.snapshot(initial)) : blank());
  let versions = $state<VersionEntry[]>([]);
  let loaders = $state<LoaderVersion[]>([]);
  let loadersFor = $state("");
  let loadersError = $state("");
  // stable releases only, unless the player asks for snapshots and betas
  let unstable = $state(app.settings?.showSnapshots ?? false);
  let advanced = $state(false);
  let saving = $state(false);
  let versionError = $state("");

  const maxMemory = $derived(Math.max(2048, Math.floor(((app.info?.totalMemoryMb ?? 8192) - 1024) / 512) * 512));
  const shown = $derived(versions.filter((v) => v.kind === "release" || (unstable && v.kind === "snapshot") || v.id === draft.gameVersion));
  const shownLoaders = $derived(loaders.filter((l) => l.stable || unstable || l.version === draft.loaderVersion));
  const latestStable = $derived(loaders.find((l) => l.stable) ?? loaders[0]);

  onMount(async () => {
    try {
      const list = await api.listVersions();
      versions = list.versions;
      if (!draft.gameVersion) draft.gameVersion = list.latestRelease;
    } catch (e) {
      versionError = String(e);
    }
  });

  $effect(() => {
    const game = draft.gameVersion;
    const loader = draft.loader;
    const key = `${loader}:${game}`;
    if (loader === "vanilla" || !game) { loaders = []; loadersFor = key; return; }
    loadersFor = "";
    loadersError = "";
    api.loaderVersions(loader, game).then((l) => {
      if (`${draft.loader}:${draft.gameVersion}` !== key) return;
      loaders = l;
      loadersFor = key;
      if (draft.loaderVersion && !l.some((x) => x.version === draft.loaderVersion)) draft.loaderVersion = null;
    }).catch((e) => { loaders = []; loadersFor = key; loadersError = String(e); });
  });

  function pickLoader(id: typeof draft.loader) {
    if (draft.loader === id) return;
    draft.loader = id;
    draft.loaderVersion = null;
  }

  const unsupported = $derived(draft.loader !== "vanilla" && loadersFor === `${draft.loader}:${draft.gameVersion}` && !loaders.length);

  async function save() {
    saving = true;
    try {
      const saved = await api.saveProfile($state.snapshot(draft) as Profile);
      await refreshProfiles();
      toast(initial ? "Profile saved" : `Created ${saved.name}`, "success");
      onsaved?.(saved);
      onclose();
    } catch (e) {
      toast(e, "error");
    } finally {
      saving = false;
    }
  }

  async function chooseJava() {
    const path = await pickJava();
    if (path) draft.javaPath = path;
  }
</script>

<Modal title={initial ? "Edit profile" : "New profile"} {onclose} width={520}>
  <div class="top">
    <label class="field grow">
      <span>Name</span>
      <!-- svelte-ignore a11y_autofocus -->
      <input class="input" bind:value={draft.name} placeholder="My profile" maxlength="40" autofocus={!initial} />
    </label>
  </div>

  <div class="field">
    <span>Icon</span>
    <div class="icons">
      {#each profileIconNames as name}
        <button class="icon-pick" class:on={draft.icon === name} onclick={() => (draft.icon = name)} aria-label={name}>
          <ProfileIcon {name} size={38} />
        </button>
      {/each}
    </div>
  </div>

  <div class="grid2">
    <label class="field">
      <span>Minecraft version</span>
      <select class="input" bind:value={draft.gameVersion} disabled={!versions.length || draft.isTuff}>
        {#if !versions.length}<option value={draft.gameVersion}>{draft.gameVersion || "Loading…"}</option>{/if}
        {#each shown as v (v.id)}<option value={v.id}>{v.id}{v.kind === "snapshot" ? " (snapshot)" : ""}</option>{/each}
      </select>
    </label>
    {#if draft.loader !== "vanilla"}
      <label class="field">
        <span>{loaderName(draft.loader)} version</span>
        <select class="input" bind:value={draft.loaderVersion} disabled={unsupported}>
          <option value={null}>{latestStable ? `Latest stable (${latestStable.version})` : loadersFor ? "Not available" : "Loading…"}</option>
          {#each shownLoaders as l (l.version)}<option value={l.version}>{l.version}{l.stable ? "" : " (beta)"}</option>{/each}
        </select>
      </label>
    {/if}
  </div>
  <div class="field">
    <span>Mod loader</span>
    <div class="seg loaders">
      {#each LOADERS as l (l.id)}
        <button class:on={draft.loader === l.id} onclick={() => pickLoader(l.id)} disabled={draft.isTuff}>{l.label}</button>
      {/each}
    </div>
  </div>
  {#if versionError}<p class="faint small selectable">{versionError}</p>{/if}
  {#if unsupported}
    <p class="note small">{loaderName(draft.loader)} doesn't support Minecraft {draft.gameVersion} yet. Pick another version or loader.{#if loadersError} <span class="faint selectable">({loadersError})</span>{/if}</p>
  {/if}
  <label class="check"><input type="checkbox" bind:checked={unstable} /> Show unstable versions (snapshots and betas)</label>

  <div class="field">
    <span class="row"><span class="grow">Memory</span><span class="mono mem">{(draft.memoryMb / 1024).toFixed(1)} GB</span></span>
    <input type="range" min="1024" max={maxMemory} step="512" bind:value={draft.memoryMb} />
    <span class="faint small">Recommended: {((app.info?.suggestedMemoryMb ?? 4096) / 1024).toFixed(1)} GB. More memory isn't faster unless you run big modpacks.</span>
  </div>

  <button class="btn ghost small adv" onclick={() => (advanced = !advanced)}>{advanced ? "Hide" : "Show"} advanced options</button>
  {#if advanced}
    <div class="grid2">
      <label class="field"><span>Window width</span>
        <input class="input" type="number" min="320" placeholder="Default" value={draft.resolution?.width ?? ""}
          oninput={(e) => { const w = Number(e.currentTarget.value); draft.resolution = w ? { width: w, height: draft.resolution?.height ?? 720 } : null; }} />
      </label>
      <label class="field"><span>Window height</span>
        <input class="input" type="number" min="240" placeholder="Default" value={draft.resolution?.height ?? ""} disabled={!draft.resolution}
          oninput={(e) => { if (draft.resolution) draft.resolution.height = Number(e.currentTarget.value) || 720; }} />
      </label>
    </div>
    <label class="field"><span>Extra JVM arguments</span>
      <textarea class="input" rows="2" bind:value={draft.jvmArgs} placeholder="-Dsodium.checks.issue2561=false"></textarea>
    </label>
    <div class="field"><span>Java</span>
      <div class="row">
        <input class="input grow" value={draft.javaPath ?? ""} placeholder="Automatic (recommended)" readonly />
        <button class="btn small" onclick={chooseJava}>Browse</button>
        {#if draft.javaPath}<button class="btn ghost small" onclick={() => (draft.javaPath = null)}>Reset</button>{/if}
      </div>
    </div>
  {/if}

  {#snippet footer()}
    <button class="btn ghost" onclick={onclose}>Cancel</button>
    <button class="btn primary" onclick={save} disabled={saving || !draft.name.trim() || !draft.gameVersion || unsupported}>{initial ? "Save" : "Create"}</button>
  {/snippet}
</Modal>

<style>
  .top { display: flex; gap: 12px; }
  .icons { display: flex; gap: 8px; flex-wrap: wrap; }
  .icon-pick { padding: 2px; background: none; border: 2px solid transparent; border-radius: 9px; cursor: pointer; transition: border-color var(--fast), transform var(--fast) var(--ease); }
  .icon-pick:hover { transform: translateY(-2px); }
  .icon-pick.on { border-color: var(--accent); }
  .grid2 { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
  .loaders { width: 100%; }
  .loaders button { flex: 1; }
  .note { color: var(--warn); margin-top: -6px; }
  .check { display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--muted); margin-top: -6px; }
  .check input { accent-color: var(--accent); }
  .mem { color: var(--accent-hover); }
  .small { font-size: 13px; }
  .adv { justify-self: start; padding-left: 0; }
</style>
