<script lang="ts">
  import { onMount } from "svelte";
  import Icon from "../components/Icon.svelte";
  import { api, pickFile } from "../lib/api";
  import { activeAccount, app } from "../lib/state.svelte";
  import { toast } from "../lib/toast.svelte";
  import type { SkinProfile } from "../lib/types";

  type Viewer = import("skinview3d").SkinViewer;
  type Anim = "idle" | "walk" | "run" | "wave" | "crouch";

  let canvas: HTMLCanvasElement;
  let viewer: Viewer | null = null;
  let skinview: typeof import("skinview3d") | null = null;
  let profile = $state<SkinProfile | null>(null);
  let profileError = $state("");
  let anim = $state<Anim>("idle");
  let pendingPath = $state<string | null>(null);
  let slim = $state(false);
  let busy = $state(false);
  let rotate = $state(true);

  const account = $derived(activeAccount());
  const activeSkin = $derived(profile?.skins.find((s) => s.state === "ACTIVE") ?? profile?.skins[0] ?? null);
  const activeCape = $derived(profile?.capes.find((c) => c.state === "ACTIVE") ?? null);

  /** An original placeholder skin: a tuff-grey mannequin with copper trim. */
  function mannequin(): string {
    const c = document.createElement("canvas");
    c.width = 64; c.height = 64;
    const g = c.getContext("2d")!;
    const fill = (col: string, x: number, y: number, w: number, h: number) => { g.fillStyle = col; g.fillRect(x, y, w, h); };
    fill("#8b8f88", 0, 0, 32, 16);
    fill("#9a9e96", 8, 8, 8, 8);
    fill("#d9824b", 9, 12, 2, 1); fill("#d9824b", 13, 12, 2, 1);
    fill("#3a3f3b", 16, 16, 24, 16); fill("#d9824b", 16, 26, 24, 1);
    fill("#6d7066", 40, 16, 16, 16); fill("#6d7066", 32, 48, 16, 16);
    fill("#2c302d", 0, 16, 16, 16); fill("#2c302d", 16, 48, 16, 16);
    return c.toDataURL();
  }

  function applyAnimation() {
    if (!viewer || !skinview) return;
    const s = skinview;
    viewer.animation = anim === "walk" ? new s.WalkingAnimation()
      : anim === "run" ? new s.RunningAnimation()
      : anim === "wave" ? new s.WaveAnimation()
      : anim === "crouch" ? new s.CrouchAnimation()
      : new s.IdleAnimation();
  }

  onMount(() => {
    let disposed = false;
    (async () => {
      skinview = await import("skinview3d");
      if (disposed) return;
      viewer = new skinview.SkinViewer({ canvas, width: 300, height: 420, skin: mannequin() });
      viewer.zoom = 0.85;
      viewer.fov = 40;
      viewer.autoRotate = rotate;
      viewer.autoRotateSpeed = 0.6;
      viewer.globalLight.intensity = 2.4;
      viewer.cameraLight.intensity = 0.8;
      applyAnimation();
      await loadProfile();
    })();
    return () => { disposed = true; viewer?.dispose(); };
  });

  $effect(() => { anim; applyAnimation(); });
  $effect(() => { if (viewer) viewer.autoRotate = rotate; });

  async function loadProfile() {
    profileError = "";
    if (account?.kind !== "msa") { profile = null; return; }
    try {
      profile = await api.skinProfile();
      showCurrent();
    } catch (e) {
      profileError = String(e);
    }
  }

  function showCurrent() {
    if (!viewer) return;
    if (activeSkin?.dataUrl) {
      slim = activeSkin.variant === "SLIM";
      viewer.loadSkin(activeSkin.dataUrl, { model: slim ? "slim" : "default" });
    }
    if (activeCape?.dataUrl) viewer.loadCape(activeCape.dataUrl); else viewer.resetCape();
  }

  async function choose() {
    const path = await pickFile("Skin", ["png"]);
    if (!path || !viewer) return;
    try {
      const data = await api.readSkinFile(path);
      pendingPath = path;
      viewer.loadSkin(data, { model: slim ? "slim" : "default" });
    } catch (e) {
      toast(e, "error");
    }
  }

  $effect(() => {
    // Re-apply the arm model when it changes while previewing.
    const model = slim ? "slim" : "default";
    if (viewer && pendingPath) api.readSkinFile(pendingPath).then((d) => viewer?.loadSkin(d, { model }));
  });

  async function apply() {
    if (!pendingPath) return;
    busy = true;
    try {
      await api.uploadSkin(pendingPath, slim);
      pendingPath = null;
      toast("Skin updated. It can take a minute to show up in-game.", "success");
      await loadProfile();
    } catch (e) {
      toast(e, "error");
    } finally {
      busy = false;
    }
  }

  async function reset() {
    busy = true;
    try { await api.resetSkin(); await loadProfile(); toast("Skin reset", "success"); }
    catch (e) { toast(e, "error"); }
    finally { busy = false; }
  }

  async function cape(id: string | null) {
    busy = true;
    try { await api.setCape(id); await loadProfile(); }
    catch (e) { toast(e, "error"); }
    finally { busy = false; }
  }
</script>

