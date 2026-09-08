package me.imyifeng.whosafterme.detection;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * 1.5 s clear-grace state machine tests (spec v1 §3, ADR-0002): a mob whose target clears
 * holds its Threat status for 1500 ms before removal; re-acquiring inside the grace is
 * silent; leaving the Detection radius (or death/despawn/dimension change) removes
 * immediately - the grace applies only to a target clear while the mob stays alive and
 * in radius.
 *
 * <p>The machine is fed each poll's observations with explicit server-provided
 * milliseconds, so every scenario is deterministic. In the poll inputs,
 * {@code targeting} = mobs passing the uniform read, {@code inRadius} = all alive mobs
 * within the Detection radius, targeting or not ({@code targeting} is always a subset).
 */
class ClearGraceTest {
    private final ClearGrace grace = new ClearGrace();

    private java.util.Set<Integer> poll(java.util.Set<Integer> targeting, java.util.Set<Integer> inRadius, long nowMs) {
        return grace.update(targeting, inRadius, nowMs);
    }

    @Test
    void targetingMobIsAThreat() {
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(7), java.util.Set.of(7), 0));
    }

    @Test
    void clearedTargetHeldForTheFullGraceWindow() {
        poll(java.util.Set.of(7), java.util.Set.of(7), 0);
        // Target clears at t=1000; the threat is held until 1000 + 1500.
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 1000));
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 2499));
        // At exactly 2500 ms after the clear the held status lapses.
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(7), 2500));
    }

    @Test
    void reacquireInsideGraceIsSilent() {
        poll(java.util.Set.of(7), java.util.Set.of(7), 0);
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 1000));
        // Re-acquire: the threat never left the effective set.
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(7), java.util.Set.of(7), 1400));
        // The old grace window is disarmed: clearing again now arms a fresh window from
        // the new clear time (1500 + 1500 = 3000), not from the first clear (1000 + 1500).
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 1500));
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 2999));
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(7), 3000));
    }

    @Test
    void radiusExitRemovesImmediately() {
        poll(java.util.Set.of(7), java.util.Set.of(7), 0);
        // The mob leaves the radius: absent from the in-radius set, so the very next
        // poll drops it - no 1500 ms hold.
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(), 500));
    }

    @Test
    void deathOrDespawnRemovesImmediately() {
        poll(java.util.Set.of(7), java.util.Set.of(7), 0);
        // Dead/despawned mobs leave the radius query; same immediate path as radius exit
        // (spec v1 §3: death/despawn/dimension change are not graced).
        poll(java.util.Set.of(7), java.util.Set.of(7), 400);
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(), 800));
    }

    @Test
    void mobLeavingRadiusDuringGraceDropsImmediately() {
        poll(java.util.Set.of(7), java.util.Set.of(7), 0);
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 1000));
        // Grace was armed, but the mob exits the radius before it lapses.
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(), 1200));
    }

    @Test
    void graceIsNotExtendedWhileTheMobStaysInRadius() {
        poll(java.util.Set.of(7), java.util.Set.of(7), 0);
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 1000));
        // Repeated polls of the same cleared mob must not re-arm the deadline.
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 1100));
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 1500));
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 2000));
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(7), 2500));
    }

    @Test
    void expiredGraceDoesNotReArmWhileTheMobStaysCleared() {
        poll(java.util.Set.of(7), java.util.Set.of(7), 0);
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(), java.util.Set.of(7), 1000));
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(7), 2500));
        // Still present, still not targeting: it must not become a threat again by itself.
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(7), 4000));
    }

    @Test
    void threatReturnsAfterGraceExpirationAndNewAcquisition() {
        poll(java.util.Set.of(7), java.util.Set.of(7), 0);
        poll(java.util.Set.of(), java.util.Set.of(7), 1000);
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(7), 2500));
        // Fresh acquisition after the grace window: a threat again, from now on.
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(7), java.util.Set.of(7), 5000));
    }

    @Test
    void mobSeenButNeverTargetingNeverBecomesAThreat() {
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(9), 0));
        assertEquals(java.util.Set.of(), poll(java.util.Set.of(), java.util.Set.of(9), 5000));
    }

    @Test
    void multipleMobsAreIndependent() {
        poll(java.util.Set.of(7, 8), java.util.Set.of(7, 8, 9), 0);
        // 7 clears (graced), 8 keeps targeting, 9 was never a threat.
        assertEquals(java.util.Set.of(7, 8), poll(java.util.Set.of(8), java.util.Set.of(7, 8, 9), 1000));
        // 7's grace lapses; 8 still targets.
        assertEquals(java.util.Set.of(8), poll(java.util.Set.of(8), java.util.Set.of(7, 8, 9), 3000));
        // 8 exits the radius; 7 re-acquires from scratch.
        assertEquals(java.util.Set.of(7), poll(java.util.Set.of(7), java.util.Set.of(7, 9), 3200));
    }

    @Test
    void graceIsExactly1500Ms() {
        assertEquals(1500, ClearGrace.GRACE_MS);
    }
}
