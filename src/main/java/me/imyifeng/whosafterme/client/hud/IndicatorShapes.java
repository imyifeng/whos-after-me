package me.imyifeng.whosafterme.client.hud;

/**
 * The drawable geometry of one Threat indicator (spec v1 §5.2): the arc band stroked
 * along the Orbit and the outward triangle at its center angle, mirroring the locked
 * prototype's drawing model (decision #7) - a canvas-style stroked path for the arc and
 * a solid triangle whose base sits on the orbit point and whose apex reaches one size
 * outward along the orbit normal.
 *
 * <p>Shapes are emitted as viewport-space triangle meshes (issue #54): flat
 * {@code float[]} arrays of xy pairs, four vertices per quad, the vertex order the GUI
 * quad pipelines consume ({@code inner_i, outer_i, outer_i+1, inner_i+1} along the
 * band; the triangle closes as a degenerate quad by repeating its apex). The renderer
 * submits them straight into the era's GPU pipeline, so edges are true geometric edges
 * instead of a scanline raster - at every GUI scale the outline stays pixel-exact.
 * Filling one quad from four vertices is the native shape of every GUI pipeline the
 * anchors ship ({@code RenderType.gui()} quads below 1.21.6, the GUI render pipelines'
 * quad builders from 1.21.6 on), so no index buffers or strips are needed.
 *
 * <p>Pure math over plain numbers, no Minecraft classes (ADR-0004). Angle convention
 * follows the Orbit module: radians, 0 = top of the orbit, positive = clockwise on
 * screen. The two Orbit placement styles are selected by {@code screenRelative} exactly
 * as in the prototype: Absolute parameterizes the ellipse by orbit angle, Screen-relative
 * walks the view rays at {@link Orbit#radiusAlong}.
 */
public final class IndicatorShapes {

    /** Arc band thickness in pixels at indicator scale 1.0 (spec v1 §5.2). */
    public static final double ARC_THICKNESS_PX = 3.0;
    /** Triangle size in pixels at indicator scale 1.0 (spec v1 §5.2): the apex reach. */
    public static final double TRIANGLE_SIZE_PX = 12.0;
    /** Base half-width as a fraction of the triangle size (prototype {@code 0.55}). */
    public static final double TRIANGLE_BASE_SPREAD = 0.55;
    /** Fewest quads an arc band is split into (prototype floor). */
    public static final int MIN_ARC_STEPS = 8;

    private IndicatorShapes() {
    }

    /**
     * Quad count for an arc band: roughly one quad per degree of arc, never fewer than
     * {@link #MIN_ARC_STEPS} - the prototype's tessellation rule. The band envelope
     * deviates from the true arc by less than a hundredth of a pixel at that resolution,
     * so the mesh reads as the prototype's stroked path.
     */
    public static int arcSteps(double halfArcRad) {
        int degrees = (int) Math.ceil(Math.toDegrees(2.0 * halfArcRad));
        return Math.max(MIN_ARC_STEPS, degrees);
    }

    /**
     * The arc band: quads hugging the Orbit path from {@code centerAngleRad - halfArcRad}
     * to {@code centerAngleRad + halfArcRad}, each quad spanning one tessellation step
     * between two path samples, offset by half the given thickness to either side of the
     * path along the outward normal (a stroked line of constant width, as the prototype
     * draws it). Returns {@code 8 * arcSteps(halfArcRad)} floats.
     *
     * @param screenRelative selects the Orbit placement style (see class docs)
     */
    public static float[] arcVertices(
            Orbit orbit, double centerAngleRad, double halfArcRad, double thicknessPx,
            boolean screenRelative) {
        int steps = arcSteps(halfArcRad);
        double halfThickness = thicknessPx / 2.0;
        float[] mesh = new float[8 * steps];

        double angle = centerAngleRad - halfArcRad;
        double innerX = ringX(orbit, angle, -halfThickness, screenRelative);
        double innerY = ringY(orbit, angle, -halfThickness, screenRelative);
        double outerX = ringX(orbit, angle, halfThickness, screenRelative);
        double outerY = ringY(orbit, angle, halfThickness, screenRelative);
        for (int i = 0; i < steps; i++) {
            angle = centerAngleRad - halfArcRad + (2.0 * halfArcRad) * (i + 1) / steps;
            double nextInnerX = ringX(orbit, angle, -halfThickness, screenRelative);
            double nextInnerY = ringY(orbit, angle, -halfThickness, screenRelative);
            double nextOuterX = ringX(orbit, angle, halfThickness, screenRelative);
            double nextOuterY = ringY(orbit, angle, halfThickness, screenRelative);

            int o = 8 * i;
            mesh[o] = (float) innerX;
            mesh[o + 1] = (float) innerY;
            mesh[o + 2] = (float) outerX;
            mesh[o + 3] = (float) outerY;
            mesh[o + 4] = (float) nextOuterX;
            mesh[o + 5] = (float) nextOuterY;
            mesh[o + 6] = (float) nextInnerX;
            mesh[o + 7] = (float) nextInnerY;

            innerX = nextInnerX;
            innerY = nextInnerY;
            outerX = nextOuterX;
            outerY = nextOuterY;
        }
        return mesh;
    }

