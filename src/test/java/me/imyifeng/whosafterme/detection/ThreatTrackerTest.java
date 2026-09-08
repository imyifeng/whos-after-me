package me.imyifeng.whosafterme.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Per-player detection bookkeeping tests (spec v1 §3, ADR-0002): the tracker keeps each
 * player's grace state and last-reported set so every poll yields a pure per-player diff.
 * This is the seam the sync protocol (ticket #27) consumes: a non-empty diff is exactly
 * one packet's worth of change, and an empty diff sends nothing.
 */
class ThreatTrackerTest {
    private static final UUID PLAYER = UUID.nameUUIDFromBytes("player".getBytes());
    private static final UUID OTHER = UUID.nameUUIDFromBytes("other".getBytes());

    @Test
    void firstPollReportsEverythingAsAdded() {
        ThreatTracker tracker = new ThreatTracker();
        ThreatDiff diff = tracker.poll(PLAYER, Set.of(7, 8), Set.of(7, 8), 0);
        assertEquals(Set.of(7, 8), diff.added());
        assertTrue(diff.added().containsAll(diff.current()));
    }

    @Test
    void unchangedThreatsProduceNoPacket() {
        ThreatTracker tracker = new ThreatTracker();
        tracker.poll(PLAYER, Set.of(7), Set.of(7), 0);
        assertTrue(tracker.poll(PLAYER, Set.of(7), Set.of(7), 100).isEmpty());
    }

    @Test
    void clearedTargetWithinGraceEmitsNoPacket() {
        ThreatTracker tracker = new ThreatTracker();
        tracker.poll(PLAYER, Set.of(7), Set.of(7), 0);
        // Target clears: the grace holds the threat, so the diff stays empty...
        assertTrue(tracker.poll(PLAYER, Set.of(), Set.of(7), 1000).isEmpty());
        // ...and the re-acquire inside the grace emits no packet either.
        assertTrue(tracker.poll(PLAYER, Set.of(7), Set.of(7), 1400).isEmpty());
    }

    @Test
    void graceExpirationEmitsARemoval() {
        ThreatTracker tracker = new ThreatTracker();
        tracker.poll(PLAYER, Set.of(7), Set.of(7), 0);
        assertTrue(tracker.poll(PLAYER, Set.of(), Set.of(7), 1000).isEmpty());
        ThreatDiff diff = tracker.poll(PLAYER, Set.of(), Set.of(7), 2500);
        assertEquals(Set.of(), diff.added());
        assertEquals(Set.of(7), diff.removed());
    }

    @Test
    void radiusExitEmitsAnImmediateRemoval() {
        ThreatTracker tracker = new ThreatTracker();
        tracker.poll(PLAYER, Set.of(7), Set.of(7), 0);
        ThreatDiff diff = tracker.poll(PLAYER, Set.of(), Set.of(), 400);
        assertEquals(Set.of(7), diff.removed());
    }

    @Test
    void playersAreTrackedIndependently() {
        ThreatTracker tracker = new ThreatTracker();
        tracker.poll(PLAYER, Set.of(7), Set.of(7), 0);
        tracker.poll(OTHER, Set.of(), Set.of(), 0);
        // A threat of PLAYER's is not a threat of OTHER's: OTHER's next poll with the same
        // world observations still reports the mob as added for THEM.
        ThreatDiff diff = tracker.poll(OTHER, Set.of(7), Set.of(7), 100);
        assertEquals(Set.of(7), diff.added());
    }

    @Test
    void forgetRestoresTheFullSetOnTheNextPoll() {
        ThreatTracker tracker = new ThreatTracker();
        tracker.poll(PLAYER, Set.of(7), Set.of(7), 0);
        tracker.forget(PLAYER);
        assertFalse(tracker.isTracked(PLAYER));
        // Dimension change / relog (ticket #27 calls this): the next poll re-reports
        // everything - the full state a RESET carries.
        ThreatDiff diff = tracker.poll(PLAYER, Set.of(7), Set.of(7), 5000);
        assertEquals(Set.of(7), diff.added());
        assertEquals(Set.of(), diff.removed());
    }

    @Test
    void retainAllDropsDisconnectedPlayers() {
        ThreatTracker tracker = new ThreatTracker();
        tracker.poll(PLAYER, Set.of(7), Set.of(7), 0);
        tracker.poll(OTHER, Set.of(9), Set.of(9), 0);
        tracker.retainAll(Set.of(PLAYER));
        assertFalse(tracker.isTracked(OTHER));
        assertTrue(tracker.isTracked(PLAYER));
        // The disconnected player's bookkeeping is gone: a rejoin polls from scratch.
        ThreatDiff rejoined = tracker.poll(OTHER, Set.of(9), Set.of(9), 100);
        assertEquals(Set.of(9), rejoined.added());
    }

    @Test
    void emptyDiffOnEmptySetIsReported() {
        ThreatTracker tracker = new ThreatTracker();
        assertTrue(tracker.poll(PLAYER, Set.of(), Set.of(), 0).isEmpty());
        assertTrue(tracker.poll(PLAYER, Set.of(), Set.of(), 100).isEmpty());
    }
}
