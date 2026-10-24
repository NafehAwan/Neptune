//! Microsoft account login: OAuth device code -> Xbox Live -> XSTS ->
//! Minecraft services. The device code flow needs no local web server and
//! works the same on every desktop.
//!
//! Minecraft only accepts Azure apps Mojang has approved, so by default this
//! signs in through login.live.com as the official Minecraft launcher, the way
//! other open launchers do. A client ID set in Settings uses the Azure flow.

use crate::models::{Account, AccountKind};
use anyhow::{anyhow, bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::time::Duration;

const DEVICE_CODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const LIVE_DEVICE_CODE_URL: &str = "https://login.live.com/oauth20_connect.srf";
const LIVE_TOKEN_URL: &str = "https://login.live.com/oauth20_token.srf";
const LIVE_SCOPE: &str = "service::user.auth.xboxlive.com::MBI_SSL";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
pub const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";
const SCOPE: &str = "XboxLive.signin offline_access";

/// The official Minecraft launcher's client ID, used unless Settings names an
/// approved Azure app. A client ID is public (every sign-in sends it).
pub const DEFAULT_CLIENT_ID: &str = "00000000402b5328";

/// Glade's old Azure app, never approved by Mojang: treated as "not set".
const OLD_CLIENT_ID: &str = "d93d1551-5d45-42b7-acfc-886f4de37af7";

fn is_live(client_id: &str) -> bool {
    client_id == DEFAULT_CLIENT_ID
}

/// (device code url, token url, scope) for this client ID.
fn endpoints(client_id: &str) -> (&'static str, &'static str, &'static str) {
    if is_live(client_id) {
        (LIVE_DEVICE_CODE_URL, LIVE_TOKEN_URL, LIVE_SCOPE)
    } else {
        (DEVICE_CODE_URL, TOKEN_URL, SCOPE)
    }
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct DeviceCode {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    #[serde(default = "default_expiry")]
    pub expires_in: u64,
    #[serde(default = "default_interval")]
    pub interval: u64,
    #[serde(default)]
    pub message: String,
}

fn default_expiry() -> u64 {
    900
}

fn default_interval() -> u64 {
    5
}

#[derive(Deserialize)]
struct MsToken {
    access_token: String,
    #[serde(default)]
    refresh_token: String,
}

pub fn client_id(runtime: Option<&str>) -> Result<String> {
    Ok(runtime
        .map(str::trim)
        .filter(|s| !s.is_empty() && *s != OLD_CLIENT_ID)
        .unwrap_or(DEFAULT_CLIENT_ID)
        .to_string())
}

pub async fn start_device_code(http: &reqwest::Client, client_id: &str) -> Result<DeviceCode> {
    let (url, _, scope) = endpoints(client_id);
    let response = http
        .post(url)
        .form(&[("client_id", client_id), ("scope", scope), ("response_type", "device_code")])
        .send()
        .await?;
    if !response.status().is_success() {
        let body: Value = response.json().await.unwrap_or_default();
        bail!(
            "Microsoft rejected the login request: {}",
            body["error_description"].as_str().unwrap_or("unknown error")
        );
    }
    Ok(response.json().await?)
}

/// Polls until the user finishes signing in, then completes the full chain.
pub async fn finish_device_code(http: &reqwest::Client, client_id: &str, code: &DeviceCode) -> Result<Account> {
    let mut interval = code.interval.max(1);
    let deadline = std::time::Instant::now() + Duration::from_secs(code.expires_in);
    loop {
        if std::time::Instant::now() > deadline {
            bail!("The login code expired. Try again.");
        }
        tokio::time::sleep(Duration::from_secs(interval)).await;
        let response = http
            .post(endpoints(client_id).1)
            .form(&[
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", client_id),
                ("device_code", code.device_code.as_str()),
            ])
            .send()
            .await?;
        if response.status().is_success() {
            let token: MsToken = response.json().await?;
            return minecraft_login(http, token, is_live(client_id)).await;
        }
        let body: Value = response.json().await.unwrap_or_default();
        match body["error"].as_str() {
            Some("authorization_pending") => continue,
            Some("slow_down") => interval += 5,
            Some("authorization_declined") => bail!("Login was cancelled."),
            Some("expired_token") => bail!("The login code expired. Try again."),
            _ => bail!(
                "Login failed: {}",
                body["error_description"].as_str().unwrap_or("unknown error")
            ),
        }
    }
}

/// Uses the refresh token to get a fresh Minecraft token.
pub async fn refresh(http: &reqwest::Client, client_id: &str, account: &Account) -> Result<Account> {
    let (_, url, scope) = endpoints(client_id);
    let response = http
        .post(url)
        .form(&[
            ("grant_type", "refresh_token"),
            ("client_id", client_id),
            ("refresh_token", account.refresh_token.as_str()),
            ("scope", scope),
        ])
        .send()
        .await?;
    if !response.status().is_success() {
        bail!("Your Microsoft session expired. Remove the account and sign in again.");
    }
    let token: MsToken = response.json().await?;
    minecraft_login(http, token, is_live(client_id)).await
}

async fn minecraft_login(http: &reqwest::Client, ms: MsToken, live: bool) -> Result<Account> {
    // live.com service tickets go in as "t=", Azure tokens as "d="
    let ticket = format!("{}={}", if live { "t" } else { "d" }, ms.access_token);
    let xbl: Value = http
        .post(XBL_URL)
        .json(&json!({
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": ticket
            },
            "RelyingParty": "http://auth.xboxlive.com",
            "TokenType": "JWT"
        }))
        .send()
        .await?
        .error_for_status()
        .context("Xbox Live sign-in failed")?
        .json()
        .await?;
    let xbl_token = xbl["Token"].as_str().ok_or_else(|| anyhow!("Xbox Live returned no token"))?;

    let response = http
        .post(XSTS_URL)
        .json(&json!({
            "Properties": { "SandboxId": "RETAIL", "UserTokens": [xbl_token] },
            "RelyingParty": "rp://api.minecraftservices.com/",
            "TokenType": "JWT"
        }))
        .send()
        .await?;
    if response.status().as_u16() == 401 {
        let body: Value = response.json().await.unwrap_or_default();
        bail!(xsts_error(body["XErr"].as_u64().unwrap_or(0)));
    }
    let xsts: Value = response.error_for_status()?.json().await?;
    let xsts_token = xsts["Token"].as_str().ok_or_else(|| anyhow!("XSTS returned no token"))?;
    let uhs = xsts["DisplayClaims"]["xui"][0]["uhs"]
        .as_str()
        .ok_or_else(|| anyhow!("XSTS returned no user hash"))?;
    let xuid = xsts["DisplayClaims"]["xui"][0]["xid"].as_str().unwrap_or("0").to_string();

    let mc: Value = http
        .post(MC_LOGIN_URL)
        .json(&json!({ "identityToken": format!("XBL3.0 x={uhs};{xsts_token}") }))
        .send()
        .await?
        .error_for_status()
        .context(if live { "Minecraft sign-in failed" } else { "Minecraft sign-in failed. Your Azure app may still be waiting for Mojang's approval; clear the client ID in Settings to use the built-in sign-in" })?
        .json()
        .await?;
    let access_token = mc["access_token"].as_str().ok_or_else(|| anyhow!("Minecraft returned no token"))?;
    let expires_in = mc["expires_in"].as_i64().unwrap_or(86400);

    let response = http.get(MC_PROFILE_URL).bearer_auth(access_token).send().await?;
    if response.status().as_u16() == 404 {
        bail!("This Microsoft account doesn't own Minecraft: Java Edition.");
    }
    let profile: Value = response.error_for_status()?.json().await?;
    Ok(Account {
        id: profile["id"].as_str().unwrap_or_default().to_string(),
        name: profile["name"].as_str().unwrap_or_default().to_string(),
        kind: AccountKind::Msa,
        access_token: access_token.to_string(),
        refresh_token: ms.refresh_token,
        expires_at: chrono::Utc::now().timestamp() + expires_in,
        xuid,
    })
}

fn xsts_error(code: u64) -> String {
    match code {
        2148916233 => "This Microsoft account has no Xbox profile. Sign in at xbox.com once, then try again.".into(),
        2148916235 => "Xbox Live isn't available in your country.".into(),
        2148916236 | 2148916237 => "This account needs adult verification on xbox.com.".into(),
        2148916238 => "This is a child account. An adult must add it to a Microsoft family first.".into(),
        _ => format!("Xbox Live denied access (code {code})."),
    }
}

pub fn offline_account(name: &str) -> Result<Account> {
    let name = name.trim();
    let valid = (3..=16).contains(&name.len())
        && name.chars().all(|c| c.is_ascii_alphanumeric() || c == '_');
    if !valid {
        bail!("Names must be 3–16 characters: letters, numbers and underscores.");
    }
    Ok(Account {
        id: crate::launch::offline_uuid(name),
        name: name.to_string(),
        kind: AccountKind::Offline,
        access_token: String::new(),
        refresh_token: String::new(),
        expires_at: 0,
        xuid: "0".into(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn client_id_order() {
        assert_eq!(client_id(Some(" my-app ")).unwrap(), "my-app");
        assert_eq!(client_id(None).unwrap(), DEFAULT_CLIENT_ID);
        assert_eq!(client_id(Some("  ")).unwrap(), DEFAULT_CLIENT_ID);
        assert_eq!(client_id(Some(OLD_CLIENT_ID)).unwrap(), DEFAULT_CLIENT_ID);
        assert!(is_live(&client_id(None).unwrap()));
    }
}
