package seashyne.shynecore.script;

import org.junit.jupiter.api.Test;
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
        "/shyne_runtime/lua/avatar/10_model_animation.lua",
        "/shyne_runtime/lua/avatar/20_avatar_world.lua",
        "/shyne_runtime/lua/avatar/30_render_tasks.lua",
        "/shyne_runtime/lua/avatar/31_render_shapes.lua",
        "/shyne_runtime/lua/avatar/40_optional_systems.lua",
        "/shyne_runtime/lua/avatar/50_easy_api.lua"
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
                  and events.api_version == "2.0" and render.api_version == "1.3"
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
            assertTrue(squapiHoverPhysics.toboolean());
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
