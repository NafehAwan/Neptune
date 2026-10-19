mod auth;
mod crash;
mod discord;
mod download;
mod fabric;
mod glade;
mod install;
mod java;
mod loaders;
mod launch;
mod meta;
mod modrinth;
mod models;
mod paths;
mod presets;
mod servers;
mod skins;
#[cfg(test)]
mod smoke;
mod store;
mod system;
mod update;

use anyhow::{anyhow, Context};
use discord::{Discord, Presence};
use install::Progress;
use models::*;
use paths::Paths;
use serde::Serialize;
use serde_json::{json, Value};
use std::collections::{HashSet, VecDeque};
use std::path::{Path, PathBuf};
use std::sync::{Arc, Mutex};
use std::time::{Duration, Instant};
use tauri::{AppHandle, Emitter, Manager, State};
use tauri_plugin_opener::OpenerExt;
use tokio::io::{AsyncBufReadExt, BufReader};
use tokio::process::Command;

/// Minecraft version the bundled Glade mod is built for.
pub const TUFF_MC_VERSION: &str = "26.3";
type CmdResult<T> = Result<T, String>;

fn err(e: anyhow::Error) -> String {
    format!("{e:#}")
}

struct RunningGame {
    profile_id: String,
    kill: Option<tokio::sync::oneshot::Sender<()>>,
}

pub struct AppState {
    paths: Paths,
    http: reqwest::Client,
    /// Serialises read-modify-write cycles on the JSON stores.
    store_lock: Mutex<()>,
    game: tokio::sync::Mutex<Option<RunningGame>>,
    discord: Discord,
}

impl AppState {
    fn settings(&self) -> Settings {
        store::load(&self.paths.file("settings.json"))
    }
    fn profiles(&self) -> Vec<Profile> {
        store::load(&self.paths.file("profiles.json"))
    }
    fn accounts(&self) -> Vec<Account> {
        store::load(&self.paths.file("accounts.json"))
    }
    fn profile(&self, id: &str) -> anyhow::Result<Profile> {
        self.profiles()
            .into_iter()
            .find(|p| p.id == id)
            .ok_or_else(|| anyhow!("profile not found"))
    }
    fn update_profiles(&self, f: impl FnOnce(&mut Vec<Profile>)) -> anyhow::Result<()> {
        let _guard = self.store_lock.lock().unwrap();
        let mut profiles = self.profiles();
        f(&mut profiles);
        store::save(&self.paths.file("profiles.json"), &profiles)
    }
    fn update_settings(&self, f: impl FnOnce(&mut Settings)) -> anyhow::Result<Settings> {
        let _guard = self.store_lock.lock().unwrap();
        let mut settings = self.settings();
        f(&mut settings);
        store::save(&self.paths.file("settings.json"), &settings)?;
        Ok(settings)
    }
    fn update_accounts(&self, f: impl FnOnce(&mut Vec<Account>)) -> anyhow::Result<()> {
        let _guard = self.store_lock.lock().unwrap();
        let mut accounts = self.accounts();
        f(&mut accounts);
        store::save_private(&self.paths.file("accounts.json"), &accounts)
    }
    fn discord_app_id(&self) -> Option<String> {
        let s = self.settings();
        if !s.discord_rpc {
            return None;
        }
        s.discord_app_id
            .filter(|id| !id.trim().is_empty())
            .or_else(|| discord::BUILD_APP_ID.map(String::from))
    }
}

fn now() -> i64 {
    chrono::Utc::now().timestamp()
}

/// Glade mod jars bundled with the launcher: one per group of Minecraft versions.
fn bundled_mods(app: &AppHandle) -> Vec<PathBuf> {
    let Ok(dir) = app.path().resource_dir() else { return Vec::new() };
    let res = dir.join("resources");
    vec![res.join("glade-mods"), res.join("tuff-mod.jar")]
}

/// Every Glade mod build: bundled with the launcher, or newer ones the updater downloaded.
fn glade_builds(app: &AppHandle, paths: &Paths) -> glade::Builds {
    glade::find_builds(&bundled_mods(app), paths)
}

// ---------------------------------------------------------------- app info

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AppInfo {
    version: String,
    data_dir: String,
    total_memory_mb: u32,
    suggested_memory_mb: u32,
    ms_login_configured: bool,
    discord_configured: bool,
    tuff_mod_bundled: bool,
    tuff_mc_version: String,
    /// The Glade mod's version, and the Minecraft versions it runs on (`~26.3`).
    glade_version: Option<String>,
    glade_minecraft: String,
}

#[tauri::command]
fn app_info(app: AppHandle, state: State<'_, AppState>) -> AppInfo {
    let settings = state.settings();
    let builds = glade_builds(&app, &state.paths);
    let glade_minecraft = builds.ranges();
    let newest = builds.0.iter().map(|b| b.info.version.clone()).max_by(|a, b| glade::compare_versions(a, b));
    AppInfo {
        version: app.package_info().version.to_string(),
        data_dir: state.paths.root.to_string_lossy().into_owned(),
        total_memory_mb: system::total_memory_mb(),
        suggested_memory_mb: system::suggested_memory_mb(),
        ms_login_configured: auth::client_id(settings.ms_client_id.as_deref()).is_ok(),
        discord_configured: settings.discord_app_id.is_some() || discord::BUILD_APP_ID.is_some(),
        tuff_mod_bundled: !builds.0.is_empty(),
        tuff_mc_version: TUFF_MC_VERSION.into(),
        glade_version: newest,
        glade_minecraft,
    }
}

#[tauri::command]
fn get_settings(state: State<'_, AppState>) -> Settings {
    state.settings()
}

#[tauri::command]
fn save_settings(state: State<'_, AppState>, settings: Settings) -> CmdResult<Settings> {
    state.update_settings(|s| *s = settings).map_err(err)
}

