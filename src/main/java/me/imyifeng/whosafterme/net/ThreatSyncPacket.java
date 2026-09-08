package me.imyifeng.whosafterme.net;

import java.util.List;
import java.util.Set;

import me.imyifeng.whosafterme.WhosAfterMe;
import me.imyifeng.whosafterme.detection.ThreatDiff;
//? if mojang_identifier {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;*/
//?}
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The S2C threat_sync payload (spec v1 §4, ADR-0002): a u8 mode byte plus entries of
 * varint entity network id + threat-kind byte. One packet carries all changes from one
 * poll batch; the mode-selection rule lives in {@link #fromDiff}. v1 sends only
 * {@link SyncProtocol#TARGETING} entries - the kind byte is reserved for post-v1
 * escalation levels.
 */
public record ThreatSyncPacket(ThreatSyncMode mode, List<Entry> entries) implements CustomPacketPayload {

    /** One synced threat: the entity's network id plus its reserved threat-kind byte. */
    public record Entry(int entityId, int threatKind) {
    }

    private static final String CHANNEL = "threat_sync";

    //? if mojang_identifier {
    public static final CustomPacketPayload.Type<ThreatSyncPacket> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(WhosAfterMe.MOD_ID, CHANNEL));
    //?} else {
    /*public static final CustomPacketPayload.Type<ThreatSyncPacket> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WhosAfterMe.MOD_ID, CHANNEL));*/
    //?}

    /**
     * Delegates the variable-length body to {@link ThreatSyncWire}; the payload itself
     * is an opaque byte sequence to the transport.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ThreatSyncPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeBytes(ThreatSyncWire.encode(packet)),
            buf -> {
                byte[] bytes = new byte[buf.readableBytes()];
                buf.readBytes(bytes);
                return ThreatSyncWire.decode(bytes);
            });

    public ThreatSyncPacket {
        entries = List.copyOf(entries);
    }

    @Override
    public Type<ThreatSyncPacket> type() {
        return ID;
    }

    /**
     * The mode-selection rule for one poll batch (spec v1 §4): an empty diff sends
     * nothing; only additions map to ADD, only removals to REMOVE, and a mix to a
     * RESET carrying the full current set - which keeps "all changes from one poll in
     * one packet" true with a single mode byte. A forced full sync (handshake, world
     * change) always produces a RESET baseline - even an empty one - because the
     * client's current set is unknowable there.
     *
     * @return the packet to send, or {@code null} when nothing should be sent.
     */
    public static ThreatSyncPacket fromDiff(ThreatDiff diff, boolean fullSync) {
        if (!fullSync && diff.isEmpty()) {
            return null;
        }
        if (fullSync || (!diff.added().isEmpty() && !diff.removed().isEmpty())) {
            return of(ThreatSyncMode.RESET, diff.current());
        }
        if (!diff.added().isEmpty()) {
            return of(ThreatSyncMode.ADD, diff.added());
        }
        return of(ThreatSyncMode.REMOVE, diff.removed());
    }

    /** Builds a packet from a set of ids, ordered by id for deterministic wire output. */
    private static ThreatSyncPacket of(ThreatSyncMode mode, Set<Integer> entityIds) {
        List<Entry> entries = entityIds.stream()
                .sorted()
                .map(id -> new Entry(id, SyncProtocol.TARGETING))
                .toList();
        return new ThreatSyncPacket(mode, entries);
    }
}
