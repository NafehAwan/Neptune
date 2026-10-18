<script lang="ts">
  import { onMount } from "svelte";
  import { fly } from "svelte/transition";
  import leaf from "../assets/brand/leaf.png";
  import Icon from "../components/Icon.svelte";
  import ProfileIcon from "../components/ProfileIcon.svelte";
  import ProfileModal from "../components/ProfileModal.svelte";
  import { api } from "../lib/api";
  import { ago, compact, greeting, playTime } from "../lib/format";
  import { activeAccount, app, go, play, refreshProfiles, selectProfile, selectedProfile } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { PingResult, Server } from "../lib/types";

  const profile = $derived(selectedProfile());
  const account = $derived(activeAccount());
  const busy = $derived(app.launching !== null);
  const running = $derived(app.running !== null);
  const percent = $derived(app.progress && app.progress.total > 0 ? Math.round((app.progress.done / app.progress.total) * 100) : null);

  let picker = $state(false);
  let creating = $state(false);
  let tuffBusy = $state(false);
  let servers = $state<Server[]>([]);
  let pings = $state<Record<string, PingResult | "offline" | undefined>>({});

  onMount(async () => {
    servers = (await api.listServers()).slice(0, 4);
    for (const s of servers) {
      api.pingServer(s.address).then((r) => (pings[s.id] = r)).catch(() => (pings[s.id] = "offline"));
    }
  });

  async function createTuff() {
    tuffBusy = true;
    try {
      const result = await api.createTuffProfile();
      await refreshProfiles();
      await selectProfile(result.profile.id);
      toast(`Glade profile ready with ${result.installed.length} mods${result.skipped.length ? ` (${result.skipped.length} not available for ${result.profile.gameVersion} yet)` : ""}.`, "success", 6000);
    } catch (e) {
      toast(e, "error");
    } finally {
      tuffBusy = false;
    }
  }

  function join(s: Server) {
    const target = (s.profileId && app.profiles.find((p) => p.id === s.profileId)) || profile;
    if (target) play(target.id, s.address);
  }

  function outside(node: HTMLElement) {
    const handler = (e: MouseEvent) => { if (!node.contains(e.target as Node)) picker = false; };
    document.addEventListener("mousedown", handler);
    return { destroy: () => document.removeEventListener("mousedown", handler) };
  }
</script>

