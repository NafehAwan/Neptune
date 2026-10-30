//! Works out why the game crashed, from the end of its log, the crash report
//! and the JVM's fatal error log, and names the mod responsible when it can.
//!
//! Loader errors (missing dependencies, wrong Minecraft version, duplicates)
//! are read straight from Fabric, Quilt, Forge and NeoForge messages. For other
//! crashes the stack trace is matched against the classes inside each mod jar,
//! and Fabric mixin handler names (`handler$abc000$modid$name`) point at the mod
//! that patched the crashing code.

use serde::Serialize;
use std::collections::HashMap;
use std::io::Read;
use std::path::Path;

#[derive(Serialize, Clone, Debug, PartialEq, Default)]
#[serde(rename_all = "camelCase")]
pub struct CrashReport {
    pub title: String,
    pub hint: String,
    /// The mod the crash points at, by name.
    pub culprit: Option<String>,
    /// That mod's file in the profile's mods folder, so it can be turned off.
    pub mod_file: Option<String>,
    /// The log lines that show the problem.
    pub evidence: Vec<String>,
}

/// A jar in the mods folder.
#[derive(Clone, Debug, Default)]
pub struct ModFile {
    pub file: String,
    pub name: String,
    pub ids: Vec<String>,
    /// fabric, quilt, forge or neoforge (what the jar declares itself as)
    pub kinds: Vec<&'static str>,
    /// Top-level packages of its classes, like `me/jellysquid/mods`.
    pub packages: Vec<String>,
}

/// Everything besides the log that helps explain a crash.
#[derive(Default)]
pub struct Context {
    pub crash_report: Option<String>,
    pub fatal_log: Option<String>,
    pub mods: Vec<ModFile>,
    /// The profile's loader, as Modrinth names it (fabric, quilt, forge, neoforge, vanilla).
    pub loader: String,
}

impl Context {
    fn by_id(&self, id: &str) -> Option<&ModFile> {
        self.mods.iter().find(|m| m.ids.iter().any(|i| i.eq_ignore_ascii_case(id)))
    }

    /// The mod a stack frame's class belongs to.
    fn by_class(&self, class: &str) -> Option<&ModFile> {
        let path = class.replace('.', "/");
        self.mods
            .iter()
            .flat_map(|m| m.packages.iter().map(move |p| (m, p)))
            .filter(|(_, p)| path.starts_with(p.as_str()) && path.as_bytes().get(p.len()) == Some(&b'/'))
            .max_by_key(|(_, p)| p.len())
            .map(|(m, _)| m)
    }

    fn name_for(&self, id: &str) -> String {
        self.by_id(id).map(|m| m.name.clone()).unwrap_or_else(|| id.to_string())
    }
}

fn report(title: impl Into<String>, hint: impl Into<String>) -> CrashReport {
    CrashReport { title: title.into(), hint: hint.into(), ..Default::default() }
}

impl CrashReport {
    fn blame(mut self, ctx: &Context, id_or_name: &str) -> Self {
        match ctx.by_id(id_or_name) {
            Some(m) => {
                self.culprit = Some(m.name.clone());
                self.mod_file = Some(m.file.clone());
            }
            None => self.culprit = Some(id_or_name.to_string()),
        }
        self
    }

    fn blame_file(mut self, m: &ModFile) -> Self {
        self.culprit = Some(m.name.clone());
        self.mod_file = Some(m.file.clone());
        self
    }

    fn show(mut self, lines: &[&str]) -> Self {
        self.evidence = lines.iter().map(|l| l.trim().chars().take(240).collect()).filter(|l: &String| !l.is_empty()).take(6).collect();
        self
    }
}

