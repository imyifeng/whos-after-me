plugins {
    // This plugin applies the correct loom variant based on the Minecraft version
    // (`net.fabricmc.fabric-loom-remap` <= 1.21.10, `net.fabricmc.fabric-loom` >= 1.21.11).
    id("dev.kikugie.loom-back-compat")
}

// DO NOT set group = ...!
version = "${property("mod.version")}+${sc.current.version}"
base.archivesName = property("mod.id") as String

// Java level per anchor (spec v1 §6): 25 on 26.1+, 21 everywhere below that.
val requiredJava: JavaVersion = when {
    sc.current.parsed >= "26.1" -> JavaVersion.VERSION_25
    else -> JavaVersion.VERSION_21
}

repositories {
    /**
     * Restricts dependency search of the given [groups] to the [maven URL][url],
     * improving the setup speed.
     */
    fun strictMaven(url: String, alias: String, vararg groups: String) = exclusiveContent {
        forRepository { maven(url) { name = alias } }
        filter { groups.forEach(::includeGroup) }
    }
    strictMaven("https://www.cursemaven.com", "CurseForge", "curse.maven")
    strictMaven("https://api.modrinth.com/maven", "Modrinth", "maven.modrinth")
    strictMaven("https://maven.midnightdust.eu/releases", "MidnightDust", "eu.midnightdust")
    strictMaven("https://maven.terraformersmc.com/releases", "Terraformers", "com.terraformersmc")
}

dependencies {
    val fabricApiVersion: String = sc.properties["deps.fabric_api"]

    /**
     * Fetches only the required Fabric API modules to not waste time downloading all of
     * them for each version.
     * @see <a href="https://github.com/FabricMC/fabric">List of Fabric API modules</a>
     */
    fun fapi(vararg modules: String) {
        for (it in modules) modImplementation(fabricApi.module(it, fabricApiVersion))
    }

    minecraft("com.mojang:minecraft:${sc.current.version}")
    // Applies Mojang mappings on remap-era anchors; a no-op on the unobfuscated ones.
    loomx.applyMojangMappings()

    // `mod{DependencyType}` works on every anchor - loom-back-compat converts these
    // to plain dependencies on the no-remap era anchors.
    modImplementation("net.fabricmc:fabric-loader:${property("deps.fabric_loader")}")
    // Core modules that exist across every anchor; feature-specific modules
    // (rendering, networking, ...) are added by their tickets.
    // Keybind module id fork (spec v1 §6): Fabric API renamed the module (and its
    // helper class) in 26.1 - `fabric-key-binding-api-v1` -> `fabric-key-mapping-api-v1`.
    val keybindModule: String = if (sc.current.parsed >= "26.1") "fabric-key-mapping-api-v1" else "fabric-key-binding-api-v1"
    fapi(
        "fabric-lifecycle-events-v1",
        "fabric-resource-loader-v0",
        "fabric-content-registries-v0",
        "fabric-registry-sync-v0",
        // Toggle keybind registration (spec v1 §7, ADR-0003).
        keybindModule,
    )
    // The full Fabric API mod for dev runs: the built jar depends on the
    // `fabric`/`fabric-api` mod id, which only exists when the umbrella mod is present.
    modLocalRuntime("net.fabricmc.fabric-api:fabric-api:$fabricApiVersion")

    // MidnightConfig config library (ADR-0003): owns persistence and the config
    // screen. Jar-in-Jar bundled so users install nothing extra (spec v1 §1).
    val midnightlibVersion: String = sc.properties["deps.midnightlib"]
    val midnightlib = "eu.midnightdust:midnightlib:$midnightlibVersion"
    modImplementation(midnightlib)
    include(midnightlib)

    // ModMenu is an optional dependency (ADR-0003): the config screen opens from
    // the Mods list when it is present; without it the JSON file is the surface.
    // Compile-only so the mod never requires ModMenu at runtime.
    val modmenuVersion: String = sc.properties["deps.modmenu"]
    modCompileOnly("com.terraformersmc:modmenu:$modmenuVersion")
}

loom {
    runConfigs.all {
        preferGradleTask = true
        generateRunConfig = true
        runDirectory = rootProject.file("run") // Shares the run directory between versions
    }
}

java {
    withSourcesJar()
    sourceCompatibility = requiredJava
    targetCompatibility = requiredJava

    toolchain {
        vendor = JvmVendorSpec.ADOPTIUM
        languageVersion = JavaLanguageVersion.of(requiredJava.majorVersion)
    }
}

tasks {
    processResources {
        fun MutableMap<String, String>.register(key: String, property: String) {
            val value: String = sc.properties[property]
            inputs.property(key, value)
            set(key, value)
        }

        val props = buildMap {
            register("id", "mod.id")
            register("name", "mod.name")
            register("version", "mod.version")
            register("minecraft", "mod.mc_compat")
            // Fabric API mod id in `depends`: `fabric` <= 1.21.11, `fabric-api` >= 26.1.
            register("fapi_dep_id", "deps.fapi_dep_id")
            register("java_version", "mod.java")
        }

        filesMatching("fabric.mod.json") { expand(props) }
    }

    // Includes the license file in the built mod
    withType<Jar> {
        val name = project.property("mod.id")
        inputs.property("mod_id", name)
        from(rootProject.file("LICENSE")) { rename { "$it-$name" } }
    }

    register<Copy>("buildAndCollect") {
        group = "build"
        description = "Builds mod jars and copies results to `build/libs/{mod version}/`"

        inputs.property("version", project.property("mod.version"))
        // loomx.modJar returns the jar task for the applied loom variant
        from(loomx.modJar.flatMap { it.archiveFile })
        into(rootProject.layout.buildDirectory.file("libs/${project.property("mod.version")}"))
    }
}
