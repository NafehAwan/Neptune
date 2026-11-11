//! Mod loaders besides plain Fabric.
//!
//! - **Quilt** serves the same kind of profile JSON as Fabric (`inheritsFrom`
//!   vanilla), so it installs the same way.
//! - **Forge** and **NeoForge** ship an installer jar. Glade reads its
//!   `version.json` and `install_profile.json`, downloads the libraries and runs
//!   the installer's processors itself (patching the game jar), the same steps
//!   the official installer does, without needing the vanilla launcher.

use crate::download::{download_all, DlTask};
use crate::fabric::{self, LoaderVersion};
use crate::meta::{maven_path, Library, VersionJson};
use crate::models::Loader;
use crate::paths::Paths;
use anyhow::{anyhow, bail, Context, Result};
use serde::Deserialize;
use serde_json::Value;
use std::collections::HashMap;
use std::io::Read;
use std::path::{Path, PathBuf};

const QUILT_META: &str = "https://meta.quiltmc.org/v3";
const FORGE_MAVEN: &str = "https://maven.minecraftforge.net/net/minecraftforge/forge";
const FORGE_FILES: &str = "https://files.minecraftforge.net/net/minecraftforge/forge";
const NEOFORGE_MAVEN: &str = "https://maven.neoforged.net/releases/net/neoforged/neoforge";
const NEOFORGE_VERSIONS: &str = "https://maven.neoforged.net/api/maven/versions/releases/net/neoforged/neoforge";

pub fn display_name(loader: &Loader) -> &'static str {
    match loader {
        Loader::Vanilla => "Vanilla",
        Loader::Fabric => "Fabric",
        Loader::Quilt => "Quilt",
        Loader::Forge => "Forge",
        Loader::NeoForge => "NeoForge",
    }
}

/// Loader versions for a Minecraft version, newest first.
pub async fn versions(http: &reqwest::Client, loader: &Loader, game_version: &str) -> Result<Vec<LoaderVersion>> {
    match loader {
        Loader::Vanilla => Ok(vec![]),
        Loader::Fabric => fabric::loader_versions(http, game_version).await,
        Loader::Quilt => {
            #[derive(Deserialize)]
            struct Entry {
                loader: QuiltLoader,
            }
            #[derive(Deserialize)]
            struct QuiltLoader {
                version: String,
            }
            let url = format!("{QUILT_META}/versions/loader/{}", urlencoding::encode(game_version));
            let entries: Vec<Entry> = http.get(url).send().await?.error_for_status()?.json().await?;
            Ok(entries
                .into_iter()
                .map(|e| LoaderVersion { stable: is_stable(&e.loader.version), version: e.loader.version })
                .collect())
        }
        Loader::Forge => {
            let all: HashMap<String, Vec<String>> =
                http.get(format!("{FORGE_FILES}/maven-metadata.json")).send().await?.error_for_status()?.json().await?;
            let promos: Value = match http.get(format!("{FORGE_FILES}/promotions_slim.json")).send().await {
                Ok(r) => r.json().await.unwrap_or_default(),
                Err(_) => Value::Null,
            };
            let recommended = promos["promos"][format!("{game_version}-recommended")].as_str().map(String::from);
            let prefix = format!("{game_version}-");
            let mut list: Vec<LoaderVersion> = all
                .get(game_version)
                .into_iter()
                .flatten()
                .rev()
                .filter_map(|full| full.strip_prefix(&prefix))
                // Forge has no beta flag; builds before the recommended one count as stable too
                .map(|v| LoaderVersion { version: v.to_string(), stable: true })
                .collect();
            // the recommended build goes first, so "latest stable" picks it
            if let Some(r) = recommended {
                if let Some(i) = list.iter().position(|v| v.version == r) {
                    let rec = list.remove(i);
                    list.insert(0, rec);
                }
            }
            Ok(list)
        }
        Loader::NeoForge => {
            #[derive(Deserialize)]
            struct List {
                versions: Vec<String>,
            }
            let Some(prefix) = neoforge_prefix(game_version) else { return Ok(vec![]) };
            let list: List = http.get(NEOFORGE_VERSIONS).send().await?.error_for_status()?.json().await?;
            Ok(list
                .versions
                .into_iter()
                .rev()
                .filter(|v| v.starts_with(&prefix))
                .map(|v| LoaderVersion { stable: is_stable(&v), version: v })
                .collect())
        }
    }
}

