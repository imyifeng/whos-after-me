package me.imyifeng.whosafterme;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Common initializer placeholder for the v1 scaffold (spec v1 §11 ticket 1).
 * The real entrypoints, detection engine, sync protocol, and HUD renderer are
 * added by later tickets.
 */
public class WhosAfterMe implements ModInitializer {
    // This logger is used to write text to the console and the log file.
    // It is considered best practice to use your mod id as the logger's name.
    public static final Logger LOGGER = LoggerFactory.getLogger("whos_after_me");
    public static final String VERSION = /*$ mod_version*/ "0.1.0";
    public static final String MINECRAFT = /*$ minecraft*/ "26.2";

    @Override
    public void onInitialize() {
        LOGGER.info("Who's After Me {} loaded on Minecraft {}", VERSION, MINECRAFT);
    }
}
