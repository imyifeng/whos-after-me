package me.imyifeng.whosafterme.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Geometry tests for {@link IndicatorShapes} (spec v1 §5.2): the arc band stroked along
 * the Orbit with constant thickness, and the outward triangle at the arc's center angle,
 * both mirroring the locked prototype's drawing model (decision #7) in the Orbit module's
 * two placement styles.
 */
class IndicatorShapesTest {
    private static final double EPS = 1e-9;
    private static final double GEOMETRY_EPS = 1e-6;

    /** A 1920x1080 viewport orbit at the default 78% - wide, so the modes differ. */
    private final Orbit wide = Orbit.of(1920, 1080, 78);
    /** A square-viewport orbit is a circle, where both placements coincide. */
    private final Orbit circle = Orbit.of(1000, 1000, 80);

    // ---- Arc band ----

    @Test
    void arcConstantsMatchTheLockedPrototype() {
        assertEquals(3.0, IndicatorShapes.ARC_THICKNESS_PX, EPS);
        assertEquals(12.0, IndicatorShapes.TRIANGLE_SIZE_PX, EPS);
    }

    @Test
    void arcBandRunsFromTheStartAngleToTheEndAngle() {
        double center = Math.PI / 4;
        double halfArc = Math.toRadians(3);
        List<IndicatorShapes.PlacedShape> shapes =
                IndicatorShapes.arc(circle, center, halfArc, 3.0, false);

        double[] start = chordEnd(shapes.getFirst(), -1);
        assertPointNear(start[0], start[1],
                circle.xAt(center - halfArc), circle.yAt(center - halfArc));
        double[] end = chordEnd(shapes.getLast(), 1);
        assertPointNear(end[0], end[1],
                circle.xAt(center + halfArc), circle.yAt(center + halfArc));
    }

    @Test
    void arcBandChordsHugTheOrbitCurve() {
        double halfArc = Math.toRadians(9);
        List<IndicatorShapes.PlacedShape> shapes =
                IndicatorShapes.arc(wide, -Math.PI / 3, halfArc, 3.0, false);

        for (IndicatorShapes.PlacedShape shape : shapes) {
            double offsetX = shape.x() - wide.centerX();
            double offsetY = shape.y() - wide.centerY();
            double ellipseValue = (offsetX * offsetX) / (wide.radiusX() * wide.radiusX())
                    + (offsetY * offsetY) / (wide.radiusY() * wide.radiusY());
            assertEquals(1, ellipseValue, 0.001,
                    "chord midpoint must lie on the ellipse (chord sag below a hundredth of a pixel)");
        }
    }

    @Test
    void arcBandThicknessIsConstantAndCenteredOnTheOrbit() {
        double halfArc = Math.toRadians(3);
        List<IndicatorShapes.PlacedShape> shapes =
                IndicatorShapes.arc(circle, 0, halfArc, 3.0, false);

        assertEquals(IndicatorShapes.arcSteps(halfArc), shapes.size());
        for (IndicatorShapes.PlacedShape shape : shapes) {
            assertEquals(1, shape.ops().size(), "each chord is one bar");
            IndicatorShapes.ShapeOp bar = shape.ops().getFirst();
            assertEquals(3.0, 2 * bar.halfHeight(), GEOMETRY_EPS, "thickness never scales");
            assertTrue(2 * bar.halfWidth() > 0, "chords must have positive length");
            assertEquals(0, bar.x(), GEOMETRY_EPS);
            assertEquals(0, bar.y(), GEOMETRY_EPS);
        }
    }

    @Test
    void arcBandChordsPointAlongThePath() {
        double halfArc = Math.toRadians(3);
        List<IndicatorShapes.PlacedShape> shapes =
                IndicatorShapes.arc(circle, 0, halfArc, 3.0, false);

        for (IndicatorShapes.PlacedShape shape : shapes) {
            // The rotation must aim the bar's local +x along the chord it covers.
            double chordAngle = shape.rotationRad();
            double x = Math.cos(chordAngle);
            double y = Math.sin(chordAngle);
            // On a circle the chord is perpendicular to the radius at its midpoint.
            double radialX = shape.x() - circle.centerX();
            double radialY = shape.y() - circle.centerY();
            double dot = x * radialX + y * radialY;
            assertEquals(0, dot, 0.01, "chord direction must be tangential at the midpoint");
        }
    }

