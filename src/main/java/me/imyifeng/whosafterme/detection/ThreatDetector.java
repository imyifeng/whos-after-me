package me.imyifeng.whosafterme.detection;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import me.imyifeng.whosafterme.WhosAfterMe;
import me.imyifeng.whosafterme.config.WhosAfterMeConfig;
import me.imyifeng.whosafterme.net.HelloPacket;
import me.imyifeng.whosafterme.net.ModdedPlayers;
import me.imyifeng.whosafterme.net.SyncProtocol;
import me.imyifeng.whosafterme.net.ThreatSyncPacket;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
//? if fapi_modern_id {
import net.fabricmc.fabric.api.entity.event.v1.ServerEntityLevelChangeEvents;
//?} else {
/*import net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents;*/
//?}
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The server detection engine's tick wiring (spec v1 §3/§4, ADR-0001, ADR-0002). Every
 * {@code pollInterval} server ticks, each marked player's surroundings are collected
 * with a radius-bounded box query and the uniform structural read decides Threats; the
 * pure {@link ThreatTracker} turns each player's observations into a per-player
 * {@link ThreatDiff}, which becomes exactly one {@code threat_sync} packet - or none
 * when nothing changed. No per-mob whitelist exists (ADR-0001): every {@code Mob} in
 * the radius is subject to the same read, so goal mobs, brain mobs, provoked neutrals,
 * and future mobs are covered by construction.
 *
 * <p>Thread confinement (ADR-0002): the engine's state is created on
 * {@code SERVER_STARTING}, touched only inside {@code END_SERVER_TICK}, the hello
 * receiver, and the world-change hook - all of which Fabric runs on the server thread
 * - and dropped on {@code SERVER_STOPPED}, so a fresh server instance never inherits a
 * dead one's state.
 */
public final class ThreatDetector {
    /**
     * The active server's engine, or null while no server runs. Written only by the
     * lifecycle hooks and read by the tick hook - all on the server thread.
     */
    private static ThreatDetector active;

    private final ThreatTracker tracker = new ThreatTracker();
    private final ModdedPlayers modded = new ModdedPlayers();
    private long pollCounter;
    private boolean announcedFirstPoll;

    private ThreatDetector() {
    }

