package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.AvatarSyncedSchema;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.script.LuaValueCodec;

import java.io.IOException;
import java.util.Objects;

/**
 * Handles avatar state variables, synced properties, and client local storage.
 */
public final class AvatarStateBridge {
    private AvatarStateBridge() {}

    public static void register(Globals globals, AvatarState state) {
        globals.set("_avatar_state_get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                Object value = state.vars().get(arg.tojstring());
                return value == null ? LuaValue.NIL : LuaValueCodec.toLua(value);
            }
        });

        globals.set("_avatar_state_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String key = args.arg(1).tojstring();
                Object value = LuaValueCodec.toJava(args.arg(2));
                if (value == null) state.vars().remove(key); else state.vars().put(key, value);
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_synced_get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                Object value = state.syncedVars().get(arg.tojstring());
                return value == null ? LuaValue.NIL : LuaValueCodec.toLua(value);
            }
        });

        globals.set("_avatar_synced_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String key = args.arg(1).tojstring();
                Object value = LuaValueCodec.toJava(args.arg(2));
                if (value != null && !state.acceptsSyncedValue(key, value)) {
                    throw new LuaError("synced value does not match synced_schema: " + key);
                }
                Object previous = value == null ? state.syncedVars().remove(key) : state.syncedVars().put(key, value);
                if (!Objects.equals(previous, value)) {
                    state.markSyncedDirty();
                    state.markSnapshotDirty();
                }
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_local_get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                Object value = ShyneClientSettings.avatarLocalValue(state.avatarId(), arg.optjstring(""));
                return value == null ? LuaValue.NIL : LuaValueCodec.toLua(value);
            }
        });

        globals.set("_avatar_local_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                ShyneClientSettings.setAvatarLocalValue(state.avatarId(), args.arg(1).optjstring(""), LuaValueCodec.toJava(args.arg(2)));
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_remote_synced_get", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String playerId = args.arg(1).optjstring("");
                String key = args.arg(2).optjstring("");
                Object value = ClientAnimationState.getAvatarSyncedVar(playerId, key);
                return value == null ? LuaValue.NIL : LuaValueCodec.toLua(value);
            }
        });

        globals.set("_avatar_schema_set", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String path = arg.optjstring("");
                try {
                    state.configureSyncedSchema(path, AvatarSyncedSchema.load(state.rootDir(), path));
                } catch (IOException error) {
                    throw new LuaError("could not load synced schema: " + error.getMessage());
                }
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_schema_validate", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String key = args.arg(1).optjstring("");
                LuaValue value = args.arg(2);
                Object decoded = LuaValueCodec.toJava(value);
                boolean valid = decoded != null && state.acceptsSyncedValue(key, decoded);
                return LuaValue.valueOf(valid);
            }
        });

        globals.set("_avatar_sync_policy", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String op = args.arg(1).optjstring("");
                String key = args.arg(2).optjstring("");
                boolean value = args.arg(3).optboolean(true);
                switch (op) {
                    case "remote_snapshot" -> state.syncPolicy().setAllowRemoteSnapshot(value);
                    case "remote_vars" -> state.syncPolicy().setAllowRemoteVars(value);
                    case "allow_var" -> state.syncPolicy().allowSyncedVar(key);
                    case "local_only_part" -> state.syncPolicy().setLocalOnlyPart(key, value);
                    case "local_only_vanilla" -> state.syncPolicy().setLocalOnlyVanillaPart(key, value);
                }
                state.markSnapshotDirty();
                return LuaValue.NIL;
            }
        });
    }
}
