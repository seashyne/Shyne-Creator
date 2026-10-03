package seashyne.shynecore.client.avatar;

import net.minecraft.client.Minecraft;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.lib.OneArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.bridge.*;
import seashyne.shynecore.client.profiler.AvatarProfiler;
import seashyne.shynecore.client.render.AvatarBoneTransformRegistry;
import seashyne.shynecore.client.render.AvatarRenderContext;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.script.LuaSandbox;
import seashyne.shynecore.voice.ShyneMicrophoneState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static seashyne.shynecore.client.avatar.bridge.AvatarBridgeHelper.vec3;

/**
 * Per-avatar Lua host. Coordinates sandboxed Lua VM lifecycle,
 * loads bootstrap standard libraries, and dispatches engine events.
 */
public final class ClientLuaAvatarRuntime {
    private static final int LOAD_INSTRUCTION_LIMIT = 1_000_000;
    private static final int EVENT_INSTRUCTION_LIMIT = 200_000;
    private static final List<String> AVATAR_BOOTSTRAP_MODULES = List.of(
        "/shyne_runtime/lua/avatar/00_core.lua",
        "/shyne_runtime/lua/avatar/01_shyne_core.lua",
        "/shyne_runtime/lua/avatar/05_events_scheduler.lua",
        "/shyne_runtime/lua/avatar/10_model_animation.lua",
        "/shyne_runtime/lua/avatar/20_avatar_world.lua",
        "/shyne_runtime/lua/avatar/21_vanilla_model.lua",
        "/shyne_runtime/lua/avatar/30_render_tasks.lua",
        "/shyne_runtime/lua/avatar/31_render_shapes.lua",
        "/shyne_runtime/lua/avatar/40_optional_systems.lua",
        "/shyne_runtime/lua/avatar/50_easy_api.lua",
        "/shyne_runtime/lua/avatar/59_figura_vectors.lua",
        "/shyne_runtime/lua/avatar/60_figura_compat.lua",
        "/shyne_runtime/lua/avatar/61_figura_client_renderer.lua",
        "/shyne_runtime/lua/avatar/62_figura_items_world.lua",
        "/shyne_runtime/lua/avatar/63_figura_action_wheel.lua",
        "/shyne_runtime/lua/avatar/64_figura_data_nameplate.lua",
        "/shyne_runtime/lua/avatar/65_figura_network.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_core.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_math.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_springs.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_locomotion.lua",
        "/shyne_runtime/lua/compat/squapi/squapi_features.lua",
        "/shyne_runtime/lua/shyne_rig.lua"
    );

    private final AvatarState state;
    private final BbModelDefinition model;
    private final Path scriptPath;
    private Globals globals;
    private LuaSandbox.Budget instructionBudget;
    private final Map<String, LuaValue> modules = new HashMap<>();

    private final AvatarModelBridge modelBridge;
    private final AvatarAnimationBridge animationBridge;
    private final AvatarInputBridge inputBridge;
    private final AvatarRenderTaskBridge renderTaskBridge;
    private final AvatarScriptCanvasBridge scriptCanvasBridge;
    private final AvatarDynamicTextureBridge dynamicTextureBridge;
    private final AvatarSystemBridge systemBridge;
    private final AvatarDataBridge dataBridge;
    private final AvatarChannelBridge channelBridge;

    private long loadElapsedNanos;
    private final Map<String, Long> eventTimes = new HashMap<>();
    private long eventSequence;

    public ClientLuaAvatarRuntime(AvatarState state, BbModelDefinition model, Path scriptPath) {
        this.state = Objects.requireNonNull(state, "state");
        this.model = Objects.requireNonNull(model, "model");
        this.scriptPath = scriptPath;

        this.inputBridge = new AvatarInputBridge(state);
        this.renderTaskBridge = new AvatarRenderTaskBridge(state);
        this.scriptCanvasBridge = new AvatarScriptCanvasBridge(state, () -> instructionBudget, EVENT_INSTRUCTION_LIMIT);
        this.dynamicTextureBridge = new AvatarDynamicTextureBridge(state);
        this.animationBridge = new AvatarAnimationBridge(state, model);
        this.systemBridge = new AvatarSystemBridge(state, model,
            inputBridge::bindingCount,
            inputBridge::conflictCount,
            renderTaskBridge::taskCount,
            modules::size);
        this.dataBridge = new AvatarDataBridge(state);
        this.channelBridge = new AvatarChannelBridge(state);
        this.modelBridge = new AvatarModelBridge(state, model,
            () -> instructionBudget, EVENT_INSTRUCTION_LIMIT);
    }

