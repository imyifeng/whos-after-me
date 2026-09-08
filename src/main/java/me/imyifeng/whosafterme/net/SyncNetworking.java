package me.imyifeng.whosafterme.net;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

/**
 * Payload registration for the threat sync protocol (spec v1 §4, ADR-0002). Called
 * from the common initializer so both logical sides know both payloads before any
 * receiver registration - the registration model of fabric-networking-api-v1 is
 * unchanged across the whole supported version range (research for issue #4); only
 * the registry accessor names fork at the Fabric API 26.1 rename era.
 */
public final class SyncNetworking {
    private SyncNetworking() {
    }

    /** Registers the hello (C2S) and threat_sync (S2C) payload codecs. */
    public static void register() {
        //? if fapi_modern_id {
        PayloadTypeRegistry.serverboundPlay().register(HelloPacket.ID, HelloPacket.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(ThreatSyncPacket.ID, ThreatSyncPacket.CODEC);
        //?} else {
        /*PayloadTypeRegistry.playC2S().register(HelloPacket.ID, HelloPacket.CODEC);
        PayloadTypeRegistry.playS2C().register(ThreatSyncPacket.ID, ThreatSyncPacket.CODEC);*/
        //?}
    }
}
