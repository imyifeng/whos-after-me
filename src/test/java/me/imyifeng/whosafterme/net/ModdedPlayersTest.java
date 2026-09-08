package me.imyifeng.whosafterme.net;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Handshake marking tests (spec v1 §4, ADR-0002): only a version-matching hello marks
 * a player as modded, unmarked (vanilla) players never receive threat_sync, and the
 * mark plus the pending full-sync flag follow the spec lifecycle (disconnect drop,
 * dimension-change re-baseline).
 */
class ModdedPlayersTest {
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player".getBytes());
    private static final UUID OTHER = UUID.nameUUIDFromBytes("other".getBytes());

    @Test
    void versionMatchMarksThePlayer() {
        ModdedPlayers players = new ModdedPlayers();
        assertTrue(players.handshake(PLAYER, SyncProtocol.PROTOCOL_VERSION));
        assertTrue(players.isMarked(PLAYER));
    }

    @Test
    void versionMismatchStaysUnmarked() {
        ModdedPlayers players = new ModdedPlayers();
        for (int version : new int[] {0, SyncProtocol.PROTOCOL_VERSION + 1, 255}) {
            assertFalse(players.handshake(PLAYER, version), "version " + version);
        }
        // Safe default: an unmarked player is indistinguishable from vanilla.
        assertFalse(players.isMarked(PLAYER));
    }

    @Test
    void aRejectedHandshakeCanBeFollowedByAMatchingOne() {
        ModdedPlayers players = new ModdedPlayers();
        assertFalse(players.handshake(PLAYER, SyncProtocol.PROTOCOL_VERSION + 1));
        assertTrue(players.handshake(PLAYER, SyncProtocol.PROTOCOL_VERSION));
        assertTrue(players.isMarked(PLAYER));
    }

    @Test
    void vanillaPlayersAreNeverMarked() {
        ModdedPlayers players = new ModdedPlayers();
        players.handshake(PLAYER, SyncProtocol.PROTOCOL_VERSION);
        // OTHER never sent hello: the poll loop must skip them entirely.
        assertFalse(players.isMarked(OTHER));
    }

    @Test
    void queuedFullSyncIsConsumedExactlyOnce() {
        ModdedPlayers players = new ModdedPlayers();
        players.handshake(PLAYER, SyncProtocol.PROTOCOL_VERSION);
        players.queueFullSync(PLAYER);
        assertTrue(players.consumeFullSync(PLAYER));
        // The second poll after the dimension change diffs normally again.
        assertFalse(players.consumeFullSync(PLAYER));
    }

    @Test
    void handshakeClearsAStaleFullSyncFlag() {
        // The handshake path sends its own immediate RESET and baselines the tracker,
        // so a pending flag from an earlier world change must not force a second one.
        ModdedPlayers players = new ModdedPlayers();
        players.queueFullSync(PLAYER);
        assertTrue(players.handshake(PLAYER, SyncProtocol.PROTOCOL_VERSION));
        assertFalse(players.consumeFullSync(PLAYER));
    }

    @Test
    void disconnectDropsTheMark() {
        ModdedPlayers players = new ModdedPlayers();
        players.handshake(PLAYER, SyncProtocol.PROTOCOL_VERSION);
        players.retainAll(Set.of(PLAYER));
        assertTrue(players.isMarked(PLAYER));
        players.retainAll(Set.of(OTHER));
        // A disconnected player is unmarked; a rejoin re-handshakes from scratch.
        assertFalse(players.isMarked(PLAYER));
    }

    @Test
    void forgetDropsMarkAndPendingFlag() {
        ModdedPlayers players = new ModdedPlayers();
        players.handshake(PLAYER, SyncProtocol.PROTOCOL_VERSION);
        players.queueFullSync(PLAYER);
        players.forget(PLAYER);
        assertFalse(players.isMarked(PLAYER));
        assertFalse(players.consumeFullSync(PLAYER));
    }
}
