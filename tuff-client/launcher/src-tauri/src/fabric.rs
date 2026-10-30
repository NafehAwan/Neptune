//! Fabric loader metadata. The profile JSON Fabric serves is a regular
//! version JSON with `inheritsFrom`, merged on top of vanilla.

use crate::meta::VersionJson;
use crate::paths::Paths;
use anyhow::{anyhow, Result};
use serde::{Deserialize, Serialize};

const META: &str = "https://meta.fabricmc.net/v2";
/// Fabric for Minecraft before 1.14 (1.8.9 and friends), with the same API.
const LEGACY_META: &str = "https://meta.legacyfabric.net/v2";

/// Minecraft before 1.14 runs Fabric through Legacy Fabric.
pub fn is_legacy(game_version: &str) -> bool {
    let mut parts = game_version.split('.');
    parts.next() == Some("1") && parts.next().and_then(|m| m.parse::<u32>().ok()).is_some_and(|m| m < 14)
}

fn meta(game_version: &str) -> &'static str {
    if is_legacy(game_version) {
        LEGACY_META
    } else {
        META
    }
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct LoaderVersion {
    pub version: String,
    pub stable: bool,
}

#[derive(Deserialize)]
struct LoaderEntry {
    loader: LoaderVersion,
}

pub async fn loader_versions(http: &reqwest::Client, game_version: &str) -> Result<Vec<LoaderVersion>> {
    let url = format!("{}/versions/loader/{}", meta(game_version), urlencoding::encode(game_version));
    let entries: Vec<LoaderEntry> = http.get(url).send().await?.error_for_status()?.json().await?;
    Ok(entries.into_iter().map(|e| e.loader).collect())
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct GameVersion {
    pub version: String,
    pub stable: bool,
}

/// Every Minecraft version Fabric supports, newest first, with releases marked
/// stable; the older ones come from Legacy Fabric.
pub async fn game_versions(http: &reqwest::Client) -> Result<Vec<GameVersion>> {
    let mut list: Vec<GameVersion> = http.get(format!("{META}/versions/game")).send().await?.error_for_status()?.json().await?;
    let legacy: Vec<GameVersion> = match http.get(format!("{LEGACY_META}/versions/game")).send().await {
        Ok(r) => r.json().await.unwrap_or_default(),
        Err(_) => Vec::new(),
    };
    for v in legacy.into_iter().filter(|v| is_legacy(&v.version)) {
        if !list.iter().any(|x| x.version == v.version) {
            list.push(v);
        }
    }
    Ok(list)
}

#[allow(dead_code)]
pub async fn latest_stable(http: &reqwest::Client, game_version: &str) -> Result<String> {
    let versions = loader_versions(http, game_version).await?;
    versions
        .iter()
        .find(|v| v.stable)
        .or_else(|| versions.first())
        .map(|v| v.version.clone())
        .ok_or_else(|| anyhow!("Fabric does not support Minecraft {game_version} yet"))
}

pub fn profile_id(game_version: &str, loader: &str) -> String {
    format!("fabric-loader-{loader}-{game_version}")
}

pub async fn profile_json(
    http: &reqwest::Client,
    paths: &Paths,
    game_version: &str,
    loader: &str,
) -> Result<VersionJson> {
    let id = profile_id(game_version, loader);
    let path = paths.versions().join(&id).join(format!("{id}.json"));
    if let Ok(bytes) = std::fs::read(&path) {
        if let Ok(v) = serde_json::from_slice(&bytes) {
            return Ok(v);
        }
    }
    let url = format!(
        "{}/versions/loader/{}/{}/profile/json",
        meta(game_version),
        urlencoding::encode(game_version),
        urlencoding::encode(loader)
    );
    let bytes = http.get(url).send().await?.error_for_status()?.bytes().await?;
    let v: VersionJson = serde_json::from_slice(&bytes)?;
    std::fs::create_dir_all(path.parent().unwrap())?;
    std::fs::write(&path, &bytes)?;
    Ok(v)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn legacy_versions() {
        assert!(is_legacy("1.8.9"));
        assert!(is_legacy("1.8.8"));
        assert!(is_legacy("1.12.2"));
        assert!(is_legacy("1.13.2"));
        assert!(!is_legacy("1.14"));
        assert!(!is_legacy("1.21.11"));
        assert!(!is_legacy("26.3"));
    }
}
