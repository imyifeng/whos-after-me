package me.imyifeng.whosafterme.config;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;
import eu.midnightdust.lib.config.MidnightConfig;
import me.imyifeng.whosafterme.WhosAfterMe;

/**
 * ModMenu integration (ADR-0003): the only config-screen entry point in v1. ModMenu is an
 * optional dependency - this class is instantiated by ModMenu when it is installed, and
 * never loaded otherwise, so the mod works without it (the JSON file is then the surface).
 *
 * <p>The lambda intentionally names no Minecraft screen type: {@code ConfigScreenFactory}'s
 * parameter follows the Yarn-to-Mojang rename across the anchors, while
 * {@link MidnightConfig#getScreen} accepts the parent screen under either mapping. The
 * single source therefore compiles on every anchor without a preprocessor fork.
 */
public class ModMenuIntegration implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return parent -> MidnightConfig.getScreen(parent, WhosAfterMe.MOD_ID);
    }
}
