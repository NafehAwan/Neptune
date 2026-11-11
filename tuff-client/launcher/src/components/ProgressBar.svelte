<script lang="ts">
  /** 0 to 1, or null for "working, amount unknown" (a sliding stripe). */
  let { value }: { value: number | null } = $props();
</script>

<div class="bar" class:unknown={value === null} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={value === null ? undefined : Math.round(value * 100)}>
  <span style:width={value === null ? undefined : `${Math.max(2, value * 100)}%`}></span>
</div>

<style>
  .bar { position: relative; height: 6px; border-radius: 999px; background: rgba(47, 191, 106, 0.14); overflow: hidden; }
  .bar span {
    position: absolute; left: 0; top: 0; bottom: 0; border-radius: inherit;
    background: linear-gradient(90deg, var(--accent), var(--accent-hover));
    transition: width 220ms linear;
  }
  .bar.unknown span { width: 30%; animation: slide 1.1s var(--ease, ease-in-out) infinite; }
  @keyframes slide { from { left: -30%; } to { left: 100%; } }
</style>
