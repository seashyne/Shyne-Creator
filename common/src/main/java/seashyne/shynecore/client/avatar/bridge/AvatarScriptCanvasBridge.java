package seashyne.shynecore.client.avatar.bridge;

import net.minecraft.client.Minecraft;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.ui.AvatarScriptCanvasRegistry;
import seashyne.shynecore.script.LuaSandbox;

import java.util.function.Supplier;

/** Bridges an input-capturing, completely Lua-rendered Avatar canvas to Minecraft screens. */
public final class AvatarScriptCanvasBridge {
    private final AvatarState state;
    private final Object canvasOwner = new Object();
    private final Supplier<LuaSandbox.Budget> budgetSupplier;
    private final int instructionLimit;

    public AvatarScriptCanvasBridge(AvatarState state, Supplier<LuaSandbox.Budget> budgetSupplier, int instructionLimit) {
        this.state = state;
        this.budgetSupplier = budgetSupplier;
        this.instructionLimit = instructionLimit;
    }

    public void register(Globals globals) {
        globals.set("_shyne_ui_canvas_define", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!state.permissionAllowed(AvatarPermission.HUD_RENDER)) return LuaValue.FALSE;
                String id = args.arg(1).optjstring("");
                boolean result = AvatarScriptCanvasRegistry.define(canvasOwner, state.avatarId(), id,
                    args.arg(2).optboolean(false), (int) args.arg(3).optlong(0), args.arg(4).optboolean(true),
                    runnable(args.arg(5), "canvas_open." + id), runnable(args.arg(6), "canvas_close." + id));
                return LuaValue.valueOf(result);
            }
        });

        globals.set("_shyne_ui_canvas_open", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                if (!state.permissionAllowed(AvatarPermission.HUD_RENDER)) return LuaValue.FALSE;
                return LuaValue.valueOf(AvatarScriptCanvasRegistry.open(canvasOwner, state.avatarId(), arg.optjstring("")));
            }
        });

        globals.set("_shyne_ui_canvas_close", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(AvatarScriptCanvasRegistry.close(canvasOwner, state.avatarId(), arg.optjstring("")));
            }
        });

        globals.set("_shyne_ui_canvas_button", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!state.permissionAllowed(AvatarPermission.HUD_RENDER)) return LuaValue.FALSE;
                String canvasId = args.arg(1).optjstring("");
                String buttonId = args.arg(2).optjstring("");
                LuaValue callback = args.arg(7);
                boolean result = AvatarScriptCanvasRegistry.addButton(canvasOwner, state.avatarId(), canvasId, buttonId,
                    args.arg(3).optdouble(0), args.arg(4).optdouble(0), args.arg(5).optdouble(0), args.arg(6).optdouble(0),
                    event -> invokePointer(callback, canvasId + "." + buttonId, event));
                return LuaValue.valueOf(result);
            }
        });

        globals.set("_shyne_ui_canvas_clear_buttons", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(AvatarScriptCanvasRegistry.clearButtons(canvasOwner, state.avatarId(), arg.optjstring("")));
            }
        });
    }

    public void dispose() {
        AvatarScriptCanvasRegistry.clearOwner(canvasOwner);
    }

    private Runnable runnable(LuaValue callback, String context) {
        return () -> invoke(callback, context, LuaValue.NIL);
    }

    private void invokePointer(LuaValue callback, String context, AvatarScriptCanvasRegistry.CanvasPointerEvent event) {
        LuaTable payload = new LuaTable();
        payload.set("id", LuaValue.valueOf(event.buttonId()));
        payload.set("x", LuaValue.valueOf(event.x()));
        payload.set("y", LuaValue.valueOf(event.y()));
        payload.set("button", LuaValue.valueOf(event.mouseButton()));
        payload.set("double_click", LuaValue.valueOf(event.doubleClick()));
        invoke(callback, context, payload);
    }

    private void invoke(LuaValue callback, String context, LuaValue payload) {
        if (callback == null || !callback.isfunction()) return;
        try {
            LuaSandbox.Budget budget = budgetSupplier.get();
            if (budget != null) budget.reset(instructionLimit);
            if (payload == LuaValue.NIL) callback.call(); else callback.call(payload);
        } catch (Throwable error) {
            ShyneCore.LOGGER.error("[AvatarCanvas] {} callback failed: {}", context, error.getMessage(), error);
        }
    }
}
