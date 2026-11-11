import type * as T from "./types";

/** True inside the Tauri webview; false in a plain browser (UI preview). */
export const isTauri = typeof window !== "undefined" && "__TAURI_INTERNALS__" in window;

async function call<R>(cmd: string, args?: Record<string, unknown>): Promise<R> {
  if (!isTauri) {
    const { mockCall } = await import("./mock");
    return mockCall(cmd, args) as Promise<R>;
  }
  const { invoke } = await import("@tauri-apps/api/core");
  return invoke<R>(cmd, args);
}

// browser preview only: the mock backend sends events through here
const previewListeners = new Map<string, Set<(payload: unknown) => void>>();
export function previewEmit(event: string, payload: unknown) {
  for (const h of previewListeners.get(event) ?? []) h(payload);
}

export async function on<P>(event: string, handler: (payload: P) => void): Promise<() => void> {
  if (!isTauri) {
    const set = previewListeners.get(event) ?? new Set();
    previewListeners.set(event, set);
    const h = handler as (payload: unknown) => void;
    set.add(h);
    return () => set.delete(h);
  }
  const { listen } = await import("@tauri-apps/api/event");
  return listen<P>(event, (e) => handler(e.payload));
}

export async function fileSrc(path: string): Promise<string> {
  if (!isTauri) return path;
  const { convertFileSrc } = await import("@tauri-apps/api/core");
  return convertFileSrc(path);
}

export async function pickFile(name: string, extensions: string[]): Promise<string | null> {
  if (!isTauri) return null;
  const { open } = await import("@tauri-apps/plugin-dialog");
  const result = await open({ multiple: false, directory: false, filters: [{ name, extensions }] });
  return typeof result === "string" ? result : null;
}

export async function pickJava(): Promise<string | null> {
  if (!isTauri) return null;
  const { open } = await import("@tauri-apps/plugin-dialog");
  const result = await open({ multiple: false, directory: false, title: "Choose a java executable" });
  return typeof result === "string" ? result : null;
}

export async function windowAction(action: "minimize" | "maximize" | "close") {
  if (!isTauri) return;
  const { getCurrentWindow } = await import("@tauri-apps/api/window");
  const w = getCurrentWindow();
  if (action === "minimize") await w.minimize();
  else if (action === "maximize") await w.toggleMaximize();
  else await w.close();
}

export const api = {
  appInfo: () => call<T.AppInfo>("app_info"),
  getSettings: () => call<T.Settings>("get_settings"),
  saveSettings: (settings: T.Settings) => call<T.Settings>("save_settings", { settings }),
  openUrl: (url: string) => call<void>("open_url", { url }),
  openFolder: (profileId?: string, sub?: string) => call<void>("open_folder", { profileId: profileId ?? null, sub: sub ?? null }),

  listVersions: () => call<T.VersionList>("list_versions"),
  loaderVersions: (loader: T.Loader, gameVersion: string) => call<T.LoaderVersion[]>("loader_versions", { loader, gameVersion }),
  presetGameVersions: () => call<T.GameVersion[]>("preset_game_versions"),

  listProfiles: () => call<T.Profile[]>("list_profiles"),
  saveProfile: (profile: T.Profile) => call<T.Profile>("save_profile", { profile }),
  deleteProfile: (id: string, deleteFiles: boolean) => call<void>("delete_profile", { id, deleteFiles }),
  duplicateProfile: (id: string) => call<T.Profile>("duplicate_profile", { id }),
  createTuffProfile: () => call<T.TuffProfileResult>("create_tuff_profile"),
  listPresets: () => call<T.Preset[]>("list_presets"),
  createPresetProfile: (preset: string, gameVersion: string | null) => call<T.TuffProfileResult>("create_preset_profile", { preset, gameVersion }),

  listAccounts: () => call<T.AccountList>("list_accounts"),
  addOfflineAccount: (name: string) => call<T.Account>("add_offline_account", { name }),
  accountSkin: (id: string) => call<string | null>("account_skin", { id }),
  defaultSkin: () => call<string | null>("default_skin"),
  msLoginStart: () => call<T.DeviceCode>("ms_login_start"),
  msLoginFinish: (code: T.DeviceCode) => call<T.Account>("ms_login_finish", { code }),
  msLoginWindow: () => call<T.Account>("ms_login_window"),
  removeAccount: (id: string) => call<void>("remove_account", { id }),
  setActiveAccount: (id: string) => call<void>("set_active_account", { id }),

  launch: (profileId: string, server?: string) => call<void>("launch", { profileId, server: server ?? null }),
  killGame: () => call<void>("kill_game"),
  runningGame: () => call<string | null>("running_game"),

  search: (query: string, kind: T.ContentKind, gameVersion: string | null, loader: string | null, sort: string, offset: number) =>
    call<T.SearchResult>("modrinth_search", { query, kind, gameVersion, loader, sort, offset }),
  install: (profileId: string, projectId: string, kind: T.ContentKind) => call<string[]>("modrinth_install", { profileId, projectId, kind }),
  contentList: (profileId: string, kind: T.ContentKind) => call<T.ContentItem[]>("content_list", { profileId, kind }),
  contentToggle: (profileId: string, kind: T.ContentKind, file: string, enabled: boolean) =>
    call<void>("content_toggle", { profileId, kind, file, enabled }),
  contentRemove: (profileId: string, kind: T.ContentKind, file: string) => call<void>("content_remove", { profileId, kind, file }),
  checkUpdate: () => call<T.UpdateStatus>("check_update"),
  updateGlade: () => call<string | null>("update_glade"),
  installUpdate: () => call<void>("install_update"),
  contentUpdates: (profileId: string) => call<T.ContentUpdate[]>("content_updates", { profileId }),
  contentUpdate: (profileId: string, kind: T.ContentKind, file: string) => call<void>("content_update", { profileId, kind, file }),
  importMrpack: (path: string) => call<T.Profile>("import_mrpack", { path }),
  installModpack: (projectId: string) => call<T.Profile>("install_modpack", { projectId }),

  listServers: () => call<T.Server[]>("list_servers"),
  saveServers: (servers: T.Server[]) => call<void>("save_servers", { servers }),
  pingServer: (address: string) => call<T.PingResult>("ping_server", { address }),

  skinProfile: () => call<T.SkinProfile>("skin_profile"),
  readSkinFile: (path: string) => call<string>("read_skin_file", { path }),
  uploadSkin: (path: string, slim: boolean) => call<void>("upload_skin", { path, slim }),
  resetSkin: () => call<void>("reset_skin"),
  setCape: (capeId: string | null) => call<void>("set_cape", { capeId }),

  listScreenshots: () => call<T.Screenshot[]>("list_screenshots"),
  deleteScreenshot: (path: string) => call<void>("delete_screenshot", { path }),
  openScreenshot: (path: string) => call<void>("open_screenshot", { path }),
};
