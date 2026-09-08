# Config storage and screen: MidnightConfig; the toggle keybind lives in vanilla Controls

The v1 config ([#13]) is minimal — a master enabled toggle (plus a runtime toggle keybind),
detection radius, orbit radius, indicator size, indicator opacity — but it must build across six
anchor versions spanning the 1.21.1 → 26.2 no-remap break ([#8]). We mandate **MidnightConfig
(MidnightLib), Jar-in-Jar bundled** for both persistence and the config screen, with the toggle
keybind registered via `fabric-key-mappings-api-v1` in the vanilla Controls screen and ModMenu
wired as an optional dependency.

## Decision shape

- **Library**: MidnightConfig owns storage and screen. It is the only candidate whose single
  upstream artifact line (1.9.3) covers all six anchors, and its consumer API
  (`MidnightConfig.init`, `@Entry` fields, `MidnightConfig.getScreen`) is identical on both
  sides of the no-remap break, so the mod's config code needs no per-version fork. Cost is the
  smallest of all options: an annotated config class, an auto-generated screen, automatic Gson
  persistence, and a one-line ModMenu hook. Upstream is itself a Stonecutter workspace and
  explicitly encourages Jar-in-Jar bundling, so users install nothing extra.
- **Keybind**: one KeyMapping in the vanilla Controls screen — the standard mod pattern, where
  players already look for keybinds. The keybind and the config screen's toggle are **one
  persisted `enabled` setting with two entry points**: the keybind flips it live, the screen
  edits the same value. It ships unbound by default.
- **Entry point**: ModMenu is the only config-screen entry in v1, as an **optional** dependency
  (`compileOnly` + the `getModConfigScreenFactory` hook; without ModMenu the mod still works,
  with the JSON file as the fallback surface). The hook's shape is stable across the break; its
  types follow the Yarn→Mojang rename, a cost paid under any option.
- **Orbit radius** joins the v1 config as a slider, reconciling the map's "radius configurable"
  HUD decision with CONTEXT.md's player-adjustable **Orbit** definition.

## Considered options

- **Cloth Config** — the de-facto standard (rejected): six version pins across the span, v26.1
  removed `AutoConfig.getConfigScreen(...)` so the canonical ModMenu snippet no longer compiles
  on 26.x, persistence is manual, and its unique asset — the built-in keybind entry — is
  outweighed by the standard Controls-screen pattern it would buy.
- **YACL** (rejected): no keybind controller, persistence manual (or a bet on the brand-new v2
  config-class path), two pin lines (3.8/3.9), largest jar.
- **Hand-rolled screen + Gson** (rejected): ~250–400 lines across the Yarn/Mojang fork plus
  owning all future vanilla GUI churn on six anchors — for five settings.
- **Vanilla `SimpleOption`/`GameOptions`** (rejected, research finding): hardwired to
  `options.txt` and the vanilla settings-screen flow.

## Consequences

- MidnightLib is effectively single-maintainer; the exposure is bounded by our tiny consumer
  surface (one annotated class plus one `getScreen` call), so a later migration stays cheap.
- The config screen's look and UX are MidnightLib's; custom screen work is out of scope for v1.
- Config values apply live — the HUD reads config each frame — and MidnightConfig writes the
  JSON file when its screen closes.

Evidence trail: decision in [#13]; library landscape research in [#12].

[#8]: https://github.com/imyifeng/whos-after-me/issues/8
[#12]: https://github.com/imyifeng/whos-after-me/issues/12
[#13]: https://github.com/imyifeng/whos-after-me/issues/13
