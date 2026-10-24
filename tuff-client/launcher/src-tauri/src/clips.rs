//! Clips: records the game window like a screen recorder, in a separate,
//! low-priority ffmpeg process (so the game keeps its FPS), into a rolling
//! buffer of one-second pieces. When the game asks (a kill, a death, the clip
//! key), the last seconds are joined into a video in ~/Videos/Glade.
//!
//! The game sends "glade-event" lines in its log:
//!   clips-config on len=15 after=2 fps=60 res=window q=balanced audio=1 cursor=0
//!   clips-config off
//!   clip kill Steve | clip death | clip key

use std::path::{Path, PathBuf};
use std::process::Stdio;
use std::time::{Duration, SystemTime};

use anyhow::{anyhow, Context, Result};
use tokio::process::{Child, Command};

#[derive(Clone, Debug, PartialEq)]
pub struct Config {
    pub len: u32,
    pub after: u32,
    pub fps: u32,
    /// "window", "1080" or "720"
    pub res: String,
    /// "light", "balanced" or "best"
    pub quality: String,
    pub audio: bool,
    pub cursor: bool,
}

/// Parses "on len=15 after=2 ..." (None for "off" or anything unreadable).
pub fn parse_config(s: &str) -> Option<Config> {
    let mut parts = s.split_whitespace();
    if parts.next()? != "on" {
        return None;
    }
    let mut c = Config { len: 15, after: 2, fps: 60, res: "window".into(), quality: "balanced".into(), audio: true, cursor: false };
    for kv in parts {
        let Some((k, v)) = kv.split_once('=') else { continue };
        match k {
            "len" => c.len = v.parse().unwrap_or(15).clamp(5, 60),
            "after" => c.after = v.parse().unwrap_or(2).min(5),
            "fps" => c.fps = if v == "30" { 30 } else { 60 },
            "res" => c.res = v.to_string(),
            "q" => c.quality = v.to_string(),
            "audio" => c.audio = v == "1",
            "cursor" => c.cursor = v == "1",
            _ => {}
        }
    }
    Some(c)
}

/// The screen rectangle (x, y, width, height) of the biggest visible window
/// that belongs to this process.
pub fn find_window(pid: u32) -> Option<(i32, i32, u32, u32)> {
    use x11rb::connection::Connection;
    use x11rb::protocol::xproto::{AtomEnum, ConnectionExt, MapState};
    let (conn, screen) = x11rb::connect(None).ok()?;
    let root = conn.setup().roots[screen].root;
    let pid_atom = conn.intern_atom(false, b"_NET_WM_PID").ok()?.reply().ok()?.atom;
    let mut best: Option<(i32, i32, u32, u32)> = None;
    let mut stack = vec![root];
    while let Some(w) = stack.pop() {
        if let Ok(Ok(tree)) = conn.query_tree(w).map(|c| c.reply()) {
            stack.extend(tree.children);
        }
        let owner = conn
            .get_property(false, w, pid_atom, AtomEnum::CARDINAL, 0, 1)
            .ok()
            .and_then(|c| c.reply().ok())
            .and_then(|r| r.value32().and_then(|mut v| v.next()));
        if owner != Some(pid) {
            continue;
        }
        let viewable = conn.get_window_attributes(w).ok().and_then(|c| c.reply().ok()).is_some_and(|a| a.map_state == MapState::VIEWABLE);
        let Some(geo) = conn.get_geometry(w).ok().and_then(|c| c.reply().ok()) else { continue };
        let Some(pos) = conn.translate_coordinates(w, root, 0, 0).ok().and_then(|c| c.reply().ok()) else { continue };
        let (wd, ht) = (geo.width as u32, geo.height as u32);
        if !viewable || wd < 200 || ht < 150 {
            continue;
        }
        if best.map_or(true, |b| b.2 * b.3 < wd * ht) {
            best = Some((pos.dst_x as i32, pos.dst_y as i32, wd, ht));
        }
    }
    best
}

pub fn ffmpeg_installed() -> bool {
    std::env::var_os("PATH").is_some_and(|p| std::env::split_paths(&p).any(|d| d.join("ffmpeg").is_file()))
}

fn on_path(program: &str) -> bool {
    std::env::var_os("PATH").is_some_and(|p| std::env::split_paths(&p).any(|d| d.join(program).is_file()))
}

