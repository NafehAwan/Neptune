//! Modrinth: search, install with required dependencies, updates and
//! `.mrpack` modpack import. Installed files are tracked per profile in
//! `.tuff/content.json` so the UI can show names, icons and updates.

use crate::download::{download_all, file_sha1, DlTask};
use crate::models::{Loader, Profile};
use crate::paths::Paths;
use crate::store;
use anyhow::{anyhow, bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::collections::{BTreeMap, HashSet, VecDeque};
use std::io::Read;
use std::path::{Component, Path, PathBuf};

const API: &str = "https://api.modrinth.com/v2";

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct ContentMeta {
    pub project_id: String,
    pub version_id: String,
    pub title: String,
    pub icon_url: Option<String>,
    pub version_number: String,
}

/// filename (without `.disabled`) -> metadata
pub type ContentIndex = BTreeMap<String, ContentMeta>;

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct ContentItem {
    pub file: String,
    pub kind: String,
    pub enabled: bool,
    pub size: u64,
    pub meta: Option<ContentMeta>,
}

pub fn folder_for(kind: &str) -> Result<&'static str> {
    Ok(match kind {
        "mod" => "mods",
        "resourcepack" => "resourcepacks",
        "shader" => "shaderpacks",
        "datapack" => "datapacks",
        _ => bail!("unsupported content type '{kind}'"),
    })
}

fn index_path(paths: &Paths, profile_id: &str) -> PathBuf {
    paths.instance(profile_id).join(".tuff").join("content.json")
}

pub fn load_index(paths: &Paths, profile_id: &str) -> ContentIndex {
    store::load(&index_path(paths, profile_id))
}

pub fn save_index(paths: &Paths, profile_id: &str, index: &ContentIndex) -> Result<()> {
    store::save(&index_path(paths, profile_id), index)
}

pub async fn search(
    http: &reqwest::Client,
    query: &str,
    kind: &str,
    game_version: Option<&str>,
    loader: Option<&str>,
    sort: &str,
    offset: u32,
) -> Result<Value> {
    let mut facets = vec![vec![format!("project_type:{kind}")]];
    if let Some(v) = game_version {
        facets.push(vec![format!("versions:{v}")]);
    }
    if let (Some(l), "mod" | "modpack") = (loader, kind) {
        facets.push(vec![format!("categories:{l}")]);
    }
    let url = format!(
        "{API}/search?query={}&facets={}&index={}&offset={offset}&limit=20",
        urlencoding::encode(query),
        urlencoding::encode(&serde_json::to_string(&facets)?),
        urlencoding::encode(sort),
    );
    Ok(http.get(url).send().await?.error_for_status()?.json().await?)
}

pub async fn project(http: &reqwest::Client, id: &str) -> Result<Value> {
    Ok(http
        .get(format!("{API}/project/{}", urlencoding::encode(id)))
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?)
}

fn loaders_for(kind: &str, profile: &Profile) -> Option<Vec<&'static str>> {
    match kind {
        "mod" => Some(vec![match profile.loader {
            Loader::Fabric => "fabric",
            Loader::Vanilla => "minecraft",
        }]),
        "shader" => Some(vec!["iris", "optifine"]),
        "resourcepack" => Some(vec!["minecraft"]),
        "datapack" => Some(vec!["datapack"]),
        _ => None,
    }
}

/// Newest compatible version, preferring releases over betas.
async fn best_version(http: &reqwest::Client, project_id: &str, kind: &str, profile: &Profile) -> Result<Option<Value>> {
    let mut url = format!(
        "{API}/project/{}/version?game_versions={}",
        urlencoding::encode(project_id),
        urlencoding::encode(&json!([profile.game_version]).to_string())
    );
    if let Some(loaders) = loaders_for(kind, profile) {
        url.push_str(&format!("&loaders={}", urlencoding::encode(&json!(loaders).to_string())));
    }
    let versions: Vec<Value> = http.get(url).send().await?.error_for_status()?.json().await?;
    Ok(versions
        .iter()
        .find(|v| v["version_type"] == "release")
        .or_else(|| versions.first())
        .cloned())
}

fn primary_file(version: &Value) -> Result<&Value> {
    let files = version["files"].as_array().ok_or_else(|| anyhow!("version has no files"))?;
    files
        .iter()
        .find(|f| f["primary"].as_bool() == Some(true))
        .or_else(|| files.first())
        .ok_or_else(|| anyhow!("version has no files"))
}

