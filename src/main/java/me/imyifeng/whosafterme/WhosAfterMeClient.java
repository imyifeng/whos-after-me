package me.imyifeng.whosafterme;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

/**
 * Client initializer for Who's After Me (spec v1 §9). Loaded only on the client
 * logical side; the {@code client} entrypoint in {@code fabric.mod.json} keeps the
 * class off the dedicated server.
 *
 * <p>Later tickets extend this initializer: the config screen entry, toggle keybind,
 * and HUD registration (spec v1 §11 tickets 3 and 8). The skeleton only logs.
 */
@Environment(EnvType.CLIENT)
public class WhosAfterMeClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        WhosAfterMe.LOGGER.info("Who's After Me {} client initialized", WhosAfterMe.VERSION);
    }
}
