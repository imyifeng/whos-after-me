//? if client_gametest {
package me.imyifeng.whosafterme.gametest;

import java.util.List;
import java.util.Locale;

import com.mojang.brigadier.exceptions.CommandSyntaxException;

import me.imyifeng.whosafterme.client.store.ClientThreatStore;
import me.imyifeng.whosafterme.client.store.ClientThreats;
import me.imyifeng.whosafterme.config.WhosAfterMeConfig;
import me.imyifeng.whosafterme.detection.ClearGrace;
import me.imyifeng.whosafterme.net.SyncProtocol;
import net.fabricmc.fabric.api.client.gametest.v1.context.ClientGameTestContext;
import net.fabricmc.fabric.api.client.gametest.v1.context.TestDedicatedServerContext;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * The three client gametest scenarios (spec v1 §8, ADR-0004, ticket #31): the end-to-end
 * proof that a server-side Threat reaches the client, on the dedicated-server topology
 * that matches the server-authoritative architecture (ADR-0005). Each scenario boots an
 * in-process dedicated server running the production detection engine, connects the
 * gametest client to it like a real player, and drives a zombie through the vanilla
 * targeting entry point ({@code setTarget}). The E2E scenarios then wait for the polled
 * diff to land in the client threat store - the ADD wait is a bounded poll of the store,
 * which is exactly how the protocol converges (ADR-0002); the REMOVE wait additionally
 * waits out the wall-clock clear grace on the server thread, the same clock the engine
 * arms it with. The screenshot smoke additionally proves the HUD actually draws.
 *
 * <p>Every scenario stands on the same hermetic footing as the server gametests: the
 * harness's deterministic world defaults are overridden explicitly (mob spawning is
 * re-enabled around the summon - the override the defaults need - then restored, the
 * frozen clock moves to midnight, the scenario pins the Detection radius and a 1-tick
 * poll), the zombie is pinned in place with {@code setNoAi}, and its entity id is
 * captured at summon time so no stray natural spawn can enter the assertions.
 *
 * <p>This file is gated by the {@code client_gametest} workspace constant (the API is
 * absent from 1.21.1); the screenshot smoke is further gated to the 26.2 canary per
 * ADR-0007, keeping GPU-variance flake off the other anchors. Everything here compiles
 * against API members present on all five client-gametest anchors, so there are no
 * version forks in the scenario bodies.
 */
final class ClientThreatSyncScenarios {

    private ClientThreatSyncScenarios() {
    }

    /** Detection radius (blocks) pinned for the scenarios (the engine reads it live). */
    private static final int SCENARIO_RADIUS_BLOCKS = 16;

    /**
     * Blocks the zombie stands south of (+Z) the player: comfortably inside the pinned
     * Detection radius, close enough for a strong indicator in the smoke, far enough to
     * be a separate body on screen.
     */
    private static final double SPAWN_OFFSET_BLOCKS = 5.0;

    /**
     * Half-extent (blocks) of the box the summoned zombie is fetched with, around the
     * exact summon position: small enough that a stray natural spawn cannot enter the
     * assertion, wide enough for the summon's own placement snap.
     */
    private static final double SUMMON_TOLERANCE_BLOCKS = 1.0;

    /**
     * Tick budget for the ADD wait: the pinned 1-tick poll plus the packet round trip
     * need a handful of ticks; the rest is margin against connection warm-up.
     */
    private static final int THREAT_WAIT_TICKS = 200;

    /** Tick budget for the REMOVE wait (the grace has already been waited out on the clock). */
    private static final int REMOVAL_WAIT_TICKS = 100;

    /** Tick budget for the smoke's client-world entity resolution. */
    private static final int ENTITY_WAIT_TICKS = 200;

    /**
     * Ticks waited after {@code time set noon}: more than vanilla's 20-tick period time
     * sync, so the client has received and rendered the frozen noon sky for the smoke.
     */
    private static final int TIME_SYNC_TICKS = 25;

    /**
     * Wall-clock slack (ms) added on top of the 1500 ms grace, so the poll after the
     * sleep is safely past the deadline the engine armed (mirrors the server gametest).
     */
    private static final long GRACE_MARGIN_MS = 200;

