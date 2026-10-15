<script lang="ts">
  import { fly } from "svelte/transition";
  import { api } from "../lib/api";
  import { activeAccount, app, refreshAccounts } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import AccountModal from "./AccountModal.svelte";
  import Avatar from "./Avatar.svelte";
  import Icon from "./Icon.svelte";

  let open = $state(false);
  const current = $derived(activeAccount());

  async function choose(id: string) {
    await api.setActiveAccount(id);
    await refreshAccounts();
    open = false;
  }

  async function remove(id: string, name: string) {
    await api.removeAccount(id);
    await refreshAccounts();
    toast(`Removed ${name}`);
  }

  function outside(node: HTMLElement) {
    const handler = (e: MouseEvent) => { if (!node.contains(e.target as Node)) open = false; };
    document.addEventListener("mousedown", handler);
    return { destroy: () => document.removeEventListener("mousedown", handler) };
  }
</script>

<div class="wrap" use:outside>
  <button class="chip-btn" class:open onclick={() => (open = !open)} aria-haspopup="menu" aria-expanded={open}>
    <Avatar account={current} size={22} />
    <span class="name">{current?.name ?? "Add account"}</span>
    {#if current}<span class="kind">{current.kind === "msa" ? "Microsoft" : "Offline"}</span>{/if}
    <Icon name="chevron" size={10} />
  </button>
  {#if open}
    <div class="menu card" role="menu" transition:fly={{ y: -6, duration: 160 }}>
      {#each app.accounts as a (a.id)}
        <div class="acct" class:active={a.id === app.activeAccount}>
          <button class="pick" onclick={() => choose(a.id)} role="menuitem">
            <Avatar account={a} size={26} />
            <span class="grow">
              <span class="n">{a.name}</span>
              <span class="k">{a.kind === "msa" ? "Microsoft account" : "Offline account"}</span>
            </span>
            {#if a.id === app.activeAccount}<span class="tick"><Icon name="check" size={12} /></span>{/if}
          </button>
          <button class="btn ghost small icon" onclick={() => remove(a.id, a.name)} aria-label="Remove {a.name}"><Icon name="trash" size={12} /></button>
        </div>
      {/each}
      <button class="add" onclick={() => { open = false; app.accountModal = true; }}>
        <Icon name="plus" size={12} /> Add account
      </button>
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
    color: var(--text); font: 600 12.5px var(--font); cursor: pointer;
    transition: border-color var(--fast), background var(--fast);
  }
  .chip-btn:hover, .chip-btn.open { border-color: #4a504b; background: var(--surface-2); }
  .kind { color: var(--faint); font-weight: 500; font-size: 11px; }
  .chip-btn :global(.icon) { color: var(--muted); }
  .menu { position: absolute; right: 0; top: 42px; width: 270px; padding: 6px; z-index: 30; box-shadow: 0 18px 40px rgba(0, 0, 0, 0.5); }
  .acct { display: flex; align-items: center; gap: 4px; border-radius: 6px; }
  .acct:hover { background: var(--surface-2); }
  .pick { flex: 1; display: flex; align-items: center; gap: 10px; padding: 7px 8px; background: none; border: 0; color: var(--text); cursor: pointer; text-align: left; min-width: 0; }
  .pick .grow { display: grid; }
  .n { font-weight: 600; font-size: 13px; }
  .k { font-size: 11px; color: var(--faint); }
  .tick { color: var(--accent-hover); }
  .add { width: 100%; display: flex; align-items: center; gap: 8px; margin-top: 4px; padding: 9px 10px; background: none; border: 0; border-top: 1px solid var(--border); color: var(--muted); font: 500 12.5px var(--font); cursor: pointer; }
  .add:hover { color: var(--accent-hover); }
</style>