fn is_stable(version: &str) -> bool {
    let v = version.to_ascii_lowercase();
    !(v.contains("beta") || v.contains("alpha") || v.contains("pre") || v.contains("rc") || v.contains("snapshot"))
}

/// NeoForge numbers its builds after the Minecraft version: 1.21.1 is 21.1.x,
/// 1.21 is 21.0.x, and the year-based 26.1 is 26.1.0.x.
pub fn neoforge_prefix(game_version: &str) -> Option<String> {
    let digits = |s: &str| !s.is_empty() && s.chars().all(|c| c.is_ascii_digit());
    if let Some(rest) = game_version.strip_prefix("1.") {
        let mut parts = rest.split('.');
        let (major, minor) = (parts.next()?, parts.next().unwrap_or("0"));
        if !digits(major) || !digits(minor) || parts.next().is_some() {
            return None;
        }
        return Some(format!("{major}.{minor}."));
    }
    let parts: Vec<&str> = game_version.split('.').collect();
    if parts.len() < 2 || parts.len() > 3 || !parts.iter().all(|p| digits(p)) {
        return None;
    }
    Some(format!("{}.{}.{}.", parts[0], parts[1], parts.get(2).unwrap_or(&"0")))
}

/// The newest stable loader version (or the newest at all).
pub async fn latest(http: &reqwest::Client, loader: &Loader, game_version: &str) -> Result<String> {
    let list = versions(http, loader, game_version).await?;
    list.iter()
        .find(|v| v.stable)
        .or_else(|| list.first())
        .map(|v| v.version.clone())
        .ok_or_else(|| anyhow!("{} doesn't support Minecraft {game_version} yet", display_name(loader)))
}

/// The loader's version JSON, to merge on top of vanilla.
pub async fn child_json(http: &reqwest::Client, paths: &Paths, loader: &Loader, game_version: &str, version: &str) -> Result<VersionJson> {
    match loader {
        Loader::Vanilla => bail!("vanilla has no loader profile"),
        Loader::Fabric => fabric::profile_json(http, paths, game_version, version).await,
        Loader::Quilt => quilt_json(http, paths, game_version, version).await,
        Loader::Forge | Loader::NeoForge => {
            let dir = ensure_installer(http, paths, loader, game_version, version).await?;
            Ok(serde_json::from_slice(&std::fs::read(dir.join("version.json"))?)?)
        }
    }
}

async fn quilt_json(http: &reqwest::Client, paths: &Paths, game_version: &str, loader: &str) -> Result<VersionJson> {
    let id = format!("quilt-loader-{loader}-{game_version}");
    let path = paths.versions().join(&id).join(format!("{id}.json"));
    if let Ok(v) = std::fs::read(&path).map_err(anyhow::Error::from).and_then(|b| Ok(serde_json::from_slice(&b)?)) {
        return Ok(v);
    }
    let url = format!(
        "{QUILT_META}/versions/loader/{}/{}/profile/json",
        urlencoding::encode(game_version),
        urlencoding::encode(loader)
    );
    let bytes = http.get(url).send().await?.error_for_status()?.bytes().await?;
    let v: VersionJson = serde_json::from_slice(&bytes)?;
    std::fs::create_dir_all(path.parent().unwrap())?;
    std::fs::write(&path, &bytes)?;
    Ok(v)
}

// ---------------------------------------------------------------- Forge and NeoForge

fn installer_url(loader: &Loader, game_version: &str, version: &str) -> String {
    match loader {
        Loader::NeoForge => format!("{NEOFORGE_MAVEN}/{version}/neoforge-{version}-installer.jar"),
        _ => {
            let full = format!("{game_version}-{version}");
            format!("{FORGE_MAVEN}/{full}/forge-{full}-installer.jar")
        }
    }
}

