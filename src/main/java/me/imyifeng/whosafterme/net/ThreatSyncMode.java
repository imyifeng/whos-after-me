package me.imyifeng.whosafterme.net;

/**
 * The threat_sync mode byte (spec v1 §4, ADR-0002): all changes from one poll batch
 * travel in a single packet with one of three semantics. RESET replaces the client's
 * whole threat set (mixed batches, handshake, world changes); ADD / REMOVE carry only
 * the diff entries.
 */
public enum ThreatSyncMode {
    RESET(0),
    ADD(1),
    REMOVE(2);

    private final int wire;

    ThreatSyncMode(int wire) {
        this.wire = wire;
    }

    /** The u8 value this mode travels as. */
    public int wire() {
        return wire;
    }

    /**
     * Decodes a mode byte. An unknown byte is a protocol violation and is rejected at
     * decode time rather than decoded into a plausible-looking packet.
     */
    public static ThreatSyncMode ofWire(int wire) {
        for (ThreatSyncMode mode : values()) {
            if (mode.wire == wire) {
                return mode;
            }
        }
        throw new IllegalArgumentException(
                "malformed threat_sync payload: unknown mode byte " + wire + " (spec v1 §4 defines 0-2)");
    }
}
