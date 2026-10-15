//! Turns the tail of a game log into a readable explanation.

use serde::Serialize;

#[derive(Serialize, Clone, Debug, PartialEq)]
pub struct CrashReport {
    pub title: String,
    pub hint: String,
}

const PATTERNS: &[(&str, &str, &str)] = &[
    (
        "java.lang.OutOfMemoryError",
        "Ran out of memory",
        "Give this profile more RAM in its settings, or remove heavy mods and resource packs.",
    ),
    (
        "Could not reserve enough space",
        "Not enough free memory",
        "The RAM setting is higher than your system can provide. Lower it in the profile settings.",
    ),
    (
        "UnsupportedClassVersionError",
        "Wrong Java version",
        "A mod or the game needs a newer Java. Clear the custom Java path so Tuff picks the right one.",
    ),
    (
        "Incompatible mods found",
        "Incompatible mods",
        "A mod is missing a dependency or doesn't support this version. Check the console for the mod name.",
    ),
    (
        "Mixin apply failed",
        "Two mods conflict",
        "A mod failed to patch the game. Disable recently added mods one by one to find the conflict.",
    ),
    (
        "MixinApplyError",
        "Two mods conflict",
        "A mod failed to patch the game. Disable recently added mods one by one to find the conflict.",
    ),
    (
        "GLFW error 65542",
        "Graphics driver problem",
        "Your GPU driver doesn't expose OpenGL. Install or update your graphics drivers.",
    ),
    (
        "Pixel format not accelerated",
        "Graphics driver problem",
        "Your GPU driver doesn't expose OpenGL. Install or update your graphics drivers.",
    ),
    (
        "No OpenGL context",
        "Graphics driver problem",
        "Minecraft couldn't create an OpenGL context. Update your graphics drivers.",
    ),
    (
        "Failed to find Minecraft resource version",
        "Broken game files",
        "Delete the version from the data folder and launch again to re-download it.",
    ),
];

pub fn analyze(lines: &[String], exit_code: i32) -> Option<CrashReport> {
    if exit_code == 0 {
        return None;
    }
    for line in lines.iter().rev() {
        for (needle, title, hint) in PATTERNS {
            if line.contains(needle) {
                return Some(CrashReport { title: (*title).into(), hint: (*hint).into() });
            }
        }
    }
    Some(CrashReport {
        title: format!("Game exited with code {exit_code}"),
        hint: "Open the console to see the full log. Crash reports are saved in the profile's crash-reports folder.".into(),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn clean_exit() {
        assert_eq!(analyze(&[], 0), None);
    }

    #[test]
    fn finds_oom() {
        let lines = vec!["[INFO] hi".to_string(), "Exception: java.lang.OutOfMemoryError: Java heap space".into()];
        assert_eq!(analyze(&lines, 1).unwrap().title, "Ran out of memory");
    }

    #[test]
    fn unknown_crash() {
        assert!(analyze(&["boom".to_string()], 255).unwrap().title.contains("255"));
    }
}
