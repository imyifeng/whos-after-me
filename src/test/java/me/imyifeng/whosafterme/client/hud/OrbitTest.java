package me.imyifeng.whosafterme.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

/**
 * Ellipse mapping tests for the {@link Orbit} record (spec v1 §5.1): the Orbit is the
 * viewport-centered ellipse matched to the viewport's aspect ratio - a circle at 1:1 -
 * scaled by the configured orbit size percentage.
 */
class OrbitTest {
    private static final double EPS = 1e-9;

    @Test
    void squareViewportMapsToACircle() {
        Orbit orbit = Orbit.of(1000, 1000, 78);
        assertEquals(500, orbit.centerX(), EPS);
        assertEquals(500, orbit.centerY(), EPS);
        assertEquals(390, orbit.radiusX(), EPS);
        assertEquals(390, orbit.radiusY(), EPS);
    }

    @Test
    void wideViewportMatchesTheAspectRatio() {
        Orbit orbit = Orbit.of(1920, 1080, 78);
        assertEquals(960, orbit.centerX(), EPS);
        assertEquals(540, orbit.centerY(), EPS);
        assertEquals(748.8, orbit.radiusX(), EPS);
        assertEquals(421.2, orbit.radiusY(), EPS);
    }

    @Test
    void tallViewportMatchesTheAspectRatio() {
        Orbit orbit = Orbit.of(1080, 1920, 50);
        assertEquals(270, orbit.radiusX(), EPS);
        assertEquals(480, orbit.radiusY(), EPS);
    }

    @Test
    void fullSizeOrbitTouchesTheViewportHalfDimensions() {
        Orbit orbit = Orbit.of(1000, 500, 100);
        assertEquals(500, orbit.radiusX(), EPS);
        assertEquals(250, orbit.radiusY(), EPS);
    }

    @Test
    void angleZeroIsTheTopOfTheOrbit() {
        Orbit orbit = Orbit.of(1920, 1080, 78);
        assertEquals(960, orbit.xAt(0), EPS);
        assertEquals(540 - 421.2, orbit.yAt(0), EPS);
    }

    @Test
    void anglesRunClockwiseFromTheTop() {
        Orbit orbit = Orbit.of(1000, 1000, 80);
        double center = 500;
        double radius = 400;

        assertEquals(center + radius, orbit.xAt(Math.PI / 2), EPS);
        assertEquals(center, orbit.yAt(Math.PI / 2), EPS);

        assertEquals(center, orbit.xAt(Math.PI), EPS);
        assertEquals(center + radius, orbit.yAt(Math.PI), EPS);

        assertEquals(center - radius, orbit.xAt(-Math.PI / 2), EPS);
        assertEquals(center, orbit.yAt(-Math.PI / 2), EPS);
    }

    @Test
    void everyAbsolutePlacementPointLiesOnTheEllipse() {
        Orbit orbit = Orbit.of(1920, 1080, 78);
        for (int i = 0; i < 24; i++) {
            double angle = -Math.PI + i * (2.0 * Math.PI) / 24;
            assertOnEllipse(orbit, orbit.xAt(angle) - orbit.centerX(), orbit.yAt(angle) - orbit.centerY(), angle);
        }
    }

    @Test
    void radialDistanceMatchesTheAxesOnTheCardinalDirections() {
        Orbit orbit = Orbit.of(1920, 1080, 78);
        assertEquals(orbit.radiusY(), orbit.radiusAlong(0), EPS);
        assertEquals(orbit.radiusX(), orbit.radiusAlong(Math.PI / 2), EPS);
        assertEquals(orbit.radiusY(), orbit.radiusAlong(Math.PI), EPS);
        assertEquals(orbit.radiusX(), orbit.radiusAlong(-Math.PI / 2), EPS);
    }

    @Test
    void radialPlacementPointLiesOnTheEllipse() {
        Orbit orbit = Orbit.of(1920, 1080, 78);
        for (int i = 0; i < 24; i++) {
            double angle = -Math.PI + i * (2.0 * Math.PI) / 24;
            double radius = orbit.radiusAlong(angle);
            assertOnEllipse(orbit, Math.sin(angle) * radius, -Math.cos(angle) * radius, angle);
        }
    }

    /** Asserts the viewport-relative point lies on the Orbit ellipse. */
    private static void assertOnEllipse(Orbit orbit, double offsetX, double offsetY, double angle) {
        double ellipseValue = (offsetX * offsetX) / (orbit.radiusX() * orbit.radiusX())
                + (offsetY * offsetY) / (orbit.radiusY() * orbit.radiusY());
        assertEquals(1, ellipseValue, 1e-9, "point at angle " + angle + " must lie on the ellipse");
    }
}
