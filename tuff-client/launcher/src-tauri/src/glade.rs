//! The Glade mod. It goes into every Fabric profile on a Minecraft version it
//! is built for, and is checked before each launch:
//!
//! - the newest Glade build (bundled with the launcher, or downloaded by the
//!   updater) is copied in when the profile's copy is missing or different;
//! - turned-off, renamed or extra copies of Glade are cleaned up;
//! - Fabric API (which Glade needs) is installed or turned back on;
//! - two copies of the same mod (a common crash) are fixed by turning the
//!   older one off.
//!
//! Players can't remove or turn Glade off; the launcher puts it back.

use crate::download::file_sha1;
use crate::models::{Loader, Profile};
use crate::modrinth;
use crate::paths::Paths;
use anyhow::{Context, Result};
use serde::Serialize;
use serde_json::Value;
use std::cmp::Ordering;
use std::collections::BTreeMap;
use std::io::Read;
use std::path::{Path, PathBuf};

/// Glade's file name inside a profile's mods folder.
pub const FILE: &str = "glade.jar";
/// Names Glade had before.
const OLD_FILES: &[&str] = &["tuff-client.jar"];
/// Glade's Fabric mod id.
pub const MOD_ID: &str = "tuff";
pub const FABRIC_API: &str = "fabric-api";
pub const LEGACY_FABRIC_API: &str = "legacy-fabric-api";

/// The Fabric API a Glade build needs (its mod id and Modrinth slug), if any.
/// The 1.8 build needs none, so it also runs where no API is published.
pub fn api_of(build: &Build) -> Option<&str> {
    build.info.api.as_deref()
}

/// What a mod jar says about itself in `fabric.mod.json`.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct ModInfo {
    pub id: String,
    pub version: String,
    /// The `depends.minecraft` range, like `~26.3`.
    pub minecraft: Option<String>,
    /// The `depends.fabricloader` range, like `>=0.19.5`.
    pub loader: Option<String>,
    /// The Fabric API it depends on (`fabric-api` or `legacy-fabric-api`).
    pub api: Option<String>,
}

pub fn read_info(jar: &Path) -> Option<ModInfo> {
    let file = std::fs::File::open(jar).ok()?;
    let mut zip = zip::ZipArchive::new(file).ok()?;
    let mut entry = zip.by_name("fabric.mod.json").ok()?;
    let mut text = String::new();
    entry.read_to_string(&mut text).ok()?;
    parse_info(&text)
}

fn parse_info(text: &str) -> Option<ModInfo> {
    // some mods put raw newlines inside strings, which strict JSON refuses
    let json: Value = serde_json::from_str(text).or_else(|_| serde_json::from_str(&text.replace(['\n', '\r'], " "))).ok()?;
    let range = |key: &str| match &json["depends"][key] {
        Value::String(s) => Some(s.clone()),
        // a list means any of them
        Value::Array(a) => Some(a.iter().filter_map(Value::as_str).collect::<Vec<_>>().join(" || ")).filter(|s| !s.is_empty()),
        _ => None,
    };
    Some(ModInfo {
        id: json["id"].as_str()?.to_string(),
        version: json["version"].as_str().unwrap_or_default().to_string(),
        minecraft: range("minecraft"),
        loader: range("fabricloader"),
        api: [FABRIC_API, LEGACY_FABRIC_API].into_iter().find(|id| !json["depends"][*id].is_null()).map(String::from),
    })
}

/// Compares versions number by number ("1.10.0" > "1.9.2"); text after `-` or
/// `+` is ignored.
pub fn compare_versions(a: &str, b: &str) -> Ordering {
    let nums = |s: &str| -> Vec<u64> {
        s.split(['-', '+']).next().unwrap_or_default().split('.').map(|p| p.trim().parse().unwrap_or(0)).collect()
    };
    let (a, b) = (nums(a), nums(b));
    for i in 0..a.len().max(b.len()) {
        match a.get(i).unwrap_or(&0).cmp(b.get(i).unwrap_or(&0)) {
            Ordering::Equal => continue,
            other => return other,
        }
    }
    Ordering::Equal
}

