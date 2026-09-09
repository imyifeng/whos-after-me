package me.imyifeng.whosafterme.client.hud;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Geometry tests for {@link IndicatorShapes} (spec v1 §5.2): the arc band stroked along
 * the Orbit with constant thickness, and the outward triangle at the arc's center angle,
 * both mirroring the locked prototype's drawing model (decision #7) in the Orbit module's
 * two placement styles. The shapes are triangle meshes in viewport coordinates - the
 * vector geometry the GPU pipelines submit directly (issue #54) - emitted as
 * quad-ordered vertex lists, four xy pairs per quad.
 */
class IndicatorShapesTest {
    private static final double EPS = 1e-9;
    /**
     * Vertex-coordinate tolerance: the meshes store float xy pairs, so derived lengths
     * at orbit distances (~1e3 px) carry a float32 rounding error around 1e-4 px - three
     * orders of magnitude below the locked look's pixel budget.
     */
    private static final double GEOMETRY_EPS = 1e-3;

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
        float[] mesh = IndicatorShapes.arcVertices(circle, center, halfArc, 3.0, false);

        double[] start = arcQuadEnd(mesh, 0, -1);
        assertPointNear(start[0], start[1],
                circle.xAt(center - halfArc), circle.yAt(center - halfArc));
        int lastQuad = quadCount(mesh) - 1;
        double[] end = arcQuadEnd(mesh, lastQuad, 1);
        assertPointNear(end[0], end[1],
                circle.xAt(center + halfArc), circle.yAt(center + halfArc));
    }

    @Test
    void arcBandHugsTheOrbitCurve() {
        double halfArc = Math.toRadians(9);
        float[] mesh = IndicatorShapes.arcVertices(wide, -Math.PI / 3, halfArc, 3.0, false);

        int steps = IndicatorShapes.arcSteps(halfArc);
        for (int i = 0; i <= steps; i++) {
            // Every sampled band center (the mean of each inner/outer pair, including the
            // shared seam vertices between quads) must lie on the ellipse - chord sag below
            // a hundredth of a pixel at the prototype's one-degree tessellation rule.
            double[] pathSample = arcPathSample(mesh, i);
            double offsetX = pathSample[0] - wide.centerX();
            double offsetY = pathSample[1] - wide.centerY();
            double ellipseValue = (offsetX * offsetX) / (wide.radiusX() * wide.radiusX())
                    + (offsetY * offsetY) / (wide.radiusY() * wide.radiusY());
            assertEquals(1, ellipseValue, 0.001,
                    "band center must lie on the ellipse (chord sag below a hundredth of a pixel)");
        }
    }

    @Test
    void arcBandThicknessIsConstantAndCenteredOnTheOrbit() {
        double halfArc = Math.toRadians(3);
        float[] mesh = IndicatorShapes.arcVertices(circle, 0, halfArc, 3.0, false);

        assertEquals(8 * IndicatorShapes.arcSteps(halfArc), mesh.length,
                "each step emits one quad of four xy pairs");
        int steps = IndicatorShapes.arcSteps(halfArc);
        for (int i = 0; i <= steps; i++) {
            double[] inner = arcRingPoint(mesh, i, false);
            double[] outer = arcRingPoint(mesh, i, true);
            double halfThickness = Math.hypot(outer[0] - inner[0], outer[1] - inner[1]) / 2.0;
            assertEquals(1.5, halfThickness, GEOMETRY_EPS, "thickness never scales");
            double[] mid = arcPathSample(mesh, i);
            assertEquals(mid[0], (inner[0] + outer[0]) / 2.0, GEOMETRY_EPS,
                    "the band is centered on the orbit");
            assertEquals(mid[1], (inner[1] + outer[1]) / 2.0, GEOMETRY_EPS,
                    "the band is centered on the orbit");
        }
    }

    @Test
    void arcBandOffsetsFollowTheOutwardNormal() {
        double halfArc = Math.toRadians(3);
        float[] mesh = IndicatorShapes.arcVertices(circle, 0, halfArc, 3.0, false);

        int steps = IndicatorShapes.arcSteps(halfArc);
        for (int i = 0; i <= steps; i++) {
            // On a circle the outward normal is the unit radial: inner-to-outer must aim
            // away from the viewport center.
            double[] inner = arcRingPoint(mesh, i, false);
            double[] outer = arcRingPoint(mesh, i, true);
            double[] mid = arcPathSample(mesh, i);
            double radialX = mid[0] - circle.centerX();
            double radialY = mid[1] - circle.centerY();
            double offsetLength = Math.hypot(outer[0] - inner[0], outer[1] - inner[1]);
            double offsetX = (outer[0] - inner[0]) / offsetLength;
            double offsetY = (outer[1] - inner[1]) / offsetLength;
            double dot = offsetX * radialX + offsetY * radialY;
            double radialLength = Math.hypot(radialX, radialY);
            assertEquals(radialLength, dot, 0.01,
                    "the band offset must aim along the outward normal at every sample");
        }
    }

    @Test
    void screenRelativeArcFollowsTheRadialRays() {
        double halfArc = Math.toRadians(3);
        double center = Math.PI / 4;
        float[] mesh = IndicatorShapes.arcVertices(wide, center, halfArc, 3.0, true);

        int steps = IndicatorShapes.arcSteps(halfArc);
        for (int i = 0; i <= steps; i++) {
            double[] mid = arcPathSample(mesh, i);
            double offsetX = mid[0] - wide.centerX();
            double offsetY = mid[1] - wide.centerY();
            // Perpendicular distance from the sample to the ray at its own orbit angle:
            // zero when the point sits on the ray, which is the Screen-relative contract.
            double angle = Math.atan2(offsetX, -offsetY);
            double rayX = Math.sin(angle);
            double rayY = -Math.cos(angle);
            double cross = rayX * offsetY - rayY * offsetX;
            assertEquals(0, cross, 0.05, "Screen-relative placement sits on the view ray");
        }

        double[] start = arcQuadEnd(mesh, 0, -1);
        double startAngle = center - halfArc;
        assertPointNear(start[0], start[1],
                wide.centerX() + Math.sin(startAngle) * wide.radiusAlong(startAngle),
                wide.centerY() - Math.cos(startAngle) * wide.radiusAlong(startAngle));
    }

    @Test
    void longArcsGetMoreQuadsThanShortOnes() {
        double shortArc = IndicatorShapes.arcSteps(Math.toRadians(3));
        double longArc = IndicatorShapes.arcSteps(Math.toRadians(9));
        assertEquals(8, shortArc, "the prototype floors the resolution at 8 chords");
        assertTrue(longArc > shortArc, "longer arcs resolve at roughly one degree per chord");
    }

    // ---- Triangle ----

    @Test
    void triangleSitsOnTheOrbitPointPointingOutward() {
        double radius = circle.radiusY();
        float[] mesh = IndicatorShapes.triangleVertices(circle, 0, 12.0, false);

        // Base centered on the orbit point at the top of the orbit...
        double[] baseMid = baseMidpoint(mesh);
        assertPointNear(baseMid[0], baseMid[1], circle.centerX(), circle.centerY() - radius);

        // ...with the base-to-apex axis (local +y) rotated onto the normal (0, -1).
        double[] apex = apex(mesh);
        double axisX = apex[0] - baseMid[0];
        double axisY = apex[1] - baseMid[1];
        double axisLength = Math.hypot(axisX, axisY);
        assertPointNear(axisX / axisLength, axisY / axisLength, 0, -1);

        // Prototype drawTri: the base half-width is 0.55 x size on the orbit and the apex
        // reaches one size outward of the orbit point.
        double baseHalf = Math.hypot(mesh[2] - mesh[0], mesh[3] - mesh[1]) / 2.0;
        assertEquals(6.6, baseHalf, GEOMETRY_EPS, "base half-width is 0.55 x size on the orbit point");
        assertEquals(12.0, axisLength, GEOMETRY_EPS,
                "the apex reaches exactly one size outward of the orbit point");
    }

    @Test
    void triangleClosesAsADegenerateQuad() {
        float[] mesh = IndicatorShapes.triangleVertices(circle, 1.0, 12.0, false);

        assertEquals(8, mesh.length, "one quad of four xy pairs");
        assertEquals(mesh[6], mesh[4], GEOMETRY_EPS, "the quad repeats the apex");
        assertEquals(mesh[7], mesh[5], GEOMETRY_EPS, "the quad repeats the apex");
    }

    @Test
    void triangleScalesWithTheConfiguredSize() {
        float[] mesh = IndicatorShapes.triangleVertices(circle, 0, 24.0, false);

        double baseHalf = Math.hypot(mesh[2] - mesh[0], mesh[3] - mesh[1]) / 2.0;
        assertEquals(13.2, baseHalf, GEOMETRY_EPS, "the base scales with the configured size");
        double[] baseMid = baseMidpoint(mesh);
        double[] apex = apex(mesh);
        assertEquals(24.0, Math.hypot(apex[0] - baseMid[0], apex[1] - baseMid[1]), GEOMETRY_EPS,
                "the apex reach scales with the configured size");
    }

    @Test
    void absoluteTriangleNormalIsTheEllipseNormal() {
        double angle = Math.PI / 4;
        float[] mesh = IndicatorShapes.triangleVertices(wide, angle, 12.0, false);

        double pointX = wide.xAt(angle) - wide.centerX();
        double pointY = wide.yAt(angle) - wide.centerY();
        double[] baseMid = baseMidpoint(mesh);
        double[] apex = apex(mesh);
        double axisX = apex[0] - baseMid[0];
        double axisY = apex[1] - baseMid[1];
        double length = Math.hypot(axisX, axisY);

        // The Absolute-mode normal is the true ellipse normal - the normalized gradient
        // of the implicit equation ((p-c)/rx^2, (p-c)/ry^2) - so the triangle stays
        // perpendicular to the orbit at every bearing. (The prototype's drawTri code
        // normalized ((p-c)/rx, (p-c)/ry), which reduces to the unit-circle radial and
        // leans off the arc away from the axis bearings; the port deliberately corrects
        // that here, per the visual-QA pointing report.)
        double gradientX = pointX / (wide.radiusX() * wide.radiusX());
        double gradientY = pointY / (wide.radiusY() * wide.radiusY());
        double gradientLength = Math.hypot(gradientX, gradientY);
        assertPointNear(axisX / length, axisY / length,
                gradientX / gradientLength, gradientY / gradientLength);

        // ...and on a wide orbit it differs from the plain radial direction, which is the
        // Screen-relative normal - the two modes must not silently coincide.
        double radialX = pointX / Math.hypot(pointX, pointY);
        double radialY = pointY / Math.hypot(pointX, pointY);
        assertTrue(Math.abs(axisX / length - radialX) > 0.01 || Math.abs(axisY / length - radialY) > 0.01,
                "the ellipse normal must differ from the radial direction off the axes");
    }

    @Test
    void screenRelativeTriangleNormalIsRadial() {
        double angle = Math.PI / 4;
        float[] mesh = IndicatorShapes.triangleVertices(wide, angle, 12.0, true);

        double radius = wide.radiusAlong(angle);
        double pointX = Math.sin(angle) * radius;
        double pointY = -Math.cos(angle) * radius;
        double length = Math.hypot(pointX, pointY);
        double[] baseMid = baseMidpoint(mesh);
        double[] apex = apex(mesh);
        double axisX = apex[0] - baseMid[0];
        double axisY = apex[1] - baseMid[1];
        double axisLength = Math.hypot(axisX, axisY);
        assertPointNear(axisX / axisLength, axisY / axisLength, pointX / length, pointY / length);
    }

    /** Asserts two points coincide within a hundredth of a pixel. */
    private static void assertPointNear(double actualX, double actualY, double expectedX, double expectedY) {
        assertEquals(expectedX, actualX, 0.01, "x mismatch");
        assertEquals(expectedY, actualY, 0.01, "y mismatch");
    }

    /** Number of quads in an arc mesh (four xy pairs each). */
    private static int quadCount(float[] mesh) {
        return mesh.length / 8;
    }

    /**
     * One end of the arc band's first ({@code quad} 0) or last quad: {@code sign -1} is
     * the start edge (against the arc direction), {@code sign 1} the end edge. Both edges
     * of an end quad are the same radial cut through the band, so either vertex pair works.
     */
    private static double[] arcQuadEnd(float[] mesh, int quad, int sign) {
        int base = sign < 0 ? quad * 8 : quad * 8 + 4;
        return new double[] {(mesh[base] + mesh[base + 2]) / 2.0,
                (mesh[base + 1] + mesh[base + 3]) / 2.0};
    }

    /** The {@code i}-th sampled point on the orbit path: the mean of the inner/outer pair. */
    private static double[] arcPathSample(float[] mesh, int i) {
        double[] inner = arcRingPoint(mesh, i, false);
        double[] outer = arcRingPoint(mesh, i, true);
        return new double[] {(inner[0] + outer[0]) / 2.0, (inner[1] + outer[1]) / 2.0};
    }

    /**
     * The {@code i}-th sampled ring vertex of an arc mesh ({@code steps + 1} samples,
     * shared between neighboring quads): {@code outer} picks the outer band ring,
     * {@code false} the inner one. Quads are laid out as (inner_i, outer_i, outer_i+1,
     * inner_i+1), so sample {@code i < steps} reads quad {@code i}'s leading pair and
     * sample {@code steps} reads the last quad's trailing pair.
     */
    private static double[] arcRingPoint(float[] mesh, int i, boolean outer) {
        int steps = quadCount(mesh);
        int offset;
        if (i < steps) {
            offset = i * 8 + (outer ? 2 : 0);
        } else {
            offset = (steps - 1) * 8 + (outer ? 4 : 6);
        }
        return new double[] {mesh[offset], mesh[offset + 1]};
    }

    /** The midpoint of the triangle mesh's base edge (the first two vertices). */
    private static double[] baseMidpoint(float[] mesh) {
        return new double[] {(mesh[0] + mesh[2]) / 2.0, (mesh[1] + mesh[3]) / 2.0};
    }

    /** The triangle mesh's apex (the quad's repeated third vertex). */
    private static double[] apex(float[] mesh) {
        return new double[] {mesh[4], mesh[5]};
    }
}