/// Explains a crash. Returns `None` for a normal exit.
pub fn analyze(lines: &[String], exit_code: i32, ctx: Option<&Context>) -> Option<CrashReport> {
    if exit_code == 0 {
        return None;
    }
    let empty = Context::default();
    let ctx = ctx.unwrap_or(&empty);
    let mut text: Vec<&str> = lines.iter().map(String::as_str).collect();
    if let Some(r) = &ctx.crash_report {
        text.extend(r.lines());
    }
    let fatal: Vec<&str> = ctx.fatal_log.as_deref().map(|f| f.lines().collect()).unwrap_or_default();

    let checks: [&dyn Fn() -> Option<CrashReport>; 11] = [
        &|| fabric_dependencies(&text, ctx),
        &|| forge_dependencies(&text, ctx),
        &|| duplicates(&text, ctx),
        &|| java_version(&text, ctx),
        &|| memory(&text, exit_code),
        &|| graphics(&text, &fatal),
        &|| mixin(&text, ctx),
        &|| damaged_file(&text, ctx),
        &|| suspected(&text, ctx),
        &|| stack(&text, ctx),
        &|| wrong_loader(ctx),
    ];
    if let Some(found) = checks.iter().find_map(|check| check()) {
        return Some(found);
    }
    let description = text.iter().find_map(|l| l.trim().strip_prefix("Description: ")).filter(|d| !d.is_empty());
    let exception = text.iter().find(|l| is_exception_line(l)).copied();
    Some(
        report(
            match description {
                Some(d) => format!("Minecraft crashed: {d}"),
                None => format!("Game exited with code {exit_code}"),
            },
            "Glade couldn't pin it on one mod. If you added mods recently, turn them off one at a time. The full log is in the console and the crash report in the profile's crash-reports folder.",
        )
        .show(&exception.into_iter().collect::<Vec<_>>()),
    )
}

fn is_exception_line(line: &str) -> bool {
    let l = line.trim_start();
    let head = l.split(':').next().unwrap_or("");
    (head.ends_with("Exception") || head.ends_with("Error")) && head.contains('.') && !head.contains(' ')
}

/// Text between the first `'` pair after `from`, e.g. `Mod 'Sodium'` -> Sodium.
fn quoted(s: &str) -> Option<&str> {
    let start = s.find('\'')? + 1;
    let end = s[start..].find('\'')? + start;
    Some(&s[start..end])
}

/// The first `(id)` in a string.
fn paren(s: &str) -> Option<&str> {
    let start = s.find('(')? + 1;
    let end = s[start..].find(')')? + start;
    Some(&s[start..end])
}

// ---------------------------------------------------------------- loader errors

/// Fabric and Quilt: "Mod 'Sodium' (sodium) 0.6 requires any version of 'Fabric API' (fabric-api), which is missing!"
fn fabric_dependencies(text: &[&str], ctx: &Context) -> Option<CrashReport> {
    for line in text {
        let l = line.trim().trim_start_matches("- ");
        if !l.starts_with("Mod '") {
            continue;
        }
        let Some(name) = quoted(l) else { continue };
        let id = paren(l).unwrap_or(name);
        let after = &l[l.find(')').map_or(0, |i| i + 1)..];
        if after.contains("which is missing") {
            let dep_part = &after[after.find(" of ").map_or(0, |i| i + 4)..];
            let dep = quoted(dep_part).or_else(|| dep_part.split_whitespace().next()).unwrap_or("another mod");
            let dep_id = paren(dep_part).unwrap_or(dep);
            let hint = if dep_id == "minecraft" {
                format!("{name} isn't made for this Minecraft version. Remove it, or install a build of it for this version from the Mods page.")
            } else {
                format!("Install {dep} from the Mods page, or turn {name} off.")
            };
            return Some(report(format!("{name} needs {dep}"), hint).blame(ctx, id).show(&[line]));
        }
        if after.contains("but only the wrong version is present") || after.contains("but a different version is present") {
            let dep_part = &after[after.find(" of ").map_or(0, |i| i + 4)..];
            let dep_id = paren(dep_part).unwrap_or("");
            let dep = quoted(dep_part).unwrap_or(dep_id);
            let title = if dep_id == "minecraft" {
                format!("{name} is for a different Minecraft version")
            } else {
                format!("{name} needs a different version of {dep}")
            };
            let hint = if dep_id == "minecraft" {
                format!("Remove {name}, or install the build for this version from the Mods page.")
            } else {
                format!("Update {dep} (Mods page, Updates) or turn {name} off.")
            };
            return Some(report(title, hint).blame(ctx, id).show(&[line]));
        }
        if after.contains("is incompatible with") || after.contains("breaks") {
            let other = quoted(&after[after.find("with").or_else(|| after.find("breaks")).unwrap_or(0)..]).unwrap_or("another mod");
            return Some(
                report(format!("{name} doesn't work with {other}"), format!("Turn off {name} or {other}."))
                    .blame(ctx, id)
                    .show(&[line]),
            );
        }
    }
    None
}

