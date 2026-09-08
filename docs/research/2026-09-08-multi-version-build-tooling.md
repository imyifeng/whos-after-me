# Multi-version build tooling for a Fabric mod spanning 1.21.1–26.2

- **Date:** 2026-09-08
- **Input:** GitHub issue #5 ("Research: multi-version build tooling for a Fabric mod spanning 1.21.1–26.2")
- **Scope:** Survey the current (September 2026) state of multi-version build tooling for Fabric mods, the hard toolchain constraints across Minecraft 1.21.1 → 26.2, and how shipping mods structure this span. Output feeds a toolchain decision ticket.
- **Method:** Primary sources — official Stonecutter docs, FabricMC blog/docs, Gradle Plugin Portal, GitHub APIs for repo activity, and real mod repositories (`fabric-example-mod`, `friends-and-foes`, `RoughlyEnoughItems`). All claims cite sources listed in §7.

## TL;DR / Recommendation

**Recommendation: Stonecutter (0.9.x) with one versioned subproject per Minecraft version, on Loom 1.17-SNAPSHOT with Gradle 9.5.x and JDK 25 in CI (toolchains target Java 21 for old versions).** This is the pattern used by actively-maintained mods that ship exactly this span (e.g. [Friends & Foes](https://github.com/Faboslav/friends-and-foes): 1.20.6 → 26.2 from one codebase). It preserves a single branch, gives per-version dependency and plugin control (required anyway because of the 26.1 Loom plugin split, see §2), and has first-class IDE support.

Do not adopt Unimined (slow, single-maintainer, no confirmed 26.x support) or Modstitch (multi-loader abstraction, unnecessary for a Fabric-only mod). Keep the ReplayMod Preprocessor as a fallback: it is still maintained but has a weaker IDE story and no structured per-version Gradle configuration. Per-version branches are what FabricMC's own template uses, but they scale poorly for a small team and 15 target versions.

## 1. The version span and its hard constraints

The range 1.21.1 → 26.2 covers **15 stable Minecraft versions** ([Fabric Meta](https://meta.fabricmc.net/v2/versions/game)): 1.21.1, 1.21.2/3, 1.21.4, 1.21.5, 1.21.6, 1.21.7, 1.21.8, 1.21.9, 1.21.10, 1.21.11, 26.1(.1/.2), 26.2.

Mojang moved to year-based versioning starting in 2026 ("26.1" = first game drop of 2026) ([minecraft.net announcement](https://www.minecraft.net/en-us/article/minecraft-new-version-numbering-system)).

| Minecraft version | Released | Java runtime | Obfuscation / mappings | Loom line current at release |
|---|---|---|---|---|
| 1.21.1 | Aug 2024 | Java 21 | Obfuscated; Yarn/Mojmap + intermediary | 1.7 (Jun 2024) |
| 1.21.2/3 | Oct–Nov 2024 | Java 21 | Obfuscated | 1.8 (Oct 2024) |
| 1.21.4 | Dec 2024 | Java 21 | Obfuscated | 1.9 (Dec 2024) |
| 1.21.5 | Mar 2025 | Java 21 | Obfuscated | 1.10 (Feb 2025) |
| 1.21.6–1.21.8 | Jun–Jul 2025 | Java 21 | Obfuscated | 1.11 (Jul 2025) |
| 1.21.9 / 1.21.10 | Oct 2025 | Java 21 | Obfuscated | 1.12 / 1.13 (Oct–Nov 2025) |
| 1.21.11 | Dec 2025 | Java 21 | **First unobfuscated version** ([Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_1.21.11)) | 1.14 (Dec 2025) |
| 26.1 (.1/.2) | Mar–Apr 2026 | **Java 25 minimum** | Unobfuscated; **no mappings published; Yarn no longer officially supported** | 1.15 (Jan 2026) / 1.16 (Apr 2026) |
| 26.2 | Jun 2026 | Java 25 | Unobfuscated | 1.17 (Jun 2026, current) |

Loom release dates from the [fabric-loom releases page](https://github.com/FabricMC/fabric-loom/releases); Java requirement for 26.1 from the [Fabric 26.1 blog post](https://fabricmc.net/2026/03/14/261.html); unobfuscation announcement from [minecraft.net](https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition) and Fabric's response ([fabricmc.net, Oct 2025](https://fabricmc.net/2025/10/31/obfuscation.html)).

### The 26.1 toolchain break (the single most important fact)

The range is split into two toolchain eras, which any multi-version setup **must** handle explicitly:

1. **1.21.1 – 1.21.10:** obfuscated game. Requires remapping Loom (`net.fabricmc.fabric-loom-remap`), Yarn or Mojmap mappings, and intermediary. Runtime Java 21.
2. **1.21.11 – 26.2:** unobfuscated game. Mojang ships method parameter and local-variable names; no mappings are published for 26.1+. Fabric [dropped official Yarn support](https://fabricmc.net/2026/03/14/261.html) and split Loom: the new `net.fabricmc.fabric-loom` plugin "does not remap Minecraft or mods" (`implementation` instead of `modImplementation`, plain `jar` instead of `remapJar`). Per the 26.1 blog: "no mods from 1.21.11 or before will work without, at a minimum, recompilation" — the intermediary boundary sits here.

Additional 26.1 constraints ([Fabric 26.1 blog](https://fabricmc.net/2026/03/14/261.html)):
- **Java 25 minimum for the Gradle JVM** (runtime for 26.1+; Gradle toolchains still compile Java-21 targets for old versions).
- Loom 1.15+ and Gradle 9.4+ recommended at the time of the post; FabricMC's templates currently ship **Gradle 9.5.1 + Loom 1.17-SNAPSHOT** on every branch from 1.21 to 26.2 ([fabric-example-mod](https://github.com/FabricMC/fabric-example-mod) branch files) — i.e. one modern toolchain can build the entire span, with the *plugin id* (remap vs non-remap Loom) selected per version.
- **IntelliJ IDEA 2025.3+ required** for mixins to resolve correctly in the unobfuscated era.
- Fabric API renamed large parts of its API at the boundaries (e.g. `ItemGroupEvents` → `CreativeModeTabEvents` at 26.1; Mojang package reshuffles at 1.21.11 and 26.2), so source-level conditionals or replacements across the span are unavoidable.

## 2. Tooling options

### 2.1 Stonecutter (recommended candidate)

- **What it is:** Gradle plugin (`dev.kikugie.stonecutter`) for multi-version workspaces: comment-based source preprocessing (`//? if versions { }`), one generated Gradle subproject per configured version, per-version dependencies, version-aware constants/swaps/replacements, and batch build tasks ([docs overview](https://stonecutter.kikugie.dev/), [wiki](https://stonecutter.kikugie.dev/wiki/)).
- **Current state (Sept 2026):** stable line **0.9.x** (0.9.7 published 2026-07-19 on the [Gradle Plugin Portal](https://plugins.gradle.org/plugin/dev.kikugie.stonecutter); docs header ships 0.9.8). A **0.10 / "v2" rewrite is in alpha** with docs under construction ([wiki/v2](https://stonecutter.kikugie.dev/wiki/v2/)). Development moved to Codeberg (`stonecutter/stonecutter`) with a GitHub mirror under the `stonecutter-versioning` org. The [fabric template](https://github.com/stonecutter-versioning/stonecutter-template-fabric) was updated to Stonecutter 0.9.3 / MC 1.21.11 in 2026 — actively maintained.
- **Requirements:** Gradle ≥ 8.8 and a Java version compatible with Gradle (Java 17+) to run builds; works with any Loom version, including the remap and non-remap plugins side by side ([getting-started guide](https://stonecutter.kikugie.dev/wiki/start/); Friends & Foes runs both plugin ids in one workspace).
- **Range coverage:** versions are plain Gradle subprojects, so any MC version Loom supports can be listed — 1.21.1 → 26.2 is squarely in scope (Friends & Foes spans 1.20.6 → 26.2 in one workspace).
- **IDE story:** dedicated IntelliJ plugin ("Stonecutter Dev", [JetBrains Marketplace](https://plugins.jetbrains.com/plugin/25044-stonecutter-dev)) with syntax highlighting and active-version code insight; the companion **Fletching Table** tool automates entrypoints, mixin refmap wiring, and access converters ([docs](https://stonecutter.kikugie.dev/)). VS Code / Eclipse get no dedicated support.
- **Limitations** (per the [docs FAQ](https://stonecutter.kikugie.dev/wiki/faq)): you develop against one "active" version at a time — other versions only fail at build time, so all-version builds (`chiseledBuild`) must run in CI; preprocessing is source-text level (it cannot change signatures/type hierarchies; heavy API divergence needs replacements or version-specific source sets); Kotlin DSL buildscripts are recommended; batch builds run sequentially (can be slow for many versions). The docs themselves advise against Stonecutter when version differences are so large that the shared codebase becomes mostly conditionals.
- **CI story:** matrix over configured versions; build via subproject tasks (`:<module>:<version>:build`) or `chiseledBuild`; used exactly this way by Friends & Foes (§3).
- **Adoption:** docs cite 400+ projects using it ([overview](https://stonecutter.kikugie.dev/)), including Elytra Trims, YACL, Structurify, PatPat (24 game versions in one repo), JJElytraSwap.

### 2.2 Per-version branches

- **What it is:** one git branch per MC version; fixes are cherry-picked across branches. This is what [FabricMC's own `fabric-example-mod`](https://github.com/FabricMC/fabric-example-mod) does (42 branches, 1.14.4 → 26.2, all on Gradle 9.5.1 + Loom 1.17-SNAPSHOT), and what REI migrated to for modern versions (§3).
- **Strengths:** zero preprocessing; full IDE fidelity per branch; trivial CI; no tooling dependency. The FabricMC template proves the whole 1.21.1–26.2 span builds on one modern Gradle/Loom.
- **Weaknesses:** with 15 versions, every fix is up to 14 cherry-picks plus conflict resolution; no single "source of truth" revision; easy for branches to rot; the Stonecutter docs call this out as the main pain ([comparison in docs overview](https://stonecutter.kikugie.dev/)). Workable for one or two versions, poor for fifteen.

### 2.3 ReplayMod Preprocessor

- **What it is:** the original comment-directive preprocessor (`//#if MC_VERSION >= ...`) for Java/Kotlin, applied at build time to produce version-specific sources; single branch, version chosen via Gradle property ([github.com/ReplayMod/preprocessor](https://github.com/ReplayMod/preprocessor)).
- **Current state:** still maintained — last push 2026-07-07, not archived (GitHub API) — but feature-stable and bare-bones: no dependency management, no build-task orchestration, no IDE plugin (conditionals render as plain comments; weaker IDE experience than Stonecutter's).
- **Range coverage:** version-agnostic; handles the span fine.
- **CI story:** simple matrix passing the version property.
- **Track record:** powered REI, Architectury-era multi-version mods for years. Note however that REI itself has moved its *recent* versions to per-MC-version branches with unremapped Loom (§3), which is weak evidence that preprocessor-only workflows are losing ground for the post-obfuscation era.

### 2.4 Unimined

- **What it is:** a Gradle "minecraft extension" supporting many loaders (Fabric, NeoForge, Forge, legacy loaders) in one buildscript, Loom-style or VanillaGradle-style ([README](https://github.com/Unimined/Unimined)).
- **Current state:** repo still receives commits (last push 2026-08-14) but the last GitHub release is **1.4.1 (June 2025)**, with 49 open issues; effectively one maintainer with an LTS-branch policy for breaking changes. The README's own TODO list (datagen, IDE launch support, etc.) is longstanding.
- **Range coverage / 26.x:** no public evidence found of tested support for the unobfuscated 26.x toolchain changes; its remapping-centric pipeline predates the split. **Unverified for 26.1/26.2 — treat as a risk.**
- **IDE story:** dev-run support focused on IntelliJ; Eclipse/VS Code launch configs unimplemented per README.
- **Verdict:** powerful for legacy/alternate-loader work; not the right bet for a Fabric-only mod targeting 1.21.1–26.2 in September 2026.

### 2.5 Modstitch

- **What it is:** Gradle plugin that unifies *multi-loader* tooling (Loom / Unimined / VanillaGradle) with a selectable preprocessor backend (JCP or Stonecutter); [isXander/modstitch](https://github.com/isXander/modstitch), last push 2026-04-01; ships a [Stonecutter template](https://github.com/isXander/modstitch-stonecutter-template). YACL is a known user ([Stonecutter docs](https://stonecutter.kikugie.dev/)).
- **Verdict:** solves multi-loader, not multi-version; low adoption (33 stars); only relevant here if NeoForge support is ever added — and even then as Stonecutter + Modstitch, not instead of it.

### 2.6 Newer / experimental

- `com.oliveryasuna.modkit.multiversion` (Gradle Plugin Portal, created 2026-07-20): per-version overrides layered on Stonecutter's preprocessor. Brand new, unproven — watch, don't adopt.
- Architectury's `loom-no-remap` plugin: used by REI for unobfuscated modern versions ([REI 26.2 build.gradle](https://github.com/shedaniel/RoughlyEnoughItems/tree/26.2)); relevant only if Architectury conventions are adopted.
- Nothing else notable surfaced in searches for 2026; Stonecutter's own v2/0.10 rewrite is the main "next thing" to watch.

## 3. How shipping mods structure this span (case studies)

| Mod | Approach | Repo layout | CI |
|---|---|---|---|
| [Friends & Foes](https://github.com/Faboslav/friends-and-foes) (active; pushed 2026-09-01; ships 1.20.6, 1.21.1, 1.21.4, 1.21.5, 1.21.8, 1.21.10, 1.21.11, 26.1.2, 26.2) | **Stonecutter 0.9**, multi-loader (Fabric + NeoForge via ModDev) | `versions/<mc>` subprojects under `fabric/` and `neoforge/` loader modules; `stonecutter.gradle.kts` holds per-version constants and **string replacements keyed by `current.parsed >= "1.21.11" / "26.1" / "26.2"`** to absorb the Mojang/Fabric API renames; both `fabric-loom` and `fabric-loom-remap` plugin ids applied per version | GitHub Actions: matrix generated from `versions/` dirs; JDK 25 everywhere (toolchains handle Java 21 targets); per-version **headless client and server smoke tests** (`headlesshq/mc-runtime-test` / `mc-server-test`); separate publish workflow |
| [FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod) (reference template) | **Per-version branches** (42 branches, 1.14.4 → 26.2) | Single-project Gradle per branch; every branch currently on Gradle 9.5.1 + Loom 1.17-SNAPSHOT — demonstrating one modern toolchain builds the whole span | Trivial per branch (one workflow, one version) |
| [RoughlyEnoughItems](https://github.com/shedaniel/RoughlyEnoughItems) (active; pushed 2026-07-29) | **Historically** ReplayMod Preprocessor on long-lived branches (4.x–16.x each spanning many MC versions); **now** one branch per MC version (`1.21.9`, `1.21.11`, `26.1`, `26.2`) with Architectury `loom-no-remap` 1.14-SNAPSHOT for the unobfuscated era | Multi-module (`api`, `fabric`, `forge`, `neoforge`, `default-plugin`) | Per-branch workflows |

Takeaway: the two actively-maintained mods that cover versions on both sides of the 26.1 break either use Stonecutter with per-version replacements (Friends & Foes) or gave up and maintain per-version branches (REI). FabricMC's template shows the per-branch model is viable but branch-heavy.

## 4. Comparison

| Criterion | Stonecutter 0.9 | Per-version branches | ReplayMod Preprocessor | Unimined | Modstitch |
|---|---|---|---|---|---|
| Covers 1.21.1–26.2 incl. 26.1 Loom split | **Yes** — per-version deps and per-version Loom plugin id | Yes | Yes | **Unverified for 26.x** | Yes, but only as multi-loader glue (pair with Stonecutter) |
| Single branch / single source of truth | **Yes** | No (N branches, cherry-pick burden) | **Yes** | Yes | Yes |
| Maintenance status (Sept 2026) | Active: 0.9.7 Jul 2026; template updated 2026; v2 in alpha; moved to Codeberg | N/A (no tool) | Maintained (pushed Jul 2026), feature-stable | Slowing: last release Jun 2025; 49 open issues; 1 maintainer | Modest: last push Apr 2026; 33 stars |
| CI story | Matrix over version subprojects or `chiseledBuild`; proven pattern (Friends & Foes) with headless client/server tests | Trivial but ×N branches | Simple matrix via version property | Standard Gradle CI | As per underlying processor |
| Developer experience | Best-in-class for the problem: IntelliJ plugin, active-version switching, constants/swaps/replacements, Fletching Table automation | Perfect IDE fidelity per branch, but no cross-branch view | Weakest IDE story (plain comments, no tooling) | IntelliJ-centric; docs thin | Good once configured; extra abstraction layer |
| Main risk | Conditionals only checked at build time for non-active versions → CI must build all versions; source-level preprocessing can't express every API change (needs replacements); 0.10 rewrite ahead may require migration | Cherry-pick drift across 15 branches; human cost | No per-version Gradle config; manual dependency wiring; fading community momentum | One-bus-factor; unclear 26.x support | Small community; adds indirection without solving multi-version itself |

## 5. Recommendation (input for the decision ticket)

1. **Adopt Stonecutter 0.9.x** with versioned subprojects. Start from [`stonecutter-versioning/stonecutter-template-fabric`](https://github.com/stonecutter-versioning/stonecutter-template-fabric) and mirror the Friends & Foes `stonecutter.gradle.kts` patterns (per-version constants + `replacements.string(current.parsed >= ...)` for cross-era API renames).
2. **Target a pragmatic version set, not all 15.** E.g. 1.21.1 (LTS-like crowd favourite), the latest of each minor line we choose to support, 26.1.2, and 26.2. Every added version is a real CI/preprocessing cost; trimming the middle (1.21.2–1.21.8) is the single biggest DX lever.
3. **Toolchain pinning:** Gradle 9.5.x wrapper, Loom 1.17-SNAPSHOT, JDK 25 for the Gradle JVM in IDE and CI; Java toolchains emit 21 for ≤ 1.21.11 targets. IntelliJ 2025.3+ required for mixins. Choose `fabric-loom-remap` for obfuscated versions and `fabric-loom` for 1.21.11+ (or Architectury `loom-no-remap` if a shared "no remap" pipeline is preferred there).
4. **CI:** generated GitHub Actions matrix (one job per version: build + headless client/server smoke test), copying the Friends & Foes workflow shape. `chiseledBuild` as the all-versions gate on PRs.
5. **Fallback:** if Stonecutter's conditionals prove too invasive (mod turns out to be API-heavy with per-version divergence), fall back to per-version branches (FabricMC-template style) rather than the Preprocessor — the branch model degrades gracefully while the Preprocessor lacks the Gradle-level per-version configuration that 26.1's toolchain split effectively requires.

## 6. Open questions for the decision ticket

- Exact version set to support (see 5.2) — this drives CI minutes and maintenance load more than the tooling choice.
- Whether NeoForge parity is ever in scope (would add Modstitch or per-loader modules into the Stonecutter workspace).
- Timing: adopt stable 0.9.x now vs waiting for the 0.10/v2 rewrite (docs under construction; no release date published).

## 7. Sources

Primary:

- Stonecutter docs: [overview](https://stonecutter.kikugie.dev/), [wiki](https://stonecutter.kikugie.dev/wiki/), [getting-started guide](https://stonecutter.kikugie.dev/wiki/start/), [FAQ / limitations](https://stonecutter.kikugie.dev/wiki/faq), [v2 announcement](https://stonecutter.kikugie.dev/wiki/v2/)
- [Stonecutter on the Gradle Plugin Portal](https://plugins.gradle.org/plugin/dev.kikugie.stonecutter) (0.9.7, 2026-07-19); [Stonecutter Dev IntelliJ plugin](https://plugins.jetbrains.com/plugin/25044-stonecutter-dev); [stonecutter-template-fabric](https://github.com/stonecutter-versioning/stonecutter-template-fabric)
- [Minecraft new version numbering system](https://www.minecraft.net/en-us/article/minecraft-new-version-numbering-system) (minecraft.net); [Java Edition 1.21.11](https://minecraft.wiki/w/Java_Edition_1.21.11) (first unobfuscated version); [Removing obfuscation in Java Edition](https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition)
- FabricMC: [Removing Obfuscation from Fabric](https://fabricmc.net/2025/10/31/obfuscation.html), [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html) (Java 25, Loom 1.15, Gradle 9.4, IntelliJ 2025.3, Yarn dropped), [Develop page](https://fabricmc.net/develop/), [Fabric Meta game versions](https://meta.fabricmc.net/v2/versions/game), [fabric-loom releases](https://github.com/FabricMC/fabric-loom/releases), [Porting to 26.2](https://docs.fabricmc.net/develop/porting/)
- Alternatives: [ReplayMod/preprocessor](https://github.com/ReplayMod/preprocessor) (activity via GitHub API), [Unimined/Unimined](https://github.com/Unimined/Unimined) (README + releases via GitHub API), [isXander/modstitch](https://github.com/isXander/modstitch) and [modstitch-stonecutter-template](https://github.com/isXander/modstitch-stonecutter-template)

Case studies (file contents read at HEAD on 2026-09-08):

- [Faboslav/friends-and-foes](https://github.com/Faboslav/friends-and-foes): `versions/`, `stonecutter.gradle.kts`, `.github/workflows/build.yml`
- [FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod): branch list, `gradle.properties` and `gradle-wrapper.properties` on the `1.21`, `1.21.1`, and `26.2` branches
- [shedaniel/RoughlyEnoughItems](https://github.com/shedaniel/RoughlyEnoughItems): branch list, `26.2` branch `build.gradle`
