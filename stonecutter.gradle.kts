plugins {
    id("dev.kikugie.stonecutter")
}

// Active development version: 26.2 (spec v1 §9, ADR-0006). To switch the version the
// IDE and ad-hoc builds work against, either edit this line or run the generated
// `Set active project to <version>` task - see README.md.
stonecutter active "26.2"

// Per-anchor preprocessing parameters (https://stonecutter.kikugie.dev/wiki/config/params).
// The spec v1 §6 version forks are absorbed here, so later tickets never touch
// buildscripts to consume them.
stonecutter parameters {
    swaps["mod_version"] = "\"${property("mod.version")}\";"
    swaps["minecraft"] = "\"${node.metadata.version}\";"

    // HUD registration fork: `HudElementRegistry.addLast` on 1.21.6+,
    // `HudRenderCallback.EVENT` before that.
    constants["hud_registry"] = current.parsed >= "1.21.6"

    // Fabric API dependency id fork: the `fabric` mod id became `fabric-api` in 26.1.
    constants["fapi_modern_id"] = current.parsed >= "26.1"

    // Keybind category fork (spec v1 §7): `KeyMapping` categories were plain translated
    // strings until 1.21.10; from 1.21.11 they are registered `KeyMapping.Category`
    // records built from an `Identifier` (same shape through 26.x).
    constants["keymap_category_object"] = current.parsed >= "1.21.11"

    // Mojang renamed `ResourceLocation` to `Identifier` at 1.21.11 (verified against
    // the mapped jars); custom payload ids fork on the same boundary (spec v1 §4).
    constants["mojang_identifier"] = current.parsed >= "1.21.11"

    // NOTE on the spec v1 §6 "entity world accessor rename": the `getWorld` ->
    // `getEntityWorld` rename at 1.21.9 exists only in Yarn. The Mojang names this
    // codebase is written against kept `level()` on every anchor (verified against the
    // mapped jars for 1.21.1, 1.21.11, 26.1.2, and 26.2), so there is deliberately no
    // replacement for it - a previous `.level()` -> `.entityWorld()` rewrite produced
    // calls that compile on no anchor.
}

// Stonecutter 0.9 dropped the built-in chiseled tasks, so the spec v1 §9
// `chiseledBuild` command - one command building every anchor - is wired up here
// on the root project.
tasks.register("chiseledBuild") {
    group = "build"
    description = "Builds all anchors and collects the jars in `build/libs/`"
    dependsOn(subprojects.map { "${it.path}:buildAndCollect" })
}

// The plain-JUnit unit suites (ADR-0004, spec v1 §8) run in `check` on every anchor;
// this is the one-command all-anchor test run, mirroring `chiseledBuild`.
tasks.register("chiseledTest") {
    group = "verification"
    description = "Runs the unit test suite on every anchor"
    dependsOn(subprojects.map { "${it.path}:test" })
}
