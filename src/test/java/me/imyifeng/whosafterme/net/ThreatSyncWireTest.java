package me.imyifeng.whosafterme.net;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Threat-sync wire codec tests (spec v1 §4, ADR-0004): the payload bytes round-trip
 * through every mode, the exact byte layout matches the spec table, and malformed
 * payloads are rejected instead of decoded into nonsense. Plain JUnit - the wire
 * format needs no Minecraft classes (ADR-0004).
 */
class ThreatSyncWireTest {

    private static ThreatSyncPacket packet(ThreatSyncMode mode, int... entityIds) {
        List<ThreatSyncPacket.Entry> entries = java.util.Arrays.stream(entityIds)
                .mapToObj(id -> new ThreatSyncPacket.Entry(id, SyncProtocol.TARGETING))
                .toList();
        return new ThreatSyncPacket(mode, entries);
    }

    @Test
    void resetRoundTrips() {
        ThreatSyncPacket original = packet(ThreatSyncMode.RESET, 4, 9, 300);
        assertEquals(original, ThreatSyncWire.decode(ThreatSyncWire.encode(original)));
    }

    @Test
    void addRoundTrips() {
        ThreatSyncPacket original = packet(ThreatSyncMode.ADD, 7);
        assertEquals(original, ThreatSyncWire.decode(ThreatSyncWire.encode(original)));
    }

    @Test
    void removeRoundTrips() {
        ThreatSyncPacket original = packet(ThreatSyncMode.REMOVE, 12, 34);
        assertEquals(original, ThreatSyncWire.decode(ThreatSyncWire.encode(original)));
    }

    @Test
    void emptyResetRoundTrips() {
        // The handshake RESET for a player with no threats carries zero entries.
        ThreatSyncPacket original = packet(ThreatSyncMode.RESET);
        assertEquals(original, ThreatSyncWire.decode(ThreatSyncWire.encode(original)));
    }

    @Test
    void multiByteVarintIdsRoundTrip() {
        // Entity network ids grow with the world's entity count; the varint must carry
        // the full unsigned 31-bit range Entity.getId() can produce.
        ThreatSyncPacket original = packet(ThreatSyncMode.ADD, 127, 128, 16383, 16384, 65535, Integer.MAX_VALUE);
        assertEquals(original, ThreatSyncWire.decode(ThreatSyncWire.encode(original)));
    }

    @Test
    void wireLayoutMatchesSpecTable() {
        // spec v1 §4: mode u8, count varint, then per entry varint id + kind u8.
        // ADD of ids 300 and 1 (both TARGETING) must encode to exactly these bytes:
        // 01 | 02 | AC 02 00 | 01 00
        assertArrayEquals(
                new byte[] {0x01, 0x02, (byte) 0xAC, 0x02, 0x00, 0x01, 0x00},
                ThreatSyncWire.encode(packet(ThreatSyncMode.ADD, 300, 1)));
        assertArrayEquals(
                new byte[] {0x00, 0x02, 0x05, 0x00, 0x06, 0x00},
                ThreatSyncWire.encode(packet(ThreatSyncMode.RESET, 5, 6)));
        assertArrayEquals(
                new byte[] {0x02, 0x01, 0x07, 0x00},
                ThreatSyncWire.encode(packet(ThreatSyncMode.REMOVE, 7)));
    }

    @Test
    void unknownModeByteIsRejected() {
        // Malformed input must not decode into a plausible-looking packet: an unknown
        // mode is a protocol violation (post-v1 modes need a new byte value).
        assertThrows(IllegalArgumentException.class,
                () -> ThreatSyncWire.decode(new byte[] {0x03, 0x00}));
    }

    @Test
    void truncatedPayloadIsRejected() {
        // Count claims two entries but the second entry's kind byte is missing.
        assertThrows(IllegalArgumentException.class,
                () -> ThreatSyncWire.decode(new byte[] {0x01, 0x02, (byte) 0xAC, 0x02, 0x00}));
    }

    @Test
    void entryCountLargerThanPayloadIsRejected() {
        // A hostile count (2^27 here) must not make the decoder allocate entries that
        // the payload cannot possibly contain: every entry needs at least two bytes.
        assertThrows(IllegalArgumentException.class, () -> ThreatSyncWire.decode(
                new byte[] {0x01, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x40}));
    }

    @Test
    void overlongVarintIsRejected() {
        // Varints are at most five bytes (31 bits); more continuation bytes are garbage.
        byte[] payload = {0x01, 0x01, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, (byte) 0x80, 0x01, 0x00};
        assertThrows(IllegalArgumentException.class, () -> ThreatSyncWire.decode(payload));
    }
}
