package seashyne.shynecore.script;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.*;
import org.luaj.vm2.lib.jse.JseMathLib;

/**
 * Creates Lua environments without filesystem, OS, network or Java reflection access.
 *
 * <p>The instruction {@link Budget} is a second safety layer: removing dangerous
 * libraries prevents capability abuse, while the budget stops infinite loops.</p>
 */
public final class LuaSandbox {
    private LuaSandbox() {}

    public static Environment create() {
        Globals globals = new Globals();
        globals.load(new BaseLib());
        globals.load(new PackageLib());
        globals.load(new TableLib());
        globals.load(new StringLib());
        globals.load(new CoroutineLib());
        globals.load(new JseMathLib());
        globals.load(new Bit32Lib());
        Budget budget = new Budget();
        globals.load(budget);
        LoadState.install(globals);
        LuaC.install(globals);

        for (String blocked : new String[]{"dofile", "loadfile", "require", "package", "io", "os", "luajava", "debug"}) {
            globals.set(blocked, LuaValue.NIL);
        }
        return new Environment(globals, budget);
    }

    public record Environment(Globals globals, Budget budget) {}

    public static final class Budget extends DebugLib {
        private int remaining;

        public void reset(int limit) {
            remaining = Math.max(1, limit);
        }

        @Override
        public void onInstruction(int pc, Varargs varargs, int top) {
            if (--remaining <= 0) throw new LuaError("Lua instruction limit exceeded");
            super.onInstruction(pc, varargs, top);
        }
    }
}
