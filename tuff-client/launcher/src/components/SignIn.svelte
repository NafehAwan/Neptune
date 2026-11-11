<script lang="ts">
  // First run: no account yet. Microsoft sign-in opens Microsoft's own page in a window.
  import { fade } from "svelte/transition";
  import art from "../assets/glade/signin.jpg";
  import logo from "../assets/brand/leaf.png";
  import { api, windowAction } from "../lib/api";
  import { app, refreshAccounts, signInMicrosoft } from "../lib/state.svelte";
  import Icon from "./Icon.svelte";

  let offline = $state(false);
  let name = $state("");
  let error = $state("");

  async function microsoft() {
    error = "";
    try {
      await signInMicrosoft();
    } catch (e) {
      if (!String(e).includes("window closed")) error = String(e);
    }
  }

  async function addOffline(e: Event) {
    e.preventDefault();
    error = "";
    try {
      const account = await api.addOfflineAccount(name.trim());
      await api.setActiveAccount(account.id);
      await refreshAccounts();
    } catch (err) {
      error = String(err);
    }
  }
</script>

<div class="signin" transition:fade={{ duration: 200 }}>
  <div class="art" style:background-image="url({art})" data-tauri-drag-region></div>
  <div class="win">
    <button onclick={() => windowAction("minimize")} aria-label="Minimize"><Icon name="minimize" size={13} /></button>
    <button class="close" onclick={() => windowAction("close")} aria-label="Close"><Icon name="close" size={13} /></button>
  </div>
  <div class="panel">
    <div class="brand"><img src={logo} alt="" width="38" height="38" /> Glade</div>
    <h1>Step into<br />the <span>Glade.</span></h1>
    <p class="sub">Sign in with your Microsoft account to play, join servers and see your friends.</p>
    <button class="ms" onclick={microsoft} disabled={app.signingIn}>
      {#if app.signingIn}<span class="spinner"></span> Waiting for Microsoft…{:else}<i><b style="background:#f25022"></b><b style="background:#7fba00"></b><b style="background:#00a4ef"></b><b style="background:#ffb900"></b></i> Sign in with Microsoft{/if}
    </button>
    <div class="or">or</div>
    {#if offline}
      <form class="off-form" onsubmit={addOffline}>
        <input class="input" bind:value={name} maxlength="16" placeholder="Username" />
        <button class="btn primary" disabled={!/^[A-Za-z0-9_]{3,16}$/.test(name.trim())}>Play</button>
      </form>
    {:else}
      <button class="off" onclick={() => (offline = true)}><Icon name="user" size={16} /> Play offline (singleplayer)</button>
    {/if}
    {#if error}<p class="err selectable">{error}</p>{/if}
    <p class="fine">Glade opens Microsoft's own sign-in page and never sees your password.
      <button class="later" onclick={() => (app.signinSkipped = true)}>Not now</button></p>
  </div>
</div>

<style>
  .signin { position: fixed; inset: 0; z-index: 60; background: var(--bg); }
  .art { position: absolute; right: 0; top: 0; bottom: 0; width: 66%; background: 60% center / cover no-repeat; -webkit-mask-image: linear-gradient(90deg, transparent, #000 34%); mask-image: linear-gradient(90deg, transparent, #000 34%); }
  .win { position: absolute; right: 12px; top: 10px; display: flex; gap: 6px; z-index: 2; }
  .win button { width: 38px; height: 34px; border-radius: 9px; border: 0; background: rgba(20, 21, 24, 0.6); color: #cfd2d7; display: grid; place-items: center; cursor: pointer; backdrop-filter: blur(6px); }
  .win button:hover { background: var(--surface-3); }
  .win .close:hover { background: var(--danger); color: #fff; }
  .panel { position: absolute; left: clamp(32px, 6vw, 80px); top: 50%; transform: translateY(-50%); width: min(380px, 40vw); }
  .brand { display: flex; align-items: center; gap: 12px; font: 800 22px var(--font); margin-bottom: 40px; }
  h1 { font: 800 38px/1.08 var(--font); letter-spacing: -0.02em; }
  h1 span { color: var(--online); }
  .sub { color: var(--muted); margin: 12px 0 28px; line-height: 1.55; font-size: 14px; }
  .ms { width: 100%; height: 54px; border-radius: 12px; border: 0; background: #fff; color: #1b1b1b; display: flex; align-items: center; justify-content: center; gap: 12px; font: 700 15px var(--font); cursor: pointer; box-shadow: 0 14px 34px rgba(0, 0, 0, 0.35); }
  .ms:disabled { cursor: default; opacity: 0.85; }
  .ms i { display: grid; grid-template-columns: 9px 9px; gap: 2px; }
  .ms i b { width: 9px; height: 9px; }
  .or { display: flex; align-items: center; gap: 12px; color: var(--faint); font-size: 12px; margin: 18px 0; }
  .or::before, .or::after { content: ""; flex: 1; height: 1px; background: var(--border-strong); }
  .off { width: 100%; height: 48px; border-radius: 12px; border: 1px solid var(--border-strong); background: var(--surface); display: flex; align-items: center; justify-content: center; gap: 10px; color: #cfd2d7; font: 600 14px var(--font); cursor: pointer; }
  .off:hover { background: var(--surface-2); }
  .off-form { display: flex; gap: 8px; }
  .off-form .input { height: 48px; }
  .off-form .btn { height: 48px; }
  .err { color: var(--danger); font-size: 12.5px; margin-top: 12px; }
  .fine { margin-top: 24px; color: var(--faint); font-size: 11.5px; line-height: 1.5; }
  .later { background: none; border: 0; padding: 0; color: var(--online); font: 600 11.5px var(--font); cursor: pointer; margin-left: 4px; }
</style>
