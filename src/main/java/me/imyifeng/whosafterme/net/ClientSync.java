package me.imyifeng.whosafterme.net;

import me.imyifeng.whosafterme.WhosAfterMe;
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
                // The thread hop is protocol behavior and lives here so the store
                // (ticket #28) can never be updated off-thread (ADR-0002).
                context.client().execute(() -> apply(packet)));
    }

    /**
     * Applies one sync packet on the client thread. Stub for ticket #28: it replaces
     * this body with the threat-store application (RESET/ADD/REMOVE keyed by network
     * id); the protocol needs nothing more from it.
     */
    private static void apply(ThreatSyncPacket packet) {
        WhosAfterMe.LOGGER.debug("threat_sync {}: {} entries", packet.mode(), packet.entries().size());
    }
}
