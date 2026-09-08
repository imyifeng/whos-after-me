package me.imyifeng.whosafterme.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Proximity visual tests for {@link OrbitGeometry} (spec v1 §5.2): proximity clamping,
 * arc-length scaling (6 deg at the Detection-radius edge to ~3x point-blank), the
 * 3-block edge fade, the opacity formula, and the far-to-near render ordering.
 */
class ProximityVisualsTest {
    private static final double EPS = 1e-9;

    @Test
    void proximityRunsFromPointBlankToOneDownToZeroAtTheRadius() {
        assertEquals(1, OrbitGeometry.proximity(0, 32), EPS);
        assertEquals(0.75, OrbitGeometry.proximity(8, 32), EPS);
        assertEquals(0, OrbitGeometry.proximity(32, 32), EPS);
        assertEquals(0, OrbitGeometry.proximity(40, 32), EPS, "beyond the radius must clamp, not go negative");
    }

    @Test
    void arcLengthScalesFromSixToEighteenDegrees() {
        assertEquals(6, OrbitGeometry.arcLengthDeg(0), EPS);
        assertEquals(12, OrbitGeometry.arcLengthDeg(0.5), EPS);
        assertEquals(18, OrbitGeometry.arcLengthDeg(1), EPS, "point-blank arc must be ~3x the base");
    }

    @Test
    void halfArcIsHalfTheArcLengthInRadians() {
        assertEquals(Math.toRadians(3), OrbitGeometry.halfArcRad(0), EPS);
        assertEquals(Math.toRadians(6), OrbitGeometry.halfArcRad(0.5), EPS);
        assertEquals(Math.toRadians(9), OrbitGeometry.halfArcRad(1), EPS);
    }

    @Test
    void edgeFadeRampsAcrossTheOutermostThreeBlocks() {
        assertEquals(0, OrbitGeometry.edgeFade(32, 32), EPS, "at the radius edge the indicator is invisible");
        assertEquals(0.5, OrbitGeometry.edgeFade(30.5, 32), EPS);
        assertEquals(1, OrbitGeometry.edgeFade(29, 32), EPS, "the fade completes 3 blocks inside the radius");
        assertEquals(1, OrbitGeometry.edgeFade(10, 32), EPS);
    }

    @Test
    void opacityBlendsProximityEdgeFadeAndConfig() {
        assertEquals(0.3, OrbitGeometry.opacity(0, 1, 1), EPS);
        assertEquals(1.0, OrbitGeometry.opacity(1, 1, 1), EPS);
        assertEquals(0.65, OrbitGeometry.opacity(0.5, 1, 1), EPS);
        assertEquals(0, OrbitGeometry.opacity(1, 0, 1), EPS, "the edge fade must hide the indicator at the radius");
        assertEquals(0.15, OrbitGeometry.opacity(0, 1, 0.5), EPS);
    }

    @Test
    void orderingIsFarToNear() {
        assertTrue(OrbitGeometry.compareFarToNear(30, 10) < 0, "the farther threat must sort first");
        assertTrue(OrbitGeometry.compareFarToNear(10, 30) > 0);
        assertEquals(0, OrbitGeometry.compareFarToNear(12, 12));
    }
}
