package me.imyifeng.whosafterme.detection;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import me.imyifeng.whosafterme.WhosAfterMe;
import me.imyifeng.whosafterme.config.WhosAfterMeConfig;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * The server detection engine's tick wiring (spec v1 §3, ADR-0001, ADR-0002). Every
 * {@code pollInterval} server ticks, each player's surroundings are collected with a
 * radius-bounded box query and the uniform structural read decides Threats; the pure
 * {@link ThreatTracker} turns each player's observations into a per-player
 * {@link ThreatDiff}. No per-mob whitelist exists (ADR-0001): every {@code Mob} in the
 * radius is subject to the same read, so goal mobs, brain mobs, provoked neutrals, and
 * future mobs are covered by construction.
 *
 * <p>Thread confinement (ADR-0002): the engine's state is created on
 * {@code SERVER_STARTING}, touched only inside {@code END_SERVER_TICK}, and dropped on
 * {@code SERVER_STOPPED} - all on the server thread. Ticket #27 (sync protocol) replaces
 * the currently unused diff with the per-player {@code threat_sync} sender.
 */
public final class ThreatDetector {
    /**
     * The active server's engine, or null while no server runs. Written only by the
     * lifecycle hooks and read by the tick hook - all on the server thread, so a fresh
     * server instance never inherits a dead one's state.
     */
    private static ThreatDetector active;

    private final ThreatTracker tracker = new ThreatTracker();
    private long pollCounter;
    private boolean announcedFirstPoll;

    private ThreatDetector() {
    }

    /** Registers the lifecycle and tick hooks. Called once from the common initializer. */
    public static void register() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> active = new ThreatDetector());
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> active = null);
        ServerTickEvents.END_SERVER_TICK.register(ThreatDetector::onEndServerTick);
    }

    private static void onEndServerTick(MinecraftServer server) {
        ThreatDetector detector = active;
        if (detector != null) {
            detector.tick(server);
        }
    }

    private void tick(MinecraftServer server) {
        // Read live from config each poll (values apply without a restart); clamp to
        // survive a hand-edited JSON file (the screen enforces the documented ranges).
        int pollInterval = Math.max(1, WhosAfterMeConfig.pollInterval);
        if (Math.floorMod(pollCounter++, pollInterval) != 0) {
            return;
        }
        double radiusBlocks = Math.max(1, WhosAfterMeConfig.detectionRadius);

        if (!announcedFirstPoll) {
            announcedFirstPoll = true;
            WhosAfterMe.LOGGER.info("Threat poll active: every {} ticks, radius {} blocks", pollInterval, radiusBlocks);
        }

        var players = server.getPlayerList().getPlayers();
        // Ticket #27 narrows this loop to modded (handshake-marked) players; until the
        // protocol lands, detection runs for everyone and simply reports nowhere.
        for (ServerPlayer player : players) {
            pollPlayer(player, radiusBlocks);
        }

        Set<UUID> online = new HashSet<>(players.size());
        for (ServerPlayer player : players) {
            online.add(player.getUUID());
        }
        tracker.retainAll(online);
    }

    /**
     * One player's poll: collect alive mobs in the radius-bounded box, apply the uniform
     * read plus the Detection-radius predicate, and feed the observations to the player's
     * bookkeeping.
     */
    private void pollPlayer(ServerPlayer player, double radiusBlocks) {
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

        // Seam for ticket #27: the returned per-player diff becomes one threat_sync
        // packet (or none when empty). Until the protocol lands there is nothing to
        // send it to, so the diff is deliberately not consumed here yet.
        tracker.poll(player.getUUID(), targetingIds, inRadiusIds, System.currentTimeMillis());
    }

    /**
     * The uniform structural read (ADR-0001): {@code getTarget()} first - the one read
     * point that covers goal-system mobs and brain mobs whose override reads the brain -
     * falling back to the brain's {@code ATTACK_TARGET} memory for mobs that store their
     * target without overriding {@code getTarget()}. Comparison is by identity against
     * the observing player; players are not mobs, so PvP never qualifies.
     */
    private static boolean targets(Mob mob, ServerPlayer player) {
        LivingEntity target = mob.getTarget();
        if (target == null) {
            target = mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null);
        }
        return target == player;
    }
}
