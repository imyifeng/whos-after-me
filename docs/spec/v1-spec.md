# Who's After Me — v1 specification

Implementation-ready v1 spec for **Who's After Me**, a Fabric mod that draws on-screen
orbit threat indicators for mobs currently targeting the player. Assembled in [#11] as the
destination of the wayfinder map ([#3]): every decision below is locked in a decision
ticket, ADR, or the prototype resolution — nothing here is open for re-litigation during
implementation.

**Decision register**

| Area | Locked by |
|---|---|
| Threat definition, coverage rule, cap, grace | ADR-0001 ([#9]) |
| Sync protocol (polled diff) | ADR-0002 ([#10]) |
| Config storage, screen, keybind | ADR-0003 ([#13]) |
| Testing strategy (suite levels) | ADR-0004 ([#18]) |
| Server-authoritative architecture | ADR-0005 (charting, [#6]) |
| Stonecutter workspace, Anchors, rolling policy | ADR-0006 ([#8]) |
| CI gates (canary, human visual gate) | ADR-0007 ([#14]) |
| HUD look and feel (orbit, arc + triangle, scaling, aiming modes) | Prototype resolution ([#7]) |
| API/version facts (HUD API forks, renames, availability) | Research [#4], [#5], [#6], [#12], [#17] |

Canonical vocabulary: [CONTEXT.md](../../CONTEXT.md). Status: **pending owner sign-off**
([#11]).

## 1. Identity and distribution

| Field | Value |
|---|---|
| Name | Who's After Me |
| Mod ID | `whos_after_me` |
| License | Apache-2.0 |
| Environment | Both sides required (`*`); non-functional without the server side (ADR-0005) |
| Target range | Minecraft 1.21.1 → 26.2, one codebase, six v1 Anchors (ADR-0006) |
| Loader | Fabric only (Forge/NeoForge/hybrids/Bedrock out of scope) |
| Dependencies | Fabric API (required); ModMenu (optional); MidnightLib Jar-in-Jar bundled, no user install |

**Modrinth / CurseForge page metadata** (page creation itself is out of scope):

- **Title/slug**: "Who's After Me" / `whos-after-me`.
- **Summary**: "See who's after you: orbit indicators for every mob targeting you, through
  walls, server-verified."
- **Description outline**: what it does (one Threat indicator per Threat on an elliptical
  orbit); the two aiming modes with screenshots; server-authoritative note ("install on
  both sides — vanilla servers show nothing by design"); configuration overview;
  supported-versions table (the shipped Anchors); link to source and issue tracker.
- **Categories**: Utility (primary); Adventure (secondary).
- **Environment flags**: client + server required (Modrinth "server" and "client";
  CurseForge "both").
- **Versioning**: mod version independent per release, artifact per Anchor
  (`whos_after_me-<modver>+<mc>`), file list = shipped Anchors.
- **Icon**: 128×128 px required by Modrinth — **open item**, see §10.

## 2. Architecture

Server-authoritative, per ADR-0005:

```
server tick (every pollInterval ticks)             client (per frame)
┌──────────────────────────────┐   S2C threat_sync  ┌──────────────────────────┐
│ uniform threat read (ADR-1)  │ ── RESET/ADD/REMOVE┆ threat store (network id →  │
│ → per-player threat set      │ ──────────────────▶│ kind) → HUD render (§5)  │
│ → diff vs last-sent (ADR-2)  │   C2S hello (join) ┆ no detection logic       │
└──────────────────────────────┘ ◀────────────────── └──────────────────────────┘
```

- The mod is required on both sides; a modded client on a vanilla server, or a vanilla
  client on a modded server, behaves as vanilla (empty orbit). PvP never qualifies:
  players are not mobs under the uniform read.
- All server protocol state (per-player last-sent sets, grace timers) is confined to the
  server tick. Client payload handlers hop from the Netty thread to the client thread
  before touching world/render state. Identical behavior on integrated and dedicated
  servers.

## 3. Detection (ADR-0001)

**Uniform structural rule**: a mob is a Threat for observing player P when

1. its attack target is P — read as `getTarget()`, falling back to the brain's
   `ATTACK_TARGET` memory (`LivingEntity.getBrain()` public API, no mixin) for mobs that
   store targets without overriding `getTarget()`; and
2. it is within the Detection radius of P (squared-distance check, entity position to
   player position, 3D).

No per-mob whitelist; goal mobs, brain mobs (warden, breeze, creaking, piglins,
hoglin/zoglin/axolotl/frog), provoked neutrals, and future mobs are covered by the same
read.

**Poll loop** (server tick, `ServerTickEvents.END_SERVER_TICK`):

- Every `pollInterval` server ticks (config, default 5) per modded player: collect mobs
  via a radius-bounded box query (`getEntitiesByClass(MobEntity.class, …)` — Mojang-name
  equivalent on 26.x via the workspace replacements), apply the rule, diff against the
  player's last-sent set (§4).
- Cost is one radius-bounded query per player per interval; negligible, spreadable
  round-robin across players if ever needed.

**1.5 s clear grace** (server-side, per ADR-0002): a mob whose target clears holds its
Threat status for 1500 ms before `REMOVE`; re-acquiring inside the grace emits no packet.
Exiting the Detection radius removes immediately (no grace). Death/despawn/dimension
change are not graced — the next poll drops them.

**Consequences of the uniform rule** (documented, not special-cased):

- **Jockeys**: each entity in a riding stack is evaluated independently; a drowned on a
  zombie nautilus can yield two Threats (rider + mount), each with its own indicator.
- **Creaking**: covered when alerted and targeting (brain memory); dormant/rooted
  creakings never target and never qualify.
- **Known gaps** (accepted by ADR-0001): ender dragon (phase-local targeting), goat rams
  (`RAM_TARGET`), pufferfish / sulfur-cube / slime / magma-cube contact damage, wither
  side heads, creepers ignited without a player target (flint and steel).

## 4. Threat sync protocol (ADR-0002)

Transport: `fabric-networking-api-v1` custom payloads, registered in the common
initializer on both logical sides before any receiver registration. The registration model
is unchanged across 1.21.1 → 26.2 (research [#4]).

**Handshake** — C2S `whos_after_me:hello`:

| Field | Type | Value |
|---|---|---|
| protocol version | u8 | `1` |

The client sends `hello` on play-phase join. The server marks a player modded only on a
version-matching `hello`, immediately sends a full-state `RESET`, and never sends threat
packets to unmarked (vanilla) players. A version mismatch is logged and treated as
unmarked (safe default; a future protocol revision bumps the byte and renegotiates).

**Threat sync** — S2C `whos_after_me:threat_sync`:

| Field | Type | Notes |
|---|---|---|
| mode | u8 | `0` RESET, `1` ADD, `2` REMOVE |
| count | varint | entry count |
| entries × count | varint + u8 | entity network id (`Entity.getId()`) + Threat-kind byte |

- **Threat kind**: v1 sends only `0` (`TARGETING`). The byte is reserved so post-v1
  escalation levels (e.g. `ATTACKING`) need no protocol change ([#6]).
- **Mode selection per poll batch**: additions and removals both present → send `RESET`
  with the full current set (keeps "all changes from one poll in one packet" true with a
  single mode byte; the full set is tiny — varint ids, ≤ a few dozen entries even in a
  raid). Only additions → `ADD`; only removals → `REMOVE`. Empty diff → no packet.
- **No server-side cap**: the full Threat set is always synced; the 8-indicator cap is a
  display rule (§5.4), so freed slots refill correctly and the cap can change without a
  protocol change (ADR-0002).
- **Worst-case indicator appear latency**: one poll interval, 250 ms at the default —
  faster than vanilla's 10–20-tick target re-selection cadence.
- **Lifecycle**: player disconnect → server drops the last-sent set (nothing to send).
  Dimension change → server drops the last-sent set; next poll emits `RESET`. Death of a
  threat mob → vanilla never clears its target, so the diff self-heals within one poll;
  the client additionally hides entries whose entity id no longer resolves (§5.2).
  Relog → fresh `RESET` after the handshake. State provably converges after any desync.

## 5. HUD rendering (#7 prototype resolution)

### 5.1 Orbit geometry

- The Orbit is centered on the viewport, an ellipse matched to the viewport aspect ratio
  (a circle at 1:1): x-radius = `orbitSize% × viewportWidth/2`, y-radius =
  `orbitSize% × viewportHeight/2`; `orbitSize` defaults to **78** (config §7).
- Viewport dimensions are the GUI-scaled resolution. All drawing goes through
  `DrawContext`; no raw GL (26.2's Vulkan backend).

### 5.2 Threat indicator anatomy

One indicator per Threat, each rendered **independently** (no grouping, no merge, no
inner rings):

- **Arc**: a short arc on the Orbit centered at the Threat's orbit angle, arc length
  **6°** scaling with proximity up to **18°** (≈3×) at point-blank; thickness **3 px**
  (constant).
- **Triangle**: at the arc's center angle, **12 px** (constant), pointing outward — the
  direction of the Threat.
- **Proximity**: `p = 1 − distance/DetectionRadius`, clamped to [0, 1].
  Arc length = 6° + 12°·p. Opacity = (0.3 + 0.7·p) × edge-fade × config opacity, where
  edge-fade ramps 0→1 across the outermost 3 blocks of the Detection radius (the fade-in
  at the radius edge). Thickness and triangle size do not scale with distance.
- **Stacking**: none. Co-directional Threats overlap naturally; **nearer Threats draw on
  top** (render far-to-near by distance). Two arcs ~1° apart read as two arrows; a
  perfectly co-directional pair shows the nearer (longer) arc covering the farther.
- **Cap**: at most **8** simultaneous indicators. Beyond 8, selection is
  **incumbent-sticky + nearest-first**: incumbents keep their slots while they remain
  Threats (no flicker); vacated slots refill nearest-first from the remaining Threats.
  The client owns this selection state; the server always syncs the full set.
- **Overflow presentation**: none in v1 — capped-out Threats get no indicator. (The
  prototype's "+K more" chip was left to the spec; it is deferred to post-v1 with the
  other presentation extras.)

### 5.3 Aiming modes

Two player-facing modes, both shipping in v1 (config §7):

- **Absolute (compass)** — default. Pitch ignored; the orbit reads like a ring around the
  player from above. Orbit angle = compass bearing of the Threat relative to the player's
  yaw: dead ahead → top of the orbit (12 o'clock), behind → bottom.
- **Screen-relative (view)** — Threats projected through the camera's pitch + FOV (FOV
  read from the player's game setting, not config). Behavior contract, validated in the
  prototype ([#7]):
  - Near the view center the arrow stays anchored to the compass bearing (a pure
    screen-angle mapping degenerates there);
  - Toward/past the screen edge it swings to the direction the Threat left the view
    (smoothstep blend from 0.1 to 1.0 over the normalized screen distance from view
    center);
  - Threats behind the camera point the way the player would turn (screen half they are
    behind).

### 5.4 Data flow

Each frame the renderer reads the client threat store, resolves each network id via the
client world (`getEntityById`, O(1)), drops entries whose entity is gone (staleness
self-heal), applies the cap selection, and draws. The store clears on dimension change,
respawn, and world disconnect. The HUD is hidden entirely when the config `enabled` is
false.

## 6. Rendering and version forks

| Concern | ≤ 1.21.4 (Anchors 1.21.1, 1.21.4) | ≥ 1.21.6 (Anchors 1.21.8, 1.21.11, 26.1.2, 26.2) |
|---|---|---|
| HUD registration | `HudRenderCallback.EVENT` (fabric-rendering-v1) | `HudElementRegistry.addLast` (fabric-rendering-v1) |
| Mappings | Yarn + intermediary | Mojang names (1.21.11 last Yarn; 26.x unobfuscated) |
| Loom plugin | `net.fabricmc.fabric-loom-remap` | `net.fabricmc.fabric-loom` (1.21.11+) |
| Fabric API dependency id | `fabric` | `fabric-api` (the `fabric` id is gone in 26.1) |
| Java | 21 | 25 |

Additional forks absorbed by the workspace (ADR-0006 constants/replacements):
`Entity#getWorld` → `getEntityWorld` at 1.21.9; `drawTexture` signature changes at
1.21.2/1.21.5 (per-version overloads if used); Fabric API class renames at 26.1. The
transitional `HudLayerRegistrationCallback` (1.21.5) is never targeted — 1.21.5 is not an
Anchor. GameTest annotation fork at 1.21.5: see §9.

## 7. Config (ADR-0003)

**Storage and screen**: MidnightConfig (MidnightLib, Jar-in-Jar bundled). One annotated
config class, auto-generated screen, automatic Gson persistence to
`config/whos_after_me.json`. The screen opens via ModMenu only — an **optional**
dependency (`compileOnly` + `getModConfigScreenFactory` hook); without ModMenu the JSON
file is the surface. Values apply live: the HUD reads config each frame; MidnightConfig
writes the file when its screen closes.

**Toggle keybind**: one KeyMapping via `fabric-key-mappings-api-v1`, living in the
vanilla Controls screen, shipping unbound. The keybind and the config screen's toggle are
one persisted `enabled` setting with two entry points; the keybind flips it live.

**Settings**:

| Key | Type | Default | Range | Read by | Purpose |
|---|---|---|---|---|---|
| `enabled` | bool | `true` | — | client | master HUD toggle; the keybind flips it live. Sync continues while hidden (detection is cheap and the server cannot see a live keybind flip) |
| `aimingMode` | enum | `ABSOLUTE` | `ABSOLUTE`, `SCREEN_RELATIVE` | client | Aiming mode (§5.3); follows from the prototype decision that both modes ship |
| `detectionRadius` | int (blocks) | `32` | 8–128 | **server** | Detection radius (§3) |
| `orbitSize` | int (%) | `78` | 25–100 | client | Orbit radius as % of the viewport half-dimensions (§5.1) |
| `indicatorScale` | float | `1.0` | 0.5–3.0 | client | multiplies arc thickness (3 px) and triangle size (12 px) together |
| `indicatorOpacity` | float | `1.0` | 0.1–1.0 | client | final multiplier on computed indicator opacity (§5.2) |
| `pollInterval` | int (ticks) | `5` | 1–40 | **server** | threat poll cadence (§3, §4) |

**Side semantics**: one config class served both sides; MidnightConfig loads
`config/whos_after_me.json` in whichever environment runs. `detectionRadius` and
`pollInterval` are read by the server process (on a dedicated server, by the server's own
file); HUD settings are read by the client. The server is authoritative for detection —
a client whose radius disagrees is simply overridden (ADR-0005). Not configurable in v1:
indicator cap (constant 8), clear grace (constant 1500 ms), protocol mode.

## 8. Testing and CI (ADR-0004, ADR-0007)

**Suite inventory** (each suite maps to a CI job via the matrix):

| Level | Suites | Anchors |
|---|---|---|
| Plain JUnit (no MC classes) | polled-diff engine (RESET/ADD/REMOVE, mixed→RESET); payload codec round-trip; Detection-radius predicate; 1.5 s grace state machine; >8 cap selection (incumbent-sticky + nearest-first); orbit geometry math (ellipse, scaling, both aiming modes) | all six, in `check` |
| Server GameTests | melee targets → Threat; brain-memory mob → Threat; provoked neutral → Threat once angered; ranged mob → Threat; out-of-radius mob → none; cleared target held through grace then dropped | all six, headless in `check` |
| Client GameTests | E2E ADD (dedicated server, `waitForClientboundPackets`, `runOnClient` store assertion); E2E REMOVE; screenshot smoke (`assertScreenshotContains`, small indicator crop) — smoke runs **only on the 26.2 canary** | five 1.21.4+; canary-gated smoke |
| Manual | `docs/visual-qa.md` — ten scenarios walked at release, recorded in release notes | release candidate (+ explicit 1.21.1 smoke) |

- GameTest annotation fork: 1.21.1/1.21.4 use the vanilla `@GameTest` + `TestContext`
  (Yarn); 1.21.5+ use Fabric's `gametest.v1` `@GameTest` + `GameTestHelper` — a small
  per-version test-source split under Stonecutter. 1.21.1 has no client-gametest module:
  its HUD is covered by proxy (shared source with the 1.21.4 client tests, same
  `HudRenderCallback` line) plus the manual release smoke.
- **CI gates** (ADR-0007): PR / `main` — compile + package + server gametests ×6, client
  gametests on the 26.2 canary (retry once). Weekly — client gametests ×5 (1.21.4+).
  Release — full sweep green + recorded human visual-QA sign-off. Matrix auto-generated
  from the Stonecutter workspace; JDK 25 runners; wrapper validation, Gradle cache,
  cancel-in-progress, SHA-pinned actions.

## 9. Build and toolchain (ADR-0006)

- Stonecutter 0.9.x workspace from `stonecutter-template-fabric`; versions as
  `versions/<mc>` subprojects; Kotlin DSL; per-version Loom plugin ids (§6 table);
  Gradle 9.5.x wrapper; Loom 1.17-SNAPSHOT; JDK 25 Gradle JVM; toolchains emit Java 21
  for ≤ 1.21.11; active development version 26.2; all-version builds (`chiseledBuild`) in
  CI.
- Mod init: common initializer registers payloads and the server tick hook; client
  initializer registers the HUD, keybind, and config init. `fabric.mod.json` per Anchor
  via workspace constants (dependency id, Java version, MC bounds).
- Entrypoints and packages (naming for the implementation):
  `WhosAfterMe` (common), `WhosAfterMeClient` (client); packages `detection` (server poll,
  grace, diff), `net` (hello, threat_sync, handshake), `client.store` (threat store, cap
  selection), `client.hud` (orbit math, renderer), `config` (MidnightConfig class).

## 10. Open items

- **Icon / branding assets** — the only fog left on the map. Needed before first release
  (Modrinth requires a 128×128 icon). Owner input required; blocked only from the release
  ticket, nothing else.
- **Owner sign-off on this spec** ([#11]) — gates the implementation ticket breakdown
  being filed.

## 11. Implementation ticket breakdown

Ordered; "after" lists blocking edges. One ticket → one branch → one PR (repo
conventions). Decision references in parentheses.

1. **Scaffold the Stonecutter workspace** (ADR-0006) — after: —.
   Six Anchor subprojects, per-version Loom plugin ids, Gradle/Loom/JDK pins, 26.2
   active, workspace constants for the §6 forks, minimal buildable mod per Anchor.
   Done: `chiseledBuild` produces six jars; CI bootstrap job green.
2. **Mod skeleton** — after: 1.
   Common + client entrypoints, `fabric.mod.json` metadata (§1), Apache-2.0, per-Anchor
   dependency ids, logging, lang keys.
3. **Config: settings, persistence, screen, keybind** (ADR-0003) — after: 2.
   The §7 settings table, MidnightConfig JIJ wiring, ModMenu optional hook, Controls
   keybind flipping `enabled` live.
4. **Orbit geometry module** (#7) — after: 2 (parallel with 5–7).
   Pure math: ellipse mapping, bearing→orbit angle (Absolute), pitch+FOV projection and
   blend (Screen-relative), proximity arc-length/opacity/edge-fade, per-frame inputs as
   parameters. JUnit-suiteable, no Minecraft classes.
5. **Server detection engine** (ADR-0001) — after: 3.
   Uniform read (`getTarget()` + `ATTACK_TARGET` memory), radius predicate, per-player
   poll loop, 1.5 s grace state machine, diff computation vs last-sent (pure logic
   separated for JUnit).
6. **Sync protocol** (ADR-0002) — after: 5.
   `hello` + `threat_sync` payloads and codecs, handshake marking, join `RESET`,
   disconnect/dimension-change state handling, mode selection rule (§4), server tick
   confinement. Codec round-trip JUnit.
7. **Client threat store** — after: 6.
   Store keyed by network id, ADD/REMOVE/RESET application, entity-resolution staleness
   self-heal, clear on dimension/respawn/disconnect, cap selection (incumbent-sticky +
   nearest-first). Selection JUnit.
8. **HUD renderer** (#7) — after: 4, 7.
   `DrawContext` arc + triangle drawing, both HUD API paths (§6), per-frame config read,
   far-to-near z-order, hide-when-disabled.
9. **Server gametests** (ADR-0004) — after: 5, 6.
   The six integration tests (§8), per-version test-source split at the 1.21.5 fork,
   wired into `check` via Loom `configureTests`.
10. **Client gametests** (ADR-0004) — after: 8.
    E2E ADD + REMOVE on 1.21.4+; screenshot smoke template confined to the 26.2 canary.
11. **CI matrix and release gate** (ADR-0007) — after: 1 (extends as 9, 10 land).
    Generated per-version matrix, canary client job with retry, weekly 1.21.4+ sweep,
    tag-push full sweep, artifact gating.
12. **Release prep** — after: 9, 10, 11; also blocked on the icon open item (§10).
    Visual-QA dry run on a 1.21.1 + 26.2 candidate against `docs/visual-qa.md`,
    Modrinth/CurseForge page metadata from §1, icon/branding assets, first release.

**Version-rolling plan** (ADR-0006): a new stable line enters the workspace within ~2
weeks of a stable Fabric API build for it (new subproject + constants; CI extends
automatically); patch releases ride their line (bump, rebuild, ship); the Anchor set must
always cover both toolchain eras and both HUD code paths. Rolling work rides normal
`chore`/`feat` branches; it does not re-open this spec.

[#3]: https://github.com/imyifeng/whos-after-me/issues/3
[#4]: https://github.com/imyifeng/whos-after-me/issues/4
[#5]: https://github.com/imyifeng/whos-after-me/issues/5
[#6]: https://github.com/imyifeng/whos-after-me/issues/6
[#7]: https://github.com/imyifeng/whos-after-me/issues/7
[#8]: https://github.com/imyifeng/whos-after-me/issues/8
[#9]: https://github.com/imyifeng/whos-after-me/issues/9
[#10]: https://github.com/imyifeng/whos-after-me/issues/10
[#11]: https://github.com/imyifeng/whos-after-me/issues/11
[#12]: https://github.com/imyifeng/whos-after-me/issues/12
[#13]: https://github.com/imyifeng/whos-after-me/issues/13
[#14]: https://github.com/imyifeng/whos-after-me/issues/14
[#17]: https://github.com/imyifeng/whos-after-me/issues/17
[#18]: https://github.com/imyifeng/whos-after-me/issues/18