/// Only plain file names are accepted for downloaded content.
fn safe_file_name(name: &str) -> Result<&str> {
    if name.is_empty() || name.contains('/') || name.contains('\\') || name.starts_with('.') {
        bail!("unsafe file name '{name}'");
    }
    Ok(name)
}

/// Installs a project (and, for mods, its required dependencies).
/// Returns the titles that were installed.
pub async fn install(
    http: &reqwest::Client,
    paths: &Paths,
    profile: &Profile,
    project_id: &str,
    kind: &str,
) -> Result<Vec<String>> {
    if kind == "mod" && profile.loader == Loader::Vanilla {
        bail!("Mods need a Fabric profile. Switch this profile to Fabric or create a Glade profile.");
    }
    if kind == "modpack" {
        bail!("Modpacks are installed as new profiles");
    }
    let mut index = load_index(paths, &profile.id);
    let mut installed_projects: HashSet<String> = index.values().map(|m| m.project_id.clone()).collect();
    let mut queue = VecDeque::from([(project_id.to_string(), None::<String>, kind.to_string())]);
    let mut installed = Vec::new();

    while let Some((requested, version_id, kind)) = queue.pop_front() {
        if installed_projects.contains(&requested) {
            continue;
        }
        // Resolve slugs to ids so dependencies are only installed once.
        let project = project(http, &requested).await?;
        let pid = project["id"].as_str().unwrap_or(&requested).to_string();
        if installed_projects.contains(&pid) {
            continue;
        }
        let version = match version_id {
            Some(vid) => Some(
                http.get(format!("{API}/version/{}", urlencoding::encode(&vid)))
                    .send()
                    .await?
                    .error_for_status()?
                    .json::<Value>()
                    .await?,
            ),
            None => best_version(http, &pid, &kind, profile).await?,
        };
        let Some(version) = version else {
            if requested == project_id {
                bail!("No version of this project supports Minecraft {}.", profile.game_version);
            }
            continue;
        };
        let file = primary_file(&version)?;
        let name = safe_file_name(file["filename"].as_str().unwrap_or_default())?.to_string();
        let dir = paths.instance(&profile.id).join(folder_for(&kind)?);
        download_all(
            http,
            vec![DlTask {
                url: file["url"].as_str().unwrap_or_default().to_string(),
                path: dir.join(&name),
                sha1: file["hashes"]["sha1"].as_str().map(String::from),
                size: file["size"].as_u64(),
            }],
            1,
            |_, _| {},
        )
        .await?;
        let title = project["title"].as_str().unwrap_or(&name).to_string();
        index.insert(
            name,
            ContentMeta {
                project_id: pid.clone(),
                version_id: version["id"].as_str().unwrap_or_default().to_string(),
                title: title.clone(),
                icon_url: project["icon_url"].as_str().map(String::from),
                version_number: version["version_number"].as_str().unwrap_or_default().to_string(),
            },
        );
        installed_projects.insert(pid.clone());
        installed_projects.insert(requested);
        installed.push(title);

        if kind == "mod" {
            for dep in version["dependencies"].as_array().into_iter().flatten() {
                if dep["dependency_type"] != "required" {
                    continue;
                }
                let dep_version = dep["version_id"].as_str().map(String::from);
                let dep_project = match dep["project_id"].as_str() {
                    Some(p) => p.to_string(),
                    None => continue,
                };
                queue.push_back((dep_project, dep_version, "mod".into()));
            }
        }
        // Shader packs need Iris on Fabric.
        if kind == "shader" && profile.loader == Loader::Fabric {
            queue.push_back(("iris".into(), None, "mod".into()));
        }
    }
    save_index(paths, &profile.id, &index)?;
    Ok(installed)
}

pub fn list(paths: &Paths, profile_id: &str, kind: &str) -> Result<Vec<ContentItem>> {
    let dir = paths.instance(profile_id).join(folder_for(kind)?);
    let index = load_index(paths, profile_id);
    let mut items = Vec::new();
    let Ok(entries) = std::fs::read_dir(&dir) else { return Ok(items) };
    for entry in entries.flatten() {
        let meta = entry.metadata()?;
        if !meta.is_file() {
            continue;
        }
        let file = entry.file_name().to_string_lossy().into_owned();
        let (base, enabled) = match file.strip_suffix(".disabled") {
            Some(b) => (b.to_string(), false),
            None => (file.clone(), true),
        };
        let valid_ext = match kind {
            "mod" => base.ends_with(".jar"),
            _ => base.ends_with(".zip") || meta.is_dir(),
        };
        if !valid_ext {
            continue;
        }
        items.push(ContentItem {
            meta: index.get(&base).cloned(),
            file: base,
            kind: kind.to_string(),
            enabled,
            size: meta.len(),
        });
    }
    items.sort_by(|a, b| {
        let an = a.meta.as_ref().map_or(&a.file, |m| &m.title).to_lowercase();
        let bn = b.meta.as_ref().map_or(&b.file, |m| &m.title).to_lowercase();
        an.cmp(&bn)
    });
    Ok(items)
}

