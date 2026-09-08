package me.imyifeng.whosafterme.net;

/**
 * Wire constants of the threat sync protocol (spec v1 §4, ADR-0002). Pure values with
 * no Minecraft imports, so the version gate runs as plain JUnit (ADR-0004).
 */
public final class SyncProtocol {
    /**
     * The hello handshake's u8 protocol version (spec v1 §4). A future protocol
     * revision bumps this byte and renegotiates; a mismatch is logged and treated as
     * unmarked - the safe default.
     */
    public static final int PROTOCOL_VERSION = 1;

    /**
     * The only threat kind v1 sends (spec v1 §4). The per-entry byte is reserved so
     * post-v1 escalation levels (e.g. ATTACKING) need no protocol change.
     */
    public static final int TARGETING = 0;

    private SyncProtocol() {
    }

    /**
     * Whether a hello carrying {@code clientVersion} marks its sender: an exact match
     * with {@link #PROTOCOL_VERSION}, or nothing.
     */
    public static boolean accepts(int clientVersion) {
        return clientVersion == PROTOCOL_VERSION;
    }
}
