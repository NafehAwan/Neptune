//! Skin and cape management through the official Minecraft services API.

use crate::auth::MC_PROFILE_URL;
use anyhow::{bail, Result};
use base64::Engine;
use serde_json::{json, Value};

pub async fn profile(http: &reqwest::Client, token: &str) -> Result<Value> {
    Ok(http
        .get(MC_PROFILE_URL)
        .bearer_auth(token)
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?)
}

/// Downloads a texture and returns it as a data URL so the 3D viewer can
/// read it without cross-origin issues.
pub async fn texture_data_url(http: &reqwest::Client, url: &str) -> Result<String> {
    if !url.starts_with("https://textures.minecraft.net/") && !url.starts_with("http://textures.minecraft.net/") {
        bail!("not a Minecraft texture URL");
    }
    let url = url.replacen("http://", "https://", 1);
    let bytes = http.get(url).send().await?.error_for_status()?.bytes().await?;
    Ok(format!(
        "data:image/png;base64,{}",
        base64::engine::general_purpose::STANDARD.encode(bytes)
    ))
}

pub async fn upload_skin(http: &reqwest::Client, token: &str, png: Vec<u8>, slim: bool) -> Result<()> {
    if png.len() > 1024 * 1024 || !png.starts_with(b"\x89PNG") {
        bail!("Skins must be PNG files under 1 MB.");
    }
    let part = reqwest::multipart::Part::bytes(png)
        .file_name("skin.png")
        .mime_str("image/png")?;
    let form = reqwest::multipart::Form::new()
        .text("variant", if slim { "slim" } else { "classic" })
        .part("file", part);
    let response = http
        .post(format!("{MC_PROFILE_URL}/skins"))
        .bearer_auth(token)
        .multipart(form)
        .send()
        .await?;
    if !response.status().is_success() {
        bail!("Mojang rejected the skin ({}). Skins must be 64×64 or 64×32.", response.status());
    }
    Ok(())
}

pub async fn reset_skin(http: &reqwest::Client, token: &str) -> Result<()> {
    http.delete(format!("{MC_PROFILE_URL}/skins/active"))
        .bearer_auth(token)
        .send()
        .await?
        .error_for_status()?;
    Ok(())
}

pub async fn set_cape(http: &reqwest::Client, token: &str, cape_id: Option<&str>) -> Result<()> {
    let url = format!("{MC_PROFILE_URL}/capes/active");
    let request = match cape_id {
        Some(id) => http.put(url).json(&json!({ "capeId": id })),
        None => http.delete(url),
    };
    request.bearer_auth(token).send().await?.error_for_status()?;
    Ok(())
}

/// Looks up a premium player's current skin through Mojang's public session
/// server (no sign-in needed) and downloads it. `Ok(None)` means the player
/// has no custom skin and uses the default one.
pub async fn session_skin(http: &reqwest::Client, uuid: &str) -> Result<Option<Vec<u8>>> {
    let uuid = uuid.replace('-', "");
    let profile: Value = http
        .get(format!("https://sessionserver.mojang.com/session/minecraft/profile/{uuid}"))
        .send()
        .await?
        .error_for_status()?
        .json()
        .await?;
    let encoded = profile["properties"]
        .as_array()
        .and_then(|props| props.iter().find(|p| p["name"] == "textures"))
        .and_then(|p| p["value"].as_str())
        .unwrap_or_default();
    if encoded.is_empty() {
        return Ok(None);
    }
    let textures: Value = serde_json::from_slice(&base64::engine::general_purpose::STANDARD.decode(encoded)?)?;
    let Some(url) = textures["textures"]["SKIN"]["url"].as_str() else {
        return Ok(None);
    };
    if !url.starts_with("https://textures.minecraft.net/") && !url.starts_with("http://textures.minecraft.net/") {
        bail!("unexpected skin URL");
    }
    let bytes = http.get(url.replacen("http://", "https://", 1)).send().await?.error_for_status()?.bytes().await?;
    Ok(Some(bytes.to_vec()))
}

/// Reads the game's own default skin (Steve) out of any installed client jar,
/// so offline accounts show the real default head without the launcher
/// shipping Mojang's texture itself.
pub fn default_skin_from_jars(versions: &std::path::Path) -> Option<Vec<u8>> {
    const ENTRIES: [&str; 2] = [
        "assets/minecraft/textures/entity/player/wide/steve.png",
        "assets/minecraft/textures/entity/steve.png",
    ];
    let dirs = std::fs::read_dir(versions).ok()?;
    for dir in dirs.flatten() {
        let id = dir.file_name().to_string_lossy().to_string();
        let jar = dir.path().join(format!("{id}.jar"));
        let Ok(file) = std::fs::File::open(&jar) else { continue };
        let Ok(mut zip) = zip::ZipArchive::new(file) else { continue };
        for entry in ENTRIES {
            if let Ok(mut f) = zip.by_name(entry) {
                let mut bytes = Vec::new();
                if std::io::Read::read_to_end(&mut f, &mut bytes).is_ok() {
                    return Some(bytes);
                }
            }
        }
    }
    None
}

pub fn png_data_url(bytes: &[u8]) -> String {
    format!("data:image/png;base64,{}", base64::engine::general_purpose::STANDARD.encode(bytes))
}
