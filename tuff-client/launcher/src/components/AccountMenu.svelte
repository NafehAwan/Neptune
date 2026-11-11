<script lang="ts">
  import { backOut } from "svelte/easing";
  import { fly, scale } from "svelte/transition";
  import { api } from "../lib/api";
  import { skinOf } from "../lib/skins.svelte";
  import { activeAccount, app, go, refreshAccounts, signInMicrosoft } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import AccountModal from "./AccountModal.svelte";
  import Face from "./Face.svelte";
  import Head3D from "./Head3D.svelte";
  import Icon from "./Icon.svelte";

  let open = $state(false);
  let adding = $state(false);
  let name = $state("");
  let error = $state("");
  const current = $derived(activeAccount());

  // other floating panels step aside while the menu is open
  $effect(() => {
    document.body.classList.toggle("account-open", open);
    return () => document.body.classList.remove("account-open");
  });

  async function microsoft() {
    open = false;
    try {
      await signInMicrosoft();
    } catch (e) {
      if (!String(e).includes("window closed")) toast(e, "error", 9000);
    }
  }

  async function choose(id: string) {
    await api.setActiveAccount(id);
    await refreshAccounts();
  }

  async function remove(id: string, who: string) {
    await api.removeAccount(id);
    await refreshAccounts();
    toast(`Removed ${who}`);
  }

  async function addOffline(e: Event) {
    e.preventDefault();
    error = "";
    try {
      const account = await api.addOfflineAccount(name.trim());
      await api.setActiveAccount(account.id);
      await refreshAccounts();
      toast(`Added ${account.name}`, "success");
      name = "";
      adding = false;
    } catch (err) {
      error = String(err);
    }
  }

  function focusNow(node: HTMLInputElement) {
    requestAnimationFrame(() => node.focus());
  }

  function outside(node: HTMLElement) {
    const handler = (e: MouseEvent) => {
      if (!node.contains(e.target as Node)) {
        open = false;
        adding = false;
      }
    };
    document.addEventListener("mousedown", handler);
    return { destroy: () => document.removeEventListener("mousedown", handler) };
  }
</script>

