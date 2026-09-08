package me.imyifeng.whosafterme.client.store;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntToDoubleFunction;

import me.imyifeng.whosafterme.net.ThreatSyncMode;
import org.junit.jupiter.api.Test;

/**
 * Client threat store tests (spec v1 §5.2/§5.4, ADR-0004): the store holds exactly what
 * the packets say, keyed by entity network id; display selection caps at 8 indicators
 * with the incumbent-sticky + nearest-first rule - incumbents keep their slots while
 * they remain Threats (no flicker), vacated slots refill nearest-first. Pure logic over
 * plain {@code int} network ids - no Minecraft classes (ADR-0004).
 */
class ClientThreatStoreTest {

    /**
     * Distance for network id {@code i} is {@code distanceById[i]} - smaller is
     * nearer. Ids beyond the array length read as 1000.
     */
    private static IntToDoubleFunction byId(Map<Integer, Double> distances) {
        return id -> distances.getOrDefault(id, 1000.0);
    }

    private static Map<Integer, Double> distances(double... distanceById) {
        Map<Integer, Double> map = new HashMap<>();
        for (int id = 0; id < distanceById.length; id++) {
            map.put(id, distanceById[id]);
        }
        return map;
    }

    private static Map<Integer, Integer> entries(int... ids) {
        Map<Integer, Integer> map = new HashMap<>();
        for (int id : ids) {
            map.put(id, 0);
        }
        return map;
    }

