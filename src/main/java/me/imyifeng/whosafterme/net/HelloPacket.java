package me.imyifeng.whosafterme.net;

import me.imyifeng.whosafterme.WhosAfterMe;
//? if mojang_identifier {
import net.minecraft.resources.Identifier;
//?} else {
/*import net.minecraft.resources.ResourceLocation;*/
//?}
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The C2S hello handshake payload (spec v1 §4, ADR-0002): a single u8 protocol
 * version. The client sends it on play-phase join; the server marks the player modded
 * only on a version match ({@link SyncProtocol#accepts}) and immediately answers with
 * a full-state RESET. A vanilla server ignores the unknown channel, which is why a
 * modded client on a vanilla server behaves as vanilla (spec v1 §2).
 */
public record HelloPacket(int protocolVersion) implements CustomPacketPayload {

    /** The one packet v1 sends; the version never varies within a protocol generation. */
    public static final HelloPacket INSTANCE = new HelloPacket(SyncProtocol.PROTOCOL_VERSION);

    private static final String CHANNEL = "hello";

    //? if mojang_identifier {
    public static final CustomPacketPayload.Type<HelloPacket> ID =
            new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(WhosAfterMe.MOD_ID, CHANNEL));
    //?} else {
    /*public static final CustomPacketPayload.Type<HelloPacket> ID =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WhosAfterMe.MOD_ID, CHANNEL));*/
    //?}

    /** The u8 version field (spec v1 §4 handshake table). */
    public static final StreamCodec<RegistryFriendlyByteBuf, HelloPacket> CODEC = StreamCodec.ofMember(
            (packet, buf) -> buf.writeByte(packet.protocolVersion()),
            buf -> new HelloPacket(buf.readUnsignedByte()));

    @Override
    public Type<HelloPacket> type() {
        return ID;
    }
}
