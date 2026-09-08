# Research: server-side threat detection hooks and sync options

Resolves research for [#6]. Companion to the domain glossary in [`CONTEXT.md`](../../CONTEXT.md)
("Threat" = a mob whose current attack target is the observing player; bounded by the fixed,
wall-transparent detection radius).

Scope: Minecraft Java Edition 1.21.1 through the 2026 calendar-versioned drops (26.x).
This document presents options and trade-offs only — no final decision is made here
(that belongs to the follow-up decision ticket).

Sources are cited inline. Vanilla behavior was verified against Yarn-mapped decompiled
sources for 1.21.1 ([youxuezhe7/Minecraft1.21.1-source]) and 1.21.11
([duollectis/minecraft-decompiled-1.21.11]) plus the official Yarn javadocs on
[maven.fabricmc.net]; community behavior claims were checked against
[Minecraft Wiki](https://minecraft.wiki). Prior art: [AggroIndicator] (source inspected).

---

## 1. How vanilla represents "attack target"

### 1.1 The two storage systems: goals vs brains

There is no single "target" store. As of 1.21.x a mob's target lives in one of two places:

**Goal-based mobs** store the target in a private field on `MobEntity`:

```java
// 1.21.1, Yarn mappings (decompiled)
public @Nullable LivingEntity getTarget() { return this.target; }
public void setTarget(@Nullable LivingEntity target) { this.target = target; }
```

Writers of the field:

- `ActiveTargetGoal.start()` and subclasses — periodic search (`reciprocalChance` = average
  ticks between searches, so re-selection happens on a 10–20 tick cadence, not every tick).
- `RevengeGoal`, `TrackOwnerAttackerGoal`, `AttackWithOwnerGoal`, raid/untamed/tame
  variants (23 `TrackTargetGoal` subclasses in 1.21.1, see [javadoc class-use list]).
- `TrackTargetGoal.stop()` — **clears** the target: `this.mob.setTarget(null)` when the
  target dies, leaves follow range, or loses visibility for long enough. The Yarn javadoc
  for `TrackTargetGoal` states: "If the goal stops, such as because the target is not
  valid, the target is removed from the owner mob."
- Individual attack goals also clear it directly, e.g. `MeleeAttackGoal.stop()` and
  `CrossbowAttackGoal.stop()` call `setTarget(null)` (verified in 1.21.1 source).
- Custom one-off goals: `PhantomEntity.FindTargetGoal` (a plain `Goal`, *not* a
  `TrackTargetGoal`) calls `setTarget(player)` on acquire, and the phantom's swoop goal
  calls `setTarget(null)` in its `stop()` (1.21.1 source).

**Brain-based mobs** store the target in `MemoryModuleType.ATTACK_TARGET`:

- `UpdateAttackTargetTask.create(...)` writes the chosen entity into the brain memory
  (verified: it calls `attackTarget.remember(newTarget)` and **never** calls
  `MobEntity.setTarget`). Forgetting tasks (`ForgetAttackTargetTask`, `DefeatTargetTask`)
  clear the memory.
- These mobs **override `getTarget()` to read the brain**, bypassing the field entirely.
  Verified overrides in 1.21.1 and 1.21.11 decompiled sources:
  `WardenEntity`, `BreezeEntity`, `CreakingEntity`, `AbstractPiglinEntity` (→ piglin,
  piglin brute), and (via code search on `getTargetInBrain`) `HoglinEntity`,
  `ZoglinEntity`, `AxolotlEntity`, `FrogEntity`.
- Consequence: **any hook placed only on `MobEntity.setTarget` misses every brain mob.**
  This is a long-known API trap ([PaperMC#6862], "Missing API for accessing targets of
  mobs using new AI brain system").

### 1.2 What is synced to the client already

The client does **not** know `getTarget()`. It only sees:

| Synced signal | Where | Meaning |
|---|---|---|
| `MOB_FLAGS` bit `ATTACKING_FLAG` (0x04) | `MobEntity` data tracker (byte) | Set by attack-*execution* goals while in attack range/drawing: `MeleeAttackGoal.start/stop`, `BowAttackGoal.start/stop` (the ticket's "RangedBowAttackGoal"; that is the 1.21.1 Yarn name), `ZombieAttackGoal`, `CrossbowAttackGoal`, plus `RaiderEntity`, `AbstractSkeletonEntity`, `DrownedEntity`. This is the vanilla skeleton bow-pose flag. It means "actively attacking", **not** "has a target" — it turns on much later than target acquisition. |
| `CHARGING` (per-class `TrackedData<Boolean>`) | e.g. `PillagerEntity`, `PiglinEntity` | Crossbow charging stages from `CrossbowAttackGoal` via `CrossbowUser.setCharging(boolean)`. |
| `FUSE_SPEED` (`TrackedData<Integer>`) | `CreeperEntity` | Creeper swell/prime animation, synced continuously. |

So a purely client-side mod can only approximate threats (e.g. "mob is attacking
something near me"), which is why comparable mods require both sides (see section 5).

### 1.3 Reading vs. hooking

A key asymmetry: **reading** `mob.getTarget()` is uniform across both systems — the field
for goal mobs, the override for brain mobs. **Hooking writes** is not: the two systems
acquire and clear targets through disjoint code paths. This drives the whole options
analysis below.

## 2. Per-mob quirks

| Mob | Targeting system | Quirks for detection |
|---|---|---|
| Zombie, spider, enderman, skeletons (bow), vindicator, vex, guardians, shulker, bee, wolf, polar bear, llama, fox, panda, iron golem, ... | Goal (`TrackTargetGoal` family, 23 subclasses in 1.21.1) | Regular `setTarget`/clear lifecycle. Enderman's `TeleportTowardsPlayerGoal` adds stare-triggered anger; spider `TargetGoal` is light-level conditional. `CreeperEntity` **overrides `setTarget`** to silently reject `GoatEntity` targets — an injection that reads the method *argument* would see a target the mob never stored; read the field after `RETURN` instead. |
| Skeletons with bow | `BowAttackGoal` | `setAttacking(true)` only in `start()`; the synced ATTACKING flag lights up when in range/drawing — usable as an "about to loose" escalation signal, not for acquisition. |
| Pillager, piglin (crossbow) | `CrossbowAttackGoal` (goal) | Explicit stage machine (UNCHARGED→CHARGING→CHARGED); charge state is client-visible via `CHARGING` data tracker. |
| Creeper | Goal + swell | Target via classic goals; swell (`FUSE_SPEED`) is separately synced and already visible client-side; detonation is goal-driven once target is in range. A creeper approaching with a target but not yet swelling is the interesting window. |
| Phantom | Custom plain `Goal`s calling `setTarget` | Not a `TrackTargetGoal`; acquire/clear both happen via custom goals — but still through `setTarget`, so field-reads and setTarget hooks still see it. Picks insomnia-lacking players within 64 blocks. |
| Ghast | **No classic targeting** | No `setTarget` call sites found in `GhastEntity` (1.21.1); it aims fireballs from its own attack goal without ever storing a target. Under the glossary's "current attack target is the observing player" definition, ghasts can never be threats unless specially cased. Open question. |
| Zombified piglin, wolf, bee, piglin (anger) | `Angerable` interface | `Angerable` stores `anger_end_time` + `angry_at` UUID, persisted to NBT ([Entity format/Angerable]). On world reload, `readAngerFromNbt` directly calls `setTarget(player)` — a target change that happens *outside* any goal lifecycle. `tickAngerLogic`/`stopAnger` expire it. |
| Warden | Brain | `getTarget()` = ATTACK_TARGET memory. Anger tracked **per suspect, 0–150**; `Angriness` thresholds CALM 0 / AGITATED 40 / ANGRY 80 (1.21.1 source; [Minecraft Wiki: Warden]). Direct damage sets the target immediately if none set; vibrations raise anger until the warden targets the top suspect. |
| Breeze | Brain | ATTACK_TARGET memory; jump/wind-charge task chain. Covered by `getTarget()` read; invisible to a setTarget-only hook. |
| Creaking (1.21.4+) | Brain + heart link | ATTACK_TARGET memory; existence is tied to a `CreakingHeartBlockEntity` "puppet"; `isUnrooted()` distinguishes active from dormant. Only active, unrooted creakings chase — detection likely wants `getTarget()` *and* activation state. ([Minecraft Wiki: Creaking](https://minecraft.wiki/w/Creaking)) |
| Hoglin, zoglin, axolotl, frog, goat (ram) | Brain | Same pattern: memory-driven, `getTargetInBrain()`; goats ram via brain tasks (prior art mixes into `PrepareRamNearestTarget`/`RamTarget` — see section 5). |

## 3. Extension points

### 3.1 Fabric API events — nothing fits directly

Inventory of relevant Fabric API modules (1.21.x era):

- `fabric-entity-events-v1` → `ServerLivingEntityEvents`: `ALLOW_DAMAGE`,
  `AFTER_DAMAGE`, `ALLOW_DEATH`, `AFTER_DEATH`, `MOB_CONVERSION` ([javadoc]).
  Damage events correlate with *revenge* targeting but not with goal re-acquisition,
  path loss, or out-of-range clears.
- `fabric-lifecycle-events-v1` → `ServerEntityEvents`: `ENTITY_LOAD`, `ENTITY_UNLOAD`,
  `START_TRACKING`, `UNTRACK` ([javadoc]) — useful for *lifecycle housekeeping*
  (resync, cleanup), not for target changes.
- `ServerEntityCombatEvents` (kill events) — not targeting.

**There is no target-change event in Fabric API** (and none is planned that I could find).
Every implementation must therefore either poll state or mixin vanilla.

### 3.2 Option A: poll `getTarget()` (no AI-internals mixins)

Scan mobs near each player on a throttle (e.g. every 5–10 ticks via
`ServerTickEvents.END_SERVER_TICK`), compute `mob.getTarget() == player && within radius`,
diff against the last-synced set, send deltas.

- Pros:
  - **Uniform across goal and brain mobs** (the only single-point read that is).
  - Immune to AI internals churn: `getTarget()` is public API, stable signature
    1.21.1 → 1.21.11 (verified in decompiles); `TrackTargetGoal`/brain tasks can be
    rewritten freely underneath.
  - Self-healing: mob death, despawn, chunk unload, dimension change, missed events —
    all just show up as "left the set".
  - No mixin compatibility surface at all (only networking).
- Cons:
  - Detection latency ≤ poll interval (irrelevant at 5–10 ticks; goal re-selection
    itself happens on a 10–20 tick cadence).
  - Per-tick cost: one `getEntitiesByClass(MobEntity.class, box, ...)` per player per
    interval, radius-bounded (detection radius from `CONTEXT.md` is small). Negligible,
    and spreadable across ticks (round-robin players).
  - Misses *transient* states invisible in `getTarget()` (e.g. "bow drawn" needs the
    synced ATTACKING flag or goal mixins — but the ATTACKING flag is also readable in
    the same poll, `mob.isAttacking()`).

### 3.3 Option B: event-driven via mixins on the write paths

Two hooks needed to cover both systems:

1. `MobEntity.setTarget` (`@Inject` at `RETURN`, then read the *field*, not the arg —
   see the creeper override quirk). Signature stable across 1.21.1–26.x: AggroIndicator
   ships this exact hook for 1.16.5 → 26.1.1 ([Modrinth: AggroIndicator]).
2. Brain writes. Choices, in decreasing fragility:
   - `Brain.setMemoryInternal` filtered on `MemoryModuleType.ATTACK_TARGET`
     (AggroIndicator's approach) — an *internal* method; also requires duck-typing a
     "brain owner" onto `Brain` because `Brain` holds no owner reference
     (AggroIndicator tracks owners by mixin into entity-add, `PersistentEntitySectionManager.addEntity`);
     plus `clearMemories`. High churn risk across 26.x drops.
   - `UpdateAttackTargetTask` / forgetting tasks — task classes are rewritten almost
     every drop (e.g. `DefeatTargetTask` split out by 1.21.9, [javadoc]); brittle.

- Pros: zero-latency events; no polling loop.
- Cons:
  - **Mob death does not call `setTarget(null)`** — an event-driven design must
    additionally observe death/remove/unload to avoid stale indicators, i.e. it needs
    most of the poll's lifecycle handling anyway.
  - Two heterogeneous hook points; the brain one is version-fragile.
  - Overrides that do not call `super.setTarget` bypass a base-class hook (creeper's
    does call super; nothing guarantees others will).

### 3.4 Option C: hybrid (event fast-path + slow poll reconciliation)

Best UX and best robustness; strictly the union of the costs. Sensible as a later
optimization if the poll's 5–10 tick latency is ever visible (it will not be, given the
indicator is a HUD arc, not a combat-critical hitbox signal).

**Summary:** Option A dominates on robustness-per-complexity; B/C only buy sub-tick
latency.

## 4. Sync design

### 4.1 Transport

Fabric `fabric-networking-api-v1` custom payloads, play S2C:

- `PayloadTypeRegistry.playS2C().register(ID, CODEC)` — **must run in the common
  initializer on both logical sides, before registering listeners** ([PayloadTypeRegistry javadoc]; [Fabric docs: networking]).
- Server: `ServerPlayNetworking.send(player, payload)`; Client:
  `ClientPlayNetworking.registerGlobalReceiver` (prior art: [AggroIndicator FabricNetworkHandler / client initializer]).
- `PlayerLookup.tracking(entity)` / `PlayerLookup.around(world, pos, r)` give the
  audience for per-mob fan-out ([PlayerLookup javadoc]).
- Works unchanged on integrated (singleplayer) and dedicated servers: in singleplayer
  the client is attached via an in-memory loopback channel, so the same payload path
  runs; the only real difference is that both sides share one JVM — keep server state
  server-thread-confined and mutate client state inside the receiver's
  `context.client().execute()` (as AggroIndicator does).

### 4.2 Option 1: event-driven per-change packets

One packet per threat add/clear, fanned out to the affected player (AggroIndicator
model: `(mobUuid, targetThisPlayer, isAboutToAttack)`).

- Pros: minimal bandwidth; reacts immediately when paired with event hooks.
- Cons: server must keep a `mob → target` map for dedup (send only on *change*), and
  must handle resync on player join / `START_TRACKING`, cleanup on logout
  (`ServerPlayConnectionEvents.DISCONNECT` / `PlayerList.remove`), and every
  death/despawn/unload path. Missed clears = permanently stuck indicator until a
  reconciliation pass runs (AggroIndicator's map bookkeeping exists precisely for this).

### 4.3 Option 2: throttled per-tick diff (recommended pairing with poll detection)

Each poll interval, per player: compute the current threat set, diff against the last
set sent to that player, send only deltas. Optionally fold into one batched packet.

- Pros: no bookkeeping of mob lifecycle at all; self-healing by construction; per-player
  state is one small set.
- Cons: latency ≤ interval (fine); needs the poll anyway (which Option A detection
  provides for free — detection and sync collapse into one loop).

### 4.4 Recommended packet contents (for the decision ticket to weigh)

- Entity reference: **`int` network id (`Entity.getId()`)** vs `UUID`.
  Network id is what vanilla entity packets use and resolves client-side via
  `world.getEntityById` in O(1); it naturally invalidates on despawn/death (client
  drops unresolvable ids — desirable). UUID is stable across tracking cycles but
  needs an O(n) or map-based lookup. Both are defensible; network id is smaller and
  simpler.
- Threat kind enum (1 byte), e.g. `TARGETING` vs `ATTACKING` (target set vs
  synced ATTACKING flag lit) — richer: `MELEE / RANGED_DRAW / CROSSBOW_CHARGE /
  EXPLODING`. The extra signal is available server-side for free in a poll
  (`mob.isAttacking()`).
- Add/clear boolean or explicit delta list (varint count + entries) if batching.

### 4.5 Client-side handling requirements (any option)

- Resolve ids to entities each frame; drop entries whose entity is gone (death,
  despawn, untracked).
- Clear all entries on player dimension change, respawn, and world disconnect.
- Server-side: entries for a player are naturally recomputed on dimension change /
  join because detection is per-player state, not per-mob state (another Option 2
  advantage).

## 5. Prior art

**[AggroIndicator]** (Raverbury, Fabric+Forge+NeoForge, ships 1.16.5 → 26.1.1,
client+server required) — the closest open-source analog, architecture inspected on
branch `multiloader_1.21.1`:

- Mixin `MobMixin` → `Mob#setTarget` at `RETURN`, dispatches on change, reads the field
  not the argument.
- Mixin `BrainMixin` → `Brain#setMemoryInternal` (filtered to ATTACK_TARGET) +
  `clearMemories`, with a `BrainAccess` duck interface; brain owners assigned by mixin
  into `PersistentEntitySectionManager#addEntity` (recreating Forge's
  `EntityJoinLevelEvent` — on Fabric that step can use `ServerEntityEvents.ENTITY_LOAD`
  instead of a mixin).
- Server keeps `Map<UUID mob, UUID targetPlayer>` for dedup; `PlayerList#remove` mixin
  clears on logout.
- Event-driven S2C `S2CMobChangeTargetPacket(mobUuid, targetThisPlayer,
  isAboutToAttack)`; client mutates renderer state on the client thread.
- Separate goal mixins (`RangedBowAttackGoal`, `RangedCrossbowAttackGoal`,
  `RangedAttackGoal`, ram tasks) power the "about to attack" escalation signal.
- Longevity (9 years of versions on one detection design) is evidence both that the
  `setTarget` hook is durable *and* that the brain-side hook needed rework across
  versions (it exists in multiple per-version branches).

Others: "Radial Aggro Indicator" (Forge/NeoForge only) offers a similar radial
on-screen direction indicator ([Modrinth]), showing player appetite for the exact
shape this mod is building.

## 6. Options at a glance

| | A: poll + throttled diff | B: mixin events + per-change packets | C: hybrid |
|---|---|---|---|
| Covers brain mobs (warden, piglin, breeze, creaking...) | Yes | Only with fragile `Brain` hook | Yes |
| Covers ghast | No (vanilla never sets a target) | No | No |
| Vanilla-internals mixin surface | None | `setTarget` (stable) + brain internals (fragile) | Both |
| Stale-indicator risk (death/despawn/unload) | Self-healing | Must hand-handle every path | Self-healing |
| Latency | ≤ poll interval (5–10 ticks) | 0 ticks | 0 ticks |
| Resync on join/dimension change | Free | Manual | Free |
| Bandwidth | Deltas only | Deltas only | Deltas only |
| Server CPU | Tiny, radius-bounded | ~zero | Tiny |
| Cross-version maintenance cost | Lowest | Highest | Highest |

## 7. Open questions (for the decision ticket)

1. Poll interval vs. event fast-path: is 5–10 tick latency acceptable for the HUD arc?
2. Payload id: entity network id vs UUID (section 4.4).
3. Threat-kind granularity: does the product want the "about to attack" escalation
   (ATTACKING flag / ranged draw), or target-only?
4. Non-targeting threats: ghasts (never set a target), creakings while rooted,
   warden pre-target anger stages — in or out of scope for v1 under the CONTEXT.md
   definition?
5. Client-only degradation mode: without a server-side companion, detection is limited
   to synced proxies (ATTACKING flag, crossbow charge, creeper fuse) — ship a degraded
   client-only mode or require both sides (AggroIndicator requires both)?
6. Integrated-server thread-safety policy: keep all server threat state inside the
   server tick loop and all client state inside packet handlers (recommended), or allow
   shared concurrent structures?

## Sources

- [MobEntity (yarn 1.21.1 javadoc)](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/entity/mob/MobEntity.html)
- [TrackTargetGoal javadoc ("If the goal stops ... the target is removed")](https://maven.fabricmc.net/docs/yarn-1.19.1-pre4+build.3/net/minecraft/entity/ai/goal/TrackTargetGoal.html) and [class-use list, yarn 1.21.1](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/entity/ai/goal/class-use/TrackTargetGoal.html)
- [ActiveTargetGoal (yarn 1.21.1 javadoc)](https://maven.fabricmc.net/docs/yarn-1.21.1+build.3/net/minecraft/entity/ai/goal/ActiveTargetGoal.html)
- Decompiled Yarn-mapped vanilla: [1.21.1 source mirror], [1.21.11 source mirror] (MobEntity, TrackTargetGoal, BowAttackGoal, CrossbowAttackGoal, MeleeAttackGoal, PhantomEntity, CreeperEntity, WardenEntity, WardenBrain/Angriness, Brain tasks, Targeter)
- [PaperMC#6862 — brain-mob targets vs getTarget/setTarget](https://github.com/PaperMC/Paper/issues/6862)
- [UpdateAttackTargetTask.TargetGetter (yarn 1.21.4 javadoc)](https://maven.fabricmc.net/docs/yarn-1.21.4+build.4/net/minecraft/entity/ai/brain/task/class-use/UpdateAttackTargetTask.TargetGetter.html)
- [Angerable (yarn javadoc)](https://maven.fabricmc.net/docs/yarn-23w03a+build.1/net/minecraft/entity/mob/Angerable.html) and [Entity format/Angerable (Minecraft Wiki)](https://minecraft.wiki/w/Entity_format/Angerable)
- [Warden (Minecraft Wiki)](https://minecraft.wiki/w/Warden) — per-suspect anger 0–150
- [ServerLivingEntityEvents javadoc](https://maven.fabricmc.net/docs/fabric-api-0.106.1+1.21.2/net/fabricmc/fabric/api/entity/event/v1/ServerLivingEntityEvents.html), [ServerEntityEvents javadoc](https://maven.fabricmc.net/docs/fabric-api-0.80.3+1.20/net/fabricmc/fabric/api/event/lifecycle/v1/ServerEntityEvents.html)
- [PayloadTypeRegistry javadoc](https://maven.fabricmc.net/docs/fabric-api-0.97.1+1.20.5/net/fabricmc/fabric/api/networking/v1/PayloadTypeRegistry.html), [PlayerLookup javadoc](https://maven.fabricmc.net/docs/fabric-api-0.97.1+1.20.5/net/fabricmc/fabric/api/networking/v1/PlayerLookup.html), [Fabric docs: events](https://docs.fabricmc.net/develop/events)
- [Java Edition 26.1 (Minecraft Wiki)](https://minecraft.wiki/w/Java_Edition_26.1) — 2026 calendar versioning (26.1 "Tiny Takeover", 26.2)
- [Modrinth: AggroIndicator](https://modrinth.com/mod/aggroindicator), [source](https://github.com/Raverbury/AggroIndicator/) (branch `multiloader_1.21.1`); [Modrinth: Radial Aggro Indicator](https://modrinth.com/mod/radial-aggro-indicator)

[#6]: https://github.com/imyifeng/whos-after-me/issues/6
[youxuezhe7/Minecraft1.21.1-source]: https://github.com/youxuezhe7/Minecraft1.21.1-source
[duollectis/minecraft-decompiled-1.21.11]: https://github.com/duollectis/minecraft-decompiled-1.21.11
[maven.fabricmc.net]: https://maven.fabricmc.net/docs/
[AggroIndicator]: https://github.com/Raverbury/AggroIndicator/
[PaperMC#6862]: https://github.com/PaperMC/Paper/issues/6862
[Modrinth]: https://modrinth.com/mod/radial-aggro-indicator
