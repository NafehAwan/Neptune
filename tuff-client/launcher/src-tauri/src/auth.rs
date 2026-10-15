//! Microsoft account login: OAuth device code -> Xbox Live -> XSTS ->
//! Minecraft services. The device code flow needs no local web server and
//! works the same on every desktop.

use crate::models::{Account, AccountKind};
use anyhow::{anyhow, bail, Context, Result};
use serde::{Deserialize, Serialize};
use serde_json::{json, Value};
use std::time::Duration;

const DEVICE_CODE_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode";
const TOKEN_URL: &str = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
const XBL_URL: &str = "https://user.auth.xboxlive.com/user/authenticate";
const XSTS_URL: &str = "https://xsts.auth.xboxlive.com/xsts/authorize";
const MC_LOGIN_URL: &str = "https://api.minecraftservices.com/authentication/login_with_xbox";
pub const MC_PROFILE_URL: &str = "https://api.minecraftservices.com/minecraft/profile";
const SCOPE: &str = "XboxLive.signin offline_access";

/// Build-time client ID; Settings can override it at runtime.
pub const BUILD_CLIENT_ID: Option<&str> = option_env!("TUFF_MS_CLIENT_ID");

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct DeviceCode {
    pub device_code: String,
    pub user_code: String,
    pub verification_uri: String,
    pub expires_in: u64,
    pub interval: u64,
    #[serde(default)]
    pub message: String,
}

#[derive(Deserialize)]
struct MsToken {
    access_token: String,
    refresh_token: String,
}

pub fn client_id(runtime: Option<&str>) -> Result<String> {
    runtime
        .filter(|s| !s.trim().is_empty())
        .map(|s| s.trim().to_string())
        .or_else(|| BUILD_CLIENT_ID.map(String::from))
        .ok_or_else(|| {
            anyhow!("Microsoft login isn't set up yet. Add your Azure client ID in Settings → Accounts.")
        })
}

pub async fn start_device_code(http: &reqwest::Client, client_id: &str) -> Result<DeviceCode> {
    let response = http
        .post(DEVICE_CODE_URL)
        .form(&[("client_id", client_id), ("scope", SCOPE)])
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
            .post(TOKEN_URL)
            .form(&[
                ("grant_type", "urn:ietf:params:oauth:grant-type:device_code"),
                ("client_id", client_id),
                ("device_code", code.device_code.as_str()),
            ])
            .send()
            .await?;
        if response.status().is_success() {
            let token: MsToken = response.json().await?;
            return minecraft_login(http, token).await;
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
    let response = http
        .post(TOKEN_URL)
        .form(&[
            ("grant_type", "refresh_token"),
            ("client_id", client_id),
            ("refresh_token", account.refresh_token.as_str()),
            ("scope", SCOPE),
        ])
        .send()
        .await?;
    if !response.status().is_success() {
        bail!("Your Microsoft session expired. Remove the account and sign in again.");
    }
    let token: MsToken = response.json().await?;
    minecraft_login(http, token).await
}

async fn minecraft_login(http: &reqwest::Client, ms: MsToken) -> Result<Account> {
    let xbl: Value = http
        .post(XBL_URL)
        .json(&json!({
            "Properties": {
                "AuthMethod": "RPS",
                "SiteName": "user.auth.xboxlive.com",
                "RpsTicket": format!("d={}", ms.access_token)
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
        .context("Minecraft sign-in failed. If you just registered your Azure app, it may still be waiting for Mojang's approval")?
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
