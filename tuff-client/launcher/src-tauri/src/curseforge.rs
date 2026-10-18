//! CurseForge: search and install mods, resource packs and shaders into a
//! profile, with required dependencies. Results are returned in the same shape
//! as Modrinth's search so the Mods page can show either source.
//!
//! CurseForge needs an API key for third-party launchers (free, from
//! console.curseforge.com). It comes from Settings, or is baked in at build
//! time with GLADE_CF_API_KEY.

use crate::download::{download_all, DlTask};
use crate::models::{Loader, Profile};
use crate::modrinth::{self, ContentMeta};
use crate::paths::Paths;
use anyhow::{anyhow, bail, Result};
use serde_json::{json, Value};
use std::collections::{HashSet, VecDeque};

const API: &str = "https://api.curseforge.com/v1";
const MINECRAFT: u32 = 432;
const FABRIC: u32 = 4;
/// Ids CurseForge stores in our content index get this prefix, so they never mix with Modrinth's.
pub const PREFIX: &str = "cf:";

pub fn api_key(from_settings: Option<&str>) -> Result<String> {
    from_settings
        .map(str::trim)
        .filter(|k| !k.is_empty())
        .map(String::from)
        // CI passes an empty value when the secret isn't set
        .or_else(|| option_env!("GLADE_CF_API_KEY").filter(|k| !k.trim().is_empty()).map(String::from))
        .ok_or_else(|| anyhow!("CurseForge needs an API key. Add yours in Settings → Mod sources."))
}

fn class_id(kind: &str) -> Result<u32> {
    Ok(match kind {
        "mod" => 6,
        "resourcepack" => 12,
        "shader" => 6552,
        "modpack" => bail!("CurseForge modpacks aren't supported yet. Use Modrinth modpacks or import an .mrpack."),
        _ => bail!("unsupported content type '{kind}'"),
    })
}

fn sort_field(sort: &str) -> u32 {
    match sort {
        "downloads" => 6,
        "updated" => 3,
        "newest" => 11,
        "follows" => 12,
        _ => 2, // popularity, the closest to relevance
    }
}

async fn get(http: &reqwest::Client, key: &str, url: String) -> Result<Value> {
    let response = http.get(url).header("x-api-key", key).header("Accept", "application/json").send().await?;
    if response.status() == reqwest::StatusCode::FORBIDDEN || response.status() == reqwest::StatusCode::UNAUTHORIZED {
        bail!("CurseForge didn't accept the API key. Check it in Settings → Mod sources.");
    }
    Ok(response.error_for_status()?.json().await?)
}

/// Searches CurseForge and answers in Modrinth's search format.
#[allow(clippy::too_many_arguments)]
pub async fn search(
    http: &reqwest::Client,
    key: &str,
    query: &str,
    kind: &str,
    game_version: Option<&str>,
    loader: Option<&str>,
    sort: &str,
    offset: u32,
) -> Result<Value> {
    let mut url = format!(
        "{API}/mods/search?gameId={MINECRAFT}&classId={}&searchFilter={}&sortField={}&sortOrder=desc&index={offset}&pageSize=20",
        class_id(kind)?,
        urlencoding::encode(query),
        sort_field(sort),
    );
    if let Some(v) = game_version {
        url.push_str(&format!("&gameVersion={}", urlencoding::encode(v)));
    }
    if kind == "mod" && loader == Some("fabric") {
        url.push_str(&format!("&modLoaderType={FABRIC}"));
    }
    let body = get(http, key, url).await?;
    let hits: Vec<Value> = body["data"]
        .as_array()
        .into_iter()
        .flatten()
        .map(|m| {
            json!({
                "project_id": format!("{PREFIX}{}", m["id"]),
                "slug": m["slug"],
                "title": m["name"],
                "description": m["summary"],
                "author": m["authors"][0]["name"].as_str().unwrap_or(""),
                "icon_url": m["logo"]["thumbnailUrl"],
                "downloads": m["downloadCount"].as_f64().unwrap_or(0.0) as u64,
                "follows": m["thumbsUpCount"].as_u64().unwrap_or(0),
                "categories": m["categories"].as_array().into_iter().flatten().filter_map(|c| c["name"].as_str()).take(3).collect::<Vec<_>>(),
                "project_type": kind,
            })
        })
        .collect();
    let total = body["pagination"]["totalCount"].as_u64().unwrap_or(hits.len() as u64).min(10_000);
    Ok(json!({ "hits": hits, "total_hits": total, "offset": offset, "limit": 20 }))
}

