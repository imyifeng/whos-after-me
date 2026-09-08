package me.imyifeng.whosafterme.detection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Per-player server-side detection bookkeeping (spec v1 §3, ADR-0002): every player's
 * clear-grace state and the Threat set last reported for them, so each poll yields a pure
 * per-player {@link ThreatDiff}. This is the seam ticket #27 consumes: it turns each
 * returned diff into ADD/REMOVE/RESET packets for that player.
 *
 * <p>State lifecycle mirrors the protocol rules: {@link #forget(UUID)} drops one player's
 * bookkeeping (dimension change, relog - the next poll re-reports the full set) and
 * {@link #retainAll(Set)} drops disconnected players (nothing to sync). All of this state
 * is touched only from the server tick (ADR-0002 confinement); the class holds no
 * Minecraft types, so the bookkeeping runs as plain JUnit (ADR-0004).
 */
public final class ThreatTracker {
    private final Map<UUID, PlayerState> players = new HashMap<>();

    /** One player's detection state: the grace machine plus what was last reported. */
    private static final class PlayerState {
        private final ClearGrace grace = new ClearGrace();
        private Set<Integer> lastSent = Set.of();
    }

    /**
     * Applies one poll's observations for a player and returns what changed relative to
     * what was last reported for them. The input sets follow the {@link ClearGrace}
     * contract: {@code targetingIds} (mobs passing the uniform read within the Detection
     * radius) is a subset of {@code inRadiusIds} (every alive mob within the radius).
     */
    public ThreatDiff poll(UUID playerId, Set<Integer> targetingIds, Set<Integer> inRadiusIds, long nowMs) {
        PlayerState state = players.computeIfAbsent(playerId, id -> new PlayerState());
        Set<Integer> effective = state.grace.update(targetingIds, inRadiusIds, nowMs);
        ThreatDiff diff = ThreatDiff.of(state.lastSent, effective);
        state.lastSent = new HashSet<>(effective);
        return diff;
    }

    /**
     * Drops one player's bookkeeping. Dimension change or relog: the next poll re-reports
     * the player's full Threat set - exactly the payload a RESET carries (ADR-0002).
     */
    public void forget(UUID playerId) {
        players.remove(playerId);
    }

    /** Drops every player not in {@code onlineIds}: a disconnected player has nothing to sync. */
    public void retainAll(Set<UUID> onlineIds) {
        players.keySet().retainAll(onlineIds);
    }

    /** Whether any bookkeeping exists for the player (pruning and test assertions). */
    public boolean isTracked(UUID playerId) {
        return players.containsKey(playerId);
    }
}
