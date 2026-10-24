<script lang="ts">
  import { api } from "../lib/api";
  import { app, refreshAccounts, signInMicrosoft } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import Modal from "./Modal.svelte";

  let { onclose }: { onclose: () => void } = $props();
  let tab = $state<"msa" | "offline">("msa");
  let name = $state("");
  let error = $state("");

  async function microsoft() {
    error = "";
    try {
      await signInMicrosoft();
      onclose();
    } catch (e) {
      error = String(e);
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
</script>

<Modal title="Add account" {onclose} width={440}>
  <div class="seg">
    <button class:on={tab === "msa"} onclick={() => { tab = "msa"; error = ""; }}>Microsoft</button>
    <button class:on={tab === "offline"} onclick={() => { tab = "offline"; error = ""; }}>Offline</button>
  </div>

  {#if tab === "msa"}
    <p class="muted">{app.signingIn ? "Sign in in the Microsoft window. It closes by itself when you're done." : "Microsoft's own sign-in page opens in a window. No code to type, and Glade never sees your password."}</p>
    <button class="btn primary big" onclick={microsoft} disabled={app.signingIn}>
      {#if app.signingIn}<span class="spinner"></span> Waiting for Microsoft…{:else}Sign in with Microsoft{/if}
    </button>
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
  .big { height: 42px; }
  form { display: grid; gap: 12px; }
  .small { font-size: 14px; }
  .error { color: var(--danger); font-size: 14.5px; background: rgba(194, 70, 59, 0.1); border: 1px solid rgba(194, 70, 59, 0.35); padding: 9px 11px; border-radius: 6px; }
</style>
