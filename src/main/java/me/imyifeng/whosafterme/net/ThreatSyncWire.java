package me.imyifeng.whosafterme.net;

import java.io.ByteArrayOutputStream;

/**
 * The threat_sync wire format (spec v1 §4): a u8 mode byte, a varint entry count, then
 * per entry a varint entity network id plus a u8 threat-kind byte. Purely byte-level -
 * no Minecraft types - so round-trips and malformed-input rejection run as plain JUnit
 * (ADR-0004). The Fabric payload codec in {@link ThreatSyncPacket} delegates here.
 *
 * <p>Decoding validates structure before trusting it: unknown modes, truncated
 * payloads, over-long varints, and entry counts the payload cannot possibly contain
 * are all rejected, so a corrupt or hostile packet never becomes a nonsense packet.
 */
public final class ThreatSyncWire {
    /** Varints carry a signed 32-bit value in at most five 7-bit groups. */
    private static final int MAX_VARINT_BYTES = 5;

    /** Every entry costs at least one id byte plus one kind byte. */
    private static final int MIN_ENTRY_BYTES = 2;

    private ThreatSyncWire() {
    }

    /** Encodes a packet into exactly the spec v1 §4 byte layout. */
    public static byte[] encode(ThreatSyncPacket packet) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(packet.mode().wire());
        writeVarInt(out, packet.entries().size());
        for (ThreatSyncPacket.Entry entry : packet.entries()) {
            writeVarInt(out, entry.entityId());
            out.write(entry.threatKind());
        }
        return out.toByteArray();
    }

    /**
     * Decodes a packet from the spec v1 §4 byte layout.
     *
     * @throws IllegalArgumentException on any structural violation.
     */
    public static ThreatSyncPacket decode(byte[] bytes) {
        Reader in = new Reader(bytes);
        ThreatSyncMode mode = ThreatSyncMode.ofWire(in.readUnsignedByte("mode"));
        int count = in.readVarInt("entry count");
        if (count < 0 || count > in.remaining() / MIN_ENTRY_BYTES) {
            throw malformed("entry count " + count + " exceeds the " + in.remaining() + " remaining bytes");
        }
        ThreatSyncPacket.Entry[] entries = new ThreatSyncPacket.Entry[count];
        for (int i = 0; i < count; i++) {
            int entityId = in.readVarInt("entity id");
            int threatKind = in.readUnsignedByte("threat kind");
            entries[i] = new ThreatSyncPacket.Entry(entityId, threatKind);
        }
        return new ThreatSyncPacket(mode, java.util.List.of(entries));
    }

    private static void writeVarInt(ByteArrayOutputStream out, int value) {
        while ((value & ~0x7F) != 0) {
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.write(value);
    }

    private static IllegalArgumentException malformed(String problem) {
        return new IllegalArgumentException("malformed threat_sync payload: " + problem);
    }

    /** Cursor over one payload's bytes with structural validation. */
    private static final class Reader {
        private final byte[] bytes;
        private int position;

        Reader(byte[] bytes) {
            this.bytes = bytes;
        }

        int remaining() {
            return bytes.length - position;
        }

        int readUnsignedByte(String field) {
            if (position >= bytes.length) {
                throw malformed("truncated " + field);
            }
            return bytes[position++] & 0xFF;
        }

        int readVarInt(String field) {
            int value = 0;
            for (int group = 0; group < MAX_VARINT_BYTES; group++) {
                int b = readUnsignedByte("varint " + field);
                value |= (b & 0x7F) << (group * 7);
                if ((b & 0x80) == 0) {
                    return value;
                }
            }
            throw malformed("over-long varint in " + field);
        }
    }
}
