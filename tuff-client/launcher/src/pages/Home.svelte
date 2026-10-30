<script lang="ts">
  import { backOut } from "svelte/easing";
  import { scale } from "svelte/transition";
  import banner from "../assets/glade/banner.jpg";
  import clips from "../assets/glade/clips.jpg";
  import giveaways from "../assets/glade/giveaways.jpg";
  import launching from "../assets/glade/launching.jpg";
  import newversion from "../assets/glade/newversion.jpg";
  import nofriends from "../assets/glade/nofriends.jpg";
  import storeArt from "../assets/glade/store.png";
  import DownloadCard from "../components/DownloadCard.svelte";
  import Icon from "../components/Icon.svelte";
  import NewProfile from "../components/NewProfile.svelte";
  import Player3D from "../components/Player3D.svelte";
  import ProfileIcon from "../components/ProfileIcon.svelte";
  import ProfileModal from "../components/ProfileModal.svelte";
  import { api } from "../lib/api";
  import { ago, loaderName } from "../lib/format";
  import { DISCORD_INVITE, GLADE_BOT, RELEASES } from "../lib/links";
  import { launchFraction } from "../lib/progress";
  import { skinOf } from "../lib/skins.svelte";
  import { activeAccount, app, go, play, selectProfile, selectedProfile } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import { QUICK_SERVERS, loadQuickPlay, pings } from "../lib/quickplay.svelte";
  loadQuickPlay();

  const profile = $derived(selectedProfile());
  const account = $derived(activeAccount());
  const busy = $derived(app.launching !== null);
  const running = $derived(app.running !== null);
  const fraction = $derived(app.progress ? launchFraction(app.progress) : null);
  const percent = $derived(fraction === null ? null : Math.floor(fraction * 100));

  let picker = $state(false);
  let modCount = $state<number | null>(null);
  $effect(() => {
    const id = profile?.id;
    modCount = null;
    if (id) api.contentList(id, "mod").then((m) => { if (profile?.id === id) modCount = m.length; }).catch(() => {});
  });
  const featured = QUICK_SERVERS[1];
  const featuredPing = $derived(pings[featured.address]);
  let creating = $state(false);
  let picking = $state(false);

  function open(url: string) {
    api.openUrl(url).catch((e) => toast(e, "error"));
  }
  function discord() {
    if (DISCORD_INVITE) open(DISCORD_INVITE);
    else toast("The Glade Discord is opening soon");
  }
  function outside(node: HTMLElement) {
    const handler = (e: MouseEvent) => { if (!node.contains(e.target as Node)) picker = false; };
    document.addEventListener("mousedown", handler);
    return { destroy: () => document.removeEventListener("mousedown", handler) };
  }
</script>