    /**
     * The triangle: base centered on the Orbit point at {@code angleRad}, apex reaching
     * {@code sizePx} outward along the orbit normal (prototype {@code drawTri}). Emitted
     * as the quad (base left, base right, apex, apex) - the repeated apex closes the quad
     * around the single triangle. Returns 8 floats.
     *
     * @param screenRelative selects the Orbit placement style and, with it, the normal:
     *     the ellipse normal in Absolute mode, the view-ray direction in Screen-relative
     */
    public static float[] triangleVertices(
            Orbit orbit, double angleRad, double sizePx, boolean screenRelative) {
        double pointX = pathX(orbit, angleRad, screenRelative);
        double pointY = pathY(orbit, angleRad, screenRelative);
        // Outward normal at the orbit point. Screen-relative mode uses the radial
        // direction: the ray the indicator sits on (prototype orbitNormal) - the
        // direction of the Threat's screen offset, which is what "toward the Threat"
        // means in this mode (it coincides with the ellipse normal on circles).
        // Absolute mode uses the ellipse normal: the normalized gradient of the implicit
        // ellipse equation, ((p-c)/rx^2, (p-c)/ry^2) - perpendicular to the orbit at
        // every bearing (unlike the unit-circle radial, which only agrees at the axis
        // bearings).
        double nx = normalX(orbit, pointX, pointY, screenRelative);
        double ny = normalY(orbit, pointX, pointY, screenRelative);
        // The tangent at the orbit point: the base edge runs along it, half its width to
        // either side - the axis perpendicular to the base through its midpoint is the
        // outward normal, which is the locked pointing direction (issue #53).
        double baseHalf = TRIANGLE_BASE_SPREAD * sizePx;
        float[] mesh = new float[8];
        mesh[0] = (float) (pointX - ny * baseHalf);
        mesh[1] = (float) (pointY + nx * baseHalf);
        mesh[2] = (float) (pointX + ny * baseHalf);
        mesh[3] = (float) (pointY - nx * baseHalf);
        mesh[4] = (float) (pointX + nx * sizePx);
        mesh[5] = (float) (pointY + ny * sizePx);
        mesh[6] = mesh[4];
        mesh[7] = mesh[5];
        return mesh;
    }

    /** Orbit point x at {@code angleRad} in the selected placement style. */
    private static double pathX(Orbit orbit, double angleRad, boolean screenRelative) {
        return screenRelative
                ? orbit.centerX() + Math.sin(angleRad) * orbit.radiusAlong(angleRad)
                : orbit.xAt(angleRad);
    }

    /** Orbit point y at {@code angleRad} in the selected placement style. */
    private static double pathY(Orbit orbit, double angleRad, boolean screenRelative) {
        return screenRelative
                ? orbit.centerY() - Math.cos(angleRad) * orbit.radiusAlong(angleRad)
                : orbit.yAt(angleRad);
    }

    /**
     * Orbit path point at {@code angleRad}, pushed {@code distancePx} along the outward
     * normal (negative pushes inward) - one ring vertex of the arc band.
     */
    private static double ringX(Orbit orbit, double angleRad, double distancePx,
            boolean screenRelative) {
        double pointX = pathX(orbit, angleRad, screenRelative);
        return pointX + normalX(orbit, pointX, pathY(orbit, angleRad, screenRelative),
                screenRelative) * distancePx;
    }

    /** Orbit path point y at {@code angleRad}, pushed along the normal - see {@link #ringX}. */
    private static double ringY(Orbit orbit, double angleRad, double distancePx,
            boolean screenRelative) {
        double pointX = pathX(orbit, angleRad, screenRelative);
        double pointY = pathY(orbit, angleRad, screenRelative);
        return pointY + normalY(orbit, pointX, pointY, screenRelative) * distancePx;
    }

    /** x of the unit outward normal at the orbit point ({@code pointX}, {@code pointY}). */
    private static double normalX(Orbit orbit, double pointX, double pointY,
            boolean screenRelative) {
        if (!screenRelative) {
            double gx = (pointX - orbit.centerX()) / (orbit.radiusX() * orbit.radiusX());
            double gy = (pointY - orbit.centerY()) / (orbit.radiusY() * orbit.radiusY());
            return gx / Math.hypot(gx, gy);
        }
        double dx = pointX - orbit.centerX();
        return dx / Math.hypot(dx, pointY - orbit.centerY());
    }

    /** y of the unit outward normal at the orbit point ({@code pointX}, {@code pointY}). */
    private static double normalY(Orbit orbit, double pointX, double pointY,
            boolean screenRelative) {
        if (!screenRelative) {
            double gx = (pointX - orbit.centerX()) / (orbit.radiusX() * orbit.radiusX());
            double gy = (pointY - orbit.centerY()) / (orbit.radiusY() * orbit.radiusY());
            return gy / Math.hypot(gx, gy);
        }
        double dy = pointY - orbit.centerY();
        return dy / Math.hypot(pointX - orbit.centerX(), dy);
    }
}
