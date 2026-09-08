package me.imyifeng.whosafterme.client.hud;

/**
 * The Orbit (CONTEXT.md): the viewport-centered ellipse matched to the viewport's
 * aspect ratio - a circle at 1:1 - on which Threat indicators are drawn (spec v1 §5.1).
 * The radii are {@code orbitSize% x viewportWidth/2} and {@code orbitSize% x
 * viewportHeight/2}, with the percentage read from the config each frame.
 *
 * <p>Pure geometry, no Minecraft classes: plain numbers in, plain numbers out, so the
 * renderer stays thin (spec v1 §11 ticket 4). All angles are radians in the module's
 * screen convention: 0 = top of the orbit (dead ahead), positive = clockwise on screen
 * (to the right), matching the prototype resolution (decision #7).
 *
 * @param centerX    viewport x of the ellipse center (pixels)
 * @param centerY    viewport y of the ellipse center (pixels)
 * @param radiusX    horizontal radius (pixels)
 * @param radiusY    vertical radius (pixels)
 */
public record Orbit(double centerX, double centerY, double radiusX, double radiusY) {

    /**
     * Builds the viewport-matched Orbit (spec v1 §5.1): centered on the viewport,
     * radii scaled by {@code orbitSizePercent} of the viewport half-dimensions.
     * Viewport dimensions are the GUI-scaled resolution.
     */
    public static Orbit of(double viewportWidth, double viewportHeight, double orbitSizePercent) {
        double scale = orbitSizePercent / 100.0;
        return new Orbit(
                viewportWidth / 2.0,
                viewportHeight / 2.0,
                viewportWidth / 2.0 * scale,
                viewportHeight / 2.0 * scale);
    }

    /**
     * Screen x of the Orbit point at {@code angleRad} - the Absolute-mode placement,
     * which parameterizes the ellipse by orbit angle (Screen-relative mode instead uses
     * {@link #radiusAlong}).
     */
    public double xAt(double angleRad) {
        return centerX + radiusX * Math.sin(angleRad);
    }

    /**
     * Screen y of the Orbit point at {@code angleRad}; screen y grows downward, so the
     * top of the orbit (angle 0) sits above the center.
     */
    public double yAt(double angleRad) {
        return centerY - radiusY * Math.cos(angleRad);
    }

    /**
     * Distance from the center to the Orbit boundary along the screen direction of
     * {@code angleRad} - the Screen-relative placement, where the indicator must sit on
     * the ray from the view center through the Threat's projected position, so
     * co-directional Threats and their triangles point along the actual view direction.
     */
    public double radiusAlong(double angleRad) {
        double unitX = Math.sin(angleRad) / radiusX;
        double unitY = Math.cos(angleRad) / radiusY;
        return 1.0 / Math.hypot(unitX, unitY);
    }
}
