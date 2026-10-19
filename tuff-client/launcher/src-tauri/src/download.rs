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

/// How far a batch of downloads has got: files finished and bytes received.
#[derive(Clone, Copy, Debug, Default, PartialEq)]
pub struct DlStatus {
    pub files: u64,
    pub files_total: u64,
    pub bytes: u64,
    pub bytes_total: u64,
}

/// Counts bytes as they arrive, across all the downloads of one batch.
struct Counter<'a> {
    files: AtomicU64,
    files_total: u64,
    bytes: AtomicU64,
    bytes_total: AtomicU64,
    notify: &'a (dyn Fn(DlStatus) + Sync),
}

impl Counter<'_> {
    fn status(&self) -> DlStatus {
        DlStatus {
            files: self.files.load(Ordering::Relaxed),
            files_total: self.files_total,
            bytes: self.bytes.load(Ordering::Relaxed),
            bytes_total: self.bytes_total.load(Ordering::Relaxed),
        }
    }

    fn add(&self, n: u64) {
        self.bytes.fetch_add(n, Ordering::Relaxed);
        (self.notify)(self.status());
    }

    /// A failed attempt's bytes are downloaded again, so they don't count.
    fn undo(&self, n: u64) {
        self.bytes.fetch_sub(n, Ordering::Relaxed);
    }
}

/// Downloads every task that's missing, reporting files done and files total.
pub async fn download_all(
    http: &reqwest::Client,
    tasks: Vec<DlTask>,
    concurrency: usize,
    on_progress: impl Fn(u64, u64) + Sync,
) -> Result<()> {
    download_all_status(http, tasks, concurrency, |s| on_progress(s.files, s.files_total)).await
}

/// Like [download_all], reporting bytes too (for "12 of 40 MB" and speed).
pub async fn download_all_status(
    http: &reqwest::Client,
    tasks: Vec<DlTask>,
    concurrency: usize,
    on_status: impl Fn(DlStatus) + Sync,
) -> Result<()> {
    let mut seen = HashSet::new();
    let pending: Vec<DlTask> = tasks
        .into_iter()
        .filter(|t| seen.insert(t.path.clone()) && needs_download(t))
        .collect();
    let counter = Counter {
        files: AtomicU64::new(0),
        files_total: pending.len() as u64,
        bytes: AtomicU64::new(0),
        // files of unknown size are added when their download starts
        bytes_total: AtomicU64::new(pending.iter().filter_map(|t| t.size).sum()),
        notify: &on_status,
    };
    on_status(counter.status());
    if pending.is_empty() {
        return Ok(());
    }
    let counter = &counter;
    let mut results = stream::iter(pending)
        .map(|task| async move { fetch(http, &task, Some(counter)).await })
        .buffer_unordered(concurrency.max(1));
    while let Some(result) = results.next().await {
        result?;
        counter.files.fetch_add(1, Ordering::Relaxed);
        on_status(counter.status());
    }
    Ok(())
}

async fn fetch(http: &reqwest::Client, task: &DlTask, counter: Option<&Counter<'_>>) -> Result<()> {
    let mut attempt = 0;
    let mut sized = task.size.is_some();
    loop {
        let mut got = 0;
        match try_fetch(http, task, counter, &mut got, &mut sized).await {
            Ok(()) => return Ok(()),
            Err(e) if attempt >= 2 => {
                return Err(e).with_context(|| format!("downloading {}", task.url))
            }
            Err(_) => {
                if let Some(c) = counter {
                    c.undo(got);
                }
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

async fn try_fetch(http: &reqwest::Client, task: &DlTask, counter: Option<&Counter<'_>>, got: &mut u64, sized: &mut bool) -> Result<()> {
    if let Some(parent) = task.path.parent() {
        tokio::fs::create_dir_all(parent).await?;
    }
    let response = http.get(&task.url).send().await?.error_for_status()?;
    if let (Some(c), false, Some(len)) = (counter, *sized, response.content_length()) {
        c.bytes_total.fetch_add(len, Ordering::Relaxed);
        *sized = true;
    }
    let tmp = part_path(&task.path);
    let mut file = tokio::fs::File::create(&tmp).await?;
    let mut hasher = Sha1::new();
    let mut body = response.bytes_stream();
    while let Some(chunk) = body.next().await {
        let chunk = chunk?;
        hasher.update(&chunk);
        file.write_all(&chunk).await?;
        *got += chunk.len() as u64;
        if let Some(c) = counter {
            c.add(chunk.len() as u64);
        }
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

    /// Serves `body` for every request, with a Content-Length.
    async fn serve(body: Vec<u8>) -> String {
        use tokio::io::AsyncReadExt;
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();
        tokio::spawn(async move {
            loop {
                let Ok((mut sock, _)) = listener.accept().await else { return };
                let body = body.clone();
                tokio::spawn(async move {
                    let mut buf = [0u8; 2048];
                    let _ = sock.read(&mut buf).await;
                    let head = format!("HTTP/1.1 200 OK\r\nContent-Length: {}\r\nConnection: close\r\n\r\n", body.len());
                    let _ = sock.write_all(head.as_bytes()).await;
                    let _ = sock.write_all(&body).await;
                });
            }
        });
        format!("http://{addr}")
    }

    #[tokio::test]
    async fn counts_files_and_bytes() {
        let dir = std::env::temp_dir().join(format!("tuff-dl-{}", uuid::Uuid::new_v4()));
        let body = vec![7u8; 300_000];
        let url = serve(body.clone()).await;
        let tasks = vec![
            DlTask { url: format!("{url}/a"), path: dir.join("a"), sha1: None, size: Some(300_000) },
            DlTask { url: format!("{url}/b"), path: dir.join("b"), sha1: None, size: Some(300_000) },
            // no size: counted from the Content-Length
            DlTask { url: format!("{url}/c"), path: dir.join("c"), sha1: None, size: None },
        ];
        let seen = std::sync::Mutex::new(Vec::new());
        let http = reqwest::Client::builder().no_proxy().build().unwrap();
        download_all_status(&http, tasks, 2, |s| seen.lock().unwrap().push(s)).await.unwrap();
        let seen = seen.into_inner().unwrap();
        let last = *seen.last().unwrap();
        assert_eq!(last, DlStatus { files: 3, files_total: 3, bytes: 900_000, bytes_total: 900_000 });
        assert_eq!(seen[0], DlStatus { files: 0, files_total: 3, bytes: 0, bytes_total: 600_000 });
        assert!(seen.windows(2).all(|w| w[1].bytes >= w[0].bytes), "bytes only go up");
        assert!(seen.len() > 4, "progress while downloading, not only at the end");
        std::fs::remove_dir_all(dir).unwrap();
    }
}