pub fn set_enabled(paths: &Paths, profile_id: &str, kind: &str, file: &str, enabled: bool) -> Result<()> {
    let file = safe_file_name(file)?;
    let dir = paths.instance(profile_id).join(folder_for(kind)?);
    let on = dir.join(file);
    let off = dir.join(format!("{file}.disabled"));
    if enabled && off.exists() {
        std::fs::rename(off, on)?;
    } else if !enabled && on.exists() {
        std::fs::rename(on, off)?;
    }
    Ok(())
}

pub fn remove(paths: &Paths, profile_id: &str, kind: &str, file: &str) -> Result<()> {
    let file = safe_file_name(file)?;
    let dir = paths.instance(profile_id).join(folder_for(kind)?);
    for candidate in [dir.join(file), dir.join(format!("{file}.disabled"))] {
        if candidate.exists() {
            std::fs::remove_file(candidate)?;
        }
    }
    let mut index = load_index(paths, profile_id);
    index.remove(file);
    save_index(paths, profile_id, &index)
}

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Update {
    pub file: String,
    pub kind: String,
    pub title: String,
    pub current: String,
    pub latest: String,
}

/// Checks every tracked file against Modrinth in a single request.
pub async fn check_updates(http: &reqwest::Client, paths: &Paths, profile: &Profile) -> Result<Vec<Update>> {
    let mut hashes = Vec::new();
    let mut by_hash = BTreeMap::new();
    for kind in ["mod", "resourcepack", "shader"] {
        for item in list(paths, &profile.id, kind)? {
            let Some(meta) = item.meta.clone() else { continue };
            let suffix = if item.enabled { "" } else { ".disabled" };
            let path = paths.instance(&profile.id).join(folder_for(kind)?).join(format!("{}{suffix}", item.file));
            if let Ok(hash) = file_sha1(&path) {
                hashes.push(hash.clone());
                by_hash.insert(hash, (item.file.clone(), kind, meta));
            }
        }
    }
    if hashes.is_empty() {
        return Ok(vec![]);
    }
    let mut body = json!({ "hashes": hashes, "algorithm": "sha1", "game_versions": [profile.game_version] });
    if profile.loader == Loader::Fabric {
        body["loaders"] = json!(["fabric", "minecraft", "iris"]);
    }
    let latest: Value = http
        .post(format!("{API}/version_files/update"))
        .json(&body)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    let mut updates = Vec::new();
    for (hash, (file, kind, meta)) in by_hash {
        let Some(v) = latest.get(&hash) else { continue };
        if v["id"].as_str().is_some_and(|id| id != meta.version_id) {
            updates.push(Update {
                file,
                kind: kind.to_string(),
                title: meta.title,
                current: meta.version_number,
                latest: v["version_number"].as_str().unwrap_or_default().to_string(),
            });
        }
    }
    Ok(updates)
}

