package me.imyifeng.whosafterme;

import eu.midnightdust.lib.config.MidnightConfig;
import me.imyifeng.whosafterme.config.WhosAfterMeConfig;
import me.imyifeng.whosafterme.detection.ThreatDetector;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common initializer for Who's After Me (spec v1 §9). Runs on both logical sides -
 * the mod is required on client and server alike (ADR-0005).
 *
 * <p>Initializes the MidnightConfig config here so each environment loads its own
 * {@code config/whos_after_me.json}: the server process reads {@code detectionRadius}
 * and {@code pollInterval} from its own file (ADR-0005 side semantics, spec v1 §7).
 * Also registers the server detection engine's tick hook (spec v1 §3, ADR-0001).
 *
 * <p>Later tickets extend this initializer: the sync protocol payload registration
 * (spec v1 §11 ticket 6).
 */
public class WhosAfterMe implements ModInitializer {
    public static final String MOD_ID = "whos_after_me";
    // Using the mod id as the logger's name is considered best practice.
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "26.2";

    @Override
    public void onInitialize() {
        MidnightConfig.init(MOD_ID, WhosAfterMeConfig.class);
        ThreatDetector.register();

        EnvType environment = FabricLoader.getInstance().getEnvironmentType();
        LOGGER.info("Who's After Me {} initialized on Minecraft {} ({})", VERSION, MINECRAFT, environment);
    }
}
