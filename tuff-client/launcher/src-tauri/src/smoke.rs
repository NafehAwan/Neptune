//! Network smoke test, run in CI only (`cargo test smoke -- --ignored`):
//! installs a profile for each loader in GLADE_SMOKE and starts the game
//! until it has opened its window, so Forge, NeoForge and Quilt installs are
//! checked for real.

use crate::install;
use crate::launch::{self, ArgInput, Session};
use crate::meta::Features;
use crate::models::{Loader, Profile};
use crate::paths::Paths;
use std::time::Duration;
use tokio::io::{AsyncBufReadExt, BufReader};

/// Lines that show the game got past loading the loader and opened its window.
const STARTED: &[&str] = &["Backend library:", "Reloading ResourceManager", "Sound engine started", "OpenAL initialized"];

#[tokio::test]
#[ignore]
async fn loaders_start() {
    let wanted = std::env::var("GLADE_SMOKE").unwrap_or_else(|_| "quilt:1.21.1,forge:1.20.1,neoforge:1.21.1".into());
    let paths = Paths { root: std::env::temp_dir().join("glade-smoke") };
    let http = reqwest::Client::builder().user_agent("Glade smoke test (github.com/nafehawan/neptune)").build().unwrap();
    let mut failures = Vec::new();
    for item in wanted.split(',').filter(|s| !s.is_empty()) {
        let (loader, game_version) = item.split_once(':').expect("loader:version");
        let loader: Loader = serde_json::from_value(serde_json::json!(loader)).expect("known loader");
        match start(&http, &paths, loader, game_version).await {
            Ok(line) => println!("SMOKE OK {item}: {line}"),
            Err(e) => {
                println!("SMOKE FAIL {item}: {e:#}");
                failures.push(item.to_string());
            }
        }
    }
    assert!(failures.is_empty(), "loaders that didn't start: {failures:?}");
}

async fn start(http: &reqwest::Client, paths: &Paths, loader: Loader, game_version: &str) -> anyhow::Result<String> {
    let profile = Profile {
        id: format!("smoke-{}-{game_version}", crate::loaders::display_name(&loader).to_lowercase()),
        name: "Smoke".into(),
        game_version: game_version.into(),
        loader,
        memory_mb: 2048,
        ..Default::default()
    };
    let last = std::sync::Mutex::new(String::new());
    let prepared = install::prepare(http, paths, &profile, None, 16, &Features::default(), &|p| {
        let mut l = last.lock().unwrap();
        if *l != p.stage {
            println!("  {}: {}", profile.id, p.stage);
            *l = p.stage.clone();
        }
    })
    .await?;
    let missing: Vec<_> = prepared.classpath.iter().filter(|p| !p.exists()).collect();
    anyhow::ensure!(missing.is_empty(), "classpath files missing: {missing:?}");

    let game_dir = paths.instance(&profile.id);
    std::fs::create_dir_all(&game_dir)?;
    let session = Session { name: "Smoke".into(), uuid: launch::offline_uuid("Smoke"), token: "0".into(), xuid: "0".into(), user_type: "legacy".into() };
    let libraries = paths.libraries();
    let args = launch::build_args(&ArgInput {
        version: &prepared.version,
        classpath: &prepared.classpath,
        natives_dir: &prepared.natives_dir,
        game_dir: &game_dir,
        assets_root: &prepared.assets_root,
        assets_index: &prepared.assets_index,
        libraries_dir: &libraries,
        session: &session,
        memory_mb: 2048,
        extra_jvm_args: "",
        resolution: None,
        server: None,
        launcher_version: "smoke",
    });
    let mut child = tokio::process::Command::new(&prepared.java)
        .args(&args)
        .current_dir(&game_dir)
        .stdout(std::process::Stdio::piped())
        .stderr(std::process::Stdio::piped())
        .kill_on_drop(true)
        .spawn()?;
    let (tx, mut rx) = tokio::sync::mpsc::unbounded_channel::<String>();
    for stream in [
        child.stdout.take().map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>),
        child.stderr.take().map(|s| Box::new(s) as Box<dyn tokio::io::AsyncRead + Unpin + Send>),
    ]
    .into_iter()
    .flatten()
    {
        let tx = tx.clone();
        tokio::spawn(async move {
            let mut lines = BufReader::new(stream).lines();
            while let Ok(Some(line)) = lines.next_line().await {
                let _ = tx.send(line);
            }
        });
    }
    drop(tx);
    let mut tail = std::collections::VecDeque::new();
    let found = tokio::time::timeout(Duration::from_secs(300), async {
        while let Some(line) = rx.recv().await {
            if STARTED.iter().any(|m| line.contains(m)) {
                return Some(line);
            }
            if tail.len() == 40 {
                tail.pop_front();
            }
            tail.push_back(line);
        }
        None
    })
    .await;
    let _ = child.kill().await;
    let crash = crate::crash::analyze(&tail.iter().cloned().collect::<Vec<_>>(), 1, None);
    match found {
        Ok(Some(line)) => Ok(line),
        Ok(None) => anyhow::bail!(
            "the game exited before opening its window ({}). Last lines:\n{}",
            crash.map(|c| c.title).unwrap_or_default(),
            tail.into_iter().collect::<Vec<_>>().join("\n")
        ),
        Err(_) => anyhow::bail!("timed out. Last lines:\n{}", tail.into_iter().collect::<Vec<_>>().join("\n")),
    }
}
