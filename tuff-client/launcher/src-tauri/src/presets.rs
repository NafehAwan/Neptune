//! Glade profile presets: ready-made Fabric profiles, each in its own folder,
//! with a researched set of mods and video settings to match.
//!
//! Mods are Modrinth slugs. Anything without a build for the profile's
//! Minecraft version is skipped at install time and reported to the player.

use serde::Serialize;

#[derive(Serialize, Clone, Debug)]
#[serde(rename_all = "camelCase")]
pub struct Preset {
    pub id: &'static str,
    pub name: &'static str,
    pub tagline: &'static str,
    /// What the player gets, in a few words each.
    pub highlights: &'static [&'static str],
    pub icon: &'static str,
    pub mods: &'static [&'static str],
    pub shaders: &'static [&'static str],
    pub resourcepacks: &'static [&'static str],
    /// options.txt values written for a fresh profile.
    #[serde(skip)]
    pub options: &'static [(&'static str, &'static str)],
}

/// Rendering, memory and loading fixes that change nothing about how the game looks.
const CORE: &[&str] = &[
    "fabric-api",
    "sodium",          // modern renderer, the biggest FPS gain
    "lithium",         // game logic: ticking, AI, physics
    "ferrite-core",    // memory use
    "modernfix",       // faster launch, less memory
    "immediatelyfast", // HUD, text and entity batching
    "entityculling",   // skips entities and block entities you can't see
    "moreculling",     // culls hidden block faces, leaves and item frames
    "dynamic-fps",     // throttles the game when unfocused or minimised
    "badoptimizations", // small client-side wins (lightmap, sky, clouds)
];

const PERFORMANCE: &[&str] = CORE;

const PERFORMANCE_PLUS: &[&str] = &[
    "fabric-api", "sodium", "lithium", "ferrite-core", "modernfix", "immediatelyfast", "entityculling",
    "moreculling", "dynamic-fps", "badoptimizations",
    "c2me-fabric",           // multithreaded chunk loading and generation
    "scalablelux",           // faster lighting engine
    "krypton",               // leaner networking
    "sodium-extra",          // turn off costly effects (fog, animations, particles)
    "reeses-sodium-options", // a cleaner video settings screen
    "ebe",                   // Enhanced Block Entities: chests and signs drawn as fast blocks
    "clumps",                // merges XP orbs
];

const PERFORMANCE_PLUS_PLUS: &[&str] = &[
    "fabric-api", "sodium", "lithium", "ferrite-core", "modernfix", "immediatelyfast", "entityculling",
    "moreculling", "dynamic-fps", "badoptimizations", "c2me-fabric", "scalablelux", "krypton",
    "sodium-extra", "reeses-sodium-options", "ebe", "clumps",
    "noisium",   // faster world generation
    "vmp-fabric", // faster entity tracking and ticking in big worlds
    "ksyxis",    // worlds open faster (no spawn chunk preloading)
];

const SCENIC: &[&str] = &[
    "fabric-api", "sodium", "lithium", "ferrite-core", "modernfix", "immediatelyfast", "entityculling",
    "dynamic-fps",
    "iris",                   // shader support
    "continuity",             // connected textures (glass, bookshelves)
    "lambdynamiclights",      // held torches light up the world
    "entity-model-features",  // smoother, animated mob models (with Fresh Animations)
    "entitytexturefeatures",  // emissive and random mob textures
    "fallingleaves",          // leaves drift down from trees
    "visuality",              // small particles: sparkles, water circles, shine
];

