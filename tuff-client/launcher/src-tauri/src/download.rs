//! Concurrent, checksum-verified downloader. Files are written to a `.part`
//! file and renamed into place only after the SHA-1 matches, so an
//! interrupted install never leaves a corrupt file behind.

use anyhow::{bail, Context, Result};
use futures::{stream, StreamExt};
use sha1::{Digest, Sha1};
use std::collections::HashSet;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;
use tokio::io::AsyncWriteExt;

#[derive(Clone, Debug)]
pub struct DlTask {
    pub url: String,
    pub path: PathBuf,
    pub sha1: Option<String>,
    pub size: Option<u64>,
}

/// True when the file is missing or its size does not match.
pub fn needs_download(task: &DlTask) -> bool {
    match std::fs::metadata(&task.path) {
        Ok(meta) => task.size.is_some_and(|s| meta.len() != s),
        Err(_) => true,
    }
}

pub async fn download_all(
    http: &reqwest::Client,
    tasks: Vec<DlTask>,
    concurrency: usize,
    on_progress: impl Fn(u64, u64),
) -> Result<()> {
    let mut seen = HashSet::new();
    let pending: Vec<DlTask> = tasks
        .into_iter()
        .filter(|t| seen.insert(t.path.clone()) && needs_download(t))
        .collect();
    let total = pending.len() as u64;
    on_progress(0, total);
    if total == 0 {
        return Ok(());
    }
    let done = AtomicU64::new(0);
    let mut results = stream::iter(pending)
        .map(|task| async move { fetch(http, &task).await })
        .buffer_unordered(concurrency.max(1));
    while let Some(result) = results.next().await {
        result?;
        let d = done.fetch_add(1, Ordering::Relaxed) + 1;
        on_progress(d, total);
    }
    Ok(())
}

pub async fn fetch(http: &reqwest::Client, task: &DlTask) -> Result<()> {
    let mut attempt = 0;
    loop {
        match try_fetch(http, task).await {
            Ok(()) => return Ok(()),
            Err(e) if attempt >= 2 => {
                return Err(e).with_context(|| format!("downloading {}", task.url))
            }
            Err(_) => {
                attempt += 1;
                tokio::time::sleep(Duration::from_millis(400 * attempt)).await;
            }
        }
    }
}

fn part_path(path: &Path) -> PathBuf {
    let mut s = path.as_os_str().to_owned();
    s.push(".part");
    PathBuf::from(s)
}

async fn try_fetch(http: &reqwest::Client, task: &DlTask) -> Result<()> {
    if let Some(parent) = task.path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let response = http.get(&task.url).send().await?.error_for_status()?;
    let tmp = part_path(&task.path);
    let mut file = tokio::fs::File::create(&tmp).await?;
    let mut hasher = Sha1::new();
    let mut body = response.bytes_stream();
    while let Some(chunk) = body.next().await {
        let chunk = chunk?;
        hasher.update(&chunk);
        file.write_all(&chunk).await?;
    }
    file.flush().await?;
    drop(file);
    if let Some(expected) = &task.sha1 {
        let got = hex::encode(hasher.finalize());
        if !got.eq_ignore_ascii_case(expected) {
            let _ = tokio::fs::remove_file(&tmp).await;
            bail!("checksum mismatch (expected {expected}, got {got})");
        }
    }
    tokio::fs::rename(&tmp, &task.path).await?;
    Ok(())
}

pub fn file_sha1(path: &Path) -> Result<String> {
    let mut file = std::fs::File::open(path)?;
    let mut hasher = Sha1::new();
    std::io::copy(&mut file, &mut hasher)?;
    Ok(hex::encode(hasher.finalize()))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn size_mismatch_needs_download() {
        let dir = std::env::temp_dir().join(format!("tuff-dl-{}", uuid::Uuid::new_v4()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("f.bin");
        std::fs::write(&path, b"abc").unwrap();
        let mut t = DlTask { url: String::new(), path: path.clone(), sha1: None, size: Some(3) };
        assert!(!needs_download(&t));
        t.size = Some(4);
        assert!(needs_download(&t));
        t.path = dir.join("missing");
        assert!(needs_download(&t));
        assert_eq!(file_sha1(&path).unwrap(), "a9993e364706816aba3e25717850c26c9cd0d89d");
        std::fs::remove_dir_all(dir).unwrap();
    }
}