#[tauri::command]
fn open_url(app: AppHandle, url: String) -> CmdResult<()> {
    if !url.starts_with("https://") {
        return Err("only https links can be opened".into());
    }
    app.opener().open_url(url, None::<&str>).map_err(|e| e.to_string())
}

/// Opens a folder inside the data directory in the file manager.
#[tauri::command]
fn open_folder(app: AppHandle, state: State<'_, AppState>, profile_id: Option<String>, sub: Option<String>) -> CmdResult<()> {
    let mut dir = match &profile_id {
        Some(id) => state.paths.instance(id),
        None => state.paths.root.clone(),
    };
    if let Some(sub) = sub.filter(|s| !s.is_empty()) {
        if sub.contains("..") || sub.contains('/') {
            return Err("invalid folder".into());
        }
        dir = dir.join(sub);
    }
    std::fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
    app.opener()
        .open_path(dir.to_string_lossy(), None::<&str>)
        .map_err(|e| e.to_string())
}

// ---------------------------------------------------------------- versions

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct VersionList {
    latest_release: String,
    latest_snapshot: String,
    versions: Vec<VersionEntry>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct VersionEntry {
    id: String,
    kind: String,
    release_time: String,
}

#[tauri::command]
async fn list_versions(state: State<'_, AppState>) -> CmdResult<VersionList> {
    let m = meta::manifest(&state.http, &state.paths, false).await.map_err(err)?;
    Ok(VersionList {
        latest_release: m.latest.release,
        latest_snapshot: m.latest.snapshot,
        versions: m
            .versions
            .into_iter()
            .map(|v| VersionEntry { id: v.id, kind: v.kind, release_time: v.release_time })
            .collect(),
    })
}

/// Loader versions for a Minecraft version, newest first (stable ones flagged).
#[tauri::command]
async fn loader_versions(state: State<'_, AppState>, loader: Loader, game_version: String) -> CmdResult<Vec<fabric::LoaderVersion>> {
    loaders::versions(&state.http, &loader, &game_version).await.map_err(err)
}

/// Minecraft versions Glade presets (Fabric) can use, newest first.
#[tauri::command]
async fn preset_game_versions(state: State<'_, AppState>) -> CmdResult<Vec<fabric::GameVersion>> {
    fabric::game_versions(&state.http).await.map_err(err)
}

// ---------------------------------------------------------------- profiles

#[tauri::command]
fn list_profiles(state: State<'_, AppState>) -> Vec<Profile> {
    let mut profiles = state.profiles();
    profiles.sort_by_key(|p| std::cmp::Reverse(p.last_played.unwrap_or(p.created)));
    profiles
}

#[tauri::command]
fn save_profile(state: State<'_, AppState>, mut profile: Profile) -> CmdResult<Profile> {
    if profile.name.trim().is_empty() {
        return Err("Give the profile a name.".into());
    }
    if profile.game_version.trim().is_empty() {
        return Err("Pick a Minecraft version.".into());
    }
    if profile.id.is_empty() {
        profile.id = uuid::Uuid::new_v4().simple().to_string();
        profile.created = now();
    }
    if profile.memory_mb == 0 {
        profile.memory_mb = state.settings().default_memory_mb;
    }
    let saved = profile.clone();
    state
        .update_profiles(|list| match list.iter_mut().find(|p| p.id == profile.id) {
            Some(existing) => *existing = profile,
            None => list.push(profile),
        })
        .map_err(err)?;
    std::fs::create_dir_all(state.paths.instance(&saved.id)).map_err(|e| e.to_string())?;
    Ok(saved)
}

#[tauri::command]
fn delete_profile(state: State<'_, AppState>, id: String, delete_files: bool) -> CmdResult<()> {
    state.update_profiles(|list| list.retain(|p| p.id != id)).map_err(err)?;
    if delete_files && !id.is_empty() && !id.contains(['/', '.']) {
        let _ = std::fs::remove_dir_all(state.paths.instance(&id));
    }
    Ok(())
}

fn copy_dir(from: &Path, to: &Path) -> std::io::Result<()> {
    std::fs::create_dir_all(to)?;
    for entry in std::fs::read_dir(from)? {
        let entry = entry?;
        let target = to.join(entry.file_name());
        let kind = entry.file_type()?;
        if kind.is_dir() {
            copy_dir(&entry.path(), &target)?;
        } else if kind.is_file() {
            std::fs::copy(entry.path(), target)?;
        }
    }
    Ok(())
}

#[tauri::command]
fn duplicate_profile(state: State<'_, AppState>, id: String) -> CmdResult<Profile> {
    let source = state.profile(&id).map_err(err)?;
    let mut copy = source.clone();
    copy.id = uuid::Uuid::new_v4().simple().to_string();
    copy.name = format!("{} copy", source.name);
    copy.created = now();
    copy.last_played = None;
    copy.play_time_secs = 0;
    let from = state.paths.instance(&id);
    if from.exists() {
        for sub in ["mods", "config", "resourcepacks", "shaderpacks", ".tuff", "options.txt"] {
            let src = from.join(sub);
            let dst = state.paths.instance(&copy.id).join(sub);
            let _ = if src.is_dir() { copy_dir(&src, &dst) } else { std::fs::copy(&src, &dst).map(|_| ()) };
        }
    }
    let saved = copy.clone();
    state.update_profiles(|list| list.push(copy)).map_err(err)?;
    Ok(saved)
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct TuffProfileResult {
    profile: Profile,
    installed: Vec<String>,
    skipped: Vec<String>,
}

#[tauri::command]
fn list_presets() -> Vec<presets::Preset> {
    presets::ALL.to_vec()
}

/// A folder name nobody uses yet: glade-performance, glade-performance-2, ...
fn free_profile_id(state: &AppState, base: &str) -> String {
    let taken: HashSet<String> = state.profiles().iter().map(|p| p.id.clone()).collect();
    (1..)
        .map(|n| if n == 1 { base.to_string() } else { format!("{base}-{n}") })
        .find(|id| !taken.contains(id) && !state.paths.instance(id).exists())
        .unwrap()
}

/// Creates a Glade profile from a preset in its own folder: the Glade mod, the
/// preset's mods, shader and resource packs, and its video settings.
#[tauri::command]
async fn create_preset_profile(app: AppHandle, state: State<'_, AppState>, preset: String, game_version: Option<String>) -> CmdResult<TuffProfileResult> {
    let preset = presets::find(&preset).ok_or_else(|| format!("Unknown preset '{preset}'"))?;
    let game_version = game_version.filter(|v| !v.trim().is_empty()).unwrap_or_else(|| TUFF_MC_VERSION.into());
    // presets are always Fabric
    let loader = loaders::latest(&state.http, &Loader::Fabric, &game_version).await.map_err(err)?;
    let id = free_profile_id(&state, &format!("glade-{}", preset.id));
    let profile = save_profile(
        state.clone(),
        Profile {
            id: id.clone(),
            name: format!("Glade {}", preset.name),
            icon: preset.icon.into(),
            game_version: game_version.clone(),
            loader: Loader::Fabric,
            loader_version: Some(loader),
            is_tuff: true,
            preset: Some(preset.id.into()),
            created: now(),
            ..Default::default()
        },
    )?;
    let (mut installed, mut skipped) = (Vec::new(), Vec::new());
    let builds = glade_builds(&app, &state.paths);
    match glade::ensure(&state.http, &state.paths, &profile, &builds).await {
        Ok(r) if r.installed => installed.push(format!("Glade {}", r.version.unwrap_or_default())),
        Ok(r) => skipped.push(r.reason.unwrap_or_else(|| "Glade mod".into())),
        Err(e) => skipped.push(format!("Glade mod ({e:#})")),
    }
    let groups: [(&[&str], &str); 3] = [(preset.mods, "mod"), (preset.shaders, "shader"), (preset.resourcepacks, "resourcepack")];
    for (slugs, kind) in groups {
        for slug in slugs {
            match modrinth::install(&state.http, &state.paths, &profile, slug, kind).await {
                Ok(titles) => installed.extend(titles),
                Err(_) => skipped.push(slug.to_string()),
            }
        }
    }
    let instance = state.paths.instance(&profile.id);
    let files = |kind: &str| -> Vec<String> {
        modrinth::list(&state.paths, &profile.id, kind).map(|l| l.into_iter().map(|i| i.file).collect()).unwrap_or_default()
    };
    let _ = presets::write_options(&instance, preset, &files("resourcepack"));
    if let Some(shader) = files("shader").first() {
        let _ = presets::enable_shader(&instance, shader);
    }
    Ok(TuffProfileResult { profile, installed, skipped })
}

#[tauri::command]
async fn create_tuff_profile(app: AppHandle, state: State<'_, AppState>) -> CmdResult<TuffProfileResult> {
    create_preset_profile(app, state, "performance".into(), None).await
}

// ---------------------------------------------------------------- accounts

/// The account's skin as a data URL: premium accounts get their real skin from
/// Mojang (cached for a few hours, and kept for offline use), offline accounts
/// get `None` and use the default skin.
#[tauri::command]
async fn account_skin(state: State<'_, AppState>, id: String) -> CmdResult<Option<String>> {
    let Some(account) = state.accounts().into_iter().find(|a| a.id == id) else { return Ok(None) };
    if !matches!(account.kind, AccountKind::Msa) {
        return Ok(None);
    }
    let dir = state.paths.root.join("skins");
    let cache = dir.join(format!("{}.png", account.id.replace(['/', '\\', '.'], "")));
    let fresh = std::fs::metadata(&cache)
        .and_then(|m| m.modified())
        .map(|t| t.elapsed().unwrap_or_default() < Duration::from_secs(6 * 3600))
        .unwrap_or(false);
    if fresh {
        if let Ok(bytes) = std::fs::read(&cache) {
            return Ok(Some(skins::png_data_url(&bytes)));
        }
    }
    match skins::session_skin(&state.http, &account.id).await {
        Ok(Some(bytes)) => {
            let _ = std::fs::create_dir_all(&dir);
            let _ = std::fs::write(&cache, &bytes);
            Ok(Some(skins::png_data_url(&bytes)))
        }
        Ok(None) => {
            let _ = std::fs::remove_file(&cache);
            Ok(None)
        }
        // offline or rate-limited: fall back to the last skin we saw
        Err(_) => Ok(std::fs::read(&cache).ok().map(|b| skins::png_data_url(&b))),
    }
}

/// The game's default skin, read from an installed Minecraft jar (None until
/// a version has been installed; the UI then uses its own stand-in).
#[tauri::command]
fn default_skin(state: State<'_, AppState>) -> Option<String> {
    let cache = state.paths.root.join("skins").join("default.png");
    if let Ok(bytes) = std::fs::read(&cache) {
        return Some(skins::png_data_url(&bytes));
    }
    let bytes = skins::default_skin_from_jars(&state.paths.versions())?;
    let _ = std::fs::create_dir_all(state.paths.root.join("skins"));
    let _ = std::fs::write(&cache, &bytes);
    Some(skins::png_data_url(&bytes))
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct AccountList {
    accounts: Vec<AccountView>,
    active: Option<String>,
}

#[tauri::command]
fn list_accounts(state: State<'_, AppState>) -> AccountList {
    let accounts = state.accounts();
    let mut active = state.settings().active_account;
    if !accounts.iter().any(|a| Some(&a.id) == active.as_ref()) {
        active = accounts.first().map(|a| a.id.clone());
    }
    AccountList { accounts: accounts.iter().map(AccountView::from).collect(), active }
}

fn store_account(state: &AppState, account: Account) -> anyhow::Result<AccountView> {
    let view = AccountView::from(&account);
    let id = account.id.clone();
    state.update_accounts(|list| {
        list.retain(|a| a.id != account.id);
        list.push(account);
    })?;
    state.update_settings(|s| s.active_account = Some(id))?;
    Ok(view)
}

#[tauri::command]
fn add_offline_account(state: State<'_, AppState>, name: String) -> CmdResult<AccountView> {
    let account = auth::offline_account(&name).map_err(err)?;
    store_account(&state, account).map_err(err)
}

#[tauri::command]
async fn ms_login_start(app: AppHandle, state: State<'_, AppState>) -> CmdResult<auth::DeviceCode> {
    let client_id = auth::client_id(state.settings().ms_client_id.as_deref()).map_err(err)?;
    let code = auth::start_device_code(&state.http, &client_id).await.map_err(err)?;
    let _ = app.opener().open_url(&code.verification_uri, None::<&str>);
    Ok(code)
}

#[tauri::command]
async fn ms_login_finish(state: State<'_, AppState>, code: auth::DeviceCode) -> CmdResult<AccountView> {
    let client_id = auth::client_id(state.settings().ms_client_id.as_deref()).map_err(err)?;
    let account = auth::finish_device_code(&state.http, &client_id, &code).await.map_err(err)?;
    store_account(&state, account).map_err(err)
}

#[tauri::command]
fn remove_account(state: State<'_, AppState>, id: String) -> CmdResult<()> {
    state.update_accounts(|list| list.retain(|a| a.id != id)).map_err(err)
}

#[tauri::command]
fn set_active_account(state: State<'_, AppState>, id: String) -> CmdResult<()> {
    state.update_settings(|s| s.active_account = Some(id)).map(|_| ()).map_err(err)
}

/// Active account with a valid token, refreshing Microsoft tokens if needed.
async fn active_account(state: &AppState) -> anyhow::Result<Account> {
    let list = list_accounts_inner(state);
    let id = list.1.ok_or_else(|| anyhow!("Add an account first (top right)."))?;
    let account = list.0.into_iter().find(|a| a.id == id).ok_or_else(|| anyhow!("account not found"))?;
    if account.kind == AccountKind::Msa && account.expires_at < now() + 300 {
        let client_id = auth::client_id(state.settings().ms_client_id.as_deref())?;
        let fresh = auth::refresh(&state.http, &client_id, &account).await?;
        store_account(state, fresh.clone())?;
        return Ok(fresh);
    }
    Ok(account)
}

fn list_accounts_inner(state: &AppState) -> (Vec<Account>, Option<String>) {
    let accounts = state.accounts();
    let mut active = state.settings().active_account;
    if !accounts.iter().any(|a| Some(&a.id) == active.as_ref()) {
        active = accounts.first().map(|a| a.id.clone());
    }
    (accounts, active)
}

/// Shares the account list with the in-game account menu: ids, names and kinds only, never tokens.
fn write_account_list(state: &AppState, game_dir: &Path) {
    let (accounts, active) = list_accounts_inner(state);
    let list = AccountList { accounts: accounts.iter().map(AccountView::from).collect(), active };
    let dir = game_dir.join("config").join("tuff");
    let written = std::fs::create_dir_all(&dir)
        .map_err(anyhow::Error::from)
        .and_then(|_| Ok(std::fs::write(dir.join("accounts.json"), serde_json::to_vec_pretty(&list)?)?));
    if let Err(e) = written {
        eprintln!("could not share the account list with the game: {e}");
    }
}

// ---------------------------------------------------------------- launching

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
struct LaunchProgress {
    profile_id: String,
    #[serde(flatten)]
    progress: Progress,
}

#[derive(Serialize, Clone)]
struct LogLine {
    line: String,
    level: &'static str,
}

fn log_level(line: &str) -> &'static str {
    if line.contains("/ERROR]") || line.contains("[ERROR]") || line.contains("Exception") || line.starts_with("\tat ") {
        "error"
    } else if line.contains("/WARN]") || line.contains("[WARN]") {
        "warn"
    } else {
        "info"
    }
}

/// Before every launch: Glade is installed and up to date where it can run,
/// with what it needs, and taken out where it can't.
async fn check_glade(app: &AppHandle, state: &AppState, profile: &mut Profile) -> anyhow::Result<glade::Report> {
    let _ = app.emit("launch-progress", LaunchProgress {
        profile_id: profile.id.clone(),
        progress: Progress { stage: "Checking Glade".into(), done: 0, total: 1 },
    });
    let builds = glade_builds(app, &state.paths);
    // Glade needs a recent Fabric Loader; move old profiles up to the newest one.
    let build = builds.for_version(&profile.game_version).filter(|_| glade::applies(profile, &builds));
    if let (Some(b), Some(current)) = (build, profile.loader_version.clone()) {
        if let Some(min) = glade::min_loader(b) {
            if glade::compare_versions(&current, &min) == std::cmp::Ordering::Less {
                let latest = loaders::latest(&state.http, &Loader::Fabric, &profile.game_version).await?;
                profile.loader_version = Some(latest.clone());
                let id = profile.id.clone();
                state.update_profiles(|list| {
                    if let Some(p) = list.iter_mut().find(|p| p.id == id) {
                        p.loader_version = Some(latest.clone());
                    }
                })?;
            }
        }
    }
    let report = glade::ensure(&state.http, &state.paths, profile, &builds).await?;
    let _ = app.emit("glade-check", json!({ "profileId": profile.id, "report": report }));
    Ok(report)
}

fn restore_window(app: &AppHandle) {
    if let Some(w) = app.get_webview_window("main") {
        let _ = w.show();
        let _ = w.unminimize();
        let _ = w.set_focus();
    }
}

#[tauri::command]
async fn launch(app: AppHandle, state: State<'_, AppState>, profile_id: String, server: Option<String>) -> CmdResult<()> {
    let mut game = state.game.lock().await;
    if game.is_some() {
        return Err("Minecraft is already running.".into());
    }
    let mut profile = state.profile(&profile_id).map_err(err)?;
    let settings = state.settings();
    let account = active_account(&state).await.map_err(err)?;
    check_glade(&app, &state, &mut profile).await.map_err(|e| format!("Glade check failed: {e:#}"))?;

    let last_emit = Mutex::new(Instant::now() - Duration::from_secs(1));
    let progress_app = app.clone();
    let pid = profile_id.clone();
    let on_progress = move |p: Progress| {
        let mut last = last_emit.lock().unwrap();
        if p.done == p.total || last.elapsed() > Duration::from_millis(60) {
            *last = Instant::now();
            let _ = progress_app.emit("launch-progress", LaunchProgress { profile_id: pid.clone(), progress: p });
        }
    };
    let features = meta::Features {
        custom_resolution: profile.resolution.is_some(),
        quick_play_multiplayer: server.is_some(),
    };
    let java_override = profile.java_path.clone().or(settings.java_path.clone());
    let prepared = install::prepare(
        &state.http,
        &state.paths,
        &profile,
        java_override,
        settings.concurrent_downloads.max(1) as usize,
        &features,
        &on_progress,
    )
    .await
    .map_err(err)?;

    let game_dir = state.paths.instance(&profile.id);
    std::fs::create_dir_all(&game_dir).map_err(|e| e.to_string())?;
    write_account_list(&state, &game_dir);
    let session = match account.kind {
        AccountKind::Msa => launch::Session {
            name: account.name.clone(),
            uuid: account.id.clone(),
            token: account.access_token.clone(),
            xuid: account.xuid.clone(),
            user_type: "msa".into(),
        },
        AccountKind::Offline => launch::Session {
            name: account.name.clone(),
            uuid: account.id.clone(),
            token: "0".into(),
            xuid: "0".into(),
            user_type: "legacy".into(),
        },
    };
    let libraries = state.paths.libraries();
    let args = launch::build_args(&launch::ArgInput {
        version: &prepared.version,
        classpath: &prepared.classpath,
        natives_dir: &prepared.natives_dir,
        game_dir: &game_dir,
        assets_root: &prepared.assets_root,
        assets_index: &prepared.assets_index,
        libraries_dir: &libraries,
        session: &session,
        memory_mb: if profile.memory_mb > 0 { profile.memory_mb } else { settings.default_memory_mb },
        extra_jvm_args: &profile.jvm_args,
        resolution: profile.resolution.as_ref().map(|r| (r.width, r.height)),
        server: server.as_deref(),
        launcher_version: &app.package_info().version.to_string(),
    });

    let _ = app.emit("launch-progress", LaunchProgress {
        profile_id: profile_id.clone(),
        progress: Progress { stage: "Starting Minecraft".into(), done: 1, total: 1 },
    });
    let mut child = Command::new(&prepared.java)
        .args(&args)
        .current_dir(&game_dir)
        .stdin(std::process::Stdio::null())
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .spawn()
        .with_context(|| format!("starting {}", prepared.java.display()))
        .map_err(err)?;

    let (kill_tx, kill_rx) = tokio::sync::oneshot::channel();
    *game = Some(RunningGame { profile_id: profile_id.clone(), kill: Some(kill_tx) });
    drop(game);
    let started = now();
    state
        .update_profiles(|list| {
            if let Some(p) = list.iter_mut().find(|p| p.id == profile_id) {
                p.last_played = Some(started);
            }
        })
        .map_err(err)?;
    let _ = app.emit("game-state", json!({ "running": true, "profileId": profile_id }));
    if let Some(w) = app.get_webview_window("main") {
        match settings.on_launch {
            OnLaunch::Keep => {}
            OnLaunch::Minimize => {
                let _ = w.minimize();
            }
            OnLaunch::Hide => {
                let _ = w.hide();
            }
        }
    }
    state.discord.set(
        state.discord_app_id(),
        Presence::Playing {
            details: format!("Playing {}", profile.name),
            state: match &server {
                Some(s) => format!("On {s}"),
                None => format!("Minecraft {}", profile.game_version),
            },
            started,
        },
    );

    // Pump logs in batches so a chatty game can't flood the UI.
    let (line_tx, mut line_rx) = tokio::sync::mpsc::unbounded_channel::<String>();
    let token = session.token.clone();
    for stream in [
        child.stdout.take().map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>),
        child.stderr.take().map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>),
    ]
    .into_iter()
    .flatten()
    {
        let tx = line_tx.clone();
        let token = token.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(stream).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                if tx.send(launch::redact(&line, &token)).is_err() {
                    break;
                }
            }
        });
    }
    drop(line_tx);

    let tail = Arc::new(Mutex::new(VecDeque::<String>::with_capacity(400)));
    let log_app = app.clone();
    let log_tail = tail.clone();
    let pump = tokio::spawn(async move {
        let mut batch = Vec::new();
        let mut tick = tokio::time::interval(Duration::from_millis(100));
        loop {
            tokio::select! {
                line = line_rx.recv() => match line {
                    Some(line) => {
                        {
                            let mut t = log_tail.lock().unwrap();
                            if t.len() == 400 { t.pop_front(); }
                            t.push_back(line.clone());
                        }
                        batch.push(LogLine { level: log_level(&line), line });
                    }
                    None => break,
                },
                _ = tick.tick() => {
                    if !batch.is_empty() {
                        let _ = log_app.emit("game-log", std::mem::take(&mut batch));
                    }
                }
            }
        }
        if !batch.is_empty() {
            let _ = log_app.emit("game-log", batch);
        }
    });

    let exit_app = app.clone();
    let crash_dir = game_dir.clone();
    let loader_name = modrinth::mod_loaders(&profile.loader)[0].to_string();
    tokio::spawn(async move {
        let mut killed = false;
        let status = tokio::select! {
            status = child.wait() => status,
            _ = kill_rx => {
                killed = true;
                let _ = child.kill().await;
                child.wait().await
            }
        };
        let _ = pump.await;
        let code = status.ok().and_then(|s| s.code()).unwrap_or(-1);
        let lines: Vec<String> = tail.lock().unwrap().iter().cloned().collect();
        let state = exit_app.state::<AppState>();
        let played = (now() - started).max(0) as u64;
        let _ = state.update_profiles(|list| {
            if let Some(p) = list.iter_mut().find(|p| p.id == profile_id) {
                p.play_time_secs += played;
            }
        });
        *state.game.lock().await = None;
        state.discord.set(state.discord_app_id(), Presence::Clear);
        restore_window(&exit_app);
        let _ = exit_app.emit("game-state", json!({ "running": false, "profileId": profile_id }));
        // stopping the game from the launcher isn't a crash
        let crash = if killed || code == 0 {
            None
        } else {
            let dir = crash_dir.clone();
            let loader = loader_name.clone();
            tokio::task::spawn_blocking(move || {
                let ctx = crash::Context {
                    crash_report: crash::newest_file(&dir.join("crash-reports"), "crash-", started),
                    fatal_log: crash::newest_file(&dir, "hs_err_pid", started),
                    mods: crash::scan_mods(&dir.join("mods")),
                    loader,
                };
                crash::analyze(&lines, code, Some(&ctx))
            })
            .await
            .ok()
            .flatten()
        };
        let _ = exit_app.emit("game-exit", json!({ "profileId": profile_id, "code": code, "crash": crash }));
    });
    Ok(())
}