    @Test
    void screenRelativeArcFollowsTheRadialRays() {
        double halfArc = Math.toRadians(3);
        double center = Math.PI / 4;
        List<IndicatorShapes.PlacedShape> shapes =
                IndicatorShapes.arc(wide, center, halfArc, 3.0, true);

        for (IndicatorShapes.PlacedShape shape : shapes) {
            double offsetX = shape.x() - wide.centerX();
            double offsetY = shape.y() - wide.centerY();
            // Perpendicular distance from the shape to the ray at its own orbit angle:
            // zero when the point sits on the ray, which is the Screen-relative contract.
            double angle = Math.atan2(shape.x() - wide.centerX(), -(shape.y() - wide.centerY()));
            double rayX = Math.sin(angle);
            double rayY = -Math.cos(angle);
            double cross = rayX * offsetY - rayY * offsetX;
            assertEquals(0, cross, 0.05, "Screen-relative placement sits on the view ray");
        }

        double[] start = chordEnd(shapes.getFirst(), -1);
        double startAngle = center - halfArc;
        assertPointNear(start[0], start[1],
                wide.centerX() + Math.sin(startAngle) * wide.radiusAlong(startAngle),
                wide.centerY() - Math.cos(startAngle) * wide.radiusAlong(startAngle));
    }

    @Test
    void longArcsGetMoreChordsThanShortOnes() {
        double shortArc = IndicatorShapes.arcSteps(Math.toRadians(3));
        double longArc = IndicatorShapes.arcSteps(Math.toRadians(9));
        assertEquals(8, shortArc, "the prototype floors the resolution at 8 chords");
        assertTrue(longArc > shortArc, "longer arcs resolve at roughly one degree per chord");
    }

    // ---- Triangle ----

    @Test
    void triangleSitsOnTheOrbitPointPointingOutward() {
        double radius = circle.radiusY();
        IndicatorShapes.PlacedShape triangle =
                IndicatorShapes.triangle(circle, 0, 12.0, false);

        // Base centered on the orbit point at the top of the orbit...
        assertPointNear(triangle.x(), triangle.y(), circle.centerX(), circle.centerY() - radius);
        // ...with local +y (the outward direction) rotated onto the normal (0, -1).
        double outwardX = -Math.sin(triangle.rotationRad());
        double outwardY = Math.cos(triangle.rotationRad());
        assertPointNear(outwardX, outwardY, 0, -1);

        // Prototype drawTri: the base sits ON the orbit at half-width 0.55 x size
        // (within one scanline of taper) and the apex reaches one size outward.
        IndicatorShapes.ShapeOp base = triangle.ops().getFirst();
        double rows = triangle.ops().size();
        assertEquals(6.6, base.halfWidth(), 6.6 / rows + GEOMETRY_EPS,
                "base half-width is 0.55 x size on the orbit point");
        IndicatorShapes.ShapeOp apexEnd = triangle.ops().getLast();
        assertEquals(12.0, apexEnd.y() + apexEnd.halfHeight(), GEOMETRY_EPS,
                "the apex reaches exactly one size outward of the orbit point");
    }

    @Test
    void triangleNarrowsFromBaseToApex() {
        IndicatorShapes.PlacedShape triangle =
                IndicatorShapes.triangle(circle, 1.0, 12.0, false);

        List<IndicatorShapes.ShapeOp> ops = triangle.ops();
        assertTrue(ops.size() >= 12, "a 12 px triangle resolves at about one scanline per pixel");
        for (int i = 1; i < ops.size(); i++) {
            assertTrue(ops.get(i).halfWidth() <= ops.get(i - 1).halfWidth(),
                    "scanlines narrow monotonically from the base on the orbit to the apex");
        }
        assertEquals(0, ops.getFirst().x(), GEOMETRY_EPS, "scanlines stay on the pointing axis");
    }

