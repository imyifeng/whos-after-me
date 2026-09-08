package me.imyifeng.whosafterme.net;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * The server's handshake bookkeeping (spec v1 §4, ADR-0002): which players a
 * version-matching hello has marked as modded, and which marked players are owed a
 * full-state RESET on their next poll (world change). Pure state - no Minecraft
 * types - so the marking rules run as plain JUnit (ADR-0004). The detector touches it
 * only from the server tick and the hello receiver, both on the server thread
 * (ADR-0002 confinement).
 */
public final class ModdedPlayers {
    private final Set<UUID> marked = new HashSet<>();
    private final Set<UUID> fullSyncPending = new HashSet<>();

    /**
     * Applies one hello handshake. A version match marks the player and clears any
     * stale full-sync flag (the handshake path sends its own immediate RESET); a
     * mismatch leaves the player unmarked - the safe default that also covers vanilla
     * players, who never send hello at all.
     *
     * @return whether the handshake marked the player.
     */
    public boolean handshake(UUID playerId, int protocolVersion) {
        if (!SyncProtocol.accepts(protocolVersion)) {
            return false;
        }
        marked.add(playerId);
        fullSyncPending.remove(playerId);
        return true;
    }

    /** Whether the player is marked modded; unmarked players never receive threat_sync. */
    public boolean isMarked(UUID playerId) {
        return marked.contains(playerId);
    }

    /**
     * The player's world changed (dimension change): what their client currently
     * displays is unknowable, so the next poll must re-baseline with a full-state
     * RESET (spec v1 §4 lifecycle).
     */
    public void queueFullSync(UUID playerId) {
        fullSyncPending.add(playerId);
    }

    /**
     * Returns (and clears) whether the player's next poll must be a RESET. One-shot by
     * design: after the RESET the normal diff rule takes over again.
     */
    public boolean consumeFullSync(UUID playerId) {
        return fullSyncPending.remove(playerId);
    }

    /** Drops one player's handshake state entirely (relog, forced unmark). */
    public void forget(UUID playerId) {
        marked.remove(playerId);
        fullSyncPending.remove(playerId);
    }

    /** Drops every player not in {@code onlineIds}: a disconnected player is unmarked. */
    public void retainAll(Set<UUID> onlineIds) {
        marked.retainAll(onlineIds);
        fullSyncPending.retainAll(onlineIds);
    }
}