#[tauri::command]
async fn kill_game(state: State<'_, AppState>) -> CmdResult<()> {
    if let Some(game) = state.game.lock().await.as_mut() {
        if let Some(kill) = game.kill.take() {
            let _ = kill.send(());
        }
    }
    Ok(())
}

#[tauri::command]
async fn running_game(state: State<'_, AppState>) -> CmdResult<Option<String>> {
    Ok(state.game.lock().await.as_ref().map(|g| g.profile_id.clone()))
}

// ---------------------------------------------------------------- content

#[tauri::command]
async fn modrinth_search(
    state: State<'_, AppState>,
    query: String,
    kind: String,
    game_version: Option<String>,
    loader: Option<String>,
    sort: String,
    offset: u32,
) -> CmdResult<Value> {
    modrinth::search(&state.http, &query, &kind, game_version.as_deref(), loader.as_deref(), &sort, offset)
        .await
        .map_err(err)
}

#[tauri::command]
async fn modrinth_install(state: State<'_, AppState>, profile_id: String, project_id: String, kind: String) -> CmdResult<Vec<String>> {
    let profile = state.profile(&profile_id).map_err(err)?;
    modrinth::install(&state.http, &state.paths, &profile, &project_id, &kind).await.map_err(err)
}

#[tauri::command]
fn content_list(app: AppHandle, state: State<'_, AppState>, profile_id: String, kind: String) -> CmdResult<Vec<modrinth::ContentItem>> {
    let mut items = modrinth::list(&state.paths, &profile_id, &kind).map_err(err)?;
    let profile = state.profile(&profile_id).map_err(err)?;
    let builds = glade_builds(&app, &state.paths);
    for item in &mut items {
        item.locked = glade::locked(&state.paths, &profile, &builds, &kind, &item.file).map(String::from);
        if item.file == glade::FILE && item.meta.is_none() {
            let dir = state.paths.instance(&profile_id).join("mods");
            let version = glade::read_info(&dir.join(glade::FILE)).map(|i| i.version).unwrap_or_default();
            item.meta = Some(modrinth::ContentMeta { title: "Glade".into(), version_number: version, ..Default::default() });
        }
    }
    // Glade first
    items.sort_by_key(|i| i.locked.is_none());
    Ok(items)
}

