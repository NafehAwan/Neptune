<script lang="ts">
  import chestArt from "../assets/glade/noprofiles.jpg";
  import Icon from "../components/Icon.svelte";
  import Modal from "../components/Modal.svelte";
  import ProfileIcon from "../components/ProfileIcon.svelte";
  import NewProfile from "../components/NewProfile.svelte";
  import ProfileModal from "../components/ProfileModal.svelte";
  import { api, pickFile } from "../lib/api";
  import { ago, bytes, gladeRuns, playTime, loaderName } from "../lib/format";
  import { launchFraction } from "../lib/progress";
  import { app, go, play, refreshProfiles, selectProfile } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { ContentItem, ContentKind, ContentUpdate, Profile } from "../lib/types";

  let selectedId = $state<string | null>(app.focusProfile ?? app.settings?.selectedProfile ?? app.profiles[0]?.id ?? null);
  app.focusProfile = null;
  const selected = $derived(app.profiles.find((p) => p.id === selectedId) ?? app.profiles[0] ?? null);

  let tab = $state<ContentKind>("mod");
  let items = $state<ContentItem[]>([]);
  let loading = $state(false);
  let updates = $state<ContentUpdate[]>([]);
  let checking = $state(false);
  let filter = $state("");
  let editing = $state<Profile | null>(null);
  let creating = $state(false);
  let picking = $state(false);
  let deleting = $state<Profile | null>(null);
  let deleteFiles = $state(true);
  let busy = $state(false);

  const tabs: { kind: ContentKind; label: string }[] = [
    { kind: "mod", label: "Mods" },
    { kind: "resourcepack", label: "Resource packs" },
    { kind: "shader", label: "Shaders" },
  ];
  const visible = $derived(items.filter((i) => (i.meta?.title ?? i.file).toLowerCase().includes(filter.toLowerCase())));

  async function load() {
    if (!selected) return;
    loading = true;
    try {
      items = await api.contentList(selected.id, tab);
    } catch (e) {
      toast(e, "error");
    } finally {
      loading = false;
    }
  }

  $effect(() => {
    selected?.id;
    tab;
    updates = [];
    load();
  });

  async function toggle(item: ContentItem) {
    if (!selected) return;
    await api.contentToggle(selected.id, item.kind, item.file, !item.enabled);
    item.enabled = !item.enabled;
  }

  async function remove(item: ContentItem) {
    if (!selected) return;
    await api.contentRemove(selected.id, item.kind, item.file);
    items = items.filter((i) => i !== item);
    toast(`Removed ${item.meta?.title ?? item.file}`);
  }

  async function checkUpdates() {
    if (!selected) return;
    checking = true;
    try {
      updates = await api.contentUpdates(selected.id);
      if (!updates.length) toast("Everything is up to date", "success");
    } catch (e) {
      toast(e, "error");
    } finally {
      checking = false;
    }
  }

  async function updateAll() {
    if (!selected) return;
    checking = true;
    for (const u of updates) {
      try {
        await api.contentUpdate(selected.id, u.kind, u.file);
      } catch (e) {
        toast(`${u.title}: ${e}`, "error");
      }
    }
    toast(`Updated ${updates.length} item${updates.length === 1 ? "" : "s"}`, "success");
    updates = [];
    checking = false;
    load();
  }

  async function importPack() {
    const path = await pickFile("Modrinth modpack", ["mrpack"]);
    if (!path) return;
    busy = true;
    try {
      const p = await api.importMrpack(path);
      await refreshProfiles();
      selectedId = p.id;
      toast(`Imported ${p.name}`, "success");
    } catch (e) {
      toast(e, "error");
    } finally {
      busy = false;
    }
  }

  async function duplicate(p: Profile) {
    const copy = await api.duplicateProfile(p.id);
    await refreshProfiles();
    selectedId = copy.id;
    toast(`Duplicated ${p.name}`, "success");
  }

  async function confirmDelete() {
    if (!deleting) return;
    await api.deleteProfile(deleting.id, deleteFiles);
    toast(`Deleted ${deleting.name}`);
    deleting = null;
    await refreshProfiles();
    selectedId = app.profiles[0]?.id ?? null;
  }

  function browseFor(kind: ContentKind) {
    if (!selected) return;
    app.focusProfile = selected.id;
    sessionStorage.setItem("tuff.browse.kind", kind);
    go("browse");
  }
</script>

