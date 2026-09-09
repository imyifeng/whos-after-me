package me.imyifeng.whosafterme.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import me.imyifeng.whosafterme.config.WhosAfterMeConfig;
import me.imyifeng.whosafterme.detection.ClearGrace;
import me.imyifeng.whosafterme.detection.ThreatDetector;
import me.imyifeng.whosafterme.detection.ThreatDiff;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

// 26.2 moved the built-in entity type constants from `EntityType` to a new `EntityTypes`
// holder (verified against the mapped jars; 26.1.2 still has them on `EntityType`). The
// spawn factories at the bottom are the only forked members - every scenario body is
// era-neutral, so the per-version test-source split stays confined to the annotation
// fork in WhosAfterMeGameTests (spec v1 §8).
//? if entity_types_modern {
import net.minecraft.world.entity.EntityTypes;
//?} else {
/*import net.minecraft.world.entity.EntityType;*/
//?}

/**
 * The six server gametest scenarios (spec v1 §8, ADR-0004, ticket #30): real mobs driven
 * through the game's own targeting entry points, observed by a real mock-profile
 * {@link ServerPlayer} ({@code GameTestHelper.makeMockServerPlayerInLevel}, present on
 * every Anchor), and judged by the production detection engine via
 * {@link ThreatDetector#observe(ServerPlayer)} - the exact poll the END_SERVER_TICK hook
 * runs, minus the hello gate the mock observers never pass.
 *
 * <p>Every scenario stands on the same hermetic footing: it pins the configured Detection
 * radius (the engine reads it live), anchors the observer at a fixed structure-relative
 * position, and spawns mobs at known offsets, so the 3D distances are exact at poll time.
 * Scenario state is isolated because each mock observer carries a random UUID and every
 * mob is discarded before the scenario ends.
 *
 * <p>Everything here compiles against {@code GameTestHelper} members present on all six
 * anchors; there are no version forks in the scenario bodies.
 */
final class ThreatDetectionScenarios {

    private ThreatDetectionScenarios() {
    }

    /** Structure-relative anchor: where the observing player stands in every scenario. */
    private static final BlockPos OBSERVER_POS = new BlockPos(4, 1, 4);

    /** Detection radius (blocks) for the in-radius scenarios. */
    private static final int IN_RADIUS_BLOCKS = 16;

    /**
     * Detection radius (blocks) for the out-of-radius scenario: the mob sits at a diagonal
     * of about 3.54 blocks - inside the engine's radius-inflated box query but outside the
     * exact 3D radius predicate, so the predicate itself is what excludes it.
     */
    private static final int OUT_RADIUS_BLOCKS = 3;

    /**
     * Wall-clock slack (ms) added on top of the 1500 ms grace before the drop poll, so
     * the poll's own clock read is safely past the deadline the engine armed.
     */
    private static final long GRACE_MARGIN_MS = 200;

    /**
     * Scenario 1 (spec §8): a melee mob (zombie) targeting the observer through the
     * vanilla targeting entry point - {@code setTarget}, what its attack goal calls -
     * must be detected as a Threat.
     */
    static void meleeTargetingMobIsThreat(GameTestHelper helper) {
        withDetectionRadius(helper, IN_RADIUS_BLOCKS, () -> {
            ServerPlayer observer = observer(helper);
            Mob zombie = spawnZombie(helper, new BlockPos(7, 1, 4));
            zombie.setNoAi(true);
            zombie.setTarget(observer);
            ThreatDiff diff = ThreatDetector.observe(observer);
            assertTrue(helper, diff.added().contains(zombie.getId()),
                    "a melee mob targeting the observer must be detected as a Threat, got " + diff);
            zombie.discard();
            release(helper, observer);
        });
        helper.succeed();
    }