/// Refuses changes to Glade and what it needs.
fn check_unlocked(app: &AppHandle, state: &AppState, profile_id: &str, kind: &str, file: &str) -> CmdResult<()> {
    let profile = state.profile(profile_id).map_err(err)?;
    match glade::locked(&state.paths, &profile, &glade_builds(app, &state.paths), kind, file) {
        Some(why) => Err(format!("{why}, so it can't be turned off or removed.")),
        None => Ok(()),
    }
}

#[tauri::command]
fn content_toggle(app: AppHandle, state: State<'_, AppState>, profile_id: String, kind: String, file: String, enabled: bool) -> CmdResult<()> {
    if !enabled {
        check_unlocked(&app, &state, &profile_id, &kind, &file)?;
    }
    modrinth::set_enabled(&state.paths, &profile_id, &kind, &file, enabled).map_err(err)
}

#[tauri::command]
fn content_remove(app: AppHandle, state: State<'_, AppState>, profile_id: String, kind: String, file: String) -> CmdResult<()> {
    check_unlocked(&app, &state, &profile_id, &kind, &file)?;
    modrinth::remove(&state.paths, &profile_id, &kind, &file).map_err(err)
}

// ---------------------------------------------------------------- updates

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct UpdateStatus {
    /// A newer launcher, if there is one.
    launcher: Option<LauncherUpdate>,
    /// The Glade mod version just downloaded, if it was updated.
    mod_updated: Option<String>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct LauncherUpdate {
    version: String,
    current: String,
    notes: String,
    page: String,
    /// The launcher can install it itself (installed from the .deb).
    can_install: bool,
}

#[tauri::command]
async fn check_update(app: AppHandle, state: State<'_, AppState>) -> CmdResult<UpdateStatus> {
    let manifest = update::manifest(&state.http).await.map_err(err)?;
    let updated = update::update_mods(&state.http, &state.paths, &glade_builds(&app, &state.paths), &manifest)
        .await
        .unwrap_or_else(|e| {
            eprintln!("Glade mod update failed: {e:#}");
            Vec::new()
        });
    let mod_updated = (!updated.is_empty()).then(|| updated.join(", "));
    let current = app.package_info().version.to_string();
    let launcher = (update::is_newer(&manifest.version, &current) && manifest.deb.is_some()).then(|| LauncherUpdate {
        version: manifest.version.clone(),
        current,
        notes: manifest.notes.clone(),
        page: manifest.page.clone(),
        can_install: update::can_install(),
    });
    Ok(UpdateStatus { launcher, mod_updated })
}

#[derive(Serialize, Clone)]
struct UpdateProgress {
    stage: &'static str,
    done: u64,
    total: u64,
}

/// Downloads and installs the newer launcher, then restarts into it.
#[tauri::command]
async fn install_update(app: AppHandle, state: State<'_, AppState>) -> CmdResult<()> {
    if state.game.lock().await.is_some() {
        return Err("Close Minecraft first; the update restarts the launcher.".into());
    }
    let manifest = update::manifest(&state.http).await.map_err(err)?;
    let deb = manifest.deb.clone().ok_or("This release has no .deb to install.")?;
    let path = update::deb_path(&state.paths, &manifest.version);
    let progress_app = app.clone();
    let last = Mutex::new(Instant::now() - Duration::from_secs(1));
    update::download(&state.http, &deb.url, &deb.sha256, &path, move |done, total| {
        let mut last = last.lock().unwrap();
        if done == total || last.elapsed() > Duration::from_millis(80) {
            *last = Instant::now();
            let _ = progress_app.emit("update-progress", UpdateProgress { stage: "download", done, total: total.max(deb.size) });
        }
    })
    .await
    .map_err(err)?;
    let _ = app.emit("update-progress", UpdateProgress { stage: "install", done: 1, total: 1 });
    update::install_deb(&path).await.map_err(err)?;
    let _ = std::fs::remove_file(&path);
    app.restart();
}

#[tauri::command]
async fn content_updates(state: State<'_, AppState>, profile_id: String) -> CmdResult<Vec<modrinth::Update>> {
    let profile = state.profile(&profile_id).map_err(err)?;
    modrinth::check_updates(&state.http, &state.paths, &profile).await.map_err(err)
}

#[tauri::command]
async fn content_update(state: State<'_, AppState>, profile_id: String, kind: String, file: String) -> CmdResult<()> {
    let profile = state.profile(&profile_id).map_err(err)?;
    modrinth::update(&state.http, &state.paths, &profile, &kind, &file).await.map_err(err)
}

async fn profile_from_mrpack(state: &AppState, pack: &Path) -> anyhow::Result<Profile> {
    let info = modrinth::read_mrpack_info(pack)?;
    let profile = Profile {
        id: uuid::Uuid::new_v4().simple().to_string(),
        name: info.name,
        icon: "chest".into(),
        game_version: info.game_version,
        loader: info.loader,
        loader_version: info.loader_version,
        memory_mb: state.settings().default_memory_mb,
        created: now(),
        ..Default::default()
    };
    let instance = state.paths.instance(&profile.id);
    std::fs::create_dir_all(&instance)?;
    if let Err(e) = modrinth::install_mrpack(&state.http, pack, &instance).await {
        let _ = std::fs::remove_dir_all(&instance);
        return Err(e);
    }
    let saved = profile.clone();
    state.update_profiles(|list| list.push(profile))?;
    Ok(saved)
}

#[tauri::command]
async fn import_mrpack(state: State<'_, AppState>, path: String) -> CmdResult<Profile> {
    profile_from_mrpack(&state, Path::new(&path)).await.map_err(err)
}

#[tauri::command]
async fn install_modpack(state: State<'_, AppState>, project_id: String) -> CmdResult<Profile> {
    let tmp = state.paths.file("cache").join(format!("{}.mrpack", uuid::Uuid::new_v4().simple()));
    let result = async {
        modrinth::download_modpack(&state.http, &project_id, &tmp).await?;
        profile_from_mrpack(&state, &tmp).await
    }
    .await;
    let _ = std::fs::remove_file(&tmp);
    result.map_err(err)
}

// ---------------------------------------------------------------- servers

#[tauri::command]
fn list_servers(state: State<'_, AppState>) -> Vec<Server> {
    store::load(&state.paths.file("servers.json"))
}

#[tauri::command]
fn save_servers(state: State<'_, AppState>, servers: Vec<Server>) -> CmdResult<()> {
    let _guard = state.store_lock.lock().unwrap();
    store::save(&state.paths.file("servers.json"), &servers).map_err(err)
}

#[tauri::command]
async fn ping_server(address: String) -> CmdResult<servers::PingResult> {
    servers::ping(&address).await.map_err(err)
}

// ---------------------------------------------------------------- skins

async fn msa_token(state: &AppState) -> anyhow::Result<String> {
    let account = active_account(state).await?;
    if account.kind != AccountKind::Msa {
        anyhow::bail!("Skins and capes need a Microsoft account.");
    }
    Ok(account.access_token)
}

#[tauri::command]
async fn skin_profile(state: State<'_, AppState>) -> CmdResult<Value> {
    let token = msa_token(&state).await.map_err(err)?;
    let mut profile = skins::profile(&state.http, &token).await.map_err(err)?;
    for key in ["skins", "capes"] {
        if let Some(items) = profile[key].as_array_mut() {
            for item in items {
                if let Some(url) = item["url"].as_str().map(String::from) {
                    item["dataUrl"] = json!(skins::texture_data_url(&state.http, &url).await.ok());
                }
            }
        }
    }
    Ok(profile)
}

#[tauri::command]
fn read_skin_file(path: String) -> CmdResult<String> {
    use base64::Engine;
    let bytes = std::fs::read(&path).map_err(|e| e.to_string())?;
    if !bytes.starts_with(b"\x89PNG") || bytes.len() > 1024 * 1024 {
        return Err("Pick a PNG skin file under 1 MB.".into());
    }
    Ok(format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes)))
}

