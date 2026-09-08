package me.imyifeng.whosafterme.detection;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The per-player Threat-set diff (spec v1 §3/§4, ADR-0002): the difference between the
 * Threat set last reported for a player and the player's current effective Threat set.
 * Pure data over plain {@code int} entity network ids - no Minecraft classes (ADR-0004).
 *
 * <p>This record is the seam the sync protocol (ticket #27) consumes: an empty diff
 * sends nothing; a diff with only additions maps to ADD, only removals to REMOVE, and a
 * mix to a RESET carrying {@link #current()} (the mode-selection rule is the protocol
 * ticket's, not the diff's business).
 */
public record ThreatDiff(Set<Integer> added, Set<Integer> removed, Set<Integer> current) {

    /**
     * Diffs the set last sent to a player against the player's current effective Threat
     * set. Neither input is mutated and the returned sets are unmodifiable.
     */
    public static ThreatDiff of(Set<Integer> lastSent, Set<Integer> current) {
        Set<Integer> added = new HashSet<>(current);
        added.removeAll(lastSent);
        Set<Integer> removed = new HashSet<>(lastSent);
        removed.removeAll(current);
        return new ThreatDiff(
                Collections.unmodifiableSet(added),
                Collections.unmodifiableSet(removed),
                Collections.unmodifiableSet(new HashSet<>(current)));
    }

    /** True when nothing changed for this player in this poll: no packet (spec v1 §4). */
    public boolean isEmpty() {
        return added.isEmpty() && removed.isEmpty();
    }
}