/// Whether a Fabric version range (`~26.3`, `26.3`, `>=26.3`, `*`) accepts the
/// Minecraft version.
pub fn accepts(range: &str, version: &str) -> bool {
    range.split("||").any(|part| {
        part.split_whitespace().all(|r| {
            if r == "*" {
                true
            } else if let Some(base) = r.strip_prefix('~') {
                version == base || version.starts_with(&format!("{base}."))
            } else if let Some(min) = r.strip_prefix(">=") {
                plain(version) && compare_versions(version, min) != Ordering::Less
            } else if let Some(max) = r.strip_prefix("<=") {
                plain(version) && compare_versions(version, max) != Ordering::Greater
            } else if let Some(min) = r.strip_prefix('>') {
                plain(version) && compare_versions(version, min) == Ordering::Greater
            } else if let Some(max) = r.strip_prefix('<') {
                plain(version) && compare_versions(version, max) == Ordering::Less
            } else {
                version == r.trim_start_matches('=')
            }
        })
    })
}

/// Release versions only (no snapshots or pre-releases).
fn plain(version: &str) -> bool {
    version.chars().all(|c| c.is_ascii_digit() || c == '.')
}

/// One Glade build: a jar for some Minecraft versions.
#[derive(Clone, Debug)]
pub struct Build {
    pub path: PathBuf,
    pub info: ModInfo,
}

impl Build {
    pub fn runs_on(&self, game_version: &str) -> bool {
        self.info.minecraft.as_deref().is_some_and(|r| accepts(r, game_version))
    }
}

/// Every Glade build the launcher has: one per group of Minecraft versions.
#[derive(Clone, Debug, Default)]
pub struct Builds(pub Vec<Build>);

impl Builds {
    /// The newest build that runs on this Minecraft version.
    pub fn for_version(&self, game_version: &str) -> Option<&Build> {
        self.0.iter().filter(|b| b.runs_on(game_version)).max_by(|a, b| compare_versions(&a.info.version, &b.info.version))
    }

    /// Every Minecraft version range Glade runs on, as one Fabric range.
    pub fn ranges(&self) -> String {
        let mut list: Vec<String> = self.0.iter().filter_map(|b| b.info.minecraft.clone()).collect();
        list.sort();
        list.dedup();
        list.join(" || ")
    }

    /// The build for this range, if there is one (to compare with an update).
    pub fn for_range(&self, range: &str) -> Option<&Build> {
        self.0.iter().filter(|b| b.info.minecraft.as_deref() == Some(range)).max_by(|a, b| compare_versions(&a.info.version, &b.info.version))
    }
}

/// Where the updater keeps Glade builds newer than the bundled ones.
pub fn downloads_dir(paths: &Paths) -> PathBuf {
    paths.file("glade").join("mods")
}

fn jars_in(dir: &Path) -> Vec<PathBuf> {
    std::fs::read_dir(dir)
        .map(|entries| entries.flatten().map(|e| e.path()).filter(|p| p.extension().is_some_and(|x| x == "jar")).collect())
        .unwrap_or_default()
}

/// All Glade builds: bundled with the launcher (a folder of jars, or the old
/// single jar) and downloaded by the updater.
pub fn find_builds(bundled: &[PathBuf], paths: &Paths) -> Builds {
    let mut files: Vec<PathBuf> = Vec::new();
    for b in bundled {
        if b.is_dir() {
            files.extend(jars_in(b));
        } else if b.is_file() {
            files.push(b.clone());
        }
    }
    files.extend(jars_in(&downloads_dir(paths)));
    files.push(paths.file("glade").join("glade-mod.jar")); // from launcher 1.1.0
    Builds(
        files
            .into_iter()
            .filter_map(|path| {
                let info = read_info(&path).filter(|i| i.id == MOD_ID)?;
                Some(Build { path, info })
            })
            .collect(),
    )
}

/// Deletes Glade builds the updater downloaded (and launcher 1.1.0's
/// glade-mod.jar) once a build at least as new exists for the same Minecraft
/// versions. Bundled builds are never touched. Returns the builds left.
pub fn prune_old(paths: &Paths, builds: Builds) -> Builds {
    let downloads = downloads_dir(paths);
    let old_single = paths.file("glade").join("glade-mod.jar");
    let removable = |b: &Build| b.path.starts_with(&downloads) || b.path == old_single;
    let mut keep = Vec::new();
    for b in &builds.0 {
        let range = b.info.minecraft.as_deref();
        // the build to keep for this range: the newest, a bundled one on a tie
        let best = builds
            .0
            .iter()
            .filter(|o| o.info.minecraft.as_deref() == range)
            .max_by(|x, y| compare_versions(&x.info.version, &y.info.version).then(removable(y).cmp(&removable(x))))
            .expect("b itself is in the list");
        if std::ptr::eq(best, b) || !removable(b) {
            keep.push(b.clone());
        } else {
            let _ = std::fs::remove_file(&b.path);
        }
    }
    Builds(keep)
}

