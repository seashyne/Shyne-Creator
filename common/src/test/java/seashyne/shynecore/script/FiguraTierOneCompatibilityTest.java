package seashyne.shynecore.script;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises the Figura Tier-1 Lua facade with native bridges replaced by deterministic mocks.
 *
 * <p>The test intentionally covers the public Lua contract rather than Minecraft client state;
 * Minecraft-backed bridge behavior is exercised by loader integration tests. It runs on the test
 * thread and does not access a game client.</p>
 */
final class FiguraTierOneCompatibilityTest {
    private static final List<String> MODULES = List.of(
        "/shyne_runtime/lua/avatar/00_core.lua",
        "/shyne_runtime/lua/avatar/01_shyne_core.lua",
        "/shyne_runtime/lua/avatar/05_events_scheduler.lua",
        "/shyne_runtime/lua/avatar/59_figura_vectors.lua",
        "/shyne_runtime/lua/avatar/60_figura_compat.lua",
        "/shyne_runtime/lua/avatar/61_figura_client_renderer.lua",
        "/shyne_runtime/lua/avatar/62_figura_items_world.lua",
        "/shyne_runtime/lua/avatar/63_figura_action_wheel.lua"
    );

    @Test
    void clientPlayerAndRendererFacadesForwardFiguraData() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        LuaTable rendererWrites = new LuaTable();
        installBootstrapMocks(globals, rendererWrites);
        runBootstrap(environment);

        LuaValue result = globals.load("""
            local main = player:getHeldItem()
            local off = player:getHeldItem(true)
            local chest = player:getItem(5)
            local mouse = client:getMousePos()
            local size = client:getScaledWindowSize()

            local chain = renderer:setShadowRadius(4)
              :setCameraPivot(vectors.vec3(1, 2, 3))
              :setCameraPos(4, 5, 6)
              :setCameraRot(vectors.vec3(7, 8, 9))
              :setFOV(1.25)
            local pivot = renderer:getCameraPivot()
            local pos = renderer:getCameraPos()
            local rot = renderer:getCameraRot()

            return client:isFirstPerson() and client:getFPS() == 144 and not client:isPaused()
              and mouse.x == 320 and mouse.y == 180 and size.x == 960 and size.y == 540
              and client:getViewer() == player
              and main:getId() == "minecraft:diamond_sword" and main:getName() == "Diamond Sword"
              and main:getCount() == 1 and main:getDamage() == 12 and main:getMaxDamage() == 1561
              and main:hasGlint() and off:getId() == "minecraft:shield" and not off:hasGlint()
              and chest:getId() == "minecraft:diamond_chestplate" and player:getItem(0) == nil
              and player:getItem(7) == nil and renderer:isFirstPerson() and renderer:isCamera()
              and chain == renderer and renderer_writes.shadow_radius == 4
              and renderer_writes.camera_pivot.x == 1 and renderer_writes.camera_pivot.y == 2
              and renderer_writes.camera_pivot.z == 3 and renderer_writes.camera_pos.x == 4
              and renderer_writes.camera_pos.y == 5 and renderer_writes.camera_pos.z == 6
              and renderer_writes.camera_rot.x == 7 and renderer_writes.camera_rot.y == 8
              and renderer_writes.camera_rot.z == 9 and renderer_writes.fov == 1.25
              and renderer:getShadowRadius() == 3.5 and renderer:getFOV() == 1.1
              and pivot.x == 9 and pivot.y == 8 and pivot.z == 7
              and pos.x == 6 and pos.y == 5 and pos.z == 4
              and rot.x == 3 and rot.y == 2 and rot.z == 1
            """, "figura-tier-one-client-player-renderer-test").call();

