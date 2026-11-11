//! Mojang version metadata: manifest, version JSON, rules and library
//! resolution. Loader profiles (Fabric) use the same JSON shape and are
//! merged on top of the vanilla JSON via `inheritsFrom`.

use crate::paths::Paths;
use anyhow::{anyhow, bail, Context, Result};
use serde::{Deserialize, Serialize};
use sha1::{Digest, Sha1};
use std::collections::{HashMap, HashSet};
use std::time::{Duration, SystemTime};

pub const MANIFEST_URL: &str = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
pub const LIBRARIES_URL: &str = "https://libraries.minecraft.net/";

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct VersionManifest {
    pub latest: Latest,
    pub versions: Vec<ManifestVersion>,
}

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct Latest {
    pub release: String,
    pub snapshot: String,
}

#[derive(Deserialize, Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ManifestVersion {
    pub id: String,
    #[serde(rename = "type")]
    pub kind: String,
    pub url: String,
    #[serde(default)]
    pub release_time: String,
    #[serde(default)]
    pub sha1: Option<String>,
}

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct VersionJson {
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub inherits_from: Option<String>,
    #[serde(rename = "type", default)]
    pub kind: Option<String>,
    #[serde(default)]
    pub main_class: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub minecraft_arguments: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub arguments: Option<Arguments>,
    #[serde(default)]
    pub asset_index: Option<AssetIndexRef>,
    #[serde(default)]
    pub assets: Option<String>,
    #[serde(default)]
    pub downloads: Option<HashMap<String, Download>>,
    #[serde(default)]
    pub libraries: Vec<Library>,
    #[serde(default)]
    pub java_version: Option<JavaVersion>,
}

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
pub struct Arguments {
    #[serde(default)]
    pub game: Vec<Argument>,
    #[serde(default)]
    pub jvm: Vec<Argument>,
}

#[derive(Deserialize, Serialize, Clone, Debug)]
#[serde(untagged)]
pub enum Argument {
    Plain(String),
    Ruled { rules: Vec<Rule>, value: ArgValue },
}

#[derive(Deserialize, Serialize, Clone, Debug)]
#[serde(untagged)]
pub enum ArgValue {
    One(String),
    Many(Vec<String>),
}

impl ArgValue {
    pub fn values(&self) -> Vec<String> {
        match self {
            ArgValue::One(s) => vec![s.clone()],
            ArgValue::Many(v) => v.clone(),
        }
    }
}

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct Rule {
    pub action: String,
    #[serde(default)]
    pub os: Option<OsRule>,
    #[serde(default)]
    pub features: Option<HashMap<String, bool>>,
}

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct OsRule {
    #[serde(default)]
    pub name: Option<String>,
    #[serde(default)]
    pub arch: Option<String>,
}

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
pub struct Library {
    pub name: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub downloads: Option<LibDownloads>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub url: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub rules: Option<Vec<Rule>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub natives: Option<HashMap<String, String>>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub sha1: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub size: Option<u64>,
}

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
pub struct LibDownloads {
    #[serde(default)]
    pub artifact: Option<Artifact>,
    #[serde(default)]
    pub classifiers: Option<HashMap<String, Artifact>>,
}

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct Artifact {
    #[serde(default)]
    pub path: Option<String>,
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
}

#[derive(Deserialize, Serialize, Clone, Debug)]
pub struct Download {
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
}

#[derive(Deserialize, Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct AssetIndexRef {
    pub id: String,
    pub url: String,
    #[serde(default)]
    pub sha1: Option<String>,
    #[serde(default)]
    pub size: Option<u64>,
}

#[derive(Deserialize, Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct JavaVersion {
    pub component: String,
    pub major_version: u32,
}

/// A library file to download and (optionally) put on the classpath.
#[derive(Clone, Debug, PartialEq)]
pub struct LibFile {
    pub path: String,
    pub url: String,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

pub fn os_name() -> &'static str {
    match std::env::consts::OS {
        "macos" => "osx",
        "windows" => "windows",
        _ => "linux",
    }
}

fn is_32bit() -> bool {
    std::env::consts::ARCH == "x86"
}

/// Launcher feature flags referenced by argument rules.
#[derive(Default, Clone, Debug)]
pub struct Features {
    pub custom_resolution: bool,
    pub quick_play_multiplayer: bool,
}

impl Features {
    fn get(&self, key: &str) -> bool {
        match key {
            "has_custom_resolution" => self.custom_resolution,
            "is_quick_play_multiplayer" => self.quick_play_multiplayer,
            _ => false,
        }
    }
}