/// Glade runs on Fabric, on the Minecraft versions it has a build for.
pub fn applies(profile: &Profile, builds: &Builds) -> bool {
    profile.loader == Loader::Fabric && builds.for_version(&profile.game_version).is_some()
}

#[derive(Serialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct Report {
    /// Glade is in the profile and up to date.
    pub installed: bool,
    pub version: Option<String>,
    /// Why Glade isn't in this profile, when it isn't.
    pub reason: Option<String>,
    /// Things the check fixed, to show the player.
    pub fixed: Vec<String>,
}

struct ModFile {
    file: String,
    enabled: bool,
    info: ModInfo,
    modified: std::time::SystemTime,
}

fn scan(dir: &Path) -> Vec<ModFile> {
    let Ok(entries) = std::fs::read_dir(dir) else { return Vec::new() };
    entries
        .flatten()
        .filter_map(|e| {
            let file = e.file_name().to_string_lossy().into_owned();
            let enabled = file.ends_with(".jar");
            if !enabled && !file.ends_with(".jar.disabled") {
                return None;
            }
            let info = read_info(&e.path())?;
            let modified = e.metadata().and_then(|m| m.modified()).unwrap_or(std::time::UNIX_EPOCH);
            Some(ModFile { file, enabled, info, modified })
        })
        .collect()
}

fn remove_glade_files(dir: &Path, keep: Option<&str>) -> Vec<String> {
    let mut removed = Vec::new();
    for m in scan(dir) {
        if m.info.id == MOD_ID && Some(m.file.as_str()) != keep && std::fs::remove_file(dir.join(&m.file)).is_ok() {
            removed.push(m.file);
        }
    }
    for name in OLD_FILES.iter().chain([&FILE]) {
        let off = dir.join(format!("{name}.disabled"));
        if off.exists() && std::fs::remove_file(&off).is_ok() {
            removed.push(format!("{name}.disabled"));
        }
    }
    removed
}

/// Puts Glade (and what it needs) into the profile, or takes it out of a
/// profile it can't run in, so it never breaks a game.
#[allow(dead_code)] // used by the tests and the CI start-up test
pub async fn ensure(http: &reqwest::Client, paths: &Paths, profile: &Profile, builds: &Builds) -> Result<Report> {
    ensure_with(http, paths, profile, builds, &|_, _| {}).await
}