<section class="home">
  <div class="banner" style:background-image="url({banner})">
    <div class="player">
      <Player3D skin={skinOf(account)} hoodie={app.settings?.gladeHoodie ?? true} still={!!app.settings?.lowMotion} />
    </div>

    <div class="launch">
      {#if !profile}
        <button class="play" onclick={() => (picking = true)}>
          <span class="pi"><Icon name="spark" size={22} /></span>
          <span class="tx"><b>START</b><small>Pick a Glade preset</small></span>
        </button>
        <button class="pill" onclick={() => (creating = true)}><Icon name="plus" size={12} /> Custom profile</button>
      {:else}
        <div class="btnrow">
          {#if running}
            <button class="play stop" onclick={() => api.killGame()}>
              <span class="pi"><Icon name="stop" size={20} /></span>
              <span class="tx"><b>PLAYING</b><small>Click to stop the game</small></span>
            </button>
          {:else}
            <button class="play" onclick={() => play(profile.id)} disabled={busy} title="Play (Ctrl+Enter)">
              {#if percent !== null}<span class="fill" style:width="{percent}%"></span>{/if}
              <span class="pi">{#if busy}<span class="spinner big"></span>{:else}<svg width="22" height="22" viewBox="0 0 24 24"><path d="M7 4.5v15a1 1 0 0 0 1.5.86l12-7.5a1 1 0 0 0 0-1.72l-12-7.5A1 1 0 0 0 7 4.5z" fill="currentColor" /></svg>{/if}</span>
              <span class="tx"><b>{busy ? (percent !== null ? `${percent}%` : "LOADING") : "PLAY"}</b><small>Minecraft {profile.gameVersion} · {loaderName(profile.loader)}</small></span>
              <span class="vs" role="button" tabindex="-1" aria-label="Change profile" onclick={(e) => { e.stopPropagation(); picker = !picker; }} onkeydown={() => {}}><Icon name="chevron" size={18} /></span>
            </button>
          {/if}
        </div>

        <div class="picker-wrap" use:outside>
          {#if busy}
            <DownloadCard inline />
          {:else}
            <button class="pill" onclick={() => (picker = !picker)} aria-haspopup="listbox" aria-expanded={picker}>
              <ProfileIcon name={profile.icon} size={20} /> {profile.name} <em>· {modCount === null ? loaderName(profile.loader) : `${modCount} mods`}</em>
            </button>
          {/if}
          {#if picker}
            <div class="picker" role="listbox" in:scale={{ start: 0.94, duration: 280, easing: backOut }} out:scale={{ start: 0.97, duration: 110 }}>
              {#each app.profiles as p (p.id)}
                <button class="opt" class:on={p.id === profile.id} role="option" aria-selected={p.id === profile.id} onclick={() => { selectProfile(p.id); picker = false; }}>
                  <ProfileIcon name={p.icon} size={30} />
                  <span class="grow"><span class="oname ellipsis">{p.name}</span><span class="oinfo">{loaderName(p.loader)} {p.gameVersion} · {ago(p.lastPlayed)}</span></span>
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
      {/if}
    </div>
  </div>

  <div class="lower">
    <div class="col">
      <div class="head"><h2>Latest news</h2><button class="link" onclick={() => open(RELEASES)}>View all News</button></div>
      <div class="news">
        <div class="slot"><span class="gd"></span>
          <button class="nc poster hot" onclick={() => open(RELEASES)}>
            <img src={clips} alt="Kill clips" />
            <span class="tag">● New</span><span class="lbl">KILL CLIPS</span><span class="arrow">→</span>
          </button>
        </div>
        <div class="slot"><span class="gd"></span>
          <button class="nc poster" onclick={discord}>
            <img src={giveaways} alt="Weekly giveaways" />
            <span class="lbl">WEEKLY GIVEAWAYS</span><span class="arrow">→</span>
          </button>
        </div>
        <div class="slot"><span class="gd"></span>
          <button class="nc" onclick={() => open(RELEASES)}>
            <span class="art" style:background-image="url({newversion})" style:background-position="right -40px center"></span>
            <span class="txt"><small>NEW VERSION</small><b>GLADE {app.info?.version ?? ""}</b><em>IS NOW AVAILABLE TO PLAY!</em></span>
            <span class="arrow">→</span>
          </button>
        </div>
        <div class="slot"><span class="gd"></span>
          <button class="nc server" onclick={() => profile && !busy && !running && play(profile.id, featured.address)} disabled={!profile}>
            <span class="art blur" style:background-image="url({banner})"></span>
            <span class="center">
              {#if featuredPing && featuredPing !== "offline" && featuredPing.favicon}<img class="logo" src={featuredPing.favicon} alt="" />{:else}<span class="logo letter">{featured.name[0]}</span>{/if}
              <small>FEATURED SERVER</small>
              <b>{featured.name.toUpperCase()}</b>
              <em>PLAY NOW · {featured.address.toUpperCase()}</em>
            </span>
            <span class="arrow">→</span>
          </button>
        </div>
      </div>
    </div>

    <div class="side">
      <div class="head"><h2>Friends</h2><button class="link" onclick={() => toast("Friends are coming soon")}>View Friends List</button></div>
      <div class="friends">
        <img src={nofriends} alt="" />
        <b>Friends are coming soon</b>
        <span>See who's online, send invites and join their servers.</span>
      </div>
      <button class="addf" onclick={() => toast("Friends are coming soon")}><Icon name="plus" size={14} /> Add a friend</button>
      <div class="mini">
        <button class="dc" onclick={discord}>
          <svg width="22" height="22" viewBox="0 0 24 24"><path fill="currentColor" d="M20.3 4.4A19.8 19.8 0 0 0 15.4 3l-.6 1.3a18.3 18.3 0 0 0-5.5 0L8.7 3a19.7 19.7 0 0 0-4.9 1.5C.7 9.1-.2 13.6.3 18a19.9 19.9 0 0 0 6 3l1.3-2a12.9 12.9 0 0 1-2-1l.5-.4a14.2 14.2 0 0 0 12.2 0l.5.4c-.6.4-1.3.7-2 1l1.3 2a19.8 19.8 0 0 0 6-3c.5-5.1-.9-9.6-3.8-13.6ZM8 15.3c-1.2 0-2.2-1.1-2.2-2.4S6.8 10.5 8 10.5s2.2 1.1 2.2 2.4-1 2.4-2.2 2.4Zm8 0c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4Z" /></svg>
          <span><b>Discord</b><small>{DISCORD_INVITE ? "Join the community" : "Opening soon"}</small></span>
        </button>
        <div class="sc">
          <span class="cart"><Icon name="spark" size={18} /></span>
          <span><b>Store</b><small>Coming soon</small></span>
          <img src={storeArt} alt="" />
        </div>
      </div>
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
  .home { position: absolute; inset: 0; padding: 62px 12px 12px 0; display: grid; grid-template-rows: minmax(250px, 44%) 1fr; gap: 14px; font-size: 13px; }

  .banner { position: relative; border-radius: 14px; overflow: hidden; border: 1px solid var(--border); background: center 22% / cover no-repeat #0b100d; }
  .banner::after { content: ""; position: absolute; inset: 0; pointer-events: none; background: radial-gradient(ellipse at center, transparent 55%, rgba(0, 0, 0, 0.45)); }
  .player { position: absolute; left: 13%; bottom: -6px; z-index: 2; }

  .launch { position: absolute; left: 50%; top: 47%; transform: translate(-50%, -50%); z-index: 3; display: grid; justify-items: center; gap: 18px; }
  .btnrow { display: flex; }
  .play {
    position: relative; height: 70px; padding: 0 9px; border: 0; border-radius: 20px; display: flex; align-items: center; gap: 14px; cursor: pointer; overflow: hidden; color: #052a13;
    background: linear-gradient(180deg, #4ff09a 0%, #27c46c 55%, #1fae5c 100%);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55), inset 0 -3px 0 rgba(0, 0, 0, 0.14), 0 6px 0 #138a45, 0 8px 0 rgba(0, 0, 0, 0.25), 0 22px 50px rgba(39, 196, 108, 0.38);
    transition: transform 120ms var(--ease), box-shadow 120ms var(--ease), filter var(--fast);
  }
  .play::before { content: ""; position: absolute; left: 10px; right: 10px; top: 4px; height: 26px; border-radius: 16px 16px 30px 30px; background: linear-gradient(rgba(255, 255, 255, 0.28), rgba(255, 255, 255, 0)); pointer-events: none; z-index: 1; }
  .play:hover:not(:disabled) { filter: brightness(1.05); }
  .play:active:not(:disabled) { transform: translateY(5px); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.55), inset 0 -3px 0 rgba(0, 0, 0, 0.14), 0 1px 0 #138a45, 0 3px 0 rgba(0, 0, 0, 0.25), 0 12px 30px rgba(39, 196, 108, 0.3); }
  .play:disabled { cursor: default; }
  .play .fill { position: absolute; left: 0; top: 0; bottom: 0; background: rgba(255, 255, 255, 0.22); transition: width 200ms linear; }
  .pi { position: relative; z-index: 2; width: 52px; height: 52px; border-radius: 15px; background: #0b3a1f; display: grid; place-items: center; color: #5dffa8; box-shadow: inset 0 2px 6px rgba(0, 0, 0, 0.35), 0 1px 0 rgba(255, 255, 255, 0.35); }
  .spinner.big { width: 20px; height: 20px; border-width: 3px; }
  .tx { position: relative; z-index: 2; display: grid; text-align: left; padding-right: 22px; min-width: 150px; }
  .tx b { font: 800 26px/1 var(--brand); letter-spacing: 0.06em; text-shadow: 0 1px 0 rgba(255, 255, 255, 0.35); }
  .tx small { font: 600 11.5px var(--font); color: rgba(5, 42, 19, 0.72); margin-top: 5px; }
  .vs { position: relative; z-index: 2; width: 44px; height: 52px; border-radius: 14px; display: grid; place-items: center; background: rgba(5, 42, 19, 0.12); box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.25); }
  .vs:hover { background: rgba(5, 42, 19, 0.2); }
  .play.stop { background: linear-gradient(180deg, #ff8a7a, #e5484d); color: #3a0606; box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.45), 0 6px 0 #a3252a, 0 8px 0 rgba(0, 0, 0, 0.25), 0 22px 50px rgba(229, 72, 77, 0.35); }
  .play.stop .pi { background: #4a0b0d; color: #ff9d92; }
  .play.stop .tx small { color: rgba(58, 6, 6, 0.75); }

  .picker-wrap { position: relative; display: grid; justify-items: center; }
  .pill { display: flex; align-items: center; gap: 8px; height: 32px; padding: 0 14px 0 6px; border-radius: 999px; border: 1px solid rgba(255, 255, 255, 0.08); background: rgba(10, 14, 12, 0.68); backdrop-filter: blur(8px); color: #cfd6d1; font: 600 12px var(--font); cursor: pointer; }
  .pill em { font-style: normal; color: #75807a; }
  .pill:hover { background: rgba(10, 14, 12, 0.85); color: #fff; }
  .picker { position: absolute; top: calc(100% + 10px); width: 320px; max-height: 300px; overflow-y: auto; padding: 8px; z-index: 10; transform-origin: top center; border-radius: 16px; background: var(--glass-strong); border: 1px solid var(--border-strong); box-shadow: 0 20px 60px rgba(0, 0, 0, 0.5); }
  .opt { width: 100%; display: flex; align-items: center; gap: 12px; padding: 8px 10px; border-radius: 12px; background: none; border: 0; color: var(--text); text-align: left; cursor: pointer; }
  .opt .grow { display: grid; min-width: 0; }
  .opt:hover { background: var(--surface-3); }
  .opt.on { background: rgba(47, 191, 106, 0.12); }
  .oname { font-weight: 600; font-size: 13.5px; }
  .oinfo { font-size: 11.5px; color: var(--muted); }
  .picker-foot { display: flex; justify-content: space-between; border-top: 1px solid var(--border); margin-top: 6px; padding-top: 6px; }

  .lower { display: grid; grid-template-columns: 1fr clamp(250px, 26%, 300px); gap: 16px; min-height: 0; }
  .col, .side { display: flex; flex-direction: column; min-height: 0; }
  .head { display: flex; align-items: baseline; justify-content: space-between; margin-bottom: 10px; }
  .head h2 { font: 800 14px var(--font); text-transform: uppercase; letter-spacing: 0.01em; }
  .link { background: none; border: 0; color: var(--muted); font: 500 11px var(--font); cursor: pointer; }
  .link:hover { color: var(--text); }
  .news { flex: 1; min-height: 0; display: grid; grid-template-columns: 1fr 1fr; grid-template-rows: 1fr 1fr; gap: 16px 14px; padding: 5px 0 0 5px; }
  .slot { position: relative; min-height: 0; }
  .gd { position: absolute; left: -5px; top: -5px; width: 11px; height: 11px; border-radius: 50%; background: var(--online); box-shadow: 0 0 0 3px var(--bg); z-index: 3; }
  .nc { position: absolute; inset: 0; width: 100%; border-radius: 10px; overflow: hidden; border: 2px solid #25282d; background: #2b2b2f; padding: 0; cursor: pointer; text-align: left; color: #fff; transition: transform var(--med) var(--spring), border-color var(--fast); }
  .nc.hot { border-color: var(--accent); }
  .nc:disabled { cursor: default; }
  .art.blur { background-size: cover; background-position: center; filter: blur(3px) brightness(0.5) saturate(1.2); transform: scale(1.08); }
  .center { position: absolute; inset: 0; display: grid; place-items: center; align-content: center; text-align: center; gap: 2px; }
  .center .logo { width: 46px; height: 46px; border-radius: 10px; margin-bottom: 8px; image-rendering: auto; object-fit: contain; }
  .center .letter { display: grid; place-items: center; background: #d11fd1; font: 800 22px var(--brand); color: #fff; }
  .center small { font: 600 10px var(--font); letter-spacing: 0.3em; color: #e8ffe9; opacity: 0.85; }
  .center b { font: 800 clamp(18px, 2.4vw, 28px)/1.1 var(--brand); margin: 4px 0 8px; }
  .center em { font: 800 8px var(--font); font-style: normal; letter-spacing: 0.14em; background: rgba(255, 255, 255, 0.92); color: #1b1b1b; padding: 3px 9px; border-radius: 3px; }
  .addf { margin-top: 10px; height: 38px; border-radius: 9px; background: var(--surface); border: 1px dashed #33363c; display: flex; align-items: center; justify-content: center; gap: 8px; color: #b9bdc5; font: 600 12.5px var(--font); cursor: pointer; }
  .addf:hover { color: var(--text); border-color: var(--border-strong); }
  .nc:hover { transform: translateY(-2px); border-color: rgba(47, 191, 106, 0.6); }
  .nc .art { position: absolute; inset: 0; background-size: auto 150%; background-repeat: no-repeat; }
  .nc img { position: absolute; inset: 0; width: 100%; height: 100%; object-fit: cover; object-position: top; }
  .txt { position: absolute; inset: 0; display: grid; align-content: center; justify-items: start; padding-left: 22px; background: linear-gradient(90deg, rgba(20, 21, 24, 0.88), rgba(20, 21, 24, 0.1) 62%); }
  .txt small { font: 600 10px var(--font); letter-spacing: 0.3em; color: #e8ffe9; opacity: 0.85; }
  .txt b { font: 800 clamp(20px, 2.6vw, 30px)/1.05 var(--brand); letter-spacing: 0.01em; margin: 6px 0 8px; }
  .txt em { font: 800 8px var(--font); font-style: normal; letter-spacing: 0.14em; background: rgba(255, 255, 255, 0.92); color: #1b1b1b; padding: 3px 9px; border-radius: 3px; }
  .arrow { position: absolute; right: 10px; bottom: 10px; width: 24px; height: 24px; border-radius: 50%; background: #fff; color: #111; display: grid; place-items: center; font: 900 12px var(--font); box-shadow: 0 4px 12px rgba(0, 0, 0, 0.35); }
  .tag { position: absolute; right: 8px; top: 8px; background: var(--danger); font: 700 9.5px var(--font); padding: 3px 8px; border-radius: 999px; }
  .lbl { position: absolute; left: 10px; bottom: 10px; font: 700 10px var(--font); letter-spacing: 0.06em; padding: 4px 9px; border-radius: 6px; background: rgba(10, 12, 14, 0.75); }

  .friends { flex: 1; min-height: 0; border-radius: 12px; background: var(--surface); border: 1px solid var(--border); display: grid; align-content: center; justify-items: center; text-align: center; padding: 10px 16px; overflow: hidden; }
  .friends img { width: 150px; height: auto; height: auto; -webkit-mask-image: radial-gradient(closest-side, #000 55%, transparent 100%); mask-image: radial-gradient(closest-side, #000 55%, transparent 100%); }
  .friends b { font: 700 14px var(--font); margin-top: 6px; }
  .friends span { color: var(--muted); font-size: 12px; margin-top: 3px; max-width: 220px; }
  .mini { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; margin-top: 10px; }
  .mini > * { position: relative; height: 70px; border-radius: 12px; padding: 12px; display: flex; flex-direction: column; justify-content: space-between; overflow: hidden; text-align: left; }
  .mini b { font: 800 13px var(--font); display: block; } .mini small { font: 600 10.5px var(--font); opacity: 0.75; }
  .dc { border: 0; background: linear-gradient(135deg, #4752c4, #5865f2); color: #fff; cursor: pointer; }
  .dc::after { content: ""; position: absolute; right: -16px; bottom: -22px; width: 70px; height: 70px; border-radius: 50%; background: rgba(255, 255, 255, 0.1); }
  .sc { background: var(--surface); border: 1px solid var(--border); }
  .sc .cart { color: var(--online); display: flex; }
  .sc img { position: absolute; right: 2px; bottom: -8px; height: 84px; filter: drop-shadow(0 6px 12px rgba(0, 0, 0, 0.5)); }
</style>
