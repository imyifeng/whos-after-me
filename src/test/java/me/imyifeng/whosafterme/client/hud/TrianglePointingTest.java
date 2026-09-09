package me.imyifeng.whosafterme.client.hud;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import me.imyifeng.whosafterme.client.hud.IndicatorShapes.PlacedShape;
import me.imyifeng.whosafterme.client.hud.IndicatorShapes.ShapeOp;

/**
 * Regression tests for the triangle pointing reported broken in visual QA: the
 * indicator triangle must point at the Threat - outward from the Orbit - and its axis
 * must stay perpendicular to the arc it sits on (the user's acceptance model: "if the
 * arc is approximated by a straight line, the arrow is perpendicular to that line"),
 * at every bearing, not just dead ahead.
 *
 * <p>The tests mirror the renderer's exact call chain (HudRenderer.render) over the
 * pure modules, sweeping bearings at a 16:9 and a 1:1 aspect. The expected normal is
 * derived independently of IndicatorShapes: the ellipse tangent comes from a numeric
 * derivative of Orbit's parameterization and the normal is that tangent rotated a
 * quarter turn, oriented outward - so a wrong formula inside the module cannot
 * self-confirm.
 */
class TrianglePointingTest {

    private static final double TOLERANCE_RAD = Math.toRadians(0.5);
    private static final double FOV_RAD = Math.toRadians(70.0);

    /** Camera frame shared with the renderer: yaw 0 faces -z, positive yaw toward +x. */
    private static double bearingBearingToOffsetX(double bearingRad, double distance) {
        return distance * Math.sin(bearingRad);
    }

    private static double bearingToOffsetZ(double bearingRad, double distance) {
        return -distance * Math.cos(bearingRad);
    }

    /** The screen direction the canvas' rotation aims the shape's axis at. */
    private static double[] axisOf(double rotationRad) {
        return new double[] {-Math.sin(rotationRad), Math.cos(rotationRad)};
    }

    private static double cross(double ax, double ay, double bx, double by) {
        return ax * by - ay * bx;
    }

    private static double dot(double ax, double ay, double bx, double by) {
        return ax * bx + ay * by;
    }

    /** Outward unit normal of the Orbit at {@code angleRad}, from a numeric tangent. */
    private static double[] outwardNormal(Orbit orbit, double angleRad) {
        double e = 1e-6;
        double tx = orbit.xAt(angleRad + e) - orbit.xAt(angleRad - e);
        double ty = orbit.yAt(angleRad + e) - orbit.yAt(angleRad - e);
        double length = Math.hypot(tx, ty);
        double nx = -ty / length;
        double ny = tx / length;
        double px = orbit.xAt(angleRad) - orbit.centerX();
        double py = orbit.yAt(angleRad) - orbit.centerY();
        if (dot(nx, ny, px, py) < 0) {
            nx = -nx;
            ny = -ny;
        }
        return new double[] {nx, ny};
    }

    private void sweepAspects(boolean screenRelative) {
        int[][] viewports = {{320, 180}, {200, 200}};
        for (int[] viewport : viewports) {
            Orbit orbit = Orbit.of(viewport[0], viewport[1], 95);
            for (int deg = -180; deg < 180; deg += 15) {
                double bearing = Math.toRadians(deg);
                double distance = 8.0;
                double dx = bearingBearingToOffsetX(bearing, distance);
                double dz = bearingToOffsetZ(bearing, distance);

                double relativeBearing = OrbitGeometry.relativeBearing(
                        Math.atan2(dx, -dz), 0.0);
                double orbitAngle = screenRelative
                        ? OrbitGeometry.screenRelativeOrbitAngle(relativeBearing,
                                CameraProjection.project(0.0, 0.0, FOV_RAD,
                                        viewport[0], viewport[1], dx, 0.0, dz))
                        : OrbitGeometry.absoluteOrbitAngle(relativeBearing);

                PlacedShape triangle = IndicatorShapes.triangle(
                        orbit, orbitAngle, IndicatorShapes.TRIANGLE_SIZE_PX, screenRelative);

                // Mirror the renderer's placement: Absolute parameterizes the ellipse,
                // Screen-relative walks the view ray to its ellipse crossing.
                double px = screenRelative
                        ? orbit.centerX() + Math.sin(orbitAngle) * orbit.radiusAlong(orbitAngle)
                        : orbit.xAt(orbitAngle);
                double py = screenRelative
                        ? orbit.centerY() - Math.cos(orbitAngle) * orbit.radiusAlong(orbitAngle)
                        : orbit.yAt(orbitAngle);
                double[] axis = axisOf(triangle.rotationRad());
                double[] normal = outwardNormal(orbit, orbitAngle);

                boolean wide = viewport[0] > viewport[1];
                String where = (screenRelative ? "view" : "compass") + " "
                        + viewport[0] + "x" + viewport[1] + " bearing " + deg + "deg";

                if (!screenRelative || !wide) {
                    // Perpendicularity the user asked for. In view mode on wide aspects
                    // the prototype locks the axis to the view ray instead (that is what
                    // "points at the Threat on screen" means there); the ray only
                    // coincides with the ellipse normal on a circle.
                    assertTrue(Math.abs(cross(axis[0], axis[1], normal[0], normal[1]))
                            < Math.sin(TOLERANCE_RAD), "axis not perpendicular at " + where);
                }
                if (screenRelative && wide) {
                    // View mode, wide aspect: the axis must aim along the view ray the
                    // indicator sits on - the direction of the Threat's screen offset.
                    double rayX = px - orbit.centerX();
                    double rayY = py - orbit.centerY();
                    double rayLength = Math.hypot(rayX, rayY);
                    assertTrue(Math.abs(cross(axis[0], axis[1], rayX / rayLength, rayY / rayLength))
                            < Math.sin(TOLERANCE_RAD), "axis off the view ray at " + where);
                }

                // The apex must be the outward end: the bars' area centroid must sit
                // between the orbit point and the shape's outward reach (prototype
                // drawTri: base on the orbit, apex at one size outward). An inverted
                // triangle (apex on the orbit, base outward) has its centroid beyond
                // half the reach and fails here.
                List<ShapeOp> ops = triangle.ops();
                double area = 0.0;
                double moment = 0.0;
                for (ShapeOp op : ops) {
                    double w = op.halfWidth() * 2.0;
                    area += w;
                    moment += w * op.y();
                }
                double centroidY = moment / area;
                assertTrue(centroidY < IndicatorShapes.TRIANGLE_SIZE_PX / 2.0,
                        "triangle inverted (apex on the orbit) at " + where);
            }
        }
    }

    @Test
    void compassModeTrianglePointsOutwardAndPerpendicularAtEveryBearing() {
        sweepAspects(false);
    }

    @Test
    void viewModeTrianglePointsOutwardAndPerpendicularAtEveryBearing() {
        sweepAspects(true);
    }
}