    /** Camera yaw (degrees) for the smoke: 60° off the zombie's bearing puts the
     * indicator in the upper-left sky - clear of the unverified-chat toast that rides
     * the top-right of offline connections, and above the horizon. */
    private static final float SMOKE_YAW_DEG = 60.0f;
    private static final float SMOKE_PITCH_DEG = 0.0f;

    /** The smoke's template image under the test mod's {@code templates/} directory. */
    private static final String INDICATOR_TEMPLATE = "whos_after_me_threat_indicator_smoke";

    /**
     * Scenario 1 (spec §8): E2E ADD. A zombie targets the connected player on a real
     * dedicated server; the production poll detects it and the clientbound ADD must land
     * in the client threat store with the TARGETING threat kind - the full sync path of
     * the architecture diagram (spec §2), nothing mocked.
     */
    static void e2eThreatReachesClientStore(ClientGameTestContext context) {
        withConnectedThreat(context, (server, zombie) -> {
            int zombieId = zombie.getId();
            context.waitFor(client -> ClientThreats.store().contains(zombieId), THREAT_WAIT_TICKS);
            assertThreatOnClient(context, zombieId);
        });
    }

    /**
     * Scenario 2 (spec §8): E2E REMOVE. After the ADD is observed, the target clears;
     * the Threat must hold through the 1500 ms grace (ADR-0002) and then be removed from
     * the client threat store by a clientbound REMOVE. The grace is wall-clock while
     * gametest ticks run unbound from real time, so the wait-out happens on the clock on
     * the server thread - after one poll has observed the clear and armed the window.
     */
    static void e2eThreatRemovalClearsClientStore(ClientGameTestContext context) {
        withConnectedThreat(context, (server, zombie) -> {
            int zombieId = zombie.getId();
            context.waitFor(client -> ClientThreats.store().contains(zombieId), THREAT_WAIT_TICKS);
            assertThreatOnClient(context, zombieId);

            server.runOnServer(minecraftServer -> zombie.setTarget(null));
            // Let a poll observe the cleared target: that poll arms the grace window.
            context.waitTicks(2);
            waitOutGraceOnServer(server);

            context.waitFor(client -> !ClientThreats.store().contains(zombieId), REMOVAL_WAIT_TICKS);
            context.runOnClient(client -> {
                if (!ClientThreats.store().entries().isEmpty()) {
                    throw new AssertionError(
                            "the client threat store must be empty after the REMOVE, got "
                                    + ClientThreats.store().entries());
                }
            });
        });
    }

    /**
     * Scenario 3 (spec §8): screenshot smoke. With a live Threat dead ahead, the HUD
     * must actually draw its indicator - proven by locating the small indicator template
     * crop in a real screenshot. Confined to the 26.2 canary (ADR-0007): the pixel
     * assertion rides the experimental screenshot API and a GPU, so the other anchors
     * carry only the store-level E2E checks above. Per ADR-0004 there are no golden
     * images - a single small crop is the whole pixel budget of the suite.
     */
    //? if client_gametest_canary {
    static void screenshotSmoke(ClientGameTestContext context) {
        withConnectedThreat(context, (server, zombie) -> {
            // A noon sky behind the indicator: the ADD wait above ran in the helper's
            // frozen midnight, so this restores a bright, star-free backdrop before the
            // screenshot. The zombie survives the brief exposure (fire ticks are slow
            // next to the scenario's runtime), and its flames sit at screen center -
            // outside the indicator crop the assertion matches against.
            runServerCommand(server, "time set noon");
            context.waitTicks(TIME_SYNC_TICKS);
            // The HUD resolves synced ids against the client world; the entity itself
            // must have arrived before its indicator can draw.
            int zombieId = zombie.getId();
            context.waitFor(client -> client.level != null && client.level.getEntity(zombieId) != null,
                    ENTITY_WAIT_TICKS);

            // Aim 60° off the zombie (it stands at +Z): the Absolute orbit moves the
            // indicator 60° left of 12 o'clock, into the open sky - the predictable,
            // HUD-free spot the template crop covers (see SMOKE_YAW_DEG).
            context.runOnClient(client -> {
                client.player.setYRot(SMOKE_YAW_DEG);
                client.player.setXRot(SMOKE_PITCH_DEG);
            });

            context.assertScreenshotContains(INDICATOR_TEMPLATE);
        });
    }
    //?}