<div class="page library">
  <div class="page-head">
    <div>
      <h1>Library</h1>
      <p class="muted">{app.profiles.length} profile{app.profiles.length === 1 ? "" : "s"}. Each one has its own mods, worlds and settings.</p>
    </div>
    <div class="row">
      <button class="btn ghost" onclick={importPack} disabled={busy}><Icon name="download" size={12} /> Import .mrpack</button>
      <button class="btn primary" onclick={() => (picking = true)}><Icon name="plus" size={12} /> New profile</button>
    </div>
  </div>

  {#if !app.profiles.length}
    <div class="empty">
      <img class="emptyart" src={chestArt} alt="" />
      <p class="pixel">No profiles yet</p>
      <p>Start from a Glade preset (Performance, Performance+, Performance++ or Scenic), or build your own.</p>
      <div class="row"><button class="btn primary" onclick={() => (picking = true)}>Choose a preset</button><button class="btn ghost" onclick={() => (creating = true)}>Custom profile</button></div>
    </div>
  {:else}
    <div class="split">
      <ul class="list">
        {#each app.profiles as p, i (p.id)}
          <li class="rise" style:--i={i}>
            <button class="item" class:on={selected?.id === p.id} onclick={() => (selectedId = p.id)}>
              <ProfileIcon name={p.icon} size={38} />
              <span class="grow">
                <span class="name ellipsis">{p.name}</span>
                <span class="sub">{loaderName(p.loader)} {p.gameVersion}</span>
              </span>
              {#if app.running === p.id}<span class="dot on"></span>{/if}
            </button>
          </li>
        {/each}
      </ul>

      {#if selected}
        <section class="detail card">
          <header class="dhead">
            <ProfileIcon name={selected.icon} size={60} />
            <div class="grow">
              <h2 class="ellipsis">{selected.name}</h2>
              <div class="chips">
                <span class="chip">{selected.loader === "vanilla" ? "Vanilla" : `${loaderName(selected.loader)} ${selected.loaderVersion ?? "latest"}`}</span>
                <span class="chip">{selected.gameVersion}</span>
                <span class="chip">{(selected.memoryMb / 1024).toFixed(1)} GB</span>
                {#if gladeRuns(app.info?.gladeMinecraft, selected.loader, selected.gameVersion)}
                  <span class="chip glade" title="Glade's in-game mod is installed and checked before every launch"><Icon name="check" size={11} /> Glade {app.info?.gladeVersion ?? ""}</span>
                {:else if selected.isTuff}
                  <span class="chip" title="Glade's in-game mod isn't available for this Minecraft version yet">No Glade in-game on {selected.gameVersion}</span>
                {/if}
                {#if selected.isTuff}<span class="chip copper">{selected.preset ? `Glade ${({ performance: "Performance", "performance-plus": "Performance+", "performance-plus-plus": "Performance++", scenic: "Scenic" } as Record<string, string>)[selected.preset] ?? ""}` : "Glade"}</span>{/if}
              </div>
              <p class="faint stat">{playTime(selected.playTimeSecs)} · {ago(selected.lastPlayed)}</p>
            </div>
            <div class="actions">
              <button class="btn primary playbtn" disabled={!!app.running || !!app.launching} onclick={() => { selectProfile(selected.id); play(selected.id); }}>
                {#if app.launching === selected.id}
                  {@const f = app.progress ? launchFraction(app.progress) : null}
                  <span class="spinner"></span> {f === null ? "Loading" : `${Math.floor(f * 100)}%`}
                {:else if app.running === selected.id}Playing{:else}<Icon name="play" size={12} /> Play{/if}
              </button>
              <div class="row tight">
                <button class="btn small icon" title="Edit" onclick={() => (editing = selected)}><Icon name="edit" size={12} /></button>
                <button class="btn small icon" title="Duplicate" onclick={() => duplicate(selected)}><Icon name="copy" size={12} /></button>
                <button class="btn small icon" title="Open folder" onclick={() => api.openFolder(selected.id)}><Icon name="folder" size={12} /></button>
                <button class="btn small icon danger" title="Delete" onclick={() => { deleting = selected; deleteFiles = true; }}><Icon name="trash" size={12} /></button>
              </div>
            </div>
          </header>

          <div class="toolbar">
            <div class="seg">
              {#each tabs as t}<button class:on={tab === t.kind} onclick={() => (tab = t.kind)}>{t.label}</button>{/each}
            </div>
            <input class="input search" placeholder="Filter" bind:value={filter} />
            <div class="grow"></div>
            <button class="btn small" onclick={checkUpdates} disabled={checking}>
              {#if checking}<span class="spinner"></span>{:else}<Icon name="refresh" size={12} />{/if} Updates
            </button>
            <button class="btn small" onclick={() => browseFor(tab)}><Icon name="plus" size={12} /> Add</button>
          </div>

          {#if updates.length}
            <div class="updates">
              <span><strong>{updates.length}</strong> update{updates.length === 1 ? "" : "s"} available: {updates.map((u) => u.title).join(", ")}</span>
              <button class="btn small" onclick={updateAll} disabled={checking}>Update all</button>
            </div>
          {/if}

          <div class="content">
            {#if loading}
              {#each Array(4) as _}<div class="skeleton" style="height:48px"></div>{/each}
            {:else if !visible.length}
              <div class="empty small">
                <p>{filter ? "Nothing matches that filter." : `No ${tabs.find((t) => t.kind === tab)?.label.toLowerCase()} installed.`}</p>
                {#if !filter}<button class="btn small" onclick={() => browseFor(tab)}>Browse Modrinth</button>{/if}
              </div>
            {:else}
              {#each visible as item, i (item.file)}
                <div class="citem rise" class:off={!item.enabled} style:--i={i}>
                  {#if item.meta?.iconUrl}<img src={item.meta.iconUrl} alt="" width="34" height="34" loading="lazy" />{:else}<span class="ph"><Icon name="cube" size={14} /></span>{/if}
                  <div class="grow">
                    <p class="ctitle ellipsis">{item.meta?.title ?? item.file}</p>
                    <p class="cmeta ellipsis">{item.meta?.versionNumber ?? item.file} · {bytes(item.size)}</p>
                  </div>
                  {#if updates.some((u) => u.file === item.file)}<span class="chip copper">Update</span>{/if}
                  {#if item.locked}
                    <span class="chip lock" title={`${item.locked}. The launcher keeps it installed and up to date.`}><Icon name="lock" size={11} /> Required</span>
                  {:else}
                    <label class="switch" title={item.enabled ? "Disable" : "Enable"}>
                      <input type="checkbox" checked={item.enabled} onchange={() => toggle(item).catch((e) => toast(e, "error"))} />
                      <span></span>
                    </label>
                    <button class="btn ghost small icon" title="Remove" onclick={() => remove(item).catch((e) => toast(e, "error"))}><Icon name="trash" size={12} /></button>
                  {/if}
                </div>
              {/each}
            {/if}
          </div>
        </section>
      {/if}
    </div>
  {/if}
</div>

{#if creating}<ProfileModal onclose={() => (creating = false)} onsaved={(p) => (selectedId = p.id)} />{/if}
{#if picking}<NewProfile onclose={() => (picking = false)} oncustom={() => (creating = true)} oncreated={(p) => (selectedId = p.id)} />{/if}
{#if editing}<ProfileModal profile={editing} onclose={() => (editing = null)} />{/if}
{#if deleting}
  <Modal title="Delete profile?" onclose={() => (deleting = null)} width={420}>
    <p class="muted">This removes <strong>{deleting.name}</strong> from your library.</p>
    <label class="row del"><input type="checkbox" bind:checked={deleteFiles} /> Also delete its worlds, mods and settings</label>
    {#snippet footer()}
      <button class="btn ghost" onclick={() => (deleting = null)}>Cancel</button>
      <button class="btn danger" onclick={confirmDelete}>Delete</button>
    {/snippet}
  </Modal>
{/if}

<style>
  .split { display: grid; grid-template-columns: 250px 1fr; gap: 18px; align-items: start; }
  .list { list-style: none; margin: 0; padding: 0; display: grid; gap: 4px; position: sticky; top: 0; }
  .item { width: 100%; display: flex; align-items: center; gap: 11px; padding: 8px; border-radius: 9px; background: none; border: 1px solid transparent; color: var(--text); text-align: left; cursor: pointer; transition: background var(--fast), border-color var(--fast); }
  .item:hover { background: var(--surface); }
  .item.on { background: var(--surface); border-color: var(--border-strong); }
  .item .grow { display: grid; }
  .name { font-weight: 600; font-size: 15.5px; }
  .sub { font-size: 14px; color: var(--muted); }

  .detail { padding: 20px; min-height: 420px; }
  .dhead { display: flex; gap: 16px; align-items: flex-start; padding-bottom: 18px; border-bottom: 1px solid var(--border); }
  .chips { display: flex; gap: 6px; flex-wrap: wrap; margin-top: 8px; }
  .stat { font-size: 14px; margin-top: 8px; }
  .actions { display: grid; gap: 8px; justify-items: end; }
  .playbtn { height: 40px; width: 136px; }
  .tight { gap: 4px; }

  .toolbar { display: flex; align-items: center; gap: 10px; margin: 16px 0 12px; flex-wrap: wrap; }
  .search { width: 160px; height: 32px; }
  .updates { display: flex; align-items: center; gap: 12px; justify-content: space-between; padding: 10px 12px; margin-bottom: 10px; border-radius: 8px; background: rgba(47, 191, 106, 0.08); border: 1px solid rgba(47, 191, 106, 0.3); font-size: 14.5px; }
  .content { display: grid; gap: 6px; }
  .citem { display: flex; align-items: center; gap: 12px; padding: 7px 8px 7px 7px; border-radius: 8px; background: var(--bg); border: 1px solid var(--border); transition: opacity var(--fast); }
  .citem.off { opacity: 0.5; }
  .citem img, .ph { width: 34px; height: 34px; border-radius: 6px; flex: none; }
  .ph { display: grid; place-items: center; background: var(--surface-2); color: var(--faint); }
  .ctitle { font-weight: 600; font-size: 15px; }
  .cmeta { font-size: 13px; color: var(--faint); }
  .empty.small { padding: 34px 16px; }
  .del { font-size: 15px; color: var(--muted); }
  .chip.glade, .chip.lock { display: inline-flex; align-items: center; gap: 4px; }
  .chip.glade { background: rgba(47, 191, 106, 0.12); color: var(--accent-deep); border-color: rgba(47, 191, 106, 0.3); }
  .chip.lock { color: var(--accent-deep); }
  .del input { accent-color: var(--danger); }
  .emptyart { width: 180px; border-radius: 16px; -webkit-mask-image: radial-gradient(closest-side, #000 60%, transparent 100%); mask-image: radial-gradient(closest-side, #000 60%, transparent 100%); }
</style>
