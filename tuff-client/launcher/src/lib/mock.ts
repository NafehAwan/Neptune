import sampleSkin from "../assets/skins/sample.png";
// Sample data for running the UI in a plain browser (`npm run dev`) where the
// Rust backend isn't available. Never loaded inside the desktop app.
import type * as T from "./types";

const now = Math.floor(Date.now() / 1000);
let settings: T.Settings = {
  defaultMemoryMb: 4096, onLaunch: "minimize", lowMotion: false, discordRpc: true, showSnapshots: false,
  javaPath: null, concurrentDownloads: 16, msClientId: null, discordAppId: null, activeAccount: "a1", selectedProfile: "p1",
};
let profiles: T.Profile[] = [
  { id: "p1", name: "Glade", icon: "tuff", gameVersion: "26.3", loader: "fabric", loaderVersion: "0.19.5", memoryMb: 4096, jvmArgs: "", javaPath: null, resolution: null, created: now - 86400 * 9, lastPlayed: now - 3600 * 3, playTimeSecs: 3600 * 14 + 1260, isTuff: true },
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
}));

const delay = (ms: number) => new Promise((r) => setTimeout(r, ms));

export async function mockCall(cmd: string, args: Record<string, any> = {}): Promise<unknown> {
  await delay(120);
  switch (cmd) {
    case "app_info":
      return { version: "1.0.0", dataDir: "~/.local/share/tuff-client", totalMemoryMb: 16384, suggestedMemoryMb: 4096, msLoginConfigured: false, discordConfigured: false, tuffModBundled: true, tuffMcVersion: "26.3" };
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
    case "ms_login_start": throw "Microsoft login isn't set up yet. Add your Azure client ID in Settings → Accounts.";
    case "list_versions":
      return {
        latestRelease: "26.3", latestSnapshot: "26.4-snapshot-2",
        versions: ["26.4-snapshot-2", "26.3", "26.2", "26.1", "1.21.11", "1.21.10", "1.21.8", "1.20.6", "1.20.1", "1.19.4", "1.18.2", "1.16.5", "1.12.2", "1.8.9"].map((id) => ({ id, kind: id.includes("snapshot") ? "snapshot" : "release", releaseTime: "" })),
      };
    case "fabric_loaders": return [{ version: "0.19.5", stable: true }, { version: "0.19.4", stable: true }];
    case "running_game": return null;
    case "launch": throw "This is a browser preview. Launch the desktop app to play.";
    case "list_servers": return servers;
    case "save_servers": servers = args.servers; return null;
    case "ping_server": {
      await delay(300 + Math.random() * 500);
      if (args.address.startsWith("localhost")) throw "server did not respond";
      return { online: Math.floor(Math.random() * 40000), max: 100000, motd: "A Minecraft network", version: "26.3", latencyMs: 20 + Math.floor(Math.random() * 80), favicon: null };
    }
    case "content_list": return args.kind === "mod" ? mods : [];
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
    default: return null;
  }
}