/// Forge and NeoForge: "Mod ID: 'jei', Requested by: 'rei_plugin', Expected range: '[19,)', Actual version: '[MISSING]'"
fn forge_dependencies(text: &[&str], ctx: &Context) -> Option<CrashReport> {
    for line in text {
        let l = line.trim();
        if !(l.contains("Mod ID: '") && l.contains("Requested by: '")) {
            continue;
        }
        let field = |key: &str| l.find(key).and_then(|i| quoted(&l[i..]));
        let Some(dep) = field("Mod ID:") else { continue };
        let by = field("Requested by:").unwrap_or("a mod");
        let actual = field("Actual version:").unwrap_or("");
        let dep_name = ctx.name_for(dep);
        let by_name = ctx.name_for(by);
        let found = if actual == "[MISSING]" || actual.is_empty() {
            report(format!("{by_name} needs {dep_name}"), format!("Install {dep_name} from the Mods page, or turn {by_name} off."))
        } else if dep == "minecraft" {
            report(
                format!("{by_name} is for a different Minecraft version"),
                format!("Remove {by_name}, or install the build for this version from the Mods page."),
            )
        } else {
            let range = field("Expected range:").unwrap_or("another version");
            report(
                format!("{by_name} needs a different version of {dep_name}"),
                format!("It wants {dep_name} {range} but {actual} is installed. Update {dep_name} or turn {by_name} off."),
            )
        };
        return Some(found.blame(ctx, by).show(&[line]));
    }
    None
}

fn duplicates(text: &[&str], ctx: &Context) -> Option<CrashReport> {
    let i = text.iter().position(|l| {
        let l = l.to_ascii_lowercase();
        l.contains("duplicate mod") || l.contains("found duplicate") || l.contains("duplicated mods")
    })?;
    let lines: Vec<&str> = text[i..text.len().min(i + 4)].to_vec();
    let id = lines.iter().find_map(|l| quoted(l).or_else(|| paren(l)));
    let name = id.map(|i| ctx.name_for(i)).unwrap_or_else(|| "A mod".into());
    let mut r = report(
        format!("{name} is installed twice"),
        "Two files contain the same mod. Remove the older one in the profile's Mods list.",
    )
    .show(&lines);
    r.culprit = id.map(|_| name.clone());
    Some(r)
}

// ---------------------------------------------------------------- the JVM and the machine

fn java_version(text: &[&str], ctx: &Context) -> Option<CrashReport> {
    let line = text.iter().find(|l| l.contains("UnsupportedClassVersionError") || l.contains("compiled by a more recent version of the Java Runtime"))?;
    // "class file version 65.0" is Java 21 (class version - 44)
    let needed = line
        .find("class file version ")
        .and_then(|i| line[i + 19..].split(|c: char| !c.is_ascii_digit()).next()?.parse::<u32>().ok())
        .map(|v| v.saturating_sub(44));
    let class = line.split_whitespace().find(|w| w.contains('/') || w.matches('.').count() > 2).map(|c| c.trim_matches(|c| c == ':' || c == ','));
    let blamed = class.and_then(|c| ctx.by_class(c));
    let title = match (blamed, needed) {
        (Some(m), Some(v)) => format!("{} needs Java {v}", m.name),
        (None, Some(v)) => format!("Needs Java {v}"),
        (Some(m), None) => format!("{} needs a newer Java", m.name),
        (None, None) => "Wrong Java version".into(),
    };
    let hint = match blamed {
        Some(m) => format!(
            "{} was built for a newer Java than this Minecraft version uses. It's probably a build for a newer Minecraft: install the right build or turn it off. If you set a custom Java path, clear it so Glade picks Java itself.",
            m.name
        ),
        None => "Clear the custom Java path in the profile (or in Settings) so Glade downloads the Java this version needs.".into(),
    };
    let r = report(title, hint).show(&[line]);
    Some(match blamed {
        Some(m) => r.blame_file(m),
        None => r,
    })
}

