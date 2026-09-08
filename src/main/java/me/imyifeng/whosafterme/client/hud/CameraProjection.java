package me.imyifeng.whosafterme.client.hud;

/**
 * The Screen-relative camera projection (spec v1 §5.3): where a world-space offset from
 * the camera lands on the viewport when pushed through the camera's yaw, pitch and FOV.
 * Feeds {@link OrbitGeometry#screenRelativeOrbitAngle}, which blends this screen
 * direction with the compass bearing per the prototype-locked contract (decision #7).
 *
 * <p>Pure math, no Minecraft classes: the renderer supplies plain numbers - camera
 * yaw/pitch/FOV in radians, viewport size in GUI-scaled pixels, and the Threat's offset
 * from the camera in world blocks. The FOV is the game setting's full angle, spanning
 * the viewport width; the vertical FOV is narrowed by the aspect ratio.
 *
 * <p>Camera frame (mirroring the locked prototype): yaw 0 faces -z, positive yaw turns
 * toward +x, positive pitch looks up; {@code dx}/{@code dy}/{@code dz} are the Threat's
 * world offset from the camera, y up.
 *
 * @param offsetX            screen x of the projected point relative to the view center (pixels, right positive)
 * @param offsetY            screen y of the projected point relative to the view center (pixels, down positive)
 * @param normalizedDistance distance from the view center in viewport half-extents (0 = centered, 1 = screen edge)
 * @param behind             whether the point is behind the camera plane
 */
public record CameraProjection(double offsetX, double offsetY, double normalizedDistance, boolean behind) {

    /**
     * Floor on the projected forward component, from the prototype: near the view plane
     * it keeps the projection finite, and behind the camera it preserves the sign of the
     * lateral offsets - so Threats behind the camera keep pointing the way the player
     * would turn instead of flipping wildly.
     */
    private static final double MIN_FORWARD = 0.12;

    /**
     * Projects the world offset {@code (dx, dy, dz)} through the camera
     * {@code (yawRad, pitchRad, fovRad)} onto the {@code viewportWidth x viewportHeight}
     * viewport. See the class docs for units and frame conventions.
     */
    public static CameraProjection project(
            double yawRad, double pitchRad, double fovRad,
            double viewportWidth, double viewportHeight,
            double dx, double dy, double dz) {

        double cosYaw = Math.cos(yawRad);
        double sinYaw = Math.sin(yawRad);
        double cosPitch = Math.cos(pitchRad);
        double sinPitch = Math.sin(pitchRad);

        // Camera basis, prototype-locked: forward from yaw+pitch, right on the ground
        // plane, up as their cross product.
        double fwdX = sinYaw * cosPitch;
        double fwdY = sinPitch;
        double fwdZ = -cosYaw * cosPitch;
        double rgtX = cosYaw;
        double rgtZ = sinYaw;
        double upX = -sinYaw * sinPitch;
        double upY = cosPitch;
        double upZ = cosYaw * sinPitch;

        double f = dx * fwdX + dy * fwdY + dz * fwdZ;
        double r = dx * rgtX + dz * rgtZ;
        double u = dx * upX + dy * upY + dz * upZ;

        double depth = Math.max(Math.abs(f), MIN_FORWARD);
        double tanHalfFov = Math.tan(fovRad / 2.0);
        double tanHalfVerticalFov = tanHalfFov * viewportHeight / viewportWidth;

        double ndcX = (r / depth) / tanHalfFov;
        double ndcY = (u / depth) / tanHalfVerticalFov;

        return new CameraProjection(
                ndcX * viewportWidth / 2.0,
                -ndcY * viewportHeight / 2.0,
                Math.hypot(ndcX, ndcY),
                f < 0);
    }
}