<section class="home">
  <div class="brand" in:fly={{ y: 10, duration: 500 }}>
    <img class="emblem" src={leaf} alt="" width="72" height="72" />
    <h1 class="word">Glade</h1>
    {#if account}<p class="hello">{greeting()}, <strong>{account.name}</strong></p>{/if}
  </div>

  {#if servers.length}
    <aside class="quick card" in:fly={{ x: 16, duration: 400, delay: 120 }}>
      <div class="qhead"><h3>Quick join</h3><button class="btn ghost small" onclick={() => go("servers")}>All</button></div>
      {#each servers as s (s.id)}
        {@const ping = pings[s.id]}
        <button class="srv" onclick={() => join(s)} disabled={busy || running} title="Join {s.address}">
          {#if ping && ping !== "offline" && ping.favicon}
            <img src={ping.favicon} alt="" width="28" height="28" />
          {:else}
            <span class="fav"><Icon name="globe" size={14} /></span>
          {/if}
          <span class="grow">
            <span class="sname ellipsis">{s.name}</span>
            <span class="smeta">
              {#if ping === undefined}<span class="faint">Pinging…</span>
              {:else if ping === "offline"}<span class="dot"></span> Offline
              {:else}<span class="dot on"></span> {compact(ping.online)} online · {ping.latencyMs}ms{/if}
            </span>
          </span>
          <span class="go"><Icon name="play" size={10} /></span>
        </button>
      {/each}
    </aside>
  {/if}

  <div class="dock-wrap">
    {#if app.progress}
      <div class="stage" transition:fly={{ y: 6, duration: 160 }}>
        {app.progress.stage}{#if app.progress.total > 1} · {app.progress.done}/{app.progress.total}{/if}
      </div>
    {/if}

    <div class="dock" in:fly={{ y: 20, duration: 420, delay: 80 }}>
      {#if profile}
        <div class="picker-wrap" use:outside>
          <button class="current" onclick={() => (picker = !picker)} aria-haspopup="listbox" aria-expanded={picker}>
            <ProfileIcon name={profile.icon} size={40} />
            <span class="meta">
              <span class="pname ellipsis">{profile.name}</span>
              <span class="pinfo">{profile.loader === "fabric" ? "Fabric" : "Vanilla"} {profile.gameVersion} · {playTime(profile.playTimeSecs)}</span>
            </span>
            <span class="caret" class:up={picker}><Icon name="chevron" size={10} /></span>
          </button>
          {#if picker}
            <div class="picker card" role="listbox" transition:fly={{ y: 8, duration: 160 }}>
              {#each app.profiles as p (p.id)}
                <button class="opt" class:on={p.id === profile.id} role="option" aria-selected={p.id === profile.id}
                  onclick={() => { selectProfile(p.id); picker = false; }}>
                  <ProfileIcon name={p.icon} size={30} />
                  <span class="grow">
                    <span class="pname ellipsis">{p.name}</span>
                    <span class="pinfo">{p.loader === "fabric" ? "Fabric" : "Vanilla"} {p.gameVersion} · {ago(p.lastPlayed)}</span>
                  </span>
                  {#if p.isTuff}<span class="chip copper">Glade</span>{/if}
                </button>
              {/each}
              <div class="picker-foot">
                <button class="btn ghost small" onclick={() => { picker = false; creating = true; }}><Icon name="plus" size={10} /> New profile</button>
                <button class="btn ghost small" onclick={() => go("library")}>Manage</button>
              </div>
            </div>
          {/if}
        </div>

        {#if running}
          <button class="btn primary play running" onclick={() => api.killGame()}>
            <span class="label">Playing</span>
            <span class="hint">Click to stop</span>
          </button>
        {:else}
          <button class="btn primary play" onclick={() => play(profile.id)} disabled={busy} title="Play (Ctrl+Enter)">
            {#if percent !== null}<span class="fill" style:width="{percent}%"></span>{/if}
            <span class="label">{#if busy}{percent !== null ? `${percent}%` : "Loading"}{:else}<Icon name="play" size={14} /> Play{/if}</span>
          </button>
        {/if}
      {:else}
        <div class="first">
          <div>
            <p class="pname">Start with the Glade profile</p>
            <p class="pinfo">Fabric {app.info?.tuffMcVersion}, the Glade mod and a tuned performance pack, set up in one click.</p>
          </div>
          <button class="btn primary play" onclick={createTuff} disabled={tuffBusy}>
            <span class="label">{#if tuffBusy}<span class="spinner"></span> Setting up{:else}<Icon name="spark" size={14} /> Create{/if}</span>
          </button>
          <button class="btn ghost" onclick={() => (creating = true)}>Custom profile</button>
        </div>
      {/if}
    </div>
  </div>
</section>

{#if creating}
  <ProfileModal onclose={() => (creating = false)} onsaved={(p) => selectProfile(p.id)} />
{/if}

<style>
  .home { position: absolute; inset: 0; overflow: hidden; }

  .brand { position: absolute; left: 0; right: 0; top: 12%; display: grid; justify-items: center; pointer-events: none; animation: float 7s ease-in-out infinite; }
  .emblem { width: clamp(52px, 6vw, 76px); height: auto; filter: drop-shadow(0 10px 18px rgba(30, 60, 40, 0.25)); margin-bottom: 6px; }
  .word {
    font: italic 600 clamp(64px, 8.5vw, 104px) / 1.05 var(--display); letter-spacing: -0.02em; color: #1F2A22;
    text-shadow: 0 2px 24px rgba(255, 255, 255, 0.55);
  }
  .hello { margin-top: 10px; color: #3A463D; font-size: 15.5px; text-shadow: 0 1px 10px rgba(255, 255, 255, 0.7); }
  .hello strong { color: var(--text); font-weight: 700; }
  @keyframes float { 50% { transform: translateY(-5px); } }

  :global(body.account-open) .quick { opacity: 0; pointer-events: none; }
  .quick {
    position: absolute; right: 22px; top: 62px; width: 280px; padding: 10px;
    transition: opacity var(--med);
  }
  .qhead { display: flex; align-items: center; justify-content: space-between; padding: 2px 4px 6px 6px; }
  .srv {
    width: 100%; display: flex; align-items: center; gap: 10px; padding: 7px 6px; border-radius: 10px;
    background: none; border: 0; color: var(--text); text-align: left; cursor: pointer; transition: background var(--fast);
  }
  .srv:hover:not(:disabled) { background: rgba(255, 255, 255, 0.6); }
  .srv:disabled { cursor: default; opacity: 0.6; }
  .srv img, .fav { width: 28px; height: 28px; border-radius: 5px; image-rendering: pixelated; flex: none; }
  .fav { display: grid; place-items: center; background: rgba(255, 255, 255, 0.6); color: var(--muted); }
  .sname { display: block; font-weight: 600; font-size: 15px; }
  .smeta { display: flex; align-items: center; gap: 6px; font-size: 13px; color: var(--muted); }
  .go { color: var(--faint); opacity: 0; transition: opacity var(--fast), color var(--fast); }
  .srv:hover .go { opacity: 1; color: var(--accent); }

  .dock-wrap { position: absolute; left: 0; right: 0; bottom: 30px; display: grid; justify-items: center; gap: 10px; padding: 0 24px; }
  .stage { font-size: 14px; color: var(--text); background: var(--glass-strong); backdrop-filter: var(--glass-blur); -webkit-backdrop-filter: var(--glass-blur); padding: 5px 12px; border-radius: 999px; border: 1px solid var(--glass-border); }
  .dock {
    display: flex; align-items: center; gap: 12px; padding: 10px;
    background: var(--glass); backdrop-filter: var(--glass-blur); -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border); border-radius: 20px;
    box-shadow: var(--glass-shadow);
    max-width: 100%;
  }
  .picker-wrap { position: relative; }
  .current {
    display: flex; align-items: center; gap: 12px; width: 290px; padding: 6px 12px 6px 6px;
    background: transparent; border: 1px solid transparent; border-radius: 13px; color: var(--text); text-align: left; cursor: pointer;
    transition: background var(--fast), border-color var(--fast);
  }
  .current:hover { background: rgba(255, 255, 255, 0.55); border-color: var(--glass-border); }
  .meta { display: grid; min-width: 0; flex: 1; }
  .pname { font-weight: 600; font-size: 16px; }
  .pinfo { font-size: 14px; color: var(--muted); }
  .caret { color: var(--muted); transition: transform var(--med) var(--ease); transform: rotate(180deg); }
  .caret.up { transform: none; }

  .picker {
    position: absolute; bottom: calc(100% + 14px); left: 0; width: 330px; max-height: 360px; overflow-y: auto; padding: 6px;
    z-index: 10;
  }
  .opt { width: 100%; display: flex; align-items: center; gap: 10px; padding: 7px; border-radius: 11px; background: none; border: 0; color: var(--text); text-align: left; cursor: pointer; }
  .opt .grow { display: grid; }
  .opt:hover { background: rgba(255, 255, 255, 0.6); }
  .opt.on { background: rgba(47, 107, 79, 0.1); }
  .picker-foot { display: flex; justify-content: space-between; border-top: 1px solid var(--border); margin-top: 4px; padding-top: 6px; }

  .play { position: relative; width: 220px; height: 52px; font-size: 18.5px; font-weight: 700; border-radius: 14px; overflow: hidden; display: grid; place-items: center; }
  .play .label { position: relative; display: inline-flex; align-items: center; gap: 10px; }
  .play .fill { position: absolute; left: 0; top: 0; bottom: 0; background: var(--accent-hover); transition: width 200ms linear; }
  .play.running { background: linear-gradient(180deg, #4E9A6E, #2F6B4F); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.25), 0 6px 18px rgba(47, 107, 79, 0.25); color: #F2FAF4; grid-template-rows: auto auto; gap: 0; }
  .play.running:hover { background: linear-gradient(180deg, #D9675C, var(--danger)); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.25), 0 8px 22px rgba(194, 70, 59, 0.3); }
  .play.running .hint { font: 500 12.5px var(--font); opacity: 0.8; letter-spacing: 0; }

  .first { display: flex; align-items: center; gap: 16px; padding: 4px 4px 4px 12px; }
  .first > div { max-width: 280px; }
</style>