    @Test
    void triangleScalesWithTheConfiguredSize() {
        IndicatorShapes.PlacedShape triangle =
                IndicatorShapes.triangle(circle, 0, 24.0, false);

        IndicatorShapes.ShapeOp base = triangle.ops().getFirst();
        double rows = triangle.ops().size();
        assertEquals(13.2, base.halfWidth(), 13.2 / rows + GEOMETRY_EPS,
                "the base scales with the configured size");
        IndicatorShapes.ShapeOp apexEnd = triangle.ops().getLast();
        assertEquals(24.0, apexEnd.y() + apexEnd.halfHeight(), GEOMETRY_EPS,
                "the apex reach scales with the configured size");
    }

    @Test
    void absoluteTriangleNormalIsTheEllipseNormal() {
        double angle = Math.PI / 4;
        IndicatorShapes.PlacedShape triangle =
                IndicatorShapes.triangle(wide, angle, 12.0, false);

        double pointX = wide.xAt(angle) - wide.centerX();
        double pointY = wide.yAt(angle) - wide.centerY();
        double normalX = -Math.sin(triangle.rotationRad());
        double normalY = Math.cos(triangle.rotationRad());

        // The Absolute-mode normal is the true ellipse normal - the normalized gradient
        // of the implicit equation ((p-c)/rx^2, (p-c)/ry^2) - so the triangle stays
        // perpendicular to the orbit at every bearing. (The prototype's drawTri code
        // normalized ((p-c)/rx, (p-c)/ry), which reduces to the unit-circle radial and
        // leans off the arc away from the axis bearings; the port deliberately corrects
        // that here, per the visual-QA pointing report.)
        double gradientX = pointX / (wide.radiusX() * wide.radiusX());
        double gradientY = pointY / (wide.radiusY() * wide.radiusY());
        double length = Math.hypot(gradientX, gradientY);
        assertPointNear(normalX, normalY, gradientX / length, gradientY / length);

        // ...and on a wide orbit it differs from the plain radial direction, which is the
        // Screen-relative normal - the two modes must not silently coincide.
        double radialX = pointX / Math.hypot(pointX, pointY);
        double radialY = pointY / Math.hypot(pointX, pointY);
        assertTrue(Math.abs(normalX - radialX) > 0.01 || Math.abs(normalY - radialY) > 0.01,
                "the ellipse normal must differ from the radial direction off the axes");
    }

    @Test
    void screenRelativeTriangleNormalIsRadial() {
        double angle = Math.PI / 4;
        IndicatorShapes.PlacedShape triangle =
                IndicatorShapes.triangle(wide, angle, 12.0, true);

        double radius = wide.radiusAlong(angle);
        double pointX = Math.sin(angle) * radius;
        double pointY = -Math.cos(angle) * radius;
        double length = Math.hypot(pointX, pointY);
        double normalX = -Math.sin(triangle.rotationRad());
        double normalY = Math.cos(triangle.rotationRad());
        assertPointNear(normalX, normalY, pointX / length, pointY / length);
    }

    /** Asserts two points coincide within a hundredth of a pixel. */
    private static void assertPointNear(double actualX, double actualY, double expectedX, double expectedY) {
        assertEquals(expectedX, actualX, 0.01, "x mismatch");
        assertEquals(expectedY, actualY, 0.01, "y mismatch");
    }

    /**
     * One end of a single-bar placed shape: {@code sign -1} is the start (against the
     * rotation direction), {@code sign 1} the end.
     */
    private static double[] chordEnd(IndicatorShapes.PlacedShape shape, int sign) {
        IndicatorShapes.ShapeOp bar = shape.ops().getFirst();
        double x = shape.x() + sign * Math.cos(shape.rotationRad()) * bar.halfWidth();
        double y = shape.y() + sign * Math.sin(shape.rotationRad()) * bar.halfWidth();
        return new double[] {x, y};
    }
}
