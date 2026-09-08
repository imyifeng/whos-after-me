package me.imyifeng.whosafterme.detection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Detection-radius predicate tests (spec v1 §3, ADR-0001): a mob qualifies only when its
 * 3D Euclidean distance from the observing player is within the Detection radius. The
 * radius-bounded box query is only a superset filter - this predicate is the exact
 * structural rule, and out-of-radius mobs never qualify.
 */
class ThreatRulesTest {
    private static final double RADIUS = 32.0;

    @Test
    void mobAtThePlayersPositionQualifies() {
        assertTrue(ThreatRules.withinRadius(0, 0, 0, RADIUS));
    }

    @Test
    void mobInsideRadiusQualifies() {
        assertTrue(ThreatRules.withinRadius(30, 0, 0, RADIUS));
        assertTrue(ThreatRules.withinRadius(-16, 0, -16, RADIUS));
    }

    @Test
    void negativeOffsetsAreSquared() {
        assertTrue(ThreatRules.withinRadius(-RADIUS, 0, 0, RADIUS));
        assertFalse(ThreatRules.withinRadius(-RADIUS - 0.5, 0, 0, RADIUS));
    }

    @Test
    void mobAtTheExactRadiusQualifies() {
        // 3-4-5 triangle scaled to the radius: exactly on the boundary counts as within.
        assertTrue(ThreatRules.withinRadius(19.2, 25.6, 0, RADIUS));
        assertTrue(ThreatRules.withinRadius(RADIUS, 0, 0, RADIUS));
    }

    @Test
    void mobJustOutsideRadiusDoesNotQualify() {
        assertFalse(ThreatRules.withinRadius(RADIUS + 0.01, 0, 0, RADIUS));
        // Diagonal (20, 20, 20) is ~34.6 blocks away: each component is inside the
        // radius, but the Euclidean distance is not. The box query alone must not qualify it.
        assertFalse(ThreatRules.withinRadius(20, 20, 20, RADIUS));
    }

    @Test
    void heightAloneCountsTowardTheRadius() {
        assertTrue(ThreatRules.withinRadius(0, 16, 0, RADIUS));
        assertFalse(ThreatRules.withinRadius(0, 40, 0, RADIUS));
    }

    @Test
    void smallRadiiKeepTheSameBoundarySemantics() {
        assertTrue(ThreatRules.withinRadius(3, 4, 0, 5));
        assertFalse(ThreatRules.withinRadius(3, 4, 0.1, 5));
    }
}
