package me.imyifeng.whosafterme.client.hud;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.tan;
import static java.lang.Math.toRadians;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Aiming-mode angle tests for {@link OrbitGeometry} (spec v1 §5.3), including the two
 * behavior contract cases validated in the prototype (decision #7):
 *
 * <ul>
 *   <li>looking up slides an ahead Threat to the orbit bottom (Screen-relative);</li>
 *   <li>a 60 deg-bearing Threat at 90 deg FOV points right at the screen edge.</li>
 * </ul>
 */
class OrbitGeometryTest {
    private static final double EPS = 1e-9;
    private static final double WIDTH = 1920;
    private static final double HEIGHT = 1080;

    // ---- Absolute (compass) mode ----

    @Test
    void absoluteModePlacesADeadAheadThreatAtTheTop() {
        assertEquals(0, OrbitGeometry.absoluteOrbitAngle(0), EPS);
    }

    @Test
    void absoluteModePlacesABehindThreatAtTheBottom() {
        assertEquals(Math.PI, OrbitGeometry.absoluteOrbitAngle(Math.PI), EPS);
    }

    @Test
    void absoluteModeMirrorsTheRelativeBearing() {
        assertEquals(Math.PI / 2, OrbitGeometry.absoluteOrbitAngle(Math.PI / 2), EPS);
        assertEquals(-Math.PI / 2, OrbitGeometry.absoluteOrbitAngle(-Math.PI / 2), EPS);
    }

    @Test
    void relativeBearingSubtractsTheFacingAngle() {
        assertEquals(toRadians(60), OrbitGeometry.relativeBearing(toRadians(90), toRadians(30)), EPS);
        assertEquals(toRadians(-60), OrbitGeometry.relativeBearing(toRadians(30), toRadians(90)), EPS);
    }

    @Test
    void relativeBearingWrapsAroundTheCompass() {
        assertEquals(toRadians(20), OrbitGeometry.relativeBearing(toRadians(10), toRadians(350)), EPS);
        assertEquals(toRadians(-20), OrbitGeometry.relativeBearing(toRadians(350), toRadians(10)), EPS);
        assertEquals(toRadians(20), OrbitGeometry.relativeBearing(toRadians(-170), toRadians(170)), EPS);
    }

    // ---- Screen-relative mode: center anchor and edge blend ----

    @Test
    void nearTheViewCenterTheAngleStaysAnchoredToTheCompassBearing() {
        // A 2 deg bearing at 70 deg FOV sits inside the 0.1 blend start, so the orbit
        // angle must be exactly the compass bearing.
        double bearing = toRadians(2);
        CameraProjection p = CameraProjection.project(
                0, 0, toRadians(70), WIDTH, HEIGHT, sin(bearing), 0, -cos(bearing));
        assertEquals(bearing, OrbitGeometry.screenRelativeOrbitAngle(bearing, p), 1e-12);
    }

    @Test
    void theEdgeBlendInterpolatesBetweenCompassAndScreenDirection() {
        double bearing = toRadians(20);
        double fov = toRadians(70);
        CameraProjection p = CameraProjection.project(
                0, 0, fov, WIDTH, HEIGHT, sin(bearing), 0, -cos(bearing));

        // The prototype-locked blend: t = (ndc - 0.1) / 0.9, w = t*t*(3 - 2t), the unit
        // screen direction of this eye-plane threat is (1, 0).
        double t = (p.normalizedDistance() - 0.1) / 0.9;
        double w = t * t * (3 - 2 * t);
        double vx = sin(bearing) * (1 - w) + w;
        double vy = -cos(bearing) * (1 - w);
        assertEquals(Math.atan2(vx, -vy), OrbitGeometry.screenRelativeOrbitAngle(bearing, p), 1e-12);
    }

    @Test
    void midBlendSwingsFromTheBearingTowardTheScreenDirection() {
        double bearing = toRadians(20);
        double fov = toRadians(70);
        CameraProjection p = CameraProjection.project(
                0, 0, fov, WIDTH, HEIGHT, sin(bearing), 0, -cos(bearing));
        double orbit = OrbitGeometry.screenRelativeOrbitAngle(bearing, p);
        double screenDirection = Math.atan2(p.offsetX(), -p.offsetY());
        assertTrue(orbit > bearing, "a partially blended angle must leave the pure bearing");
        assertTrue(orbit < screenDirection, "a partially blended angle must not overshoot the screen direction");
    }

    // ---- Screen-relative mode: prototype contract cases (spec v1 §5.3) ----

    @Test
    void lookingUpSlidesAnAheadThreatToTheOrbitBottom() {
        // Threat dead ahead, player pitched 60 deg up: the projection lands far below
        // the view center (fully past the blend end), so the indicator points down.
        CameraProjection p = CameraProjection.project(
                0, toRadians(60), toRadians(70), WIDTH, HEIGHT, 0, -0.67, -14);
        assertEquals(Math.PI, OrbitGeometry.screenRelativeOrbitAngle(0, p), 1e-6);
    }

    @Test
    void wideAngleThreatAtTheScreenEdgePointsRight() {
        // A 60 deg-bearing threat at 90 deg FOV is past the 45 deg half-FOV, so the
        // indicator swings fully to the Threat's screen direction: right of center.
        double bearing = toRadians(60);
        CameraProjection p = CameraProjection.project(
                0, 0, toRadians(90), WIDTH, HEIGHT, sin(bearing), 0, -cos(bearing));
        assertEquals(Math.PI / 2, OrbitGeometry.screenRelativeOrbitAngle(bearing, p), 1e-6);
    }

    @Test
    void behindThreatsPointTowardTheScreenHalfTheyAreBehind() {
        CameraProjection right = CameraProjection.project(0, 0, toRadians(70), WIDTH, HEIGHT, 5, 0, 10);
        double orbitRight = OrbitGeometry.screenRelativeOrbitAngle(Math.PI, right);
        assertTrue(orbitRight > 0 && orbitRight < Math.PI,
                "a threat behind on the right must point into the right screen half");

        CameraProjection left = CameraProjection.project(0, 0, toRadians(70), WIDTH, HEIGHT, -5, 0, 10);
        double orbitLeft = OrbitGeometry.screenRelativeOrbitAngle(Math.PI, left);
        assertTrue(orbitLeft < 0 && orbitLeft > -Math.PI,
                "a threat behind on the left must point into the left screen half");
    }
}