impl Rule {
    pub fn matches(&self, features: &Features) -> bool {
        if let Some(os) = &self.os {
            if let Some(name) = &os.name {
                if name != os_name() {
                    return false;
                }
            }
            if let Some(arch) = &os.arch {
                let current = if is_32bit() { "x86" } else { std::env::consts::ARCH };
                if arch != current {
                    return false;
                }
            }
        }
        if let Some(wanted) = &self.features {
            if wanted.iter().any(|(k, v)| features.get(k) != *v) {
                return false;
            }
        }
        true
    }
}

/// Mojang rule semantics: start disallowed, the last matching rule wins.
pub fn rules_allow(rules: &[Rule], features: &Features) -> bool {
    if rules.is_empty() {
        return true;
    }
    let mut allowed = false;
    for rule in rules {
        if rule.matches(features) {
            allowed = rule.action == "allow";
        }
    }
    allowed
}

/// `group:artifact:version[:classifier][@ext]` to a repository path.
pub fn maven_path(name: &str) -> Option<String> {
    let (coords, ext) = match name.split_once('@') {
        Some((c, e)) => (c, e),
        None => (name, "jar"),
    };
    let parts: Vec<&str> = coords.split(':').collect();
    if parts.len() < 3 {
        return None;
    }
    let (group, artifact, version) = (parts[0], parts[1], parts[2]);
    let file = match parts.get(3) {
        Some(classifier) => format!("{artifact}-{version}-{classifier}.{ext}"),
        None => format!("{artifact}-{version}.{ext}"),
    };
    Some(format!(
        "{}/{artifact}/{version}/{file}",
        group.replace('.', "/")
    ))
}

impl Library {
    pub fn allowed(&self, features: &Features) -> bool {
        self.rules
            .as_ref()
            .map_or(true, |rules| rules_allow(rules, features))
    }

    /// Identity used to let a loader override a vanilla library:
    /// group:artifact plus classifier, ignoring the version.
    pub fn key(&self) -> String {
        let coords = self.name.split('@').next().unwrap_or(&self.name);
        let parts: Vec<&str> = coords.split(':').collect();
        match parts.len() {
            0..=2 => coords.to_string(),
            3 => format!("{}:{}", parts[0], parts[1]),
            _ => format!("{}:{}:{}", parts[0], parts[1], parts[3]),
        }
    }

    /// The classpath artifact, if this library has one.
    pub fn artifact(&self) -> Option<LibFile> {
        if let Some(downloads) = &self.downloads {
            let a = downloads.artifact.as_ref()?;
            let path = a.path.clone().or_else(|| maven_path(&self.name))?;
            if a.url.is_empty() {
                return None;
            }
            return Some(LibFile {
                path,
                url: a.url.clone(),
                sha1: a.sha1.clone(),
                size: a.size,
            });
        }
        // natives-only libraries (Legacy Fabric's LWJGL 2) have no plain jar
        if self.natives.is_some() {
            return None;
        }
        let path = maven_path(&self.name)?;
        let base = self.url.as_deref().unwrap_or(LIBRARIES_URL);
        Some(LibFile {
            url: format!("{}/{}", base.trim_end_matches('/'), path),
            path,
            sha1: self.sha1.clone(),
            size: self.size,
        })
    }

    /// Classpath entry with no download: Forge and NeoForge list the jars their
    /// installer generates with an empty URL.
    pub fn generated_path(&self) -> Option<String> {
        let a = self.downloads.as_ref()?.artifact.as_ref()?;
        if !a.url.is_empty() {
            return None;
        }
        a.path.clone().or_else(|| maven_path(&self.name))
    }

    /// Pre-1.19 style native jar (extracted to the natives directory).
    pub fn native(&self) -> Option<LibFile> {
        let classifier = self
            .natives
            .as_ref()?
            .get(os_name())?
            .replace("${arch}", if is_32bit() { "32" } else { "64" });
        if let Some(a) = self
            .downloads
            .as_ref()
            .and_then(|d| d.classifiers.as_ref())
            .and_then(|c| c.get(&classifier))
        {
            let path = a
                .path
                .clone()
                .or_else(|| maven_path(&format!("{}:{classifier}", self.name)))?;
            return Some(LibFile {
                path,
                url: a.url.clone(),
                sha1: a.sha1.clone(),
                size: a.size,
            });
        }
        let path = maven_path(&format!("{}:{classifier}", self.name))?;
        let base = self.url.as_deref().unwrap_or(LIBRARIES_URL);
        Some(LibFile {
            url: format!("{}/{}", base.trim_end_matches('/'), path),
            path,
            sha1: None,
            size: None,
        })
    }
}