    /**
     * Scenario 2 (spec §8): a brain-memory mob (piglin) whose target is stored only in
     * the brain's {@code ATTACK_TARGET} memory - the fallback read of the uniform rule
     * (ADR-0001) - must be detected as a Threat.
     */
    static void brainMemoryMobIsThreat(GameTestHelper helper) {
        withDetectionRadius(helper, IN_RADIUS_BLOCKS, () -> {
            ServerPlayer observer = observer(helper);
            Mob piglin = spawnPiglin(helper, new BlockPos(4, 1, 7));
            piglin.setNoAi(true);
            ThreatDiff before = ThreatDetector.observe(observer);
            assertTrue(helper, before.current().isEmpty(),
                    "a brain mob without any target must not be a Threat, got " + before);
            // Store the target exclusively in the brain memory, not via setTarget, so
            // only the ATTACK_TARGET path can qualify the mob.
            piglin.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, observer);
            ThreatDiff after = ThreatDetector.observe(observer);
            assertTrue(helper, after.added().contains(piglin.getId()),
                    "a mob targeting through the ATTACK_TARGET brain memory must be detected as a Threat, got " + after);
            piglin.discard();
            release(helper, observer);
        });
        helper.succeed();
    }

    /**
     * Scenario 3 (spec §8): a provoked neutral (bee) must not be a Threat while calm and
     * must become one once angered. The provocation is a real player melee attack; the
     * angered state is then expressed through the same vanilla targeting entry the mob's
     * own retaliation goal calls, and the mob's persistent-anger AI must hold the target
     * on its own - so the wait loop gives it ticks to validate and re-arm.
     */
    static void provokedNeutralIsThreatOnceAngered(GameTestHelper helper) {
        int savedRadius = pinDetectionRadius(IN_RADIUS_BLOCKS);
        ServerPlayer observer = observer(helper);
        Mob bee = spawnBee(helper, new BlockPos(4, 2, 4));
        ThreatDiff before = ThreatDetector.observe(observer);
        assertTrue(helper, before.current().isEmpty(),
                "an unprovoked neutral must not be a Threat, got " + before);
        observer.attack(bee);
        bee.setTarget(observer);
        helper.startSequence()
                .thenWaitUntil(() -> assertSoon(helper, bee.getTarget() == observer,
                        "the provoked neutral must target the player once angered"))
                .thenExecute(() -> {
                    ThreatDiff diff = ThreatDetector.observe(observer);
                    assertTrue(helper, diff.added().contains(bee.getId()),
                            "a provoked neutral targeting the player must be detected as a Threat, got " + diff);
                    bee.discard();
                    release(helper, observer);
                    restoreDetectionRadius(savedRadius);
                })
                .thenSucceed();
    }

    /**
     * Scenario 4 (spec §8): a ranged mob (skeleton) targeting the observer must be
     * detected as a Threat - the uniform read has no per-mob whitelist (ADR-0001).
     */
    static void rangedMobIsThreat(GameTestHelper helper) {
        withDetectionRadius(helper, IN_RADIUS_BLOCKS, () -> {
            ServerPlayer observer = observer(helper);
            Mob skeleton = spawnSkeleton(helper, new BlockPos(4, 1, 7));
            skeleton.setNoAi(true);
            skeleton.setTarget(observer);
            ThreatDiff diff = ThreatDetector.observe(observer);
            assertTrue(helper, diff.added().contains(skeleton.getId()),
                    "a ranged mob targeting the observer must be detected as a Threat, got " + diff);
            skeleton.discard();
            release(helper, observer);
        });
        helper.succeed();
    }

    /**
     * Scenario 5 (spec §8): a mob beyond the Detection radius must not be a Threat even
     * while targeting - the box query is only a superset filter; the exact 3D radius
     * predicate excludes it (spec §3, ADR-0001).
     */
    static void outOfRadiusMobIsNoThreat(GameTestHelper helper) {
        withDetectionRadius(helper, OUT_RADIUS_BLOCKS, () -> {
            ServerPlayer observer = observer(helper);
            Mob zombie = spawnZombie(helper, new BlockPos(7, 1, 7));
            zombie.setNoAi(true);
            zombie.setTarget(observer);
            ThreatDiff diff = ThreatDetector.observe(observer);
            assertTrue(helper, diff.current().isEmpty(),
                    "a mob beyond the Detection radius must not be a Threat even while targeting, got " + diff);
            zombie.discard();
            release(helper, observer);
        });
        helper.succeed();
    }

    /**
     * Scenario 6 (spec §8): a cleared target holds its Threat status through the 1500 ms
     * grace window and is dropped once it lapses (spec §3, ADR-0002). The first poll adds
     * the Threat, the immediate second poll must keep it (the clear opens the grace), and
     * a poll after the window must remove it. The wait is conditioned on the wall clock -
     * the engine arms the grace with {@code System.currentTimeMillis()}, and gametest
     * servers tick faster or slower than 20 tps depending on the host, so tick counts are
     * not a reliable measure of elapsed wall time. The mob is pinned with {@code setNoAi}
     * so its own goals cannot re-acquire the target mid-window.
     */
    static void clearedTargetHoldsThroughGraceThenDrops(GameTestHelper helper) {
        int savedRadius = pinDetectionRadius(IN_RADIUS_BLOCKS);
        ServerPlayer observer = observer(helper);
        Mob zombie = spawnZombie(helper, new BlockPos(7, 1, 4));
        zombie.setNoAi(true);
        zombie.setTarget(observer);
        ThreatDiff added = ThreatDetector.observe(observer);
        assertTrue(helper, added.added().contains(zombie.getId()),
                "precondition failed: the mob must be a Threat before its target clears, got " + added);
        zombie.setTarget(null);
        long clearedAtMs = System.currentTimeMillis();
        ThreatDiff held = ThreatDetector.observe(observer);
        assertTrue(helper, held.isEmpty() && held.current().contains(zombie.getId()),
                "a cleared target must hold its Threat status through the grace window, got " + held);
        long graceDeadlineMs = clearedAtMs + ClearGrace.GRACE_MS + GRACE_MARGIN_MS;
        helper.startSequence()
                .thenExecute(() -> pauseUntil(graceDeadlineMs))
                .thenExecute(() -> {
                    ThreatDiff dropped = ThreatDetector.observe(observer);
                    assertTrue(helper, dropped.removed().contains(zombie.getId()),
                            "after the grace window the cleared target must be dropped, got " + dropped);
                    zombie.discard();
                    release(helper, observer);
                    restoreDetectionRadius(savedRadius);
                })
                .thenSucceed();
    }

    /**
     * Pauses the (headless) server thread until the given wall-clock instant. The engine
     * arms the grace with {@code System.currentTimeMillis()}, and gametest servers tick
     * far faster than 20 tps, so no tick count is a reliable measure of elapsed wall
     * time - the wait must be on the clock itself.
     */
    private static void pauseUntil(long deadlineMs) {
        long remainingMs;
        while ((remainingMs = deadlineMs - System.currentTimeMillis()) > 0) {
            try {
                Thread.sleep(remainingMs);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("interrupted while waiting for the grace window", interrupted);
            }
        }
    }

    /**
     * A mock-profile {@link ServerPlayer} registered in the server's player list
     * (random UUID, so every scenario's tracker state is independent), positioned at the
     * fixed structure-relative anchor.
     */
    private static ServerPlayer observer(GameTestHelper helper) {
        ServerPlayer observer = spawnObserver(helper);
        observer.setPos(Vec3.atBottomCenterOf(helper.absolutePos(OBSERVER_POS)));
        return observer;
    }

    //? if modern_mock_player {
    /**
     * 26.1 turned the mob target read into a validated one ({@code Mob.setTarget} drops
     * creative and spectator players), while vanilla's in-level mock player helper is
     * creative by definition - a mob could never target it. The observer is therefore a
     * survival player built and registered here exactly the way the vanilla helper does
     * it: constructed against the test level, then placed into the server through its
     * player list over an embedded channel.
     */
    private static ServerPlayer spawnObserver(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        GameProfile profile = new GameProfile(UUID.randomUUID(), "whos-after-me-observer");
        ServerPlayer observer = new ServerPlayer(level.getServer(), level, profile, ClientInformation.createDefault()) {
            @Override
            public GameType gameMode() {
                return GameType.SURVIVAL;
            }
        };
        // Vanilla's own helper applies the game type to the ability flags before
        // registration; without this the player keeps flagged abilities and is not seen
        // as an enemy by the mob target read.
        GameType.SURVIVAL.updatePlayerAbilities(observer.getAbilities());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, observer, CommonListenerCookie.createInitial(profile, false));
        return observer;
    }
    //?} else {
    /*
    private static ServerPlayer spawnObserver(GameTestHelper helper) {
        // The in-level mock player helper registers the player in the level and the
        // server's player list, in the default survival mode - everything the scenarios
        // and the vanilla anger resolution need. (Deprecated on the newest anchors, which
        // fork to the survival observer above.)
        return helper.makeMockServerPlayerInLevel();
    }
    */
    //?}

    /**
     * Pins the configured Detection radius (the engine reads it live each poll) and runs
     * the scenario, restoring the previous value afterwards. Sequence-driven scenarios
     * pin and restore around their whole lifecycle instead, since their final polls run
     * after this wrapper returns.
     */
    private static void withDetectionRadius(GameTestHelper helper, int radiusBlocks, Runnable scenario) {
        int saved = pinDetectionRadius(radiusBlocks);
        try {
            scenario.run();
        } finally {
            restoreDetectionRadius(saved);
        }
    }

    private static int pinDetectionRadius(int radiusBlocks) {
        int saved = WhosAfterMeConfig.detectionRadius;
        WhosAfterMeConfig.detectionRadius = radiusBlocks;
        return saved;
    }

    private static void restoreDetectionRadius(int saved) {
        WhosAfterMeConfig.detectionRadius = saved;
    }

    /**
     * Fails the enclosing test when {@code condition} does not hold. Used by the
     * synchronous scenario bodies; inside a sequence wait loop use {@link #assertSoon},
     * whose exception re-arms the wait instead of failing.
     */
    private static void assertTrue(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            //? if gametest_component_asserts {
            helper.fail(Component.literal(message));
            //?} else {
            /*helper.fail(message);*/
            //?}
        }
    }

    /**
     * The wait-loop form of {@code assertTrue}: throws the framework's assertion
     * exception, which a {@code thenWaitUntil} loop treats as "check again next tick"
     * until the test's tick budget runs out.
     */
    private static void assertSoon(GameTestHelper helper, boolean condition, String message) {
        if (!condition) {
            //? if gametest_component_asserts {
            throw helper.assertionException(message);
            //?} else {
            /*helper.assertTrue(condition, message);*/
            //?}
        }
    }


    /**
     * Drops the observer's bookkeeping from the server: mock players have no client to
     * disconnect, so they are removed from the player list explicitly.
     */
    private static void release(GameTestHelper helper, ServerPlayer observer) {
        ServerLevel level = helper.getLevel();
        level.getServer().getPlayerList().remove(observer);
    }

    // The 26.2 entity-type holder rename (see the import above): one spawn factory per
    // scenario mob, returning `Mob` so the fork stays invisible to the scenario bodies.
    //? if entity_types_modern {
    private static Mob spawnZombie(GameTestHelper helper, BlockPos pos) {
        return helper.spawn(EntityTypes.ZOMBIE, pos);
    }

    private static Mob spawnSkeleton(GameTestHelper helper, BlockPos pos) {
        return helper.spawn(EntityTypes.SKELETON, pos);
    }

    private static Mob spawnPiglin(GameTestHelper helper, BlockPos pos) {
        return helper.spawn(EntityTypes.PIGLIN, pos);
    }

    private static Mob spawnBee(GameTestHelper helper, BlockPos pos) {
        return helper.spawn(EntityTypes.BEE, pos);
    }
    //?} else {
    /*
    private static Mob spawnZombie(GameTestHelper helper, BlockPos pos) {
        return helper.spawn(EntityType.ZOMBIE, pos);
    }

    private static Mob spawnSkeleton(GameTestHelper helper, BlockPos pos) {
        return helper.spawn(EntityType.SKELETON, pos);
    }

    private static Mob spawnPiglin(GameTestHelper helper, BlockPos pos) {
        return helper.spawn(EntityType.PIGLIN, pos);
    }

    private static Mob spawnBee(GameTestHelper helper, BlockPos pos) {
        return helper.spawn(EntityType.BEE, pos);
    }
    */
    //?}
}
