package me.imyifeng.whosafterme.detection;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The 1.5 s clear-grace state machine for one player (spec v1 §3, ADR-0002). A mob whose
 * target clears holds its Threat status for {@link #GRACE_MS} before removal;
 * re-acquiring inside the grace is silent (no diff is ever produced for the round trip);
 * leaving the Detection radius removes immediately - and so do death, despawn, and
 * dimension change, which show up the same way in the poll inputs. The grace applies
 * only to a target clear while the mob stays alive and within the radius.
 *
 * <p>Pure per-player bookkeeping with no Minecraft classes (ADR-0004): each poll feeds in
 * that poll's observations - {@code targetingIds}, the mobs passing the uniform read, and
 * {@code inRadiusIds}, every alive mob within the Detection radius, targeting or not -
 * and the machine returns the player's effective Threat set (current targets plus mobs
 * still inside their grace window). All times are server-provided milliseconds so the
 * state machine stays deterministic and unit-testable.
 */
public final class ClearGrace {
    /**
     * How long a cleared target holds its Threat status (spec v1 §3). Constant in v1 -
     * deliberately not configurable (spec v1 §7).
     */
    public static final long GRACE_MS = 1500;

    /** Entity ids of mobs in a grace window, mapped to the deadline at which it lapses. */
    private final Map<Integer, Long> graceDeadlines = new HashMap<>();
    /** The previous poll's effective set: the candidates for a new grace window. */
    private Set<Integer> lastEffective = Set.of();

    /**
     * Applies one poll's observations and returns the player's effective Threat set as
     * entity ids.
     *
     * <p>@param targetingIds ids of mobs whose attack target is the observing player and
     * which are within the Detection radius (a subset of {@code inRadiusIds})
     * <p>@param inRadiusIds ids of every alive mob within the Detection radius this poll,
     * targeting the player or not
     * <p>@param nowMs server-provided wall-clock milliseconds
     */
    public Set<Integer> update(Set<Integer> targetingIds, Set<Integer> inRadiusIds, long nowMs) {
        // Snapshot of who was in grace when this poll started, so the arming pass below
        // cannot mistake an entry it is about to expire for a fresh target clear.
        Set<Integer> gracedAtPollStart = Set.copyOf(graceDeadlines.keySet());

        // Absent from the radius query (radius exit, death, despawn, unload): immediate
        // removal, never graced.
        graceDeadlines.keySet().removeIf(id -> !inRadiusIds.contains(id));
        // Re-acquire inside the grace: disarm the deadline - the threat never left the
        // effective set, so nothing is emitted for the round trip.
        for (Integer id : targetingIds) {
            graceDeadlines.remove(id);
        }
        // A previous threat that stays in radius but just lost its target opens its grace
        // window. Armed once per clear: polls during the window must not extend it.
        for (Integer id : lastEffective) {
            if (!targetingIds.contains(id) && inRadiusIds.contains(id) && !gracedAtPollStart.contains(id)) {
                graceDeadlines.put(id, nowMs + GRACE_MS);
            }
        }
        // Grace expiry: the held status lapses exactly GRACE_MS after the clear.
        graceDeadlines.entrySet().removeIf(entry -> entry.getValue() <= nowMs);

        Set<Integer> effective = new HashSet<>(targetingIds);
        effective.addAll(graceDeadlines.keySet());
        lastEffective = Set.copyOf(effective);
        return effective;
    }
}
