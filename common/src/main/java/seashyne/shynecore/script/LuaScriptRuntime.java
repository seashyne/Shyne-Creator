package seashyne.shynecore.script;

import com.google.gson.JsonObject;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.bridge.BridgeProtocol;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

public class LuaScriptRuntime {
    private static final int LOAD_INSTRUCTION_LIMIT = 1_000_000;
    private static final int HOOK_INSTRUCTION_LIMIT = 250_000;
    private final String modId;
    private final Path scriptPath;
    private final Consumer<JsonObject> sink;
    private Globals globals;
    private LuaSandbox.Budget instructionLimiter;

    public LuaScriptRuntime(String modId, Path scriptPath, Consumer<JsonObject> sink) {
        this.modId = modId;
        this.scriptPath = scriptPath;
        this.sink = sink;
    }

    public boolean load() {
        try {
            globals = createSandboxGlobals();
            installApi(globals);
            globals.set("MOD_ID", LuaValue.valueOf(modId));
            instructionLimiter.reset(LOAD_INSTRUCTION_LIMIT);
            loadBootstrap(globals);
            String source = Files.readString(scriptPath);
            instructionLimiter.reset(LOAD_INSTRUCTION_LIMIT);
            globals.load(source, scriptPath.getFileName().toString()).call();
            return true;
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[Lua] Could not load {}: {}", scriptPath, e.getMessage(), e);
            return false;
        }
    }

    public void callHook(String hook, Map<String, Object> args) {
        try {
            if (globals == null) return;
            LuaTable ctx = toLuaTable(args);
            globals.set("ctx", ctx);
            globals.set("HOOK", LuaValue.valueOf(hook));
            LuaValue fn = globals.get(hook);
            if (fn.isnil() || !fn.isfunction()) return;
            instructionLimiter.reset(HOOK_INSTRUCTION_LIMIT);
            fn.call(ctx);
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[Lua:{}] Hook {} failed: {}", modId, hook, e.getMessage(), e);
        }
    }

    private Globals createSandboxGlobals() {
        LuaSandbox.Environment environment = LuaSandbox.create();
        instructionLimiter = environment.budget();
        return environment.globals();
    }

