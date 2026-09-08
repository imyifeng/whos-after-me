# Visual QA checklist

The manual layer of the v1 testing strategy ([#18], ADR-0004): everything no harness covers —
visual quality, feel, and rendering backends. A release ships only after this checklist is
walked on the release candidate and the results are recorded in the release notes ([#14]).

**Method**: in a fresh world (creative flight + `/summon`), enable the HUD and exercise each
scenario at the default config unless the scenario says otherwise. Note pass/fail per scenario
plus anything that looks off; screenshots welcome.

| # | Scenario | Pass when |
|---|----------|-----------|
| 1 | Aspect ratios — repeat at 16:9, 16:10, and 21:9 (ultrawide) | The orbit hugs the viewport aspect; arcs and triangles stay on it, nothing clips at screen edges |
| 2 | GUI scales — repeat at auto, 1, 2, 3, 4 | Indicators stay readable; the orbit scales sensibly at every scale |
| 3 | Absolute aiming mode (default) — let mobs ring you at cardinal and diagonal bearings | Each triangle points along the threat's compass bearing, including threats behind walls |
| 4 | Screen-relative aiming mode — pitch up and down, turn, change FOV | Triangles track the threat's on-screen direction across pitch and FOV changes |
| 5 | Simultaneous threats — 8+ mixed mobs (melee, ranged, provoked neutral) | Every threat renders its own arc; co-directional pairs overlap with the nearer on top; beyond 8, nearest-first fill without flicker |
| 6 | Config feel — drag detection radius, orbit radius, size, and opacity sliders live | The orbit rescales and indicators resize/fade immediately, no lag or artifacts |
| 7 | Combat latency — sprint and strafe against zombies and skeletons; then hide behind a wall | Indicators appear when targeted and clear promptly after the 1.5 s grace; an indicator persists through a wall until the grace expires — no stuck arcs |
| 8 | Performance — a raid or 50+ hostile mobs | No perceptible frame impact from the HUD |
| 9 | 26.2 Vulkan backend — repeat scenarios 1, 3, and 5 on the Vulkan renderer | Rendering matches the default (GL) backend |
| 10 | 1.21.1 smoke — run the 1.21.1 artifact of the same build | A summoned zombie produces an indicator with a correct direction (the human check behind the 1.21.1 proxy-coverage story) |

Evidence trail: strategy decision in [#18]; CI envelope and release gate in [#14];
look-and-feel defaults from the orbit-HUD prototype in [#7].

[#7]: https://github.com/imyifeng/whos-after-me/issues/7
[#14]: https://github.com/imyifeng/whos-after-me/issues/14
[#18]: https://github.com/imyifeng/whos-after-me/issues/18