fn install_dir(paths: &Paths, loader: &Loader, game_version: &str, version: &str) -> PathBuf {
    let name = match loader {
        Loader::NeoForge => format!("neoforge-{version}"),
        _ => format!("forge-{game_version}-{version}"),
    };
    paths.root.join("loaders").join(name)
}

/// Downloads the installer and unpacks what Glade needs from it: the version
/// JSON, the install profile and the libraries bundled under `maven/`.
async fn ensure_installer(http: &reqwest::Client, paths: &Paths, loader: &Loader, game_version: &str, version: &str) -> Result<PathBuf> {
    let dir = install_dir(paths, loader, game_version, version);
    if dir.join("version.json").is_file() {
        return Ok(dir);
    }
    if version.contains(['/', '\\']) || version.starts_with('.') {
        bail!("bad loader version '{version}'");
    }
    std::fs::create_dir_all(&dir)?;
    let installer = dir.join("installer.jar");
    download_all(
        http,
        vec![DlTask { url: installer_url(loader, game_version, version), path: installer.clone(), sha1: None, size: None }],
        1,
        |_, _| {},
    )
    .await
    .with_context(|| format!("downloading the {} {version} installer", display_name(loader)))?;

    let mut zip = zip::ZipArchive::new(std::fs::File::open(&installer)?)?;
    let profile: Value = serde_json::from_str(&read_entry(&mut zip, "install_profile.json")?)?;
    let libraries = paths.libraries();
    // libraries the installer carries with it
    for i in 0..zip.len() {
        let mut entry = zip.by_index(i)?;
        let Some(name) = entry.enclosed_name() else { continue };
        let Ok(rel) = name.strip_prefix("maven") else { continue };
        if entry.is_dir() || rel.as_os_str().is_empty() {
            continue;
        }
        let out = libraries.join(rel);
        if !out.exists() {
            std::fs::create_dir_all(out.parent().unwrap())?;
            std::io::copy(&mut entry, &mut std::fs::File::create(&out)?)?;
        }
    }

    if let Some(info) = profile.get("versionInfo") {
        // Old Forge (1.12.2 and earlier): the version JSON is inside the install
        // profile and the Forge jar sits in the installer.
        let file = profile["install"]["filePath"].as_str().ok_or_else(|| anyhow!("installer has no Forge jar"))?;
        let coords = profile["install"]["path"].as_str().ok_or_else(|| anyhow!("installer has no Forge path"))?;
        let out = libraries.join(maven_path(coords).ok_or_else(|| anyhow!("bad Forge path {coords}"))?);
        std::fs::create_dir_all(out.parent().unwrap())?;
        let mut jar = zip.by_name(file)?;
        std::io::copy(&mut jar, &mut std::fs::File::create(&out)?)?;
        std::fs::write(dir.join("version.json"), serde_json::to_vec(info)?)?;
        std::fs::write(dir.join(".installed"), b"")?;
    } else {
        let json_path = profile["json"].as_str().unwrap_or("/version.json").trim_start_matches('/');
        let version_json = read_entry(&mut zip, json_path)?;
        std::fs::write(dir.join("install_profile.json"), serde_json::to_vec(&profile)?)?;
        // written last: its presence means the installer was unpacked completely
        std::fs::write(dir.join("version.json"), version_json)?;
    }
    Ok(dir)
}

fn read_entry(zip: &mut zip::ZipArchive<std::fs::File>, name: &str) -> Result<String> {
    let mut s = String::new();
    zip.by_name(name).with_context(|| format!("installer has no {name}"))?.read_to_string(&mut s)?;
    Ok(s)
}

#[derive(Deserialize)]
struct InstallProfile {
    #[serde(default)]
    data: HashMap<String, SidedValue>,
    #[serde(default)]
    processors: Vec<Processor>,
    #[serde(default)]
    libraries: Vec<Library>,
}

