package me.imyifeng.whosafterme;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common initializer for Who's After Me (spec v1 §9). Runs on both logical sides -
 * the mod is required on client and server alike (ADR-0005).
 *
 * <p>Later tickets extend this initializer: the sync protocol payload registration
 * and the server tick hook (spec v1 §11 tickets 5 and 6). The skeleton only
 * establishes the mod's identity and logging; nothing user-visible yet.
 */
public class WhosAfterMe implements ModInitializer {
    // Using the mod id as the logger's name is considered best practice.
    public static final Logger LOGGER = LoggerFactory.getLogger("whos_after_me");
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "26.2";

    @Override
    public void onInitialize() {
        EnvType environment = FabricLoader.getInstance().getEnvironmentType();
        LOGGER.info("Who's After Me {} initialized on Minecraft {} ({})", VERSION, MINECRAFT, environment);
    }
}
