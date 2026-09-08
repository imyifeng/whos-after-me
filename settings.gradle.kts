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
    // Stonecutter workspace - see https://stonecutter.kikugie.dev/
    id("dev.kikugie.stonecutter") version "0.9.8"

    // Applies the correct Loom plugin id per anchor (spec v1 §6, ADR-0006):
    // `net.fabricmc.fabric-loom-remap` for the obfuscated era (<= 1.21.10),
    // `net.fabricmc.fabric-loom` for the no-remap era (>= 1.21.11).
    // https://codeberg.org/KikuGie/loom-back-compat
    id("dev.kikugie.loom-back-compat") version "0.4.2"

    // Lets Gradle download missing JDK toolchains (e.g. Java 25 for 26.x anchors).
    // https://github.com/gradle/foojay-toolchains
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

stonecutter {
    create(rootProject) {
        // The six v1 Anchors in ascending order (spec v1 §1, ADR-0006).
        versions("1.21.1", "1.21.4", "1.21.8", "1.21.11", "26.1.2", "26.2")
        vcsVersion = "26.2"
    }
}

rootProject.name = "whos-after-me"
