package me.imyifeng.whosafterme.net;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import me.imyifeng.whosafterme.detection.ThreatDiff;
import org.junit.jupiter.api.Test;

/**
 * Threat-sync packet mode-selection tests (spec v1 §4, ADR-0002): one poll batch
 * becomes at most one packet. Only additions map to ADD, only removals to REMOVE,
 * a mix to a RESET carrying the full current set, and a forced full sync (handshake,
 * dimension change) always produces a RESET baseline.
 */
class ThreatSyncPacketTest {

    private static ThreatDiff diffOf(Set<Integer> added, Set<Integer> removed, Set<Integer> current) {
        return new ThreatDiff(added, removed, current);
    }

    private static Set<Integer> ids(ThreatSyncPacket packet) {
        return packet.entries().stream().map(ThreatSyncPacket.Entry::entityId).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void emptyDiffSendsNothing() {
        // No change since the last poll: no packet (spec v1 §4).
        assertNull(ThreatSyncPacket.fromDiff(diffOf(Set.of(), Set.of(), Set.of(7)), false));
    }

    @Test
    void onlyAdditionsSendAdd() {
        ThreatDiff diff = ThreatDiff.of(Set.of(7), Set.of(7, 8));
        ThreatSyncPacket packet = ThreatSyncPacket.fromDiff(diff, false);
        assertEquals(ThreatSyncMode.ADD, packet.mode());
        assertEquals(Set.of(8), ids(packet));
    }

    @Test
    void onlyRemovalsSendRemove() {
        ThreatDiff diff = ThreatDiff.of(Set.of(7, 8), Set.of(8));
        ThreatSyncPacket packet = ThreatSyncPacket.fromDiff(diff, false);
        assertEquals(ThreatSyncMode.REMOVE, packet.mode());
        assertEquals(Set.of(7), ids(packet));
    }

    @Test
    void mixedDiffSendsResetWithFullCurrentSet() {
        // Additions and removals in one poll batch: RESET replaces the client's whole
        // set, keeping "all changes from one poll in one packet" true with a single
        // mode byte (spec v1 §4).
        ThreatDiff diff = ThreatDiff.of(Set.of(7, 8), Set.of(8, 9, 10));
        ThreatSyncPacket packet = ThreatSyncPacket.fromDiff(diff, false);
        assertEquals(ThreatSyncMode.RESET, packet.mode());
        assertEquals(Set.of(8, 9, 10), ids(packet));
    }

    @Test
    void fullSyncTurnsPureAdditionsIntoReset() {
        // Handshake and dimension change: there is no last-sent state on the client to
        // add onto, so the first report must replace (spec v1 §4 lifecycle).
        ThreatDiff diff = ThreatDiff.of(Set.of(), Set.of(7, 8));
        ThreatSyncPacket packet = ThreatSyncPacket.fromDiff(diff, true);
        assertEquals(ThreatSyncMode.RESET, packet.mode());
        assertEquals(Set.of(7, 8), ids(packet));
    }

    @Test
    void fullSyncEmitsResetEvenWhenTheStateIsEmpty() {
        // The baseline must be definitive even with no threats: a RESET with zero
        // entries establishes "the server has nothing for you".
        ThreatSyncPacket packet = ThreatSyncPacket.fromDiff(diffOf(Set.of(), Set.of(), Set.of()), true);
        assertEquals(ThreatSyncMode.RESET, packet.mode());
        assertTrue(packet.entries().isEmpty());
    }

    @Test
    void entriesCarryTheTargetingKind() {
        // v1 sends only TARGETING (0); the byte is reserved for post-v1 escalation
        // levels (spec v1 §4).
        ThreatSyncPacket packet = ThreatSyncPacket.fromDiff(ThreatDiff.of(Set.of(), Set.of(3)), true);
        assertEquals(List.of(new ThreatSyncPacket.Entry(3, SyncProtocol.TARGETING)), packet.entries());
    }

    @Test
    void resetEntriesAreOrderedByIdForDeterministicWireOutput() {
        ThreatDiff diff = ThreatDiff.of(Set.of(), Set.of(9, 2, 5));
        ThreatSyncPacket packet = ThreatSyncPacket.fromDiff(diff, true);
        assertEquals(List.of(2, 5, 9), packet.entries().stream().map(ThreatSyncPacket.Entry::entityId).toList());
    }
}
