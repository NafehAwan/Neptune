//! Builds the java command line from a resolved version.

use crate::meta::{rules_allow, Argument, Features, VersionJson};
use std::collections::HashMap;
use std::path::{Path, PathBuf};

pub const LAUNCHER_NAME: &str = "glade";

/// Low-pause G1 settings tuned for a game client.
pub const TUNED_JVM_FLAGS: &[&str] = &[
    "-XX:+UseG1GC",
    "-XX:+ParallelRefProcEnabled",
    "-XX:MaxGCPauseMillis=50",
    "-XX:+UnlockExperimentalVMOptions",
    "-XX:+DisableExplicitGC",
    "-XX:G1NewSizePercent=30",
    "-XX:G1MaxNewSizePercent=40",
    "-XX:G1HeapRegionSize=8M",
    "-XX:G1ReservePercent=20",
    "-XX:InitiatingHeapOccupancyPercent=15",
    "-XX:+UseStringDeduplication",
];

pub struct Session {
    pub name: String,
    pub uuid: String,
    pub token: String,
    pub xuid: String,
    pub user_type: String,
}

pub struct ArgInput<'a> {
    pub version: &'a VersionJson,
    pub classpath: &'a [PathBuf],
    pub natives_dir: &'a Path,
    pub game_dir: &'a Path,
    pub assets_root: &'a Path,
    pub assets_index: &'a str,
    pub libraries_dir: &'a Path,
    pub session: &'a Session,
    pub memory_mb: u32,
    pub extra_jvm_args: &'a str,
    pub resolution: Option<(u32, u32)>,
    pub server: Option<&'a str>,
    pub launcher_version: &'a str,
}

/// Replaces `${name}` placeholders; unknown placeholders are left intact.
pub fn substitute(input: &str, vars: &HashMap<&str, String>) -> String {
    let mut out = String::with_capacity(input.len());
    let mut rest = input;
    while let Some(start) = rest.find("${") {
        out.push_str(&rest[..start]);
        let after = &rest[start + 2..];
        match after.find('}') {
            Some(end) => {
                let key = &after[..end];
                match vars.get(key) {
                    Some(v) => out.push_str(v),
                    None => {
                        out.push_str("${");
                        out.push_str(key);
                        out.push('}');
                    }
                }
                rest = &after[end + 1..];
            }
            None => {
                out.push_str(&rest[start..]);
                rest = "";
            }
        }
    }
    out.push_str(rest);
    out
}

fn expand(args: &[Argument], features: &Features, vars: &HashMap<&str, String>) -> Vec<String> {
    let mut out = Vec::new();
    for arg in args {
        match arg {
            Argument::Plain(s) => out.push(substitute(s, vars)),
            Argument::Ruled { rules, value } => {
                if rules_allow(rules, features) {
                    out.extend(value.values().iter().map(|s| substitute(s, vars)));
                }
            }
        }
    }
    out
}

fn supports_quick_play(version: &VersionJson) -> bool {
    version.arguments.as_ref().is_some_and(|a| {
        a.game.iter().any(|arg| match arg {
            Argument::Plain(s) => s.contains("quickPlayMultiplayer"),
            Argument::Ruled { value, .. } => value
                .values()
                .iter()
                .any(|s| s.contains("quickPlayMultiplayer")),
        })
    })
}

