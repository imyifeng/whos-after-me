package me.imyifeng.whosafterme.net;

import java.util.LinkedHashMap;
import java.util.Map;

import me.imyifeng.whosafterme.WhosAfterMe;
import me.imyifeng.whosafterme.client.store.ClientThreats;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client side of the threat sync protocol (spec v1 §4, ADR-0002): announces the
 * protocol version on play-phase join and receives threat_sync. Every payload handler
 * hops from the Netty thread to the client thread before touching world or render
 * state (ADR-0002); behavior is identical against integrated and dedicated servers
 * because the same registration runs wherever the client logical side loads.
 *
 * <p>The payload is applied to the client threat store (spec v1 §5.4), keyed by
 * entity network id: RESET replaces the store's whole set, ADD / REMOVE carry only
 * the diff entries. The store's own lifecycle (dimension change, respawn, disconnect)
 * lives in {@link ClientThreats}, not here - this class is protocol only.
 */
@Environment(EnvType.CLIENT)
public final class ClientSync {
    private ClientSync() {
    }

    /** Registers the handshake sender and the threat_sync receiver. Called once from the client initializer. */
    public static void register() {
        // Play-phase join: announce the protocol version (spec v1 §4). A vanilla
        // server ignores the unknown channel; a modded one answers with a RESET.
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) ->
                sender.sendPacket(HelloPacket.INSTANCE));

        ClientPlayNetworking.registerGlobalReceiver(ThreatSyncPacket.ID, (packet, context) ->
                // The thread hop is protocol behavior (ADR-0002): the threat store is
                // only ever updated on the client thread.
                context.client().execute(() -> apply(packet)));
    }

    /** Applies one sync packet to the client threat store on the client thread. */
    private static void apply(ThreatSyncPacket packet) {
        Map<Integer, Integer> entries = new LinkedHashMap<>();
        for (ThreatSyncPacket.Entry entry : packet.entries()) {
            entries.put(entry.entityId(), entry.threatKind());
        }
        ClientThreats.store().apply(packet.mode(), entries);
        WhosAfterMe.LOGGER.debug("threat_sync {}: {} entries", packet.mode(), packet.entries().size());
    }
}
