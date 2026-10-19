//! Updates, always on. The launcher reads `latest.json` from the newest GitHub
//! release (published by CI whenever the version goes up):
//!
//! - a newer Glade mod is downloaded quietly and used from the next launch;
//! - a newer launcher is downloaded as a .deb and installed with the system's
//!   password prompt, then the launcher restarts.
//!
//! Every file is checked against its SHA-256 before it's used.

use crate::glade;
use crate::paths::Paths;
use anyhow::{bail, Context, Result};
use futures::StreamExt;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::cmp::Ordering;
use std::path::{Path, PathBuf};
use std::time::Duration;
use tokio::io::AsyncWriteExt;

/// Set at build time by CI to this repository's latest release.
pub const MANIFEST_URL: &str = match option_env!("GLADE_UPDATE_URL") {
    Some(url) => url,
    None => "https://github.com/NafehAwan/Neptune/releases/latest/download/latest.json",
};

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Manifest {
    /// The launcher version.
    pub version: String,
    pub notes: String,
    /// The release page, for downloading by hand.
    pub page: String,
    pub deb: Option<Asset>,
    #[serde(rename = "mod")]
    pub glade_mod: Option<ModAsset>,
}

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
#[serde(default)]
pub struct Asset {
    pub url: String,
    pub sha256: String,
    pub size: u64,
}

#[derive(Deserialize, Serialize, Clone, Debug, Default)]
#[serde(default)]
pub struct ModAsset {
    pub version: String,
    /// The Minecraft version range the build supports, like `~26.3`.
    pub minecraft: String,
    pub url: String,
    pub sha256: String,
    pub size: u64,
}

pub async fn manifest(http: &reqwest::Client) -> Result<Manifest> {
    http.get(MANIFEST_URL)
        .timeout(Duration::from_secs(20))
        .send()
        .await?
        .error_for_status()?
        .json()
        .await
        .context("reading the update information")
}

/// Whether `latest` is a newer launcher than `current`.
pub fn is_newer(latest: &str, current: &str) -> bool {
    !latest.is_empty() && glade::compare_versions(latest, current) == Ordering::Greater
}

/// Downloads `url` to `dest`, checking its SHA-256, reporting (done, total) bytes.
pub async fn download(http: &reqwest::Client, url: &str, sha256: &str, dest: &Path, on_progress: impl Fn(u64, u64)) -> Result<()> {
    if let Some(dir) = dest.parent() {
        tokio::fs::create_dir_all(dir).await?;
    }
    let response = http.get(url).send().await?.error_for_status()?;
    let total = response.content_length().unwrap_or(0);
    let part = dest.with_extension("part");
    let mut file = tokio::fs::File::create(&part).await?;
    let mut hasher = Sha256::new();
    let mut done = 0u64;
    let mut body = response.bytes_stream();
    while let Some(chunk) = body.next().await {
        let chunk = chunk?;
        hasher.update(&chunk);
        file.write_all(&chunk).await?;
        done += chunk.len() as u64;
        on_progress(done, total);
    }
    file.flush().await?;
    drop(file);
    let got = hex::encode(hasher.finalize());
    if sha256.is_empty() || !got.eq_ignore_ascii_case(sha256) {
        let _ = tokio::fs::remove_file(&part).await;
        bail!("the download was damaged (checksum mismatch); try again");
    }
    tokio::fs::rename(&part, dest).await?;
    Ok(())
}

/// Downloads a newer Glade mod, if there is one. Returns its version.
pub async fn update_mod(http: &reqwest::Client, paths: &Paths, current: Option<&glade::Build>, manifest: &Manifest) -> Result<Option<String>> {
    let Some(asset) = manifest.glade_mod.as_ref().filter(|a| !a.url.is_empty()) else { return Ok(None) };
    if current.is_some_and(|c| glade::compare_versions(&asset.version, &c.info.version) != Ordering::Greater) {
        return Ok(None);
    }
    let dest = glade::downloaded_path(paths);
    let fresh = dest.with_extension("new");
    download(http, &asset.url, &asset.sha256, &fresh, |_, _| {}).await?;
    if glade::read_info(&fresh).map_or(true, |i| i.id != glade::MOD_ID) {
        let _ = std::fs::remove_file(&fresh);
        bail!("the downloaded Glade mod isn't valid");
    }
    std::fs::rename(&fresh, &dest)?;
    Ok(Some(asset.version.clone()))
}

fn on_path(program: &str) -> bool {
    std::env::var_os("PATH").is_some_and(|p| std::env::split_paths(&p).any(|d| d.join(program).is_file()))
}

/// The launcher was installed from the .deb and can install the next one.
pub fn can_install() -> bool {
    let installed = std::env::current_exe().is_ok_and(|p| p.starts_with("/usr/"));
    cfg!(target_os = "linux") && installed && on_path("pkexec") && on_path("dpkg")
}

pub fn deb_path(paths: &Paths, version: &str) -> PathBuf {
    paths.file("glade").join("updates").join(format!("glade_{version}_amd64.deb"))
}

/// Installs the .deb with the system password prompt.
pub async fn install_deb(deb: &Path) -> Result<()> {
    let status = tokio::process::Command::new("pkexec")
        .arg("dpkg")
        .arg("-i")
        .arg(deb)
        .status()
        .await
        .context("starting the installer")?;
    match status.code() {
        Some(0) => Ok(()),
        Some(126) | Some(127) => bail!("The password prompt was closed, so the update wasn't installed."),
        _ => bail!("The update couldn't be installed ({status})."),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn newer_versions() {
        assert!(is_newer("1.1.0", "1.0.0"));
        assert!(is_newer("1.10.0", "1.9.9"));
        assert!(!is_newer("1.0.0", "1.0.0"));
        assert!(!is_newer("0.9.0", "1.0.0"));
        assert!(!is_newer("", "1.0.0"));
    }

    #[test]
    fn reads_the_manifest() {
        let m: Manifest = serde_json::from_str(
            r#"{"version":"1.1.0","notes":"n","page":"p","deb":{"url":"u","sha256":"ab","size":3},
                "mod":{"version":"1.1.0","minecraft":"~26.3","url":"m","sha256":"cd","size":2}}"#,
        )
        .unwrap();
        assert_eq!(m.version, "1.1.0");
        assert_eq!(m.deb.unwrap().url, "u");
        assert_eq!(m.glade_mod.unwrap().minecraft, "~26.3");
    }
}
