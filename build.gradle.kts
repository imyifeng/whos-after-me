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
    // Era boundary for Fabric API module ids (spec v1 §6): 26.1 renamed the keybind
    // module (and its helper class) and the block-view module, and added the
    // permission module. One switch serves every id fork below.
    val modernFapi: Boolean = sc.current.parsed >= "26.1"

    // Keybind module id fork (spec v1 §6): `fabric-key-binding-api-v1` -> `fabric-key-mapping-api-v1`.
    val keybindModule: String = if (modernFapi) "fabric-key-mapping-api-v1" else "fabric-key-binding-api-v1"
    // The Minecraft classes this mod compiles against (ServerPlayer, Level,
    // MinecraftServer) carry Fabric API interface injections, so javac needs the
    // injected interfaces' modules on the compile classpath.
    val blockGetterModule: String = if (modernFapi) "fabric-block-getter-api-v2" else "fabric-block-view-api-v2"
    val permissionModule: String? = if (modernFapi) "fabric-permission-api-v1" else null
    fapi(
        "fabric-lifecycle-events-v1",
        // Threat sync protocol (spec v1 §4, ADR-0002): payload registration, the
        // server hello receiver, and the client play-phase join event.
        "fabric-networking-api-v1",
        // Dimension-change state drop (spec v1 §4 lifecycle).
        "fabric-entity-events-v1",
        "fabric-resource-loader-v0",
        "fabric-content-registries-v0",
        "fabric-registry-sync-v0",
        "fabric-data-attachment-api-v1",
        blockGetterModule,
        // Toggle keybind registration (spec v1 §7, ADR-0003).
        keybindModule,
        *(permissionModule?.let { arrayOf(it) } ?: arrayOf<String>()),
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

    // Plain JUnit for the pure-logic unit suites (ADR-0004, spec v1 §8): the suites
    // need no Minecraft classes and run in `check` on every Anchor. No Fabric Loader
    // JUnit - registry-dependent coverage belongs to the GameTest levels.
    val junitBomVersion: String = sc.properties["deps.junit_bom"]
    testImplementation(platform("org.junit:junit-bom:$junitBomVersion"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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

    // Plain JUnit 5 on the unit test suites (ADR-0004). The `check` task runs them on
    // every Anchor; `chiseledTest` (stonecutter.gradle.kts) is the all-anchor run.
    test {
        useJUnitPlatform()
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
