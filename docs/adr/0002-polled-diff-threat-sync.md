# Threat sync is a server-side polled diff, not event-driven packets

Threat sync ([#10]) is a **per-player polled diff protocol**. Every 5 server ticks
(configurable), the server recomputes the threat set for each modded player — every mob within
the detection radius whose attack target (per ADR-0001's uniform read) is that player — diffs it
against the set last sent to that player, and sends one packet only when the diff is non-empty.
The client renders whatever the packets say and owns no detection logic and no hysteresis clock.

## Protocol shape

- **Handshake**: on join, a modded client sends a tiny C2S `hello` payload carrying a u8 protocol
  version. The server marks the player as modded, immediately sends a full-state `RESET`, and
  never sends threat packets to unmarked (vanilla) players.
- **Single S2C payload** with a mode byte — `RESET` (replace the whole set),
  `ADD` / `REMOVE` (diff entries). All changes from one poll batch into one packet.
- **Entry**: varint entity network id (`Entity.getId()`) + threat-kind byte (reserved; v1 sends
  only `TARGETING`, post-v1 escalation rendering per [#9]).
- **Threat-kind granularity**: v1 has no escalation fast-path; the ATTACKING mob flag remains
  unused (documented in [#6] as an escalation signal, not "has target").

## Lifecycle and ordering

- **Hysteresis lives server-side**: the 1.5 s target-clear grace ([#9]) delays `REMOVE`; a
  re-acquire inside the grace emits no packet at all. Radius exit removes immediately.
- **Death/despawn**: vanilla never clears targets on death, so the diff self-heals within one
  poll; the client additionally hides an indicator whose entity id has left the client world
  (belt-and-braces; pure client rendering logic, no protocol change).
- **Dimension change / relog**: the client clears its indicator set, the server drops the
  player's last-sent set and follows with a `RESET` — state provably converges after any desync.
- **Ordering**: all server protocol state (per-player last-sent sets, grace timers) is confined
  to the server tick; the single sender makes per-player packet order deterministic with no
  locks. Client payload handlers hop from the Netty thread to the client thread before touching
  world/render state; behavior is identical on integrated and dedicated servers.

## Considered options

- **Event-driven mixins** (`setTarget` write-path + brain memory hooks, the AggroIndicator
  approach) (rejected): 0-tick latency, but the brain hook churns every Minecraft drop, every
  death/despawn/unload path needs manual bookkeeping, and the mixin surface is the single
  biggest multi-version risk across the rolling 1.21.1–26.2 range.
- **Hybrid** (poll fallback + event fast-path) (rejected): two mechanisms to keep consistent for
  a saving of at most 5 ticks on indicator appear — invisible for a HUD arc.
- **UUID entity ids** (rejected): 16 bytes vs ~1–4, O(n) client lookup, and a despawned UUID
  stays resolvable, so it cannot drive the client-side staleness self-heal.
- **Server-side cap at 8 entries** (rejected): the top-8 incumbent-sticky fill ([#9]) is a
  display rule; only a full set on the client lets freed slots be refilled correctly, and the
  cap threshold can then change without touching the protocol.

The 5-tick interval samples faster than vanilla's 10–20-tick target re-selection cadence, so the
diff tracks the underlying signal change rate; worst-case indicator appear latency is 250 ms.

Evidence trail: decision in [#10]; detection and sync-options research in [#6].

[#6]: https://github.com/imyifeng/whos-after-me/issues/6
[#9]: https://github.com/imyifeng/whos-after-me/issues/9
[#10]: https://github.com/imyifeng/whos-after-me/issues/10