#[tauri::command]
async fn upload_skin(state: State<'_, AppState>, path: String, slim: bool) -> CmdResult<()> {
    let token = msa_token(&state).await.map_err(err)?;
    let bytes = std::fs::read(&path).map_err(|e| e.to_string())?;
    skins::upload_skin(&state.http, &token, bytes, slim).await.map_err(err)
}

#[tauri::command]
async fn reset_skin(state: State<'_, AppState>) -> CmdResult<()> {
    let token = msa_token(&state).await.map_err(err)?;
    skins::reset_skin(&state.http, &token).await.map_err(err)
}

#[tauri::command]
async fn set_cape(state: State<'_, AppState>, cape_id: Option<String>) -> CmdResult<()> {
    let token = msa_token(&state).await.map_err(err)?;
    skins::set_cape(&state.http, &token, cape_id.as_deref()).await.map_err(err)
}

// ---------------------------------------------------------------- screenshots

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct Screenshot {
    path: String,
    profile_id: String,
    profile_name: String,
    modified: i64,
}

#[tauri::command]
fn list_screenshots(state: State<'_, AppState>) -> Vec<Screenshot> {
    let mut shots = Vec::new();
    for profile in state.profiles() {
        let dir = state.paths.instance(&profile.id).join("screenshots");
        let Ok(entries) = std::fs::read_dir(dir) else { continue };
        for entry in entries.flatten() {
            let path = entry.path();
            if path.extension().and_then(|e| e.to_str()) != Some("png") {
                continue;
            }
            let modified = entry
                .metadata()
                .and_then(|m| m.modified())
                .ok()
                .and_then(|t| t.duration_since(std::time::UNIX_EPOCH).ok())
                .map_or(0, |d| d.as_secs() as i64);
            shots.push(Screenshot {
                path: path.to_string_lossy().into_owned(),
                profile_id: profile.id.clone(),
                profile_name: profile.name.clone(),
                modified,
            });
        }
    }
    shots.sort_by_key(|s| std::cmp::Reverse(s.modified));
    shots
}

