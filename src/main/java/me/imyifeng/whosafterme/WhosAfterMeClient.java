package me.imyifeng.whosafterme;

import com.mojang.blaze3d.platform.InputConstants;
import eu.midnightdust.lib.config.MidnightConfig;
import me.imyifeng.whosafterme.config.WhosAfterMeConfig;
import me.imyifeng.whosafterme.net.ClientSync;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
//? if fapi_modern_id {
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
//?} else {
/*import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;*/
//?}
import net.minecraft.client.KeyMapping;
//? if keymap_category_object {
import net.minecraft.resources.Identifier;
//?}
import org.lwjgl.glfw.GLFW;

/**
 * Client initializer for Who's After Me (spec v1 §9). Loaded only on the client
 * logical side; the {@code client} entrypoint in {@code fabric.mod.json} keeps the
 * class off the dedicated server.
 *
 * <p>Registers the toggle keybind (spec v1 §7, ADR-0003): one {@link KeyMapping} in the
 * vanilla Controls screen, shipping unbound. The keybind and the config screen's toggle
 * are one persisted {@code enabled} setting with two entry points - the keybind flips it
 * live and writes the same JSON file MidnightConfig owns. Also registers the client
 * side of the sync protocol: the hello handshake and the threat_sync receiver
 * (spec v1 §4). Later tickets extend this initializer with the HUD registration
 * (spec v1 §11 ticket 8).
 */
@Environment(EnvType.CLIENT)
public class WhosAfterMeClient implements ClientModInitializer {
    private static KeyMapping toggleHud;

    //? if keymap_category_object {
    // From 1.21.11 keybind categories are registered records; the label translation
    // key is derived from the id as `key.category.<namespace>.<path>`.
    private static final KeyMapping.Category TOGGLE_CATEGORY =
            KeyMapping.Category.register(Identifier.fromNamespaceAndPath(WhosAfterMe.MOD_ID, "main"));
    //?}

    @Override
    public void onInitializeClient() {
        // The client side of the threat sync protocol (spec v1 §4): hello on
        // play-phase join, threat_sync received on the client thread.
        ClientSync.register();
        //? if keymap_category_object {
        toggleHud = register(new KeyMapping(
                "key.whos_after_me.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, TOGGLE_CATEGORY));
        //?} else {
        /*toggleHud = register(new KeyMapping(
                "key.whos_after_me.toggle", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_UNKNOWN, "key.categories.whos_after_me"));*/
        //?}

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHud.consumeClick()) {
                WhosAfterMeConfig.enabled = !WhosAfterMeConfig.enabled;
                MidnightConfig.write(WhosAfterMe.MOD_ID);
                // The HUD appearing/disappearing is the visible feedback; that is the
                // renderer ticket's surface. Here we just flip, persist, and log.
                WhosAfterMe.LOGGER.info("Threat HUD toggled {}", WhosAfterMeConfig.enabled ? "on" : "off");
            }
        });

        WhosAfterMe.LOGGER.info("Who's After Me {} client initialized", WhosAfterMe.VERSION);
    }

    /** Registers the keybind, absorbing the Fabric API 26.1 helper rename (spec v1 §6). */
    private static KeyMapping register(KeyMapping mapping) {
        //? if fapi_modern_id {
        return KeyMappingHelper.registerKeyMapping(mapping);
        //?} else {
        /*return KeyBindingHelper.registerKeyBinding(mapping);*/
        //?}
    }
}