    /**
     * One scenario's hermetic footing: a fresh in-process dedicated server, the client
     * connected to it, the harness's deterministic defaults overridden (mob spawning is
     * re-enabled around the summon - the override the harness defaults need - and
     * re-disabled afterwards, so no stray natural spawn can enter the assertions),
     * Detection radius and poll cadence pinned, and a zombie targeting the connected
     * player. The scenario callback runs between the ADD wait and the teardown;
     * everything is released even when it fails.
     */
    private static void withConnectedThreat(ClientGameTestContext context, Scenario scenario) {
        try (TestDedicatedServerContext server = context.worldBuilder().createServer()) {
            // The connection type forks at 26.2 (TestDedicatedServerConnection, which
            // extends and AutoCloses TestServerConnection, returned there).
            try (var connection = server.connect()) {
                // The harness's consistent world defaults disable mob spawning
                // (SPAWN_MOBS=false); the scenario overrides that default around its own
                // summon (the override the harness needs, spec v1 section 8). The frozen
                // clock also moves to midnight: no sun burn can interrupt the scenario
                // (the smoke re-sets noon itself once its assertions run).
                setMobSpawning(server, true);
                runServerCommand(server, "time set midnight");
                int[] saved = pinServerConfig(server);
                try {
                    Mob zombie = summonTargetingZombie(context, server);
                    // The threat is now a commanded, NoAi-pinned mob, so the harness's
                    // deterministic default is restored: nothing natural can spawn into
                    // the REMOVE scenario's whole-store assertion below.
                    setMobSpawning(server, false);
                    context.waitFor(client -> ClientThreats.store().contains(zombie.getId()),
                            THREAT_WAIT_TICKS);
                    scenario.run(server, zombie);
                    server.runOnServer(minecraftServer -> zombie.discard());
                } finally {
                    restoreServerConfig(server, saved);
                }
            }
        }
    }

    /** The scenario callback: runs with the threat live on the connected client. */
    @FunctionalInterface
    private interface Scenario {
        void run(TestDedicatedServerContext server, Mob zombie);
    }

    /**
     * Asserts, on the client thread, that the synced entry is present with the one
     * threat kind v1 sends (spec §4) - the store assertion of both E2E scenarios.
     */
    private static void assertThreatOnClient(ClientGameTestContext context, int zombieId) {
        context.runOnClient(client -> {
            ClientThreatStore store = ClientThreats.store();
            if (!store.contains(zombieId) || store.kindOf(zombieId) != SyncProtocol.TARGETING) {
                throw new AssertionError("the client threat store must hold the synced Threat with the"
                        + " TARGETING kind, got " + store.entries());
            }
        });
    }

    /**
     * Flips the mob-spawning gamerule. The gamerule id forks at 26.1, where Mojang
     * renamed {@code doMobSpawning} to {@code spawn_mobs} (verified against the mapped
     * jars); the command's success is proven by {@link #runServerCommand}.
     */
    private static void setMobSpawning(TestDedicatedServerContext server, boolean enabled) {
        //? if spawn_gamerule_modern {
        runServerCommand(server, "gamerule spawn_mobs " + enabled);
        //?} else {
        /*runServerCommand(server, "gamerule doMobSpawning " + enabled);*/
        //?}
    }