    public boolean load() {
        long started = System.nanoTime();
        try {
            LuaSandbox.Environment environment = LuaSandbox.create();
            globals = environment.globals();
            instructionBudget = environment.budget();
            globals.set("SHYNE_AVATAR_ID", LuaValue.valueOf(state.avatarId()));
            globals.set("SHYNE_AVATAR_PATH", LuaValue.valueOf(state.rootDir().toString().replace('\\', '/')));
            globals.set("AVATAR_ID", LuaValue.valueOf(state.avatarId()));
            globals.set("AVATAR_PATH", LuaValue.valueOf(state.rootDir().toString().replace('\\', '/')));
            globals.set("SHYNE_API_VERSION", LuaValue.valueOf(state.apiStandard()));
            globals.set("SHYNE_API_AUTOMATIC", LuaValue.valueOf(state.automaticApi()));

            installApi();
            installModuleLoader();
            instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
            loadBootstrap();
            instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
            globals.load(Files.readString(scriptPath), scriptPath.getFileName().toString()).call();
            return true;
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[AvatarLua] Could not load {}: {}", scriptPath, e.getMessage(), e);
            String defaultFile = scriptPath != null ? scriptPath.getFileName().toString() : "avatar.lua";
            lastLoadError = seashyne.shynecore.client.avatar.runtime.AvatarScriptErrorParser.parse(e, defaultFile);
            systemBridge.reportRuntimeError("script_load", lastLoadError.file(), lastLoadError.cleanMessage());
            return false;
        } finally {
            loadElapsedNanos = System.nanoTime() - started;
        }
    }

    private seashyne.shynecore.client.avatar.runtime.AvatarScriptErrorParser.ScriptErrorInfo lastLoadError;
    public seashyne.shynecore.client.avatar.runtime.AvatarScriptErrorParser.ScriptErrorInfo lastLoadError() { return lastLoadError; }

    public long loadElapsedNanos() { return loadElapsedNanos; }

    public LuaValue eval(String expression) {
        if (globals == null) return LuaValue.NIL;
        instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
        try {
            return globals.load("return " + expression, "eval").call();
        } catch (Exception notExpr) {
            return globals.load(expression, "eval").call();
        }
    }

    public String evalToString(String expression) {
        if (globals == null) return "No active avatar Lua runtime.";
        try {
            LuaValue result = eval(expression);
            return result != null ? result.tojstring() : "nil";
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private void installApi() {
        AvatarStateBridge.register(globals, state);
        AvatarActionBridge.register(globals, state);
        modelBridge.register(globals);
        AvatarRendererBridge.register(globals, state);
        animationBridge.register(globals);
        AvatarWorldBridge.register(globals, state);
        inputBridge.register(globals);
        renderTaskBridge.register(globals);
        scriptCanvasBridge.register(globals);
        dynamicTextureBridge.register(globals);
        systemBridge.register(globals);
        dataBridge.register(globals);
        channelBridge.register(globals);
        AvatarAudioStreamBridge.register(globals, state);
    }

    private void loadBootstrap() throws IOException {
        for (String module : AVATAR_BOOTSTRAP_MODULES) {
            loadBundledLua(module, module.substring(module.lastIndexOf('/') + 1));
        }
    }

    private void loadBundledLua(String resource, String chunkName) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("missing avatar bootstrap resource: " + resource);
            instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
            globals.load(new String(in.readAllBytes(), StandardCharsets.UTF_8), chunkName).call();
        }
    }

