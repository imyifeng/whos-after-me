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

    // Vector HUD geometry submission fork (issue #54), by GUI pipeline era (verified
    // against the mapped jars):
    // - 1.21.1 is the last immediate-mode GuiGraphics exposing a public
    //   `bufferSource()` - triangles go through `getBuffer(RenderType.gui())` plus an
    //   explicit batch end.
    // - 1.21.4 made the buffer source private and added `drawSpecial(Consumer)` instead
    //   (submit vertices, the call ends the batch) - covered by `!hud_registry` below.
    // - 1.21.6+ turned the GUI extraction-based: the graphics object only accumulates
    //   render state, so triangles ride a custom `GuiElementRenderState` submitted into
    //   the frame's `GuiRenderState` - which the graphics classes hold privately, hence
    //   the accessor mixins (`!fapi_modern_id` targets `GuiGraphics`, `fapi_modern_id`
    //   targets 26.1+'s `GuiGraphicsExtractor`).
    constants["gui_buffer_source"] = current.parsed < "1.21.2"

    // Fabric API dependency id fork: the `fabric` mod id became `fabric-api` in 26.1.
    constants["fapi_modern_id"] = current.parsed >= "26.1"

    // Keybind category fork (spec v1 §7): `KeyMapping` categories were plain translated
    // strings until 1.21.10; from 1.21.11 they are registered `KeyMapping.Category`
    // records built from an `Identifier` (same shape through 26.x).
    constants["keymap_category_object"] = current.parsed >= "1.21.11"

    // Mojang renamed `ResourceLocation` to `Identifier` at 1.21.11 (verified against
    // the mapped jars); custom payload ids fork on the same boundary (spec v1 §4).
    constants["mojang_identifier"] = current.parsed >= "1.21.11"

    // Server GameTest annotation fork (spec v1 §8, ADR-0004): Mojang reworked the vanilla
    // framework at 1.21.5, and Fabric's `gametest.v1` annotation replaced the vanilla one
    // (whose `template` attribute era the anchors below 1.21.5 use). Only the gametest
    // sources fork on this constant; the scenario bodies are shared.
    constants["gametest_api_v1"] = current.parsed >= "1.21.5"

    // 26.2 moved the built-in entity type constants from `EntityType` to a new `EntityTypes`
    // holder (verified against the mapped jars; 26.1.2 still has them on `EntityType`). The
    // gametest spawn factories resolve mob types through the era's holder class.
    constants["entity_types_modern"] = current.parsed >= "26.2"

    // 26.1 turned the mob target read into a validated one (`Mob.setTarget` drops creative
    // and spectator players via `asValidTarget`), while the vanilla in-level mock player
    // the gametests observe through is creative. The gametests build a survival observer
    // on those anchors instead of using the helper. Verified against the mapped jars.
    constants["modern_mock_player"] = current.parsed >= "26.1"

    // The 1.21.8 gametest assertion fork: that release's `GameTestHelper` took `Component`
    // messages only, and the String overloads are back on every later Anchor (verified
    // against the mapped jars). Only the gametest assertion utilities fork on this.
    constants["gametest_component_asserts"] = current.parsed >= "1.21.8" && current.parsed < "1.21.9"

    // Client GameTest availability (spec v1 §8, ADR-0004): `fabric-client-gametest-api-v1`
    // ships from 1.21.4 onward and is absent from the entire 1.21.1 line (testing research,
    // #17). Gates the client gametest sources, the module dependency, and the run.
    constants["client_gametest"] = current.parsed >= "1.21.4"

    // The screenshot smoke canary (ADR-0007): the pixel-level HUD check rides an
    // experimental API on a GPU, so it runs only on 26.2, the CI canary and default
    // development Anchor. Rolling the canary means editing this line (and the template).
    constants["client_gametest_canary"] = current.version == "26.2"

    // The spawn gamerule id fork: Mojang renamed the gamerule ids at 1.21.11
    // (`doMobSpawning` became `spawn_mobs`; verified against the mapped jars for
    // 1.21.4, 1.21.8, 1.21.11, 26.1.2, and 26.2 - 1.21.11's run rejected the old id).
    // Only the client gametest scenario's deterministic-defaults override forks on this.
    constants["spawn_gamerule_modern"] = current.parsed >= "1.21.11"

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

// The server gametests (ADR-0004, spec v1 §8) run headless per anchor via Loom's
// `gameTest` run, which `fabricApi.configureTests` wires into `check`; this is the
// one-command all-anchor gametest run, mirroring `chiseledBuild`/`chiseledTest`.
tasks.register("chiseledServerTest") {
    group = "verification"
    description = "Runs the headless server gametests on every anchor"
    dependsOn(subprojects.map { "${it.path}:runGameTest" })
}

// The client gametests (ADR-0004, ADR-0007, ticket #31) run via Loom's `clientGameTest`
// run on the anchors that ship the module (1.21.4+; 1.21.1 has none). The run task only
// exists on those anchors, and it is registered while each subproject configures, so the
// edge is attached after evaluation through its task path - the same string form as
// `chiseledServerTest` above (a lazy `tasks.matching(...).configureEach` would never
// fire: nothing realizes the subproject tasks while Gradle computes this task's graph).
val chiseledClientTest = tasks.register("chiseledClientTest") {
    group = "verification"
    description = "Runs the client gametests on every anchor that ships them (1.21.4+)"
}

subprojects {
    afterEvaluate {
        if (tasks.findByName("runClientGameTest") != null) {
            chiseledClientTest.get().dependsOn("${project.path}:runClientGameTest")
        }
    }
}