/// The ffmpeg arguments for recording this rectangle into one-second pieces.
pub fn record_args(c: &Config, rect: (i32, i32, u32, u32), display: &str, out: &Path, audio: bool) -> Vec<String> {
    let (x, y, w, h) = rect;
    // even sizes, as the video encoder needs
    let (w, h) = (w & !1, h & !1);
    let mut a: Vec<String> = Vec::new();
    let mut push = |s: &str| a.push(s.to_string());
    for s in ["-hide_banner", "-loglevel", "error", "-y", "-thread_queue_size", "512", "-f", "x11grab"] {
        push(s);
    }
    a.extend(["-framerate".into(), c.fps.to_string(), "-video_size".into(), format!("{w}x{h}")]);
    a.extend(["-draw_mouse".into(), if c.cursor { "1" } else { "0" }.into(), "-i".into(), format!("{display}+{x},{y}")]);
    if audio {
        // what you hear: the default output's monitor
        a.extend(["-thread_queue_size", "512", "-f", "pulse", "-i", "@DEFAULT_MONITOR@"].map(String::from));
    }
    let target = match c.res.as_str() {
        "1080" => 1080,
        "720" => 720,
        _ => 0,
    };
    if target > 0 && h > target {
        a.extend(["-vf".into(), format!("scale=-2:{target}")]);
    }
    let (preset, crf) = match c.quality.as_str() {
        "light" => ("ultrafast", "30"),
        "best" => ("veryfast", "19"),
        _ => ("ultrafast", "24"),
    };
    let gop = c.fps.to_string();
    a.extend(["-c:v", "libx264", "-preset", preset, "-tune", "zerolatency", "-crf", crf, "-pix_fmt", "yuv420p"].map(String::from));
    a.extend(["-g".into(), gop.clone(), "-keyint_min".into(), gop, "-sc_threshold".into(), "0".into()]);
    if audio {
        a.extend(["-c:a", "aac", "-b:a", "160k"].map(String::from));
    }
    let keep = (c.len + c.after + 10).to_string();
    a.extend(["-f", "segment", "-segment_time", "1", "-segment_format", "mpegts", "-reset_timestamps", "1"].map(String::from));
    a.extend(["-segment_wrap".into(), keep, out.join("seg%04d.ts").to_string_lossy().into_owned()]);
    a
}

/// Records one game while Clips is on.
pub struct Recorder {
    pid: u32,
    dir: PathBuf,
    config: Option<Config>,
    rect: Option<(i32, i32, u32, u32)>,
    child: Option<Child>,
    audio: bool,
    started: Option<std::time::Instant>,
    run: u32,
}

impl Recorder {
    pub fn new(pid: u32) -> Self {
        let dir = std::env::temp_dir().join(format!("glade-clips-{pid}"));
        Recorder { pid, dir, config: None, rect: None, child: None, audio: true, started: None, run: 0 }
    }

    /// New settings from the game ("off" stops recording).
    pub async fn configure(&mut self, config: Option<Config>) -> Result<()> {
        if config == self.config {
            return Ok(());
        }
        self.stop().await;
        self.audio = config.as_ref().is_some_and(|c| c.audio);
        self.config = config;
        if self.config.is_some() {
            if !ffmpeg_installed() {
                return Err(anyhow!("Clips need ffmpeg. Install it with: sudo apt install ffmpeg"));
            }
            self.check().await?;
        }
        Ok(())
    }

    /// Keeps the recording going and following the window (call every couple of seconds).
    pub async fn check(&mut self) -> Result<()> {
        let Some(config) = self.config.clone() else { return Ok(()) };
        let rect = find_window(self.pid);
        if rect.is_none() {
            return Ok(()); // window not up yet (or not on X11)
        }
        let exited = match self.child.as_mut() {
            Some(c) => c.try_wait().ok().flatten().is_some(),
            None => true,
        };
        if rect == self.rect && !exited {
            return Ok(());
        }
        // a quick failure with sound on usually means no sound server: go on without it
        if exited && self.child.is_some() && self.audio && self.started.is_some_and(|s| s.elapsed() < Duration::from_secs(5)) {
            self.audio = false;
        }
        self.stop().await;
        self.rect = rect;
        self.run += 1;
        let out = self.dir.join(format!("run{}", self.run));
        std::fs::create_dir_all(&out).context("making the clip buffer folder")?;
        let display = std::env::var("DISPLAY").unwrap_or_else(|_| ":0".into());
        let args = record_args(&config, rect.unwrap(), &display, &out, self.audio);
        // low priority, so the game keeps the CPU when it needs it
        let mut cmd = if on_path("nice") {
            let mut c = Command::new("nice");
            c.args(["-n", "10", "ffmpeg"]);
            c
        } else {
            Command::new("ffmpeg")
        };
        let child = cmd.args(&args).stdin(Stdio::null()).stdout(Stdio::null()).stderr(Stdio::null()).kill_on_drop(true).spawn().context("starting ffmpeg")?;
        self.child = Some(child);
        self.started = Some(std::time::Instant::now());
        Ok(())
    }

    pub async fn stop(&mut self) {
        if let Some(mut c) = self.child.take() {
            let _ = c.kill().await;
        }
    }

    pub fn active(&self) -> bool {
        self.config.is_some() && self.child.is_some()
    }

    pub fn config(&self) -> Option<Config> {
        self.config.clone()
    }

    pub fn buffer_dir(&self) -> PathBuf {
        self.dir.clone()
    }

