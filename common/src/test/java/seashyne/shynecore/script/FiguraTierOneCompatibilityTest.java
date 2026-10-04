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
        "/shyne_runtime/lua/avatar/63_figura_action_wheel.lua",
        "/shyne_runtime/lua/avatar/64_figura_data_nameplate.lua",
        "/shyne_runtime/lua/avatar/65_figura_network.lua"
    );

    @Test
    void clientPlayerAndRendererFacadesForwardFiguraData() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        LuaTable rendererWrites = new LuaTable();
        installBootstrapMocks(environment, rendererWrites);
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
        installBootstrapMocks(environment, new LuaTable());
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

    @Test
    void dynamicTextureFacadePublishesPixelsThroughNativeTextureBridge() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        installBootstrapMocks(environment, new LuaTable());
        runBootstrap(environment);

        LuaValue result = globals.load("""
            local texture = textures:newTexture("hud_meter", 12, 8)
            texture:setPixel(1, 2, 1, 0.5, 0, 0.25):apply()
            local wrote_pixel = texture_writes.pixel.x == 1 and texture_writes.pixel.y == 2
              and texture_writes.pixel.argb == 0x40FF8000
            texture:fill(0, 0, 1, 1):apply()
            return texture ~= nil and texture:id() == "shyne_creator:runtime/test/hud_meter"
              and texture:getWidth() == 12 and texture:getHeight() == 8
              and wrote_pixel and texture_writes.fill == 0xFF0000FF and texture_writes.applied == 2
            """, "figura-tier-one-dynamic-texture-test").call();

        assertTrue(result.toboolean(), "Figura texture facade must route pixel edits and apply calls to the native texture bridge");
    }

    @Test
    void figuraInputEventsAndModelTextureBindingForwardNativeData() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        installBootstrapMocks(environment, new LuaTable());
        runBootstrap(environment);

        LuaValue result = globals.load("""
            local key, scan, modifiers, payload = 0, 0, 0, nil
            events.KEY_PRESS:register(function(k, s, m, event) key, scan, modifiers, payload = k, s, m, event end)
            events._dispatch("key_press", { key = 71, scan_code = 7, modifiers = 2 })
            local texture = textures:newTexture("model_meter", 4, 4)
            local bound = texture:bindToModel("skin.png")
            return key == 71 and scan == 7 and modifiers == 2 and payload.key == 71
              and bound and texture_writes.model_target == "skin.png"
            """, "figura-tier-one-input-material-test").call();

        assertTrue(result.toboolean(), "Figura input events and dynamic model material binding must forward native data");
    }

    @Test
    void keybindFacadeUsesTheNativeInputBridgeAndPreservesFiguraControls() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        installBootstrapMocks(environment, new LuaTable());
        runBootstrap(environment);

        LuaValue result = globals.load("""
            local dash = keybinds:newKeybind("Dash", "key.keyboard.g", true)
            dash.press = function() end
            local created = dash ~= nil and dash:getName() == "Dash" and dash:getKey() == "key.keyboard.g"
              and dash:isGuiEnabled() and dash:isDefault() and dash.press ~= nil and keybinds:getKeybinds().Dash == dash
            dash:setKey("key.keyboard.h"):setEnabled(false):setGUI(false)
            local changed = dash:getKey() == "key.keyboard.h" and dash:getKeyName() == "H" and dash:getID() == 72
              and not dash:isDefault() and not dash:isEnabled() and not dash:isGuiEnabled()
            dash:setEnabled(true):setGUI(true):reset()
            local vanilla = keybinds:fromVanilla("key.jump")
            return created and changed and dash:isEnabled() and dash:isGuiEnabled() and dash:isDefault()
              and dash:getKey() == "key.keyboard.g" and vanilla ~= nil and vanilla:getKey() == "key.keyboard.space"
              and keybinds:getVanillaKey("missing") == nil
            """, "figura-tier-one-keybind-test").call();

        assertTrue(result.toboolean(), "Figura keybind facade must use native bindings and expose key, GUI, enable, reset, and vanilla controls");
    }

    @Test
    void extendedEventsParityDispatchesCorrectly() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        installBootstrapMocks(environment, new LuaTable());
        runBootstrap(environment);

        LuaValue result = globals.load("""
            local mouse_ok, use_ok, chat_ok, dmg_ok, totem_ok, skull_ok = false, false, false, false, false, false

            events.MOUSE_MOVE:register(function(x, y, dx, dy, ev)
                if x == 100 and y == 200 and dx == 5 and dy == -3 and ev.x == 100 then
                    mouse_ok = true
                end
            end)
            events._dispatch("mouse_move", { x = 100, y = 200, dx = 5, dy = -3 })

            events.USE_ITEM:register(function(item, action, count, ev)
                if item == "minecraft:apple" and action == "EAT" and count == 32 then
                    use_ok = true
                end
            end)
            events._dispatch("item_use", { item = "minecraft:apple", action = "EAT", count = 32 })

            events.CHAT_RECEIVE_MESSAGE:register(function(raw, text, sender_uuid, sender_name, ev)
                if raw == "hello" and text == "hello" and sender_uuid == "123" and sender_name == "Player" then
                    chat_ok = true
                end
            end)
            events._dispatch("chat_receive", { raw = "hello", text = "hello", sender_uuid = "123", sender_name = "Player" })

            events.DAMAGE:register(function(amount, source, ev)
                if amount == 4.5 and source == "generic" then
                    dmg_ok = true
                end
            end)
            events._dispatch("damage", { amount = 4.5, source = "generic" })

            events.TOTEM:register(function(ev)
                if ev.entity == "player" then
                    totem_ok = true
                end
            end)
            events._dispatch("totem", { entity = "player" })

            events.SKULL_RENDER:register(function(delta, ctx, ev)
                if delta == 0.5 and ctx == "SKULL" and ev.is_skull == true then
                    skull_ok = true
                end
            end)
            events._dispatch("render", { delta = 0.5, context = "SKULL", is_skull = true })

            return mouse_ok and use_ok and chat_ok and dmg_ok and totem_ok and skull_ok
            """, "figura-extended-events-parity-test").call();

        assertTrue(result.toboolean(), "All extended Figura events must dispatch and map payload correctly");
    }

    @Test
    void nameplateDataJsonAndResourcesFacadesOperateSafely() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        installBootstrapMocks(environment, new LuaTable());
        runBootstrap(environment);

        LuaValue result = globals.load("""
            -- 1. Nameplate 2.0
            nameplate.ENTITY:setText("Hero"):setColor("#55FFFF"):setBadge("★"):setVisible(true)
            nameplate.CHAT:setText("ChatHero"):setBadge("VIP"):setColor(0xFFFF55)
            nameplate.LIST:setText("TabHero"):setBadge("[PRO]")
            nameplate.ENTITY:setPos(1, 2, 3):setScale(2, 2, 2):setPivot(0.5, 0.5, 0.5)

            local np_ok = nameplate.ENTITY:getText() == "Hero"
              and nameplate.ENTITY:getBadge() == "★"
              and nameplate.ENTITY:isVisible() == true
              and nameplate.CHAT:getText() == "ChatHero"
              and nameplate.CHAT:getBadge() == "VIP"
              and nameplate.LIST:getText() == "TabHero"
              and nameplate.LIST:getBadge() == "[PRO]"
              and nameplate.ENTITY:getPos().x == 1
              and nameplate.ENTITY:getScale().x == 2
              and nameplate.ENTITY:getPivot().x == 0.5

            -- 2. Data persistence
            data:setName("profile")
            data:save("rank", "Grandmaster")
            data["level"] = 99
            local data_ok = data:load("rank") == "Grandmaster"
              and data["level"] == 99
              and data:has("rank") == true

            -- 3. JSON encode/decode
            local encoded = json:encode({ id = "shyne", count = 42 })
            local decoded = json:decode(encoded)
            local json_ok = decoded ~= nil and decoded.id == "shyne" and decoded.count == 42

            -- 4. Resources
            local res_has = resources:has("avatar.json")
            local res_text = resources:getText("avatar.json")
            local res_ok = res_has == true and res_text == '{"name":"TestAvatar"}'

            return np_ok and data_ok and json_ok and res_ok
            """, "figura-nameplate-data-json-test").call();

        assertTrue(result.toboolean(), "Nameplate 2.0, Data, JSON, and Resources facades must function safely");
    }

    @Test
    void typedPingsEnforceSchemasAndPayloadSanitization() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        installBootstrapMocks(environment, new LuaTable());
        runBootstrap(environment);

        LuaValue result = globals.load("""
            local received_power = nil
            local received_element = nil
            pings:define("cast_ability", {"string", "number"}, function(elem, power)
                received_element = elem
                received_power = power
            end)

            pings.cast_ability("ice", 50)
            local queue = synced_writes["__figura_ping_queue"]
            local queued_entry = queue and queue.events and queue.events[1]
            local valid_args = queued_entry and queued_entry.name == "cast_ability"
              and queued_entry.args and queued_entry.args[1] == "ice" and queued_entry.args[2] == 50

            -- Verify schema introspection
            local schema = pings:get_schema("cast_ability")
            local schema_ok = schema ~= nil and schema[1] == "string" and schema[2] == "number"

            -- Test invalid type handling: 12345 coerces to "12345", "invalid_num" coerces to 0
            pings.cast_ability(12345, "invalid_num")
            local queue_after = synced_writes["__figura_ping_queue"]
            local second_entry = queue_after and queue_after.events and queue_after.events[2]
            local sanitized_ok = second_entry and type(second_entry.args[1]) == "string"
              and second_entry.args[1] == "12345"
              and type(second_entry.args[2]) == "number"
              and second_entry.args[2] == 0

            return valid_args and schema_ok and sanitized_ok
            """, "figura-typed-pings-test").call();

        assertTrue(result.toboolean(), "Typed pings must enforce schemas and argument sanitization");
    }

    @Test
    void networkChannelsEnforcePermissionsAndLoopback() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        installBootstrapMocks(environment, new LuaTable());
        runBootstrap(environment);

        LuaValue result = globals.load("""
            -- Default shyne: channel should be allowed
            local shyne_send = network.send("shyne:sync", { count = 1 })

            -- Unregistered custom channel should be denied initially
            local unreg_send = network.send("custom:packet", { data = "test" })

            -- Requesting permission for custom channel
            local allow_ok = network.allow_channel("custom:packet")
            local is_allowed = network.is_allowed("custom:packet")
            local reg_send = network.send("custom:packet", { data = "test" })

            -- Shims
            local net_send = net.send("shyne:sync", "shim_test")
            local srv_send = server_packets.send("shyne:sync", "srv_test")

            local connected = network.is_connected()

            return shyne_send == true
              and unreg_send == false
              and allow_ok == true
              and is_allowed == true
              and reg_send == true
              and net_send == true
              and srv_send == true
              and connected == true
            """, "figura-network-channels-test").call();

        assertTrue(result.toboolean(), "Network channel and Figura shims must enforce allowed channels");
    }

    @Test
    void figuraExtendedLibrariesAndHelpersSupport() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        installBootstrapMocks(environment, new LuaTable());
        runBootstrap(environment);

        LuaValue result = globals.load("""
            -- 1. Universal vec constructor
            local v0 = vec()
            local v2 = vec(10, 20)
            local v3 = vec(1, 2, 3)
            local v4 = vec(1, 2, 3, 4)
            local v_num = vec(5)
            local v_tbl = vec({ x = 7, y = 8, z = 9 })

            -- 2. Hex <-> RGB color conversions
            local c_white = vectors.hexToRGB("#ffffff")
            local hex_white = vectors.rgbToHex(c_white)
            local c_pink = vectors.hexToRGB("#ff8a90")
            local hex_pink = vectors.rgbToHex(c_pink)

            -- 3. Figura Math Helpers
            local lerped = math.lerp(0, 10, 0.5)
            local clamped = math.clamp(15, 0, 10)
            local sign_pos = math.sign(42)
            local sign_neg = math.sign(-42)
            local rounded = math.round(3.6)

            -- 4. Player methods
            local yaw = player:getBodyYaw()
            local pose = player:getPose()
            local rot = player:getRot()
            local using = player:isUsingItem()
            local swinging = player:isSwingingArm()

            -- 5. Nameplate aliases and advanced methods
            nameplate.Entity:setVisible(true):setOutline(true):setShadow(false):setLight(15, 15)
            nameplate.ALL:setText("Test Name"):setBackgroundColor(0.5, 1, 0, 0)
            local json_str = toJson({ name = "Pink", value = 123 })
            local parsed = parseJson(json_str)

            -- 6. Action wheel methods
            local p = action_wheel:newPage()
            local act = p:newAction()
                :title("Test")
                :setTexture(textures["textures.main"])
                :toggleTitle("Alt")
                :toggleTexture(textures["textures.alt"])
                :toggleColor(1, 0, 0)
                :setOnToggle(function(t) end)
                :setOnLeftClick(function() end)

            -- 7. Sounds playSound with pos as 2nd arg
            sounds:playSound("test_sound", player:getPos(), 1, 1)

            -- 8. Models setPrimaryTexture (if models facade is present)
            if models and models.setPrimaryTexture then
                models:setPrimaryTexture("Custom", textures["model.alt"])
                models.setPrimaryTexture("Custom", textures["model.alt"])
            end

            return v0.x == 0 and v0.y == 0 and v0.z == 0
              and v2.x == 10 and v2.y == 20
              and v3.x == 1 and v3.y == 2 and v3.z == 3
              and v4.w == 4
              and v_num.x == 5 and v_num.y == 5 and v_num.z == 5
              and v_tbl.x == 7 and v_tbl.y == 8 and v_tbl.z == 9
              and hex_white == "ffffff"
              and hex_pink == "ff8a90"
              and lerped == 5
              and clamped == 10
              and sign_pos == 1 and sign_neg == -1
              and rounded == 4
              and yaw == 45
              and pose == "CROUCHING"
              and parsed.name == "Pink"
              and parsed.value == 123
              and using == false
              and swinging == false
            """, "figura-extended-helpers-test").call();

        assertTrue(result.toboolean(), "Figura extended libraries, math helpers, and facade methods must function correctly");
    }

    /** Installs only the native callbacks needed to load and observe the compatibility facade. */
    private static void installBootstrapMocks(LuaSandbox.Environment environment, LuaTable rendererWrites) {
        Globals globals = environment.globals();
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
        LuaTable textureWrites = new LuaTable();
        globals.set("texture_writes", textureWrites);
        globals.set("_avatar_dynamic_texture_create", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable value = new LuaTable();
                value.set("id", LuaValue.valueOf("shyne_creator:runtime/test/" + args.arg(1).optjstring("texture")));
                value.set("width", LuaValue.valueOf(args.arg(2).optint(64)));
                value.set("height", LuaValue.valueOf(args.arg(3).optint(64)));
                return value;
            }
        });
        globals.set("_avatar_dynamic_texture_set_pixel", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable pixel = new LuaTable();
                pixel.set("x", args.arg(2));
                pixel.set("y", args.arg(3));
                pixel.set("argb", LuaValue.valueOf(args.arg(4).optlong(0) & 0xFFFFFFFFL));
                textureWrites.set("pixel", pixel);
                return LuaValue.TRUE;
            }
        });
        globals.set("_avatar_dynamic_texture_fill", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                textureWrites.set("fill", LuaValue.valueOf(args.arg(2).optlong(0) & 0xFFFFFFFFL));
                return LuaValue.TRUE;
            }
        });
        globals.set("_avatar_dynamic_texture_apply", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                textureWrites.set("applied", LuaValue.valueOf(textureWrites.get("applied").optint(0) + 1));
                return LuaValue.TRUE;
            }
        });
        globals.set("_avatar_dynamic_texture_bind_model", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                textureWrites.set("model_texture", args.arg(1));
                textureWrites.set("model_target", args.arg(2));
                return LuaValue.TRUE;
            }
        });
        globals.set("_avatar_action_register", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.TRUE;
            }
        });

        // Mocks for Nameplate 2.0, Data, JSON, Resources
        LuaTable nameplateWrites = new LuaTable();
        globals.set("nameplate_writes", nameplateWrites);
        globals.set("_avatar_nameplate_target_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                LuaTable target = new LuaTable();
                target.set("text", args.arg(2));
                target.set("visible", args.arg(3));
                target.set("badge", args.arg(4));
                target.set("color", args.arg(5));
                target.set("bold", args.arg(6));
                target.set("italic", args.arg(7));
                nameplateWrites.set(args.arg(1).optjstring("entity"), target);
                return LuaValue.TRUE;
            }
        });
        globals.set("_avatar_nameplate_transform_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                nameplateWrites.set("posX", args.arg(1));
                nameplateWrites.set("scaleX", args.arg(4));
                nameplateWrites.set("pivotX", args.arg(7));
                return LuaValue.TRUE;
            }
        });
        globals.set("_avatar_json_encode", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                Object obj = LuaValueCodec.toJava(arg);
                return LuaValue.valueOf(new com.google.gson.Gson().toJson(obj));
            }
        });
        globals.set("_avatar_json_decode", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                Object obj = new com.google.gson.Gson().fromJson(arg.optjstring(""), Object.class);
                return LuaValueCodec.toLua(obj);
            }
        });

        java.util.Map<String, java.util.Map<String, Object>> mockStorage = new java.util.HashMap<>();
        globals.set("_avatar_data_save", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String ns = args.arg(1).optjstring("default");
                String key = args.arg(2).optjstring("");
                mockStorage.computeIfAbsent(ns, k -> new java.util.HashMap<>()).put(key, LuaValueCodec.toJava(args.arg(3)));
                return LuaValue.TRUE;
            }
        });
        globals.set("_avatar_data_load", new org.luaj.vm2.lib.TwoArgFunction() {
            @Override public LuaValue call(LuaValue nsArg, LuaValue keyArg) {
                var map = mockStorage.get(nsArg.optjstring("default"));
                if (map == null) return LuaValue.NIL;
                return LuaValueCodec.toLua(map.get(keyArg.optjstring("")));
            }
        });
        globals.set("_avatar_data_has", new org.luaj.vm2.lib.TwoArgFunction() {
            @Override public LuaValue call(LuaValue nsArg, LuaValue keyArg) {
                var map = mockStorage.get(nsArg.optjstring("default"));
                return LuaValue.valueOf(map != null && map.containsKey(keyArg.optjstring("")));
            }
        });
        globals.set("_avatar_data_clear", new OneArgFunction() {
            @Override public LuaValue call(LuaValue nsArg) {
                mockStorage.remove(nsArg.optjstring("default"));
                return LuaValue.TRUE;
            }
        });
        globals.set("_avatar_resource_has", new OneArgFunction() {
            @Override public LuaValue call(LuaValue pathArg) {
                return LuaValue.valueOf("avatar.json".equals(pathArg.optjstring("")));
            }
        });
        globals.set("_avatar_resource_read", new OneArgFunction() {
            @Override public LuaValue call(LuaValue pathArg) {
                return "avatar.json".equals(pathArg.optjstring(""))
                    ? LuaValue.valueOf("{\"name\":\"TestAvatar\"}")
                    : LuaValue.NIL;
            }
        });
        java.util.Set<String> allowedChannels = new java.util.HashSet<>(java.util.List.of("shyne:sync", "avatar:ping"));
        globals.set("_avatar_net_send", new org.luaj.vm2.lib.TwoArgFunction() {
            @Override public LuaValue call(LuaValue chan, LuaValue json) {
                String c = chan.optjstring("").trim().toLowerCase(java.util.Locale.ROOT);
                if (allowedChannels.contains(c) || c.startsWith("shyne:") || c.startsWith("avatar:")) {
                    return LuaValue.TRUE;
                }
                return LuaValue.FALSE;
            }
        });
        globals.set("_avatar_net_is_allowed", new OneArgFunction() {
            @Override public LuaValue call(LuaValue chan) {
                String c = chan.optjstring("").trim().toLowerCase(java.util.Locale.ROOT);
                return LuaValue.valueOf(allowedChannels.contains(c) || c.startsWith("shyne:") || c.startsWith("avatar:"));
            }
        });
        globals.set("_avatar_net_allow_channel", new OneArgFunction() {
            @Override public LuaValue call(LuaValue chan) {
                allowedChannels.add(chan.optjstring("").trim().toLowerCase(java.util.Locale.ROOT));
                return LuaValue.TRUE;
            }
        });
        globals.set("_avatar_net_connected", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.TRUE; }
        });
        environment.budget().reset(1_000_000);
        globals.load("""
            local bindings = {}
            local function binding(id) return bindings[id] end
            function _shyne_input_bind(id, _, key, _, _, _, _, _, _, _, _, gui)
              local value = { key = key, default_key = key, enabled = true, gui = gui == true }
              bindings[id] = value
              return id
            end
            function _shyne_input_unbind(id) bindings[id] = nil; return true end
            function _shyne_input_is_down(_) return false end
            function _shyne_input_get_key(id) return binding(id) and binding(id).key or nil end
            function _shyne_input_get_key_name(id)
              local key = binding(id) and binding(id).key or ""
              return key == "key.keyboard.h" and "H" or (key == "key.keyboard.g" and "G" or "Space")
            end
            function _shyne_input_get_default_key(id) return binding(id) and binding(id).default_key or nil end
            function _shyne_input_get_id(id) return binding(id) and (binding(id).key == "key.keyboard.h" and 72 or 71) or -1 end
            function _shyne_input_set_key(id, key) if not binding(id) then return false end; binding(id).key = key; return true end
            function _shyne_input_reset(id) if not binding(id) then return false end; binding(id).key = binding(id).default_key; return true end
            function _shyne_input_is_default(id) return binding(id) and binding(id).key == binding(id).default_key or false end
            function _shyne_input_set_enabled(id, enabled) if not binding(id) then return false end; binding(id).enabled = enabled; return true end
            function _shyne_input_is_enabled(id) return binding(id) and binding(id).enabled or false end
            function _shyne_input_set_gui(id, gui) if not binding(id) then return false end; binding(id).gui = gui; return true end
            function _shyne_input_is_gui(id) return binding(id) and binding(id).gui or false end
            function _shyne_input_conflicts(_) return {} end
            function _shyne_input_vanilla_key(id) return id == "key.jump" and "key.keyboard.space" or nil end
            function _shyne_input_vanilla_name(id) return id == "key.jump" and "Jump" or nil end
            """, "figura-tier-one-input-mocks").call();
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
            case "player.body_yaw" -> LuaValue.valueOf(45.0);
            case "player.pose" -> LuaValue.valueOf("CROUCHING");
            case "player.rot" -> vector(10, 20, 0);
            case "player.look" -> vector(0, 0, 1);
            case "player.pos" -> vector(100, 64, -200);
            case "player.velocity" -> vector(0.5, 0, 0.2);
            case "player.using_item" -> LuaValue.FALSE;
            case "player.swinging" -> LuaValue.FALSE;
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