/// Like [ensure], reporting the download when Fabric API has to be installed.
pub async fn ensure_with(
    http: &reqwest::Client,
    paths: &Paths,
    profile: &Profile,
    builds: &Builds,
    on_progress: &(dyn Fn(&str, crate::download::DlStatus) + Send + Sync),
) -> Result<Report> {
    let mods = paths.instance(&profile.id).join("mods");
    let mut report = Report::default();
    let build = builds.for_version(&profile.game_version);
    let Some(build) = build.filter(|_| profile.loader == Loader::Fabric) else {
        // Glade built for another version would stop the game from starting.
        if mods.is_dir() {
            for f in remove_glade_files(&mods, None) {
                report.fixed.push(format!("Removed {f}, which is built for another Minecraft version"));
            }
        }
        report.reason = Some(match build {
            _ if profile.loader != Loader::Fabric => "Glade's in-game mod runs on Fabric profiles.".into(),
            _ if builds.0.is_empty() => "This copy of the launcher has no Glade mod build.".into(),
            _ => format!("Glade's in-game mod isn't available for Minecraft {} yet.", profile.game_version),
        });
        return Ok(report);
    };

    std::fs::create_dir_all(&mods).context("creating the mods folder")?;
    for f in remove_glade_files(&mods, Some(FILE)) {
        report.fixed.push(format!("Removed an extra Glade copy ({f})"));
    }
    let target = mods.join(FILE);
    let same = target.is_file() && file_sha1(&target).ok() == file_sha1(&build.path).ok();
    if !same {
        let tmp = mods.join(format!("{FILE}.part"));
        std::fs::copy(&build.path, &tmp).context("copying Glade into the profile")?;
        std::fs::rename(&tmp, &target).context("copying Glade into the profile")?;
        report.fixed.push(format!("Installed Glade {}", build.info.version));
    }

    // Fabric API: turn it back on, or install it.
    modrinth::prune_index(paths, &profile.id);
    let found = scan(&mods);
    if let Some(api) = api_of(build).filter(|api| !found.iter().any(|m| m.enabled && m.info.id == *api)) {
        if let Some(off) = found.iter().find(|m| m.info.id == api) {
            let name = off.file.trim_end_matches(".disabled").to_string();
            modrinth::set_enabled(paths, &profile.id, "mod", &name, true)?;
            report.fixed.push("Turned Fabric API back on (Glade needs it)".into());
        } else {
            modrinth::install_with(http, paths, profile, api, "mod", on_progress)
                .await
                .context("installing Fabric API, which Glade needs")?;
            report.fixed.push("Installed Fabric API (Glade needs it)".into());
        }
    }

    // Two copies of one mod stop Fabric from starting: keep the newest.
    let mut by_id: BTreeMap<String, Vec<ModFile>> = BTreeMap::new();
    for m in scan(&mods).into_iter().filter(|m| m.enabled) {
        by_id.entry(m.info.id.clone()).or_default().push(m);
    }
    for (_, mut copies) in by_id.into_iter().filter(|(_, c)| c.len() > 1) {
        copies.sort_by(|a, b| compare_versions(&b.info.version, &a.info.version).then(b.modified.cmp(&a.modified)));
        for old in copies.iter().skip(1) {
            modrinth::set_enabled(paths, &profile.id, "mod", &old.file, false)?;
            report.fixed.push(format!("Turned off {} (an older copy of {})", old.file, copies[0].file));
        }
    }

    report.installed = true;
    report.version = Some(build.info.version.clone());
    Ok(report)
}

/// Whether the player may turn off or remove this mod file.
pub fn locked(paths: &Paths, profile: &Profile, builds: &Builds, kind: &str, file: &str) -> Option<&'static str> {
    if kind != "mod" || profile.loader != Loader::Fabric {
        return None;
    }
    let build = builds.for_version(&profile.game_version)?;
    if file == FILE {
        return Some("Glade is part of this profile");
    }
    let dir = paths.instance(&profile.id).join("mods");
    let path = [dir.join(file), dir.join(format!("{file}.disabled"))].into_iter().find(|p| p.is_file())?;
    (Some(read_info(&path)?.id.as_str()) == api_of(build)).then_some("Glade needs Fabric API")
}

