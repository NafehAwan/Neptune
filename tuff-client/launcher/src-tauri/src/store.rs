use anyhow::{Context, Result};
use serde::{de::DeserializeOwned, Serialize};
use std::io::Write;
use std::path::Path;

/// Reads a JSON file, falling back to the type's default when the file is
/// missing or unreadable so a corrupt file never bricks the launcher.
pub fn load<T: DeserializeOwned + Default>(path: &Path) -> T {
    std::fs::read(path)
        .ok()
        .and_then(|bytes| serde_json::from_slice(&bytes).ok())
        .unwrap_or_default()
}

pub fn save<T: Serialize>(path: &Path, value: &T) -> Result<()> {
    write_atomic(path, value, false)
}

/// Same as [`save`] but readable by the current user only (used for tokens).
pub fn save_private<T: Serialize>(path: &Path, value: &T) -> Result<()> {
    write_atomic(path, value, true)
}

fn write_atomic<T: Serialize>(path: &Path, value: &T, private: bool) -> Result<()> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent)?;
    }
    let tmp = path.with_extension("json.tmp");
    let bytes = serde_json::to_vec_pretty(value)?;
    let mut options = std::fs::OpenOptions::new();
    options.write(true).create(true).truncate(true);
    #[cfg(unix)]
    if private {
        use std::os::unix::fs::OpenOptionsExt;
        options.mode(0o600);
    }
    #[cfg(not(unix))]
    let _ = private;
    let mut file = options
        .open(&tmp)
        .with_context(|| format!("writing {}", tmp.display()))?;
    file.write_all(&bytes)?;
    file.sync_all()?;
    std::fs::rename(&tmp, path)?;
    Ok(())
}
