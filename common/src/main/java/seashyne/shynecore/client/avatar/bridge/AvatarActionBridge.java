package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarAction;
import seashyne.shynecore.client.avatar.AvatarState;

import java.util.function.IntConsumer;

/**
 * Bridges Lua Action Wheel and UI Palette declarations with {@link AvatarState}.
 *
 * <p>Supports both Shyne native {@code ui.action} / {@code ui.toggle} APIs
 * and Figura-compatible {@code action_wheel} pages and actions.</p>
 */
public final class AvatarActionBridge {
    private AvatarActionBridge() {}

    /**
     * Registers Action Wheel Lua bridge functions into the provided Lua global environment.
     *
     * @param globals The target sandboxed Lua globals table
     * @param state   The active avatar state instance
     */
    public static void register(Globals globals, AvatarState state) {
        Runnable resetBudget = () -> {
            try {
                LuaValue resetFn = globals.get("_shyne_reset_event_budget");
                if (resetFn != null && resetFn.isfunction()) {
                    resetFn.call();
                }
            } catch (Throwable ignored) {}
        };

        VarArgFunction registerActionFunction = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String id = args.arg(1).optjstring("action");
                String title = args.arg(2).isnil() ? id : args.arg(2).optjstring("");
                String description = args.arg(3).optjstring("");
                String page = args.arg(4).optjstring("main");
                boolean localOnly = args.arg(5).optboolean(false);
                boolean closeOnUse = args.arg(6).optboolean(true);
                LuaValue primaryCallback = args.arg(7);
                String icon = args.arg(8).optjstring("");
                LuaValue secondaryCallback = args.arg(9);

                boolean isToggle = args.arg(10).optboolean(false);
                boolean toggled = args.arg(11).optboolean(false);
                Integer color = args.arg(12).isnumber() ? (int) args.arg(12).tolong() : null;
                Integer hoverColor = args.arg(13).isnumber() ? (int) args.arg(13).tolong() : null;

                int slotIndex = args.arg(14).optint(-1);
                String texture = args.arg(15).optjstring("");
                int texU = args.arg(16).optint(0);
                int texV = args.arg(17).optint(0);
                int texW = args.arg(18).optint(0);
                int texH = args.arg(19).optint(0);
                float texScale = (float) args.arg(20).optdouble(1.0);
                Integer toggleColor = args.arg(21).isnumber() ? (int) args.arg(21).tolong() : null;
                LuaValue scrollCallback = args.arg(22);

                Runnable onPrimary = primaryCallback.isfunction() ? () -> {
                    try {
                        resetBudget.run();
                        primaryCallback.call();
                    } catch (Throwable t) {
                        ShyneCore.LOGGER.error("[AvatarAction] Action '{}' primary callback failed: {}", id, t.getMessage());
                    }
                } : () -> {};

                Runnable onSecondary = secondaryCallback.isfunction() ? () -> {
                    try {
                        resetBudget.run();
                        secondaryCallback.call();
                    } catch (Throwable t) {
                        ShyneCore.LOGGER.error("[AvatarAction] Action '{}' secondary callback failed: {}", id, t.getMessage());
                    }
                } : null;

                IntConsumer onScroll = scrollCallback.isfunction() ? dir -> {
                    try {
                        resetBudget.run();
                        scrollCallback.call(LuaValue.valueOf(dir));
                    } catch (Throwable t) {
                        ShyneCore.LOGGER.error("[AvatarAction] Action '{}' scroll callback failed: {}", id, t.getMessage());
                    }
                } : null;

                AvatarAction action = new AvatarAction(
                    id, title, description, page, icon,
                    localOnly, closeOnUse, isToggle, toggled,
                    color, hoverColor, toggleColor,
                    slotIndex, texture, texU, texV, texW, texH, texScale,
                    onPrimary, onSecondary, onScroll
                );

                state.registerAction(action);
                return LuaValue.TRUE;
            }
        };

        globals.set("_avatar_action_register", registerActionFunction);
        globals.set("_avatar_action_add", registerActionFunction);

        globals.set("_avatar_action_unregister", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue pageArg, LuaValue idArg) {
                state.unregisterAction(pageArg.optjstring(null), idArg.optjstring(null));
                return LuaValue.TRUE;
            }
        });

        globals.set("_avatar_action_clear", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                state.clearActions(arg.optjstring(null));
                return LuaValue.TRUE;
            }
        });

        globals.set("_avatar_action_register_page", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                state.registerActionPage(arg.optjstring(""));
                return LuaValue.TRUE;
            }
        });

        globals.set("_avatar_action_set_page", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                state.setActiveActionPage(arg.isnil() ? "" : arg.optjstring(""));
                return LuaValue.TRUE;
            }
        });

        globals.set("_avatar_action_get_page", new ZeroArgFunction() {
            @Override
            public LuaValue call() {
                String active = state.activeActionPage();
                return active == null || active.isEmpty() ? LuaValue.NIL : LuaValue.valueOf(active);
            }
        });
    }
}
