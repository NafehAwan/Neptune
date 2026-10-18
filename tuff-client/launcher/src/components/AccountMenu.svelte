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
    {#if current}<span class="state" class:offline={current.kind !== "msa"}></span>{/if}
    <Icon name="chevron" size={10} />
  </button>

  {#if open}
    <div class="menu" role="menu" transition:fly={{ y: -6, duration: 160 }}>
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
    display: flex; align-items: center; gap: 9px; height: 38px; padding: 0 12px 0 6px;
    background: var(--glass); backdrop-filter: var(--glass-blur); -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border); border-radius: 999px; box-shadow: var(--glass-shadow);
    color: var(--text); font: 600 14.5px var(--font); cursor: pointer;
    transition: background var(--fast);
  }
  .chip-btn:hover, .chip-btn.open { background: var(--glass-strong); }
  .chip-btn :global(.icon) { color: var(--muted); }
  .nohead { width: 26px; height: 26px; display: grid; place-items: center; }
  .state { width: 7px; height: 7px; border-radius: 50%; background: var(--online); }
  .state.offline { background: #C9A66B; }

  .menu {
    position: absolute; right: 0; top: 46px; width: 330px; padding: 8px; z-index: 30;
    background: rgba(250, 251, 247, 0.94); backdrop-filter: var(--glass-blur); -webkit-backdrop-filter: var(--glass-blur);
    border: 1px solid var(--glass-border); border-radius: 20px;
    box-shadow: 0 24px 60px rgba(30, 60, 40, 0.2), inset 0 1px 0 rgba(255, 255, 255, 0.85);
  }
  .hero { display: flex; align-items: center; gap: 14px; padding: 6px 10px 8px; }
  .big { width: 96px; height: 96px; display: grid; place-items: center; flex: none; filter: drop-shadow(0 10px 14px rgba(30, 60, 40, 0.22)); }
  .hname { font: italic 600 24px var(--display); letter-spacing: -0.01em; }
  .hkind { font-size: 12.5px; color: var(--muted); margin-top: 2px; }
  .sep { height: 1px; background: rgba(255, 255, 255, 0.8); margin: 6px 6px; box-shadow: 0 1px 0 rgba(31, 42, 34, 0.05); }
  .label { font: 600 11px var(--mono); letter-spacing: 0.12em; text-transform: uppercase; color: var(--faint); padding: 6px 10px 4px; }
  .acct { display: flex; align-items: center; gap: 2px; border-radius: 12px; }
  .acct:hover, .acct.active { background: rgba(47, 107, 79, 0.07); }
  .pick { flex: 1; display: flex; align-items: center; gap: 11px; padding: 7px 8px; background: none; border: 0; color: var(--text); cursor: pointer; text-align: left; min-width: 0; }
  .pick .grow { display: grid; min-width: 0; }
  .n { font-weight: 700; font-size: 14.5px; }
  .k { font-size: 12px; color: var(--muted); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
  .tick { color: var(--accent); }
  .del { width: 28px; height: 28px; display: grid; place-items: center; margin-right: 6px; background: none; border: 0; border-radius: 8px; color: var(--faint); cursor: pointer; opacity: 0; transition: opacity var(--fast), color var(--fast), background var(--fast); }
  .acct:hover .del { opacity: 1; }
  .del:hover { color: var(--danger); background: rgba(194, 70, 59, 0.08); }
  .action { width: 100%; display: flex; align-items: center; gap: 11px; padding: 7px 8px; background: none; border: 0; border-radius: 12px; color: var(--text); font: 600 14px var(--font); cursor: pointer; }
  .action:hover, .action.on { background: rgba(47, 107, 79, 0.07); }
  .ai { width: 30px; height: 30px; border-radius: 9px; display: grid; place-items: center; background: rgba(47, 107, 79, 0.12); color: var(--accent); }
  .offline { display: flex; gap: 8px; padding: 6px 6px 4px; }
  .offline .input { height: 36px; }
  .offline .btn { height: 36px; }
  .err { color: var(--danger); font-size: 12.5px; padding: 2px 10px 4px; }
</style>
