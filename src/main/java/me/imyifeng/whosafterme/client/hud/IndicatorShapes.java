package me.imyifeng.whosafterme.client.hud;

import java.util.ArrayList;
import java.util.List;

/**
 * The drawable geometry of one Threat indicator (spec v1 §5.2): the arc band stroked
 * along the Orbit and the outward triangle at its center angle, mirroring the locked
 * prototype's drawing model (decision #7) - a canvas-style stroked path for the arc and
 * a solid triangle whose base sits on the orbit point and whose apex reaches one size
 * outward along the orbit normal.
 *
 * <p>Everything is expressed as {@link PlacedShape}s: a local frame on the viewport plus
 * axis-aligned bars to paint in it. The renderer only has to push a translation and a
 * rotation, fill bars, and pop - the same on every HUD API line, where the only era
 * difference is the pose-stack type (spec v1 §6). The bar decomposition exists because
 * the 26.x GUI pipeline accepts axis-aligned fills only; a one-pixel scanline raster of
 * a 12 px triangle and degree-scale chords of a stroked arc are visually identical to
 * the prototype's primitives.
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
    /** Height of one triangle scanline in pixels. */
    public static final double TRIANGLE_SCANLINE_PX = 1.0;
    /** Fewest chords an arc band is split into (prototype floor). */
    public static final int MIN_ARC_STEPS = 8;

    private IndicatorShapes() {
    }

    /** One axis-aligned bar to paint inside an already-rotated local frame (pixels). */
    public record ShapeOp(double x, double y, double halfWidth, double halfHeight) {
    }

    /**
     * A local frame placed on the viewport at ({@code x}, {@code y}) and rotated by
     * {@code rotationRad}, plus the bars to paint in it.
     */
    public record PlacedShape(double x, double y, double rotationRad, List<ShapeOp> ops) {
    }

    /**
     * Chord count for an arc band: roughly one chord per degree of arc, never fewer than
     * {@link #MIN_ARC_STEPS} - the prototype's tessellation rule.
     */
    public static int arcSteps(double halfArcRad) {
        int degrees = (int) Math.ceil(Math.toDegrees(2.0 * halfArcRad));
        return Math.max(MIN_ARC_STEPS, degrees);
    }

    /**
     * The arc band: chords hugging the Orbit path from {@code centerAngleRad -
     * halfArcRad} to {@code centerAngleRad + halfArcRad}, each a bar of the given
     * thickness centered on the path (a stroked line, as the prototype draws it).
     *
     * @param screenRelative selects the Orbit placement style (see class docs)
     */
    public static List<PlacedShape> arc(
            Orbit orbit, double centerAngleRad, double halfArcRad, double thicknessPx,
            boolean screenRelative) {
        int steps = arcSteps(halfArcRad);
        double halfThickness = thicknessPx / 2.0;
        List<PlacedShape> chords = new ArrayList<>(steps);
        double startX = pathX(orbit, centerAngleRad - halfArcRad, screenRelative);
        double startY = pathY(orbit, centerAngleRad - halfArcRad, screenRelative);
        for (int i = 1; i <= steps; i++) {
            double angle = centerAngleRad - halfArcRad + (2.0 * halfArcRad) * i / steps;
            double endX = pathX(orbit, angle, screenRelative);
            double endY = pathY(orbit, angle, screenRelative);
            double dx = endX - startX;
            double dy = endY - startY;
            chords.add(new PlacedShape(
                    (startX + endX) / 2.0,
                    (startY + endY) / 2.0,
                    Math.atan2(dy, dx),
                    List.of(new ShapeOp(0, 0, Math.hypot(dx, dy) / 2.0, halfThickness))));
            startX = endX;
            startY = endY;
        }
        return chords;
    }

    /**
     * The triangle: base centered on the Orbit point at {@code angleRad}, apex reaching
     * {@code sizePx} outward along the orbit normal (prototype {@code drawTri}). Painted
     * as scanlines that narrow linearly from the base on the orbit to the apex, so the
     * shape reads identically on fill-only pipelines.
     *
     * @param screenRelative selects the Orbit placement style and, with it, the normal:
     *     the ellipse normal in Absolute mode, the view-ray direction in Screen-relative
     */
    public static PlacedShape triangle(
            Orbit orbit, double angleRad, double sizePx, boolean screenRelative) {
        double pointX = pathX(orbit, angleRad, screenRelative);
        double pointY = pathY(orbit, angleRad, screenRelative);
        double normalX;
        double normalY;
        if (screenRelative) {
            // Radial direction: the ray the indicator sits on (prototype orbitNormal) -
            // the direction of the Threat's screen offset, which is what "toward the
            // Threat" means in this mode. Coincides with the ellipse normal on circles.
            double dx = pointX - orbit.centerX();
            double dy = pointY - orbit.centerY();
            double length = Math.hypot(dx, dy);
            normalX = dx / length;
            normalY = dy / length;
        } else {
            // Ellipse normal: the normalized gradient of the implicit ellipse equation,
            // ((p-c)/rx^2, (p-c)/ry^2) - perpendicular to the orbit at every bearing
            // (unlike the unit-circle radial, which only agrees at the axis bearings).
            double gx = (pointX - orbit.centerX()) / (orbit.radiusX() * orbit.radiusX());
            double gy = (pointY - orbit.centerY()) / (orbit.radiusY() * orbit.radiusY());
            double length = Math.hypot(gx, gy);
            normalX = gx / length;
            normalY = gy / length;
        }
        // Rotation that aims the local +y axis (screen down) along the outward normal.
        double rotationRad = Math.atan2(-normalX, normalY);

        // Prototype profile: half-width 0.55 * size at the base (s = 0, on the orbit)
        // shrinking linearly to the apex at s = size, one size outward.
        int rows = (int) Math.ceil(sizePx / TRIANGLE_SCANLINE_PX);
        List<ShapeOp> ops = new ArrayList<>(rows);
        for (int i = 0; i < rows; i++) {
            double fromBase = sizePx * (i + 0.5) / rows;
            ops.add(new ShapeOp(
                    0,
                    fromBase,
                    TRIANGLE_BASE_SPREAD * sizePx * (1.0 - fromBase / sizePx),
                    TRIANGLE_SCANLINE_PX / 2.0));
        }
        return new PlacedShape(pointX, pointY, rotationRad, List.copyOf(ops));
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
}