/// Merges a loader profile (`child`) on top of the version it inherits from.
pub fn merge(child: VersionJson, parent: VersionJson) -> VersionJson {
    let mut out = parent;
    out.id = child.id;
    out.inherits_from = None;
    if child.kind.is_some() {
        out.kind = child.kind;
    }
    if child.main_class.is_some() {
        out.main_class = child.main_class;
    }
    if child.minecraft_arguments.is_some() {
        out.minecraft_arguments = child.minecraft_arguments;
    }
    if let Some(child_args) = child.arguments {
        let mut args = out.arguments.take().unwrap_or_default();
        args.game.extend(child_args.game);
        args.jvm.extend(child_args.jvm);
        out.arguments = Some(args);
    }
    if child.asset_index.is_some() {
        out.asset_index = child.asset_index;
    }
    if child.assets.is_some() {
        out.assets = child.assets;
    }
    if child.java_version.is_some() {
        out.java_version = child.java_version;
    }
    let overridden: HashSet<String> = child.libraries.iter().map(Library::key).collect();
    let mut libraries = child.libraries;
    libraries.extend(
        out.libraries
            .into_iter()
            .filter(|lib| !overridden.contains(&lib.key())),
    );
    out.libraries = libraries;
    out
}

pub fn sha1_hex(bytes: &[u8]) -> String {
    hex::encode(Sha1::digest(bytes))
}

/// Version manifest, cached on disk for 30 minutes and used as an offline
/// fallback when Mojang is unreachable.
pub async fn manifest(http: &reqwest::Client, paths: &Paths, force: bool) -> Result<VersionManifest> {
    let cache = paths.versions().join("version_manifest_v2.json");
    let fresh = std::fs::metadata(&cache)
        .and_then(|m| m.modified())
        .ok()
        .and_then(|t| SystemTime::now().duration_since(t).ok())
        .is_some_and(|age| age < Duration::from_secs(30 * 60));
    if fresh && !force {
        if let Ok(m) = serde_json::from_slice(&std::fs::read(&cache)?) {
            return Ok(m);
        }
    }
    let fetched: Result<VersionManifest> = async {
        let bytes = http
            .get(MANIFEST_URL)
            .send()
            .await?
            .error_for_status()?
            .bytes()
            .await?;
        let m: VersionManifest = serde_json::from_slice(&bytes)?;
        std::fs::create_dir_all(paths.versions())?;
        std::fs::write(&cache, &bytes)?;
        Ok(m)
    }
    .await;
    match fetched {
        Ok(m) => Ok(m),
        Err(e) => std::fs::read(&cache)
            .ok()
            .and_then(|b| serde_json::from_slice(&b).ok())
            .ok_or_else(|| e.context("could not reach Mojang and no cached version list exists")),
    }
}