fn memory(text: &[&str], exit_code: i32) -> Option<CrashReport> {
    if let Some(line) = text.iter().find(|l| l.contains("java.lang.OutOfMemoryError")) {
        return Some(
            report("Ran out of memory", "Give this profile more RAM (Edit profile, Memory), or remove heavy mods, shaders or big resource packs.")
                .show(&[line]),
        );
    }
    if let Some(line) = text.iter().find(|l| l.contains("Could not reserve enough space") || l.contains("Could not create the Java Virtual Machine")) {
        return Some(report("Not enough free memory", "The profile's RAM setting is more than your PC can give. Lower it in Edit profile.").show(&[line]));
    }
    if exit_code == 137 && cfg!(unix) {
        return Some(report(
            "The system stopped the game (out of memory)",
            "Your PC ran low on memory. Close other programs, or lower the profile's RAM so the system has room.",
        ));
    }
    None
}

fn graphics(text: &[&str], fatal: &[&str]) -> Option<CrashReport> {
    const DRIVER: &[(&str, &str)] = &[
        ("atio6axx", "AMD"),
        ("atioglxx", "AMD"),
        ("amdvlk", "AMD"),
        ("radeonsi", "AMD"),
        ("ig9icd", "Intel"),
        ("ig7icd", "Intel"),
        ("ig75icd", "Intel"),
        ("iris_dri", "Intel"),
        ("i965_dri", "Intel"),
        ("nvoglv", "NVIDIA"),
        ("libnvidia", "NVIDIA"),
    ];
    if let Some(i) = fatal.iter().position(|l| l.contains("Problematic frame:")) {
        // the frame itself is on the next line: "# C  [atio6axx.dll+0x1234]"
        let frame = fatal.get(i + 1).copied().unwrap_or(fatal[i]);
        let lower = frame.to_ascii_lowercase();
        if let Some((_, vendor)) = DRIVER.iter().find(|(file, _)| lower.contains(file)) {
            return Some(
                report(
                    format!("{vendor} graphics driver crashed"),
                    format!("Update your {vendor} graphics driver. If it keeps happening, turn off shaders or try without performance mods that change rendering."),
                )
                .show(&[frame]),
            );
        }
        if lower.contains("lwjgl") || lower.contains("opengl") || lower.contains("libgl") {
            return Some(report("Graphics driver crashed", "Update your graphics driver, and turn off shaders if you use them.").show(&[frame]));
        }
    }
    const GL: &[&str] = &[
        "GLFW error 65542",
        "GLFW error 65543",
        "Pixel format not accelerated",
        "No OpenGL context",
        "WGL: The driver does not appear to support OpenGL",
        "Failed to create a Vulkan",
        "Couldn't set pixel format",
    ];
    let line = text.iter().find(|l| GL.iter().any(|g| l.contains(g)))?;
    Some(
        report(
            "Graphics driver problem",
            "Minecraft couldn't start your graphics card. Install or update the graphics driver from AMD, Intel or NVIDIA (not the one Windows picks).",
        )
        .show(&[line]),
    )
}

// ---------------------------------------------------------------- crashes inside mods

/// "Mixin apply for mod sodium failed", "... from mod sodium failed injection check"
fn mixin(text: &[&str], ctx: &Context) -> Option<CrashReport> {
    for line in text {
        let l = line.trim();
        let id = if let Some(i) = l.find("Mixin apply for mod ") {
            l[i + 20..].split_whitespace().next()
        } else if l.contains("Mixin") && l.contains(" from mod ") {
            l[l.find(" from mod ")? + 10..].split(|c: char| c.is_whitespace() || c == ',' || c == ']').next()
        } else {
            None
        };
        let Some(id) = id.filter(|i| !i.is_empty() && *i != "(unknown)") else { continue };
        let name = ctx.name_for(id);
        return Some(
            report(
                format!("{name} failed to load"),
                format!("{name} couldn't patch the game. This build may be for another Minecraft version, or it clashes with another mod. Update it, or turn it off."),
            )
            .blame(ctx, id)
            .show(&[line]),
        );
    }
    None
}