<div class="wrap" use:outside>
  <button class="chip-btn" class:open class:out={!current} onclick={() => (open = !open)} aria-haspopup="menu" aria-expanded={open}>
    {#if current}
      <Face skin={skinOf(current)} size={32} />
    {:else}
      <span class="nohead"><Icon name="user" size={14} /></span>
    {/if}
    <span class="who2"><small>{current ? (current.kind === "msa" ? "Signed in" : "Offline") : "Not signed in"}</small><span class="name ellipsis">{current?.name ?? "Add account"}</span></span>
  </button>

  {#if open}
    <div class="menu" role="menu" in:scale={{ start: 0.92, duration: 320, easing: backOut }} out:scale={{ start: 0.96, duration: 120 }}>
      {#if current}
        <div class="hero">
          <div class="big"><Head3D skin={skinOf(current)} size={84} spin yaw={-30} pitch={-16} /></div>
          <div class="who">
            <p class="hname">{current.name}</p>
            <p class="hkind">{current.kind === "msa" ? "Microsoft account · skin from Minecraft" : "Offline account · default skin"}</p>
            <div class="pills">
              <button class="pill" onclick={() => { open = false; go("wardrobe"); }}>Change skin</button>
              <button class="pill" onclick={() => { open = false; go("settings"); }}>Profile</button>
            </div>
          </div>
        </div>
        <div class="sep"></div>
      {/if}

      <p class="label">Accounts</p>
      {#each app.accounts as a (a.id)}
        <div class="acct" class:active={a.id === app.activeAccount}>
          <button class="pick" onclick={() => choose(a.id)} role="menuitem">
            <Head3D skin={skinOf(a)} size={28} />
            <span class="grow">
              <span class="n">{a.name}</span>
              <span class="k">{a.kind === "msa" ? "Microsoft" : "Offline · singleplayer and offline servers"}</span>
            </span>
            {#if a.id === app.activeAccount}<span class="tick"><Icon name="check" size={16} /></span>{/if}
          </button>
          <button class="del" onclick={() => remove(a.id, a.name)} aria-label="Remove {a.name}" title="Remove"><Icon name="trash" size={12} /></button>
        </div>
      {/each}

      <div class="sep"></div>
      <button class="action" onclick={microsoft} disabled={app.signingIn}>
        <span class="ai">{#if app.signingIn}<span class="spinner"></span>{:else}<Icon name="plus" size={16} />{/if}</span> {app.signingIn ? "Waiting for Microsoft…" : "Add Microsoft account"}
      </button>
      <button class="action" class:on={adding} onclick={() => { adding = !adding; error = ""; }}>
        <span class="ai"><Icon name="user-plus" size={16} /></span> Add offline account
      </button>
      {#if adding}
        <form class="offline" onsubmit={addOffline} transition:fly={{ y: -4, duration: 140 }}>
          <input class="input" bind:value={name} maxlength="16" placeholder="Username" use:focusNow />
          <button class="addbtn" disabled={!/^[A-Za-z0-9_]{3,16}$/.test(name.trim())}>Add</button>
        </form>
        {#if error}<p class="err">{error}</p>{/if}
      {/if}
    </div>
  {/if}
</div>

{#if app.accountModal}
  <AccountModal onclose={() => (app.accountModal = false)} />
{/if}

<style>
  /* Sizes follow the Grove Glass mockups: the launcher chip (design/app.js, launcher()) and the account menu (accountMenu()). */
  .wrap { position: relative; }
  .chip-btn {
    display: flex; align-items: center; gap: 9px; height: 42px; width: 176px; padding: 0 12px 0 6px;
    background: linear-gradient(90deg, #1f7a45, #2fbf6a); border: 0; border-radius: 11px;
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.18), 0 8px 22px rgba(47, 191, 106, 0.18);
    color: #fff; font: 700 13px var(--font); cursor: pointer; text-align: left; transition: filter var(--fast);
  }
  .chip-btn.out { background: var(--surface); border: 1px solid var(--border); box-shadow: none; }
  .chip-btn:hover, .chip-btn.open { filter: brightness(1.08); }
  .chip-btn :global(canvas) { border-radius: 6px; background: rgba(0, 0, 0, 0.18); }
  .who2 { display: grid; flex: 1; min-width: 0; line-height: 1.15; }
  .who2 small { font: 600 8.5px var(--font); letter-spacing: 0.1em; text-transform: uppercase; opacity: 0.8; }
  .chip-btn :global(.icon) { color: #fff; opacity: 0.85; flex: none; }
  .nohead { width: 26px; height: 26px; display: grid; place-items: center; }

  .menu {
    position: absolute; right: 0; top: 50px; width: 320px; padding: 8px; z-index: 30; border-radius: 20px; transform-origin: top right;
    background: var(--glass-strong); border: 1px solid var(--glass-border);
    backdrop-filter: blur(22px) saturate(1.5); -webkit-backdrop-filter: blur(22px) saturate(1.5);
    box-shadow: 0 20px 60px rgba(0, 0, 0, 0.45), inset 0 1px 0 rgba(255, 255, 255, 0.05);
    font-size: 13px;
  }
  .hero { display: flex; align-items: center; gap: 16px; height: 112px; padding: 0 14px; }
  .big { width: 84px; height: 84px; display: grid; place-items: center; flex: none; filter: drop-shadow(0 10px 14px rgba(0, 0, 0, 0.5)); }
  .hname { font: 700 22px var(--display); letter-spacing: -0.02em; }
  .hkind { font-size: 12px; color: var(--muted); margin-top: 2px; }
  .pills { display: flex; gap: 6px; margin-top: 8px; }
  .pill { font: 600 11px var(--font); padding: 3px 8px; border-radius: 999px; border: 0; background: var(--surface-3); color: var(--text); cursor: pointer; }
  .pill:hover { background: #363a41; }
  .sep { height: 1px; background: var(--border); margin: 0 6px 6px; }
  .label { font: 400 10.5px var(--mono); letter-spacing: 0.12em; text-transform: uppercase; color: var(--muted); padding: 4px 10px 6px; }
  .acct { display: flex; align-items: center; border-radius: 12px; }
  .acct.active { background: var(--surface-2); }
  .acct:hover:not(.active) { background: rgba(255, 255, 255, 0.05); }
  .pick { flex: 1; display: flex; align-items: center; gap: 12px; height: 46px; padding: 0 10px; background: none; border: 0; color: var(--text); cursor: pointer; text-align: left; min-width: 0; }
  .pick .grow { display: grid; min-width: 0; }
  .n { font-weight: 600; font-size: 13.5px; }
  .k { font-size: 11.5px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .tick { color: var(--accent); display: grid; }
  .del { width: 28px; height: 28px; display: grid; place-items: center; margin-right: 6px; background: none; border: 0; border-radius: 8px; color: var(--muted); cursor: pointer; opacity: 0; transition: opacity var(--fast), color var(--fast); }
  .acct:hover .del { opacity: 1; }
  .acct.active .del { display: none; }
  .del:hover { color: var(--danger); }
  .action { width: 100%; display: flex; align-items: center; gap: 12px; height: 40px; padding: 0 10px; background: none; border: 0; border-radius: 10px; color: var(--text); font: 600 13px var(--font); cursor: pointer; }
  .action:hover, .action.on { background: var(--surface-2); }
  .ai { width: 28px; height: 28px; border-radius: 8px; display: grid; place-items: center; background: rgba(47, 191, 106, 0.12); color: var(--accent); }
  .offline { display: flex; gap: 8px; padding: 6px 6px 0; }
  .offline .input { flex: 1; height: 36px; background: var(--surface); border-color: rgba(47, 191, 106, 0.45); font-size: 12.5px; border-radius: 10px; }
  .addbtn { height: 36px; padding: 0 16px; border-radius: 10px; border: 0; background: var(--accent); color: var(--accent-ink); font: 600 13.5px var(--font); cursor: pointer; }
  .addbtn:disabled { opacity: 0.5; cursor: default; }
  .err { color: var(--danger); font-size: 12px; padding: 6px 10px 2px; }
</style>