fn numeric(id: &str) -> Result<u64> {
    id.trim_start_matches(PREFIX).parse().map_err(|_| anyhow!("not a CurseForge project id: {id}"))
}

/// Newest file for the profile's version (and Fabric, for mods), preferring releases.
async fn best_file(http: &reqwest::Client, key: &str, mod_id: u64, kind: &str, profile: &Profile) -> Result<Option<Value>> {
    let mut url = format!("{API}/mods/{mod_id}/files?gameVersion={}&pageSize=50", urlencoding::encode(&profile.game_version));
    if kind == "mod" && profile.loader == Loader::Fabric {
        url.push_str(&format!("&modLoaderType={FABRIC}"));
    }
    let body = get(http, key, url).await?;
    let mut files: Vec<Value> = body["data"].as_array().cloned().unwrap_or_default();
    files.sort_by(|a, b| b["fileDate"].as_str().unwrap_or("").cmp(a["fileDate"].as_str().unwrap_or("")));
    Ok(files.iter().find(|f| f["releaseType"] == 1).or_else(|| files.first()).cloned())
}

/// Installs a CurseForge project (and, for mods, its required dependencies)
/// into the profile. Returns the titles that were installed.
pub async fn install(http: &reqwest::Client, key: &str, paths: &Paths, profile: &Profile, project_id: &str, kind: &str) -> Result<Vec<String>> {
    if kind == "mod" && profile.loader == Loader::Vanilla {
        bail!("Mods need a Fabric profile. Switch this profile to Fabric or create a Glade profile.");
    }
    class_id(kind)?;
    let mut index = modrinth::load_index(paths, &profile.id);
    let mut have: HashSet<String> = index.values().map(|m| m.project_id.clone()).collect();
    let first = numeric(project_id)?;
    let mut queue = VecDeque::from([(first, kind.to_string())]);
    let mut installed = Vec::new();

    while let Some((mod_id, kind)) = queue.pop_front() {
        let pid = format!("{PREFIX}{mod_id}");
        if !have.insert(pid.clone()) {
            continue;
        }
        let project = get(http, key, format!("{API}/mods/{mod_id}")).await?["data"].clone();
        let title = project["name"].as_str().unwrap_or("CurseForge project").to_string();
        let Some(file) = best_file(http, key, mod_id, &kind, profile).await? else {
            if mod_id == first {
                bail!("No file of {title} supports Minecraft {}.", profile.game_version);
            }
            continue;
        };
        let Some(url) = file["downloadUrl"].as_str() else {
            if mod_id == first {
                bail!("{title}'s author only allows downloads from curseforge.com, so it can't be installed here.");
            }
            continue;
        };
        let name = file["fileName"].as_str().unwrap_or_default();
        if name.is_empty() || name.contains(['/', '\\']) || name.starts_with('.') {
            bail!("unsafe file name '{name}'");
        }
        let sha1 = file["hashes"].as_array().into_iter().flatten().find(|h| h["algo"] == 1).and_then(|h| h["value"].as_str()).map(String::from);
        let dir = paths.instance(&profile.id).join(modrinth::folder_for(&kind)?);
        download_all(
            http,
            vec![DlTask { url: url.to_string(), path: dir.join(name), sha1, size: file["fileLength"].as_u64() }],
            1,
            |_, _| {},
        )
        .await?;
        index.insert(
            name.to_string(),
            ContentMeta {
                project_id: pid,
                version_id: file["id"].to_string(),
                title: title.clone(),
                icon_url: project["logo"]["thumbnailUrl"].as_str().map(String::from),
                version_number: file["displayName"].as_str().unwrap_or(name).to_string(),
            },
        );
        installed.push(title);
        if kind == "mod" {
            for dep in file["dependencies"].as_array().into_iter().flatten() {
                // 3 = required dependency
                if dep["relationType"] == 3 {
                    if let Some(id) = dep["modId"].as_u64() {
                        queue.push_back((id, "mod".into()));
                    }
                }
            }
        }
    }
    modrinth::save_index(paths, &profile.id, &index)?;
    Ok(installed)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn ids_and_classes() {
        assert_eq!(numeric("cf:238222").unwrap(), 238222);
        assert!(numeric("sodium").is_err());
        assert_eq!(class_id("resourcepack").unwrap(), 12);
        assert!(class_id("modpack").is_err());
        assert!(api_key(Some("  ")).is_err() || option_env!("GLADE_CF_API_KEY").is_some_and(|k| !k.trim().is_empty()));
        assert_eq!(api_key(Some(" abc ")).unwrap(), "abc");
    }
}
