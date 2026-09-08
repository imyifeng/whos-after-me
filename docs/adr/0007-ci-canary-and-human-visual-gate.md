# CI gates on a 26.2 client-test canary; releases gate on a human visual-QA sign-off

CI runs a per-version matrix **auto-generated from the Stonecutter workspace** (one job per
Anchor, JDK 25 runners). **Pull requests block** on compile + package + headless server
gametests on all six Anchors, plus client gametests on a single canary — **26.2**, the
default development version — which retries once before failing. Pushes to `main` run the
same gate. A weekly scheduled sweep extends client gametests to every 1.21.4+ Anchor. A
**release** requires the full sweep green **plus** a human completing the
`docs/visual-qa.md` checklist and recording it in the release notes. Decided in [#14] on
top of the toolchain decision ([#8]) and the testing-hooks research ([#17]).

## Why

- **Client gametests are the only automated threat→HUD check, but they ride an experimental
  API and a GPU.** `fabric-client-gametest-api-v1` is `@ApiStatus.Experimental`, needs XVfb,
  and has known network-synchronizer flake on GitHub Actions. Running them on every Anchor
  would multiply that flake by six and by weekly minutes; confining the PR gate to one
  canary keeps every PR gated end-to-end while containing the blast radius.
- **The canary is 26.2 deliberately**: newest APIs, default development version, and the
  anchor where a Fabric API break will show first.
- **1.21.1 never runs client gametests** — no Fabric API version for it ships the module.
  Its HUD coverage is by proxy through 1.21.4 (same `HudRenderCallback` API line, shared
  source) plus the manual release smoke (ADR-0004).
- **Visual quality has no harness at all** (ADR-0004 rejected golden-image suites): arc
  geometry across aspect ratios and GUI scales, readability, feel, the 26.2 Vulkan backend.
  No automation can sign these off, so the release gate is deliberately human.
- **Generated matrix, not hand-written jobs**: rolling version pickup (ADR-0006) then
  extends CI by adding the subproject alone — no workflow edits.

## Decision shape

- Standing defaults on every workflow: Gradle wrapper validation, Gradle cache,
  cancel-in-progress concurrency per ref, actions pinned by commit SHA.
- PR gate (required status checks on `main`): all six Anchors — compile + package + server
  gametests; canary 26.2 only — additionally client gametests, retry-once.
- `main`: same jobs. Weekly schedule: client gametests on all five 1.21.4+ Anchors.
- Release (tag push): full sweep — server gametests ×6, client gametests ×5 (1.21.4+) —
  then the recorded human visual-QA sign-off. Publishing to Modrinth/CurseForge stays out
  of scope; artifacts attach to the GitHub release only after sign-off.
- The CI envelope and the test inventory move together: if the testing strategy (ADR-0004)
  drops a suite, the corresponding CI jobs are removed with it.

## Considered options

- **Client gametests on every Anchor in the PR gate** (rejected): six times the XVfb/GPU
  flake and CI minutes to guard against experimental-API drift a weekly sweep catches
  anyway.
- **No client gametests in CI** (rejected): loses the only automated end-to-end
  threat→packet→HUD check; the mod would ship with zero rendering automation.
- **Fully automated releases (no human gate)** (rejected): visual quality is the product's
  core promise and has no harness; an unattended release could ship an unreadable or
  misaligned HUD.
- **Golden-image suites instead of the human gate** (rejected): per-Anchor, per-GUI-scale
  goldens are high-maintenance and cross-GPU flaky; ADR-0004 confines pixel assertions to
  a 26.2-only smoke template.

## Consequences

- CI is most fragile exactly where this ADR places it: one canary, retry-once, experimental
  API. A `client-gametest-v1` break costs the render smoke and packet waits, not the whole
  automated net.
- Nothing automated verifies visual quality or 1.21.1 rendering. The human layer is
  load-bearing: **a release without the recorded `docs/visual-qa.md` sign-off does not
  ship.**
- The weekly sweep is the drift detector for the four non-canary client anchors; a break
  there surfaces up to a week late by design.

Evidence trail: decision in [#14]; CI-relevant research in [#17]; suite levels in ADR-0004
([#18]); anchors and pins in ADR-0006 ([#8]).

[#8]: https://github.com/imyifeng/whos-after-me/issues/8
[#14]: https://github.com/imyifeng/whos-after-me/issues/14
[#17]: https://github.com/imyifeng/whos-after-me/issues/17
[#18]: https://github.com/imyifeng/whos-after-me/issues/18
