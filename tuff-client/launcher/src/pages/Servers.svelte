<script lang="ts">
  import { onMount } from "svelte";
  import { flip } from "svelte/animate";
  import Icon from "../components/Icon.svelte";
  import Modal from "../components/Modal.svelte";
  import { api } from "../lib/api";
  import { compact } from "../lib/format";
  import { app, play, selectedProfile } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { PingResult, Server } from "../lib/types";

  let servers = $state<Server[]>([]);
  let pings = $state<Record<string, PingResult | string | undefined>>({});
  let editing = $state<Server | null>(null);
  let loaded = $state(false);

  onMount(async () => {
    servers = await api.listServers();
    loaded = true;
    pingAll();
  });

  function pingOne(s: Server) {
    pings[s.id] = undefined;
    api.pingServer(s.address).then((r) => (pings[s.id] = r)).catch((e) => (pings[s.id] = String(e)));
  }
  function pingAll() { servers.forEach(pingOne); }

  async function persist() {
    await api.saveServers($state.snapshot(servers) as Server[]);
  }

  async function saveEdit() {
    if (!editing) return;
    const s = { ...editing, name: editing.name.trim() || editing.address.trim(), address: editing.address.trim() };
    if (!s.address) return toast("Enter a server address", "error");
    const i = servers.findIndex((x) => x.id === s.id);
    if (i >= 0) servers[i] = s; else servers.push(s);
    editing = null;
    await persist();
    pingOne(s);
  }

  async function remove(s: Server) {
    servers = servers.filter((x) => x.id !== s.id);
    await persist();
  }

  async function move(s: Server, dir: -1 | 1) {
    const i = servers.indexOf(s);
    const j = i + dir;
    if (j < 0 || j >= servers.length) return;
    [servers[i], servers[j]] = [servers[j], servers[i]];
    await persist();
  }

  function join(s: Server) {
    const target = (s.profileId && app.profiles.find((p) => p.id === s.profileId)) || selectedProfile();
    if (!target) return toast("Create a profile first", "error");
    play(target.id, s.address);
  }

  function latencyClass(ms: number) { return ms < 80 ? "good" : ms < 180 ? "ok" : "bad"; }
</script>

<div class="page">
  <div class="page-head">
    <div>
      <h1>Servers</h1>
      <p class="muted">Live player counts and ping. Play joins straight into the server.</p>
    </div>
    <div class="row">
      <button class="btn ghost" onclick={pingAll} disabled={!servers.length}><Icon name="refresh" size={12} /> Refresh</button>
      <button class="btn primary" onclick={() => (editing = { id: crypto.randomUUID(), name: "", address: "", profileId: null })}><Icon name="plus" size={12} /> Add server</button>
    </div>
  </div>

  {#if loaded && !servers.length}
    <div class="empty"><p class="pixel">No servers yet</p><p>Add a server to see who's online and join it in one click.</p>
      <button class="btn" onclick={() => (editing = { id: crypto.randomUUID(), name: "", address: "", profileId: null })}>Add server</button></div>
  {/if}

  <div class="list">
    {#each servers as s, i (s.id)}
      {@const p = pings[s.id]}
      <article class="srv rise lift" style:--i={i} animate:flip={{ duration: 260 }}>
        {#if p && typeof p !== "string" && p.favicon}<img src={p.favicon} alt="" width="48" height="48" />{:else}<span class="fav"><Icon name="globe" size={18} /></span>{/if}
        <div class="grow">
          <div class="row t"><h4 class="ellipsis">{s.name}</h4><span class="addr mono selectable">{s.address}</span></div>
          <p class="motd ellipsis">
            {#if p === undefined}<span class="faint">Pinging…</span>
            {:else if typeof p === "string"}<span class="faint">Can't reach server</span>
            {:else}{p.motd || "A Minecraft server"}{/if}
          </p>
          {#if s.profileId}<p class="faint uses">Uses {app.profiles.find((x) => x.id === s.profileId)?.name ?? "a deleted profile"}</p>{/if}
        </div>
        <div class="stats">
          {#if p && typeof p !== "string"}
            <span class="players"><span class="dot on"></span> {compact(p.online)} / {compact(p.max)}</span>
            <span class="lat {latencyClass(p.latencyMs)}">{p.latencyMs} ms</span>
            <span class="ver faint ellipsis">{p.version}</span>
          {:else if typeof p === "string"}
            <span class="players"><span class="dot"></span> Offline</span>
          {/if}
        </div>
        <div class="acts">
          <button class="btn small icon ghost" onclick={() => move(s, -1)} disabled={i === 0} title="Move up" aria-label="Move up"><span class="up"><Icon name="chevron" size={10} /></span></button>
          <button class="btn small icon ghost" onclick={() => move(s, 1)} disabled={i === servers.length - 1} title="Move down" aria-label="Move down"><Icon name="chevron" size={10} /></button>
          <button class="btn small icon ghost" onclick={() => (editing = { ...s })} title="Edit"><Icon name="edit" size={12} /></button>
          <button class="btn small icon ghost danger" onclick={() => remove(s)} title="Remove"><Icon name="trash" size={12} /></button>
          <button class="btn primary joinbtn" onclick={() => join(s)} disabled={!!app.running || !!app.launching}><Icon name="play" size={10} /> Join</button>
        </div>
      </article>
    {/each}
  </div>
</div>

{#if editing}
  <Modal title={servers.some((s) => s.id === editing?.id) ? "Edit server" : "Add server"} onclose={() => (editing = null)} width={440}>
    <label class="field"><span>Address</span>
      <!-- svelte-ignore a11y_autofocus -->
      <input class="input mono" bind:value={editing.address} placeholder="play.example.net" autofocus />
    </label>
    <label class="field"><span>Name</span><input class="input" bind:value={editing.name} placeholder="My server" /></label>
    <label class="field"><span>Play with</span>
      <select class="input" bind:value={editing.profileId}>
        <option value={null}>Selected profile</option>
        {#each app.profiles as p (p.id)}<option value={p.id}>{p.name} · {p.gameVersion}</option>{/each}
      </select>
    </label>
    {#snippet footer()}
      <button class="btn ghost" onclick={() => (editing = null)}>Cancel</button>
      <button class="btn primary" onclick={saveEdit}>Save</button>
    {/snippet}
  </Modal>
{/if}

<style>
  .list { display: grid; gap: 8px; }
  .srv { display: flex; align-items: center; gap: 16px; padding: 14px; border-radius: 10px; background: var(--surface); border: 1px solid var(--border); }
  .srv img, .fav { width: 48px; height: 48px; border-radius: 8px; image-rendering: pixelated; flex: none; }
  .fav { display: grid; place-items: center; background: var(--surface-2); color: var(--faint); }
  .t { gap: 10px; align-items: baseline; }
  h4 { margin: 0; font-size: 17px; font-weight: 600; }
  .addr { font-size: 13px; color: var(--faint); }
  .motd { font-size: 14.5px; color: var(--muted); margin-top: 3px; }
  .uses { font-size: 13px; margin-top: 2px; }
  .stats { display: grid; justify-items: end; gap: 2px; width: 130px; font-size: 14px; }
  .players { display: inline-flex; align-items: center; gap: 6px; font-weight: 600; }
  .lat.good { color: var(--online); }
  .lat.ok { color: var(--warn); }
  .lat.bad { color: var(--danger); }
  .ver { max-width: 130px; font-size: 12.5px; }
  .acts { display: flex; align-items: center; gap: 2px; }
  .up { display: block; transform: rotate(180deg); }
  .joinbtn { margin-left: 8px; height: 36px; width: 92px; }
</style>