fn inside_instances(state: &AppState, path: &str) -> anyhow::Result<PathBuf> {
    let path = std::fs::canonicalize(path)?;
    let root = std::fs::canonicalize(state.paths.instances())?;
    if !path.starts_with(&root) {
        anyhow::bail!("path is outside the launcher's folders");
    }
    Ok(path)
}

#[tauri::command]
fn delete_screenshot(state: State<'_, AppState>, path: String) -> CmdResult<()> {
    let path = inside_instances(&state, &path).map_err(err)?;
    std::fs::remove_file(path).map_err(|e| e.to_string())
}

#[tauri::command]
fn open_screenshot(app: AppHandle, state: State<'_, AppState>, path: String) -> CmdResult<()> {
    let path = inside_instances(&state, &path).map_err(err)?;
    app.opener()
        .open_path(path.to_string_lossy(), None::<&str>)
        .map_err(|e| e.to_string())
}

// ---------------------------------------------------------------- entry

pub fn run() {
    let paths = Paths::new();
    let _ = std::fs::create_dir_all(paths.instances());
    let http = reqwest::Client::builder()
        .user_agent(concat!("Glade/", env!("CARGO_PKG_VERSION"), " (github.com/nafehawan/neptune)"))
        .connect_timeout(Duration::from_secs(15))
        .build()
        .expect("http client");

    tauri::Builder::default()
        .plugin(tauri_plugin_dialog::init())
        .plugin(tauri_plugin_opener::init())
        .manage(AppState {
            paths,
            http,
            store_lock: Mutex::new(()),
            game: tokio::sync::Mutex::new(None),
            discord: Discord::default(),
        })
        .invoke_handler(tauri::generate_handler![
            app_info,
            get_settings,
            save_settings,
            open_url,
            open_folder,
            list_versions,
            loader_versions,
            preset_game_versions,
            list_profiles,
            save_profile,
            delete_profile,
            duplicate_profile,
            create_tuff_profile,
            create_preset_profile,
            list_presets,
            list_accounts,
            account_skin,
            default_skin,
            add_offline_account,
            ms_login_start,
            ms_login_finish,
            remove_account,
            set_active_account,
            launch,
            kill_game,
            running_game,
            modrinth_search,
            modrinth_install,
            content_list,
            content_toggle,
            content_remove,
            content_updates,
            content_update,
            check_update,
            install_update,
            import_mrpack,
            install_modpack,
            list_servers,
            save_servers,
            ping_server,
            skin_profile,
            read_skin_file,
            upload_skin,
            reset_skin,
            set_cape,
            list_screenshots,
            delete_screenshot,
            open_screenshot,
        ])
        .run(tauri::generate_context!())
        .expect("error while running Glade");
}
