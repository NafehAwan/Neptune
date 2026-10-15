//! Resolves a profile into everything needed to start the game: merged
//! version JSON, libraries, natives, assets, client jar and Java.

use crate::download::{download_all, DlTask};
use crate::meta::{self, Features, VersionJson};
use crate::models::{Loader, Profile};
use crate::paths::Paths;
use crate::{fabric, java};
use anyhow::{anyhow, Context, Result};
use serde::{Deserialize, Serialize};
use std::collections::HashMap;
use std::path::PathBuf;

pub const ASSETS_URL: &str = "https://resources.download.minecraft.net";

#[derive(Serialize, Clone, Debug)]
pub struct Progress {
    pub stage: String,
    pub done: u64,
    pub total: u64,
}

pub struct Prepared {
    pub version: VersionJson,
    pub classpath: Vec<PathBuf>,
    pub natives_dir: PathBuf,
    pub assets_root: PathBuf,
    pub assets_index: String,
    pub java: PathBuf,
}

#[derive(Deserialize)]
struct AssetIndex {
    objects: HashMap<String, AssetObject>,
    #[serde(default, rename = "virtual")]
    is_virtual: bool,
    #[serde(default)]
    map_to_resources: bool,
}

#[derive(Deserialize)]
struct AssetObject {
    hash: String,
    size: u64,
}

pub async fn resolve_version(http: &reqwest::Client, paths: &Paths, profile: &Profile) -> Result<VersionJson> {
    let vanilla = meta::vanilla_json(http, paths, &profile.game_version).await?;
    Ok(match profile.loader {
        Loader::Vanilla => vanilla,
        Loader::Fabric => {
            let loader = match &profile.loader_version {
                Some(v) => v.clone(),
                None => fabric::latest_stable(http, &profile.game_version).await?,
            };
            let child = fabric::profile_json(http, paths, &profile.game_version, &loader).await?;
            meta::merge(child, vanilla)
        }
    })
}

pub async fn prepare(
    http: &reqwest::Client,
    paths: &Paths,
    profile: &Profile,
    java_override: Option<String>,
    concurrency: usize,
    features: &Features,
    progress: &(dyn Fn(Progress) + Send + Sync),
) -> Result<Prepared> {
    let report = |stage: &str, done: u64, total: u64| {
        progress(Progress { stage: stage.to_string(), done, total })
    };
    report("Reading version", 0, 0);
    let version = resolve_version(http, paths, profile).await?;
    let game_version = profile.game_version.clone();

    // Client jar
    let client = version
        .downloads
        .as_ref()
        .and_then(|d| d.get("client"))
        .ok_or_else(|| anyhow!("version {game_version} has no client download"))?;
    let client_jar = paths.versions().join(&game_version).join(format!("{game_version}.jar"));

    // Libraries
    let lib_root = paths.libraries();
    let mut tasks = vec![DlTask {
        url: client.url.clone(),
        path: client_jar.clone(),
        sha1: client.sha1.clone(),
        size: client.size,
    }];
    let mut classpath = Vec::new();
    let mut natives = Vec::new();
    for lib in version.libraries.iter().filter(|l| l.allowed(features)) {
        if let Some(a) = lib.artifact() {
            let path = lib_root.join(&a.path);
            if !classpath.contains(&path) {
                classpath.push(path.clone());
            }
            tasks.push(DlTask { url: a.url, path, sha1: a.sha1, size: a.size });
        }
        if let Some(n) = lib.native() {
            let path = lib_root.join(&n.path);
            natives.push(path.clone());
            tasks.push(DlTask { url: n.url, path, sha1: n.sha1, size: n.size });
        }
    }
    classpath.push(client_jar);
    download_all(http, tasks, concurrency, |d, t| report("Downloading game files", d, t)).await?;

    // Old-style natives
    let natives_dir = paths.natives(&version.id);
    if !natives.is_empty() {
        std::fs::create_dir_all(&natives_dir)?;
        for jar in &natives {
            extract_natives(jar, &natives_dir)
                .with_context(|| format!("extracting natives from {}", jar.display()))?;
        }
    }

    // Assets
    let index_ref = version
        .asset_index
        .clone()
        .ok_or_else(|| anyhow!("version has no asset index"))?;
    let assets_root = paths.assets();
    let index_path = assets_root.join("indexes").join(format!("{}.json", index_ref.id));
    download_all(
        http,
        vec![DlTask {
            url: index_ref.url.clone(),
            path: index_path.clone(),
            sha1: index_ref.sha1.clone(),
            size: index_ref.size,
        }],
        1,
        |_, _| {},
    )
    .await?;
    let index: AssetIndex = serde_json::from_slice(&std::fs::read(&index_path)?)?;
    let objects = assets_root.join("objects");
    let tasks = index
        .objects
        .values()
        .map(|o| {
            let prefix = &o.hash[..2];
            DlTask {
                url: format!("{ASSETS_URL}/{prefix}/{}", o.hash),
                path: objects.join(prefix).join(&o.hash),
                sha1: Some(o.hash.clone()),
                size: Some(o.size),
            }
        })
        .collect();
    download_all(http, tasks, concurrency, |d, t| report("Downloading assets", d, t)).await?;
    let mut effective_assets_root = assets_root.clone();
    if index.is_virtual || index.map_to_resources {
        // Pre-1.7 versions read loose files instead of hashed objects.
        let target = if index.map_to_resources {
            paths.instance(&profile.id).join("resources")
        } else {
            assets_root.join("virtual").join(&index_ref.id)
        };
        for (name, o) in &index.objects {
            if name.split('/').any(|p| p == "..") {
                continue;
            }
            let dest = target.join(name);
            if !dest.exists() {
                std::fs::create_dir_all(dest.parent().unwrap())?;
                std::fs::copy(objects.join(&o.hash[..2]).join(&o.hash), &dest)?;
            }
        }
        if index.is_virtual {
            effective_assets_root = target;
        }
    }

    // Java
    let java = match java_override.filter(|p| !p.trim().is_empty()) {
        Some(path) => PathBuf::from(path),
        None => {
            let component = version
                .java_version
                .as_ref()
                .map(|j| j.component.clone())
                .unwrap_or_else(|| "jre-legacy".into());
            match java::ensure_runtime(http, paths, &component, concurrency, |d, t| {
                report("Downloading Java", d, t)
            })
            .await
            {
                Ok(path) => path,
                Err(e) => java::system_java().ok_or_else(|| {
                    e.context("no Java available; set a Java path in Settings")
                })?,
            }
        }
    };

    Ok(Prepared {
        assets_index: index_ref.id,
        version,
        classpath,
        natives_dir,
        assets_root: effective_assets_root,
        java,
    })
}

fn extract_natives(jar: &std::path::Path, dest: &std::path::Path) -> Result<()> {
    let file = std::fs::File::open(jar)?;
    let mut archive = zip::ZipArchive::new(file)?;
    for i in 0..archive.len() {
        let mut entry = archive.by_index(i)?;
        let Some(name) = entry.enclosed_name() else { continue };
        if entry.is_dir() || name.starts_with("META-INF") {
            continue;
        }
        let out = dest.join(name);
        if let Some(parent) = out.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let mut f = std::fs::File::create(out)?;
        std::io::copy(&mut entry, &mut f)?;
    }
    Ok(())
}
