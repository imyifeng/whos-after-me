# Testing hooks and GameTest availability: Minecraft 1.21.1 through 26.2

Research for [issue #17](https://github.com/imyifeng/whos-after-me/issues/17), parent wayfinder ticket.
Date: 2026-09-08. Primary sources: `FabricMC/fabric-api` and `FabricMC/fabric-loom` source trees (per-version branches, inspected via the GitHub API), Fabric docs (`docs.fabricmc.net`), Fabric maven (`maven.fabricmc.net`), `minecraft.wiki` changelogs, and the repo's prior research reports. Build anchors per the toolchain decision: 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2.

## TL;DR

1. **Server-side GameTests work on all six anchors, unchanged mechanics end to end.** Fabric's `fabric-gametest-api-v1` exists on every anchor version. Registration is always the `fabric-gametest` entrypoint plus annotated instance methods, headless running is always `-Dfabric-api.gametest` on a dedicated-server run with `-Dfabric-api.gametest.report-file` for JUnit XML output. Only the *annotation and helper types* fork once, at 1.21.5, following Mojang's vanilla GameTest rework.
2. **There is exactly one API-shape fork that matters, and it sits between anchors 1.21.4 and 1.21.8.** 1.21.1/1.21.4 use the vanilla annotation (`net.minecraft.test.GameTest` in Yarn, `templateName=...EMPTY_STRUCTURE`) with `TestContext`/`complete()`; 1.21.5+ use Fabric's own `net.fabricmc.fabric.api.gametest.v1.GameTest` with `GameTestHelper`/`succeed()`. A small per-version test-source split (trivial under Stonecutter) absorbs it.
3. **Client game tests exist and can assert on the HUD — but not on 1.21.1.** `fabric-client-gametest-api-v1` (still `@Experimental`) runs a real client with deterministic settings, can spin up a dedicated server (`createServer()`), wait for clientbound packets (`waitForClientboundPackets` — a natural fit for asserting the polled-diff RESET/ADD/REMOVE protocol), and assert pixels via screenshot templates (`assertScreenshotContains(template)` with a tunable fuzzy matcher). It was added to the 1.21.4 line in December 2024 and is absent from the entire 1.21.1 line.
4. **Headless CI is solved at both ends of the range.** Old style: a Loom run config with the two `-D` properties. New style (Loom 1.10+, January 2025): `fabricApi.configureTests { ... }` generates a `gametest` source set, wires server tests into `check`/`build`, and provides `runClientGameTest`; the docs give a GitHub Actions recipe using production run tasks + XVfb virtual framebuffer. The DSL exists in both post-split Loom plugin ids (remap and no-remap), so one modern Loom covers all anchors.
5. **Stonecutter 0.9.x adds nothing test-specific — which is fine.** Each Minecraft version is a plain Gradle subproject, so JUnit test sets, Loom gametest runs, and `check` all work per subproject; cross-version batch runs must be scripted (Stonecutter documents batch builds, not batch tests).
6. **Realistic split for this mod:** threat detection and diff-sync logic are fully automatable (plain JUnit for pure logic, server gametests for real mob AI); the HUD is automatable on 1.21.4+ via client gametests, ideally asserting client *state* (threat list) plus one or two screenshot smoke checks; 1.21.1 HUD verification and all visual-quality concerns (arc alignment across aspect ratios, readability, feel) stay manual.

## 1. What exists per anchor

| Anchor | `fabric-gametest-api-v1` (server) | `fabric-client-gametest-api-v1` (client) | Server test API shape | Fabric API line (latest, per prior report) |
|---|---|---|---|---|
| 1.21.1 | yes | **no** | vanilla `@GameTest` (Yarn `net.minecraft.test.GameTest`, `templateName`) + `TestContext` | 0.116.x+1.21.1 |
| 1.21.4 | yes | yes (from 0.112.1+1.21.4, Dec 2024) | vanilla `@GameTest` + `TestContext` (Yarn names) | 0.119.4+1.21.4 |
| 1.21.8 | yes | yes | Fabric `@GameTest` (`gametest.v1`) + `GameTestHelper` | 0.136.1+1.21.8 |
| 1.21.11 | yes | yes | Fabric `@GameTest` + `GameTestHelper` | 0.141.6+1.21.11 |
| 26.1.2 | yes | yes | Fabric `@GameTest` + `GameTestHelper` | 0.146.1+26.1.2 (javadoc confirms both `gametest.v1` and `client.gametest.v1` packages) |
| 26.2 | yes | yes | Fabric `@GameTest` + `GameTestHelper` | 0.160.0+26.2 |

Verified by inspecting the module trees of each `FabricMC/fabric-api` version branch (`1.21.1`, `1.21.4`, `1.21.5`, `1.21.8`, `1.21.9`, `1.21.10`, `1.21.11`, `26.1`, `26.1.2`, `26.2`; the module `fabric-client-gametest-api-v1` first appears on the 1.21.4 branch). The server module is the same one Fabric API uses to test itself — its build makes every other module depend on the gametest API "to try and promote its usage".

## 2. Server-side GameTests (fabric-gametest-api-v1)

### 2.1 The 1.21.5 fork in API shape

The fork is caused by Mojang's vanilla GameTest rework in 1.21.5, which Fabric's module then mirrored:

- **1.21.1 / 1.21.4 (Yarn era).** Register an empty class under the `fabric-gametest` entrypoint; test methods are instance methods taking `net.minecraft.test.TestContext` (Yarn name) and annotated with the *vanilla* annotation `net.minecraft.test.GameTest`, normally with `templateName = FabricGameTest.EMPTY_STRUCTURE` ("fabric-gametest-api-v1:empty", an empty 8x8 structure); end tests with `context.complete()`. An optional `FabricGameTest` interface overrides test-method invocation. (Source: `fabric-gametest-api-v1` `package-info.java` and `FabricGameTest.java` on the `1.21.1` branch.)
- **1.21.5 through 26.2 (Mojang-names era).** Same entrypoint key, but tests are annotated with *Fabric's* `net.fabricmc.fabric.api.gametest.v1.GameTest`, whose attributes map onto vanilla's new `TestData` (`environment` referencing the new data-driven `test_environment` registry, `dimension`, `structure` defaulting to the empty structure, `maxTicks`, `setupTicks`, `required`, `rotation`, `manualOnly`, `maxAttempts`, `requiredSuccesses`, `skyAccess`, `padding`). Methods take `net.minecraft.gametest.framework.GameTestHelper` and end with `helper.succeed()`; `CustomTestMethodInvoker` replaces the old `FabricGameTest` hook. (Source: the same files on the `26.3` branch, identical on `1.21.5`+ branches.)

What Mojang changed in 1.21.5 (per the wiki changelog): a new `/test` command (`create`, `locate`, `reset*`, `run*`, `stop`, `verify`) replacing the old dev-only `/gametest`; test blocks and test instance blocks; test instances and test environments as registry assets (data-driven JSON, default `minecraft:default`); and a headless entry point `net.minecraft.gametest.Main` that "automatically starts a server, runs all available game tests and then exits" with `--tests`, `--report`, `--verify` flags. Later changes are minor and data-pack-level: 26.1 replaced the test-environment `time_of_day` setting with `clock_time` (world clocks); 26.2 added a `difficulty` test-environment setting. Neither affects mod test code.

Practical consequence for this repo: **one small version-conditional test source (or two per-version test sources under Stonecutter) covers the fork**; everything downstream — entrypoint, headless flags, report format — is identical across the range.

### 2.2 Headless running (all versions, unchanged)

From both eras' `package-info.java`, verbatim mechanics: "To run the server with GameTest enabled, add `-Dfabric-api.gametest` to the JVM arguments. The server works like the usual dedicated server, except that all experimental features are turned on by default." and "To export the test result, set `fabric-api.gametest.report-file` property to the output file path." The era docs both show the same Loom `runs { gametest { ... vmArg "-Dfabric-api.gametest" ... } }` pattern for a manual run config — meaning even the oldest anchor needs nothing newer than a custom Loom run.

## 3. Client-side game tests and HUD testing (fabric-client-gametest-api-v1)

### 3.1 Availability

- **Added** to the 1.21.4 line by PR [#4292](https://github.com/FabricMC/fabric-api/pull/4292) "Start work on Client Game Test API" (merged 2024-12-16 11:32 UTC); Fabric API `0.112.1+1.21.4` was published two hours later the same day, and the 0.113.0+1.21.4 notes already list client-gametest changes. The module is absent from the 1.21, 1.21.1, 1.21.2 and 1.21.3 branches — **the 1.21.1 anchor cannot run client gametests at any Fabric API version.**
- **Present and evolving continuously** on 1.21.4 → 26.2 (verified on each version branch) and current 26.3; the package `net.fabricmc.fabric.api.client.gametest.v1` is stable across all of them. Still marked `@ApiStatus.Experimental` as of the 26.3 branch — Fabric API could still break it in a minor line.

### 3.2 What it can do (source: 26.3 branch API sources; the same surface exists on 1.21.4+)

- Entry: `fabric-client-gametest` entrypoint, `FabricClientGameTest.runTest(ClientGameTestContext)`.
- **Real client, deterministic by construction**: tests run on a dedicated gametest thread with one server tick per client tick and one client tick per frame; the game stays paused until `waitTick()`/`waitTicks()`/`waitFor(Predicate<Minecraft>)`. Defaults are flattened for reproducibility: flat world, seed 1, structures off, time and weather frozen, render distance 5, clouds off, chunk fade off.
- **Server topologies for end-to-end tests**: `context.worldBuilder().create()` for singleplayer (giving `TestSingleplayerContext` with both `getConnection()` and `getServer()`), or `worldBuilder().createServer()` for a **real dedicated server** the test client connects to (`TestDedicatedServerContext`; `online-mode` is auto-set to false so no account is needed). This matches this mod's server-authoritative architecture exactly.
- **Packet-level synchronization**, directly relevant to the polled-diff protocol: `TestServerConnection.waitForClientboundPackets()`, `waitForServerboundPackets()`, `waitForClientboundEntityUpdates(EntityType<?>...)`.
- **HUD/render assertions**: `takeScreenshot(name | TestScreenshotOptions)`; `assertScreenshotEquals(template)` and `assertScreenshotContains(template)` (the latter returns the `Vector2i` where the template was found). Matching is template-subimage ("needle in haystack") based, with a default fuzzy algorithm (mean squared difference, threshold 0.005), an exact-match variant, and custom algorithm support.
- **Input and screens**: `getInput()` (keyboard/mouse injection), `clickScreenButton(translationKey)`, `tryClickScreenButton(...)`.
- **Arbitrary client-code inspection**: `runOnClient(Minecraft -> ...)` — allows asserting on the client's *state* (e.g. the mod's client-side threat list / registered HUD elements) without touching pixels.

Caveats for this mod's tests: the deterministic world defaults disable mob spawning (`SPAWN_MOBS=false`) and freeze time/weather, so threat-detection tests must override those settings (`setUseConsistentSettings(false)` or explicit gamerule/command changes via the server context); and golden-image screenshots are only as stable as the GPU/graphics stack — the fuzzy matcher and small template crops (just the indicator region) mitigate but do not eliminate cross-machine flakiness.

### 3.3 Is there any other HUD test harness?

No first-party alternative exists. Fabric docs state: "Minecraft provides the game test framework for testing server-side features. Fabric additionally provides client game tests for testing client-side features, similar to an end-to-end test." Vanilla has no client-side test framework. Homegrown options (headless client + Xvfb + external screenshot diffing, or unit-testing the `DrawContext` call sequence with mocks) duplicate what the client gametest API already provides; mock-based HUD-callback tests would only assert that registration happened, not that anything renders.

## 4. Unit tests

- **Plain JUnit** in a normal `src/test/java` source set works for pure logic (diff computation, payload encoding) with no Minecraft classes on the classpath.
- **Fabric Loader JUnit** (`net.fabricmc:fabric-loader-junit`, `useJUnitPlatform()`) boots a real classloader environment so registry-dependent classes (`EntityType`s, `ItemStack`) are usable in tests; docs: "Unit tests should be used to test components of your code ... while game tests spin up an actual Minecraft client and server to run your tests, which makes it suitable for testing features and gameplay." Unit tests "run automatically on every build, including CI builds such as GitHub Actions". On maven since loader 0.15, current 0.19.5 — covers every anchor's loader (0.16.9–0.19.3).

## 5. Headless / CI running

### 5.1 Loom's test DSL (Loom 1.10+, January 2025)

`fabricApi.configureTests` was added to Loom on 2025-01-02 (PR [#1240](https://github.com/FabricMC/fabric-loom/pull/1240) "Add DSL to configure Fabric API game tests"), i.e. it ships in the Loom 1.10 line and everything after. It is present in the current Loom 1.17 tree and is shared by both post-26.1 plugin ids (`LoomRemapGradlePlugin` and `LoomNoRemapGradlePlugin` sit in the same source tree) — so the toolchain chosen in the multi-version decision (one modern Loom, plugin id selected per version) exposes it for **all six anchors**, including 1.21.1.

What it generates (source: `FabricApiTesting.java`, Loom dev/1.17):

- A dedicated `gametest` source set (test mod with its own `fabric.mod.json`; `src/gametest/java` + `resources`).
- A server gametest run (system property `fabric-api.gametest`) wired as a dependency of `check` — so "Server game tests will be run automatically with the `build` Gradle task".
- A client gametest run (system property `fabric.client.gametest` + `testModResourcesPath`), invoked via `runClientGameTest`, with a `deleteGameTestRunDir` cleanup and automatic EULA acceptance (`eula = true` in the DSL "By setting this to true, you agree to the Minecraft EULA").
- Options: `createSourceSet`, `modId`, `enableGameTests` (default true), `enableClientGameTests` (default true), `eula`.

Projects pinned to contemporary Loom versions (1.7–1.9, what 1.21.1/1.21.4 templates used at release) instead configure runs manually via `loom.runs` + the two `-D` properties — the pattern documented in the 1.21.1-era `package-info.java`. Both styles work under Stonecutter because subprojects are plain Gradle projects.

### 5.2 GitHub Actions recipe (docs, current)

- Server gametests: any workflow that runs `build`/`check` already runs them.
- Client gametests: register `runProductionClientGameTest` (`net.fabricmc.loom.task.prod.ClientProductionRunTask`) with `productionRuntimeMods "net.fabricmc.fabric-api:fabric-api:..."` and jvmArg `-Dfabric.client.gametest`; XVfb "using a virtual framebuffer ... Defaults to true only on Linux and when the `CI` environment variable is set". Known flake: "game test may fail on GitHub Actions due to an error in the network synchronizer", workaround `-Dfabric.client.gametest.disableNetworkSynchronizer=true`. Screenshots land in `build/run/clientGameTest/screenshots` for artifact upload.
- Cost shape: each anchor's server gametest run boots a dedicated server (tens of seconds to minutes); client gametests boot a full client per anchor. Both multiply across six Stonecutter subprojects — a reason to gate client tests to a subset of anchors in the CI matrix decision.
- Vanilla fallback for 1.21.5+: `net.minecraft.gametest.Main` runs a server + all tests + exit headless from the (unobfuscated, 26.x) vanilla jar with `--tests`/`--report`/`--verify` — useful for vanilla-behavior probes, but mod tests still go through Fabric's runner.

## 6. Stonecutter 0.9.x and per-subproject tests

- Stonecutter's model is "each version you support is a Gradle subproject" under `versions/{name}/`, generated from the shared `src/` by preprocessing; there is a controller project and per-version `gradle.properties` (Stonecutter wiki, project setup).
- **The 0.9.x wiki has no testing page, no test-related tasks, and no batch test task** (navigation covers setup, preprocessing, dependencies, multiloader, Fletching Table; nothing about tests). Its own Fabric template contains no test sources.
- Consequence: testing behaves exactly like plain multi-project Gradle. Each subproject can declare its own test dependencies (e.g. Fabric API version with/without the client gametest module), its own Loom runs, and its own `test`/`check` tasks; the Loom `configureTests` DSL applies per subproject. Batch builds are Stonecutter's `chiseled*` tasks (per the repo's multi-version tooling research); an equivalent cross-version test runner (e.g. `chiseledCheck`-style aggregate over `:check` of each subproject) is a small custom task if wanted.
- Caveat from the toolchain research that also applies to tests: Stonecutter develops against one "active" version; other versions only compile/test when built in CI — so test runs across all six anchors must live in CI, not in local workflow.

## 7. What can be automated vs. manual for "Who's After Me"

**Automatable (cheap, every anchor):**
- Threat-set → polled-diff computation, RESET/ADD/REMOVE payload semantics (ADR-0002): pure logic, plain JUnit.
- Detection radius math, uniform threat rule (ADR-0001), provoked-neutral qualification given a target state: unit tests; registry-dependent variants via Fabric Loader JUnit.

**Automatable (server gametests, all anchors):**
- Real-mob integration: summon mobs, force them to target the player, tick the server, assert the detector's threat set (including special cases from the mob-coverage decision: creaking, jockeys/riders). Runs headless in `check`/CI via `-Dfabric-api.gametest` or the Loom DSL.

**Automatable (client gametests, 1.21.4/1.21.8/1.21.11/26.1.2/26.2 — not 1.21.1):**
- End-to-end: dedicated server with a mob targeting the player → `waitForClientboundPackets()` → assert the client's stored threat list via `runOnClient(...)` → optionally `assertScreenshotContains(template)` on a small HUD-region crop as a rendering smoke check. 1.21.1 has no client gametest module; its HUD path (`HudRenderCallback`) can be covered by unit-level registration tests plus manual scenarios, or treated as covered-by-proxy by 1.21.4 (same HUD API line per the API-landscape research).

**Manual-only (no harness exists):**
- Visual quality: arc geometry across viewport aspect ratios, triangle orientation legibility, radius-adjustment feel, GUI-scale/resolution permutations, color/opacity choices.
- Experience checks: HUD readability under real gameplay (movement, combat, effects), latency behavior of the 1-second poll window, performance feel on real hardware; 26.2's experimental Vulkan backend rendering.
- A short manual scenario script per release (spawn threats in a real world, verify indicator directions/distances) is the realistic substitute.

## 8. Options and trade-offs (for the later testing-strategy decision — no decision here)

| Option | Covers | Trade-offs |
|---|---|---|
| **A. Plain JUnit + Fabric Loader JUnit only** | Detection/diff/sync logic on all six anchors | Cheapest and fastest in CI; no real-mob AI, no network, no rendering coverage; HUD entirely manual |
| **B. A + server gametests via manual Loom run config (`-Dfabric-api.gametest`)** | Adds real mob-AI integration on all six anchors | Works on every Loom/FAPI version including 1.21.1; more config boilerplate, no DSL conveniences (no auto-`check` wiring, manual report-file plumbing); still no rendering coverage |
| **C. A + Loom `configureTests` (server + client gametests)** | Full stack end to end: mob AI, S2C packet sync, HUD rendering on the five 1.21.4+ anchors | One modern Loom serves all anchors; `waitForClientboundPackets` fits the polled-diff protocol exactly; screenshot templates give real render assertions; costs: client API is `@Experimental`, client runs are the slowest/flakiest part of CI (XVfb, network-synchronizer workaround, GPU variance), and 1.21.1 must fall back to B/A for HUD |
| **D. C + per-anchor golden-image suites in the CI matrix** | Same as C, plus pixel regression tracking across every anchor | Highest automation; high maintenance (goldens per anchor/GUI scale), cross-GPU flakiness, and six-way CI cost multiplication — likely only worth it for a handful of smoke templates rather than full HUD goldens |

Version-line splits that matter for any option: (1) test API shape fork at 1.21.5 (annotation/helper rename); (2) client gametests exist only from 1.21.4 onward; (3) the Loom test DSL needs Loom 1.10+ (satisfied by the chosen unified toolchain); (4) 26.x adds only data-pack-level gametest knobs (world clocks in 26.1, difficulty in 26.2).

## 9. Sources

- Fabric docs, "Automated Testing" (unit tests, game tests, Loom `configureTests`, GH Actions recipe): https://docs.fabricmc.net/develop/automatic-testing
- `fabric-gametest-api-v1` sources: `package-info.java` / `GameTest.java` / `FabricGameTest.java` on branches [1.21.1](https://github.com/FabricMC/fabric-api/tree/1.21.1/fabric-gametest-api-v1) and [26.3](https://github.com/FabricMC/fabric-api/tree/26.3/fabric-gametest-api-v1) (checked 1.21.4/1.21.5/1.21.8/1.21.11/26.1.2/26.2 branches for module presence)
- `fabric-client-gametest-api-v1` sources (26.3 branch): `package-info.java`, `FabricClientGameTest.java`, `ClientGameTestContext.java`, `TestServerConnection.java`, `TestWorldBuilder.java`, `TestSingleplayerContext.java`, `screenshot/TestScreenshotComparisonAlgorithm.java`
- Client gametest introduction: PR [#4292](https://github.com/FabricMC/fabric-api/pull/4292) (merged 2024-12-16); release dates for 0.112.1/0.113.0+1.21.4 via the GitHub releases API
- Loom test DSL: PR [#1240](https://github.com/FabricMC/fabric-loom/pull/1240) (2025-01-02); `configuration/fabricapi/FabricApiTesting.java` on `FabricMC/fabric-loom` branch `dev/1.17`
- Vanilla GameTest rework and headless entry point: [Java Edition 1.21.5](https://minecraft.wiki/w/Java_Edition_1.21.5); later gametest deltas: [26.1](https://minecraft.wiki/w/Java_Edition_26.1), [26.2](https://minecraft.wiki/w/Java_Edition_26.2)
- Fabric Loader JUnit on maven: https://maven.fabricmc.net/net/fabricmc/fabric-loader-junit/
- Stonecutter 0.9.x docs (subproject model; no test-specific features): https://stonecutter.kikugie.dev/wiki/ ; repo prior research: `docs/research/2026-09-08-multi-version-build-tooling.md` (branch `research/multi-version-tooling`) for the toolchain context and `docs/research/fabric-api-yarn-landscape-1.21.1-26.2.md` (branch `research/api-landscape`) for per-anchor Fabric API versions