<div class="page">
  <div class="page-head">
    <div>
      <h1>Wardrobe</h1>
      <p class="muted">Preview and change your skin and cape. Changes apply to your Minecraft account everywhere.</p>
    </div>
  </div>

  <div class="layout">
    <section class="stage card">
      <canvas bind:this={canvas}></canvas>
      <div class="anims seg">
        {#each ["idle", "walk", "run", "wave", "crouch"] as a}
          <button class:on={anim === a} onclick={() => (anim = a as Anim)}>{a[0].toUpperCase() + a.slice(1)}</button>
        {/each}
      </div>
      <label class="rot"><input type="checkbox" bind:checked={rotate} /> Rotate</label>
    </section>

    <section class="side">
      {#if account?.kind !== "msa"}
        <div class="card box">
          <h3>Preview only</h3>
          <p class="muted">Sign in with a Microsoft account to change your skin and cape. You can still preview skin files here.</p>
          <div class="row"><button class="btn" onclick={choose}><Icon name="folder" size={12} /> Preview a skin</button>
            <button class="btn ghost" onclick={() => (app.accountModal = true)}>Add account</button></div>
        </div>
      {:else if profileError}
        <div class="card box"><h3>Couldn't load your profile</h3><p class="muted selectable">{profileError}</p><button class="btn" onclick={loadProfile}>Retry</button></div>
      {/if}

      {#if account?.kind === "msa" && !profileError}
        <div class="card box">
          <h3>Skin</h3>
          <div class="row">
            <button class="btn" onclick={choose} disabled={busy}><Icon name="folder" size={12} /> Choose PNG</button>
            <button class="btn ghost" onclick={reset} disabled={busy}>Reset to default</button>
          </div>
          <div class="field">
            <span class="lab">Arms</span>
            <div class="seg"><button class:on={!slim} onclick={() => (slim = false)}>Classic</button><button class:on={slim} onclick={() => (slim = true)}>Slim</button></div>
          </div>
          {#if pendingPath}
            <div class="pending">
              <span class="ellipsis grow">Previewing {pendingPath.split(/[\\/]/).pop()}</span>
              <button class="btn ghost small" onclick={() => { pendingPath = null; showCurrent(); }}>Cancel</button>
              <button class="btn primary small" onclick={apply} disabled={busy}>Apply</button>
            </div>
          {/if}
        </div>

        <div class="card box">
          <h3>Cape</h3>
          {#if !profile}
            <div class="skeleton" style="height:70px"></div>
          {:else if !profile.capes.length}
            <p class="muted">This account has no capes yet.</p>
          {:else}
            <div class="capes">
              <button class="cape" class:on={!activeCape} onclick={() => cape(null)} disabled={busy}><span class="none">None</span></button>
              {#each profile.capes as c (c.id)}
                <button class="cape" class:on={c.id === activeCape?.id} onclick={() => cape(c.id)} disabled={busy} title={c.alias ?? "Cape"}>
                  {#if c.dataUrl}<span class="cape-art" style:background-image="url({c.dataUrl})"></span>{/if}
                  <span class="cname ellipsis">{c.alias ?? "Cape"}</span>
                </button>
              {/each}
            </div>
          {/if}
        </div>
      {/if}
    </section>
  </div>
</div>

<style>
  .layout { display: grid; grid-template-columns: 340px 1fr; gap: 18px; align-items: start; }
  .stage {
    position: relative; display: grid; justify-items: center; padding: 18px 0 16px; overflow: hidden;
    background: radial-gradient(70% 60% at 50% 40%, rgba(217, 130, 75, 0.14), transparent 70%), var(--surface);
  }
  canvas { width: 300px; height: 420px; cursor: grab; }
  canvas:active { cursor: grabbing; }
  .anims { margin-top: 6px; }
  .anims button { padding: 0 9px; }
  .rot { position: absolute; top: 12px; right: 14px; display: flex; gap: 6px; align-items: center; font-size: 12px; color: var(--muted); }
  .rot input { accent-color: var(--accent); }
  .side { display: grid; gap: 14px; }
  .box { padding: 18px; display: grid; gap: 12px; }
  .lab { font-size: 12px; color: var(--muted); font-weight: 500; }
  .field { display: grid; gap: 6px; justify-items: start; }
  .pending { display: flex; align-items: center; gap: 8px; padding: 8px 10px; border-radius: 7px; background: rgba(217, 130, 75, 0.08); border: 1px solid rgba(217, 130, 75, 0.3); font-size: 12.5px; }
  .capes { display: grid; grid-template-columns: repeat(auto-fill, minmax(92px, 1fr)); gap: 8px; }
  .cape { display: grid; justify-items: center; gap: 6px; padding: 10px 6px 8px; background: var(--bg); border: 2px solid var(--border); border-radius: 8px; color: var(--text); cursor: pointer; transition: border-color var(--fast); }
  .cape:hover { border-color: var(--border-strong); }
  .cape.on { border-color: var(--accent); }
  .cape-art { width: 40px; height: 64px; image-rendering: pixelated; background-repeat: no-repeat; background-size: 640px 320px; background-position: -4px -4px; }
  .none { height: 64px; display: grid; place-items: center; color: var(--faint); font-size: 12px; }
  .cname { font-size: 11px; color: var(--muted); max-width: 100%; }
</style>
