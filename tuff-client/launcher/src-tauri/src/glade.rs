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

/// What a mod jar says about itself in `fabric.mod.json`.
#[derive(Clone, Debug, Default, PartialEq)]
pub struct ModInfo {
    pub id: String,
    pub version: String,
    /// The `depends.minecraft` range, like `~26.3`.
    pub minecraft: Option<String>,
    /// The `depends.fabricloader` range, like `>=0.19.5`.
    pub loader: Option<String>,
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
        Value::Array(a) => a.iter().filter_map(Value::as_str).next().map(String::from),
        _ => None,
    };
    Some(ModInfo {
        id: json["id"].as_str()?.to_string(),
        version: json["version"].as_str().unwrap_or_default().to_string(),
        minecraft: range("minecraft"),
        loader: range("fabricloader"),
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

/// The Glade build the launcher will use.
#[derive(Clone, Debug)]
pub struct Build {
    pub path: PathBuf,
    pub info: ModInfo,
}

impl Build {
    pub fn runs_on(&self, game_version: &str) -> bool {
        accepts(self.info.minecraft.as_deref().unwrap_or(crate::TUFF_MC_VERSION), game_version)
    }
}

/// Where the updater keeps a newer Glade build than the bundled one.
pub fn downloaded_path(paths: &Paths) -> PathBuf {
    paths.file("glade").join("glade-mod.jar")
}

/// The newest of the bundled and the downloaded Glade builds.
pub fn newest_build(bundled: Option<PathBuf>, paths: &Paths) -> Option<Build> {
    [bundled, Some(downloaded_path(paths))]
        .into_iter()
        .flatten()
        .filter_map(|path| {
            let info = read_info(&path).filter(|i| i.id == MOD_ID)?;
            Some(Build { path, info })
        })
        .max_by(|a, b| compare_versions(&a.info.version, &b.info.version))
}

/// Glade runs on Fabric, on the Minecraft versions its build supports.
pub fn applies(profile: &Profile, build: Option<&Build>) -> bool {
    profile.loader == Loader::Fabric && build.is_some_and(|b| b.runs_on(&profile.game_version))
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
pub async fn ensure(http: &reqwest::Client, paths: &Paths, profile: &Profile, build: Option<&Build>) -> Result<Report> {
    let mods = paths.instance(&profile.id).join("mods");
    let mut report = Report::default();
    let Some(build) = build.filter(|_| applies(profile, build)) else {
        // Glade built for another version would stop the game from starting.
        if mods.is_dir() {
            for f in remove_glade_files(&mods, None) {
                report.fixed.push(format!("Removed {f}, which is built for another Minecraft version"));
            }
        }
        report.reason = Some(match build {
            _ if profile.loader != Loader::Fabric => "Glade's in-game mod runs on Fabric profiles.".into(),
            Some(b) => format!(
                "Glade's in-game mod is built for Minecraft {}; this profile is {}.",
                b.info.minecraft.as_deref().unwrap_or(crate::TUFF_MC_VERSION).trim_start_matches(['~', '=']),
                profile.game_version
            ),
            None => "This copy of the launcher has no Glade mod build.".into(),
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
    if !found.iter().any(|m| m.enabled && m.info.id == FABRIC_API) {
        if let Some(off) = found.iter().find(|m| m.info.id == FABRIC_API) {
            let name = off.file.trim_end_matches(".disabled").to_string();
            modrinth::set_enabled(paths, &profile.id, "mod", &name, true)?;
            report.fixed.push("Turned Fabric API back on (Glade needs it)".into());
        } else {
            modrinth::install(http, paths, profile, FABRIC_API, "mod")
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
pub fn locked(paths: &Paths, profile: &Profile, build: Option<&Build>, kind: &str, file: &str) -> Option<&'static str> {
    if kind != "mod" || !applies(profile, build) {
        return None;
    }
    if file == FILE {
        return Some("Glade is part of this profile");
    }
    let dir = paths.instance(&profile.id).join("mods");
    let path = [dir.join(file), dir.join(format!("{file}.disabled"))].into_iter().find(|p| p.is_file())?;
    (read_info(&path)?.id == FABRIC_API).then_some("Glade needs Fabric API")
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
        assert_eq!(arr.minecraft.as_deref(), Some("1.21"));
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
        jar(&src, "glade.jar", r#"{"id":"tuff","version":"1.1.0","depends":{"minecraft":"~26.3"}}"#);
        // an old, turned-off copy, Fabric API turned off, and a mod twice
        jar(&mods, "tuff-client.jar.disabled", r#"{"id":"tuff","version":"1.0.0"}"#);
        jar(&mods, "fabric-api-0.1.jar.disabled", r#"{"id":"fabric-api","version":"0.1"}"#);
        jar(&mods, "sodium-0.6.jar", r#"{"id":"sodium","version":"0.6.0"}"#);
        jar(&mods, "sodium-0.7.jar", r#"{"id":"sodium","version":"0.7.0"}"#);

        let build = newest_build(Some(src.join("glade.jar")), &paths).unwrap();
        let report = ensure(&reqwest::Client::new(), &paths, &profile, Some(&build)).await.unwrap();
        assert!(report.installed, "{report:?}");
        assert!(mods.join(FILE).is_file());
        assert!(!mods.join("tuff-client.jar.disabled").exists());
        assert!(mods.join("fabric-api-0.1.jar").is_file());
        assert!(mods.join("sodium-0.7.jar").is_file());
        assert!(mods.join("sodium-0.6.jar.disabled").is_file());
        assert_eq!(locked(&paths, &profile, Some(&build), "mod", FILE), Some("Glade is part of this profile"));
        assert!(locked(&paths, &profile, Some(&build), "mod", "fabric-api-0.1.jar").is_some());
        assert!(locked(&paths, &profile, Some(&build), "mod", "sodium-0.7.jar").is_none());

        // a second check changes nothing
        let again = ensure(&reqwest::Client::new(), &paths, &profile, Some(&build)).await.unwrap();
        assert!(again.fixed.is_empty(), "{:?}", again.fixed);

        // switching the profile to another version takes Glade out
        let other = Profile { game_version: "1.21.11".into(), ..profile.clone() };
        let r = ensure(&reqwest::Client::new(), &paths, &other, Some(&build)).await.unwrap();
        assert!(!r.installed && r.reason.is_some());
        assert!(!mods.join(FILE).exists());
        let _ = std::fs::remove_dir_all(root);
    }
}
