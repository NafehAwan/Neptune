<script lang="ts">
  import Icon from "../components/Icon.svelte";
  import { api } from "../lib/api";
  import { compact } from "../lib/format";
  import { app, refreshProfiles, selectedProfile } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { ContentKind, SearchHit } from "../lib/types";

  const kinds: { kind: ContentKind; label: string }[] = [
    { kind: "mod", label: "Mods" },
    { kind: "resourcepack", label: "Resource packs" },
    { kind: "shader", label: "Shaders" },
    { kind: "modpack", label: "Modpacks" },
  ];
  const sorts = [
    { id: "relevance", label: "Relevance" },
    { id: "downloads", label: "Downloads" },
    { id: "follows", label: "Followers" },
    { id: "newest", label: "Newest" },
    { id: "updated", label: "Recently updated" },
  ];

  let kind = $state<ContentKind>((sessionStorage.getItem("tuff.browse.kind") as ContentKind) || "mod");
  sessionStorage.removeItem("tuff.browse.kind");
  let profileId = $state<string | null>(app.focusProfile ?? selectedProfile()?.id ?? null);
  app.focusProfile = null;
  let query = $state("");
  let sort = $state("relevance");
  let hits = $state<SearchHit[]>([]);
  let total = $state(0);
  let loading = $state(false);
  let error = $state("");
  let installing = $state<Record<string, "busy" | "done">>({});
  let installed = $state<Set<string>>(new Set());

  const profile = $derived(app.profiles.find((p) => p.id === profileId) ?? null);
  let timer: ReturnType<typeof setTimeout>;
  let generation = 0;

  async function search(append = false) {
    const gen = ++generation;
    loading = true;
    error = "";
    try {
      const filterVersion = kind === "modpack" ? null : profile?.gameVersion ?? null;
      const loader = profile?.loader === "fabric" ? "fabric" : null;
      const r = await api.search(query, kind, filterVersion, kind === "mod" ? loader ?? "fabric" : loader, sort, append ? hits.length : 0);
      if (gen !== generation) return;
      hits = append ? [...hits, ...r.hits] : r.hits;
      total = r.total_hits;
    } catch (e) {
      if (gen === generation) error = String(e);
    } finally {
      if (gen === generation) loading = false;
    }
  }

  $effect(() => {
    kind; sort; profileId;
    const q = query;
    clearTimeout(timer);
    timer = setTimeout(() => search(false), q ? 300 : 0);
  });

  $effect(() => {
    const id = profileId;
    if (!id || kind === "modpack") { installed = new Set(); return; }
    api.contentList(id, kind).then((items) => (installed = new Set(items.map((i) => i.meta?.projectId).filter(Boolean) as string[]))).catch(() => {});
  });

  async function install(hit: SearchHit) {
    installing[hit.project_id] = "busy";
    try {
      if (kind === "modpack") {
        const p = await api.installModpack(hit.project_id);
        await refreshProfiles();
        toast(`${p.name} is ready in your library`, "success");
      } else {
        if (!profile) throw "Pick a profile to install into.";
        const titles = await api.install(profile.id, hit.project_id, kind);
        const extra = titles.length > 1 ? ` (+${titles.length - 1} dependenc${titles.length === 2 ? "y" : "ies"})` : "";
        toast(`Installed ${hit.title}${extra} into ${profile.name}`, "success");
        installed = new Set([...installed, hit.project_id]);
      }
      installing[hit.project_id] = "done";
    } catch (e) {
      delete installing[hit.project_id];
      toast(e, "error");
    }
  }
</script>

