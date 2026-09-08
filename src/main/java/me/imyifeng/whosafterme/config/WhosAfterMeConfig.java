package me.imyifeng.whosafterme.config;

import eu.midnightdust.lib.config.MidnightConfig;

/**
 * The mod's config (spec v1 §7, ADR-0003): a single MidnightConfig class served to both
 * logical sides. MidnightConfig persists it as Gson JSON to {@code config/whos_after_me.json}
 * in whichever environment runs, so the server-read entries ({@link #detectionRadius},
 * {@link #pollInterval}) resolve from the server process's own file (ADR-0005 side
 * semantics). The config screen is auto-generated and opens via ModMenu when present.
 *
 * <p>Fields are {@code public static} - the required MidnightConfig pattern - and values
 * apply live: consumers (the HUD renderer, the server poll loop) read them at use time,
 * and MidnightConfig writes the file when its screen closes or the keybind flips
 * {@link #enabled}.
 *
 * <p>Entry order mirrors the spec v1 §7 table. Not configurable in v1: the indicator cap
 * (constant 8), the clear grace (constant 1500 ms), and the protocol mode.
 */
public class WhosAfterMeConfig extends MidnightConfig {
    /** Master HUD toggle; the toggle keybind flips it live. Sync continues while hidden. */
    @Entry
    public static boolean enabled = true;

    /** Aiming mode (spec v1 §5.3); both modes ship in v1. */
    @Entry
    public static AimingMode aimingMode = AimingMode.ABSOLUTE;

    /**
     * Detection radius in blocks (spec v1 §3). Read by the server process - on a dedicated
     * server by the server's own config file - and authoritative there (ADR-0005); the
     * server simply overrides a client whose copy disagrees. Editable in the file or the
     * screen (live on integrated servers); deliberately not marked {@code @Server}, which
     * would hide it from the screen entirely.
     */
    @Entry(min = 8, max = 128, isSlider = true)
    public static int detectionRadius = 32;

    /** Orbit radius as a percentage of the viewport half-dimensions (spec v1 §5.1). */
    @Entry(min = 25, max = 100, isSlider = true)
    public static int orbitSize = 78;

    /** Multiplies arc thickness (3 px) and triangle size (12 px) together (spec v1 §5.2). */
    @Entry(min = 0.5f, max = 3.0f, isSlider = true)
    public static float indicatorScale = 1.0f;

    /** Final multiplier on the computed indicator opacity (spec v1 §5.2). */
    @Entry(min = 0.1f, max = 1.0f, isSlider = true)
    public static float indicatorOpacity = 1.0f;

    /** Threat poll cadence in server ticks (spec v1 §3). Server-read, like {@link #detectionRadius}. */
    @Entry(min = 1, max = 40, isSlider = true)
    public static int pollInterval = 5;
}
