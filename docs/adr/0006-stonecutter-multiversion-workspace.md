# Multi-version builds run on a Stonecutter workspace; v1 ships six anchors

The mod targets Minecraft 1.21.1 → 26.2 from **one codebase on one branch**, built by
**Stonecutter 0.9.x** with one Gradle subproject per version ([#8]). v1 ships six
**Anchors** — 1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2 — chosen to exercise both
toolchain eras and both HUD code paths; further versions are picked up on the rolling
policy below.

## Why

- **The range has a hard toolchain break.** 1.21.1–1.21.10 are obfuscated (Yarn +
  intermediary, remapping Loom); 1.21.11 is the first unobfuscated version and the last
  Yarn ever; 26.1+ ships Mojang names with no published mappings, needs Java 25, and splits
  Loom into remap and no-remap plugin ids. Any single-codebase setup must select the Loom
  plugin id per version — which is exactly Stonecutter's per-subproject model.
- **Per-version branches don't scale to the rolling policy.** Fifteen stable versions in
  range, more coming: every fix would cost up to N cherry-picks with no source of truth.
  The two shipping mods that cover this span either use Stonecutter with per-version
  replacements (Friends & Foes, 1.20.6 → 26.2) or gave up and maintain per-version
  branches (REI).
- **Preprocessor-only and alternative toolchains lost.** ReplayMod Preprocessor has no
  per-version Gradle configuration (which the 26.1 Loom split effectively requires) and the
  weakest IDE story; Unimined has no confirmed 26.x no-remap support; Modstitch solves
  multi-loader, not multi-version (NeoForge is out of scope); Stonecutter 0.10/v2 is still
  alpha — migrate only once stable.

## Decision shape

- **Workspace**: start from `stonecutter-versioning/stonecutter-template-fabric`; versions
  as `versions/<mc>` subprojects; comment-based preprocessing (`//? if versions`) plus
  per-version constants and string replacements keyed on `current.parsed >= "1.21.11" /
  "26.1"` to absorb the Mojang/Fabric API renames. Kotlin DSL buildscripts.
- **Per-version Loom plugin id**: `net.fabricmc.fabric-loom-remap` for 1.21.1–1.21.10
  (obfuscated, Yarn, intermediary); `net.fabricmc.fabric-loom` for 1.21.11+ (unobfuscated;
  `implementation` instead of `modImplementation`, plain `jar` instead of `remapJar`).
- **Pins**: Gradle 9.5.x wrapper; Loom 1.17-SNAPSHOT; JDK 25 for the Gradle JVM in IDE and
  CI; Java toolchains emit class-file 21 for targets ≤ 1.21.11; IntelliJ IDEA 2025.3+
  required for mixin resolution.
- **Active (development) version: 26.2** — unobfuscated Mojang names, best IDE experience;
  switchable per task. Non-active versions only fail at build time, so all-version builds
  (`chiseledBuild`) must run in CI (ADR-0007).
- **v1 anchors** (why each anchors): 1.21.1 — LTS-like favourite, `HudRenderCallback` path,
  remap era; 1.21.4 — most-installed post-1.21 line, pre-deprecation `HudRenderCallback`,
  first client-gametest-capable anchor; 1.21.8 — latest of the 1.21.6–8 line,
  `HudElementRegistry` + Yarn + remap era; 1.21.11 — the Loom split boundary, first
  unobfuscated, last Yarn; 26.1.2 — Java 25 no-remap era anchor; 26.2 — current release,
  default development version, 26.2 CI canary.

## Consequences

- The other stable versions in range (1.21.2/3, 1.21.5, 1.21.9, 1.21.10, 26.1, 26.1.1) ship
  later on demand; adding one is a new subproject plus constants, not a code fork.
- Heavy API divergence that preprocessing cannot express (signature changes, type moves)
  lands in per-version source sets or replacements; the version forks the mod actually
  needs are enumerated in the v1 spec (HUD API, gametest annotation, `getEntityWorld`,
  Fabric API dependency id).
- Stonecutter 0.9.x is a known-multi-version but single-maintainer tool; the workspace it
  generates is plain Gradle, so a later migration (e.g. to a stable 0.10) touches build
  files, not mod code.

## Rolling update policy

- A new stable Minecraft line enters the workspace within ~2 weeks of a stable Fabric API
  build existing for it.
- Patch releases ride the existing line: bump the target, rebuild, ship (e.g. 26.1.3,
  26.2.1).
- The supported set must always cover both toolchain eras (remap and no-remap) and both HUD
  code paths (`HudRenderCallback` and `HudElementRegistry`).

Evidence trail: decision in [#8]; API landscape in [#4]; build-tooling research in [#5];
testing per anchor in [#17].

[#4]: https://github.com/imyifeng/whos-after-me/issues/4
[#5]: https://github.com/imyifeng/whos-after-me/issues/5
[#8]: https://github.com/imyifeng/whos-after-me/issues/8
[#17]: https://github.com/imyifeng/whos-after-me/issues/17
