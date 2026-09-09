# Who's After Me

A Fabric mod that shows on-screen indicators for mobs that currently intend to attack
the player: one **Threat** indicator per threat, drawn on an orbit around the player,
visible through walls and verified server-side. See [CONTEXT.md](CONTEXT.md) for the
canonical vocabulary and [docs/spec/v1-spec.md](docs/spec/v1-spec.md) for the v1
specification.

## Workspace

One codebase produces one mod jar per supported Minecraft version (**Anchor**). The
multi-version workspace is managed by [Stonecutter](https://stonecutter.kikugie.dev/)
0.9.x with one subproject per Anchor under `versions/` (ADR-0006).

The six v1 Anchors and their toolchain eras:

| Anchor | Loom plugin | Class output | Notes |
|---|---|---|---|
| 1.21.1 | `net.fabricmc.fabric-loom-remap` | Java 21 | obfuscated era, Mojang mappings |
| 1.21.4 | `net.fabricmc.fabric-loom-remap` | Java 21 | obfuscated era, Mojang mappings |
| 1.21.8 | `net.fabricmc.fabric-loom-remap` | Java 21 | obfuscated era, Mojang mappings |
| 1.21.11 | `net.fabricmc.fabric-loom-remap` | Java 21 | last obfuscated release, last Yarn |
| 26.1.2 | `net.fabricmc.fabric-loom` | Java 25 | unobfuscated era, official names |
| 26.2 | `net.fabricmc.fabric-loom` | Java 25 | unobfuscated era, active development version |

### A note on the era boundary

ADR-0006 expected 1.21.11 to be the first unobfuscated Minecraft release, but Fabric
shipped 1.21.11 obfuscated (["Fabric for Minecraft 1.21.11"](https://fabricmc.net/2025/12/05/12111.html)):
26.1 is the first unobfuscated version, and this is also where Fabric API switched its
jar packaging and dropped the `fabric` mod id. The workspace therefore keeps 1.21.11 in
the remapping era (which is also what the `dev.kikugie.loom-back-compat` defaults and
Fabric API packaging require - a no-remap build could not consume Fabric API for that
version). A spec/ADR amendment is tracked separately; this split is the only one that
produces valid jars for all six Anchors.

## Building

```sh
./gradlew chiseledBuild
```

builds every Anchor in one command and collects the jars into `build/libs/<mod
version>/` (for example `whos_after_me-0.1.0+26.2.jar`). `./gradlew chiseledBuild`
plus `check` per Anchor is what CI runs.

Other useful commands:

- `./gradlew :<anchor>:build` - build a single Anchor (e.g. `:26.2:build`).
- `./gradlew :<anchor>:runClient` / `runServer` - dev-run a single Anchor.
- `./gradlew chiseledTest` - run the plain-JUnit unit suite on every Anchor (the suite
  also runs on each Anchor via `check`).
- `./gradlew chiseledServerTest` - run the headless server gametests on every Anchor
  (they also run on each Anchor via `check`, since Loom wires its `gameTest` run into
  `check`). Each Anchor boots its own dedicated server with an isolated run directory
  (`versions/<anchor>/build/run/gameTest`), so the generated worlds never clash across
  Anchors or with the shared root `run/`. A JUnit XML report lands in
  `versions/<anchor>/build/reports/gametest/report.xml`.

## Testing (ADR-0004, spec v1 §8)

The suites follow the testing strategy signed off in ADR-0004:

- **Plain JUnit** (`src/test`) - the pure-logic suites: polled-diff engine, payload
  codec, radius predicate, clear-grace state machine, cap selection, orbit geometry.
  No Minecraft classes; run in `check` on every Anchor.
- **Server gametests** (`src/gametest`) - the six real-mob integration tests from spec
  §8: melee mob -> Threat, brain-memory mob -> Threat, provoked neutral -> Threat once
  angered, ranged mob -> Threat, out-of-radius mob -> no Threat, cleared target held
  through the grace window then dropped. They drive the production detection engine
  through `ThreatDetector.observe` (the exact END_SERVER_TICK poll, minus the hello
  gate the tests' mock observers never pass) and run headless on all six Anchors.

The gametest sources split on version forks the same way the workspace absorbs every
spec v1 §6 fork (constants in [stonecutter.gradle.kts](stonecutter.gradle.kts)): the
1.21.5 GameTest annotation fork (vanilla `@GameTest` + `template` before, Fabric
`gametest.v1` after) is the only per-era split; the 26.1 creative-mock-player read
validation, the 26.2 `EntityTypes` holder rename, and the 1.21.8 Component assertion
messages are absorbed by the `modern_mock_player`, `entity_types_modern`, and
`gametest_component_asserts` constants.

### Toolchain requirements

- Gradle 9.5.x (via the wrapper), Loom 1.17-SNAPSHOT for both Loom plugin ids.
- The Gradle JVM on IDE and CI machines should be JDK 25 (ADR-0006). Command line
  builds work on any JDK the installed Gradle supports: missing JDK toolchains
  (Java 21 for the obfuscated era, Java 25 for 26.1+) are downloaded automatically
  via the foojay resolver.

## Switching the active version

The **active development version is 26.2**. The version the shared `src/` tree is
processed against is controlled by the `stonecutter active "<version>"` line in
[stonecutter.gradle.kts](stonecutter.gradle.kts); in day-to-day work switch it with the
generated Gradle tasks:

```sh
./gradlew "Set active project to 1.21.1"   # switch
./gradlew "Reset active project"           # back to 26.2 - run before committing
```

Non-active Anchors are only compiled by `chiseledBuild` (and their own `:<anchor>:build`),
so an Anchor-breaking change surfaces at build time, not in the IDE.

## Version forks (spec v1 §6)

The per-version API forks are absorbed by the workspace, not by per-anchor build edits:

- **HUD registration** - the `hud_registry` Stonecutter constant (true on 1.21.6+)
  selects `HudElementRegistry.addLast` vs the `HudRenderCallback.EVENT` path.
- **Fabric API dependency id** - the `deps.fapi_dep_id` property (`fabric` ->
  `fabric-api` in 26.1), mirrored by the `fapi_modern_id` constant, is expanded into
  each jar's `fabric.mod.json`. The same constant forks the keybind helper class
  (`KeyBindingHelper` -> `KeyMappingHelper` in 26.1).
- **Keybind category** - the `keymap_category_object` Stonecutter constant (true on
  1.21.11+) selects registered `KeyMapping.Category` records vs plain category strings.
- **Mappings era** - the codebase is written against Mojang names on every Anchor;
  `loomx.applyMojangMappings()` applies them on the obfuscated Anchors and is a no-op
  on the unobfuscated ones.
- **Entity world accessor** - spec v1 §6 lists a `getWorld` -> `getEntityWorld` rename
  at 1.21.9, but that fork exists only in Yarn. In the Mojang names used here the
  accessor is `level()` on every Anchor (verified against the mapped jars), so no
  replacement is needed and none is applied.

All of these live in [stonecutter.gradle.kts](stonecutter.gradle.kts) (constants,
replacements) and [stonecutter.properties.toml](stonecutter.properties.toml)
(per-anchor properties), so gameplay tickets never touch buildscripts to absorb them.

## License

Apache-2.0 - see [LICENSE](LICENSE).
