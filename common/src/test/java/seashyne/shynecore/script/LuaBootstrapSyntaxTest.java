package seashyne.shynecore.script;

import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class LuaBootstrapSyntaxTest {
    @Test
    void standardBootstrapCompilesInTheSandbox() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/shyne_runtime/lua/shyne_avatar.lua")) {
            assertNotNull(input, "Lua API bootstrap resource must be packaged");
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            LuaSandbox.Environment environment = LuaSandbox.create();
            environment.budget().reset(1_000_000);
            // Loading compiles the complete Standard file without executing its native bridges.
            environment.globals().load(source, "shyne_avatar.lua");
        }
    }

    @Test
    void standardBootstrapRunsVectorCapabilityAndSchedulerPrimitives() throws Exception {
        try (InputStream input = getClass().getResourceAsStream("/shyne_runtime/lua/shyne_avatar.lua")) {
            assertNotNull(input);
            String source = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            LuaSandbox.Environment environment = LuaSandbox.create();
            var globals = environment.globals();
            globals.set("SHYNE_API_VERSION", LuaValue.valueOf("1.1"));
            globals.set("SHYNE_API_AUTOMATIC", LuaValue.TRUE);
            globals.set("_shyne_api_modules", new ZeroArgFunction() {
                @Override public LuaValue call() {
                    LuaTable modules = new LuaTable();
                    modules.set("vector", LuaValue.valueOf("1.1"));
                    modules.set("scheduler", LuaValue.valueOf("1.1"));
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

            environment.budget().reset(1_000_000);
            globals.load(source, "shyne_avatar.lua").call();
            LuaValue length = globals.load("return (vector.new(3, 0, 4) * 2):length()", "vector-test").call();
            assertEquals(10.0, length.todouble(), 0.0001);
            assertTrue(globals.get("shyne").get("api").get("automatic").toboolean());

            LuaValue pending = globals.load("""
                task.after(1, function() return true end)
                events._dispatch("tick", { type = "tick" })
                return task.pending()
                """, "scheduler-test").call();
            assertEquals(0, pending.toint());
        }
    }

    private static OneArgFunction constantFalse() {
        return new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) { return LuaValue.FALSE; }
        };
    }
}
