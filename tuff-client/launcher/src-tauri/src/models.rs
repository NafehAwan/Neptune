use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "lowercase")]
#[derive(Default)]
pub enum Loader {
    #[default]
    Vanilla,
    Fabric,
}


#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct Profile {
    pub id: String,
    pub name: String,
    pub icon: String,
    pub game_version: String,
    pub loader: Loader,
    pub loader_version: Option<String>,
    pub memory_mb: u32,
    pub jvm_args: String,
    pub java_path: Option<String>,
    pub resolution: Option<Resolution>,
    pub created: i64,
    pub last_played: Option<i64>,
    pub play_time_secs: u64,
    pub is_tuff: bool,
    /// The Glade preset this profile was made from, if any.
    pub preset: Option<String>,
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
pub struct Resolution {
    pub width: u32,
    pub height: u32,
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "lowercase")]
#[derive(Default)]
pub enum OnLaunch {
    Keep,
    #[default]
    Minimize,
    Hide,
}


#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase", default)]
pub struct Settings {
    pub default_memory_mb: u32,
    pub on_launch: OnLaunch,
    pub low_motion: bool,
    pub discord_rpc: bool,
    pub show_snapshots: bool,
    pub java_path: Option<String>,
    pub concurrent_downloads: u32,
    pub ms_client_id: Option<String>,
    pub discord_app_id: Option<String>,
    pub active_account: Option<String>,
    pub selected_profile: Option<String>,
}

impl Default for Settings {
    fn default() -> Self {
        Self {
            default_memory_mb: crate::system::suggested_memory_mb(),
            on_launch: OnLaunch::default(),
            low_motion: false,
            discord_rpc: true,
            show_snapshots: false,
            java_path: None,
            concurrent_downloads: 16,
            ms_client_id: None,
            discord_app_id: None,
            active_account: None,
            selected_profile: None,
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "lowercase")]
pub enum AccountKind {
    Msa,
    Offline,
}

/// Stored account, including secrets. Never sent to the UI as-is; see
/// [`AccountView`].
#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    pub id: String,
    pub name: String,
    pub kind: AccountKind,
    #[serde(default)]
    pub access_token: String,
    #[serde(default)]
    pub refresh_token: String,
    #[serde(default)]
    pub expires_at: i64,
    #[serde(default)]
    pub xuid: String,
}

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct AccountView {
    pub id: String,
    pub name: String,
    pub kind: AccountKind,
}

impl From<&Account> for AccountView {
    fn from(a: &Account) -> Self {
        Self {
            id: a.id.clone(),
            name: a.name.clone(),
            kind: a.kind.clone(),
        }
    }
}

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase", default)]
pub struct Server {
    pub id: String,
    pub name: String,
    pub address: String,
    pub profile_id: Option<String>,
}
