package me.imyifeng.whosafterme.detection;

/**
 * The Detection-radius predicate (spec v1 §3, ADR-0001): a mob qualifies as a Threat for
 * an observing player only when its position is within the Detection radius of the
 * player's position, measured as a 3D Euclidean distance. The radius-bounded box query
 * in {@code ThreatDetector} is only a superset filter; this predicate is the exact
 * structural rule, so out-of-radius mobs never qualify.
 *
 * <p>Pure functions over plain numbers with no Minecraft classes, so the rule runs as
 * plain JUnit in {@code check} on every Anchor (ADR-0004).
 */
public final class ThreatRules {

    private ThreatRules() {
    }

    /**
     * Whether a point offset from the observing player by ({@code dx}, {@code dy},
     * {@code dz}) - mob position minus player position, in blocks - lies within the
     * Detection radius. The distance is Euclidean in 3D (height counts), and a point at
     * exactly the radius counts as within.
     */
    public static boolean withinRadius(double dx, double dy, double dz, double radiusBlocks) {
        return dx * dx + dy * dy + dz * dz <= radiusBlocks * radiusBlocks;
    }
}
