package seashyne.shynecore.events;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.loader.ShyneModLoader;

import java.util.Map;

/**
 * ShyneEventBus
 *
 * Registers Fabric event callbacks and forwards them as named hook calls
 * into every loaded Lua content pack.
 *
 * Hooks available in main.lua:
 *
 *   def on_server_start(args):    # server is fully started
 *   def on_server_stop(args):     # server shutting down
 *   def on_player_join(args):     # args: player (name), uuid
 *   def on_player_leave(args):    # args: player (name), uuid
 *   def on_chat(args):            # args: player (name), message
 *   def on_tick(args):            # args: tick (count)  — every 20 ticks (1 second)
 */
public class ShyneEventBus {

    private ShyneModLoader modLoader;
    private int tickCounter = 0;

    /** Called once the mod loader is ready */
    public void bind(ShyneModLoader modLoader) {
        this.modLoader = modLoader;
        registerFabricEvents();
    }

    private void registerFabricEvents() {
        // Server lifecycle
        ServerLifecycleEvents.SERVER_STARTED.register(this::onServerStart);
        ServerLifecycleEvents.SERVER_STOPPING.register(this::onServerStop);

        // Player join / leave
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
            onPlayerJoin(handler.getPlayer()));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            onPlayerLeave(handler.getPlayer()));

        // Chat messages
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) ->
            onChat(sender, message.decoratedContent().getString()));

        // Tick — fire on_tick hook every 20 ticks (~1 second)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            tickCounter++;
            if (tickCounter % 20 == 0) {
                fireHook("on_tick", Map.of("tick", tickCounter));
            }
        });
    }

    // ─── Event handlers ───────────────────────────────────────────────────────

    private void onServerStart(MinecraftServer server) {
        ShyneCore.LOGGER.info("[EventBus] Server started — firing on_server_start");
        fireHook("on_server_start", Map.of("version", server.getServerVersion()));
    }

    private void onServerStop(MinecraftServer server) {
        ShyneCore.LOGGER.info("[EventBus] Server stopping — firing on_server_stop");
        fireHook("on_server_stop", Map.of());
    }

    private void onPlayerJoin(ServerPlayer player) {
        fireHook("on_player_join", Map.of(
            "player", player.getName().getString(),
            "uuid",   player.getStringUUID()
        ));
    }

    private void onPlayerLeave(ServerPlayer player) {
        fireHook("on_player_leave", Map.of(
            "player", player.getName().getString(),
            "uuid",   player.getStringUUID()
        ));
    }

    private void onChat(ServerPlayer sender, String message) {
        fireHook("on_chat", Map.of(
            "player",  sender.getName().getString(),
            "message", message
        ));
    }

    // ─── Utility ─────────────────────────────────────────────────────────────

    private void fireHook(String hook, Map<String, Object> args) {
        if (modLoader != null) {
            modLoader.fireHook(hook, args);
        }
    }
}
