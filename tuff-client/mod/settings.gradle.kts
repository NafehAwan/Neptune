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
        // Glade is private and built for Minecraft 1.21.11 only.
        versions("1.21.11")
        vcsVersion = "1.21.11"
    }
}

rootProject.name = "tuff-client"