fn damaged_file(text: &[&str], ctx: &Context) -> Option<CrashReport> {
    let line = text.iter().find(|l| {
        l.contains("ZipException") || l.contains("zip END header not found") || l.contains("Invalid or corrupt jarfile") || l.contains("error in opening zip file")
    })?;
    let jar = ctx.mods.iter().find(|m| text.iter().any(|l| l.contains(&m.file)));
    let r = report(
        match jar {
            Some(m) => format!("{} is damaged", m.name),
            None => "A game file is damaged".into(),
        },
        match jar {
            Some(m) => format!("{} didn't download completely. Remove it and install it again from the Mods page.", m.name),
            None => "A download didn't finish. Launch again so Glade re-downloads it; if a mod is named in the log, reinstall that mod.".into(),
        },
    )
    .show(&[line]);
    Some(match jar {
        Some(m) => r.blame_file(m),
        None => r,
    })
}

/// Forge and NeoForge crash reports: "Suspected Mods: Sodium (sodium), Version: 0.6"
fn suspected(text: &[&str], ctx: &Context) -> Option<CrashReport> {
    let line = text.iter().find(|l| l.trim_start().starts_with("Suspected Mod"))?;
    let list = line.split_once(':')?.1.trim();
    if list.is_empty() || list.eq_ignore_ascii_case("none") || list.starts_with("NONE") {
        return None;
    }
    let first = list.split(',').next()?.trim();
    let id = paren(first).unwrap_or(first);
    let name = first.split(" (").next().unwrap_or(first).trim();
    let name = if name.is_empty() { ctx.name_for(id) } else { name.to_string() };
    Some(
        report(format!("{name} crashed the game"), format!("The crash happened inside {name}. Update it from the Mods page or turn it off."))
            .blame(ctx, id)
            .show(&[line]),
    )
}

const NOT_MODS: &[&str] = &[
    "java.", "javax.", "jdk.", "sun.", "com.sun.", "net.minecraft.", "com.mojang.", "net.fabricmc.", "org.quiltmc.", "org.spongepowered.",
    "net.minecraftforge.", "net.neoforged.", "cpw.mods.", "org.lwjgl.", "it.unimi.", "com.google.", "org.apache.", "io.netty.", "org.slf4j.",
    "kotlin.", "org.objectweb.", "com.llamalad7.", "oshi.", "org.joml.",
];

/// The stack trace: Fabric mixin handlers carry their mod id
/// (`handler$zbd000$sodium$onRender`); otherwise the first frame inside a mod jar.
fn stack(text: &[&str], ctx: &Context) -> Option<CrashReport> {
    let frames: Vec<&str> = text.iter().filter_map(|l| l.trim_start().strip_prefix("at ")).collect();
    let exception = text.iter().find(|l| is_exception_line(l)).copied();
    for frame in &frames {
        let method = frame.split('(').next().unwrap_or(frame);
        let name = method.rsplit('.').next().unwrap_or("");
        let parts: Vec<&str> = name.split('$').collect();
        // handler$<hash>$<modid>$<name>, also redirect$, modify$, wrapOperation$ ...
        if parts.len() >= 4 && parts[1].len() >= 6 && parts[1].chars().all(|c| c.is_ascii_alphanumeric()) {
            let id = parts[2];
            if ctx.by_id(id).is_some() || !id.is_empty() && id.chars().all(|c| c.is_ascii_lowercase() || c.is_ascii_digit() || c == '_' || c == '-') {
                let mod_name = ctx.name_for(id);
                return Some(
                    report(format!("{mod_name} crashed the game"), format!("The crash happened in code {mod_name} adds to Minecraft. Update it from the Mods page or turn it off."))
                        .blame(ctx, id)
                        .show(&[exception.unwrap_or(""), frame]),
                );
            }
        }
    }
    for frame in &frames {
        let method = frame.split('(').next().unwrap_or(frame);
        let class = method.rsplit_once('.').map_or(method, |(c, _)| c);
        if NOT_MODS.iter().any(|p| class.starts_with(p)) {
            continue;
        }
        if let Some(m) = ctx.by_class(class) {
            return Some(
                report(format!("{} crashed the game", m.name), format!("The crash happened inside {}. Update it from the Mods page or turn it off.", m.name))
                    .blame_file(m)
                    .show(&[exception.unwrap_or(""), frame]),
            );
        }
    }
    None
}

