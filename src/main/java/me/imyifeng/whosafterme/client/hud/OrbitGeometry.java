package me.imyifeng.whosafterme.client.hud;

/**
 * The math behind every Threat indicator (spec v1 §5, prototype resolution #7): the two
 * aiming modes' orbit angles, proximity scaling of arc length and opacity, the
 * Detection-radius edge fade, and the far-to-near render ordering.
 *
 * <p>Pure functions over plain numbers with no Minecraft classes, so the renderer stays
 * thin and the suite runs as plain JUnit in {@code check} on every Anchor (ADR-0004,
 * spec v1 §11 ticket 4). Angle convention throughout: radians, 0 = dead ahead / top of
 * the orbit, positive = clockwise on screen (to the right), range (-pi, pi].
 */
public final class OrbitGeometry {

    /** Arc length in degrees at the Detection-radius edge (spec v1 §5.2). */
    public static final double MIN_ARC_DEG = 6.0;
    /** Arc length in degrees point-blank: ~3x the base (spec v1 §5.2). */
    public static final double MAX_ARC_DEG = 18.0;
    /** Opacity at the Detection-radius edge before the edge fade and config multiplier (spec v1 §5.2). */
    public static final double MIN_OPACITY = 0.3;
    /** Opacity added by point-blank proximity (spec v1 §5.2). */
    public static final double PROXIMITY_OPACITY = 0.7;
    /** Width in blocks of the fade-in band across the outermost Detection-radius blocks (spec v1 §5.2). */
    public static final double EDGE_FADE_BLOCKS = 3.0;
    /** Screen-relative blend start, in normalized screen distance from the view center (spec v1 §5.3). */
    public static final double BLEND_START = 0.1;
    /** Screen-relative blend end: fully screen-directed at/behind the screen edge (spec v1 §5.3). */
    public static final double BLEND_END = 1.0;

    private OrbitGeometry() {
    }

    // ---- Absolute (compass) aiming mode (spec v1 §5.3) ----

    /**
     * Bearing of a Threat relative to the player's facing. Both inputs share one
     * clockwise-positive frame (any consistent frame works - e.g. Minecraft compass
     * bearings read as {@code atan2(dx, -dz)} with facing = yaw + pi); the result is
     * wrapped into (-pi, pi]. Pitch is ignored by design in Absolute mode.
     */
    public static double relativeBearing(double threatBearingRad, double playerFacingRad) {
        double bearing = (threatBearingRad - playerFacingRad) % (2.0 * Math.PI);
        if (bearing > Math.PI) {
            bearing -= 2.0 * Math.PI;
        } else if (bearing < -Math.PI) {
            bearing += 2.0 * Math.PI;
        }
        return bearing;
    }

    /**
     * Orbit angle in Absolute mode (the default): the compass bearing of the Threat
     * relative to the player's yaw - dead ahead maps to the top of the orbit, behind to
     * the bottom. The orbit reads like a ring around the player from above; the mapping
     * is the identity on the relative bearing.
     */
    public static double absoluteOrbitAngle(double relativeBearingRad) {
        return relativeBearingRad;
    }

    // ---- Screen-relative (view) aiming mode (spec v1 §5.3) ----

    /**
     * Orbit angle in Screen-relative mode, per the prototype-locked behavior contract
     * (spec v1 §5.3, decision #7):
     *
     * <ul>
     *   <li>near the view center the angle stays anchored to the compass bearing, where
     *       a pure screen-angle mapping degenerates;</li>
     *   <li>toward/past the screen edge it swings to the direction the Threat left the
     *       view - a smoothstep blend from {@link #BLEND_START} to {@link #BLEND_END}
     *       over {@link CameraProjection#normalizedDistance()};</li>
     *   <li>Threats behind the camera point the way the player would turn: the
     *       projection keeps the screen half the Threat is behind.</li>
     * </ul>
     */
    public static double screenRelativeOrbitAngle(double relativeBearingRad, CameraProjection projection) {
        double weight = smoothstep(BLEND_START, BLEND_END, projection.normalizedDistance());
        double screenX = projection.offsetX();
        double screenY = projection.offsetY();
        double screenLength = Math.hypot(screenX, screenY);
        double unitScreenX = screenLength == 0 ? 0 : screenX / screenLength;
        double unitScreenY = screenLength == 0 ? 0 : screenY / screenLength;
        double blendX = Math.sin(relativeBearingRad) * (1 - weight) + unitScreenX * weight;
        double blendY = -Math.cos(relativeBearingRad) * (1 - weight) + unitScreenY * weight;
        return Math.atan2(blendX, -blendY);
    }

    // ---- Proximity visuals (spec v1 §5.2) ----

    /**
     * Proximity {@code p = 1 - distance / detectionRadius}, clamped to [0, 1]. Drives
     * the arc length and opacity scaling; thickness and triangle size never scale.
     */
    public static double proximity(double distance, double detectionRadius) {
        return clamp(1.0 - distance / detectionRadius, 0.0, 1.0);
    }

    /** Arc length in degrees: {@link #MIN_ARC_DEG} at the radius edge to {@link #MAX_ARC_DEG} point-blank. */
    public static double arcLengthDeg(double proximity) {
        return MIN_ARC_DEG + (MAX_ARC_DEG - MIN_ARC_DEG) * proximity;
    }

    /** Half the arc length in radians - the arc is drawn centered on the orbit angle. */
    public static double halfArcRad(double proximity) {
        return Math.toRadians(arcLengthDeg(proximity) / 2.0);
    }

    /**
     * Edge fade: ramps 0 to 1 across the outermost {@link #EDGE_FADE_BLOCKS} blocks of
     * the Detection radius - the fade-in at the radius edge. Zero at and beyond the
     * radius, so a Threat at the edge is invisible.
     */
    public static double edgeFade(double distance, double detectionRadius) {
        return clamp((detectionRadius - distance) / EDGE_FADE_BLOCKS, 0.0, 1.0);
    }

    /** Final indicator opacity: {@code (0.3 + 0.7 p) x edge fade x config opacity}. */
    public static double opacity(double proximity, double edgeFade, double configOpacity) {
        return (MIN_OPACITY + PROXIMITY_OPACITY * proximity) * edgeFade * configOpacity;
    }

    /**
     * Far-to-near render ordering (spec v1 §5.2 stacking rule): co-directional Threats
     * overlap naturally and nearer Threats draw on top, so the renderer sorts Threats
     * with this comparator and draws in order. Returns a negative value when
     * {@code distanceA} is the farther of the two (drawn first).
     */
    public static int compareFarToNear(double distanceA, double distanceB) {
        return Double.compare(distanceB, distanceA);
    }

    /** Classic smoothstep, clamped: 0 at or below {@code edge0}, 1 at or above {@code edge1}. */
    private static double smoothstep(double edge0, double edge1, double x) {
        double t = clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