/// Full argument list after the java binary.
pub fn build_args(input: &ArgInput) -> Vec<String> {
    let sep = if cfg!(windows) { ";" } else { ":" };
    let classpath = input
        .classpath
        .iter()
        .map(|p| p.to_string_lossy().into_owned())
        .collect::<Vec<_>>()
        .join(sep);
    let quick_play = input.server.is_some() && supports_quick_play(input.version);
    let features = Features {
        custom_resolution: input.resolution.is_some(),
        quick_play_multiplayer: quick_play,
    };
    let (width, height) = input.resolution.unwrap_or((854, 480));
    let s = input.session;
    let path = |p: &Path| p.to_string_lossy().into_owned();
    let vars: HashMap<&str, String> = HashMap::from([
        ("auth_player_name", s.name.clone()),
        ("auth_uuid", s.uuid.clone()),
        ("auth_access_token", s.token.clone()),
        ("auth_session", s.token.clone()),
        ("auth_xuid", s.xuid.clone()),
        ("clientid", String::new()),
        ("user_type", s.user_type.clone()),
        ("user_properties", "{}".into()),
        ("version_name", input.version.id.clone()),
        ("version_type", input.version.kind.clone().unwrap_or_else(|| "release".into())),
        ("game_directory", path(input.game_dir)),
        ("assets_root", path(input.assets_root)),
        ("game_assets", path(input.assets_root)),
        ("assets_index_name", input.assets_index.to_string()),
        ("natives_directory", path(input.natives_dir)),
        ("library_directory", path(input.libraries_dir)),
        ("classpath", classpath),
        ("classpath_separator", sep.to_string()),
        ("launcher_name", LAUNCHER_NAME.into()),
        ("launcher_version", input.launcher_version.to_string()),
        ("resolution_width", width.to_string()),
        ("resolution_height", height.to_string()),
        ("quickPlayMultiplayer", input.server.unwrap_or_default().to_string()),
    ]);

    let mut args = vec![
        format!("-Xmx{}M", input.memory_mb),
        format!("-Xms{}M", (input.memory_mb / 2).max(512).min(input.memory_mb)),
    ];
    args.extend(TUNED_JVM_FLAGS.iter().map(|s| s.to_string()));

    match &input.version.arguments {
        Some(a) if !a.jvm.is_empty() => args.extend(expand(&a.jvm, &features, &vars)),
        _ => {
            for template in ["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"] {
                args.push(substitute(template, &vars));
            }
        }
    }
    args.extend(input.extra_jvm_args.split_whitespace().map(String::from));
    args.push(
        input
            .version
            .main_class
            .clone()
            .unwrap_or_else(|| "net.minecraft.client.main.Main".into()),
    );

    match (&input.version.arguments, &input.version.minecraft_arguments) {
        (Some(a), _) if !a.game.is_empty() => args.extend(expand(&a.game, &features, &vars)),
        (_, Some(legacy)) => args.extend(legacy.split_whitespace().map(|s| substitute(s, &vars))),
        _ => {}
    }
    if let (Some(server), false) = (input.server, quick_play) {
        let (host, port) = match server.rsplit_once(':') {
            Some((h, p)) if p.parse::<u16>().is_ok() => (h, p),
            _ => (server, "25565"),
        };
        args.extend(["--server".into(), host.into(), "--port".into(), port.into()]);
    }
    if input.resolution.is_some() && input.version.minecraft_arguments.is_some() {
        args.extend(["--width".into(), width.to_string(), "--height".into(), height.to_string()]);
    }
    args
}

/// Hides the access token wherever it would appear in logs.
pub fn redact(line: &str, token: &str) -> String {
    if token.len() > 8 && line.contains(token) {
        line.replace(token, "<access token>")
    } else {
        line.to_string()
    }
}