/// Mods made for another loader don't load, and are a common cause of "my mod
/// does nothing" or a missing-dependency crash.
fn wrong_loader(ctx: &Context) -> Option<CrashReport> {
    let fits = |m: &ModFile| match ctx.loader.as_str() {
        "fabric" => m.kinds.contains(&"fabric"),
        "quilt" => m.kinds.iter().any(|k| *k == "quilt" || *k == "fabric"),
        "forge" => m.kinds.contains(&"forge"),
        "neoforge" => m.kinds.iter().any(|k| *k == "neoforge" || *k == "forge"),
        _ => true,
    };
    let m = ctx.mods.iter().find(|m| !m.kinds.is_empty() && !fits(m))?;
    let made_for = match m.kinds[0] {
        "fabric" => "Fabric",
        "quilt" => "Quilt",
        "forge" => "Forge",
        _ => "NeoForge",
    };
    Some(
        report(
            format!("{} is a {made_for} mod", m.name),
            format!("This profile doesn't run {made_for} mods. Remove {} and install the version for this profile's loader from the Mods page.", m.name),
        )
        .blame_file(m),
    )
}

// ---------------------------------------------------------------- reading the profile

/// Reads every jar in a mods folder: its mod ids, name, loader and packages.
pub fn scan_mods(dir: &Path) -> Vec<ModFile> {
    let Ok(entries) = std::fs::read_dir(dir) else { return vec![] };
    let mut out = Vec::new();
    for entry in entries.flatten() {
        let file = entry.file_name().to_string_lossy().into_owned();
        if !file.ends_with(".jar") {
            continue;
        }
        if let Some(m) = read_jar(&entry.path(), file) {
            out.push(m);
        }
    }
    out
}

fn read_jar(path: &Path, file: String) -> Option<ModFile> {
    let mut zip = zip::ZipArchive::new(std::fs::File::open(path).ok()?).ok()?;
    let mut m = ModFile { name: file.trim_end_matches(".jar").to_string(), file, ..Default::default() };
    let read = |zip: &mut zip::ZipArchive<std::fs::File>, name: &str| -> Option<String> {
        let mut s = String::new();
        zip.by_name(name).ok()?.read_to_string(&mut s).ok()?;
        Some(s)
    };
    if let Some(json) = read(&mut zip, "fabric.mod.json").and_then(|s| serde_json::from_str::<serde_json::Value>(&s).ok()) {
        m.kinds.push("fabric");
        if let Some(id) = json["id"].as_str() {
            m.ids.push(id.into());
        }
        if let Some(name) = json["name"].as_str() {
            m.name = name.into();
        }
        for p in json["provides"].as_array().into_iter().flatten().filter_map(|p| p.as_str()) {
            m.ids.push(p.into());
        }
    }
    if let Some(json) = read(&mut zip, "quilt.mod.json").and_then(|s| serde_json::from_str::<serde_json::Value>(&s).ok()) {
        m.kinds.push("quilt");
        if let Some(id) = json["quilt_loader"]["id"].as_str() {
            m.ids.push(id.into());
        }
        if let Some(name) = json["quilt_loader"]["metadata"]["name"].as_str() {
            m.name = name.into();
        }
    }
    for (toml, kind) in [("META-INF/neoforge.mods.toml", "neoforge"), ("META-INF/mods.toml", "forge")] {
        if let Some(text) = read(&mut zip, toml) {
            m.kinds.push(kind);
            let (ids, names) = toml_mods(&text);
            if m.ids.is_empty() {
                if let Some(n) = names.first() {
                    m.name = n.clone();
                }
            }
            m.ids.extend(ids);
        }
    }
    // packages: the first three folders of its classes
    let mut packages: HashMap<String, ()> = HashMap::new();
    for name in zip.file_names() {
        if !name.ends_with(".class") || name.starts_with("META-INF") {
            continue;
        }
        let parts: Vec<&str> = name.split('/').collect();
        if parts.len() < 3 {
            continue;
        }
        let depth = (parts.len() - 1).min(3);
        packages.insert(parts[..depth].join("/"), ());
        if packages.len() > 64 {
            break;
        }
    }
    m.packages = packages
        .into_keys()
        .filter(|p| !NOT_MODS.iter().any(|n| p.replace('/', ".").starts_with(n) || n.trim_end_matches('.').replace('.', "/") == *p))
        .collect();
    m.ids.dedup();
    Some(m)
}

