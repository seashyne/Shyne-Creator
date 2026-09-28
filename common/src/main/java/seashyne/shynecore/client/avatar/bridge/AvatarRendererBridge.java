package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarState;

import static seashyne.shynecore.client.avatar.bridge.AvatarBridgeHelper.vec3;

/**
 * Owns the state bridge behind Figura's local {@code renderer} global.
 *
 * <p>Calls arrive on the client Lua/render thread. The bridge writes only the
 * active avatar's local render state; it never changes persistent Minecraft
 * settings or sends camera values to other players.</p>
 */
public final class AvatarRendererBridge {
    private AvatarRendererBridge() {}

    /**
     * Installs native setter and getter functions used by the Lua Figura
     * renderer facade.
     *
     * @param globals active sandbox globals
     * @param state active avatar's thread-visible state
     */
    public static void register(Globals globals, AvatarState state) {
        globals.set("_figura_renderer_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                requireCameraPermission(state);
                String key = args.arg(1).optjstring("");
                switch (key) {
                    case "shadow_radius" -> {
                        if (args.arg(2).isnil()) state.setShadowRadius(-1f);
                        else state.setShadowRadius(clamp(number(args, 2, 0f), 0f, 12f));
                    }
                    case "camera_pivot" -> {
                        state.setCameraPivot(number(args, 2, 0f), number(args, 3, 0f), number(args, 4, 0f));
                    }
                    case "camera_pos" -> state.setCameraOffset(number(args, 2, 0f), number(args, 3, 0f), number(args, 4, 0f));
                    case "camera_rot" -> {
                        state.setCameraAbsoluteRotation(number(args, 2, 0f), number(args, 3, 0f), number(args, 4, 0f));
                    }
                    case "fov" -> {
                        if (args.arg(2).isnil()) state.setCameraFovMultiplier(Float.NaN);
                        else state.setCameraFovMultiplier(number(args, 2, Float.NaN));
                    }
                }
                return LuaValue.NIL;
            }
        });

        globals.set("_figura_renderer_get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return switch (arg.optjstring("")) {
                    case "shadow_radius" -> state.shadowRadius() < 0f
                        ? LuaValue.NIL : LuaValue.valueOf(state.shadowRadius());
                    case "camera_pivot" -> state.cameraPivotControlled()
                        ? vec3(state.cameraPivotX(), state.cameraPivotY(), state.cameraPivotZ()) : LuaValue.NIL;
                    case "camera_pos" -> vec3(state.cameraOffsetX(), state.cameraOffsetY(), state.cameraOffsetZ());
                    case "camera_rot" -> state.cameraAbsoluteRotationControlled()
                        ? vec3(state.cameraAbsoluteRotationX(), state.cameraAbsoluteRotationY(), state.cameraAbsoluteRotationZ()) : LuaValue.NIL;
                    case "fov" -> Float.isFinite(state.cameraFovMultiplier())
                        ? LuaValue.valueOf(state.cameraFovMultiplier()) : LuaValue.NIL;
                    default -> LuaValue.NIL;
                };
            }
        });
    }

    private static void requireCameraPermission(AvatarState state) {
        if (!state.permissionAllowed(AvatarPermission.CAMERA)) {
            throw new LuaError("camera permission is not granted for this avatar");
        }
    }

    private static float number(Varargs args, int index, float fallback) {
        double value = args.arg(index).optdouble(fallback);
        return Double.isFinite(value) ? (float) value : fallback;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
