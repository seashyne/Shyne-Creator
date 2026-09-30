package seashyne.shynecore.script;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LuaBootstrapSyntaxTest {
    private static final List<String> AVATAR_MODULES = List.of(
        "/shyne_runtime/lua/avatar/00_core.lua",
        "/shyne_runtime/lua/avatar/01_shyne_core.lua",
        "/shyne_runtime/lua/avatar/05_events_scheduler.lua",
        "/shyne_runtime/lua/avatar/10_model_animation.lua",
        "/shyne_runtime/lua/avatar/20_avatar_world.lua",
        "/shyne_runtime/lua/avatar/21_vanilla_model.lua",
        "/shyne_runtime/lua/avatar/30_render_tasks.lua",
        "/shyne_runtime/lua/avatar/31_render_shapes.lua",
        "/shyne_runtime/lua/avatar/40_optional_systems.lua",
        "/shyne_runtime/lua/avatar/50_easy_api.lua",
        "/shyne_runtime/lua/avatar/59_figura_vectors.lua",
        "/shyne_runtime/lua/avatar/60_figura_compat.lua",
        "/shyne_runtime/lua/avatar/61_figura_client_renderer.lua",
        "/shyne_runtime/lua/avatar/62_figura_items_world.lua",
        "/shyne_runtime/lua/avatar/63_figura_action_wheel.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_core.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_math.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_springs.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_locomotion.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_features.lua"
    );

    @Test
    void standardBootstrapCompilesInTheSandbox() throws Exception {
        String source = avatarBootstrap();
        LuaSandbox.Environment environment = LuaSandbox.create();
        environment.budget().reset(1_000_000);
        // Loading compiles the complete Standard bundle without executing its native bridges.
        environment.globals().load(source, "shyne_avatar_bundle.lua");
        try (InputStream input = getClass().getResourceAsStream("/shyne_runtime/lua/shyne_rig.lua")) {
            assertNotNull(input, "Rig bootstrap resource must be packaged");
            environment.budget().reset(1_000_000);
            environment.globals().load(new String(input.readAllBytes(), StandardCharsets.UTF_8), "shyne_rig.lua");
        }
    }

    @Test
    void standardBootstrapRunsVectorCapabilityAndSchedulerPrimitives() throws Exception {
            String source = avatarBootstrap();
            LuaSandbox.Environment environment = LuaSandbox.create();
            var globals = environment.globals();
            globals.set("SHYNE_API_VERSION", LuaValue.valueOf("2.0"));
            globals.set("SHYNE_API_AUTOMATIC", LuaValue.TRUE);
            globals.set("_shyne_api_modules", new ZeroArgFunction() {
                @Override public LuaValue call() {
                    LuaTable modules = new LuaTable();
                    modules.set("vector", LuaValue.valueOf("1.1"));
                modules.set("scheduler", LuaValue.valueOf("1.1"));
                modules.set("rig", LuaValue.valueOf("1.3"));
                    return modules;
                }
            });
            globals.set("_shyne_api_supports", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
            });
            globals.set("_shyne_permission_allowed", constantFalse());
            globals.set("_shyne_permission_requested", constantFalse());
            globals.set("_shyne_permissions", new ZeroArgFunction() {
                @Override public LuaValue call() { return new LuaTable(); }
            });
            globals.set("_shyne_report_error", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) { return LuaValue.NIL; }
            });
            globals.set("_shyne_read", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) { return LuaValue.NIL; }
            });
            globals.set("_avatar_part_mutate", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) { return LuaValue.NIL; }
            });
            globals.set("_avatar_action_add", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
            });
            globals.set("_avatar_action_register", new VarArgFunction() {
                @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
            });
            globals.set("_avatar_vanilla_transform", new OneArgFunction() {
                @Override public LuaValue call(LuaValue arg) {
                    LuaTable vector = new LuaTable();
                    vector.set("x", LuaValue.ZERO); vector.set("y", LuaValue.ZERO); vector.set("z", LuaValue.ZERO);
                    vector.set(1, LuaValue.ZERO); vector.set(2, LuaValue.ZERO); vector.set(3, LuaValue.ZERO);
                    LuaTable transform = new LuaTable();
                    transform.set("position", vector); transform.set("rotation", vector); transform.set("visible", LuaValue.TRUE);
                    return transform;
                }
            });

            environment.budget().reset(1_000_000);
            globals.load(source, "shyne_avatar_bundle.lua").call();
            try (InputStream rigInput = getClass().getResourceAsStream("/shyne_runtime/lua/shyne_rig.lua")) {
                assertNotNull(rigInput);
                globals.load(new String(rigInput.readAllBytes(), StandardCharsets.UTF_8), "shyne_rig.lua").call();
            }
            LuaValue length = globals.load("return (vector.new(3, 0, 4) * 2):length()", "vector-test").call();
            assertEquals(10.0, length.todouble(), 0.0001);
            assertTrue(globals.get("shyne").get("api").get("automatic").toboolean());

            LuaValue easyApi = globals.load("""
                local writes, played, ticks = {}, {}, 0
                _avatar_part_mutate = function(path, operation, x, y, z)
                  writes[path] = writes[path] or {}
                  writes[path][operation] = { x = x, y = y, z = z }
                end
                model.animation.get = function(name)
                  local proxy = {}
                  function proxy:loop(value) played.loop = value; return self end
                  function proxy:additive(value) played.additive = value; return self end
                  function proxy:play() played.name = name; return self end
                  return proxy
                end
                local configured = shyne.setup({
                  parts = { Ears = { visible = true, rotation = vector.new(3, 4, 5) } },
                  animations = { ear_wiggle = { loop = true, additive = true, play = true } },
                  events = { tick = function() ticks = ticks + 1 end }
                })
                events._dispatch("tick", { type = "tick" })
                return configured.parts.Ears ~= nil and part("Ears") ~= nil
                  and type(anim) == "function" and type(on) == "function"
                  and writes["model.Ears"].visible.x == true and writes["model.Ears"].rot.x == 3
                  and played.name == "ear_wiggle" and played.loop and played.additive and ticks == 1
                """, "easy-api-test").call();
            assertTrue(easyApi.toboolean());

            LuaValue transformAndAttachment = globals.load("""
                local matrix = matrix4.multiply(matrix4.translation(vector.new(1, 2, 3)), matrix4.scale(vector.new(2, 2, 2)))
                local point = matrix4.transform_point(matrix, vector.new(1, 1, 1))
                local restored = matrix4.transform_point(matrix4.inverse(matrix), point)
                local render_calls, attached_x, attached_path, attached_local_x = 0, 0, "", 0
                _avatar_part_info = function(path)
                  return {
                    world_position = { x = 10, y = 20, z = 30 }, world_rotation = { x = 0, y = 0, z = 0 },
                    world_scale = { x = 1, y = 1, z = 1 },
                    world_matrix = { 0.0625, 0, 0, 0, 0, 0.0625, 0, 0, 0, 0, 0.0625, 0, 10, 20, 30, 1 },
                    transform_exact = true, render_context = "RENDER"
                  }
                end
                _shyne_render_task = function(id, kind, world, content, resource, x, y, z, x2, y2, z2,
                    width, height, scale, color, shadow, visible, max_distance, z_index, opacity, bone, local_x)
                  render_calls, attached_x, attached_path, attached_local_x = render_calls + 1, x, bone, local_x
                  return true
                end
                render.sprite("ear_tag", { attach = "model.Head", local_offset = vector.new(16, 0, 0) })
                events._dispatch("post_render", { context = "RENDER", delta = 0.5 })
                return math.abs(point.x - 3) < 0.0001 and math.abs(point.y - 4) < 0.0001 and math.abs(point.z - 5) < 0.0001
                  and math.abs(restored.x - 1) < 0.0001 and math.abs(restored.y - 1) < 0.0001 and math.abs(restored.z - 1) < 0.0001
                  and render_calls == 1 and math.abs(attached_x) < 0.0001
                  and attached_path == "model.Head" and math.abs(attached_local_x - 16) < 0.0001
                  and events.api_version == "2.0" and render.api_version == "1.4"
                """, "transform-attachment-test").call();
            assertTrue(transformAndAttachment.toboolean());

            LuaValue pending = globals.load("""
                task.after(1, function() return true end)
                events._dispatch("tick", { type = "tick" })
                return task.pending()
                """, "scheduler-test").call();
            assertEquals(0, pending.toint());

            LuaValue rig = globals.load("""
                local spring = rig.spring("model.Tail1", { gravity = vector.new(8, 0, 0), stiffness = 0.5, damping = 1 })
                events._dispatch("tick", { type = "tick" })
                local bounce = squapi.bounceObject:new()
                bounce:doBounce(1, 0.5, 1)
                return spring.value.x > 0 and bounce.position > 0 and shyne.api.supports("rig", ">=1.3")
                """, "rig-test").call();
            assertTrue(rig.toboolean());

            // Regression coverage for two bugs that visual smoke tests miss:
            // default armor variants must not overlap a material match, and a
            // graph without `order` must use priority deterministically.
            LuaValue rigSelection = globals.load("""
                local writes = {}
                _avatar_part_mutate = function(path, operation, value)
                  if operation == "visible" then writes[path] = value end
                end
                _shyne_read = function(key)
                  if key == "player.armor_chest" then
                    return { empty = false, id = "minecraft:diamond_chestplate", material = "minecraft:diamond" }
                  end
                  return nil
                end
                local armor = rig.armor({
                  chest = { variants = {
                    default = { "model.GenericChest" },
                    ["material:minecraft:diamond"] = { "model.DiamondChest" }
                  }}
                })
                armor:update()

                local played = {}
                model.animation.get = function(name)
                  local proxy = {}
                  function proxy:loop(value) return self end
                  function proxy:weight(value) return self end
                  function proxy:priority(value) return self end
                  function proxy:transition(value) played.transition = value; return self end
                  function proxy:fade_in(value) played.fade_in = value; return self end
                  function proxy:fade_out(value) played.fade_out = value; return self end
                  function proxy:play() played.name = name; return self end
                  function proxy:stop() return self end
                  return proxy
                end
                local graph = rig.animation_graph({
                  default = "idle", transition = 7,
                  states = {
                    walk = { when = function() return true end, priority = 10 },
                    swim = { when = function() return true end, priority = 20 },
                    idle = {}
                  }
                })
                graph:update()
                return writes["model.GenericChest"] == false and writes["model.DiamondChest"] == true
                  and graph.active == "swim" and played.name == "swim"
                  and played.fade_in == 7 and played.fade_out == 7 and played.transition == 7
                """, "rig-selection-test").call();
            assertTrue(rigSelection.toboolean());

            LuaValue squapiCompatibility = globals.load("""
                local api = squapi
                api.autoFunctionUpdates = false
                local tail = api.tail:new({ "model.Tail1", "model.Tail2" }, 0, 0, 1, 1, 2)
                local legacyTail = api.tails({ "model.LegacyTail" }, 2.5, 15, 5, 2, 1.2, 0.25, 0, 0.5, 0.01, 0.025, 60, 4, 6)
                local ears = api.ear:new("model.LeftEar", "model.RightEar")
                local legacyEars = api.ear("model.LegacyLeftEar", "model.LegacyRightEar", false, 400, 0.35, true, 1, 0.05, 0.05)
                local legacyBewb = api.bewb("model.LegacyBewb", false, 3, 0.05, 0.1)
                local leg = api.leg:new("model.LeftLeg", 0.7, false, true)
                local arm = api.arm:new("model.RightArm", 1, true, true)
                local eyes = api.eye:new("model.Eye", 0.5, 0.5, 0.5, 0.5)
                local head = api.smoothHead:new({ "model.Neck", "model.Head" }, 1, 0.1, 1, true)
                local torso = api.smoothTorso("model.Torso", 0.5, 0.4)
                function legacyTail:target(_) return 10, 0 end
                local expectedLegacyPitch = api.bouncetowards(0, 10, 0, 0.01, 0.025)
                legacyTail:tick(); tail:tick(); ears:tick(); leg:tick(); arm:tick(); eyes:tick(); torso:tick(); head:tick()
                local pos, velocity = api.bouncetowards(0, 1, 0, 0.2, 0.1)
                return #api.tails == 2 and #api.ears == 2 and #api.legs == 1 and #api.arms == 1
                  and #api.eyes == 1 and #api.smoothHeads == 2 and type(pos) == "number" and type(velocity) == "number"
                  and legacyTail.legacy and legacyTail.bendStrength == 2.5 and legacyTail.velocityPush == 0.25
                  and legacyTail.downLimit == -6 and legacyTail.upLimit == 4
                  and math.abs(legacyTail.legacyMotion[1].pitch - expectedLegacyPitch) < 0.000001
                  and ears.leftYaw ~= ears.rightYaw and head.positionIndex == 2
                  and legacyEars.legacy and legacyEars.earStiffness == 0.05 and legacyEars.earBounce == 0.05
                  and legacyBewb.legacy and legacyBewb.doIdle == false and legacyBewb.bendability == 3
                  and api.cancelHeadMovement and torso.ignoreCancelHeadMovement and torso.publishTorsoOffset
                """, "squapi-compatibility-test").call();
            assertTrue(squapiCompatibility.toboolean());

            // HoverPoint and the older FloatPoint must remain native controllers:
            // their visual collision is a world.probe response, not a Figura API
            // call or a permissive no-op.
            LuaValue squapiHoverPhysics = globals.load("""
                local writes, collision = {}, false
                _avatar_part_mutate = function(path, operation, x, y, z)
                  writes[path] = writes[path] or {}
                  writes[path][operation] = { x = x, y = y, z = z }
                end
                _shyne_read = function(key)
                  if key == "player.pos" then return { x = 10, y = 64, z = -3 } end
                  if key == "player.body_yaw" then return -180 end
                  if key == "world.probe" then
                    if collision then return { hit = true, type = "BLOCK", distance = 0.1, normal = { x = -1, y = 0, z = 0 } } end
                    return { hit = false, type = "MISS", distance = 16, normal = { x = 0, y = 0, z = 0 } }
                  end
                  return nil
                end
                local api = squapi
                api.autoFunctionUpdates = false
                local hover = api.hoverPoint:new("model.HoverRoot", vector.new(1, 2, 3), 0.2, 5, 1, 0.05, true, true)
                local legacy = api.floatPoint("model.LegacyFloat", 16, 0, 0)
                hover:tick()
                local projected = writes["model.HoverRoot"] and writes["model.HoverRoot"].pos
                collision = true
                hover.pos, hover.vel = vector.new(10, 64, -3), vector.new(1, 0, 0)
                hover:tick()
                legacy:tick()
                return #api.hoverPoints >= 1 and #api.floatPoints >= 1
                  and projected ~= nil and math.abs(projected.x - 16) < 0.0001 and math.abs(projected.y - 32) < 0.0001 and math.abs(projected.z - 48) < 0.0001
                  and hover.pos.x < 10.2 and hover.vel.x < 0
                  and legacy.points[1].pos ~= 0 and writes["model.LegacyFloat"] ~= nil
                """, "squapi-hover-physics-test").call();
            LuaValue figuraHostAndParticle = globals.load("""
                local particle_spawned = nil
                _shyne_particle_spawn = function(id, x, y, z, vx, vy, vz, r, g, b, scale)
                  particle_spawned = {
                    id = id,
                    pos = { x = x, y = y, z = z },
                    vel = { x = vx, y = vy, z = vz },
                    r = r, g = g, b = b, scale = scale
                  }
                  return true
                end

                -- Test host proxy and avatar voice helpers
                local host_ok = host ~= nil and host:isHost() == true and type(host:getAir()) == "number"
                local avatar_voice_ok = avatar:isSpeaking() ~= nil and type(avatar:getVoiceLevel()) == "number"

                -- Test particle.spawn with vector velocity
                particle.spawn("minecraft:flame", vector.new(1, 2, 3), vector.new(0, 0.5, 0))
                local flame_ok = particle_spawned ~= nil and particle_spawned.id == "minecraft:flame"
                  and particle_spawned.pos.x == 1 and particle_spawned.vel.y == 0.5

                -- Test particle.spawn with minecraft:dust and RGB color
                particle.spawn("minecraft:dust", vector.new(4, 5, 6), {
                  velocity = vector.new(0.1, 0.2, 0.3),
                  color = { 1, 0.5, 0.2 },
                  scale = 1.5
                })
                local dust_ok = particle_spawned ~= nil and particle_spawned.id == "minecraft:dust"
                  and particle_spawned.vel.x == 0.1 and particle_spawned.r == 1
                  and particle_spawned.g == 0.5 and particle_spawned.scale == 1.5

                -- Test particles:newParticle (Figura compat)
                particles:newParticle("minecraft:portal", { 7, 8, 9 }, { 0, 1, 0 })
                local figura_particle_ok = particle_spawned ~= nil and particle_spawned.id == "minecraft:portal"
                  and particle_spawned.pos.x == 7 and particle_spawned.vel.y == 1

                -- Test world.getPlayers and world.getEntities (Shyne & Figura compat)
                _shyne_read = function(key, ...)
                  if key == "world.players" then
                    return {
                      { name = "Friend1", uuid = "uuid-1", pos = { x = 10, y = 64, z = 10 }, distance = 5, health = 20, max_health = 20, crouching = false, sprinting = true, on_ground = true, is_self = false }
                    }
                  elseif key == "world.entities" then
                    return {
                      { name = "Zombie", uuid = "uuid-z", type = "minecraft:zombie", pos = { x = 12, y = 64, z = 12 }, distance = 6, health = 15, max_health = 20, is_living = true, is_monster = true, is_player = false, on_ground = true }
                    }
                  end
                  return nil
                end

                local players = world.getPlayers(32)
                local p_ok = #players == 1 and players[1]:getName() == "Friend1" and players[1]:getPos().x == 10 and players[1]:isSprinting() == true

                local entities = world.getEntities(32)
                local e_ok = #entities == 1 and entities[1]:getType() == "minecraft:zombie" and entities[1]:isMonster() == true

                local mc_players = minecraft.world.players(32)
                local mc_p_ok = #mc_players == 1 and mc_players[1].name == "Friend1"

                return host_ok and avatar_voice_ok and flame_ok and dust_ok and figura_particle_ok and p_ok and e_ok and mc_p_ok
                """, "figura-host-and-particle-test").call();
            assertTrue(figuraHostAndParticle.toboolean());
    }

    @Test
    void stringAdditionAndColorModuleSupport() throws Exception {
        String source = avatarBootstrap();
        LuaSandbox.Environment environment = LuaSandbox.create();
        var globals = environment.globals();
        globals.set("SHYNE_API_VERSION", LuaValue.valueOf("2.0"));
        globals.set("SHYNE_API_AUTOMATIC", LuaValue.TRUE);
        globals.set("_shyne_api_modules", new ZeroArgFunction() {
            @Override public LuaValue call() { return new LuaTable(); }
        });
        globals.set("_shyne_api_supports", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
        });
        globals.set("_shyne_read", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NIL; }
        });
        environment.budget().reset(1_000_000);
        globals.load(source, "shyne_avatar_bundle.lua").call();

        LuaValue result = globals.load("""
            -- 1. Number addition stays arithmetic
            local math_test = (5 + 2 == 7)

            -- 2. String + string concatenation
            local s1 = "Hello " + "World"
            local s1_ok = (s1 == "Hello World")

            -- 3. String + number concatenation
            local s2 = "HP: " + 100
            local s2_ok = (s2 == "HP: 100")

            -- 4. Color helpers with '+' operator
            local boss_title = Color.red + Color.bold + Color.skull + " LORD"
            local boss_ok = (boss_title == "§c§l☠ LORD")

            -- 5. Color functional wrappers
            local green_txt = Color.green("READY")
            local green_ok = (green_txt == "§aREADY§r")

            -- 6. Color.fmt tag parsing
            local tag_txt = Color.fmt("<red><bold><skull> BOSS</bold></red> &6[GOLD]")
            local tag_ok = (tag_txt == "§c§l☠ BOSS§r§r §6[GOLD]")

            local diag = string.format("math=%s s1=%s s2=%s boss=%s green=%s tag=%s [tag_txt='%s']",
              tostring(math_test), tostring(s1_ok), tostring(s2_ok), tostring(boss_ok), tostring(green_ok), tostring(tag_ok), tostring(tag_txt))
            if not (math_test and s1_ok and s2_ok and boss_ok and green_ok and tag_ok) then
              return diag
            end
            return "OK"
        """, "string-add-test").call();

        assertEquals("OK", result.tojstring(), "String '+' operator and Color module should work seamlessly");
    }

    @Test
    void declarativeUiFxSubsystemTest() throws Exception {
        String source = avatarBootstrap();
        LuaSandbox.Environment environment = LuaSandbox.create();
        var globals = environment.globals();
        globals.set("SHYNE_API_VERSION", LuaValue.valueOf("2.0"));
        globals.set("SHYNE_API_AUTOMATIC", LuaValue.TRUE);
        globals.set("_shyne_api_modules", new ZeroArgFunction() {
            @Override public LuaValue call() { return new LuaTable(); }
        });
        globals.set("_shyne_api_supports", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
        });
        globals.set("_shyne_read", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NIL; }
        });
        environment.budget().reset(1_000_000);
        globals.load(source, "shyne_avatar_bundle.lua").call();

        LuaValue result = globals.load("""
            -- 1. Test fx.damage spawning floating text
            local dmg_id = fx.damage(500, { x = 10, y = 64, z = 10 }, { crit = true })
            local dmg_ok = (type(dmg_id) == "string" and #fx._damages == 1 and fx._damages[1].text == "§c§l☠ CRIT! -500")

            -- 2. Test fx.bar with smooth ghost bar
            fx.bar("boss_hp", { x = 20, y = 20, width = 100, height = 8, current = 75, max = 100, text = "BOSS" })
            local bar_ok = (fx._bars["boss_hp"] ~= nil and fx._bars["boss_hp"].ghost == 75)

            -- 3. Test fx.typewriter
            local done, txt = fx.typewriter("dialog", "Hello!", { speed = 2, sound = false })
            local tw_ok = (done == false and txt == "He")

            -- 4. Test fx.pulse & fx.shake
            local p = fx.pulse({ min = 1.0, max = 2.0, time = 0 })
            local pulse_ok = (p >= 1.0 and p <= 2.0)
            local sx, sy = fx.shake(3)
            local shake_ok = (math.abs(sx) <= 3 and math.abs(sy) <= 3)

            return dmg_ok and bar_ok and tw_ok and pulse_ok and shake_ok
        """, "fx-test").call();

        assertTrue(result.toboolean(), "Declarative fx subsystem should work seamlessly");
    }

    @Test
    void audioStreamAndBeatSubsystemTest() throws IOException {
        String source = avatarBootstrap();
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        globals.set("AVATAR_ID", LuaValue.valueOf("stream_test"));
        globals.set("AVATAR_PATH", LuaValue.valueOf("/test/path"));
        globals.set("SHYNE_API_VERSION", LuaValue.valueOf("2.0"));
        globals.set("SHYNE_API_AUTOMATIC", LuaValue.TRUE);
        globals.set("_shyne_api_modules", new ZeroArgFunction() {
            @Override public LuaValue call() { return new LuaTable(); }
        });
        globals.set("_shyne_api_supports", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
        });
        globals.set("_shyne_read", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NIL; }
        });

        globals.set("_shyne_audio_stream_create", new org.luaj.vm2.lib.VarArgFunction() {
            @Override public org.luaj.vm2.Varargs invoke(org.luaj.vm2.Varargs args) {
                return LuaValue.valueOf(101);
            }
        });
        globals.set("_shyne_audio_stream_get_level", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) { return LuaValue.valueOf(0.75); }
        });
        globals.set("_shyne_audio_stream_is_beat", new org.luaj.vm2.lib.OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) { return LuaValue.TRUE; }
        });

        environment.budget().reset(1_000_000);
        globals.load(source, "shyne_avatar_bundle.lua").call();

        LuaValue result = globals.load("""
            local stream = sound.stream("https://example.com/music.ogg", { volume = 0.8, loop = true })
            local handle_ok = (stream ~= nil and stream.id == 101)
            local level_ok = (stream:getLevel() == 0.75)
            local beat_ok = (stream:isBeat() == true)

            local beat_fired = false
            fx.beat(stream, function(s)
                beat_fired = true
            end)
            events._dispatch("tick", { type = "tick" })

            return handle_ok and level_ok and beat_ok and beat_fired and (shyne.stream ~= nil)
        """, "stream-test").call();

        assertTrue(result.toboolean(), "sound.stream and fx.beat should work properly in Lua");
    }

    @Test
    void builtInLibrariesCompileAndExecute() throws Exception {
        LuaSandbox.Environment environment = LuaSandbox.create();
        Globals globals = environment.globals();
        environment.budget().reset(1_000_000);

        // 1. Classic OOP
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/lib/classic.lua")) {
            assertNotNull(in);
            LuaValue classic = globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), "classic").call();
            globals.set("Object", classic);
            LuaValue testClassic = globals.load("""
                local Point = Object:extend()
                function Point:new(x, y) self.x = x; self.y = y end
                local p = Point(10, 20)
                return p.x == 10 and p.y == 20 and p:is(Point)
            """, "classic_test").call();
            assertTrue(testClassic.toboolean(), "classic.lua should support OOP inheritance");
        }

        // 2. Tween
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/lib/tween.lua")) {
            assertNotNull(in);
            LuaValue tween = globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), "tween").call();
            globals.set("tween", tween);
            LuaValue testTween = globals.load("""
                local pos = { x = 0 }
                local t = tween.new(2.0, pos, { x = 100 }, 'linear')
                t:update(1.0)
                local half = (pos.x == 50)
                t:update(1.0)
                local done = (pos.x == 100)
                return half and done
            """, "tween_test").call();
            assertTrue(testTween.toboolean(), "tween.lua should calculate linear and easing interpolations");
        }

        // 3. Inspect
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/lib/inspect.lua")) {
            assertNotNull(in);
            LuaValue inspect = globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), "inspect").call();
            globals.set("inspect", inspect);
            LuaValue testInspect = globals.load("""
                local data = { hp = 100, name = "Hero", tags = {"a", "b"} }
                local text = inspect(data)
                return type(text) == "string" and text:find("hp = 100") ~= nil
            """, "inspect_test").call();
            assertTrue(testInspect.toboolean(), "inspect.lua should serialize tables to readable strings");
        }

        // 4. Noise
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/lib/noise.lua")) {
            assertNotNull(in);
            LuaValue noise = globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), "noise").call();
            globals.set("noise", noise);
            LuaValue testNoise = globals.load("""
                local val1 = noise.perlin(1.5, 2.5, 3.5)
                local val2 = noise.perlin2d(10.2, 5.8)
                return type(val1) == "number" and type(val2) == "number"
            """, "noise_test").call();
            assertTrue(testNoise.toboolean(), "noise.lua should compute Perlin and Simplex noise");
        }

        // 5. Vector
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/lib/vector.lua")) {
            assertNotNull(in);
            LuaValue vector = globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), "vector").call();
            globals.set("vector", vector);
            LuaValue testVector = globals.load("""
                local v1 = vector(1, 2, 3)
                local v2 = vector(4, 5, 6)
                local v3 = v1 + v2
                local dot = v1:dot(v2)
                local mid = v1:lerp(v2, 0.5)
                return v3.x == 5 and v3.y == 7 and v3.z == 9 and dot == 32 and mid.x == 2.5
            """, "vector_test").call();
            assertTrue(testVector.toboolean(), "vector.lua should compute vector additions, dots, and lerps");
        }

        // 6. Signal
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/lib/signal.lua")) {
            assertNotNull(in);
            LuaValue signal = globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), "signal").call();
            globals.set("signal", signal);
            LuaValue testSignal = globals.load("""
                local sig = signal()
                local received = 0
                local disconnect = sig:connect(function(val)
                    received = received + val
                end)
                sig:fire(10)
                sig:fire(5)
                disconnect()
                sig:fire(100)
                return received == 15 and sig:count() == 0
            """, "signal_test").call();
            assertTrue(testSignal.toboolean(), "signal.lua should handle connect, fire, and disconnect");
        }

        // 7. Color
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/lib/color.lua")) {
            assertNotNull(in);
            LuaValue color = globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), "color").call();
            globals.set("color", color);
            LuaValue testColor = globals.load("""
                local c1 = color.fromHex('#FF0000')
                local c2 = color.fromHex('#0000FF')
                local mixed = c1:lerp(c2, 0.5)
                return c1.r == 1 and c1.b == 0 and mixed.r > 0.4 and mixed.b > 0.4
            """, "color_test").call();
            assertTrue(testColor.toboolean(), "color.lua should parse hex and calculate lerp");
        }

        // 8. Timer
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/lib/timer.lua")) {
            assertNotNull(in);
            LuaValue timer = globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), "timer").call();
            globals.set("timer", timer);
            LuaValue testTimer = globals.load("""
                local count = 0
                timer.after(0.5, function() count = count + 1 end)
                timer.update(0.2)
                local before = (count == 0)
                timer.update(0.4)
                local after = (count == 1)
                return before and after
            """, "timer_test").call();
            assertTrue(testTimer.toboolean(), "timer.lua should schedule and execute delayed tasks");
        }
    }

    @Test
    void universalReplaceVanillaSupportsAllFormats() throws Exception {
        String source = avatarBootstrap();
        LuaSandbox.Environment environment = LuaSandbox.create();
        var globals = environment.globals();
        globals.set("SHYNE_API_VERSION", LuaValue.valueOf("2.0"));
        globals.set("SHYNE_API_AUTOMATIC", LuaValue.TRUE);
        globals.set("_shyne_api_modules", new ZeroArgFunction() {
            @Override public LuaValue call() { return new LuaTable(); }
        });
        globals.set("_shyne_api_supports", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.TRUE; }
        });
        globals.set("_shyne_permission_allowed", constantFalse());
        globals.set("_shyne_permission_requested", constantFalse());
        globals.set("_shyne_permissions", new ZeroArgFunction() {
            @Override public LuaValue call() { return new LuaTable(); }
        });
        globals.set("_shyne_report_error", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NIL; }
        });
        globals.set("_shyne_read", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) { return LuaValue.NIL; }
        });

        java.util.Map<String, Boolean> visibilityLog = new java.util.HashMap<>();
        globals.set("_avatar_vanilla_visible", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                visibilityLog.put(args.arg(1).tojstring(), args.arg(2).toboolean());
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_vanilla_transform", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                LuaTable t = new LuaTable();
                t.set("visible", LuaValue.TRUE);
                return t;
            }
        });

        environment.budget().reset(1_000_000);
        globals.load(source, "shyne_avatar_bundle.lua").call();

        // 1. Boolean format
        globals.load("shyne.replace_vanilla(true)", "test").call();
        assertEquals(Boolean.FALSE, visibilityLog.get("PLAYER"), "Boolean true should hide PLAYER");

        // 2. String format (compound "arms")
        globals.load("shyne.replace_vanilla('arms')", "test").call();
        assertEquals(Boolean.TRUE, visibilityLog.get("PLAYER"), "Partial string should keep PLAYER visible");
        assertEquals(Boolean.FALSE, visibilityLog.get("RIGHT_ARM"), "arms should hide RIGHT_ARM");
        assertEquals(Boolean.FALSE, visibilityLog.get("LEFT_ARM"), "arms should hide LEFT_ARM");
        assertEquals(Boolean.FALSE, visibilityLog.get("RIGHT_SLEEVE"), "arms should hide RIGHT_SLEEVE");
        assertEquals(Boolean.FALSE, visibilityLog.get("LEFT_SLEEVE"), "arms should hide LEFT_SLEEVE");

        // 3. Comma-separated string format ("arms, legs")
        globals.load("shyne.replace_vanilla('arms, legs')", "test").call();
        assertEquals(Boolean.FALSE, visibilityLog.get("RIGHT_LEG"), "arms, legs should hide RIGHT_LEG");
        assertEquals(Boolean.FALSE, visibilityLog.get("LEFT_LEG"), "arms, legs should hide LEFT_LEG");
        assertEquals(Boolean.FALSE, visibilityLog.get("RIGHT_PANTS"), "arms, legs should hide RIGHT_PANTS");

        // 4. Array table format
        globals.load("shyne.replace_vanilla({'head', 'torso'})", "test").call();
        assertEquals(Boolean.FALSE, visibilityLog.get("HEAD"), "array should hide HEAD");
        assertEquals(Boolean.FALSE, visibilityLog.get("HAT"), "array should hide HAT");
        assertEquals(Boolean.FALSE, visibilityLog.get("BODY"), "array should hide BODY");
        assertEquals(Boolean.FALSE, visibilityLog.get("JACKET"), "array should hide JACKET");

        // 5. Key-value table format
        globals.load("shyne.replace_vanilla({ right_arm = true, left_arm = false })", "test").call();
        assertEquals(Boolean.FALSE, visibilityLog.get("RIGHT_ARM"), "key-value should hide right_arm");
        assertEquals(Boolean.TRUE, visibilityLog.get("LEFT_ARM"), "key-value should show left_arm");

        // 6. Declarative shyne.setup
        globals.load("shyne.setup({ replace_vanilla = { 'legs' } })", "test").call();
        assertEquals(Boolean.FALSE, visibilityLog.get("RIGHT_LEG"), "shyne.setup should configure replace_vanilla");

        // 7. vanilla_model proxy
        globals.load("vanilla_model.ARMS:setVisible(false)", "test").call();
        assertEquals(Boolean.FALSE, visibilityLog.get("RIGHT_ARM"), "vanilla_model.ARMS should hide RIGHT_ARM");
        assertEquals(Boolean.FALSE, visibilityLog.get("LEFT_ARM"), "vanilla_model.ARMS should hide LEFT_ARM");

        globals.load("vanilla_model.ARMS:setVisible(true)", "test").call();
        assertEquals(Boolean.TRUE, visibilityLog.get("RIGHT_ARM"), "vanilla_model.ARMS should restore RIGHT_ARM");
        assertEquals(Boolean.TRUE, visibilityLog.get("LEFT_ARM"), "vanilla_model.ARMS should restore LEFT_ARM");
    }

    private String avatarBootstrap() throws IOException {
        StringBuilder source = new StringBuilder(48 * 1024);
        for (String resource : AVATAR_MODULES) {
            try (InputStream input = getClass().getResourceAsStream(resource)) {
                assertNotNull(input, "Lua API module must be packaged: " + resource);
                source.append(new String(input.readAllBytes(), StandardCharsets.UTF_8)).append('\n');
            }
        }
        return source.toString();
    }

    private static OneArgFunction constantFalse() {
        return new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) { return LuaValue.FALSE; }
        };
    }
}
