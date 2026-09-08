package me.imyifeng.whosafterme.client.store;

import java.util.List;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;

/**
 * Client-side owner of the shared {@link ClientThreatStore} (spec v1 §5.4): clears it
 * on the store lifecycle events (dimension change, respawn, world disconnect) and
 * gives the HUD renderer (spec v1 §11 ticket 8) the per-frame read - resolve each
 * network id in the client world, self-heal the stale entries out, apply the indicator
 * cap selection.
 *
 * <p>Dimension change and respawn both arrive as freshly built client state (a new
 * {@code ClientLevel}, a new {@code LocalPlayer}), so the tick hook clears whenever
 * either instance changed; disconnect additionally clears through
 * {@code ClientPlayConnectionEvents.DISCONNECT}. All hooks run on the client thread,
 * as does the sync receiver's thread hop (ADR-0002), so the store is only ever
 * touched from one thread.
 */
@Environment(EnvType.CLIENT)
public final class ClientThreats {
    private static final ClientThreatStore STORE = new ClientThreatStore();

    private static ClientLevel lastLevel;
    private static LocalPlayer lastPlayer;

    private ClientThreats() {
    }

    /** The client threat store. Consume only from the client thread. */
    public static ClientThreatStore store() {
        return STORE;
    }

    /** Registers the store lifecycle hooks. Called once from the client initializer. */
    public static void register() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> STORE.clear());
        ClientTickEvents.END_CLIENT_TICK.register(ClientThreats::onEndClientTick);
    }

    /** Any new world or viewer instance invalidates the synced set (spec v1 §5.4). */
    private static void onEndClientTick(Minecraft client) {
        if (client.level != lastLevel || client.player != lastPlayer) {
            lastLevel = client.level;
            lastPlayer = client.player;
            STORE.clear();
        }
    }

    /**
     * Per-frame store refresh for the renderer: drops entries whose entity id no
     * longer resolves in {@code level} (staleness self-heal, ADR-0002) and applies the
     * cap selection (spec v1 §5.2) using viewer-relative squared distances.
     *
     * @return the selected entity network ids in slot order, empty when not in a world.
     */
    public static List<Integer> refresh(ClientLevel level, LocalPlayer viewer) {
        if (level == null || viewer == null) {
            STORE.clear();
            return List.of();
        }
        STORE.prune(id -> level.getEntity(id) != null);
        return STORE.select(id -> {
            Entity entity = level.getEntity(id);
            return entity == null
                    ? Double.MAX_VALUE
                    : viewer.distanceToSqr(entity.getX(), entity.getY(), entity.getZ());
        });
    }
}
