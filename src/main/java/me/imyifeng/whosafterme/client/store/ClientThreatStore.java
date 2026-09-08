package me.imyifeng.whosafterme.client.store;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.IntPredicate;
import java.util.function.IntToDoubleFunction;

import me.imyifeng.whosafterme.net.ThreatSyncMode;

/**
 * The client threat store (spec v1 §5.4): holds exactly what the sync packets say,
 * keyed by entity network id. RESET replaces the whole set, ADD merges entries,
 * REMOVE drops them - the store runs no detection logic and no hysteresis of its own
 * (ADR-0002); the server always syncs the full Threat set.
 *
 * <p>On top of the synced set the store owns the client-side display selection
 * (spec v1 §5.2): at most {@link #INDICATOR_CAP} simultaneous Threat indicators,
 * chosen with the incumbent-sticky + nearest-first rule. Incumbents keep their slots
 * while they remain Threats (no flicker, however many Threats churn beyond the cap);
 * vacated slots refill nearest-first from the remaining Threats.
 *
 * <p>Pure logic over plain {@code int} network ids - no Minecraft classes (ADR-0004),
 * so the whole state machine runs as plain JUnit. Entity resolution and the
 * staleness self-heal's world lookup live in {@link ClientThreats}. Instances are
 * not thread-safe: state is confined to the client thread (protocol payloads are
 * hopped onto it by the receiver, ADR-0002).
 */
public final class ClientThreatStore {

    /**
     * The maximum number of simultaneous Threat indicators (spec v1 §5.2). Constant
     * in v1 - not configurable - and display-only: the synced set is never capped.
     */
    public static final int INDICATOR_CAP = 8;

    /** The full synced Threat set: entity network id → threat-kind byte (spec v1 §4). */
    private final Map<Integer, Integer> threats = new HashMap<>();

    /**
     * The display selection in slot order: incumbents first (their established
     * order), then nearest-first refills. Always a subset of {@link #threats}.
     */
    private final List<Integer> slots = new ArrayList<>();

    /**
     * Applies one threat_sync packet's entries (spec v1 §4): RESET replaces the
     * whole set - a baseline, so Threats that survive it keep their selection
     * slots; ADD merges keyed by network id (a re-sent id updates its kind byte);
     * REMOVE drops the listed ids, vacating their slots. Removing an unknown id is
     * a no-op. The map is copied; the caller keeps ownership.
     */
    public void apply(ThreatSyncMode mode, Map<Integer, Integer> entries) {
        switch (mode) {
            case RESET -> {
                threats.clear();
                threats.putAll(entries);
                dropVacatedSlots();
            }
            case ADD -> threats.putAll(entries);
            case REMOVE -> {
                for (int id : entries.keySet()) {
                    threats.remove(id);
                }
                dropVacatedSlots();
            }
        }
    }

    /**
     * Forgets everything - synced set and selection state. The lifecycle clear for
     * dimension change, respawn, and world disconnect (spec v1 §5.4): no stale
     * incumbency survives, so repopulation starts from an empty selection.
     */
    public void clear() {
        threats.clear();
        slots.clear();
    }

    /**
     * The staleness self-heal (spec v1 §5.4): drops every entry whose network id the
     * predicate rejects - in the live client, ids that no longer resolve in the
     * client world - and vacates their selection slots.
     *
     * @param entityResolves whether an entity network id still resolves in the
     *     client world.
     * @return whether anything was dropped.
     */
    public boolean prune(IntPredicate entityResolves) {
        boolean dropped = threats.keySet().removeIf(id -> !entityResolves.test(id));
        if (dropped) {
            dropVacatedSlots();
        }
        return dropped;
    }

    /**
     * Applies the indicator cap selection (spec v1 §5.2) and returns the selected
     * network ids in slot order. Call once per frame with a distance value per
     * current Threat id (squared or absolute - only the ordering matters; ties fall
     * back to ascending id so the fill is deterministic).
     *
     * <p>Incumbents keep their slots while they remain Threats - nearer newcomers
     * never displace them; only vacated or never-filled slots refill, nearest-first
     * from the Threats not currently selected. With the whole set at or below the
     * cap, everything is selected.
     */
    public List<Integer> select(IntToDoubleFunction distance) {
        List<Integer> selection = new ArrayList<>();
        for (int id : slots) {
            if (threats.containsKey(id)) {
                selection.add(id);
            }
        }
        if (selection.size() < INDICATOR_CAP && threats.size() > selection.size()) {
            List<Integer> candidates = new ArrayList<>();
            for (int id : threats.keySet()) {
                if (!selection.contains(id)) {
                    candidates.add(id);
                }
            }
            candidates.sort(Comparator.comparingDouble((Integer id) -> distance.applyAsDouble(id))
                    .thenComparingInt(id -> id));
            for (int id : candidates) {
                if (selection.size() >= INDICATOR_CAP) {
                    break;
                }
                selection.add(id);
            }
        }
        slots.clear();
        slots.addAll(selection);
        return List.copyOf(selection);
    }

    /** The number of Threats in the synced set (uncapped - the cap is display-only). */
    public int size() {
        return threats.size();
    }

    /** Whether the network id is in the synced set. */
    public boolean contains(int entityId) {
        return threats.containsKey(entityId);
    }

    /**
     * The threat-kind byte of a synced entry (reserved for post-v1 escalation levels,
     * spec v1 §4). The id must be contained - see {@link #contains(int)}.
     */
    public int kindOf(int entityId) {
        return threats.get(entityId);
    }

    /** The full synced Threat set as an unmodifiable id → kind-byte view. */
    public Map<Integer, Integer> entries() {
        return Collections.unmodifiableMap(threats);
    }

    /** Slots of entries that are no longer Threats vacate; survivors keep their order. */
    private void dropVacatedSlots() {
        slots.removeIf(id -> !threats.containsKey(id));
    }
}