    /** Registers the lifecycle, tick, and protocol hooks. Called once from the common initializer. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> active = new ThreatDetector());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> active = null);
        ServerTickEvents.END_SERVER_TICK.register(ThreatDetector::onEndServerTick);
        // The hello handshake (spec v1 §4): play receivers run on the server thread,
        // matching the engine's tick confinement.
        ServerPlayNetworking.registerGlobalReceiver(HelloPacket.ID, (payload, context) -> {
            ThreatDetector detector = active;
            if (detector != null) {
                detector.handshake(context.player(), payload.protocolVersion());
            }
        });
        // Dimension change (spec v1 §4 lifecycle): drop the player's state so the next
        // poll re-baselines with a RESET. Disconnect is handled by the tick's retainAll.
        // Fabric API renamed the event ("world" to "level") at 26.1.
        //? if fapi_modern_id {
        ServerEntityLevelChangeEvents.AFTER_PLAYER_CHANGE_LEVEL.register((player, origin, destination) -> {
            ThreatDetector detector = active;
            if (detector != null) {
                detector.dropPlayerState(player.getUUID());
            }
        });
        //?} else {
        /*ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register((player, origin, destination) -> {
            ThreatDetector detector = active;
            if (detector != null) {
                detector.dropPlayerState(player.getUUID());
            }
        });*/
        //?}
    }

    private static void onEndServerTick(MinecraftServer server) {
        ThreatDetector detector = active;
        if (detector != null) {
            detector.tick(server);
        }
    }

    /**
     * One full production poll for one observer, exposed for the server gametests
     * (ADR-0004, ticket #30): a gametest server has no connected client to receive
     * {@code threat_sync}, so the tests read the poll's diff directly. This is the exact
     * END_SERVER_TICK path - the radius-bounded box query, the uniform read, the
     * Detection-radius predicate, and the tracker with its clear grace - without the
     * marked-player gate, which the tests' mock observers never pass (no hello). Like the
     * tick itself, it must run on the server thread, where the gametests execute.
     */
    public static ThreatDiff observe(ServerPlayer observer) {
        ThreatDetector detector = active;
        if (detector == null) {
            throw new IllegalStateException("ThreatDetector is not active: no server is running");
        }
        return detector.pollPlayer(observer, radiusBlocks());
    }

    private void tick(MinecraftServer server) {
        // Read live from config each poll (values apply without a restart); clamp to
        // survive a hand-edited JSON file (the screen enforces the documented ranges).
        int pollInterval = Math.max(1, WhosAfterMeConfig.pollInterval);
        if (Math.floorMod(pollCounter++, pollInterval) != 0) {
            return;
        }
        double radiusBlocks = radiusBlocks();

        if (!announcedFirstPoll) {
            announcedFirstPoll = true;
            WhosAfterMe.LOGGER.info("Threat poll active: every {} ticks, radius {} blocks", pollInterval, radiusBlocks);
        }

        var players = server.getPlayerList().getPlayers();
        for (ServerPlayer player : players) {
            UUID playerId = player.getUUID();
            // Unmarked (vanilla) players are never detected for and never receive
            // threat_sync (spec v1 §4).
            if (!modded.isMarked(playerId)) {
                continue;
            }
            ThreatDiff diff = pollPlayer(player, radiusBlocks);
            ThreatSyncPacket packet = ThreatSyncPacket.fromDiff(diff, modded.consumeFullSync(playerId));
            if (packet != null) {
                ServerPlayNetworking.send(player, packet);
            }
        }

        Set<UUID> online = new HashSet<>(players.size());
        for (ServerPlayer player : players) {
            online.add(player.getUUID());
        }
        tracker.retainAll(online);
        modded.retainAll(online);
    }

    /**
     * One hello handshake (spec v1 §4, ADR-0002). A version match marks the player and
     * immediately sends a full-state RESET, baselined through the tracker so the next
     * poll diffs from the state just sent. A mismatch is logged and leaves the player
     * unmarked - they simply never receive threat packets.
     */
    private void handshake(ServerPlayer player, int protocolVersion) {
        UUID playerId = player.getUUID();
        if (!modded.handshake(playerId, protocolVersion)) {
            WhosAfterMe.LOGGER.info(
                    "{} sent hello with protocol version {} (supported: {}); leaving them unmarked",
                    player.getName().getString(), protocolVersion, SyncProtocol.PROTOCOL_VERSION);
            return;
        }
        tracker.forget(playerId);
        // Fresh tracker state: the diff reports the full current set, and the forced
        // full sync pins the mode to RESET so the client replaces whatever it had.
        ThreatDiff diff = pollPlayer(player, radiusBlocks());
        ServerPlayNetworking.send(player, ThreatSyncPacket.fromDiff(diff, true));
    }

    /**
     * Drops one player's synced state. Dimension change (spec v1 §4): the client
     * clears its own indicator set and the server forgets its last-sent set, so the
     * next poll re-baselines with a full-state RESET.
     */
    private void dropPlayerState(UUID playerId) {
        tracker.forget(playerId);
        if (modded.isMarked(playerId)) {
            modded.queueFullSync(playerId);
        }
    }

    /**
     * One player's poll: collect alive mobs in the radius-bounded box, apply the uniform
     * read plus the Detection-radius predicate, and feed the observations to the player's
     * bookkeeping.
     *
     * @return what changed relative to what was last reported for the player.
     */
    private ThreatDiff pollPlayer(ServerPlayer player, double radiusBlocks) {
        // `level()` is the entity world accessor on every anchor in the Mojang names
        // this codebase is written against (the spec v1 §6 rename fork is Yarn-only).
        Level level = player.level();
        AABB queryBox = player.getBoundingBox().inflate(radiusBlocks);

        Set<Integer> targetingIds = new HashSet<>();
        Set<Integer> inRadiusIds = new HashSet<>();
        // The box is a superset filter around the player; ThreatRules does the exact 3D
        // check, so out-of-radius mobs never qualify. Mob::isAlive keeps dead mobs from
        // holding their (never-cleared) target through the death animation.
        for (Mob mob : level.getEntitiesOfClass(Mob.class, queryBox, Mob::isAlive)) {
            if (!ThreatRules.withinRadius(
                    mob.getX() - player.getX(),
                    mob.getY() - player.getY(),
                    mob.getZ() - player.getZ(),
                    radiusBlocks)) {
                continue;
            }
            inRadiusIds.add(mob.getId());
            if (targets(mob, player)) {
                targetingIds.add(mob.getId());
            }
        }

        return tracker.poll(player.getUUID(), targetingIds, inRadiusIds, System.currentTimeMillis());
    }

    private static double radiusBlocks() {
        return Math.max(1, WhosAfterMeConfig.detectionRadius);
    }

    /**
     * The uniform structural read (ADR-0001): {@code getTarget()} first - the one read
     * point that covers goal-system mobs and brain mobs whose override reads the brain -
     * falling back to the brain's {@code ATTACK_TARGET} memory for mobs that store their
     * target without overriding {@code getTarget()}. Comparison is by identity against
     * the observing player; players are not mobs, so PvP never qualifies.
     *
     * <p>The fallback is guarded by {@code hasMemoryValue}: brains only look up registered
     * memories, and {@code Brain.getMemory} throws for an unregistered one (goal mobs
     * like zombies and skeletons never register {@code ATTACK_TARGET}), so an unguarded
     * read would break the whole poll on every goal-driven mob.
     */
    private static boolean targets(Mob mob, ServerPlayer player) {
        LivingEntity target = mob.getTarget();
        if (target == null && mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)) {
            target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        }
        return target == player;
    }
}