    /**
     * Runs one server-console command and fails the scenario if the game rejected it -
     * the deterministic-defaults overrides (gamerule, clock, summon) must be proven to
     * have applied. Dispatched through brigadier directly, so every rejection (unknown
     * rule, bad syntax, failed precondition) surfaces as a {@code CommandSyntaxException}
     * on every anchor - the era's {@code Commands} wrappers either log silently or
     * changed their return type. The integer result is deliberately not judged: a
     * boolean gamerule set to {@code false} legitimately reports a zero result.
     */
    private static void runServerCommand(TestDedicatedServerContext server, String command) {
        server.computeOnServer(minecraftServer -> {
            try {
                return minecraftServer.getCommands().getDispatcher()
                        .execute(command, minecraftServer.createCommandSourceStack());
            } catch (CommandSyntaxException rejected) {
                throw new AssertionError("the server rejected the command: " + command, rejected);
            }
        });
    }

    /**
     * Pins the configured Detection radius and a 1-tick poll on the server (the engine
     * reads the config live each poll) and returns the previous values.
     */
    private static int[] pinServerConfig(TestDedicatedServerContext server) {
        int[] saved = new int[2];
        server.runOnServer(minecraftServer -> {
            saved[0] = WhosAfterMeConfig.detectionRadius;
            saved[1] = WhosAfterMeConfig.pollInterval;
            WhosAfterMeConfig.detectionRadius = SCENARIO_RADIUS_BLOCKS;
            WhosAfterMeConfig.pollInterval = 1;
        });
        return saved;
    }

    private static void restoreServerConfig(TestDedicatedServerContext server, int[] saved) {
        server.runOnServer(minecraftServer -> {
            WhosAfterMeConfig.detectionRadius = saved[0];
            WhosAfterMeConfig.pollInterval = saved[1];
        });
    }

    /**
     * Summons a zombie a fixed offset due south of (+Z) the connected player through the
     * era-stable {@code /summon} command and pins it in place: no AI (no pathfinding
     * drift, no gravity) and the player as its target through the vanilla targeting
     * entry point. Returns the server-side mob.
     */
    private static Mob summonTargetingZombie(ClientGameTestContext context, TestDedicatedServerContext server) {
        Vec3 summonAt = server.computeOnServer(minecraftServer -> {
            ServerPlayer player = connectedPlayer(minecraftServer);
            return player.position().add(0.0, 0.0, SPAWN_OFFSET_BLOCKS);
        });
        runServerCommand(server, String.format(Locale.ROOT, "summon minecraft:zombie %s %s %s",
                summonAt.x, summonAt.y, summonAt.z));
        return server.computeOnServer(minecraftServer -> {
            ServerLevel level = minecraftServer.overworld();
            ServerPlayer player = connectedPlayer(minecraftServer);
            List<Mob> summoned = level.getEntitiesOfClass(Mob.class,
                    new AABB(summonAt, summonAt).inflate(
                            SUMMON_TOLERANCE_BLOCKS, SUMMON_TOLERANCE_BLOCKS, SUMMON_TOLERANCE_BLOCKS));
            if (summoned.size() != 1) {
                throw new AssertionError(
                        "expected exactly one summoned mob at the player's +Z offset, got " + summoned.size());
            }
            Mob zombie = summoned.get(0);
            zombie.setNoAi(true);
            zombie.setTarget(player);
            return zombie;
        });
    }

    /** The gametest client's server-side player (the connection's only player). */
    private static ServerPlayer connectedPlayer(MinecraftServer server) {
        List<ServerPlayer> players = server.getPlayerList().getPlayers();
        if (players.isEmpty()) {
            throw new AssertionError("the gametest client's server player is missing");
        }
        return players.get(0);
    }

    /**
     * Waits out the clear grace on the server thread - the wall clock the engine arms
     * the grace with. Gametest ticks run unbound from real time, so no tick count is a
     * reliable measure of elapsed wall time (the server gametests sleep on the same
     * clock); the caller must already have let one poll arm the window.
     */
    private static void waitOutGraceOnServer(TestDedicatedServerContext server) {
        server.runOnServer(minecraftServer -> {
            long deadline = System.currentTimeMillis() + ClearGrace.GRACE_MS + GRACE_MARGIN_MS;
            long remaining;
            while ((remaining = deadline - System.currentTimeMillis()) > 0) {
                try {
                    Thread.sleep(remaining);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException("interrupted while waiting out the grace window", interrupted);
                }
            }
        });
    }
}
//?}
