# Fabric API and Yarn landscape: Minecraft 1.21.1 through 26.2

Research for [issue #4](https://github.com/imyifeng/whos-after-me/issues/4), parent [#3](https://github.com/imyifeng/whos-after-me/issues/3).
Date: 2026-09-08. Primary sources: Fabric meta API (`meta.fabricmc.net`), Modrinth API (Fabric API builds), `fabricmc.net` version blog posts, `docs.fabricmc.net`, `minecraft.wiki`.

## TL;DR decisions this research supports

1. **There is no single codebase that compiles across the whole range.** 1.21.1 through 1.21.11 are Yarn-mapped and obfuscated; 26.1+ is unobfuscated, officially mapped with Mojang names, and needs different Loom/Gradle/Java. Plan two version lines (e.g. via Stonecutter): `1.21.1` (or `1.21.4`/`1.21.11`) on one side and `26.x` on the other.
2. **The HUD API changed three times in this range.** `HudRenderCallback` (works on 1.21.1-1.21.4) was deprecated in Fabric API 0.116.0+1.21.5 in favor of `HudLayerRegistrationCallback`, which was itself rewritten in 1.21.6 into **`HudElementRegistry`** — the API that survives to 26.2. A threat-indicator overlay must ship both code paths (`HudRenderCallback` for <=1.21.4, `HudElementRegistry` for >=1.21.6).
3. **Networking registration is the most stable area**: the `CustomPayload` + `PayloadTypeRegistry` + `ServerPlayNetworking`/`ClientPlayNetworking` model introduced in 1.20.5 still holds through 26.2. Only add-ons changed (configuration events in 1.21.2, `reconfigure` in 1.21.5, `registerLarge` packet splitting in 1.21.11).
4. **Threat (target) detection internals are stable; the damage call is not.** `MobEntity.goalSelector`/`targetSelector` and the `TargetGoal` family survive the whole range; the breaks are `Entity#damage(ServerWorld, ...)` in 1.21.2, the attribute `GENERIC_` prefix drop in 1.21.2, and the wholesale Yarn->Mojmap rename in 26.1 (`MobEntity` -> `Mob`, `CreeperEntity` -> `Creeper`, ...). Every mixin target string changes at 26.1.
5. **New mobs with non-standard "who is targeting me" semantics**: the **Creaking** (1.21.4) never uses classic target goals in the usual way (observation-gated movement, damage immunity, heart linkage); 1.21.11 adds hostile **mount archetypes** (camel husk, zombie nautilus, parched, zombie horseman) where the threat is a jockey riding a mount; 26.2's **sulfur cube** counts against the hostile cap but never targets anyone. Detection code must handle jockeys and the creaking specially.

## (a) Version table

All 15 game versions below are marked stable by Fabric's meta API and have a Fabric API build line on Modrinth (checked 2026-09-08). "FAPI latest" is the newest Fabric API version targeting that game version.

| Minecraft | Release | Latest Fabric API | Yarn mappings (latest) | Fabric Loader (blog-era latest) | Loom / Gradle / Java | Named drop |
|---|---|---|---|---|---|---|
| 1.21.1 | 2024-08-08 | `0.116.17+1.21.1` | `1.21.1+build.3` | 0.16.x | Loom 1.7 | Tricky Trials patch |
| 1.21.2 | 2024-10-22 | `0.106.1+1.21.2` | yes | 0.16.7 | Loom 1.8 | Bundles of Bravery |
| 1.21.3 | 2024-10-23 | `0.114.1+1.21.3` | yes | 0.16.7 | Loom 1.8 | (patch) |
| 1.21.4 | 2024-12-03 | `0.119.4+1.21.4` | `1.21.4+build.8` | 0.16.9 | Loom 1.9 | The Garden Awakens |
| 1.21.5 | 2025-03-25 | `0.128.2+1.21.5` | yes | 0.16.10 | Loom 1.10 / Gradle 8.12 | Spring to Life |
| 1.21.6 | 2025-06-17 | `0.128.2+1.21.6` | yes | 0.16.14 | Loom 1.10 | Chase the Skies |
| 1.21.7 | 2025-06-30 | `0.129.0+1.21.7` | yes | 0.16.14 | Loom 1.10 | (patch) |
| 1.21.8 | 2025-07-17 | `0.136.1+1.21.8` | `1.21.8+build.1` | 0.16.14 | Loom 1.10 | (patch) |
| 1.21.9 | 2025-09-30 | `0.134.1+1.21.9` | `1.21.9+build.1` | 0.17.2 (0.17.0 bundles MixinExtras 5.0) | Loom 1.11 | The Copper Age |
| 1.21.10 | 2025-10-07 | `0.138.4+1.21.10` | `1.21.10+build.3` | 0.17.2 | Loom 1.11 | (patch) |
| 1.21.11 | 2025-12-09 | `0.141.6+1.21.11` | `1.21.11+build.6` (**last Yarn ever**) | 0.18.1 | Loom 1.14 | Mounts of Mayhem |
| 26.1 | 2026-03-24 | `0.155.3+26.1.2` (line covers 26.1, 26.1.1) | **none** | 0.18.4 | Loom 1.15 / Gradle 9.4.0 / Java 25 (Gradle JVM), IntelliJ 2025.3+ for mixins | Tiny Takeover |
| 26.1.1 | patch | (same 26.1.x line) | none | 0.18.4 | Loom 1.15 | (hotfix) |
| 26.1.2 | patch | `0.155.3+26.1.2` | none | 0.18.4 | Loom 1.15 | (hotfix) |
| 26.2 | 2026-06-16 | `0.160.0+26.2` | **none** | 0.19.3 (0.19.0 adds enum extensions) | Loom 1.17 / Gradle 9.5.1 | Chaos Cubed |

Facts worth flagging:

- **Yarn stopped at 1.21.11.** Mojang removed obfuscation from Java Edition jars after Mounts of Mayhem; Fabric deprecated Yarn and retired Intermediary ("the game will use Mojang's names at runtime"), recommending all new mods use official Mojang mappings ([fabricmc.net, "Removing Obfuscation from Fabric", 2025-10-31](https://fabricmc.net/2025/10/31/obfuscation.html); confirmed against the meta API: zero Yarn builds exist for any 26.x version). 26.1's post is explicit: "Yarn is no longer officially supported by Fabric"; **every mod touching Minecraft code must be recompiled for 26.1**.
- **The `fabric` mod ID is gone in 26.1** — `fabric.mod.json` must depend on `fabric-api`.
- 26.1 build script changes: new `net.fabricmc.fabric-loom` plugin (no remapping); `modImplementation` -> `implementation`; `remapJar` -> `jar`.
- Fabric API backports continue on old lines (1.21.1 still gets `0.116.x` maintenance releases as of 2026-09), so 1.21.1 remains a viable LTS target.
- 26.3 snapshots (`26.3-snapshot-*`) are already being served Fabric API builds (`0.153.0+26.3` through `0.160.0+26.3`), so the pipeline continues unchanged.

## (b) HUD and rendering API deltas

Chronology of the changes a HUD overlay (threat indicators on an orbit) must absorb:

| Version | Change | Impact on this mod |
|---|---|---|
| 1.21.1 (baseline) | `HudRenderCallback.EVENT.register(...)` (fabric-rendering-v1); `DrawContext.drawTexture(Identifier, ..., textureWidth, textureHeight)` | Starting point |
| 1.21.2 | `drawTexture` loses `textureWidth`/`textureHeight`; variants take `Function<Identifier, RenderLayer>`; entity rendering refactored to render states (`EntityRenderer<S extends EntityRenderState>`, `updateRenderState`); `WorldRenderer` vertex helpers moved to `VertexRendering`; `CoreShaderRegistrationCallback` removed | Texture-drawing helpers need per-version overloads |
| 1.21.4 | fabric-rendering-v0 removed; item model definitions moved to JSON; server-side pick item events | Minor |
| 1.21.5 | **`HudRenderCallback` deprecated** in favor of new `HudLayerRegistrationCallback`/`HudLayerRegistry` (Fabric API 0.116.0); `drawTexture` takes `RenderPipeline` instead of `RenderLayer`; `SpecialBlockRendererRegistry` | First HUD API fork |
| 1.21.6 | **HUD API rewritten: `HudElementRegistry`** (`addLast`, `attachElementBefore(VanillaHudElements.CHAT, ...)`, `replaceElement`); Material API removed; many `RenderSystem` methods removed without replacement (use `RenderPipelines` + `RenderLayers`); vanilla rendering splits into extraction + render phases; `BlockRenderLayerMap` merged into fabric-rendering-v1 | Second HUD fork — this is the API to use for everything >= 1.21.6 |
| 1.21.9/1.21.10 | World Render Events removed (reintroduced for 1.21.10/1.21.11 with extraction/rendering split); draws submitted via `OrderedRenderCommandQueue`; `BlockEntityRenderer<T, BlockEntityRenderState>`; **`Entity#getWorld` renamed to `getEntityWorld`** (touches almost all mods); `DebugHudEntries` for F3 lines; keybinding categories need `KeyBinding.Category.create(Identifier)` | `getEntityWorld` rename is a mechanical but universal break |
| 26.1 | **`HudRenderCallback` removed**; `HudElementRegistry` survives; `ChunkSectionLayer` replaces `RenderType`/`RenderLayer` for terrain; `ColorProviderRegistry` -> `BlockColorRegistry`; `FluidModel` replaces `FluidRenderHandler`; last OpenGL-only release — raw GL calls must go through Blaze3D; Model Loading API and Indigo/renderer modules were not available at the initial 26.1 release | HUD path is clean if written against `HudElementRegistry` |
| 26.2 | Experimental Vulkan backend switchable (OpenGL to be removed once stable); GUI/HUD methods reorganized into `Gui` and `Hud` classes (`Minecraft.getInstance().gui.setScreen(...)`); raw-GL migration enforced | Avoid raw GL entirely; keep to `DrawContext`/Blaze3D |

Bottom line for the indicator renderer: target `HudElementRegistry.addLast`/`attachElementBefore` for >= 1.21.6 (through 26.2 unchanged), `HudRenderCallback` for 1.21.1-1.21.4, and expect a transitional `HudLayerRegistrationCallback` if 1.21.5 is targeted. Draw primitives through `DrawContext` only.

## (c) Networking / payload API deltas

The `CustomPayload` registration model (payload `CustomPayload.Id`, `PacketCodec`, `PayloadTypeRegistry.playC2S()/playS2C().register`, receivers via `ServerPlayNetworking.registerGlobalReceiver` / `ClientPlayNetworking.registerGlobalReceiver`) introduced in 1.20.5 **is unchanged across the entire range** — no version in 1.21.1..26.2 altered the registration or receiver signatures.

Changes that do exist around it:

| Version | Change |
|---|---|
| 1.21.2 | `ClientConfigurationConnectionEvents.READY` renamed to `COMPLETE`; new `START` event; `MinecraftClient`/`MinecraftServer` added to networking contexts |
| 1.21.5 | `ServerPlayNetworking.reconfigure(...)` added (play network reconfiguration); `RegistryAttribute.OPTIONAL` lets a registry be missing on the client without disconnect |
| 1.21.11 | Opt-in **packet splitter** for oversized payloads: `PayloadTypeRegistry.playS2C().registerLarge(ID, CODEC, DATA_SIZE)`; `RecipeSynchronization.synchronizeRecipeSerializer(...)` restores pre-1.21.2-style recipe sync (servers are then expected to have a recipe viewer) |
| 26.2 | Client command confirmation: `.requires(FabricClientCommandSource::attended)` |

For this mod: if "who's after me" stays detection-only (server-side mixin reads mob targets, client renders indicators over vanilla networking of nothing), the whole range needs **zero networking code**. Any future server->client threat broadcast is a single `CustomPayload` registered identically on all versions, with `registerLarge` needed only on 1.21.11+ for large payloads.

## (d) Mob AI / targeting internals relevant to server-side detection mixins

Stable across the whole range:

- `MobEntity` (Yarn) keeps public `goalSelector` and `targetSelector` fields (`GoalSelector` with priorities), the `target` field with `getTarget()`/`setTarget(LivingEntity)`, and the `TargetGoal` family (`ActiveTargetGoal`, `HurtByTargetGoal`, `TrackTargetGoal`) — verified present in 1.21.1, still present under mojmap names in 26.x (`Mob`, `NearestAttackableTargetGoal`, `HurtByTargetGoal`). Detection can therefore always be expressed as: hook `Mob#setTarget`/`getTarget` (mojmap) or read `targetSelector` state, plus a distance check against the detection radius.

Changed in the range:

| Version | Change | Detection-mixin consequence |
|---|---|---|
| 1.21.2 | **`Entity#damage` now requires a `ServerWorld`** (client-side variant split out as `clientDamage`); attributes renamed: `GENERIC_` prefix dropped (`GENERIC_ATTACK_KNOCKBACK` -> `ATTACK_KNOCKBACK`, id `generic.x` -> `x`); `MobEntity#convertTo` needs `EntityConversionType` + `EntityConversionContext`; `EntityType.create` requires a `SpawnReason`; `EntityType.Builder#build` requires a `RegistryKey` | Any follow-up damage or attribute read in detection code needs `world instanceof ServerWorld serverWorld` guards; attribute IDs change |
| 1.21.5 | `NbtCompound` getters return `Optional` (fallback overloads added) | Custom serialized goal/detection state needs rewrite |
| 1.21.6 | Custom `TrackedData` handlers must register via `FabricTrackedDataRegistry.registerHandler(ID, HANDLER)` | Matters if detection state is synced with tracked data |
| 1.21.9 | `Entity#getWorld` -> `Entity#getEntityWorld` | Mechanical rename in all entity-touching mixins |
| 26.1 | **Mappings flip to Mojang names** (unobfuscated jar, no intermediary). Yarn `MobEntity` -> `Mob`, `HostileEntity` -> `Monster`, `CreeperEntity` -> `Creeper`, `ActiveTargetGoal` -> `NearestAttackableTargetGoal`; Fabric API classes renamed to match (`ItemGroupEvents` -> `CreativeModeTabEvents`); IntelliJ migration map published in Fabric Docs; Loom `migrateMappings` or the Ravel IDE plugin do the port, "but you still have to review the results ... especially if migrating Mixins" | **Every mixin reference string, shadow field, and accessor must be re-derived.** Budget this as the single biggest 26.1 porting cost. Tools: Linkie (linkie.shedaniel.dev) and mcsrc.dev for name lookup |

26.1/26.2 also added small targeting-adjacent behavior changes: baby polar bears no longer attack foxes (26.1); the golden dandelion can age-lock mobs gated by the `#cannot_be_age_locked` entity tag (26.1); vexes are considered owned by their evoker for `execute on owner` (26.2). None change where `Mob#setTarget` is called.

## (e) Hostile archetypes introduced in the range with unusual targeting

| Mob | Version | Targeting behavior | Detection consequence |
|---|---|---|---|
| **Creaking** | 1.21.4 | Spawned by an activated creaking heart; heart-linked creakings ignore all damage (void and `/kill` excepted). Two states: *unalerted* (wanders) and *alerted* (a Survival/Adventure player within ~12 blocks can see it). **Moves, attacks, and can only be stopped when no player observes it** (observation = player view direction within ~60 degrees, no block occlusion; FOV setting and third person irrelevant; a carved pumpkin on the player lets it move). Never leaves a 32-block radius of its heart; dies when the heart is broken, when pushed 32+ blocks away, or after 5 s of standing inside a player. 1 HP, drops nothing | A creaking that is "after you" may never call `setTarget` the way normal mobs do; its alert state is observation-driven. Special-case it: treat *alerted creaking within detection radius* as a threat, and expect damage/kill mechanics not to apply |
| **Zombie nautilus** | 1.21.11 (Mounts of Mayhem) | Undead nautilus, spawns ridden by a drowned with a trident (jockey); hostile while untamed | Threat is a **nested jockey**: detection must consider `getControllingPassenger`/vehicle stacks, not just free-swimming mobs |
| **Camel husk** | 1.21.11 | Undead camel, spawns with two riders (husk with iron spear + parched jockey); hostile until ridden/tamed | Same jockey nesting; two entities ride one mount |
| **Parched** | 1.21.11 | Desert skeleton variant; shoots Arrows of Weakness, immune to Weakness, does not burn in sunlight, fires slower (bogged-like) | Classic ranged target selection — works with standard `NearestAttackableTargetGoal` detection, but is a new skeleton subtype to classify |
| **Zombie horseman** | 1.21.11 | Zombie horse now spawns naturally at night in plains/savanna ridden by a spear-wielding zombie; hostile while untamed | Jockey nesting again; spear gives zombies a **charge attack with longer reach** than melee |
| **Sulfur cube** | 26.2 (Chaos Cubed) | Counts toward the hostile mob cap but **never targets or attacks players**; contact damage only in "hot" (magma) archetype; splits in two when killed | Must be excluded from threat classification despite hostile-cap membership — check target, not category |

Also in range but conventional: the happy ghast (1.21.6) and copper golem (1.21.9) are passive and never target players. 26.1 (Tiny Takeover) added no new mobs.

## Sources

- Fabric meta API: `https://meta.fabricmc.net/v2/versions/game`, `/versions/yarn/<version>`, `/versions/loader` (game-version stability, Yarn build existence, Loader versions; queried 2026-09-08)
- Modrinth API: `https://api.modrinth.com/v2/project/fabric-api/version` (Fabric API build-to-game-version mapping; queried 2026-09-08)
- Fabric blog: [1.21.2 & 1.21.3](https://fabricmc.net/2024/10/14/1212.html), [1.21.4](https://fabricmc.net/2024/12/02/1214.html), [1.21.5](https://fabricmc.net/2025/03/24/1215.html), [1.21.6/7/8](https://fabricmc.net/2025/06/15/1216.html), [1.21.9 & 1.21.10](https://fabricmc.net/2025/09/23/1219.html), [Removing Obfuscation from Fabric (2025-10-31)](https://fabricmc.net/2025/10/31/obfuscation.html), [1.21.11](https://fabricmc.net/2025/12/05/12111.html), [26.1](https://fabricmc.net/2026/03/14/261.html), [26.2](https://fabricmc.net/2026/06/15/262.html)
- Fabric docs: [Migrating mappings (Yarn -> Mojang)](https://docs.fabricmc.net/develop/porting/mappings/), [Rendering in the HUD (HudElementRegistry)](https://docs.fabricmc.net/develop/rendering/hud)
- Fabric API deprecated list for 0.119.2+1.21.5: `https://maven.fabricmc.net/docs/fabric-api-0.119.2+1.21.5/deprecated-list.html` (HudRenderCallback deprecation)
- DrawContext javadocs: [yarn 1.21](https://maven.fabricmc.net/docs/yarn-1.21+build.2/net/minecraft/client/gui/DrawContext.html), [yarn 1.21.4](https://maven.fabricmc.net/docs/yarn-1.21.4+build.1/net/minecraft/client/gui/DrawContext.html), [yarn 1.21.6](https://maven.fabricmc.net/docs/yarn-1.21.6+build.1/net/minecraft/client/gui/DrawContext.html)
- minecraft.wiki: [Mounts of Mayhem](https://minecraft.wiki/w/Mounts_of_Mayhem), [Java Edition 26.1](https://minecraft.wiki/w/Java_Edition_26.1), [Java Edition 26.2](https://minecraft.wiki/w/Java_Edition_26.2), [Creaking](https://minecraft.wiki/w/Creaking), [Java Edition 1.21](https://minecraft.wiki/w/Java_Edition_1.21)
- minecraft.net: [Minecraft new version numbering system](https://www.minecraft.net/en-us/article/minecraft-new-version-numbering-system)
