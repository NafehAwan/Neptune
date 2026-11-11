import type { LaunchProgress } from "./types";

/** Download speed in bytes per second, smoothed so the number doesn't jump around. */
export class Speed {
  private stage = "";
  private lastBytes = 0;
  private lastTime = 0;
  value = 0;

  sample(p: LaunchProgress, now = performance.now()): number {
    if (p.stage !== this.stage || p.bytes < this.lastBytes || p.bytesTotal === 0) {
      this.stage = p.stage;
      this.lastBytes = p.bytes;
      this.lastTime = now;
      this.value = 0;
      return 0;
    }
    const dt = (now - this.lastTime) / 1000;
    if (dt >= 0.25) {
      const rate = (p.bytes - this.lastBytes) / dt;
      this.value = this.value === 0 ? rate : this.value * 0.7 + rate * 0.3;
      this.lastBytes = p.bytes;
      this.lastTime = now;
    }
    return this.value;
  }
}

function mb(n: number): string {
  const v = n / 1024 / 1024;
  return v >= 100 ? v.toFixed(0) : v >= 10 ? v.toFixed(1) : v.toFixed(v >= 1 ? 1 : 2);
}

function size(n: number): string {
  return n >= 1024 * 1024 ? `${mb(n)} MB` : `${Math.max(1, Math.round(n / 1024))} KB`;
}

function duration(secs: number): string {
  if (secs < 60) return `${Math.max(1, Math.round(secs))} s`;
  if (secs < 3600) return `${Math.round(secs / 60)} min`;
  return `${Math.floor(secs / 3600)} h ${Math.round((secs % 3600) / 60)} min`;
}

/** "12.4 of 40.1 MB · 3.2 MB/s · 9 s left", or null when nothing is downloading. */
export function downloaded(p: LaunchProgress, speed: number): string | null {
  if (p.bytesTotal <= 0) return null;
  const done = Math.min(p.bytes, p.bytesTotal);
  const unit = p.bytesTotal >= 1024 * 1024 ? "MB" : "KB";
  const amount = unit === "MB" ? `${mb(done)} of ${mb(p.bytesTotal)} MB` : `${size(done)} of ${size(p.bytesTotal)}`;
  const parts = [amount];
  if (speed > 1024) {
    parts.push(`${size(speed)}/s`);
    const left = (p.bytesTotal - done) / speed;
    if (left >= 2) parts.push(`${duration(left)} left`);
  }
  return parts.join(" · ");
}

/** How far the current launch stage is, 0 to 1, or null when unknown. */
export function launchFraction(p: LaunchProgress): number | null {
  if (p.bytesTotal > 0) return Math.min(1, p.bytes / p.bytesTotal);
  if (p.total > 0) return Math.min(1, p.done / p.total);
  return null;
}

/** How far a new profile is: `done` steps out of `total`, plus the current file. */
export function createFraction(p: LaunchProgress): number {
  if (p.total <= 0) return 0;
  const file = p.bytesTotal > 0 ? Math.min(1, p.bytes / p.bytesTotal) : 0;
  return Math.min(1, (p.done + (p.done < p.total ? file : 0)) / p.total);
}
