//! Java runtimes from Mojang's runtime manifest, so players never have to
//! install the right Java themselves. Falls back to a system `java`.

use crate::download::{download_all_status, DlStatus, DlTask};
use crate::paths::Paths;
use anyhow::{anyhow, Context, Result};
use serde_json::Value;
use std::path::{Path, PathBuf};

const RUNTIMES_URL: &str = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
const MARKER: &str = ".tuff-complete";

pub fn platform() -> Option<&'static str> {
    match (std::env::consts::OS, std::env::consts::ARCH) {
        ("linux", "x86_64") => Some("linux"),
        ("linux", "x86") => Some("linux-i386"),
        ("windows", "x86_64") => Some("windows-x64"),
        ("windows", "aarch64") => Some("windows-arm64"),
        ("macos", "aarch64") => Some("mac-os-arm64"),
        ("macos", "x86_64") => Some("mac-os"),
        _ => None,
    }
}

fn java_binary(dir: &Path) -> PathBuf {
    if cfg!(windows) {
        dir.join("bin").join("javaw.exe")
    } else if cfg!(target_os = "macos") {
        dir.join("jre.bundle/Contents/Home/bin/java")
    } else {
        dir.join("bin").join("java")
    }
}

/// Downloads (once) and returns the java binary for a runtime component.
pub async fn ensure_runtime(
    http: &reqwest::Client,
    paths: &Paths,
    component: &str,
    concurrency: usize,
    on_progress: impl Fn(DlStatus) + Sync,
) -> Result<PathBuf> {
    let dir = paths.runtimes().join(component);
    let java = java_binary(&dir);
    if dir.join(MARKER).exists() && java.exists() {
        return Ok(java);
    }
    let platform = platform().ok_or_else(|| anyhow!("no Mojang Java runtime for this platform"))?;
    let all: Value = http.get(RUNTIMES_URL).send().await?.error_for_status()?.json().await?;
    let manifest_url = all[platform][component][0]["manifest"]["url"]
        .as_str()
        .ok_or_else(|| anyhow!("Java runtime '{component}' is not available for {platform}"))?;
    let manifest: Value = http.get(manifest_url).send().await?.error_for_status()?.json().await?;
    let files = manifest["files"]
        .as_object()
        .ok_or_else(|| anyhow!("malformed Java runtime manifest"))?;

    let mut tasks = Vec::new();
    let mut executables = Vec::new();
    let mut links = Vec::new();
    for (rel, entry) in files {
        if rel.split('/').any(|part| part == "..") {
            continue;
        }
        let path = dir.join(rel);
        match entry["type"].as_str() {
            Some("directory") => std::fs::create_dir_all(&path)?,
            Some("file") => {
                let raw = &entry["downloads"]["raw"];
                let Some(url) = raw["url"].as_str() else { continue };
                tasks.push(DlTask {
                    url: url.to_string(),
                    path: path.clone(),
                    sha1: raw["sha1"].as_str().map(String::from),
                    size: raw["size"].as_u64(),
                });
                if entry["executable"].as_bool() == Some(true) {
                    executables.push(path);
                }
            }
            Some("link") => {
                if let Some(target) = entry["target"].as_str() {
                    links.push((path, target.to_string()));
                }
            }
            _ => {}
        }
    }
    download_all_status(http, tasks, concurrency, on_progress)
        .await
        .context("downloading Java")?;

    #[cfg(unix)]
    {
        use std::os::unix::fs::PermissionsExt;
        for path in executables {
            std::fs::set_permissions(&path, std::fs::Permissions::from_mode(0o755))?;
        }
        for (path, target) in links {
            let _ = std::fs::remove_file(&path);
            if let Some(parent) = path.parent() {
                std::fs::create_dir_all(parent)?;
            }
            std::os::unix::fs::symlink(&target, &path)?;
        }
    }
    #[cfg(not(unix))]
    let _ = (executables, links);

    std::fs::write(dir.join(MARKER), component)?;
    Ok(java)
}

/// `java` found on PATH (or JAVA_HOME).
pub fn system_java() -> Option<PathBuf> {
    let exe = if cfg!(windows) { "java.exe" } else { "java" };
    if let Some(home) = std::env::var_os("JAVA_HOME") {
        let candidate = PathBuf::from(home).join("bin").join(exe);
        if candidate.is_file() {
            return Some(candidate);
        }
    }
    std::env::split_paths(&std::env::var_os("PATH")?)
        .map(|dir| dir.join(exe))
        .find(|p| p.is_file())
}
