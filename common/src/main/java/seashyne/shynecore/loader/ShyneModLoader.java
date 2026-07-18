package seashyne.shynecore.loader;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.bridge.ActionBus;
import seashyne.shynecore.diagnostics.ContentDiagnostics;
import seashyne.shynecore.model.BbModelRegistry;
import seashyne.shynecore.script.LuaScriptRuntime;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.*;
import java.util.*;

/** Loads Lua-only content packs from shyne-mods/. */
public final class ShyneModLoader {
    private static final Gson GSON = new Gson();

    private final ActionBus actionBus;
    private final Path shyneModsDir;
    private final List<ModInfo> loadedMods = new ArrayList<>();
    private final Map<String, LuaScriptRuntime> runtimes = new HashMap<>();
    private final BbModelRegistry bbModelRegistry;
    private final ContentDiagnostics diagnostics;

    public ShyneModLoader(ActionBus actionBus, Path gameDir, BbModelRegistry bbModelRegistry, ContentDiagnostics diagnostics) {
        this.actionBus = actionBus;
        this.shyneModsDir = gameDir.resolve("shyne-mods");
        this.bbModelRegistry = bbModelRegistry;
        this.diagnostics = diagnostics;
    }

    public void discoverAndLoad() {
        loadedMods.clear();
        runtimes.clear();
        if (diagnostics != null) diagnostics.clearSource("shyne_mod");
        try {
            Files.createDirectories(shyneModsDir);
        } catch (IOException e) {
            ShyneCore.LOGGER.error("[Loader] Cannot prepare shyne-mods: {}", e.getMessage());
            if (diagnostics != null) diagnostics.error("shyne_mod", "filesystem", shyneModsDir,
                "Cannot create shyne-mods directory: " + e.getMessage(), "Check directory write permissions.");
            return;
        }

        bbModelRegistry.discover(shyneModsDir);
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(shyneModsDir)) {
            for (Path entry : stream) if (Files.isDirectory(entry)) loadMod(entry);
        } catch (IOException e) {
            ShyneCore.LOGGER.error("[Loader] Failed to scan shyne-mods: {}", e.getMessage());
            if (diagnostics != null) diagnostics.error("shyne_mod", "registry", shyneModsDir,
                "Failed to scan shyne-mods: " + e.getMessage(), "Check that shyne-mods is readable.");
        }
    }

    private void loadMod(Path modDir) {
        Path modJson = modDir.resolve("mod.json");
        ModInfo info = new ModInfo();
        info.path = modDir;
        String entryName = "main.lua";

        if (Files.exists(modJson)) {
            try (Reader reader = Files.newBufferedReader(modJson)) {
                JsonObject obj = GSON.fromJson(reader, JsonObject.class);
                info.id = string(obj, "id", modDir.getFileName().toString());
                info.name = string(obj, "name", info.id);
                info.version = string(obj, "version", "0.0.1");
                info.author = string(obj, "author", "unknown");
                info.description = string(obj, "description", "");
                entryName = string(obj, "entry", "main.lua");
                String engine = string(obj, "script_engine", "lua");
                if (!engine.equalsIgnoreCase("lua")) {
                    reject(info.id, modJson, "Only script_engine 'lua' is supported.", "Convert the pack entry to main.lua.");
                    return;
                }
            } catch (Exception e) {
                reject(modDir.getFileName().toString(), modJson, "Bad mod.json: " + e.getMessage(), "Fix the JSON syntax.");
                return;
            }
        }

        if (!entryName.toLowerCase(Locale.ROOT).endsWith(".lua")) {
            reject(info.id, modJson, "Only .lua entry files are supported.", "Set entry to main.lua.");
            return;
        }
        Path entry = modDir.resolve(entryName).normalize();
        if (!entry.startsWith(modDir.normalize())) {
            reject(info.id, modJson, "Entry path escapes the mod directory.", "Keep entry inside the pack folder.");
            return;
        }
        info.entryFile = entry;
        if (!Files.isRegularFile(entry)) {
            reject(info.id, entry, "Lua entry file was not found.", "Add main.lua or update entry in mod.json.");
            return;
        }

        LuaScriptRuntime runtime = new LuaScriptRuntime(info.id, entry, actionBus::emit);
        if (!runtime.load()) {
            reject(info.id, entry, "Lua script failed to load.", "Check the Lua syntax and sandbox API usage.");
            return;
        }
        runtimes.put(info.id, runtime);
        loadedMods.add(info);
    }

    private void reject(String id, Path path, String message, String fix) {
        ShyneCore.LOGGER.warn("[Loader:{}] {}", id, message);
        if (diagnostics != null) diagnostics.error("shyne_mod", id, path, message, fix);
    }

    public void fireHook(String hook, Map<String, Object> args) {
        for (ModInfo mod : loadedMods) fireHookIn(mod.id, hook, args);
    }

    public void fireHookIn(String modId, String hook, Map<String, Object> args) {
        LuaScriptRuntime runtime = runtimes.get(modId);
        if (runtime != null) runtime.callHook(hook, args);
    }

    public int getLoadedCount() { return loadedMods.size(); }
    public List<ModInfo> getLoadedMods() { return Collections.unmodifiableList(loadedMods); }
    public Path getShyneModsDir() { return shyneModsDir; }

    private static String string(JsonObject obj, String key, String fallback) {
        return obj != null && obj.has(key) && !obj.get(key).isJsonNull() ? obj.get(key).getAsString() : fallback;
    }

    public static final class ModInfo {
        public Path entryFile;
        public String id = "unknown";
        public String name = "Unknown Mod";
        public String version = "0.0.1";
        public String author = "unknown";
        public String description = "";
        public Path path;
    }
}
