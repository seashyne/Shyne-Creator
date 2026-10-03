package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.render.AvatarDynamicTextureRegistry;

/**
 * Connects Figura-style Lua texture calls to Shyne's GPU-backed runtime textures.
 * เชื่อมการเรียก texture แบบ Figura ใน Lua เข้ากับ runtime texture ที่อยู่บน GPU ของ Shyne.
 */
public final class AvatarDynamicTextureBridge {
    private final AvatarState state;
    private final Object textureOwner = new Object();

    public AvatarDynamicTextureBridge(AvatarState state) {
        this.state = state;
    }

    /**
     * Registers only local visual APIs; runtime pixels are never sent to other players.
     * ลงทะเบียนเฉพาะ API ภาพในเครื่อง; pixel runtime จะไม่ถูกส่งไปยังผู้เล่นคนอื่น.
     */
    public void register(Globals globals) {
        globals.set("_avatar_dynamic_texture_create", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!visualPermissionAllowed()) return LuaValue.NIL;
                var info = AvatarDynamicTextureRegistry.create(textureOwner, state.avatarId(), args.arg(1).optjstring("texture"),
                    args.arg(2).optint(64), args.arg(3).optint(64));
                if (info == null) return LuaValue.NIL;
                LuaTable result = new LuaTable();
                result.set("id", LuaValue.valueOf(info.id()));
                result.set("width", LuaValue.valueOf(info.width()));
                result.set("height", LuaValue.valueOf(info.height()));
                return result;
            }
        });
        globals.set("_avatar_dynamic_texture_set_pixel", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(visualPermissionAllowed() && AvatarDynamicTextureRegistry.setPixel(textureOwner, state.avatarId(),
                    args.arg(1).optjstring("texture"), args.arg(2).optint(-1), args.arg(3).optint(-1), (int) args.arg(4).optlong(0)));
            }
        });
        globals.set("_avatar_dynamic_texture_fill", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(visualPermissionAllowed() && AvatarDynamicTextureRegistry.fill(textureOwner, state.avatarId(),
                    args.arg(1).optjstring("texture"), (int) args.arg(2).optlong(0)));
            }
        });
        globals.set("_avatar_dynamic_texture_apply", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(visualPermissionAllowed() && AvatarDynamicTextureRegistry.apply(textureOwner, state.avatarId(), arg.optjstring("texture")));
            }
        });
        globals.set("_avatar_dynamic_texture_bind_model", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(visualPermissionAllowed() && AvatarDynamicTextureRegistry.bindModelTexture(
                    textureOwner, state.avatarId(), state.modelId(), args.arg(1).optjstring("texture"), args.arg(2).optjstring("0")
                ));
            }
        });
    }

    /**
     * Releases only this runtime's local GPU textures when its avatar is unloaded.
     * คืนเฉพาะ texture GPU ในเครื่องของ runtime นี้เมื่อ avatar ถูก unload.
     */
    public void dispose() {
        AvatarDynamicTextureRegistry.clearOwner(textureOwner);
    }

    private boolean visualPermissionAllowed() {
        return state.permissionAllowed(AvatarPermission.HUD_RENDER) || state.permissionAllowed(AvatarPermission.WORLD_RENDER);
    }
}
