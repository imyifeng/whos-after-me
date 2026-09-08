# Testing strategy: level C with smoke-only pixel checks; 1.21.1 HUD covered by proxy

v1 must be testable across six anchor versions (1.21.1, 1.21.4, 1.21.8, 1.21.11, 26.1.2, 26.2)
inside the CI envelope fixed by [#14], on a client-gametest API that is experimental and absent
from 1.21.1 entirely. We adopt **level C from the testing-hooks research ([#17]) — plain-JUnit
unit suites, server GameTests on all six anchors, client GameTests on 1.21.4+ — with pixel
assertions limited to small-crop screenshot smoke templates that run only on the 26.2 canary**,
and we cover the 1.21.1 HUD **by proxy** through 1.21.4 plus a manual release scenario.

## Decision shape

- **Unit tests: plain JUnit only, no Fabric Loader JUnit.** The pure logic — the polled-diff
  engine (RESET/ADD/REMOVE, [#10]), the payload codec round-trip, the detection-radius
  predicate, the 1.5 s clear-grace state machine, the >8-threat selection (incumbent-sticky +
  nearest-first, [#9]), and the orbit geometry math (aspect-ratio ellipse, arc-length scaling,
  both aiming modes) — needs no Minecraft classes. Anything needing live registries belongs in
  server GameTests, which provide the same coverage Loader JUnit would, for free.
- **Server GameTests: six integration tests, all six anchors**, headless in `check` via Loom's
  `configureTests`: a melee mob targets the player → threat; a brain-memory mob
  (ATTACK_TARGET path) → threat; a provoked neutral → threat once angered; a ranged mob →
  threat; a mob beyond the detection radius → no threat; a cleared target → threat held
  through the grace window, then dropped. Real mob AI is where the history is ([#9] falsified
  a brain-driven-mobs claim against a mirror), so these run against the real game, not mocks.
  The single API-shape fork at 1.21.5 (vanilla vs Fabric `@GameTest`) is a small per-version
  test-source split under Stonecutter.
- **Client GameTests: three, on 1.21.4+.** End-to-end ADD and REMOVE — dedicated-server
  topology matching the server-authoritative architecture, `waitForClientboundPackets`, then
  client threat-list assertions via `runOnClient` — are the real check; one
  `assertScreenshotContains` smoke template on a small indicator crop proves the HUD draws.
  State assertions run on all five 1.21.4+ anchors in the weekly sweep; the screenshot smoke
  is confined to the 26.2 canary, keeping GPU-variance flake off the sweep.
- **1.21.1: covered by proxy.** No Fabric API version for 1.21.1 ships a client-gametest
  module. The Stonecutter common source means the HUD code exercised by the 1.21.4 client
  GameTests (same `HudRenderCallback` API line) is the code 1.21.1 compiles; 1.21.1's own unit
  and server suites run on-anchor; each release's visual-QA pass includes an explicit 1.21.1
  smoke scenario.
- **Everything beyond smoke is manual**: `docs/visual-qa.md` is the versioned scenario
  checklist the release gate ([#14]) walks and records.

## Considered options

- **Level A (unit only)** (rejected): detection and sync — the whole mod — would never be
  exercised by automation.
- **Level B (no client GameTests)** (rejected): drops the only automated end-to-end
  threat→packet→HUD check, and #14's canary job and weekly client sweep with it.
- **Level D (per-anchor golden-image suites)** (rejected): goldens per anchor and GUI scale
  across six anchors is high-maintenance and cross-GPU flaky; smoke templates give the
  "does it draw" guarantee without the maintenance.
- **Fabric Loader JUnit** (rejected as an addition): its registry-dependent coverage overlaps
  what server GameTests already give.

## Consequences

- CI is most fragile exactly where [#14] put it: one canary, retry-once, experimental API. A
  Fabric API break of `client-gametest-v1` costs the render smoke and the packet waits, not
  the whole automated net.
- Nothing automated verifies 1.21.1 rendering; the guarantee is proxy-by-shared-source plus
  the human release check. A HUD regression confined to a 1.21.1-only version block would slip
  past automation by design.
- The manual layer is load-bearing: a release without the recorded `docs/visual-qa.md`
  sign-off does not ship ([#14]).

Evidence trail: decision in [#18]; testing-hooks research in [#17]; CI envelope in [#14];
coverage rules in [#9]; protocol semantics in [#10].

[#9]: https://github.com/imyifeng/whos-after-me/issues/9
[#10]: https://github.com/imyifeng/whos-after-me/issues/10
[#14]: https://github.com/imyifeng/whos-after-me/issues/14
[#17]: https://github.com/imyifeng/whos-after-me/issues/17
[#18]: https://github.com/imyifeng/whos-after-me/issues/18
