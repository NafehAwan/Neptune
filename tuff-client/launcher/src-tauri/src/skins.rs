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