    /// Stops and deletes the buffer (when the game closes).
    pub async fn finish(&mut self) {
        self.stop().await;
        let _ = std::fs::remove_dir_all(&self.dir);
    }
}

/// Joins the newest `seconds` pieces of the buffer into an mp4 in `out_dir`.
pub async fn save(buffer: &Path, seconds: u32, out_dir: &Path, name: &str) -> Result<PathBuf> {
    let mut pieces: Vec<(SystemTime, PathBuf)> = Vec::new();
    for run in std::fs::read_dir(buffer).context("reading the clip buffer")?.flatten() {
        for f in std::fs::read_dir(run.path()).into_iter().flatten().flatten() {
            let p = f.path();
            if p.extension().is_some_and(|e| e == "ts") {
                if let Ok(m) = f.metadata().and_then(|m| m.modified()) {
                    pieces.push((m, p));
                }
            }
        }
    }
    pieces.sort();
    // the newest piece is still being written
    pieces.pop();
    if pieces.is_empty() {
        return Err(anyhow!("nothing recorded yet"));
    }
    let take = pieces.len().min(seconds as usize);
    let chosen = &pieces[pieces.len() - take..];
    std::fs::create_dir_all(out_dir).context("making the clips folder")?;
    let list = buffer.join(format!("list-{}.txt", std::process::id()));
    let body: String = chosen.iter().map(|(_, p)| format!("file '{}'\n", p.to_string_lossy().replace('\'', "'\\''"))).collect();
    std::fs::write(&list, body)?;
    let out = out_dir.join(format!("{name}.mp4"));
    let status = Command::new("ffmpeg")
        .args(["-hide_banner", "-loglevel", "error", "-y", "-f", "concat", "-safe", "0", "-i"])
        .arg(&list)
        .args(["-c", "copy", "-bsf:a", "aac_adtstoasc", "-movflags", "+faststart"])
        .arg(&out)
        .stdin(Stdio::null())
        .status()
        .await
        .context("running ffmpeg")?;
    let _ = std::fs::remove_file(&list);
    if !status.success() {
        return Err(anyhow!("ffmpeg couldn't join the clip ({status})"));
    }
    Ok(out)
}

/// ~/Videos/Glade (or the data folder when there's no Videos folder).
pub fn clips_dir(data: &Path) -> PathBuf {
    dirs::video_dir().map(|v| v.join("Glade")).unwrap_or_else(|| data.join("clips"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn reads_config() {
        assert_eq!(parse_config("off"), None);
        let c = parse_config("on len=20 after=3 fps=30 res=720 q=best audio=0 cursor=1").unwrap();
        assert_eq!((c.len, c.after, c.fps, c.res.as_str(), c.quality.as_str(), c.audio, c.cursor), (20, 3, 30, "720", "best", false, true));
        assert_eq!(parse_config("on len=999").unwrap().len, 60);
    }

    #[test]
    fn records_the_window_region() {
        let c = parse_config("on len=15 after=2 fps=60 res=720 q=balanced audio=1 cursor=0").unwrap();
        let a = record_args(&c, (100, 50, 1921, 1081), ":0", Path::new("/tmp/x"), true).join(" ");
        assert!(a.contains("-video_size 1920x1080"), "{a}");
        assert!(a.contains("-i :0+100,50"), "{a}");
        assert!(a.contains("@DEFAULT_MONITOR@"), "{a}");
        assert!(a.contains("scale=-2:720"), "{a}");
        assert!(a.contains("-segment_wrap 27"), "{a}");
        let quiet = record_args(&c, (0, 0, 640, 480), ":0", Path::new("/tmp/x"), false).join(" ");
        assert!(!quiet.contains("pulse") && !quiet.contains("scale="), "{quiet}");
    }

    /// Real recording on an X display (run with xvfb-run ... --ignored).
    #[tokio::test]
    #[ignore]
    async fn records_and_saves_a_clip() {
        let display = std::env::var("DISPLAY").expect("needs an X display");
        let root = std::env::temp_dir().join(format!("glade-clip-test-{}", std::process::id()));
        let buffer = root.join("buffer");
        let run = buffer.join("run1");
        std::fs::create_dir_all(&run).unwrap();
        let c = parse_config("on len=3 after=0 fps=30 res=window q=light audio=0 cursor=0").unwrap();
        let args = record_args(&c, (0, 0, 640, 480), &display, &run, false);
        let mut child = Command::new("ffmpeg").args(&args).spawn().unwrap();
        tokio::time::sleep(Duration::from_secs(5)).await;
        let out = save(&buffer, 3, &root.join("out"), "test clip").await.unwrap();
        let _ = child.kill().await;
        let probe = std::process::Command::new("ffprobe")
            .args(["-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0"])
            .arg(&out)
            .output()
            .unwrap();
        let secs: f64 = String::from_utf8_lossy(&probe.stdout).trim().parse().unwrap();
        assert!((2.0..=4.5).contains(&secs), "clip is {secs} s");
        let _ = std::fs::remove_dir_all(root);
    }
}