/// `modId="jei"` and `displayName="Just Enough Items"` from a mods.toml.
fn toml_mods(text: &str) -> (Vec<String>, Vec<String>) {
    let value = |line: &str, key: &str| -> Option<String> {
        let rest = line.trim().strip_prefix(key)?.trim_start().strip_prefix('=')?.trim();
        let q = rest.chars().next()?;
        if q != '"' && q != '\'' {
            return None;
        }
        let end = rest[1..].find(q)? + 1;
        Some(rest[1..end].to_string())
    };
    let mut ids = Vec::new();
    let mut names = Vec::new();
    let mut in_mods = false;
    for line in text.lines() {
        let t = line.trim();
        if t.starts_with('[') {
            in_mods = t == "[[mods]]";
            continue;
        }
        if !in_mods {
            continue;
        }
        if let Some(v) = value(t, "modId") {
            ids.push(v);
        }
        if let Some(v) = value(t, "displayName") {
            names.push(v);
        }
    }
    (ids, names)
}

/// The newest file in `dir` whose name starts with `prefix`, if written after `since` (unix seconds).
pub fn newest_file(dir: &Path, prefix: &str, since: i64) -> Option<String> {
    let mut best: Option<(std::time::SystemTime, std::path::PathBuf)> = None;
    for e in std::fs::read_dir(dir).ok()?.flatten() {
        if !e.file_name().to_string_lossy().starts_with(prefix) {
            continue;
        }
        let Ok(modified) = e.metadata().and_then(|m| m.modified()) else { continue };
        let secs = modified.duration_since(std::time::UNIX_EPOCH).map(|d| d.as_secs() as i64).unwrap_or(0);
        if secs + 2 < since {
            continue;
        }
        if best.as_ref().map_or(true, |(t, _)| modified > *t) {
            best = Some((modified, e.path()));
        }
    }
    let text = std::fs::read(best?.1).ok()?;
    // keep the useful part of very long reports
    let text = String::from_utf8_lossy(&text[..text.len().min(400_000)]).into_owned();
    Some(text)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn lines(s: &[&str]) -> Vec<String> {
        s.iter().map(|l| l.to_string()).collect()
    }

    fn sodium() -> ModFile {
        ModFile {
            file: "sodium-fabric-0.6.0.jar".into(),
            name: "Sodium".into(),
            ids: vec!["sodium".into()],
            kinds: vec!["fabric"],
            packages: vec!["net/caffeinemc/mods".into()],
        }
    }

    fn ctx() -> Context {
        Context { mods: vec![sodium()], loader: "fabric".into(), ..Default::default() }
    }

    #[test]
    fn clean_exit() {
        assert_eq!(analyze(&[], 0, None), None);
    }

    #[test]
    fn finds_oom() {
        let l = lines(&["[INFO] hi", "Exception: java.lang.OutOfMemoryError: Java heap space"]);
        assert_eq!(analyze(&l, 1, None).unwrap().title, "Ran out of memory");
    }

    #[test]
    fn unknown_crash() {
        assert!(analyze(&lines(&["boom"]), 255, None).unwrap().title.contains("255"));
    }

    #[test]
    fn fabric_missing_dependency() {
        let l = lines(&[
            "Incompatible mods found!",
            "\t - Mod 'Sodium' (sodium) 0.6.0 requires any version of 'Fabric API' (fabric-api), which is missing!",
        ]);
        let r = analyze(&l, 1, Some(&ctx())).unwrap();
        assert_eq!(r.title, "Sodium needs Fabric API");
        assert_eq!(r.mod_file.as_deref(), Some("sodium-fabric-0.6.0.jar"));
    }

    #[test]
    fn fabric_wrong_minecraft() {
        let l = lines(&["\t - Mod 'Sodium' (sodium) 0.5.3 requires version 1.20.1 of 'Minecraft' (minecraft), but only the wrong version is present: 1.21.1!"]);
        let r = analyze(&l, 1, Some(&ctx())).unwrap();
        assert_eq!(r.title, "Sodium is for a different Minecraft version");
    }

    #[test]
    fn forge_missing_dependency() {
        let l = lines(&["\tMod ID: 'jei', Requested by: 'emi_jei', Expected range: '[19,)', Actual version: '[MISSING]'"]);
        let r = analyze(&l, 1, None).unwrap();
        assert_eq!(r.title, "emi_jei needs jei");
    }

    #[test]
    fn mixin_names_the_mod() {
        let l = lines(&["Caused by: org.spongepowered.asm.mixin.transformer.throwables.MixinTransformerError: Mixin apply for mod sodium failed sodium.mixins.json:Foo"]);
        let r = analyze(&l, 1, Some(&ctx())).unwrap();
        assert_eq!(r.title, "Sodium failed to load");
        assert_eq!(r.culprit.as_deref(), Some("Sodium"));
    }

    #[test]
    fn mixin_handler_in_stack() {
        let l = lines(&[
            "java.lang.NullPointerException: Cannot invoke \"Object.hashCode()\"",
            "\tat net.minecraft.client.renderer.LevelRenderer.handler$zbd000$sodium$onRender(LevelRenderer.java:1234)",
        ]);
        let r = analyze(&l, 1, Some(&ctx())).unwrap();
        assert_eq!(r.title, "Sodium crashed the game");
    }

    #[test]
    fn frame_inside_mod_jar() {
        let l = lines(&[
            "java.lang.IllegalStateException: oops",
            "\tat java.base/java.util.Objects.requireNonNull(Objects.java:1)",
            "\tat net.caffeinemc.mods.sodium.client.render.Thing.go(Thing.java:10)",
        ]);
        let r = analyze(&l, 1, Some(&ctx())).unwrap();
        assert_eq!(r.culprit.as_deref(), Some("Sodium"));
    }

    #[test]
    fn java_class_version() {
        let l = lines(&["java.lang.UnsupportedClassVersionError: net/caffeinemc/mods/sodium/Foo has been compiled by a more recent version of the Java Runtime (class file version 69.0), this version of the Java Runtime only recognizes class file versions up to 65.0"]);
        let r = analyze(&l, 1, Some(&ctx())).unwrap();
        assert_eq!(r.title, "Sodium needs Java 25");
    }

    #[test]
    fn suspected_mods_line() {
        let report_text = "Description: Rendering overlay\n\nSuspected Mods: Create (create), Version: 0.5\n";
        let c = Context { crash_report: Some(report_text.into()), ..Default::default() };
        let r = analyze(&[], 1, Some(&c)).unwrap();
        assert_eq!(r.title, "Create crashed the game");
    }

    #[test]
    fn gpu_driver() {
        let c = Context { fatal_log: Some("# Problematic frame:\n# C  [atio6axx.dll+0x1234]".into()), ..Default::default() };
        assert_eq!(analyze(&[], -1073741819, Some(&c)).unwrap().title, "AMD graphics driver crashed");
    }

    #[test]
    fn wrong_loader_mod() {
        let mut m = sodium();
        m.kinds = vec!["forge"];
        let c = Context { mods: vec![m], loader: "fabric".into(), ..Default::default() };
        assert_eq!(analyze(&lines(&["boom"]), 1, Some(&c)).unwrap().title, "Sodium is a Forge mod");
    }

    #[test]
    fn toml_parsing() {
        let (ids, names) = toml_mods("modLoader=\"javafml\"\n[[mods]]\nmodId=\"jei\"\ndisplayName=\"Just Enough Items\"\n[[dependencies.jei]]\nmodId=\"minecraft\"\n");
        assert_eq!(ids, vec!["jei"]);
        assert_eq!(names, vec!["Just Enough Items"]);
    }
}
