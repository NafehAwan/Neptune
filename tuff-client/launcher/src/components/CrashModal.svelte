<script lang="ts">
  // Shown when the game crashes: what went wrong, the mod behind it (with a
  // button to turn it off) and the log lines that show it.
  import { api } from "../lib/api";
  import { loaderName } from "../lib/format";
  import { app, go } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { GameExit } from "../lib/types";
  import Icon from "./Icon.svelte";
  import crashArt from "../assets/glade/crash.jpg";
  import Modal from "./Modal.svelte";

  let { exit, onclose }: { exit: GameExit; onclose: () => void } = $props();
  const crash = $derived(exit.crash!);
  const profile = $derived(app.profiles.find((p) => p.id === exit.profileId));
  let disabled = $state(false);

  async function turnOff() {
    if (!profile || !crash.modFile) return;
    try {
      await api.contentToggle(profile.id, "mod", crash.modFile, false);
      disabled = true;
      toast(`Turned off ${crash.culprit ?? crash.modFile} in ${profile.name}`, "success");
    } catch (e) {
      toast(e, "error");
    }
  }
</script>

<Modal title="Minecraft crashed" {onclose} width={560}>
  <div class="head">
    <img class="art" src={crashArt} alt="" width="76" height="76" />
    <div class="grow">
      <p class="title">{crash.title}</p>
      {#if profile}<p class="where">{profile.name} · {loaderName(profile.loader)} {profile.gameVersion} · exit code {exit.code}</p>{/if}
    </div>
  </div>

  {#if crash.culprit}
    <div class="culprit">
      <span class="label">Cause</span>
      <span class="name">{crash.culprit}</span>
      {#if crash.modFile}<span class="file mono">{crash.modFile}</span>{/if}
    </div>
  {/if}

  <p class="hint">{crash.hint}</p>

  {#if crash.evidence.length}
    <div class="evidence mono selectable">
      {#each crash.evidence as line}<p>{line}</p>{/each}
    </div>
  {/if}

  {#snippet footer()}
    {#if profile}
      <button class="btn ghost" onclick={() => api.openFolder(profile.id, "crash-reports")}><Icon name="folder" size={12} /> Crash reports</button>
    {/if}
    <button class="btn ghost" onclick={() => { onclose(); go("console"); }}>Open console</button>
    {#if crash.modFile && profile}
      <button class="btn primary" onclick={turnOff} disabled={disabled}>
        {#if disabled}<Icon name="check" size={12} /> Turned off{:else}Turn off {crash.culprit}{/if}
      </button>
    {:else}
      <button class="btn primary" onclick={onclose}>OK</button>
    {/if}
  {/snippet}
</Modal>

<style>
  .head { display: flex; gap: 12px; align-items: center; }
  .title { font: 700 21px var(--display); letter-spacing: -0.01em; line-height: 1.2; }
  .where { font-size: 12.5px; color: var(--muted); margin-top: 2px; }
  .culprit { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; padding: 10px 12px; border-radius: 12px; background: var(--surface-2); border: 1px solid var(--glass-border); }
  .label { font-size: 11px; font-weight: 700; letter-spacing: 0.08em; text-transform: uppercase; color: var(--muted); }
  .name { font-weight: 650; }
  .file { font-size: 12px; color: var(--muted); margin-left: auto; }
  .hint { font-size: 14.5px; line-height: 1.5; }
  .evidence { max-height: 150px; overflow: auto; padding: 10px 12px; border-radius: 10px; background: rgba(255, 255, 255, 0.05); font-size: 12px; line-height: 1.5; }
  .evidence p { white-space: pre-wrap; word-break: break-word; }
  .art { border-radius: 14px; flex: none; object-fit: cover; }
</style>
