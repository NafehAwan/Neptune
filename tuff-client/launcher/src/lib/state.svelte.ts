import { api, on } from "./api";
import { toast } from "./toast.svelte";
import type { Account, AppInfo, GameExit, LaunchProgress, LogLine, Profile, Settings } from "./types";

export type Route = "home" | "library" | "browse" | "servers" | "wardrobe" | "screenshots" | "console" | "settings";

const MAX_LOG_LINES = 5000;

export const app = $state({
  ready: false,
  route: "home" as Route,
  info: null as AppInfo | null,
  settings: null as Settings | null,
  profiles: [] as Profile[],
  accounts: [] as Account[],
  activeAccount: null as string | null,
  running: null as string | null,
  launching: null as string | null,
  progress: null as LaunchProgress | null,
  logs: [] as LogLine[],
  lastExit: null as GameExit | null,
  /** Profile the library page should open, set by other pages. */
  focusProfile: null as string | null,
  accountModal: false,
});

export function selectedProfile(): Profile | null {
  return app.profiles.find((p) => p.id === app.settings?.selectedProfile) ?? app.profiles[0] ?? null;
}

export function activeAccount(): Account | null {
  return app.accounts.find((a) => a.id === app.activeAccount) ?? null;
}

export function go(route: Route) {
  app.route = route;
}

export async function refreshProfiles() {
  app.profiles = await api.listProfiles();
}

export async function refreshAccounts() {
  const list = await api.listAccounts();
  app.accounts = list.accounts;
  app.activeAccount = list.active;
}

export async function updateSettings(patch: Partial<Settings>) {
  if (!app.settings) return;
  app.settings = await api.saveSettings({ ...app.settings, ...patch });
}

export async function selectProfile(id: string) {
  await updateSettings({ selectedProfile: id });
}

export async function play(profileId: string, server?: string) {
  if (app.running || app.launching) return;
  if (!app.activeAccount) {
    app.accountModal = true;
    toast("Add an account to play.", "info");
    return;
  }
  app.launching = profileId;
  app.progress = { profileId, stage: "Preparing", done: 0, total: 0 };
  app.logs = [];
  app.lastExit = null;
  try {
    await api.launch(profileId, server);
  } catch (e) {
    toast(e, "error");
  } finally {
    app.launching = null;
    app.progress = null;
  }
}

export async function init() {
  const [info, settings] = await Promise.all([api.appInfo(), api.getSettings()]);
  app.info = info;
  app.settings = settings;
  await Promise.all([refreshProfiles(), refreshAccounts()]);
  app.running = await api.runningGame();

  await on<LaunchProgress>("launch-progress", (p) => {
    if (app.launching === p.profileId) app.progress = p;
  });
  await on<LogLine[]>("game-log", (batch) => {
    app.logs.push(...batch);
    if (app.logs.length > MAX_LOG_LINES) app.logs.splice(0, app.logs.length - MAX_LOG_LINES);
  });
  await on<{ running: boolean; profileId: string }>("game-state", (s) => {
    app.running = s.running ? s.profileId : null;
    if (!s.running) refreshProfiles();
  });
  await on<GameExit>("game-exit", (exit) => {
    app.lastExit = exit;
    if (exit.crash) toast(`${exit.crash.title}. Open the console for details.`, "error");
  });
  app.ready = true;
}