#[derive(Deserialize)]
struct SidedValue {
    client: String,
}

#[derive(Deserialize)]
struct Processor {
    jar: String,
    #[serde(default)]
    classpath: Vec<String>,
    #[serde(default)]
    args: Vec<String>,
    #[serde(default)]
    sides: Option<Vec<String>>,
}

/// Runs the installer's processors once (they patch the game jar and write
/// the result into the libraries folder). Needs the vanilla client jar and Java.
#[allow(clippy::too_many_arguments)]
pub async fn finish_install(
    http: &reqwest::Client,
    paths: &Paths,
    loader: &Loader,
    game_version: &str,
    version: &str,
    client_jar: &Path,
    java: &Path,
    concurrency: usize,
    progress: &(dyn Fn(&str, u64, u64) + Send + Sync),
) -> Result<()> {
    let dir = install_dir(paths, loader, game_version, version);
    if dir.join(".installed").exists() {
        return Ok(());
    }
    let profile: InstallProfile = serde_json::from_slice(&std::fs::read(dir.join("install_profile.json"))?)?;
    let libraries = paths.libraries();
    let tasks: Vec<DlTask> = profile
        .libraries
        .iter()
        .filter_map(|l| l.artifact())
        .map(|a| DlTask { url: a.url, path: libraries.join(&a.path), sha1: a.sha1, size: a.size })
        .collect();
    let name = display_name(loader);
    download_all(http, tasks, concurrency, |d, t| progress(&format!("Downloading {name}"), d, t)).await?;

    let installer = dir.join("installer.jar");
    let lib = |coords: &str| -> Result<PathBuf> {
        Ok(libraries.join(maven_path(coords).ok_or_else(|| anyhow!("bad library name {coords}"))?))
    };
    let mut vars: HashMap<String, String> = HashMap::from([
        ("SIDE".into(), "client".into()),
        ("MINECRAFT_JAR".into(), client_jar.to_string_lossy().into_owned()),
        ("MINECRAFT_VERSION".into(), game_version.into()),
        ("ROOT".into(), paths.root.to_string_lossy().into_owned()),
        ("INSTALLER".into(), installer.to_string_lossy().into_owned()),
        ("LIBRARY_DIR".into(), libraries.to_string_lossy().into_owned()),
    ]);
    let mut zip = zip::ZipArchive::new(std::fs::File::open(&installer)?)?;
    for (key, value) in &profile.data {
        let v = &value.client;
        let resolved = if let Some(coords) = v.strip_prefix('[').and_then(|s| s.strip_suffix(']')) {
            lib(coords)?.to_string_lossy().into_owned()
        } else if let Some(literal) = v.strip_prefix('\'').and_then(|s| s.strip_suffix('\'')) {
            literal.to_string()
        } else if let Some(inner) = v.strip_prefix('/') {
            // a file inside the installer, such as the binary patches
            let out = dir.join("data").join(inner);
            if !out.exists() {
                std::fs::create_dir_all(out.parent().unwrap())?;
                std::io::copy(&mut zip.by_name(inner)?, &mut std::fs::File::create(&out)?)?;
            }
            out.to_string_lossy().into_owned()
        } else {
            v.clone()
        };
        vars.insert(key.clone(), resolved);
    }

    let steps: Vec<&Processor> = profile
        .processors
        .iter()
        .filter(|p| p.sides.as_ref().map_or(true, |s| s.iter().any(|x| x == "client")))
        .collect();
    let sep = if cfg!(windows) { ";" } else { ":" };
    for (i, step) in steps.iter().enumerate() {
        progress(&format!("Installing {name}"), i as u64, steps.len() as u64);
        let jar = lib(&step.jar)?;
        let main = main_class(&jar)?;
        let mut cp = vec![jar.to_string_lossy().into_owned()];
        for c in &step.classpath {
            cp.push(lib(c)?.to_string_lossy().into_owned());
        }
        let args: Vec<String> = step
            .args
            .iter()
            .map(|a| match a.strip_prefix('[').and_then(|s| s.strip_suffix(']')) {
                Some(coords) => lib(coords).map(|p| p.to_string_lossy().into_owned()),
                None => Ok(fill(a, &vars)),
            })
            .collect::<Result<_>>()?;
        let out = tokio::process::Command::new(java)
            .arg("-cp")
            .arg(cp.join(sep))
            .arg(&main)
            .args(&args)
            .current_dir(&dir)
            .output()
            .await
            .with_context(|| format!("running {name} installer step {}", step.jar))?;
        if !out.status.success() {
            let log = String::from_utf8_lossy(&out.stderr);
            let tail: Vec<&str> = log.lines().rev().take(8).collect();
            bail!(
                "{name} installer step {} failed: {}",
                step.jar,
                tail.into_iter().rev().collect::<Vec<_>>().join(" | ")
            );
        }
    }
    progress(&format!("Installing {name}"), steps.len() as u64, steps.len() as u64);
    std::fs::write(dir.join(".installed"), b"")?;
    Ok(())
}

