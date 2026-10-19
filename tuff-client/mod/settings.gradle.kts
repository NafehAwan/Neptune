pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net/")
        maven("https://maven.kikugie.dev/releases") { name = "KikuGie Releases" }
        maven("https://maven.kikugie.dev/snapshots") { name = "KikuGie Snapshots" }
    }
}

plugins {
    // One source tree, one jar per Minecraft version (https://stonecutter.kikugie.dev)
    id("dev.kikugie.stonecutter") version "0.9.8"
    // Picks the right Loom for 26.1+ (unobfuscated) and older versions (Mojang mappings)
    id("dev.kikugie.loom-back-compat") version "0.4.2"
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // Every Minecraft version Glade builds for; each needs a table in stonecutter.properties.toml.
        versions("1.21.11", "26.1", "26.2", "26.3")
        vcsVersion = "26.3"
    }
}

rootProject.name = "tuff-client"