/// Offline-mode UUID, identical to Java's `UUID.nameUUIDFromBytes`.
pub fn offline_uuid(name: &str) -> String {
    use md5::{Digest, Md5};
    let mut hash: [u8; 16] = Md5::digest(format!("OfflinePlayer:{name}").as_bytes()).into();
    hash[6] = (hash[6] & 0x0f) | 0x30;
    hash[8] = (hash[8] & 0x3f) | 0x80;
    uuid::Uuid::from_bytes(hash).simple().to_string()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn session() -> Session {
        Session {
            name: "Steve".into(),
            uuid: "abc".into(),
            token: "secret-token-123".into(),
            xuid: "0".into(),
            user_type: "msa".into(),
        }
    }

    #[test]
    fn substitution() {
        let vars = HashMap::from([("a", "1".to_string()), ("b", "two".to_string())]);
        assert_eq!(substitute("x${a}y${b}z", &vars), "x1ytwoz");
        assert_eq!(substitute("${missing}", &vars), "${missing}");
        assert_eq!(substitute("open ${a", &vars), "open ${a");
        assert_eq!(substitute("-Dpath=${a}", &vars), "-Dpath=1");
    }

    fn version(json: serde_json::Value) -> VersionJson {
        serde_json::from_value(json).unwrap()
    }

    fn input<'a>(v: &'a VersionJson, s: &'a Session, cp: &'a [PathBuf], server: Option<&'a str>) -> ArgInput<'a> {
        ArgInput {
            version: v,
            classpath: cp,
            natives_dir: Path::new("/n"),
            game_dir: Path::new("/g"),
            assets_root: Path::new("/a"),
            assets_index: "29",
            libraries_dir: Path::new("/l"),
            session: s,
            memory_mb: 4096,
            extra_jvm_args: "-Dfoo=bar",
            resolution: None,
            server,
            launcher_version: "1.0.0",
        }
    }

    #[test]
    fn modern_args() {
        let v = version(serde_json::json!({
            "id": "26.3",
            "mainClass": "net.minecraft.client.main.Main",
            "arguments": {
                "game": ["--username", "${auth_player_name}", "--gameDir", "${game_directory}",
                    { "rules": [{ "action": "allow", "features": { "has_custom_resolution": true } }],
                      "value": ["--width", "${resolution_width}"] },
                    { "rules": [{ "action": "allow", "features": { "is_quick_play_multiplayer": true } }],
                      "value": ["--quickPlayMultiplayer", "${quickPlayMultiplayer}"] }],
                "jvm": ["-Djava.library.path=${natives_directory}", "-cp", "${classpath}"]
            }
        }));
        let s = session();
        let cp = vec![PathBuf::from("/l/a.jar"), PathBuf::from("/v/26.3.jar")];
        let args = build_args(&input(&v, &s, &cp, None));
        assert_eq!(args[0], "-Xmx4096M");
        assert!(args.contains(&"-Djava.library.path=/n".to_string()));
        assert!(args.contains(&"/l/a.jar:/v/26.3.jar".to_string()));
        let main = args.iter().position(|a| a == "net.minecraft.client.main.Main").unwrap();
        assert_eq!(args[main - 1], "-Dfoo=bar");
        assert_eq!(&args[main + 1..], &["--username", "Steve", "--gameDir", "/g"]);

        let args = build_args(&input(&v, &s, &cp, Some("play.example.net")));
        assert!(args.ends_with(&["--quickPlayMultiplayer".into(), "play.example.net".into()]));
    }

    #[test]
    fn legacy_args_use_server_flags() {
        let v = version(serde_json::json!({
            "id": "1.8.9",
            "mainClass": "net.minecraft.client.main.Main",
            "minecraftArguments": "--username ${auth_player_name} --accessToken ${auth_access_token}"
        }));
        let s = session();
        let cp = vec![PathBuf::from("/x.jar")];
        let args = build_args(&input(&v, &s, &cp, Some("mc.example.net:25570")));
        assert!(args.contains(&"-Djava.library.path=/n".to_string()));
        assert!(args.ends_with(&[
            "--server".into(), "mc.example.net".into(), "--port".into(), "25570".into()
        ]));
        assert!(args.contains(&"secret-token-123".to_string()));
    }

    #[test]
    fn redaction() {
        assert_eq!(redact("token=secret-token-123", "secret-token-123"), "token=<access token>");
        assert_eq!(redact("nothing here", "secret-token-123"), "nothing here");
    }

    #[test]
    fn offline_uuid_matches_java() {
        // Java: UUID.nameUUIDFromBytes("OfflinePlayer:Notch".getBytes(UTF_8))
        assert_eq!(offline_uuid("Notch"), "b50ad385829d3141a2167e7d7539ba7f");
    }
}