/// Replaces a tracked file with the newest compatible version.
pub async fn update(http: &reqwest::Client, paths: &Paths, profile: &Profile, kind: &str, file: &str) -> Result<()> {
    let index = load_index(paths, &profile.id);
    let meta = index.get(file).cloned().ok_or_else(|| anyhow!("{file} isn't tracked"))?;
    let enabled = paths.instance(&profile.id).join(folder_for(kind)?).join(file).exists();
    remove(paths, &profile.id, kind, file)?;
    install(http, paths, profile, &meta.project_id, kind).await?;
    if !enabled {
        let new_index = load_index(paths, &profile.id);
        if let Some((name, _)) = new_index.iter().find(|(_, m)| m.project_id == meta.project_id) {
            set_enabled(paths, &profile.id, kind, name, false)?;
        }
    }
    Ok(())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct MrIndex {
    name: String,
    #[serde(default)]
    files: Vec<MrFile>,
    dependencies: BTreeMap<String, String>,
}

#[derive(Deserialize)]
struct MrFile {
    path: String,
    hashes: BTreeMap<String, String>,
    #[serde(default)]
    env: Option<BTreeMap<String, String>>,
    downloads: Vec<String>,
    #[serde(rename = "fileSize", default)]
    file_size: Option<u64>,
}

/// Paths inside a modpack must stay inside the instance.
fn safe_relative(path: &str) -> Option<PathBuf> {
    let p = Path::new(path);
    if p.components().all(|c| matches!(c, Component::Normal(_))) {
        Some(p.to_path_buf())
    } else {
        None
    }
}

pub struct ModpackInfo {
    pub name: String,
    pub game_version: String,
    pub loader: Loader,
    pub loader_version: Option<String>,
}

pub fn read_mrpack_info(pack: &Path) -> Result<ModpackInfo> {
    let mut archive = zip::ZipArchive::new(std::fs::File::open(pack)?)?;
    let mut json = String::new();
    archive
        .by_name("modrinth.index.json")
        .context("not a Modrinth modpack")?
        .read_to_string(&mut json)?;
    let index: MrIndex = serde_json::from_str(&json)?;
    let game_version = index
        .dependencies
        .get("minecraft")
        .cloned()
        .ok_or_else(|| anyhow!("modpack has no Minecraft version"))?;
    let (loader, loader_version) = if let Some(v) = index.dependencies.get("fabric-loader") {
        (Loader::Fabric, Some(v.clone()))
    } else if index.dependencies.keys().any(|k| k != "minecraft") {
        bail!("Only Fabric and vanilla modpacks are supported.");
    } else {
        (Loader::Vanilla, None)
    };
    Ok(ModpackInfo { name: index.name, game_version, loader, loader_version })
}

/// Downloads modpack files and extracts overrides into the instance.
pub async fn install_mrpack(http: &reqwest::Client, pack: &Path, instance: &Path) -> Result<()> {
    let index: MrIndex = {
        let mut archive = zip::ZipArchive::new(std::fs::File::open(pack)?)?;
        let mut json = String::new();
        archive.by_name("modrinth.index.json")?.read_to_string(&mut json)?;
        serde_json::from_str(&json)?
    };
    let mut tasks = Vec::new();
    for file in index.files {
        let client_env = file.env.as_ref().and_then(|e| e.get("client")).map(String::as_str);
        if client_env == Some("unsupported") {
            continue;
        }
        let Some(rel) = safe_relative(&file.path) else { continue };
        let Some(url) = file.downloads.first() else { continue };
        tasks.push(DlTask {
            url: url.clone(),
            path: instance.join(rel),
            sha1: file.hashes.get("sha1").cloned(),
            size: file.file_size,
        });
    }
    download_all(http, tasks, 12, |_, _| {}).await?;

    let mut archive = zip::ZipArchive::new(std::fs::File::open(pack)?)?;
    for prefix in ["overrides/", "client-overrides/"] {
        for i in 0..archive.len() {
            let mut entry = archive.by_index(i)?;
            let Some(name) = entry.enclosed_name() else { continue };
            let Ok(rel) = name.strip_prefix(prefix.trim_end_matches('/')) else { continue };
            if rel.as_os_str().is_empty() {
                continue;
            }
            let out = instance.join(rel);
            if entry.is_dir() {
                std::fs::create_dir_all(&out)?;
                continue;
            }
            if let Some(parent) = out.parent() {
                std::fs::create_dir_all(parent)?;
            }
            std::io::copy(&mut entry, &mut std::fs::File::create(&out)?)?;
        }
    }
    Ok(())
}

/// Downloads the newest compatible .mrpack of a Modrinth modpack project.
pub async fn download_modpack(http: &reqwest::Client, project_id: &str, dest: &Path) -> Result<()> {
    let versions: Vec<Value> = http
        .get(format!("{API}/project/{}/version", urlencoding::encode(project_id)))
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    let version = versions
        .iter()
        .find(|v| v["version_type"] == "release")
        .or_else(|| versions.first())
        .ok_or_else(|| anyhow!("modpack has no versions"))?;
    let file = primary_file(version)?;
    download_all(
        http,
        vec![DlTask {
            url: file["url"].as_str().unwrap_or_default().to_string(),
            path: dest.to_path_buf(),
            sha1: file["hashes"]["sha1"].as_str().map(String::from),
            size: None,
        }],
        1,
        |_, _| {},
    )
    .await
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn rejects_path_escapes() {
        assert!(safe_relative("mods/a.jar").is_some());
        assert!(safe_relative("../evil").is_none());
        assert!(safe_relative("/etc/passwd").is_none());
        assert!(safe_relative("mods/../../x").is_none());
        assert!(safe_file_name("a.jar").is_ok());
        assert!(safe_file_name("../a.jar").is_err());
        assert!(safe_file_name(".hidden").is_err());
    }
}
