package me.imyifeng.whosafterme.gametest;

import net.minecraft.gametest.framework.GameTestHelper;

/**
 * The six server gametests (spec v1 §8, ADR-0004, ticket #30), registered under the
 * {@code fabric-gametest} entrypoint. The only per-era difference in the gametest sources
 * lives in this file: the GameTest annotation itself, split by the {@code gametest_api_v1}
 * workspace constant (spec v1 §8). Anchors below 1.21.5 declare the vanilla annotation
 * with the {@code template} attribute naming the Fabric empty structure; 1.21.5 and above
 * declare Fabric's {@code gametest.v1} annotation, whose {@code structure} already defaults
 * to that structure. Every scenario body is shared - see {@link ThreatDetectionScenarios}.
 */
//? if gametest_api_v1 {
import net.fabricmc.fabric.api.gametest.v1.GameTest;

public class WhosAfterMeGameTests {

    // Spec §8 scenario 1: a melee mob targeting the player -> Threat.
    @GameTest(maxTicks = 1000)
    public void meleeTargetingMobIsThreat(GameTestHelper helper) {
        ThreatDetectionScenarios.meleeTargetingMobIsThreat(helper);
    }

    // Spec §8 scenario 2: a brain-memory mob (ATTACK_TARGET path) -> Threat.
    @GameTest(maxTicks = 1000)
    public void brainMemoryMobIsThreat(GameTestHelper helper) {
        ThreatDetectionScenarios.brainMemoryMobIsThreat(helper);
    }

    // Spec §8 scenario 3: a provoked neutral -> Threat once angered.
    @GameTest(maxTicks = 1000)
    public void provokedNeutralIsThreatOnceAngered(GameTestHelper helper) {
        ThreatDetectionScenarios.provokedNeutralIsThreatOnceAngered(helper);
    }

    // Spec §8 scenario 4: a ranged mob -> Threat.
    @GameTest(maxTicks = 1000)
    public void rangedMobIsThreat(GameTestHelper helper) {
        ThreatDetectionScenarios.rangedMobIsThreat(helper);
    }

    // Spec §8 scenario 5: a mob beyond the Detection radius -> no Threat.
    @GameTest(maxTicks = 1000)
    public void outOfRadiusMobIsNoThreat(GameTestHelper helper) {
        ThreatDetectionScenarios.outOfRadiusMobIsNoThreat(helper);
    }

    // Spec §8 scenario 6: a cleared target held through the grace window, then dropped.
    @GameTest(maxTicks = 1000)
    public void clearedTargetHoldsThroughGraceThenDrops(GameTestHelper helper) {
        ThreatDetectionScenarios.clearedTargetHoldsThroughGraceThenDrops(helper);
    }
}
//?} else {
/*
import net.fabricmc.fabric.api.gametest.v1.FabricGameTest;
import net.minecraft.gametest.framework.GameTest;

public class WhosAfterMeGameTests {

    // Spec §8 scenario 1: a melee mob targeting the player -> Threat.
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1000)
    public void meleeTargetingMobIsThreat(GameTestHelper helper) {
        ThreatDetectionScenarios.meleeTargetingMobIsThreat(helper);
    }

    // Spec §8 scenario 2: a brain-memory mob (ATTACK_TARGET path) -> Threat.
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1000)
    public void brainMemoryMobIsThreat(GameTestHelper helper) {
        ThreatDetectionScenarios.brainMemoryMobIsThreat(helper);
    }

    // Spec §8 scenario 3: a provoked neutral -> Threat once angered.
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1000)
    public void provokedNeutralIsThreatOnceAngered(GameTestHelper helper) {
        ThreatDetectionScenarios.provokedNeutralIsThreatOnceAngered(helper);
    }

    // Spec §8 scenario 4: a ranged mob -> Threat.
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1000)
    public void rangedMobIsThreat(GameTestHelper helper) {
        ThreatDetectionScenarios.rangedMobIsThreat(helper);
    }

    // Spec §8 scenario 5: a mob beyond the Detection radius -> no Threat.
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1000)
    public void outOfRadiusMobIsNoThreat(GameTestHelper helper) {
        ThreatDetectionScenarios.outOfRadiusMobIsNoThreat(helper);
    }

    // Spec §8 scenario 6: a cleared target held through the grace window, then dropped.
    @GameTest(template = FabricGameTest.EMPTY_STRUCTURE, timeoutTicks = 1000)
    public void clearedTargetHoldsThroughGraceThenDrops(GameTestHelper helper) {
        ThreatDetectionScenarios.clearedTargetHoldsThroughGraceThenDrops(helper);
    }
}
*/
//?}
