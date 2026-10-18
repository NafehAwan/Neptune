export type Loader = "vanilla" | "fabric" | "quilt" | "forge" | "neoforge";
export type OnLaunch = "keep" | "minimize" | "hide";

export interface Profile {
  id: string;
  name: string;
  icon: string;
  gameVersion: string;
  loader: Loader;
  loaderVersion: string | null;
  memoryMb: number;
  jvmArgs: string;
  javaPath: string | null;
  resolution: { width: number; height: number } | null;
  created: number;
  lastPlayed: number | null;
  playTimeSecs: number;
  isTuff: boolean;
  preset?: string | null;
}

export interface Settings {
  defaultMemoryMb: number;
  onLaunch: OnLaunch;
  lowMotion: boolean;
  discordRpc: boolean;
  showSnapshots: boolean;
  javaPath: string | null;
  concurrentDownloads: number;
  msClientId: string | null;
  discordAppId: string | null;
  activeAccount: string | null;
  selectedProfile: string | null;
}

export interface AppInfo {
  version: string;
  dataDir: string;
  totalMemoryMb: number;
  suggestedMemoryMb: number;
  msLoginConfigured: boolean;
  discordConfigured: boolean;
  tuffModBundled: boolean;
  tuffMcVersion: string;
}

export interface Account { id: string; name: string; kind: "msa" | "offline" }
export interface AccountList { accounts: Account[]; active: string | null }

export interface DeviceCode {
  device_code: string;
  user_code: string;
  verification_uri: string;
  expires_in: number;
  interval: number;
  message: string;
}

export interface VersionEntry { id: string; kind: string; releaseTime: string }
export interface VersionList { latestRelease: string; latestSnapshot: string; versions: VersionEntry[] }
export interface LoaderVersion { version: string; stable: boolean }
export interface GameVersion { version: string; stable: boolean }

export interface LaunchProgress { profileId: string; stage: string; done: number; total: number }
export interface LogLine { line: string; level: "info" | "warn" | "error" }
export interface CrashReport {
  title: string;
  hint: string;
  /** The mod the crash points at. */
  culprit: string | null;
  /** That mod's file, so the crash dialog can turn it off. */
  modFile: string | null;
  evidence: string[];
}
export interface GameExit { profileId: string; code: number; crash: CrashReport | null }

export type ContentKind = "mod" | "resourcepack" | "shader" | "modpack";
export interface ContentMeta { projectId: string; versionId: string; title: string; iconUrl: string | null; versionNumber: string }
export interface ContentItem { file: string; kind: ContentKind; enabled: boolean; size: number; meta: ContentMeta | null }
export interface ContentUpdate { file: string; kind: ContentKind; title: string; current: string; latest: string }

export interface SearchHit {
  project_id: string;
  slug: string;
  title: string;
  description: string;
  author: string;
  icon_url: string | null;
  downloads: number;
  follows: number;
  categories: string[];
  project_type: ContentKind;
}
export interface SearchResult { hits: SearchHit[]; total_hits: number; offset: number; limit: number }

export interface Server { id: string; name: string; address: string; profileId: string | null }
export interface PingResult { online: number; max: number; motd: string; version: string; latencyMs: number; favicon: string | null }

export interface Screenshot { path: string; profileId: string; profileName: string; modified: number }

export interface SkinEntry { id: string; state: string; url: string; variant?: string; alias?: string; dataUrl: string | null }
export interface SkinProfile { id: string; name: string; skins: SkinEntry[]; capes: SkinEntry[] }

export interface Preset {
  id: string;
  name: string;
  tagline: string;
  highlights: string[];
  icon: string;
  mods: string[];
  shaders: string[];
  resourcepacks: string[];
}
export interface TuffProfileResult { profile: Profile; installed: string[]; skipped: string[] }
