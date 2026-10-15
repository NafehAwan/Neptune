<script lang="ts">
  import { api } from "../lib/api";
  import { app, go, refreshAccounts } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { DeviceCode } from "../lib/types";
  import Icon from "./Icon.svelte";
  import Modal from "./Modal.svelte";

  let { onclose }: { onclose: () => void } = $props();
  let tab = $state<"msa" | "offline">("msa");
  let code = $state<DeviceCode | null>(null);
  let busy = $state(false);
  let name = $state("");
  let error = $state("");

  async function microsoft() {
    busy = true;
    error = "";
    try {
      code = await api.msLoginStart();
      const account = await api.msLoginFinish(code);
      await refreshAccounts();
      toast(`Signed in as ${account.name}`, "success");
      onclose();
    } catch (e) {
      error = String(e);
      code = null;
    } finally {
      busy = false;
    }
  }

  async function offline(e: Event) {
    e.preventDefault();
    error = "";
    try {
      const account = await api.addOfflineAccount(name);
      await refreshAccounts();
      toast(`Added ${account.name}`, "success");
      onclose();
    } catch (e) {
      error = String(e);
    }
  }

  async function copy() {
    if (code) await navigator.clipboard.writeText(code.user_code);
    toast("Code copied");
  }
</script>

<Modal title="Add account" {onclose} width={440}>
  <div class="seg">
    <button class:on={tab === "msa"} onclick={() => { tab = "msa"; error = ""; }}>Microsoft</button>
    <button class:on={tab === "offline"} onclick={() => { tab = "offline"; error = ""; }}>Offline</button>
  </div>

  {#if tab === "msa"}
    {#if code}
      <p class="muted">Enter this code on the Microsoft page that just opened in your browser.</p>
      <button class="code" onclick={copy} title="Copy code">
        <span class="pixel">{code.user_code}</span>
        <Icon name="copy" size={14} />
      </button>
      <div class="row muted waiting"><span class="spinner"></span> Waiting for you to sign in…</div>
      <button class="btn ghost small" onclick={() => code && api.openUrl(code.verification_uri)}>
        <Icon name="link" size={12} /> Open {code.verification_uri.replace("https://", "")}
      </button>
    {:else if app.info && !app.info.msLoginConfigured}
      <p class="muted">Microsoft sign-in needs an Azure app client ID. Add yours in Settings → Accounts, or use an offline account for singleplayer.</p>
      <button class="btn" onclick={() => { onclose(); go("settings"); }}>Open settings</button>
    {:else}
      <p class="muted">You'll sign in on Microsoft's own page. Tuff Client never sees your password.</p>
      <button class="btn primary big" onclick={microsoft} disabled={busy}>
        {#if busy}<span class="spinner"></span>{/if} Sign in with Microsoft
      </button>
    {/if}
  {:else}
    <form onsubmit={offline}>
      <label class="field">
        <span>Username</span>
        <!-- svelte-ignore a11y_autofocus -->
        <input class="input" bind:value={name} maxlength="16" placeholder="Steve" autofocus />
      </label>
      <p class="faint small">Offline accounts work in singleplayer and on offline-mode servers.</p>
      <button class="btn primary" type="submit" disabled={name.trim().length < 3}>Add offline account</button>
    </form>
  {/if}

  {#if error}<p class="error selectable">{error}</p>{/if}
</Modal>

<style>
  .seg { width: 100%; }
  .seg button { flex: 1; }
  .code {
    display: flex; align-items: center; justify-content: center; gap: 14px;
    padding: 18px; background: var(--bg); border: 1px dashed var(--accent-deep); border-radius: 8px;
    color: var(--accent-hover); cursor: pointer;
  }
  .code .pixel { font-size: 28px; font-weight: 700; letter-spacing: 0.14em; font-variant-numeric: tabular-nums; }
  .waiting { font-size: 15px; }
  .big { height: 42px; }
  form { display: grid; gap: 12px; }
  .small { font-size: 14px; }
  .error { color: #f0a49b; font-size: 14.5px; background: rgba(217, 88, 75, 0.1); border: 1px solid rgba(217, 88, 75, 0.35); padding: 9px 11px; border-radius: 6px; }
</style>
