<script lang="ts">
  import { backOut } from "svelte/easing";
  import { fly, scale } from "svelte/transition";
  import { api } from "../lib/api";
  import { skinOf } from "../lib/skins.svelte";
  import { activeAccount, app, go, refreshAccounts } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import AccountModal from "./AccountModal.svelte";
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
  <button class="chip-btn" class:open onclick={() => (open = !open)} aria-haspopup="menu" aria-expanded={open}>
    {#if current}
      <Head3D skin={skinOf(current)} size={26} />
    {:else}
      <span class="nohead"><Icon name="user" size={14} /></span>
    {/if}
    <span class="name">{current?.name ?? "Add account"}</span>
    {#if current}<span class="kind">{current.kind === "msa" ? "Microsoft" : "Offline"}</span>{/if}
    <Icon name="chevron" size={16} />
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
      <button class="action" onclick={() => { open = false; app.accountModal = true; }}>
        <span class="ai"><Icon name="plus" size={16} /></span> Add Microsoft account
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
    display: flex; align-items: center; gap: 10px; height: 34px; padding: 0 14px 0 4px;
    background: rgba(255, 255, 255, 0.38); border: 1px solid rgba(255, 255, 255, 0.7); border-radius: 999px;
    backdrop-filter: blur(20px) saturate(1.5); -webkit-backdrop-filter: blur(20px) saturate(1.5);
    box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.8), 0 10px 30px rgba(30, 60, 40, 0.12);
    color: var(--text); font: 600 13px var(--font); cursor: pointer;
    transition: background var(--fast);
  }
  .chip-btn:hover, .chip-btn.open { background: rgba(255, 255, 255, 0.62); }
  .kind { font: 400 11px var(--mono); color: var(--muted); margin-left: 4px; }
  .chip-btn :global(.icon) { color: var(--text); }
  .nohead { width: 26px; height: 26px; display: grid; place-items: center; }

  .menu {
    position: absolute; right: 0; top: 46px; width: 320px; padding: 8px; z-index: 30; border-radius: 20px; transform-origin: top right;
    background: rgba(255, 255, 255, 0.62); border: 1px solid rgba(255, 255, 255, 0.7);
    backdrop-filter: blur(22px) saturate(1.5); -webkit-backdrop-filter: blur(22px) saturate(1.5);
    box-shadow: 0 20px 60px rgba(30, 60, 40, 0.18), inset 0 1px 0 rgba(255, 255, 255, 0.8);
    font-size: 13px;
  }
  .hero { display: flex; align-items: center; gap: 16px; height: 112px; padding: 0 14px; }
  .big { width: 84px; height: 84px; display: grid; place-items: center; flex: none; filter: drop-shadow(0 10px 14px rgba(30, 60, 40, 0.25)); }
  .hname { font: italic 600 22px var(--display); letter-spacing: -0.02em; }
  .hkind { font-size: 12px; color: var(--muted); margin-top: 2px; }
  .pills { display: flex; gap: 6px; margin-top: 8px; }
  .pill { font: 600 11px var(--font); padding: 3px 8px; border-radius: 999px; border: 0; background: rgba(255, 255, 255, 0.6); color: var(--text); cursor: pointer; }
  .pill:hover { background: rgba(255, 255, 255, 0.9); }
  .sep { height: 1px; background: rgba(255, 255, 255, 0.7); margin: 0 6px 6px; }
  .label { font: 400 10.5px var(--mono); letter-spacing: 0.12em; text-transform: uppercase; color: var(--muted); padding: 4px 10px 6px; }
  .acct { display: flex; align-items: center; border-radius: 12px; }
  .acct.active { background: rgba(255, 255, 255, 0.5); }
  .acct:hover:not(.active) { background: rgba(255, 255, 255, 0.3); }
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
  .action:hover, .action.on { background: rgba(255, 255, 255, 0.45); }
  .ai { width: 28px; height: 28px; border-radius: 8px; display: grid; place-items: center; background: rgba(47, 107, 79, 0.12); color: var(--accent); }
  .offline { display: flex; gap: 8px; padding: 6px 6px 0; }
  .offline .input { flex: 1; height: 36px; background: rgba(255, 255, 255, 0.65); border-color: rgba(47, 107, 79, 0.45); font-size: 12.5px; border-radius: 10px; }
  .addbtn { height: 36px; padding: 0 16px; border-radius: 10px; border: 0; background: var(--accent); color: #fff; font: 600 13.5px var(--font); cursor: pointer; }
  .addbtn:disabled { opacity: 0.5; cursor: default; }
  .err { color: var(--danger); font-size: 12px; padding: 6px 10px 2px; }
</style>