    private void loadBootstrap(Globals g) {
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/shyne_easy.lua")) {
            if (in == null) {
                ShyneCore.LOGGER.warn("[Lua] shyne_easy.lua bootstrap not found; Lua mods will use low-level API only.");
                return;
            }
            String bootstrap = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            g.load(bootstrap, "shyne_easy.lua").call();
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[Lua] Failed to load shyne_easy.lua: {}", e.getMessage(), e);
        }
    }

    private void installApi(Globals g) {
        g.set("send_message", actionFn(BridgeProtocol.ACT_SEND_MESSAGE, new String[]{"text", "player"}));
        g.set("broadcast", new OneArgFunction() {
            @Override public LuaValue call(LuaValue text) {
                emit(BridgeProtocol.ACT_SEND_MESSAGE, mapOf("text", text.tojstring()));
                return LuaValue.NIL;
            }
        });
        g.set("run_command", actionFn(BridgeProtocol.ACT_RUN_COMMAND, new String[]{"command"}));
        g.set("give_item", actionFn(BridgeProtocol.ACT_GIVE_ITEM, new String[]{"player", "item", "count"}));
        g.set("give_shyne_item", actionFn(BridgeProtocol.ACT_GIVE_SHYNE_ITEM, new String[]{"player", "item", "count"}));
        g.set("set_block", actionFn(BridgeProtocol.ACT_SET_BLOCK, new String[]{"x", "y", "z", "block"}));
        g.set("play_sound", actionFn(BridgeProtocol.ACT_PLAY_SOUND, new String[]{"player", "sound", "source"}));
        g.set("load_bbmodel", actionFn(BridgeProtocol.ACT_LOAD_BBMODEL, new String[]{"path"}));
        g.set("attach_model", actionFn(BridgeProtocol.ACT_ATTACH_MODEL, new String[]{"entity", "model", "offset_x", "offset_y", "offset_z", "scale", "anchor_bone"}));
        g.set("detach_model", actionFn(BridgeProtocol.ACT_DETACH_MODEL, new String[]{"entity"}));
        g.set("play_animation", actionFn(BridgeProtocol.ACT_PLAY_ANIMATION, new String[]{"player", "model", "animation", "sound", "source"}));
        g.set("play_animation_entity", actionFn(BridgeProtocol.ACT_PLAY_ANIMATION_ENTITY, new String[]{"entity", "model", "animation"}));
        g.set("stop_animation", actionFn(BridgeProtocol.ACT_STOP_ANIMATION, new String[]{"player"}));
        g.set("stop_animation_entity", actionFn(BridgeProtocol.ACT_STOP_ANIMATION_ENTITY, new String[]{"entity"}));
        g.set("summon_entity", actionFn(BridgeProtocol.ACT_SUMMON_ENTITY, new String[]{"owner", "entity_type", "model", "animation", "duration_ticks", "offset_x", "offset_y", "offset_z", "scale", "tag"}));
        g.set("despawn_entity", actionFn(BridgeProtocol.ACT_DESPAWN_ENTITY, new String[]{"entity"}));
        g.set("despawn_owner_summons", actionFn(BridgeProtocol.ACT_DESPAWN_OWNER_SUMMONS, new String[]{"owner"}));
        g.set("launch_projectile", actionFn(BridgeProtocol.ACT_LAUNCH_PROJECTILE, new String[]{"owner", "entity_type", "model", "animation", "impact_animation", "speed", "damage", "hitbox_radius", "duration_ticks", "scale", "tag", "homing", "homing_strength", "pierce", "explode_radius"}));
        g.set("advance_combo", actionFn(BridgeProtocol.ACT_ADVANCE_COMBO, new String[]{"entity", "combo", "branch", "max_stage", "reset_ticks", "model", "animation_prefix"}));
        g.set("reset_combo", actionFn(BridgeProtocol.ACT_RESET_COMBO, new String[]{"entity"}));
        g.set("set_team", actionFn(BridgeProtocol.ACT_SET_TEAM, new String[]{"entity", "team"}));
        g.set("configure_team", actionFn(BridgeProtocol.ACT_CONFIGURE_TEAM, new String[]{"team", "friendly_fire", "target_mobs", "target_players"}));
        g.set("set_mana", actionFn(BridgeProtocol.ACT_SET_MANA, new String[]{"entity", "mana", "max_mana"}));
        g.set("grant_xp", actionFn(BridgeProtocol.ACT_GRANT_XP, new String[]{"entity", "amount"}));
        g.set("unlock_skill", actionFn(BridgeProtocol.ACT_UNLOCK_SKILL, new String[]{"entity", "skill"}));
        g.set("equip_skill", actionFn(BridgeProtocol.ACT_EQUIP_SKILL, new String[]{"entity", "slot", "skill"}));
        g.set("equip_weapon", actionFn(BridgeProtocol.ACT_EQUIP_WEAPON, new String[]{"entity", "slot", "weapon"}));
        g.set("cast_skill", actionFn(BridgeProtocol.ACT_CAST_SKILL, new String[]{"entity", "skill"}));
        g.set("schedule", actionFn(BridgeProtocol.ACT_SCHEDULE, new String[]{"hook", "delay_ticks"}));
        g.set("log_info", logFn("info"));
        g.set("log_warn", logFn("warn"));
        g.set("log_error", logFn("error"));
        g.set("_emit_action", emitActionFn());
    }

    private OneArgFunction logFn(String level) {
        return new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                emit(BridgeProtocol.ACT_LOG, mapOf("level", level, "text", arg.tojstring()));
                return LuaValue.NIL;
            }
        };
    }

    private VarArgFunction emitActionFn() {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String action = args.arg(1).optjstring("");
                Map<String, Object> payload = new LinkedHashMap<>();
                LuaValue second = args.arg(2);
                if (second.istable()) payload.putAll(tableToMap(second.checktable()));
                emit(action, payload);
                return LuaValue.NIL;
            }
        };
    }

    private VarArgFunction actionFn(String action, String[] keys) {
        return new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Map<String, Object> payload = new LinkedHashMap<>();
                if (args.narg() == 1 && args.arg1().istable()) {
                    payload.putAll(tableToMap(args.arg1().checktable()));
                } else {
                    int count = Math.min(args.narg(), keys.length);
                    for (int i = 0; i < count; i++) {
                        LuaValue v = args.arg(i + 1);
                        if (!v.isnil()) payload.put(keys[i], fromLua(v));
                    }
                }
                emit(action, payload);
                return LuaValue.NIL;
            }
        };
    }

    private Map<String, Object> tableToMap(LuaTable table) {
        Map<String, Object> payload = new LinkedHashMap<>();
        LuaValue key = LuaValue.NIL;
        while (true) {
            Varargs next = table.next(key);
            key = next.arg1();
            if (key.isnil()) break;
            LuaValue value = next.arg(2);
            if (!value.isnil()) payload.put(key.tojstring(), fromLua(value));
        }
        return payload;
    }

    private void emit(String action, Map<String, Object> args) {
        JsonObject msg = new JsonObject();
        if (BridgeProtocol.ACT_SCHEDULE.equals(action)) {
            msg.addProperty(BridgeProtocol.F_TYPE, BridgeProtocol.RES_SCHEDULE);
            msg.addProperty(BridgeProtocol.F_MOD_ID, modId);
            Object hook = args.getOrDefault("hook", "on_tick");
            Object delay = args.getOrDefault("delay_ticks", 20);
            msg.addProperty(BridgeProtocol.F_HOOK, String.valueOf(hook));
            msg.addProperty(BridgeProtocol.F_DELAY, delay instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(delay)));
        } else {
            msg.addProperty(BridgeProtocol.F_TYPE, BridgeProtocol.RES_ACTION);
            msg.addProperty(BridgeProtocol.F_MOD_ID, modId);
            msg.addProperty(BridgeProtocol.F_ACTION, action);
            JsonObject payload = new JsonObject();
            for (Map.Entry<String, Object> entry : args.entrySet()) addJson(payload, entry.getKey(), entry.getValue());
            msg.add(BridgeProtocol.F_ARGS, payload);
        }
        sink.accept(msg);
    }

    private void addJson(JsonObject out, String key, Object value) {
        if (value == null) return;
        if (value instanceof Number n) out.addProperty(key, n);
        else if (value instanceof Boolean b) out.addProperty(key, b);
        else out.addProperty(key, String.valueOf(value));
    }

    private Object fromLua(LuaValue value) {
        if (value.isnil()) return null;
        if (value.isboolean()) return value.toboolean();
        if (value.isint()) return value.toint();
        if (value.isnumber()) return value.todouble();
        if (value.istable()) return tableToMap(value.checktable()).toString();
        return value.tojstring();
    }

    private LuaTable toLuaTable(Map<String, Object> args) {
        LuaTable table = new LuaTable();
        for (Map.Entry<String, Object> entry : args.entrySet()) table.set(entry.getKey(), toLuaValue(entry.getValue()));
        return table;
    }

    private LuaValue toLuaValue(Object value) {
        if (value == null) return LuaValue.NIL;
        if (value instanceof Map<?, ?> map) {
            LuaTable table = new LuaTable();
            for (Map.Entry<?, ?> entry : map.entrySet()) table.set(String.valueOf(entry.getKey()), toLuaValue(entry.getValue()));
            return table;
        }
        if (value instanceof Boolean b) return LuaValue.valueOf(b);
        if (value instanceof Integer i) return LuaValue.valueOf(i);
        if (value instanceof Long l) return LuaValue.valueOf(l);
        if (value instanceof Float f) return LuaValue.valueOf(f);
        if (value instanceof Double d) return LuaValue.valueOf(d);
        return LuaValue.valueOf(String.valueOf(value));
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) out.put(String.valueOf(values[i]), values[i + 1]);
        return out;
    }

}