/// Vanilla version JSON, downloaded once and verified against the manifest.
pub async fn vanilla_json(http: &reqwest::Client, paths: &Paths, id: &str) -> Result<VersionJson> {
    let path = paths.versions().join(id).join(format!("{id}.json"));
    if let Ok(bytes) = std::fs::read(&path) {
        if let Ok(v) = serde_json::from_slice(&bytes) {
            return Ok(v);
        }
    }
    let cached = manifest(http, paths, false).await?;
    let entry = match cached.versions.iter().find(|v| v.id == id) {
        Some(e) => e.clone(),
        None => manifest(http, paths, true)
            .await?
            .versions
            .into_iter()
            .find(|v| v.id == id)
            .ok_or_else(|| anyhow!("Minecraft {id} does not exist"))?,
    };
    let bytes = http
        .get(&entry.url)
        .send()
        .await?
        .error_for_status()?
        .bytes()
        .await
        .with_context(|| format!("downloading version {id}"))?;
    if let Some(expected) = &entry.sha1 {
        if !sha1_hex(&bytes).eq_ignore_ascii_case(expected) {
            bail!("version {id} failed its checksum");
        }
    }
    let v: VersionJson = serde_json::from_slice(&bytes)?;
    std::fs::create_dir_all(path.parent().unwrap())?;
    std::fs::write(&path, &bytes)?;
    Ok(v)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn rule(action: &str, os: Option<&str>, feature: Option<(&str, bool)>) -> Rule {
        Rule {
            action: action.into(),
            os: os.map(|n| OsRule {
                name: Some(n.into()),
                arch: None,
            }),
            features: feature.map(|(k, v)| HashMap::from([(k.to_string(), v)])),
        }
    }

    #[test]
    fn empty_rules_allow() {
        assert!(rules_allow(&[], &Features::default()));
    }

    #[test]
    fn os_rules() {
        let f = Features::default();
        assert!(rules_allow(&[rule("allow", Some(os_name()), None)], &f));
        assert!(!rules_allow(&[rule("allow", Some("not-an-os"), None)], &f));
        // allow everywhere, then disallow on this OS: the last match wins
        assert!(!rules_allow(
            &[rule("allow", None, None), rule("disallow", Some(os_name()), None)],
            &f
        ));
    }

    #[test]
    fn feature_rules() {
        let r = [rule("allow", None, Some(("has_custom_resolution", true)))];
        assert!(!rules_allow(&r, &Features::default()));
        let f = Features {
            custom_resolution: true,
            ..Default::default()
        };
        assert!(rules_allow(&r, &f));
    }

    #[test]
    fn maven_paths() {
        assert_eq!(
            maven_path("net.fabricmc:fabric-loader:0.19.5").unwrap(),
            "net/fabricmc/fabric-loader/0.19.5/fabric-loader-0.19.5.jar"
        );
        assert_eq!(
            maven_path("org.lwjgl:lwjgl:3.3.3:natives-linux").unwrap(),
            "org/lwjgl/lwjgl/3.3.3/lwjgl-3.3.3-natives-linux.jar"
        );
        assert_eq!(
            maven_path("a.b:c:1@zip").unwrap(),
            "a/b/c/1/c-1.zip"
        );
        assert!(maven_path("broken").is_none());
    }

    #[test]
    fn library_keys_keep_classifier() {
        let a = Library { name: "org.lwjgl:lwjgl:3.3.3".into(), ..Default::default() };
        let legacy = Library {
            name: "org.lwjgl.lwjgl:lwjgl-platform:2.9.4+legacyfabric.17".into(),
            url: Some("https://maven.legacyfabric.net/".into()),
            natives: Some(HashMap::from([("linux".to_string(), "natives-linux".to_string())])),
            ..Default::default()
        };
        assert!(legacy.artifact().is_none(), "natives-only library has no plain jar");
        if os_name() == "linux" {
            assert_eq!(
                legacy.native().unwrap().url,
                "https://maven.legacyfabric.net/org/lwjgl/lwjgl/lwjgl-platform/2.9.4+legacyfabric.17/lwjgl-platform-2.9.4+legacyfabric.17-natives-linux.jar"
            );
        }
        let b = Library { name: "org.lwjgl:lwjgl:3.3.3:natives-linux".into(), ..Default::default() };
        assert_ne!(a.key(), b.key());
        assert_eq!(a.key(), "org.lwjgl:lwjgl");
    }

    #[test]
    fn fabric_style_library_artifact() {
        let lib = Library {
            name: "net.fabricmc:intermediary:1.0".into(),
            url: Some("https://maven.fabricmc.net/".into()),
            ..Default::default()
        };
        let a = lib.artifact().unwrap();
        assert_eq!(a.url, "https://maven.fabricmc.net/net/fabricmc/intermediary/1.0/intermediary-1.0.jar");
    }

    #[test]
    fn merge_child_overrides() {
        let parent: VersionJson = serde_json::from_value(serde_json::json!({
            "id": "26.3",
            "mainClass": "net.minecraft.client.main.Main",
            "arguments": { "game": ["--username", "${auth_player_name}"], "jvm": ["-cp", "${classpath}"] },
            "libraries": [
                { "name": "org.ow2.asm:asm:9.6" },
                { "name": "com.mojang:brigadier:1.0" }
            ],
            "javaVersion": { "component": "java-runtime-epsilon", "majorVersion": 25 }
        })).unwrap();
        let child: VersionJson = serde_json::from_value(serde_json::json!({
            "id": "fabric-loader-0.19.5-26.3",
            "inheritsFrom": "26.3",
            "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
            "arguments": { "game": [], "jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "] },
            "libraries": [ { "name": "org.ow2.asm:asm:9.9", "url": "https://maven.fabricmc.net/" } ]
        })).unwrap();
        let merged = merge(child, parent);
        assert_eq!(merged.id, "fabric-loader-0.19.5-26.3");
        assert_eq!(merged.main_class.as_deref(), Some("net.fabricmc.loader.impl.launch.knot.KnotClient"));
        let names: Vec<_> = merged.libraries.iter().map(|l| l.name.as_str()).collect();
        assert_eq!(names, vec!["org.ow2.asm:asm:9.9", "com.mojang:brigadier:1.0"]);
        assert_eq!(merged.arguments.unwrap().jvm.len(), 3);
        assert_eq!(merged.java_version.unwrap().major_version, 25);
    }

    #[test]
    fn parses_ruled_arguments() {
        let args: Arguments = serde_json::from_value(serde_json::json!({
            "game": [
                "--demo-less",
                { "rules": [{ "action": "allow", "features": { "has_custom_resolution": true } }],
                  "value": ["--width", "${resolution_width}"] },
                { "rules": [{ "action": "allow", "os": { "name": "osx" } }], "value": "-XstartOnFirstThread" }
            ]
        })).unwrap();
        assert_eq!(args.game.len(), 3);
    }

    #[test]
    fn sha1_known_value() {
        assert_eq!(sha1_hex(b"abc"), "a9993e364706816aba3e25717850c26c9cd0d89d");
    }
}
