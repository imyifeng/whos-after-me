# Threat detection is server-authoritative; the client only renders

The mod is **required on both sides**. The server detects Threats — every mob whose attack
target (per ADR-0001's uniform read) is the observing player, within the Detection radius —
diffs and syncs them per ADR-0002, and the client renders the HUD from those packets. The
client owns **no detection logic**: no heuristics over synced proxies (the `ATTACKING` mob
flag, crossbow `CHARGING`, creeper `FUSE_SPEED`), no fallback mode for vanilla servers.
Decided at charting ([map #3]) and confirmed in the detection research ([#6]).

## Why

- **The client cannot know targets.** `getTarget()` is server-only state; the only
  client-visible signals are appearance flags that mean "actively attacking", not "has a
  target" — they light up late (skeletons draw, creepers swell) and never for mobs that
  target without entering attack range. A heuristic client would show wrong Threats,
  late Threats, or both.
- **The product definition needs target identity.** Threat is "attack target == observing
  player", including provoked neutrals and wall-transparent detection through the Detection
  radius. Neither is reconstructable from synced data.
- **One read-point, zero AI mixins.** The polled uniform read is the single hook that covers
  goal mobs and brain mobs; it lives server-side where that state lives. Mixing in vanilla
  AI internals (the client-only alternative's prerequisite) would import the exact
  multi-version fragility ADR-0002 rejected for the write path.
- **PvP exclusion falls out for free.** Players are never `MobEntity` targets under the
  uniform read; a heuristic mode would need bespoke player-filtering.

## Consequences

- On a vanilla server (mod client only), the mod shows nothing. This is a documented,
  accepted limitation — a pure-client heuristic mode is explicitly out of scope for v1 and
  beyond.
- Singleplayer and dedicated servers take the same code path: the integrated server shares
  the client JVM but all server protocol state stays confined to the server tick, and client
  packet handlers hop to the client thread before touching render state (ADR-0002).
- The Detection radius is a server-side read of the config (ADR-0003's file, loaded by
  whichever side runs the poll); a client whose file disagrees is simply overridden by the
  authoritative server.
- A server without the mod and a client with it interoperate as vanilla in both directions
  (the client's handshake goes unanswered; it renders an empty orbit).

Evidence trail: standing decision from charting ([map #3]); detection and sync-options
analysis in [#6]; protocol consequences fixed in [#10].

[map #3]: https://github.com/imyifeng/whos-after-me/issues/3
[#6]: https://github.com/imyifeng/whos-after-me/issues/6
[#10]: https://github.com/imyifeng/whos-after-me/issues/10
