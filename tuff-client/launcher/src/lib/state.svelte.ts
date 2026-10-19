import { api, isTauri, on } from "./api";
import { Speed } from "./progress";
import { toast } from "./toast.svelte";
import type { Account, AppInfo, GameExit, GladeReport, LaunchProgress, LauncherUpdate, LogLine, Profile, Settings, UpdateProgress } from "./types";

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
  /** Download speed of the launch, in bytes per second. */
  speed: 0,
  /** A Glade preset profile being created, and its download speed. */
  creating: false,
  createProgress: null as LaunchProgress | null,
  createSpeed: 0,
  logs: [] as LogLine[],
  lastExit: null as GameExit | null,
  /** The crash dialog is showing for app.lastExit. */
  crashOpen: false,
  /** Profile the library page should open, set by other pages. */
  focusProfile: null as string | null,
  accountModal: false,
  /** A newer launcher; the update screen installs it. */
  update: null as LauncherUpdate | null,
  updateProgress: null as UpdateProgress | null,
});

const launchSpeed = new Speed();
const createSpeed = new Speed();

/** Creates a Glade preset profile, keeping app.createProgress up to date meanwhile. */
export async function createPresetProfile(preset: string, gameVersion: string) {
  app.creating = true;
  app.createProgress = { profileId: "", stage: "Starting", done: 0, total: 0, bytes: 0, bytesTotal: 0 };
  app.createSpeed = 0;
  try {
    return await api.createPresetProfile(preset, gameVersion);
  } finally {
    app.creating = false;
    app.createProgress = null;
  }
}

const UPDATE_CHECK_MS = 60 * 60 * 1000;

/** Checks for a newer launcher and Glade mod. Updates can't be turned off. */
export async function checkForUpdates() {
  try {
    const status = await api.checkUpdate();
    if (status.modUpdated) toast(`Glade mod updated to ${status.modUpdated}. It's used from your next launch.`, "success", 6000);
    if (status.launcher) app.update = status.launcher;
  } catch {
    // offline or no release yet: try again later
  }
}

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
  app.progress = { profileId, stage: "Preparing", done: 0, total: 0, bytes: 0, bytesTotal: 0 };
  app.speed = 0;
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
    if (app.launching !== p.profileId) return;
    app.progress = p;
    app.speed = launchSpeed.sample(p);
  });
  await on<LaunchProgress>("create-progress", (p) => {
    if (!app.creating) return;
    app.createProgress = p;
    app.createSpeed = createSpeed.sample(p);
  });
  await on<LogLine[]>("game-log", (batch) => {
    app.logs.push(...batch);
    if (app.logs.length > MAX_LOG_LINES) app.logs.splice(0, app.logs.length - MAX_LOG_LINES);
  });
  await on<{ running: boolean; profileId: string }>("game-state", (s) => {
    app.running = s.running ? s.profileId : null;
    if (!s.running) refreshProfiles();
  });
  await on<UpdateProgress>("update-progress", (p) => (app.updateProgress = p));
  await on<{ profileId: string; report: GladeReport }>("glade-check", ({ report }) => {
    for (const fix of report.fixed) toast(fix, "info", 5000);
  });
  await on<GameExit>("game-exit", (exit) => {
    app.lastExit = exit;
    if (exit.crash) app.crashOpen = true;
  });
  app.ready = true;
  checkForUpdates();
  setInterval(checkForUpdates, UPDATE_CHECK_MS);
  // browser preview only: ?crash shows the crash dialog with a sample report
  if (!isTauri && location.search.includes("crash")) {
    app.lastExit = {
      profileId: app.profiles[0]?.id ?? "",
      code: 1,
      crash: {
        title: "Sodium needs Fabric API",
        hint: "Install Fabric API from the Mods page, or turn Sodium off.",
        culprit: "Sodium",
        modFile: "sodium-fabric-0.6.13+mc26.3.jar",
        evidence: ["- Mod 'Sodium' (sodium) 0.6.13 requires any version of 'Fabric API' (fabric-api), which is missing!"],
      },
    };
    app.crashOpen = true;
  }
}
