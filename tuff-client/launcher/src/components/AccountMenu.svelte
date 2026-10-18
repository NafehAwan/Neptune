<script lang="ts">
  import { fly } from "svelte/transition";
  import { api } from "../lib/api";
  import { skinOf } from "../lib/skins.svelte";
  import { activeAccount, app, refreshAccounts } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import AccountModal from "./AccountModal.svelte";
  import Head3D from "./Head3D.svelte";
  import Icon from "./Icon.svelte";

  let open = $state(false);
  let adding = $state(false);
  let name = $state("");
  let error = $state("");
  const current = $derived(activeAccount());

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
      <Head3D skin={skinOf(current)} size={24} />
    {:else}
      <span class="nohead"><Icon name="user" size={14} /></span>
    {/if}
    <span class="name">{current?.name ?? "Add account"}</span>
    {#if current}<span class="kind">{current.kind === "msa" ? "Microsoft" : "Offline"}</span>{/if}
    <Icon name="chevron" size={10} />
  </button>

  {#if open}
    <div class="menu card" role="menu" transition:fly={{ y: -6, duration: 160 }}>
      {#if current}
        <div class="hero">
          <div class="big"><Head3D skin={skinOf(current)} size={84} spin yaw={-30} pitch={-16} /></div>
          <div class="who">
            <p class="hname">{current.name}</p>
            <p class="hkind">{current.kind === "msa" ? "Microsoft account · skin from Minecraft" : "Offline account · default skin"}</p>
          </div>
        </div>
        <div class="sep"></div>
      {/if}

      <p class="label">Accounts</p>
      {#each app.accounts as a (a.id)}
        <div class="acct" class:active={a.id === app.activeAccount}>
          <button class="pick" onclick={() => choose(a.id)} role="menuitem">
            <Head3D skin={skinOf(a)} size={30} />
            <span class="grow">
              <span class="n">{a.name}</span>
              <span class="k">{a.kind === "msa" ? "Microsoft" : "Offline · singleplayer and offline servers"}</span>
            </span>
            {#if a.id === app.activeAccount}<span class="tick"><Icon name="check" size={13} /></span>{/if}
          </button>
          <button class="del" onclick={() => remove(a.id, a.name)} aria-label="Remove {a.name}" title="Remove"><Icon name="trash" size={12} /></button>
        </div>
      {/each}

      <div class="sep"></div>
      <button class="action" onclick={() => { open = false; app.accountModal = true; }}>
        <span class="ai"><Icon name="plus" size={13} /></span> Add Microsoft account
      </button>
      <button class="action" class:on={adding} onclick={() => { adding = !adding; error = ""; }}>
        <span class="ai"><Icon name="user" size={13} /></span> Add offline account
      </button>
      {#if adding}
        <form class="offline" onsubmit={addOffline} transition:fly={{ y: -4, duration: 140 }}>
          <input class="input" bind:value={name} maxlength="16" placeholder="Username" use:focusNow />
          <button class="btn primary small" disabled={!/^[A-Za-z0-9_]{3,16}$/.test(name.trim())}>Add</button>
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
  .wrap { position: relative; }
  .chip-btn {
    display: flex; align-items: center; gap: 9px; height: 34px; padding: 0 10px 0 6px;
    background: rgba(30, 33, 31, 0.75); backdrop-filter: blur(8px);
    border: 1px solid var(--border-strong); border-radius: 8px;
    color: var(--text); font: 600 14.5px var(--font); cursor: pointer;
    transition: border-color var(--fast), background var(--fast);
  }
  .chip-btn:hover, .chip-btn.open { border-color: #4a504b; background: var(--surface-2); }
  .kind { color: var(--faint); font-weight: 500; font-size: 12.5px; }
  .chip-btn :global(.icon) { color: var(--muted); }
  .nohead { width: 24px; height: 24px; display: grid; place-items: center; }

  .menu { position: absolute; right: 0; top: 42px; width: 300px; padding: 6px; z-index: 30; box-shadow: 0 18px 40px rgba(0, 0, 0, 0.5); }
  .hero { display: flex; align-items: center; gap: 12px; padding: 6px 8px 8px; }
  .big { width: 84px; height: 84px; display: grid; place-items: center; flex: none; filter: drop-shadow(0 8px 12px rgba(0, 0, 0, 0.45)); }
  .hname { font-weight: 700; font-size: 18px; }
  .hkind { font-size: 12.5px; color: var(--faint); margin-top: 2px; }
  .sep { height: 1px; background: var(--border); margin: 6px 4px; }
  .label { font-size: 11.5px; font-weight: 600; letter-spacing: 0.1em; text-transform: uppercase; color: var(--faint); padding: 4px 8px 2px; }
  .acct { display: flex; align-items: center; gap: 4px; border-radius: 6px; }
  .acct:hover, .acct.active { background: var(--surface-2); }
  .pick { flex: 1; display: flex; align-items: center; gap: 10px; padding: 7px 8px; background: none; border: 0; color: var(--text); cursor: pointer; text-align: left; min-width: 0; }
  .pick .grow { display: grid; min-width: 0; }
  .n { font-weight: 600; font-size: 15px; }
  .k { font-size: 12.5px; color: var(--faint); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .tick { color: var(--accent-hover); }
  .del { width: 28px; height: 28px; display: grid; place-items: center; margin-right: 4px; background: none; border: 0; border-radius: 6px; color: var(--faint); cursor: pointer; opacity: 0; transition: opacity var(--fast), color var(--fast); }
  .acct:hover .del { opacity: 1; }
  .del:hover { color: var(--danger); }
  .action { width: 100%; display: flex; align-items: center; gap: 10px; padding: 8px 10px; background: none; border: 0; border-radius: 6px; color: var(--muted); font: 500 14.5px var(--font); cursor: pointer; }
  .action:hover, .action.on { color: var(--accent-hover); background: var(--surface-2); }
  .ai { display: grid; place-items: center; }
  .offline { display: flex; gap: 8px; padding: 6px 6px 4px; }
  .offline .input { height: 34px; }
  .offline .btn { height: 34px; }
  .err { color: var(--danger); font-size: 12.5px; padding: 2px 10px 4px; }
</style>
