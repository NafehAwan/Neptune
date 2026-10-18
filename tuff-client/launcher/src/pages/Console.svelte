<script lang="ts">
  import { tick } from "svelte";
  import Icon from "../components/Icon.svelte";
  import { api } from "../lib/api";
  import { app, go } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";

  let level = $state<"all" | "warn" | "error">("all");
  let query = $state("");
  let follow = $state(true);
  let box: HTMLDivElement;

  const lines = $derived(
    app.logs.filter((l) =>
      (level === "all" || (level === "warn" ? l.level !== "info" : l.level === "error")) &&
      (!query || l.line.toLowerCase().includes(query.toLowerCase())),
    ),
  );
  const runningProfile = $derived(app.profiles.find((p) => p.id === app.running));
  const exitProfile = $derived(app.profiles.find((p) => p.id === app.lastExit?.profileId));

  $effect(() => {
    lines.length;
    if (follow) tick().then(() => box && (box.scrollTop = box.scrollHeight));
  });

  function scrolled() {
    follow = box.scrollHeight - box.scrollTop - box.clientHeight < 40;
  }

  async function copy() {
    await navigator.clipboard.writeText(lines.map((l) => l.line).join("\n"));
    toast(`Copied ${lines.length} lines`);
  }
</script>

<div class="page console">
  <div class="page-head">
    <div>
      <h1>Console</h1>
      <p class="muted">
        {#if runningProfile}<span class="dot on inline"></span> {runningProfile.name} is running
        {:else if app.lastExit}Last session ended with code {app.lastExit.code}
        {:else}Game output appears here while you play.{/if}
      </p>
    </div>
    {#if app.running}
      <button class="btn danger" onclick={() => api.killGame()}><Icon name="stop" size={12} /> Stop game</button>
    {/if}
  </div>

  {#if app.lastExit?.crash && !app.running}
    <div class="crash">
      <div class="grow">
        <p class="ct">{app.lastExit.crash.title}</p>
        <p class="muted">{app.lastExit.crash.hint}</p>
      </div>
      {#if exitProfile}
        <button class="btn small" onclick={() => api.openFolder(exitProfile.id, "crash-reports")}><Icon name="folder" size={12} /> Crash reports</button>
        <button class="btn small" onclick={() => { app.focusProfile = exitProfile.id; go("library"); }}>Open profile</button>
      {/if}
    </div>
  {/if}

  <div class="toolbar">
    <div class="seg">
      <button class:on={level === "all"} onclick={() => (level = "all")}>All</button>
      <button class:on={level === "warn"} onclick={() => (level = "warn")}>Warnings</button>
      <button class:on={level === "error"} onclick={() => (level = "error")}>Errors</button>
    </div>
    <input class="input search" placeholder="Search log" bind:value={query} />
    <div class="grow"></div>
    <span class="faint count">{lines.length} lines</span>
    <button class="btn small" onclick={copy} disabled={!lines.length}><Icon name="copy" size={12} /> Copy</button>
    <button class="btn small ghost" onclick={() => (app.logs = [])} disabled={!app.logs.length}>Clear</button>
  </div>

  <div class="log selectable" bind:this={box} onscroll={scrolled}>
    {#if !lines.length}
      <p class="faint placeholder">{app.logs.length ? "No lines match." : "Nothing logged yet. Start a profile to see its output."}</p>
    {/if}
    {#each lines as l, i (i)}
      <div class="line {l.level}">{l.line}</div>
    {/each}
  </div>
  {#if !follow && lines.length}
    <button class="btn small jump" onclick={() => { follow = true; box.scrollTop = box.scrollHeight; }}>Jump to latest</button>
  {/if}
</div>

<style>
  .console { display: flex; flex-direction: column; padding-bottom: 24px; }
  .inline { display: inline-block; margin-right: 4px; }
  .crash { display: flex; gap: 12px; align-items: center; padding: 12px 14px; margin-bottom: 14px; border-radius: 9px; background: rgba(217, 88, 75, 0.08); border: 1px solid rgba(217, 88, 75, 0.35); font-size: 14.5px; }
  .ct { font-weight: 600; color: #f0a49b; font-size: 15.5px; }
  .toolbar { display: flex; gap: 10px; align-items: center; margin-bottom: 10px; }
  .search { width: 220px; height: 32px; }
  .count { font-size: 14px; }
  .log {
    flex: 1; min-height: 200px; overflow: auto; padding: 12px 14px;
    background: #0e100e; border: 1px solid var(--border); border-radius: 9px;
    font: 13.5px/1.6 var(--mono); color: #c9c6be;
  }
  .line { white-space: pre-wrap; word-break: break-all; content-visibility: auto; contain-intrinsic-size: auto 19px; }
  .line.warn { color: var(--warn); }
  .line.error { color: #ef8f86; }
  .placeholder { font-family: var(--font); }
  .jump { position: absolute; right: 48px; bottom: 40px; }
</style>