    private void installModuleLoader() {
        globals.set("require", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String module = arg.checkjstring();
                String normalizedModule = module.trim().toLowerCase(Locale.ROOT);
                if (normalizedModule.equals("squapi") || normalizedModule.endsWith(".squapi")) {
                    LuaValue squapi = globals.get("squapi");
                    if (!squapi.isnil()) return squapi;
                }
                if (normalizedModule.equals("rig") || normalizedModule.endsWith(".rig") || normalizedModule.equals("shyne_rig")) {
                    LuaValue rig = globals.get("rig");
                    if (!rig.isnil()) return rig;
                }
                LuaValue cached = modules.get(module);
                if (cached != null) return cached;
                if (!ShyneLibraryManager.isValidModuleName(module)) throw new LuaError("invalid module name: " + module);

                // 1. Try local avatar directory first (user custom scripts take precedence)
                Path root = state.rootDir().toAbsolutePath().normalize();
                Path source = root.resolve(module.replace('.', java.io.File.separatorChar).replace('/', java.io.File.separatorChar) + ".lua").normalize();
                if (source.startsWith(root) && Files.isRegularFile(source)) {
                    try {
                        modules.put(module, LuaValue.TRUE); // break recursive require cycles
                        instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
                        LuaValue result = globals.load(Files.readString(source), module).call();
                        if (result.isnil()) result = LuaValue.TRUE;
                        modules.put(module, result);
                        return result;
                    } catch (Exception error) {
                        modules.remove(module);
                        throw new LuaError("could not load module " + module + ": " + error.getMessage());
                    }
                }

                // 2. Try bundled classpath resources (checks /shyne_runtime/lua/<mod>.lua and /shyne_runtime/lua/lib/<mod>.lua)
                String path1 = "/shyne_runtime/lua/" + module.replace('.', '/') + ".lua";
                String path2 = module.startsWith("lib.") ? null : "/shyne_runtime/lua/lib/" + module + ".lua";
                InputStream stream = getClass().getResourceAsStream(path1);
                if (stream == null && path2 != null) {
                    stream = getClass().getResourceAsStream(path2);
                }
                if (stream != null) {
                    try (InputStream bundled = stream) {
                        modules.put(module, LuaValue.TRUE); // break recursive require cycles
                        instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
                        LuaValue result = globals.load(new String(bundled.readAllBytes(), StandardCharsets.UTF_8), module).call();
                        if (result.isnil()) result = LuaValue.TRUE;
                        modules.put(module, result);
                        return result;
                    } catch (Exception e) {
                        modules.remove(module);
                        throw new LuaError("could not load bundled module " + module + ": " + e.getMessage());
                    }
                }

                // 3. Try Local Library Cache (.minecraft/shyne_creator/libs/ or .minecraft/shyne-mods/libraries/)
                Path cachedLib = ShyneLibraryManager.resolveCachedLibrary(root, module);
                if (cachedLib != null && Files.isRegularFile(cachedLib)) {
                    try {
                        modules.put(module, LuaValue.TRUE); // break recursive require cycles
                        instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
                        LuaValue result = globals.load(Files.readString(cachedLib), module).call();
                        if (result.isnil()) result = LuaValue.TRUE;
                        modules.put(module, result);
                        return result;
                    } catch (Exception e) {
                        modules.remove(module);
                        throw new LuaError("could not load cached library " + module + ": " + e.getMessage());
                    }
                }

                if (!source.startsWith(root)) throw new LuaError("module escapes avatar folder: " + module);
                throw new LuaError("module not found: " + module);
            }
        });
    }

    public void entityInit(Minecraft client) { callEvent("ENTITY_INIT", eventPayload("entity_init")); }

    public void tick(Minecraft client) {
        systemBridge.tickReset();
        inputBridge.pollInputBindings(instructionBudget, EVENT_INSTRUCTION_LIMIT);
        callEvent("TICK", eventPayload("tick"));
    }

    public void render(float partialTick, String context) {
        callEvent("RENDER", renderEventPayload("render", partialTick, context));
    }

    public void postRender(float partialTick, String context) {
        callEvent("POST_RENDER", renderEventPayload("post_render", partialTick, context));
    }

    public void worldRender(float partialTick) {
        callEvent("WORLD_RENDER", renderEventPayload("world_render", partialTick, AvatarRenderContext.WORLD));
    }

    public void postWorldRender(float partialTick) {
        callEvent("POST_WORLD_RENDER", renderEventPayload("post_world_render", partialTick, AvatarRenderContext.WORLD));
    }

    public void microphone(ShyneMicrophoneState.Snapshot snapshot) {
        LuaTable event = eventPayload("microphone");
        event.set("level", LuaValue.valueOf(snapshot.level()));
        event.set("speaking", LuaValue.valueOf(snapshot.speaking()));
        event.set("muted", LuaValue.valueOf(snapshot.muted()));
        event.set("whispering", LuaValue.valueOf(snapshot.whispering()));
        callEvent("MICROPHONE", event);
    }

    public void input(String type, int key, int scanCode, int action, int modifiers, double horizontal, double vertical, String characters) {
        String normalized = type == null ? "input" : type.trim().toLowerCase(Locale.ROOT);
        LuaTable event = eventPayload(normalized);
        event.set("key", LuaValue.valueOf(key));
        event.set("scan_code", LuaValue.valueOf(scanCode));
        event.set("action", LuaValue.valueOf(action));
        event.set("modifiers", LuaValue.valueOf(modifiers));
        event.set("horizontal", LuaValue.valueOf(horizontal));
        event.set("vertical", LuaValue.valueOf(vertical));
        event.set("characters", LuaValue.valueOf(characters == null ? "" : characters));
        callEvent(normalized.toUpperCase(Locale.ROOT), event);
    }

    public void itemUse(String itemId, String hand, String action, int particleCount) {
        LuaTable event = eventPayload("use_item");
        event.set("item", LuaValue.valueOf(itemId == null ? "" : itemId));
        event.set("hand", LuaValue.valueOf(hand == null ? "main_hand" : hand));
        event.set("action", LuaValue.valueOf(action == null ? "use" : action));
        event.set("particle_count", LuaValue.valueOf(particleCount));
        callEvent("USE_ITEM", event);
    }

    public void chatReceive(String text, String json, String senderUuid, String senderName) {
        LuaTable event = eventPayload("chat_receive_message");
        event.set("text", LuaValue.valueOf(text == null ? "" : text));
        event.set("json", LuaValue.valueOf(json == null ? "" : json));
        event.set("sender", LuaValue.valueOf(senderUuid == null ? "" : senderUuid));
        event.set("sender_name", LuaValue.valueOf(senderName == null ? "" : senderName));
        callEvent("CHAT_RECEIVE_MESSAGE", event);
    }

    public void damage(float amount, String sourceType, String attackerId, boolean isLocalPlayer) {
        LuaTable event = eventPayload("damage");
        event.set("amount", LuaValue.valueOf(amount));
        event.set("source", LuaValue.valueOf(sourceType == null ? "generic" : sourceType));
        event.set("attacker", LuaValue.valueOf(attackerId == null ? "" : attackerId));
        event.set("local_player", LuaValue.valueOf(isLocalPlayer));
        callEvent("DAMAGE", event);
    }

    public void totem(String entityId, boolean isLocalPlayer) {
        LuaTable event = eventPayload("totem");
        event.set("entity", LuaValue.valueOf(entityId == null ? "" : entityId));
        event.set("local_player", LuaValue.valueOf(isLocalPlayer));
        callEvent("TOTEM", event);
    }

    public void channelPacket(String senderUuid, String channel, String payloadJson) {
        LuaTable event = eventPayload("channel_packet");
        event.set("sender", LuaValue.valueOf(senderUuid == null ? "" : senderUuid));
        event.set("channel", LuaValue.valueOf(channel == null ? "" : channel));
        event.set("data", LuaValue.valueOf(payloadJson == null ? "{}" : payloadJson));
        callEvent("CHANNEL_PACKET", event);
    }

    public void dispose() {
        if (globals != null) {
            callEvent("AVATAR_UNLOAD", eventPayload("avatar_unload"));
        }
        inputBridge.dispose();
        renderTaskBridge.dispose();
        scriptCanvasBridge.dispose();
        dynamicTextureBridge.dispose();
        globals = null;
        instructionBudget = null;
        modules.clear();
    }

    private LuaTable eventPayload(String type) {
        Minecraft client = null;
        try {
            client = Minecraft.getInstance();
        } catch (Throwable ignored) {}
        long now = System.nanoTime();
        Long previous = eventTimes.put(type, now);
        LuaTable event = new LuaTable();
        event.set("type", LuaValue.valueOf(type));
        event.set("time", LuaValue.valueOf(now / 1_000_000_000.0));
        event.set("tick", LuaValue.valueOf(client == null || client.level == null ? 0 : client.level.getGameTime()));
        event.set("context", LuaValue.valueOf(type.equals("render") ? "player" : "client"));
        event.set("delta", LuaValue.valueOf(previous == null ? 0 : Math.min(1.0, (now - previous) / 1_000_000_000.0)));
        event.set("sequence", LuaValue.valueOf(++eventSequence));
        event.set("api", LuaValue.valueOf(state.apiStandard()));
        return event;
    }

    private LuaTable renderEventPayload(String type, float partialTick, String context) {
        Minecraft client = null;
        try {
            client = Minecraft.getInstance();
        } catch (Throwable ignored) {}
        LuaTable event = eventPayload(type);
        String normContext = AvatarRenderContext.normalize(context);
        event.set("context", LuaValue.valueOf(normContext));
        event.set("delta", LuaValue.valueOf(Math.max(0f, Math.min(1f, partialTick))));
        event.set("partial_tick", event.get("delta"));
        event.set("frame_delta", LuaValue.valueOf(client == null || client.getDeltaTracker() == null ? 1.0f : client.getDeltaTracker().getRealtimeDeltaTicks()));
        event.set("first_person", LuaValue.valueOf(AvatarRenderContext.FIRST_PERSON.equals(normContext)));
        event.set("is_portrait", LuaValue.valueOf(AvatarRenderContext.PORTRAIT.equals(normContext)));
        event.set("is_skull", LuaValue.valueOf(AvatarRenderContext.SKULL.equals(normContext)));
        event.set("is_held_item", LuaValue.valueOf(AvatarRenderContext.HELD_ITEM.equals(normContext)));
        if (client != null && client.gui != null) {
            var screen = client.gui.screen();
            event.set("screen", LuaValue.valueOf(screen == null ? "" : screen.getClass().getSimpleName()));
        }
        if (client != null && client.gameRenderer != null && client.gameRenderer.mainCamera() != null) {
            var camera = client.gameRenderer.mainCamera();
            event.set("camera_position", vec3(camera.position().x, camera.position().y, camera.position().z));
            event.set("camera_rotation", vec3(camera.xRot(), camera.yRot(), 0));
        }
        return event;
    }

    private void callEvent(String key, LuaValue... args) {
        if (globals == null) return;
        long started = System.nanoTime();
        try {
            LuaValue events = globals.get("events");
            if (!events.istable()) {
                LuaValue root = globals.get("shyne");
                events = root.istable() ? root.get("events") : LuaValue.NIL;
            }
            if (events.istable()) {
                LuaValue dispatcher = events.get("_dispatch");
                if (dispatcher.isfunction()) {
                    instructionBudget.reset(EVENT_INSTRUCTION_LIMIT);
                    LuaValue[] withName = new LuaValue[(args == null ? 0 : args.length) + 1];
                    withName[0] = LuaValue.valueOf(key.toLowerCase(Locale.ROOT));
                    if (args != null && args.length > 0) System.arraycopy(args, 0, withName, 1, args.length);
                    dispatcher.invoke(LuaValue.varargsOf(withName));
                    return;
                }
            }
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[AvatarLua] event {} failed: {}", key, e.getMessage(), e);
            systemBridge.reportRuntimeError("event", key, e.getMessage());
        } finally {
            AvatarProfiler.recordLuaEvent(key, System.nanoTime() - started);
        }
    }
}
