package seashyne.shynecore.client.avatar.bridge;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.TwoArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarNameplateStyle;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.network.ShyneNetwork;
import seashyne.shynecore.script.LuaValueCodec;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Sandboxed bridge exposing safe JSON encoding/decoding, avatar-scoped persistent storage,
 * safe asset/resource lookup, and Nameplate 2.0 configuration.
 */
public final class AvatarDataBridge {
    private static final long MAX_RESOURCE_READ_BYTES = 2L * 1024L * 1024L; // 2MB
    private static final long MAX_DATA_FILE_BYTES = 512L * 1024L; // 512KB per namespace
    private final AvatarState state;

    public AvatarDataBridge(AvatarState state) {
        this.state = state;
    }

    public void register(Globals globals) {
        // --- JSON Bridge ---
        globals.set("_avatar_json_encode", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                try {
                    Object javaObj = LuaValueCodec.toJava(arg);
                    String json = ShyneNetwork.GSON.toJson(javaObj);
                    return LuaValue.valueOf(json);
                } catch (Exception e) {
                    throw new LuaError("JSON encode error: " + e.getMessage());
                }
            }
        });

        globals.set("_avatar_json_decode", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                if (!arg.isstring()) return LuaValue.NIL;
                String json = arg.tojstring();
                if (json.isBlank()) return LuaValue.NIL;
                try {
                    Object parsed = ShyneNetwork.GSON.fromJson(json, Object.class);
                    return LuaValueCodec.toLua(parsed);
                } catch (Exception e) {
                    throw new LuaError("JSON decode error: " + e.getMessage());
                }
            }
        });

        // --- Safe Scoped Data Storage ---
        globals.set("_avatar_data_save", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String namespace = sanitizeKey(args.arg(1).optjstring("default"));
                String key = sanitizeKey(args.arg(2).optjstring(""));
                LuaValue value = args.arg(3);
                if (key.isBlank()) return LuaValue.FALSE;
                return LuaValue.valueOf(saveDataEntry(namespace, key, value));
            }
        });

        globals.set("_avatar_data_load", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue nsArg, LuaValue keyArg) {
                String namespace = sanitizeKey(nsArg.optjstring("default"));
                String key = sanitizeKey(keyArg.optjstring(""));
                if (key.isBlank()) return LuaValue.NIL;
                return loadDataEntry(namespace, key);
            }
        });

        globals.set("_avatar_data_has", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue nsArg, LuaValue keyArg) {
                String namespace = sanitizeKey(nsArg.optjstring("default"));
                String key = sanitizeKey(keyArg.optjstring(""));
                if (key.isBlank()) return LuaValue.FALSE;
                Map<String, Object> map = readDataMap(namespace);
                return LuaValue.valueOf(map.containsKey(key));
            }
        });

        globals.set("_avatar_data_get_all", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue nsArg) {
                String namespace = sanitizeKey(nsArg.optjstring("default"));
                Map<String, Object> map = readDataMap(namespace);
                return LuaValueCodec.toLua(map);
            }
        });

        globals.set("_avatar_data_clear", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue nsArg) {
                String namespace = sanitizeKey(nsArg.optjstring("default"));
                Path file = resolveStorageFile(namespace);
                try {
                    Files.deleteIfExists(file);
                    return LuaValue.TRUE;
                } catch (IOException e) {
                    return LuaValue.FALSE;
                }
            }
        });

        // --- Safe Resources / Assets Lookup ---
        globals.set("_avatar_resource_has", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue pathArg) {
                String relative = pathArg.optjstring("");
                try {
                    Path file = AvatarLoader.resolveAvatarFile(state.rootDir(), relative);
                    return LuaValue.valueOf(Files.isRegularFile(file));
                } catch (Exception e) {
                    return LuaValue.FALSE;
                }
            }
        });

        globals.set("_avatar_resource_read", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue pathArg) {
                String relative = pathArg.optjstring("");
                try {
                    Path file = AvatarLoader.resolveAvatarFile(state.rootDir(), relative);
                    if (!Files.isRegularFile(file)) return LuaValue.NIL;
                    long size = Files.size(file);
                    if (size > MAX_RESOURCE_READ_BYTES) {
                        throw new LuaError("Resource file exceeds maximum read budget (" + MAX_RESOURCE_READ_BYTES + " bytes)");
                    }
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    return LuaValue.valueOf(content);
                } catch (LuaError le) {
                    throw le;
                } catch (Exception e) {
                    return LuaValue.NIL;
                }
            }
        });

        globals.set("_avatar_resource_list", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue dirArg) {
                String relative = dirArg.optjstring("");
                LuaTable table = new LuaTable();
                try {
                    Path dir = relative.isBlank() ? state.rootDir() : AvatarLoader.resolveAvatarFile(state.rootDir(), relative);
                    if (Files.isDirectory(dir)) {
                        try (Stream<Path> stream = Files.list(dir)) {
                            int index = 1;
                            for (Path p : stream.toList()) {
                                table.set(index++, LuaValue.valueOf(state.rootDir().relativize(p).toString().replace('\\', '/')));
                            }
                        }
                    }
                } catch (Exception ignored) {}
                return table;
            }
        });

        globals.set("_avatar_file_write", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue pathArg, LuaValue contentArg) {
                if (!state.permissionAllowed(AvatarPermission.DATA_STORAGE)) {
                    throw new LuaError("File write requires 'data' permission in avatar.json");
                }
                String relative = pathArg.checkjstring();
                try {
                    Path file = AvatarLoader.resolveAvatarFile(state.rootDir(), relative);
                    if (file.getParent() != null) Files.createDirectories(file.getParent());
                    Files.writeString(file, contentArg.checkjstring(), StandardCharsets.UTF_8);
                    return LuaValue.TRUE;
                } catch (Exception e) {
                    throw new LuaError("File write error: " + e.getMessage());
                }
            }
        });

        globals.set("_avatar_file_append", new TwoArgFunction() {
            @Override
            public LuaValue call(LuaValue pathArg, LuaValue contentArg) {
                if (!state.permissionAllowed(AvatarPermission.DATA_STORAGE)) {
                    throw new LuaError("File append requires 'data' permission in avatar.json");
                }
                String relative = pathArg.checkjstring();
                try {
                    Path file = AvatarLoader.resolveAvatarFile(state.rootDir(), relative);
                    if (file.getParent() != null) Files.createDirectories(file.getParent());
                    Files.writeString(file, contentArg.checkjstring(), StandardCharsets.UTF_8,
                        java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.APPEND);
                    return LuaValue.TRUE;
                } catch (Exception e) {
                    throw new LuaError("File append error: " + e.getMessage());
                }
            }
        });

        // --- Nameplate 2.0 Bridge ---
        globals.set("_avatar_nameplate_target_set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                String target = args.arg(1).optjstring("entity").toLowerCase(Locale.ROOT);
                String text = args.arg(2).optjstring("");
                boolean visible = args.arg(3).optboolean(true);
                String badge = args.arg(4).optjstring("");
                int color = (int) args.arg(5).optlong(0xFFFFFFFFL);
                boolean bold = args.arg(6).optboolean(false);
                boolean italic = args.arg(7).optboolean(false);

                if (target.equals("chat")) {
                    if (!state.permissionAllowed(AvatarPermission.CHAT_NAMEPLATE)) return LuaValue.FALSE;
                    state.setChatNameplate(text, visible);
                    state.setChatNameplateStyle(badge, color, bold, italic);
                    return LuaValue.TRUE;
                }
                if (target.equals("list")) {
                    if (!state.permissionAllowed(AvatarPermission.TAB_LIST_NAMEPLATE)) return LuaValue.FALSE;
                    state.setListNameplate(text, visible);
                    state.setListNameplateStyle(badge, color, bold, italic);
                    return LuaValue.TRUE;
                }
                if (target.equals("all")) {
                    if (state.permissionAllowed(AvatarPermission.NAMEPLATE)) {
                        state.setNameplate(text, visible);
                        state.setNameplateStyle(badge, color, bold, italic);
                    }
                    if (state.permissionAllowed(AvatarPermission.CHAT_NAMEPLATE)) {
                        state.setChatNameplate(text, visible);
                        state.setChatNameplateStyle(badge, color, bold, italic);
                    }
                    if (state.permissionAllowed(AvatarPermission.TAB_LIST_NAMEPLATE)) {
                        state.setListNameplate(text, visible);
                        state.setListNameplateStyle(badge, color, bold, italic);
                    }
                    return LuaValue.TRUE;
                }
                // default: entity
                if (!state.permissionAllowed(AvatarPermission.NAMEPLATE)) return LuaValue.FALSE;
                state.setNameplate(text, visible);
                state.setNameplateStyle(badge, color, bold, italic);
                return LuaValue.TRUE;
            }
        });

        globals.set("_avatar_nameplate_transform_set", new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                state.setNameplatePos((float) args.arg(1).optdouble(0), (float) args.arg(2).optdouble(0), (float) args.arg(3).optdouble(0));
                state.setNameplateScale((float) args.arg(4).optdouble(1), (float) args.arg(5).optdouble(1), (float) args.arg(6).optdouble(1));
                state.setNameplatePivot((float) args.arg(7).optdouble(0), (float) args.arg(8).optdouble(0), (float) args.arg(9).optdouble(0));
                return LuaValue.TRUE;
            }
        });

        globals.set("_avatar_nameplate_target_get", new OneArgFunction() {
            @Override
            public LuaValue call(LuaValue arg) {
                String target = arg.optjstring("entity").toLowerCase(Locale.ROOT);
                LuaTable table = new LuaTable();
                if (target.equals("chat")) {
                    table.set("text", LuaValue.valueOf(state.chatNameplateText()));
                    table.set("visible", LuaValue.valueOf(state.chatNameplateVisible()));
                    table.set("badge", LuaValue.valueOf(state.chatNameplateStyle().badge()));
                    table.set("color", LuaValue.valueOf(state.chatNameplateStyle().colorArgb()));
                    table.set("bold", LuaValue.valueOf(state.chatNameplateStyle().bold()));
                    table.set("italic", LuaValue.valueOf(state.chatNameplateStyle().italic()));
                    return table;
                }
                if (target.equals("list")) {
                    table.set("text", LuaValue.valueOf(state.listNameplateText()));
                    table.set("visible", LuaValue.valueOf(state.listNameplateVisible()));
                    table.set("badge", LuaValue.valueOf(state.listNameplateStyle().badge()));
                    table.set("color", LuaValue.valueOf(state.listNameplateStyle().colorArgb()));
                    table.set("bold", LuaValue.valueOf(state.listNameplateStyle().bold()));
                    table.set("italic", LuaValue.valueOf(state.listNameplateStyle().italic()));
                    return table;
                }
                table.set("text", LuaValue.valueOf(state.nameplateText()));
                table.set("visible", LuaValue.valueOf(state.nameplateVisible()));
                table.set("badge", LuaValue.valueOf(state.nameplateStyle().badge()));
                table.set("color", LuaValue.valueOf(state.nameplateStyle().colorArgb()));
                table.set("bold", LuaValue.valueOf(state.nameplateStyle().bold()));
                table.set("italic", LuaValue.valueOf(state.nameplateStyle().italic()));
                return table;
            }
        });
    }

    private Path resolveStorageFile(String namespace) {
        String safeAvatar = sanitizeKey(state.avatarId());
        Path baseDir;
        try {
            baseDir = AvatarLoader.avatarsDir();
        } catch (Throwable fallback) {
            baseDir = state.rootDir() != null && state.rootDir().getParent() != null
                ? state.rootDir().getParent()
                : Path.of("build/test_avatars");
        }
        Path dir = baseDir.resolve(".data").resolve(safeAvatar);
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {}
        return dir.resolve(namespace + ".json");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readDataMap(String namespace) {
        Path file = resolveStorageFile(namespace);
        if (!Files.isRegularFile(file)) return new LinkedHashMap<>();
        try {
            String json = Files.readString(file, StandardCharsets.UTF_8);
            Object parsed = ShyneNetwork.GSON.fromJson(json, Object.class);
            if (parsed instanceof Map<?, ?> map) {
                Map<String, Object> safe = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    safe.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                return safe;
            }
        } catch (Exception ignored) {}
        return new LinkedHashMap<>();
    }

    private boolean saveDataEntry(String namespace, String key, LuaValue value) {
        Map<String, Object> map = readDataMap(namespace);
        if (value.isnil()) {
            map.remove(key);
        } else {
            map.put(key, LuaValueCodec.toJava(value));
        }
        String json = ShyneNetwork.GSON.toJson(map);
        if (json.length() > MAX_DATA_FILE_BYTES) {
            throw new LuaError("Avatar data exceeds maximum storage quota (" + MAX_DATA_FILE_BYTES + " bytes)");
        }
        Path file = resolveStorageFile(namespace);
        try {
            Files.writeString(file, json, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private LuaValue loadDataEntry(String namespace, String key) {
        Map<String, Object> map = readDataMap(namespace);
        Object val = map.get(key);
        return LuaValueCodec.toLua(val);
    }

    private static String sanitizeKey(String key) {
        if (key == null) return "default";
        String safe = key.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
        return safe.isBlank() ? "default" : safe;
    }
}
