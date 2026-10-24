import sampleSkin from "../assets/skins/sample.png";
// Sample data for running the UI in a plain browser (`npm run dev`) where the
// Rust backend isn't available. Never loaded inside the desktop app.
import type * as T from "./types";
import { previewEmit } from "./api";

// mirrors src-tauri/src/presets.rs
const PRESETS: T.Preset[] = [
  { id: "performance", name: "Performance", icon: "tuff", tagline: "Smooth everyday play. The essential optimisation mods, and the game looks exactly the same.",
    highlights: ["Sodium renderer", "Faster ticking and loading", "Less memory", "Vanilla look"],
    mods: ["fabric-api", "modmenu", "sodium", "lithium", "ferrite-core", "modernfix", "immediatelyfast", "entityculling", "moreculling", "dynamic-fps", "badoptimizations"], shaders: [], resourcepacks: [] },
  { id: "performance-plus", name: "Performance+", icon: "bulb", tagline: "More FPS on average PCs: multithreaded chunks, a faster lighting engine and lighter video settings.",
    highlights: ["Everything in Performance", "Faster chunks and lighting", "Fast graphics", "8 chunk view"],
    mods: ["fabric-api", "modmenu", "sodium", "lithium", "ferrite-core", "modernfix", "immediatelyfast", "entityculling", "moreculling", "dynamic-fps", "badoptimizations", "c2me-fabric", "scalablelux", "krypton", "sodium-extra", "reeses-sodium-options", "ebe", "clumps"], shaders: [], resourcepacks: [] },
  { id: "performance-plus-plus", name: "Performance++", icon: "diamond", tagline: "Maximum FPS for low-end PCs and laptops: every optimisation and the leanest settings.",
    highlights: ["Everything in Performance+", "Faster world generation", "Minimal effects", "6 chunk view"],
    mods: ["fabric-api", "modmenu", "sodium", "lithium", "ferrite-core", "modernfix", "immediatelyfast", "entityculling", "moreculling", "dynamic-fps", "badoptimizations", "c2me-fabric", "scalablelux", "krypton", "sodium-extra", "reeses-sodium-options", "ebe", "clumps", "noisium", "vmp-fabric", "ksyxis"], shaders: [], resourcepacks: [] },
  { id: "scenic", name: "Scenic", icon: "grass", tagline: "Clean, good-looking graphics that still run well: soft shaders, connected textures and lively mobs.",
    highlights: ["Complementary shaders", "Connected textures", "Dynamic lights", "Fresh Animations"],
    mods: ["fabric-api", "modmenu", "sodium", "lithium", "ferrite-core", "modernfix", "immediatelyfast", "entityculling", "dynamic-fps", "iris", "continuity", "lambdynamiclights", "entity-model-features", "entitytexturefeatures", "fallingleaves", "visuality"], shaders: ["complementary-reimagined"], resourcepacks: ["fresh-animations"] },
];

const now = Math.floor(Date.now() / 1000);
let settings: T.Settings = {
  defaultMemoryMb: 4096, onLaunch: "minimize", lowMotion: false, discordRpc: true, showSnapshots: false,
  javaPath: null, concurrentDownloads: 16, msClientId: null, discordAppId: null, activeAccount: "a1", selectedProfile: "p1",
};
let profiles: T.Profile[] = [
  { id: "glade-performance", preset: "performance", name: "Glade Performance", icon: "tuff", gameVersion: "26.3", loader: "fabric", loaderVersion: "0.19.5", memoryMb: 4096, jvmArgs: "", javaPath: null, resolution: null, created: now - 86400 * 9, lastPlayed: now - 3600 * 3, playTimeSecs: 3600 * 14 + 1260, isTuff: true },
  { id: "p2", name: "Survival", icon: "grass", gameVersion: "26.3", loader: "vanilla", loaderVersion: null, memoryMb: 3072, jvmArgs: "", javaPath: null, resolution: null, created: now - 86400 * 30, lastPlayed: now - 86400, playTimeSecs: 3600 * 52, isTuff: false },
  { id: "p3", name: "PvP 1.8.9", icon: "sword", gameVersion: "1.8.9", loader: "vanilla", loaderVersion: null, memoryMb: 2048, jvmArgs: "", javaPath: null, resolution: null, created: now - 86400 * 60, lastPlayed: now - 86400 * 4, playTimeSecs: 3600 * 8, isTuff: false },
  { id: "p4", name: "Create & Build", icon: "pickaxe", gameVersion: "1.21.11", loader: "fabric", loaderVersion: "0.19.5", memoryMb: 6144, jvmArgs: "", javaPath: null, resolution: null, created: now - 86400 * 90, lastPlayed: null, playTimeSecs: 0, isTuff: false },
];
let accounts: T.Account[] = [
  { id: "a1", name: "CopperGolem", kind: "msa" },
  { id: "a2", name: "Builder", kind: "offline" },
];
let servers: T.Server[] = [
  { id: "s1", name: "Hypixel", address: "mc.hypixel.net", profileId: "p1" },
  { id: "s2", name: "Wynncraft", address: "play.wynncraft.com", profileId: null },
  { id: "s3", name: "Local test", address: "localhost:25565", profileId: null },
];
const mods: T.ContentItem[] = [
  ["Fabric API", "0.162.0+26.3"], ["Sodium", "0.8.2"], ["Lithium", "0.17.0"], ["FerriteCore", "8.0.1"],
  ["Entity Culling", "1.9.3"], ["ImmediatelyFast", "1.6.4"], ["ModernFix", "5.24.1"], ["Glade", "1.0.0"],
].map(([title, v], i) => ({
  file: `${title.toLowerCase().replace(/\W+/g, "-")}.jar`, kind: "mod" as const, enabled: i !== 6, size: 400000 + i * 91234,
  meta: { projectId: `id${i}`, versionId: `v${i}`, title, iconUrl: null, versionNumber: v },
  locked: title === "Glade" ? "Glade is part of this profile" : title === "Fabric API" ? "Glade needs Fabric API" : null,
}));

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

