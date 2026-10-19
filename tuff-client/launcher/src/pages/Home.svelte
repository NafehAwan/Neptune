<script lang="ts">
  import { onMount } from "svelte";
  import { backOut } from "svelte/easing";
  import { fly, scale } from "svelte/transition";
  import Icon from "../components/Icon.svelte";
  import ProfileIcon from "../components/ProfileIcon.svelte";
  import NewProfile from "../components/NewProfile.svelte";
  import ProfileModal from "../components/ProfileModal.svelte";
  import { api } from "../lib/api";
  import { ago, compact, greeting, playTime, loaderName } from "../lib/format";
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
  let picking = $state(false);
  let servers = $state<Server[]>([]);
  let pings = $state<Record<string, PingResult | "offline" | undefined>>({});

  onMount(async () => {
    servers = (await api.listServers()).slice(0, 4);
    for (const s of servers) {
      api.pingServer(s.address).then((r) => (pings[s.id] = r)).catch(() => (pings[s.id] = "offline"));
    }
  });

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
    <h1 class="word">Glade</h1>
    {#if account}<p class="hello">{greeting()}, <b>{account.name}</b></p>{/if}
  </div>

  <div class="news" in:fly={{ x: 16, duration: 400, delay: 120 }}>
    <div class="newscard lift">
      <div class="k">What's new</div>
      <div class="tt">Glade 1.1: always up to date</div>
      <div class="ss">Glade is in every Fabric 26.3 profile and checked before each launch.</div>
    </div>
    {#if servers.length}
      <div class="newscard lift">
        <div class="k">Quick join</div>
        {#each servers as s (s.id)}
          {@const ping = pings[s.id]}
          <button class="srv" onclick={() => join(s)} disabled={busy || running} title="Join {s.address}">
            <Icon name="globe" size={15} />
            <span class="sname ellipsis">{s.name}</span>
            <span class="p">{ping === undefined ? "…" : ping === "offline" ? "off" : compact(ping.online)}</span>
          </button>
        {/each}
      </div>
    {/if}
  </div>

  <div class="playbar-wrap">
    {#if app.progress}
      <div class="stage" transition:fly={{ y: 6, duration: 160 }}>
        {app.progress.stage}{#if app.progress.total > 1} · {app.progress.done}/{app.progress.total}{/if}
      </div>
    {/if}

    <div class="playbar" in:fly={{ y: 20, duration: 420, delay: 80 }}>
      {#if profile}
        <div class="picker-wrap" use:outside>
          <button class="prof" onclick={() => (picker = !picker)} aria-haspopup="listbox" aria-expanded={picker}>
            <span class="pi"><Icon name="cube" size={20} /></span>
            <span class="meta">
              <span class="pname ellipsis">{profile.name} · {loaderName(profile.loader)} {profile.gameVersion}</span>
              <span class="pinfo">{playTime(profile.playTimeSecs)}</span>
            </span>
            <span class="caret" class:up={picker}><Icon name="chevron" size={16} /></span>
          </button>
          {#if picker}
            <div class="picker" role="listbox" in:scale={{ start: 0.92, duration: 320, easing: backOut }} out:scale={{ start: 0.96, duration: 120 }}>
              {#each app.profiles as p (p.id)}
                <button class="opt" class:on={p.id === profile.id} role="option" aria-selected={p.id === profile.id}
                  onclick={() => { selectProfile(p.id); picker = false; }}>
                  <ProfileIcon name={p.icon} size={30} />
                  <span class="grow">
                    <span class="oname ellipsis">{p.name}</span>
                    <span class="oinfo">{loaderName(p.loader)} {p.gameVersion} · {ago(p.lastPlayed)}</span>
                  </span>
                  {#if p.isTuff}<span class="chip copper">Glade</span>{/if}
                </button>
              {/each}
              <div class="picker-foot">
                <button class="btn ghost small" onclick={() => { picker = false; picking = true; }}><Icon name="plus" size={12} /> New profile</button>
                <button class="btn ghost small" onclick={() => go("library")}>Manage</button>
              </div>
            </div>
          {/if}
        </div>

        {#if running}
          <button class="play running" onclick={() => api.killGame()}>
            <span class="label">Playing</span>
            <span class="hint">Click to stop</span>
          </button>
        {:else}
          <button class="play" onclick={() => play(profile.id)} disabled={busy} title="Play (Ctrl+Enter)">
            {#if percent !== null}<span class="fill" style:width="{percent}%"></span>{/if}
            <span class="label">{#if busy}{percent !== null ? `${percent}%` : "Loading"}{:else}<Icon name="play" size={15} /> Play{/if}</span>
          </button>
        {/if}
      {:else}
        <div class="first">
          <div>
            <p class="pname">Pick a Glade preset</p>
            <p class="pinfo">Performance, Performance+, Performance++ or Scenic: Fabric {app.info?.tuffMcVersion} with hand-picked mods, ready in one click.</p>
          </div>
          <button class="play" onclick={() => (picking = true)}>
            <span class="label"><Icon name="spark" size={15} /> Choose</span>
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
{#if picking}
  <NewProfile onclose={() => (picking = false)} oncustom={() => (creating = true)} />
{/if}

<style>
  /* Sizes and colours follow the Grove Glass launcher mockup (design/app.js, launcher()). */
  .home { position: absolute; inset: 0; overflow: hidden; font-size: 13px; }

  .brand { position: absolute; left: 44px; right: 340px; top: 200px; text-align: center; pointer-events: none; }
  .word { font: italic 600 88px / 1 var(--display); letter-spacing: -0.02em; color: #1F2A22; }
  .hello { margin-top: 12px; font-size: 15px; color: #3A463D; }
  .hello b { color: #1F2A22; }

  .news { position: absolute; right: 22px; top: 70px; width: 270px; display: flex; flex-direction: column; gap: 10px; transition: opacity var(--med); }
  :global(body.account-open) .news { opacity: 0; pointer-events: none; }
  .newscard {
    border-radius: 14px; padding: 14px;
    background: rgba(255, 255, 255, 0.38); border: 1px solid rgba(255, 255, 255, 0.7);
    backdrop-filter: blur(20px) saturate(1.5); -webkit-backdrop-filter: blur(20px) saturate(1.5);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8), 0 10px 30px rgba(30, 60, 40, 0.12);
  }
  .k { font: 500 10.5px var(--mono); color: var(--muted); letter-spacing: 0.1em; text-transform: uppercase; }
  .tt { font-weight: 600; margin-top: 6px; font-size: 13.5px; }
  .ss { color: var(--muted); font-size: 12px; margin-top: 4px; line-height: 1.45; }
  .srv {
    width: 100%; display: flex; align-items: center; gap: 10px; padding: 8px 0;
    background: none; border: 0; border-top: 1px solid rgba(255, 255, 255, 0.6); color: var(--text);
    font: 400 12.5px var(--font); text-align: left; cursor: pointer;
  }
  .k + .srv { border-top: 0; }
  .srv :global(.icon) { color: var(--muted); }
  .srv:hover:not(:disabled) .sname { color: var(--accent); }
  .srv:disabled { cursor: default; }
  .sname { flex: 1; min-width: 0; }
  .p { margin-left: auto; font: 500 11px var(--mono); color: var(--muted); }

  .playbar-wrap { position: absolute; left: calc(50% - 150px); transform: translateX(-50%); bottom: 26px; display: grid; justify-items: center; gap: 10px; }
  .stage { font-size: 12.5px; color: var(--text); background: rgba(255, 255, 255, 0.6); backdrop-filter: blur(14px); padding: 4px 12px; border-radius: 999px; border: 1px solid rgba(255, 255, 255, 0.7); }
  .playbar {
    display: flex; align-items: center; gap: 14px; padding: 10px 10px 10px 14px; border-radius: 18px;
    background: rgba(255, 255, 255, 0.38); border: 1px solid rgba(255, 255, 255, 0.7);
    backdrop-filter: blur(20px) saturate(1.5); -webkit-backdrop-filter: blur(20px) saturate(1.5);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8), 0 10px 30px rgba(30, 60, 40, 0.12);
  }
  .picker-wrap { position: relative; }
  .prof {
    display: flex; align-items: center; gap: 12px; min-width: 210px; max-width: 300px; padding: 0;
    background: none; border: 0; color: var(--text); text-align: left; cursor: pointer;
  }
  .pi { width: 40px; height: 40px; border-radius: 10px; background: rgba(255, 255, 255, 0.55); display: grid; place-items: center; color: var(--accent); flex: none; }
  .meta { display: grid; min-width: 0; }
  .pname { font-weight: 600; font-size: 13px; }
  .pinfo { font-size: 12px; color: var(--muted); }
  .caret { color: var(--muted); display: grid; transition: transform var(--med) var(--ease); }
  .caret.up { transform: rotate(180deg); }

  .picker {
    position: absolute; bottom: calc(100% + 18px); left: -14px; width: 320px; max-height: 360px; overflow-y: auto; padding: 8px; z-index: 10; transform-origin: bottom left;
    border-radius: 18px; background: rgba(251, 251, 248, 0.92); border: 1px solid rgba(255, 255, 255, 0.7);
    backdrop-filter: blur(20px) saturate(1.5); box-shadow: 0 20px 60px rgba(30, 60, 40, 0.18);
  }
  .opt { width: 100%; display: flex; align-items: center; gap: 12px; padding: 8px 10px; border-radius: 12px; background: none; border: 0; color: var(--text); text-align: left; cursor: pointer; }
  .opt .grow { display: grid; min-width: 0; }
  .opt:hover { background: rgba(255, 255, 255, 0.6); }
  .opt.on { background: rgba(255, 255, 255, 0.75); }
  .oname { font-weight: 600; font-size: 13.5px; }
  .oinfo { font-size: 11.5px; color: var(--muted); }
  .picker-foot { display: flex; justify-content: space-between; border-top: 1px solid rgba(31, 42, 34, 0.08); margin-top: 6px; padding-top: 6px; }

  .play {
    position: relative; height: 48px; padding: 0 36px; border: 0; border-radius: 10px; overflow: hidden;
    background: var(--accent); color: #fff; font: 700 16px var(--font); cursor: pointer;
    display: grid; place-items: center; transition: background var(--fast);
  }
  .play:hover:not(:disabled) { background: var(--accent-hover); }
  .play:disabled { cursor: default; }
  .play .label { position: relative; display: inline-flex; align-items: center; gap: 8px; }
  .play .fill { position: absolute; left: 0; top: 0; bottom: 0; background: var(--accent-hover); transition: width 200ms linear; }
  .play.running { grid-template-rows: auto auto; gap: 0; padding: 0 30px; }
  .play.running:hover { background: var(--danger); }
  .play.running .hint { font: 500 11.5px var(--font); opacity: 0.85; }

  .first { display: flex; align-items: center; gap: 14px; }
  .first > div { max-width: 260px; }
  .first .pinfo { margin-top: 2px; }
</style>
