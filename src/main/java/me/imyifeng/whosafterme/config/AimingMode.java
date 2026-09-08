package me.imyifeng.whosafterme.config;

/**
 * Aiming mode (spec v1 §5.3, CONTEXT.md): how a threat's direction maps onto the orbit.
 *
 * <p>{@code ABSOLUTE} is the default: the orbit reads like a ring around the player from
 * above, pitch ignored (compass bearing). {@code SCREEN_RELATIVE} projects threats through
 * the camera's pitch and FOV. Both ship in v1.
 */
public enum AimingMode {
    ABSOLUTE,
    SCREEN_RELATIVE
}