/** Plays a fake download as progress events: `mb` megabytes at about 6 MB/s. */
async function fakeDownload(event: string, base: Omit<T.LaunchProgress, "bytes" | "bytesTotal">, mb: number, files = 0) {
  const total = Math.round(mb * 1024 * 1024);
  const steps = Math.max(4, Math.round(mb / 0.6));
  for (let i = 0; i <= steps; i++) {
    const p = { ...base, bytes: Math.round((total * i) / steps), bytesTotal: total };
    if (files) Object.assign(p, { done: Math.round((files * i) / steps), total: files });
    previewEmit(event, p);
    await delay(100);
  }
}

export async function mockCall(cmd: string, args: Record<string, any> = {}): Promise<unknown> {
  await delay(120);
  switch (cmd) {
    case "app_info":
      return { version: "1.0.0", dataDir: "~/.local/share/tuff-client", totalMemoryMb: 16384, suggestedMemoryMb: 4096, msLoginConfigured: true, discordConfigured: false, tuffModBundled: true, tuffMcVersion: "26.3", gladeVersion: "1.1.0", gladeMinecraft: ">=1.21 <=1.21.1 || ~26.3" };
    case "get_settings": return settings;
    case "save_settings": settings = args.settings; return settings;
    case "list_profiles": return profiles;
    case "save_profile": {
      const p = { ...args.profile, id: args.profile.id || crypto.randomUUID(), created: args.profile.created || now };
      profiles = [...profiles.filter((x) => x.id !== p.id), p];
      return p;
    }
    case "delete_profile": profiles = profiles.filter((p) => p.id !== args.id); return null;
    case "duplicate_profile": {
      const src = profiles.find((p) => p.id === args.id)!;
      const p = { ...src, id: crypto.randomUUID(), name: `${src.name} copy` };
      profiles = [...profiles, p];
      return p;
    }
    case "list_accounts": return { accounts, active: settings.activeAccount };
    case "account_skin": return accounts.find((a) => a.id === args.id)?.kind === "msa" ? sampleSkin : null;
    case "default_skin": return null;
    case "set_active_account": settings.activeAccount = args.id; return null;
    case "add_offline_account": { const a = { id: crypto.randomUUID(), name: args.name, kind: "offline" }; accounts = [...accounts, a as T.Account]; return a; }
    case "remove_account": accounts = accounts.filter((a) => a.id !== args.id); return null;
    case "ms_login_window":
    case "ms_login_start": throw "Microsoft login isn't set up yet. Add your Azure client ID in Settings → Accounts.";
    case "list_versions":
      return {
        latestRelease: "26.3", latestSnapshot: "26.4-snapshot-2",
        versions: ["26.4-snapshot-2", "26.3", "26.2", "26.1", "1.21.11", "1.21.10", "1.21.8", "1.20.6", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2", "1.8.9"].map((id) => ({ id, kind: id.includes("snapshot") ? "snapshot" : "release", releaseTime: "" })),
      };
    case "loader_versions": {
      const v = args.gameVersion as string;
      switch (args.loader) {
        case "fabric": return [{ version: "0.19.6-beta.1", stable: false }, { version: "0.19.5", stable: true }, { version: "0.19.4", stable: true }];
        case "quilt": return [{ version: "0.30.0-beta.2", stable: false }, { version: "0.29.2", stable: true }];
        case "forge": return v.startsWith("26.") ? [] : [{ version: "47.4.0", stable: true }, { version: "47.3.12", stable: true }];
        case "neoforge": return v === "1.12.2" ? [] : [{ version: "21.1.80-beta", stable: false }, { version: "21.1.77", stable: true }];
        default: return [];
      }
    }
    case "preset_game_versions":
      return ["26.4-snapshot-2", "26.3", "26.2", "26.1", "1.21.11", "1.21.10", "1.21.8", "1.20.6", "1.20.1"].map((version) => ({ version, stable: !version.includes("snapshot") }));
    case "running_game": return null;
    case "launch": {
      // ?download plays a fake first launch
      if (location.search.includes("download")) {
        const id = args.profileId as string;
        const ev = "launch-progress";
        previewEmit(ev, { profileId: id, stage: "Checking Glade", done: 0, total: 1, bytes: 0, bytesTotal: 0 });
        await delay(600);
        await fakeDownload(ev, { profileId: id, stage: "Downloading Fabric API", done: 0, total: 1 }, 2.4);
        await fakeDownload(ev, { profileId: id, stage: "Downloading game files", done: 0, total: 0 }, 38, 64);
        await fakeDownload(ev, { profileId: id, stage: "Downloading sounds and textures", done: 0, total: 0 }, 420, 3912);
      }
      throw "This is a browser preview. Launch the desktop app to play.";
    }
    case "list_servers": return servers;
    case "save_servers": servers = args.servers; return null;
    case "ping_server": {
      await delay(300 + Math.random() * 500);
      if (args.address.startsWith("localhost")) throw "server did not respond";
      return { online: Math.floor(Math.random() * 40000), max: 100000, motd: "A Minecraft network", version: "26.3", latencyMs: 20 + Math.floor(Math.random() * 80), favicon: null };
    }
    case "content_list": return args.kind === "mod" ? mods : [];
    case "check_update":
      // browser preview only: ?update shows the update screen
      return location.search.includes("update")
        ? { launcher: { version: "1.2.0", current: "1.1.0", notes: "Glade is now in every Fabric profile.", page: "#", canInstall: true }, modUpdated: null }
        : { launcher: null, modUpdated: null };
    case "install_update": await new Promise((r) => setTimeout(r, 1500)); throw "The password prompt was closed, so the update wasn't installed.";
    case "content_updates": return [{ file: mods[1].file, kind: "mod", title: "Sodium", current: "0.8.2", latest: "0.8.3" }];
    case "modrinth_search": {
      const titles = ["Sodium", "Iris Shaders", "Lithium", "Fabric API", "Mod Menu", "Entity Culling", "ImmediatelyFast", "Zoomify", "Continuity", "Sound Physics"];
      return {
        hits: titles.map((t, i) => ({ project_id: `x${i}`, slug: t, title: t, description: "A short description of what this project adds to the game.", author: "someone", icon_url: null, downloads: 98000000 / (i + 1), follows: 12000, categories: ["optimization"], project_type: args.kind })),
        total_hits: 200, offset: 0, limit: 20,
      };
    }
    case "list_screenshots": return [];
    case "skin_profile": throw "Skins and capes need a Microsoft account.";
    case "list_presets": return PRESETS;
    case "create_preset_profile": {
      const preset = PRESETS.find((p) => p.id === args.preset)!;
      const ev = "create-progress";
      const steps = 1 + preset.mods.length + preset.shaders.length + preset.resourcepacks.length;
      previewEmit(ev, { profileId: "", stage: "Adding Glade", done: 0, total: steps, bytes: 0, bytesTotal: 0 });
      await delay(300);
      for (const [i, slug] of [...preset.mods, ...preset.shaders, ...preset.resourcepacks].entries()) {
        previewEmit(ev, { profileId: "", stage: `Finding ${slug.replace(/-/g, " ")}`, done: i + 1, total: steps, bytes: 0, bytesTotal: 0 });
        await delay(150);
        await fakeDownload(ev, { profileId: "", stage: `Downloading ${slug}`, done: i + 1, total: steps }, 0.3 + (i % 4) * 0.9);
      }
      previewEmit(ev, { profileId: "", stage: "Finishing", done: steps, total: steps, bytes: 0, bytesTotal: 0 });
      let id = `glade-${preset.id}`, n = 2;
      while (profiles.some((p) => p.id === id)) id = `glade-${preset.id}-${n++}`;
      const profile: T.Profile = { id, name: `Glade ${preset.name}`, icon: preset.icon, gameVersion: args.gameVersion ?? "26.3", loader: "fabric", loaderVersion: "0.19.5", memoryMb: 4096, jvmArgs: "", javaPath: null, resolution: null, created: now, lastPlayed: null, playTimeSecs: 0, isTuff: true, preset: preset.id };
      profiles = [...profiles, profile];
      return { profile, installed: preset.mods, skipped: profile.gameVersion === "26.3" ? [] : ["Glade mod (built for 26.3)"] };
    }
    default: return null;
  }
}
