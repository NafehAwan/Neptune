//! Discord Rich Presence on a background thread so a missing or slow
//! Discord client never blocks the launcher.

use discord_rich_presence::{activity, DiscordIpc, DiscordIpcClient};
use std::sync::mpsc::{channel, Sender};
use std::sync::Mutex;

pub const BUILD_APP_ID: Option<&str> = option_env!("TUFF_DISCORD_APP_ID");

pub enum Presence {
    Playing { details: String, state: String, started: i64 },
    Clear,
}

#[derive(Default)]
pub struct Discord {
    tx: Mutex<Option<(String, Sender<Presence>)>>,
}

impl Discord {
    pub fn set(&self, app_id: Option<String>, presence: Presence) {
        let Some(app_id) = app_id.filter(|s| !s.trim().is_empty()) else { return };
        let mut guard = self.tx.lock().unwrap();
        let needs_thread = !matches!(&*guard, Some((id, _)) if *id == app_id);
        if needs_thread {
            let (tx, rx) = channel::<Presence>();
            let id = app_id.clone();
            std::thread::spawn(move || {
                let mut client: Option<DiscordIpcClient> = None;
                for message in rx {
                    if client.is_none() {
                        client = DiscordIpcClient::new(&id).ok().and_then(|mut c| c.connect().ok().map(|_| c));
                    }
                    let Some(c) = client.as_mut() else { continue };
                    let ok = match &message {
                        Presence::Playing { details, state, started } => c
                            .set_activity(
                                activity::Activity::new()
                                    .details(details)
                                    .state(state)
                                    .timestamps(activity::Timestamps::new().start(*started))
                                    .assets(activity::Assets::new().large_image("tuff").large_text("Tuff Client")),
                            )
                            .is_ok(),
                        Presence::Clear => c.clear_activity().is_ok(),
                    };
                    if !ok {
                        client = None;
                    }
                }
            });
            *guard = Some((app_id, tx));
        }
        if let Some((_, tx)) = &*guard {
            let _ = tx.send(presence);
        }
    }
}