/// Replaces `{KEY}` tokens with installer data.
fn fill(arg: &str, vars: &HashMap<String, String>) -> String {
    let mut out = String::new();
    let mut rest = arg;
    while let Some(start) = rest.find('{') {
        out.push_str(&rest[..start]);
        match rest[start + 1..].find('}') {
            Some(end) => {
                let key = &rest[start + 1..start + 1 + end];
                match vars.get(key) {
                    Some(v) => out.push_str(v),
                    None => out.push_str(&rest[start..start + end + 2]),
                }
                rest = &rest[start + end + 2..];
            }
            None => {
                out.push_str(&rest[start..]);
                rest = "";
            }
        }
    }
    out.push_str(rest);
    out
}

fn main_class(jar: &Path) -> Result<String> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(jar).with_context(|| format!("missing {}", jar.display()))?)?;
    let manifest = read_entry(&mut zip, "META-INF/MANIFEST.MF")?;
    manifest
        .lines()
        .find_map(|l| l.strip_prefix("Main-Class:"))
        .map(|s| s.trim().to_string())
        .ok_or_else(|| anyhow!("{} has no main class", jar.display()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn neoforge_numbers() {
        assert_eq!(neoforge_prefix("1.21.1").as_deref(), Some("21.1."));
        assert_eq!(neoforge_prefix("1.21").as_deref(), Some("21.0."));
        assert_eq!(neoforge_prefix("26.1").as_deref(), Some("26.1.0."));
        assert_eq!(neoforge_prefix("26.1.2").as_deref(), Some("26.1.2."));
        assert_eq!(neoforge_prefix("25w14a"), None);
        assert_eq!(neoforge_prefix("1.21.5-pre1"), None);
    }

    #[test]
    fn stability() {
        assert!(is_stable("21.1.77"));
        assert!(!is_stable("21.5.0-beta"));
        assert!(!is_stable("0.29.0-beta.3"));
    }

    #[test]
    fn processor_args() {
        let vars = HashMap::from([("SIDE".to_string(), "client".to_string()), ("MAPPINGS".to_string(), "/l/m.txt".to_string())]);
        assert_eq!(fill("{SIDE}", &vars), "client");
        assert_eq!(fill("--map={MAPPINGS}", &vars), "--map=/l/m.txt");
        assert_eq!(fill("{UNKNOWN}", &vars), "{UNKNOWN}");
        assert_eq!(fill("plain", &vars), "plain");
    }

    #[test]
    fn installer_urls() {
        assert_eq!(
            installer_url(&Loader::Forge, "1.20.1", "47.3.0"),
            "https://maven.minecraftforge.net/net/minecraftforge/forge/1.20.1-47.3.0/forge-1.20.1-47.3.0-installer.jar"
        );
        assert_eq!(
            installer_url(&Loader::NeoForge, "1.21.1", "21.1.77"),
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/21.1.77/neoforge-21.1.77-installer.jar"
        );
    }
}
