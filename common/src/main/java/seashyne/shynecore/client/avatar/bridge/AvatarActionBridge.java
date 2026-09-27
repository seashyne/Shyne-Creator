package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarAction;
import seashyne.shynecore.client.avatar.AvatarState;

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
        VarArgFunction registerActionFunction = new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String id = args.arg(1).optjstring("action");
                String title = args.arg(2).optjstring(id);
                String description = args.arg(3).optjstring("");
                String page = args.arg(4).optjstring("main");
                boolean localOnly = args.arg(5).optboolean(false);
                boolean closeOnUse = args.arg(6).optboolean(true);
                LuaValue primaryCallback = args.arg(7);
                String icon = args.arg(8).optjstring("spark");
                LuaValue secondaryCallback = args.arg(9);

                boolean isToggle = args.arg(10).optboolean(false);
                boolean toggled = args.arg(11).optboolean(false);
                Integer color = args.arg(12).isnumber() ? args.arg(12).toint() : null;
                Integer hoverColor = args.arg(13).isnumber() ? args.arg(13).toint() : null;

                Runnable onPrimary = primaryCallback.isfunction() ? () -> {
                    try {
                        primaryCallback.call();
                    } catch (Throwable t) {
                        ShyneCore.LOGGER.error("[AvatarAction] Action '{}' primary callback failed: {}", id, t.getMessage());
                    }
                } : () -> {};

                Runnable onSecondary = secondaryCallback.isfunction() ? () -> {
                    try {
                        secondaryCallback.call();
                    } catch (Throwable t) {
                        ShyneCore.LOGGER.error("[AvatarAction] Action '{}' secondary callback failed: {}", id, t.getMessage());
                    }
                } : null;

                AvatarAction action = new AvatarAction(
                    id, title, description, page, icon,
                    localOnly, closeOnUse, isToggle, toggled,
                    color, hoverColor,
                    onPrimary, onSecondary
                );

                state.registerAction(action);
                return LuaValue.TRUE;
            }
        };

        globals.set("_avatar_action_register", registerActionFunction);
        globals.set("_avatar_action_add", registerActionFunction);

        globals.set("_avatar_action_clear", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String page = arg.optjstring(null);
                if (page == null) {
                    state.actionsByPage().clear();
                } else {
                    state.actionsByPage().remove(page);
                }
                return LuaValue.TRUE;
            }
        });
    }
}