    @Test
    void resetReplacesTheWholeSet() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.ADD, entries(1, 2));
        store.apply(ThreatSyncMode.RESET, entries(3));
        assertFalse(store.contains(1));
        assertFalse(store.contains(2));
        assertTrue(store.contains(3));
        assertEquals(1, store.size());
    }

    @Test
    void resetWithNoEntriesEstablishesAnEmptySet() {
        // The handshake and dimension-change baseline is definitive even with no
        // threats (spec v1 §4): a RESET with zero entries means "nothing is a Threat".
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.ADD, entries(7));
        store.apply(ThreatSyncMode.RESET, Map.of());
        assertEquals(0, store.size());
        assertTrue(store.select(byId(distances())).isEmpty());
    }

    @Test
    void addMergesIntoTheSet() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(7));
        store.apply(ThreatSyncMode.ADD, entries(8, 9));
        assertTrue(store.contains(7));
        assertTrue(store.contains(8));
        assertTrue(store.contains(9));
        assertEquals(3, store.size());
    }

    @Test
    void removeDropsOnlyTheListedIds() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(7, 8));
        store.apply(ThreatSyncMode.REMOVE, entries(7));
        assertFalse(store.contains(7));
        assertTrue(store.contains(8));
    }

    @Test
    void removeOfAnUnknownIdIsANoOp() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.REMOVE, entries(999));
        assertEquals(0, store.size());
    }

    @Test
    void reAddingAnIdUpdatesItsKind() {
        // One entry per network id: a re-sent id overwrites the kind byte rather than
        // duplicating the entry (the store is keyed by network id, spec v1 §5.4).
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.ADD, Map.of(7, 0));
        store.apply(ThreatSyncMode.ADD, Map.of(7, 1));
        assertEquals(1, store.size());
        assertEquals(1, store.kindOf(7));
    }

    @Test
    void selectionBelowTheCapShowsEverything() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(1, 2, 3, 4, 5));
        assertEquals(Set.of(1, 2, 3, 4, 5), new HashSet<>(store.select(byId(distances()))));
    }

    @Test
    void selectionCapsAtEightAndFillsNearestFirst() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        // Distance grows with the id: the eight nearest (0-7) fill the cap, 8 and 9
        // stay uncapped-out (spec v1 §5.2: at most 8 simultaneous indicators).
        List<Integer> selected = store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
        assertEquals(8, selected.size());
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7), new HashSet<>(selected));
        assertFalse(selected.contains(8));
        assertFalse(selected.contains(9));
    }

    @Test
    void incumbentsKeepTheirSlotsWhileTheyRemainThreats() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));

        // A new Threat appears closer than most incumbents: no displacement - the cap
        // selection is incumbent-sticky, so the indicator set never flickers.
        store.apply(ThreatSyncMode.ADD, entries(10));
        List<Integer> selected = store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, -1)));
        assertEquals(Set.of(0, 1, 2, 3, 4, 5, 6, 7), new HashSet<>(selected));
    }

    @Test
    void vacatedSlotsRefillNearestFirst() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));

        // Incumbent 3 leaves: its slot refills with the nearest remaining Threat -
        // 8, and 9 would only follow if another slot opened.
        store.apply(ThreatSyncMode.REMOVE, entries(3));
        List<Integer> selected = store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
        assertEquals(8, selected.size());
        assertEquals(Set.of(0, 1, 2, 4, 5, 6, 7, 8), new HashSet<>(selected));
    }

    @Test
    void refillsKeepTheSurvivorsSlotOrder() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));

        store.apply(ThreatSyncMode.REMOVE, entries(3));
        List<Integer> selected = store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
        // Survivors keep their established order; the refill is appended.
        assertEquals(List.of(0, 1, 2, 4, 5, 6, 7, 8), selected);
    }

    @Test
    void selectionStaysStableUnderChurnAboveTheCap() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        List<Integer> baseline = store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));

        // Farther threats come and go beyond the cap; unselected Threats churn while
        // every incumbent remains - the selection must not move.
        Map<Integer, Double> far = distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9);
        for (int churnId = 10; churnId < 20; churnId++) {
            store.apply(ThreatSyncMode.ADD, entries(churnId));
            assertEquals(baseline, store.select(byId(far)));
            store.apply(ThreatSyncMode.REMOVE, entries(churnId));
            assertEquals(baseline, store.select(byId(far)));
        }
    }

    @Test
    void survivingIncumbentsKeepTheirSlotsThroughAReset() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));

        // A mixed poll batch RESETs with the full current set: incumbents 0-4 remain
        // Threats and keep their slots; 5-7 are gone; new ids 20-22 arrive.
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 20, 21, 22));
        List<Integer> selected = store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
        assertEquals(Set.of(0, 1, 2, 3, 4, 20, 21, 22), new HashSet<>(selected));
    }

    @Test
    void pruneDropsEntriesWhoseEntityIdNoLongerResolves() {
        // Staleness self-heal (spec v1 §5.4): entries whose network id no longer
        // resolves in the client world are dropped, selection included.
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(1, 2, 3));
        assertTrue(store.prune(id -> id != 2));
        assertFalse(store.contains(2));
        assertTrue(store.contains(1));
        assertTrue(store.contains(3));
        assertEquals(Set.of(1, 3), new HashSet<>(store.select(byId(distances()))));
    }

    @Test
    void pruneOfEverythingResolvableIsANoOp() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(1, 2, 3));
        assertFalse(store.prune(id -> true));
        assertEquals(3, store.size());
    }

    @Test
    void prunedIncumbentSlotRefillsNearestFirst() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));

        // Incumbent 0's entity is gone from the client world: the self-heal drops it
        // and the freed slot refills with the nearest uncapped Threat (8).
        store.prune(id -> id != 0);
        List<Integer> selected = store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));
        assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8), new HashSet<>(selected));
    }

    @Test
    void clearEmptiesTheStoreAndForgetsTheSelection() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9)));

        // Dimension change, respawn, and disconnect (spec v1 §5.4).
        store.clear();
        assertEquals(0, store.size());
        assertTrue(store.select(byId(distances())).isEmpty());

        // No stale incumbency survives a clear: after repopulating, the previously
        // uncapped-out far Threat is selected again.
        store.apply(ThreatSyncMode.ADD, entries(9));
        assertEquals(List.of(9), store.select(byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9))));
    }

    @Test
    void equalDistancesRefillLowestIdsFirst() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(9, 2, 5));
        // All distances equal: the nearest-first fill falls back to ascending network
        // id, so the selection is deterministic.
        assertEquals(List.of(2, 5, 9), store.select(byId(distances())));
    }

    @Test
    void kindsSurviveSelectionAndPrune() {
        // The kind byte travels with the entry (reserved for post-v1 escalation,
        // spec v1 §4); selection and self-heal never touch it.
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, Map.of(1, 0, 2, 3));
        store.prune(id -> id != 2);
        store.select(byId(distances()));
        assertEquals(0, store.kindOf(1));
    }

    @Test
    void selectIsIdempotentWithoutChanges() {
        ClientThreatStore store = new ClientThreatStore();
        store.apply(ThreatSyncMode.RESET, entries(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        IntToDoubleFunction far = byId(distances(0, 1, 2, 3, 4, 5, 6, 7, 8, 9));
        List<Integer> first = store.select(far);
        assertEquals(first, store.select(far));
        assertEquals(first, store.select(far));
    }
}
