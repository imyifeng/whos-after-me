package me.imyifeng.whosafterme.client.hud;

import static java.lang.Math.cos;
import static java.lang.Math.sin;
import static java.lang.Math.tan;
import static java.lang.Math.toRadians;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Camera projection tests for {@link CameraProjection} (spec v1 §5.3, Screen-relative
 * aiming): world offsets pushed through the camera's yaw, pitch and FOV. The camera
 * frame mirrors the locked prototype (decision #7): yaw 0 faces -z, positive yaw turns
 * toward +x, positive pitch looks up, and the FOV spans the viewport width.
 */
class CameraProjectionTest {
    private static final double EPS = 1e-9;
    private static final double WIDTH = 1920;
    private static final double HEIGHT = 1080;

    @Test
    void threatOnTheViewAxisProjectsToTheViewCenter() {
        CameraProjection p = CameraProjection.project(0, 0, toRadians(70), WIDTH, HEIGHT, 0, 0, -14);
        assertEquals(0, p.offsetX(), EPS);
        assertEquals(0, p.offsetY(), EPS);
        assertEquals(0, p.normalizedDistance(), EPS);
        assertFalse(p.behind());
    }

    @Test
    void lateralOffsetScalesWithTheHorizontalFov() {
        // A threat at relative bearing 60 deg on the eye plane: ndcX = tan(60) / tan(35).
        double bearing = toRadians(60);
        CameraProjection p = CameraProjection.project(
                0, 0, toRadians(70), WIDTH, HEIGHT, 14 * sin(bearing), 0, -14 * cos(bearing));
        double ndcX = tan(bearing) / tan(toRadians(35));
        assertEquals(ndcX * WIDTH / 2, p.offsetX(), 1e-6);
        assertEquals(0, p.offsetY(), EPS);
        assertEquals(ndcX, p.normalizedDistance(), 1e-6);
        assertFalse(p.behind());
    }

    @Test
    void verticalOffsetScalesWithTheViewportAspect() {
        // Straight ahead and two blocks above the eye plane; the vertical half-FOV is
        // narrowed by the aspect ratio, and screen y grows downward.
        CameraProjection p = CameraProjection.project(0, 0, toRadians(70), WIDTH, HEIGHT, 0, 2, -14);
        double ndcY = 2.0 / (14 * tan(toRadians(35)) * HEIGHT / WIDTH);
        assertEquals(-ndcY * HEIGHT / 2, p.offsetY(), 1e-6);
        assertEquals(ndcY, p.normalizedDistance(), 1e-6);
        assertFalse(p.behind());
    }

    @Test
    void lookingUpMovesAnAheadThreatBelowTheViewCenter() {
        CameraProjection p = CameraProjection.project(
                0, toRadians(60), toRadians(70), WIDTH, HEIGHT, 0, -0.67, -14);
        assertTrue(p.offsetY() > 0, "looking up must move the threat's projection down the screen");
        assertTrue(p.normalizedDistance() > 1.0);
        assertFalse(p.behind());
    }

    @Test
    void behindCameraKeepsTheTurnDirection() {
        CameraProjection right = CameraProjection.project(0, 0, toRadians(70), WIDTH, HEIGHT, 5, 0, 10);
        assertTrue(right.behind());
        assertTrue(right.offsetX() > 0, "a threat behind on the right must stay on the right");

        CameraProjection left = CameraProjection.project(0, 0, toRadians(70), WIDTH, HEIGHT, -5, 0, 10);
        assertTrue(left.behind());
        assertTrue(left.offsetX() < 0, "a threat behind on the left must stay on the left");
    }

    @Test
    void inPlaneThreatStaysFinite() {
        // A threat in the view plane has zero forward component; the projection must
        // stay finite instead of dividing by zero.
        CameraProjection p = CameraProjection.project(0, 0, toRadians(70), WIDTH, HEIGHT, 5, 0, 0);
        assertTrue(Double.isFinite(p.offsetX()));
        assertTrue(Double.isFinite(p.normalizedDistance()));
        assertFalse(p.behind());
    }
}
