//? if client_gametest {
package me.imyifeng.whosafterme.gametest;

import net.fabricmc.fabric.api.client.gametest.v1.FabricClientGameTest;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;

/**
 * The client gametests (spec v1 §8, ADR-0004, ticket #31), registered under the
 * {@code fabric-client-gametest} entrypoint. The client gametest harness runs one
 * {@link FabricClientGameTest} per declared entrypoint, so the three scenarios of the
 * client suite live as sequential blocks inside a single {@link #runTest} call - each
 * scenario boots its own in-process dedicated server, keeping them as isolated as
 * separate entrypoints would.
 *
 * <p>The whole file is gated by the {@code client_gametest} workspace constant:
 * {@code fabric-client-gametest-api-v1} exists from 1.21.4 onward only, so on 1.21.1
 * this compilation unit is empty and the entrypoint key in the test mod descriptor is
 * never queried (nothing on that anchor ships a client gametest harness).
 *
 * <p>The scenarios assert the end-to-end path of the server-authoritative architecture
 * (ADR-0005): a real dedicated server runs the production detection engine, the client
 * connects like a player, and the polled-diff packets land in the client threat store -
 * see {@link ClientThreatSyncScenarios}. The screenshot smoke is additionally gated to
 * the 26.2 canary per ADR-0007.
 */
public class WhosAfterMeClientGameTests implements FabricClientGameTest {

    @Override
    public void runTest(ClientGameTestContext context) {
        // Spec §8: E2E ADD - a server-side Threat reaches the client threat store.
        ClientThreatSyncScenarios.e2eThreatReachesClientStore(context);
        // Spec §8: E2E REMOVE - the client threat store clears after the grace window.
        ClientThreatSyncScenarios.e2eThreatRemovalClearsClientStore(context);
        // Spec §8: screenshot smoke - the HUD actually draws. Canary-gated (ADR-0007).
        //? if client_gametest_canary {
        ClientThreatSyncScenarios.screenshotSmoke(context);
        //?}
    }
}
//?}