pub const ALL: &[Preset] = &[
    Preset {
        id: "performance",
        name: "Performance",
        tagline: "Smooth everyday play. The essential optimisation mods, and the game looks exactly the same.",
        highlights: &["Sodium renderer", "Faster ticking and loading", "Less memory", "Vanilla look"],
        icon: "tuff",
        mods: PERFORMANCE,
        shaders: &[],
        resourcepacks: &[],
        options: &[("enableVsync", "false"), ("maxFps", "260")],
    },
    Preset {
        id: "performance-plus",
        name: "Performance+",
        tagline: "More FPS on average PCs: multithreaded chunks, a faster lighting engine and lighter video settings.",
        highlights: &["Everything in Performance", "Faster chunks and lighting", "Fast graphics", "8 chunk view"],
        icon: "bulb",
        mods: PERFORMANCE_PLUS,
        shaders: &[],
        resourcepacks: &[],
        options: &[
            ("enableVsync", "false"), ("maxFps", "260"), ("renderDistance", "8"), ("simulationDistance", "6"),
            ("graphicsMode", "0"), ("renderClouds", "\"fast\""), ("particles", "1"), ("entityShadows", "false"),
            ("biomeBlendRadius", "1"),
        ],
    },
    Preset {
        id: "performance-plus-plus",
        name: "Performance++",
        tagline: "Maximum FPS for low-end PCs and laptops: every optimisation and the leanest settings.",
        highlights: &["Everything in Performance+", "Faster world generation", "Minimal effects", "6 chunk view"],
        icon: "diamond",
        mods: PERFORMANCE_PLUS_PLUS,
        shaders: &[],
        resourcepacks: &[],
        options: &[
            ("enableVsync", "false"), ("maxFps", "260"), ("renderDistance", "6"), ("simulationDistance", "5"),
            ("graphicsMode", "0"), ("renderClouds", "\"false\""), ("particles", "2"), ("entityShadows", "false"),
            ("biomeBlendRadius", "0"), ("mipmapLevels", "0"), ("ao", "false"), ("entityDistanceScaling", "0.5"),
        ],
    },
    Preset {
        id: "scenic",
        name: "Scenic",
        tagline: "Clean, good-looking graphics that still run well: soft shaders, connected textures and lively mobs.",
        highlights: &["Complementary shaders", "Connected textures", "Dynamic lights", "Fresh Animations"],
        icon: "grass",
        mods: SCENIC,
        shaders: &["complementary-reimagined"],
        resourcepacks: &["fresh-animations"],
        options: &[
            ("renderDistance", "12"), ("simulationDistance", "8"), ("graphicsMode", "1"), ("renderClouds", "\"true\""),
            ("particles", "0"), ("entityShadows", "true"), ("biomeBlendRadius", "2"), ("ao", "true"),
        ],
    },
];

pub fn find(id: &str) -> Option<&'static Preset> {
    ALL.iter().find(|p| p.id == id)
}

/// Writes the preset's video settings for a fresh profile, leaving an existing options.txt alone.
pub fn write_options(instance: &std::path::Path, preset: &Preset, resourcepacks: &[String]) -> std::io::Result<()> {
    let file = instance.join("options.txt");
    if file.exists() {
        return Ok(());
    }
    let mut lines: Vec<String> = preset.options.iter().map(|(k, v)| format!("{k}:{v}")).collect();
    if !resourcepacks.is_empty() {
        let packs: Vec<String> = std::iter::once("\"vanilla\"".to_string())
            .chain(std::iter::once("\"fabric\"".to_string()))
            .chain(resourcepacks.iter().map(|f| format!("\"file/{f}\"")))
            .collect();
        lines.push(format!("resourcePacks:[{}]", packs.join(",")));
    }
    std::fs::create_dir_all(instance)?;
    std::fs::write(file, lines.join("\n") + "\n")
}

/// Turns the installed shader pack on in Iris.
pub fn enable_shader(instance: &std::path::Path, shader_file: &str) -> std::io::Result<()> {
    let dir = instance.join("config");
    std::fs::create_dir_all(&dir)?;
    let file = dir.join("iris.properties");
    if file.exists() {
        return Ok(());
    }
    std::fs::write(file, format!("enableShaders=true\nshaderPack={shader_file}\n"))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn presets_have_unique_ids_and_mods() {
        let mut ids: Vec<_> = ALL.iter().map(|p| p.id).collect();
        ids.sort();
        ids.dedup();
        assert_eq!(ids.len(), ALL.len());
        for p in ALL {
            assert!(p.mods.contains(&"fabric-api"), "{} needs Fabric API", p.id);
            let mut mods = p.mods.to_vec();
            mods.sort();
            let before = mods.len();
            mods.dedup();
            assert_eq!(before, mods.len(), "{} lists a mod twice", p.id);
        }
    }

    #[test]
    fn options_are_written_once() {
        let dir = std::env::temp_dir().join(format!("glade-preset-test-{}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        let p = find("performance-plus").unwrap();
        write_options(&dir, p, &["Fresh.zip".into()]).unwrap();
        let text = std::fs::read_to_string(dir.join("options.txt")).unwrap();
        assert!(text.contains("renderDistance:8"));
        assert!(text.contains("\"file/Fresh.zip\""));
        std::fs::write(dir.join("options.txt"), "mine").unwrap();
        write_options(&dir, p, &[]).unwrap();
        assert_eq!(std::fs::read_to_string(dir.join("options.txt")).unwrap(), "mine");
        let _ = std::fs::remove_dir_all(&dir);
    }
}