        assertTrue(result.toboolean(), "Tier-1 Figura client, player item, and renderer facades must preserve values");
    }

    @Test
    void pingsQueueMultipleCallsAndDeduplicatesSyncedDeliveries() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        installBootstrapMocks(globals, new LuaTable());
        runBootstrap(environment);

        LuaValue result = globals.load("""
            local total = 0
            pings.add = function(value) total = total + value end
            pings.add(2)
            pings.add(5)

            local queue = synced_writes["__figura_ping_queue"]
            local queue_ok = queue ~= nil and queue.first == 1 and queue.last == 2
              and #queue.events == 2 and queue.events[1].seq == 1 and queue.events[1].args[1] == 2
              and queue.events[2].seq == 2 and queue.events[2].args[1] == 5

            events._dispatch("synced_var_change", { key = "__figura_ping_queue", value = queue })
            local delivered_once = total == 14
            events._dispatch("synced_var_change", { key = "__figura_ping_queue", value = queue })

            return queue_ok and delivered_once and total == 14
            """, "figura-tier-one-pings-test").call();

        assertTrue(result.toboolean(), "Ping queue must retain same-tick calls and ignore duplicate synced deliveries");
    }

    /** Installs only the native callbacks needed to load and observe the compatibility facade. */
    private static void installBootstrapMocks(Globals globals, LuaTable rendererWrites) {
        globals.set("SHYNE_API_VERSION", LuaValue.valueOf("2.0"));
        globals.set("SHYNE_API_AUTOMATIC", LuaValue.TRUE);
        globals.set("_shyne_api_modules", new ZeroArgFunction() {
            @Override public LuaValue call() { return new LuaTable(); }
        });
        globals.set("_shyne_api_supports", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
        });
        globals.set("_shyne_permission_allowed", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) { return LuaValue.TRUE; }
        });
        globals.set("_shyne_permission_requested", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) { return LuaValue.FALSE; }
        });
        globals.set("_shyne_permissions", new ZeroArgFunction() {
            @Override public LuaValue call() { return new LuaTable(); }
        });
        globals.set("_shyne_report_error", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NIL; }
        });
        globals.set("_shyne_read", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return read(args.arg(1).optjstring(""), args.arg(2));
            }
        });

        LuaTable syncedWrites = new LuaTable();
        globals.set("synced_writes", syncedWrites);
        globals.set("_avatar_synced_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                syncedWrites.set(args.arg(1), args.arg(2));
                return LuaValue.NIL;
            }
        });
        globals.set("_figura_renderer_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String key = args.arg(1).optjstring("");
                if (key.equals("camera_pivot") || key.equals("camera_pos") || key.equals("camera_rot")) {
                    rendererWrites.set(key, vector(
                        args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0)
                    ));
                } else {
                    rendererWrites.set(key, args.arg(2));
                }
                return LuaValue.NIL;
            }
        });
        globals.set("renderer_writes", rendererWrites);
        globals.set("_figura_renderer_get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue key) {
                return switch (key.optjstring("")) {
                    case "shadow_radius" -> LuaValue.valueOf(3.5);
                    case "camera_pivot" -> vector(9, 8, 7);
                    case "camera_pos" -> vector(6, 5, 4);
                    case "camera_rot" -> vector(3, 2, 1);
                    case "fov" -> LuaValue.valueOf(1.1);
                    default -> LuaValue.NIL;
                };
            }
        });
    }

    /** Returns the mocked read-only client or inventory value for a native Lua read. */
    private static LuaValue read(String key, LuaValue argument) {
        return switch (key) {
            case "client.first_person", "client.camera_is_player" -> LuaValue.TRUE;
            case "client.paused" -> LuaValue.FALSE;
            case "client.fps" -> LuaValue.valueOf(144);
            case "client.mouse_x" -> LuaValue.valueOf(320);
            case "client.mouse_y" -> LuaValue.valueOf(180);
            case "client.window_w" -> LuaValue.valueOf(960);
            case "client.window_h" -> LuaValue.valueOf(540);
            case "player.main_hand" -> item("minecraft:diamond_sword", "Diamond Sword", 1, 12, 1561, true);
            case "player.off_hand" -> item("minecraft:shield", "Shield", 1, 0, 336, false);
            case "player.item" -> itemForSlot(argument.optint(0));
            default -> LuaValue.NIL;
        };
    }

    /** Creates an ItemStack-shaped value matching the bridge's Lua-facing fields. */
    private static LuaTable item(String id, String name, int count, int damage, int maxDamage, boolean glint) {
        LuaTable value = new LuaTable();
        value.set("id", LuaValue.valueOf(id));
        value.set("name", LuaValue.valueOf(name));
        value.set("count", LuaValue.valueOf(count));
        value.set("damage", LuaValue.valueOf(damage));
        value.set("max_damage", LuaValue.valueOf(maxDamage));
        value.set("glint", LuaValue.valueOf(glint));
        return value;
    }

    /** Provides Figura slot 1-6 values, leaving unsupported slots absent. */
    private static LuaValue itemForSlot(int slot) {
        return switch (slot) {
            case 1 -> item("minecraft:diamond_sword", "Diamond Sword", 1, 12, 1561, true);
            case 2 -> item("minecraft:shield", "Shield", 1, 0, 336, false);
            case 5 -> item("minecraft:diamond_chestplate", "Diamond Chestplate", 1, 3, 528, true);
            default -> LuaValue.NIL;
        };
    }

    /** Creates a plain native vector table so the facade must wrap it in a Figura vector. */
    private static LuaTable vector(double x, double y, double z) {
        LuaTable value = new LuaTable();
        value.set("x", LuaValue.valueOf(x));
        value.set("y", LuaValue.valueOf(y));
        value.set("z", LuaValue.valueOf(z));
        return value;
    }

    /** Loads the minimal runtime modules needed by the Figura compatibility layer. */
    private void runBootstrap(LuaSandbox.Environment environment) throws IOException {
        StringBuilder source = new StringBuilder(24 * 1024);
        for (String resource : MODULES) {
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, "Lua API module must be packaged: " + resource);
                source.append(new String(input.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        environment.budget().reset(1_000_000);
        environment.globals().load(source.toString(), "figura-tier-one-bootstrap.lua").call();
    }
}