/// The lowest Fabric Loader version this Glade build accepts.
pub fn min_loader(build: &Build) -> Option<String> {
    build.info.loader.as_deref()?.split_whitespace().find_map(|r| r.strip_prefix(">=")).map(String::from)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_fabric_mod_json() {
        let info = parse_info(r#"{"schemaVersion":1,"id":"tuff","version":"1.2.0","depends":{"fabricloader":">=0.19.5","minecraft":"~26.3"}}"#).unwrap();
        assert_eq!(info.id, "tuff");
        assert_eq!(info.version, "1.2.0");
        assert_eq!(info.minecraft.as_deref(), Some("~26.3"));
        assert_eq!(info.loader.as_deref(), Some(">=0.19.5"));
        let arr = parse_info(r#"{"id":"x","depends":{"minecraft":["1.21","1.21.1"]}}"#).unwrap();
        assert_eq!(arr.minecraft.as_deref(), Some("1.21 || 1.21.1"));
        assert_eq!(info.api, None);
        let modern = parse_info(r#"{"id":"tuff","depends":{"minecraft":"~26.3","fabric-api":"*"}}"#).unwrap();
        assert_eq!(modern.api.as_deref(), Some(FABRIC_API));
        let legacy = parse_info(r#"{"id":"tuff","depends":{"minecraft":"1.8.9","legacy-fabric-api":"*"}}"#).unwrap();
        assert_eq!(legacy.api.as_deref(), Some(LEGACY_FABRIC_API));
    }

    #[test]
    fn version_ranges() {
        assert!(accepts("~26.3", "26.3"));
        assert!(accepts("~26.3", "26.3.1"));
        assert!(!accepts("~26.3", "26.30"));
        assert!(!accepts("~26.3", "26.4"));
        assert!(!accepts("~26.3", "1.21.11"));
        assert!(accepts("26.3", "26.3"));
        assert!(accepts(">=26.3 <26.5", "26.4"));
        assert!(!accepts(">=26.3 <26.5", "26.5"));
        assert!(accepts("*", "1.8.9"));
        assert!(accepts("1.21 || ~26.3", "26.3.2"));
        assert!(accepts(">=1.21 <=1.21.1", "1.21"));
        assert!(accepts(">=1.21 <=1.21.1", "1.21.1"));
        assert!(!accepts(">=1.21 <=1.21.1", "1.21.2"));
        assert!(accepts(">=1.21.6 <=1.21.8", "1.21.7"));
        assert!(!accepts(">=1.21.6 <=1.21.8", "1.21.9"));
    }

    #[test]
    fn picks_the_build_for_each_version() {
        let dir = std::env::temp_dir().join(format!("glade-builds-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&dir).unwrap();
        jar(&dir, "a.jar", r#"{"id":"tuff","version":"1.2.0","depends":{"minecraft":"~26.3"}}"#);
        jar(&dir, "b.jar", r#"{"id":"tuff","version":"1.2.0","depends":{"minecraft":">=1.21 <=1.21.1"}}"#);
        jar(&dir, "c.jar", r#"{"id":"tuff","version":"1.3.0","depends":{"minecraft":">=1.21 <=1.21.1"}}"#);
        jar(&dir, "other.jar", r#"{"id":"sodium","version":"9","depends":{"minecraft":"*"}}"#);
        let paths = Paths { root: dir.join("home") };
        let builds = find_builds(&[dir.clone()], &paths);
        assert_eq!(builds.0.len(), 3);
        assert_eq!(builds.for_version("1.21").unwrap().info.version, "1.3.0");
        assert_eq!(builds.for_version("26.3").unwrap().info.version, "1.2.0");
        assert!(builds.for_version("1.21.5").is_none());
        assert_eq!(builds.ranges(), ">=1.21 <=1.21.1 || ~26.3");
        let _ = std::fs::remove_dir_all(dir);
    }

    #[test]
    fn compares_versions() {
        assert_eq!(compare_versions("1.10.0", "1.9.2"), Ordering::Greater);
        assert_eq!(compare_versions("1.0", "1.0.0"), Ordering::Equal);
        assert_eq!(compare_versions("1.0.0+26.3", "1.0.1"), Ordering::Less);
    }

    fn jar(dir: &Path, name: &str, json: &str) {
        let file = std::fs::File::create(dir.join(name)).unwrap();
        let mut zip = zip::ZipWriter::new(file);
        zip.start_file("fabric.mod.json", zip::write::SimpleFileOptions::default()).unwrap();
        std::io::Write::write_all(&mut zip, json.as_bytes()).unwrap();
        zip.finish().unwrap();
    }

    #[tokio::test]
    async fn installs_glade_and_fixes_the_mods_folder() {
        let root = std::env::temp_dir().join(format!("glade-ensure-{}", uuid::Uuid::new_v4()));
        let paths = Paths { root: root.clone() };
        let profile = Profile { id: "p".into(), game_version: "26.3".into(), loader: Loader::Fabric, ..Default::default() };
        let mods = paths.instance("p").join("mods");
        std::fs::create_dir_all(&mods).unwrap();
        let src = root.join("src");
        std::fs::create_dir_all(&src).unwrap();
        jar(&src, "glade.jar", r#"{"id":"tuff","version":"1.1.0","depends":{"minecraft":"~26.3","fabric-api":"*"}}"#);
        // an old, turned-off copy, Fabric API turned off, and a mod twice
        jar(&mods, "tuff-client.jar.disabled", r#"{"id":"tuff","version":"1.0.0"}"#);
        jar(&mods, "fabric-api-0.1.jar.disabled", r#"{"id":"fabric-api","version":"0.1"}"#);
        jar(&mods, "sodium-0.6.jar", r#"{"id":"sodium","version":"0.6.0"}"#);
        jar(&mods, "sodium-0.7.jar", r#"{"id":"sodium","version":"0.7.0"}"#);

        let builds = find_builds(&[src.join("glade.jar")], &paths);
        assert!(builds.for_version("26.3").is_some());
        let report = ensure(&reqwest::Client::new(), &paths, &profile, &builds).await.unwrap();
        assert!(report.installed, "{report:?}");
        assert!(mods.join(FILE).is_file());
        assert!(!mods.join("tuff-client.jar.disabled").exists());
        assert!(mods.join("fabric-api-0.1.jar").is_file());
        assert!(mods.join("sodium-0.7.jar").is_file());
        assert!(mods.join("sodium-0.6.jar.disabled").is_file());
        assert_eq!(locked(&paths, &profile, &builds, "mod", FILE), Some("Glade is part of this profile"));
        assert!(locked(&paths, &profile, &builds, "mod", "fabric-api-0.1.jar").is_some());
        assert!(locked(&paths, &profile, &builds, "mod", "sodium-0.7.jar").is_none());

        // a second check changes nothing
        let again = ensure(&reqwest::Client::new(), &paths, &profile, &builds).await.unwrap();
        assert!(again.fixed.is_empty(), "{:?}", again.fixed);

        // switching the profile to another version takes Glade out
        let other = Profile { game_version: "1.21.11".into(), ..profile.clone() };
        let r = ensure(&reqwest::Client::new(), &paths, &other, &builds).await.unwrap();
        assert!(!r.installed && r.reason.is_some());
        assert!(!mods.join(FILE).exists());
        let _ = std::fs::remove_dir_all(root);
    }

    #[tokio::test]
    async fn legacy_build_needs_no_api() {
        let root = std::env::temp_dir().join(format!("glade-legacy-{}", uuid::Uuid::new_v4()));
        let paths = Paths { root: root.clone() };
        let profile = Profile { id: "p".into(), game_version: "1.8.8".into(), loader: Loader::Fabric, ..Default::default() };
        let src = root.join("src");
        std::fs::create_dir_all(&src).unwrap();
        jar(&src, "glade-1.8.jar", r#"{"id":"tuff","version":"1.2.0","depends":{"minecraft":">=1.8.8 <=1.8.9"}}"#);
        let builds = find_builds(&[src.join("glade-1.8.jar")], &paths);
        // nothing to download, so this works offline
        let report = ensure(&reqwest::Client::new(), &paths, &profile, &builds).await.unwrap();
        assert!(report.installed, "{report:?}");
        assert!(paths.instance("p").join("mods").join(FILE).is_file());
        assert!(!report.fixed.iter().any(|f| f.contains("API")), "{:?}", report.fixed);
        let _ = std::fs::remove_dir_all(root);
    }

    #[test]
    fn prunes_outdated_downloads() {
        let root = std::env::temp_dir().join(format!("glade-prune-{}", uuid::Uuid::new_v4()));
        let paths = Paths { root: root.clone() };
        let bundled = root.join("bundled");
        let downloads = downloads_dir(&paths);
        std::fs::create_dir_all(&bundled).unwrap();
        std::fs::create_dir_all(&downloads).unwrap();
        jar(&bundled, "glade-26.3.jar", r#"{"id":"tuff","version":"1.3.1","depends":{"minecraft":"~26.3"}}"#);
        jar(&bundled, "glade-1.21.11.jar", r#"{"id":"tuff","version":"1.3.1","depends":{"minecraft":"1.21.11"}}"#);
        // older than the bundled build: goes
        jar(&downloads, "glade-26.3.jar", r#"{"id":"tuff","version":"1.3.0","depends":{"minecraft":"~26.3"}}"#);
        jar(&paths.file("glade"), "glade-mod.jar", r#"{"id":"tuff","version":"1.1.0","depends":{"minecraft":"~26.3"}}"#);
        // newer than the bundled build: stays
        jar(&downloads, "glade-1.21.11.jar", r#"{"id":"tuff","version":"1.4.0","depends":{"minecraft":"1.21.11"}}"#);
        let builds = prune_old(&paths, find_builds(&[bundled.clone()], &paths));
        assert!(!downloads.join("glade-26.3.jar").exists());
        assert!(!paths.file("glade").join("glade-mod.jar").exists());
        assert!(downloads.join("glade-1.21.11.jar").exists());
        assert!(bundled.join("glade-1.21.11.jar").exists(), "bundled builds are never deleted");
        assert_eq!(builds.for_version("1.21.11").unwrap().info.version, "1.4.0");
        assert_eq!(builds.for_version("26.3").unwrap().info.version, "1.3.1");
        let _ = std::fs::remove_dir_all(root);
    }
}