<div class="page">
  <div class="page-head">
    <div>
      <h1>Mods</h1>
      <p class="muted">Search Modrinth. Results only show content that works with your selected profile.</p>
    </div>
  </div>

  <div class="bar">
    <div class="searchbox">
      <Icon name="search" size={14} />
      <input class="input" placeholder="Search {kinds.find((k) => k.kind === kind)?.label.toLowerCase()}…" bind:value={query} />
    </div>
    {#if kind !== "modpack"}
      <select class="input target" bind:value={profileId} title="Install into">
        {#each app.profiles as p (p.id)}<option value={p.id}>{p.name} · {p.loader === "fabric" ? "Fabric" : "Vanilla"} {p.gameVersion}</option>{/each}
        {#if !app.profiles.length}<option value={null}>No profiles</option>{/if}
      </select>
    {/if}
    <select class="input sort" bind:value={sort}>
      {#each sorts as s}<option value={s.id}>{s.label}</option>{/each}
    </select>
  </div>
  <div class="seg kinds">
    {#each kinds as k}<button class:on={kind === k.kind} onclick={() => (kind = k.kind)}>{k.label}</button>{/each}
  </div>

  {#if kind === "mod" && profile?.loader === "vanilla"}
    <p class="note">Mods need a Fabric profile. <strong>{profile.name}</strong> is vanilla, so pick or create a Fabric profile to install mods.</p>
  {/if}

  {#if error}
    <div class="empty"><p class="pixel">Couldn't reach Modrinth</p><p class="selectable">{error}</p><button class="btn" onclick={() => search()}>Retry</button></div>
  {:else}
    <div class="results">
      {#each hits as hit (hit.project_id)}
        {@const state = installed.has(hit.project_id) ? "done" : installing[hit.project_id]}
        <article class="hit">
          {#if hit.icon_url}<img src={hit.icon_url} alt="" width="56" height="56" loading="lazy" />{:else}<span class="ph"><Icon name="cube" size={20} /></span>{/if}
          <div class="grow">
            <div class="row t"><h4 class="ellipsis">{hit.title}</h4><span class="by">by {hit.author}</span></div>
            <p class="desc">{hit.description}</p>
            <div class="row stats">
              <span><Icon name="download" size={10} /> {compact(hit.downloads)}</span>
              {#each hit.categories.slice(0, 3) as c}<span class="tag">{c}</span>{/each}
            </div>
          </div>
          <button class="btn install" class:done={state === "done"} onclick={() => install(hit)}
            disabled={!!state || (kind !== "modpack" && !profile) || (kind === "mod" && profile?.loader === "vanilla")}>
            {#if state === "busy"}<span class="spinner"></span>{:else if state === "done"}<Icon name="check" size={12} /> Installed{:else}<Icon name="download" size={12} /> Install{/if}
          </button>
        </article>
      {/each}
      {#if loading}
        {#each Array(hits.length ? 2 : 6) as _}<div class="skeleton" style="height:86px"></div>{/each}
      {:else if !hits.length}
        <div class="empty"><p class="pixel">Nothing found</p><p>Try another search{profile ? ` or a different version than ${profile.gameVersion}` : ""}.</p></div>
      {:else if hits.length < total}
        <button class="btn more" onclick={() => search(true)}>Load more</button>
      {/if}
    </div>
  {/if}
</div>

<style>
  .bar { display: flex; gap: 10px; margin-bottom: 12px; }
  .searchbox { position: relative; flex: 1; color: var(--faint); }
  .searchbox :global(.icon) { position: absolute; left: 12px; top: 10px; }
  .searchbox .input { padding-left: 34px; height: 38px; font-size: 16px; }
  .target { width: 260px; height: 38px; }
  .sort { width: 170px; height: 38px; }
  .kinds { margin-bottom: 18px; }
  .note { font-size: 14.5px; color: var(--warn); margin-bottom: 14px; }
  .results { display: grid; gap: 8px; }
  .hit { display: flex; align-items: center; gap: 14px; padding: 14px; border-radius: 10px; background: var(--surface); border: 1px solid var(--border); transition: border-color var(--fast), transform var(--fast) var(--ease); }
  .hit:hover { border-color: var(--border-strong); }
  .hit img, .ph { width: 56px; height: 56px; border-radius: 10px; flex: none; background: var(--surface-2); }
  .ph { display: grid; place-items: center; color: var(--faint); }
  .t { gap: 8px; align-items: baseline; }
  h4 { margin: 0; font-size: 17px; font-weight: 600; }
  .by { font-size: 14px; color: var(--faint); white-space: nowrap; }
  .desc { font-size: 14.5px; color: var(--muted); margin-top: 3px; display: -webkit-box; -webkit-line-clamp: 2; line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }
  .stats { margin-top: 8px; gap: 8px; font-size: 13px; color: var(--faint); }
  .stats span { display: inline-flex; align-items: center; gap: 5px; }
  .tag { padding: 1px 7px; border-radius: 4px; background: var(--surface-2); }
  .install { width: 112px; flex: none; }
  .install.done { color: var(--online); border-color: rgba(111, 191, 115, 0.35); background: rgba(111, 191, 115, 0.08); opacity: 1; }
  .more { justify-self: center; margin-top: 8px; }
</style>
