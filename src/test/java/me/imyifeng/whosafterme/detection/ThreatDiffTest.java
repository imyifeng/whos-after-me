package me.imyifeng.whosafterme.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Threat-set diff tests (spec v1 §3/§4, ADR-0002): each poll produces the difference
 * between what was last reported for a player and the player's current effective Threat
 * set. The sync protocol (ticket #27) maps the diff onto ADD/REMOVE/RESET packets; the
 * diff itself is pure logic (ADR-0004).
 */
class ThreatDiffTest {
    @Test
    void firstDiffReportsEverythingAsAdded() {
        ThreatDiff diff = ThreatDiff.of(Set.of(), Set.of(3, 5, 9));
        assertEquals(Set.of(3, 5, 9), diff.added());
        assertEquals(Set.of(), diff.removed());
        assertEquals(Set.of(3, 5, 9), diff.current());
        assertFalse(diff.isEmpty());
    }

    @Test
    void missingEntriesAreRemovals() {
        ThreatDiff diff = ThreatDiff.of(Set.of(3, 5, 9), Set.of(5));
        assertEquals(Set.of(), diff.added());
        assertEquals(Set.of(3, 9), diff.removed());
        assertFalse(diff.isEmpty());
    }

    @Test
    void mixedChangeProducesBothDirections() {
        ThreatDiff diff = ThreatDiff.of(Set.of(3, 5), Set.of(5, 9));
        assertEquals(Set.of(9), diff.added());
        assertEquals(Set.of(3), diff.removed());
    }

    @Test
    void unchangedSetProducesAnEmptyDiff() {
        ThreatDiff diff = ThreatDiff.of(Set.of(3, 5), Set.of(5, 3));
        assertEquals(Set.of(), diff.added());
        assertEquals(Set.of(), diff.removed());
        assertTrue(diff.isEmpty());
    }

    @Test
    void emptyToEmptyIsEmpty() {
        assertTrue(ThreatDiff.of(Set.of(), Set.of()).isEmpty());
    }

    @Test
    void inputsAreNotMutated() {
        Set<Integer> lastSent = new HashSet<>(Set.of(3, 5));
        Set<Integer> current = new HashSet<>(Set.of(5, 9));
        ThreatDiff.of(lastSent, current);
        assertEquals(Set.of(3, 5), lastSent);
        assertEquals(Set.of(5, 9), current);
    }

    @Test
    void emptyLastSentAndEmptyCurrentBothHandled() {
        ThreatDiff diff = ThreatDiff.of(Set.of(3), Set.of());
        assertEquals(Set.of(3), diff.removed());
        assertEquals(Set.of(), diff.current());
    }
}
