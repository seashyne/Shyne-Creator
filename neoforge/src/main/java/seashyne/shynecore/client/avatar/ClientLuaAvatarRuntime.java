package seashyne.shynecore.client.avatar;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.client.config.ShyneClientSettings;
import seashyne.shynecore.client.input.DynamicAvatarInputRegistry;
import seashyne.shynecore.client.profiler.AvatarProfiler;
import seashyne.shynecore.client.render.AvatarBoneTransformRegistry;
import seashyne.shynecore.client.render.AvatarRenderContext;
import seashyne.shynecore.client.render.AvatarRenderTaskRegistry;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.script.LuaSandbox;
import seashyne.shynecore.script.LuaValueCodec;
import seashyne.shynecore.voice.ShyneMicrophoneState;
import net.minecraft.client.Minecraft;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Per-avatar Lua host. This class is deliberately the boundary between the
 * sandboxed Lua API and Minecraft client state: scripts never receive direct
 * Java objects, and every mutable value is copied into {@link AvatarState}.
 */
public final class ClientLuaAvatarRuntime {
    private static final int LOAD_INSTRUCTION_LIMIT = 1_000_000;
    private static final int EVENT_INSTRUCTION_LIMIT = 200_000;
    private static final List<String> AVATAR_BOOTSTRAP_MODULES = List.of(
        "/shyne_runtime/lua/avatar/00_core.lua",
        "/shyne_runtime/lua/avatar/10_model_animation.lua",
        "/shyne_runtime/lua/avatar/20_avatar_world.lua",
        "/shyne_runtime/lua/avatar/30_render_tasks.lua",
        "/shyne_runtime/lua/avatar/31_render_shapes.lua",
        "/shyne_runtime/lua/avatar/40_optional_systems.lua",
        "/shyne_runtime/lua/avatar/50_easy_api.lua"
    );
    private final AvatarState state;
    private final BbModelDefinition model;
    private final Path scriptPath;
    private Globals globals;
    private LuaSandbox.Budget instructionBudget;
    private final Map<String, LuaValue> modules = new HashMap<>();
    private final Object inputOwner = new Object();
    private final Object renderTaskOwner = new Object();
    private final Set<String> renderTaskIds = new HashSet<>();
    private final Map<String, InputBinding> inputBindings = new LinkedHashMap<>();
    private long lastShyneCommandNanos;
    private int particlesThisTick;
    private long loadElapsedNanos;
    private final Deque<String> runtimeErrors = new ArrayDeque<>();
    private final Map<String, Long> eventTimes = new HashMap<>();
    private long eventSequence;

    public ClientLuaAvatarRuntime(AvatarState state, BbModelDefinition model, Path scriptPath) {
        this.state = state;
        this.model = Objects.requireNonNull(model, "model");
        this.scriptPath = scriptPath;
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
            instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
            loadBootstrap();
            installModuleLoader();
            instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
            globals.load(Files.readString(scriptPath), scriptPath.getFileName().toString()).call();
            return true;
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[AvatarLua] Could not load {}: {}", scriptPath, e.getMessage(), e);
            return false;
        } finally {
            loadElapsedNanos = System.nanoTime() - started;
        }
    }

    public long loadElapsedNanos() { return loadElapsedNanos; }

    private void installApi() {
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
        globals.set("_avatar_part_mutate", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String path = args.arg(1).tojstring();
                String op = args.arg(2).tojstring();
                if ("reset".equals(op)) {
                    if (state.parts().remove(state.resolvePath(path)) != null) state.markSnapshotDirty();
                    return LuaValue.NIL;
                }
                AvatarPartState part = state.getPart(path);
                boolean changed = switch (op) {
                    case "visible" -> part.setVisible(args.arg(3).toboolean());
                    case "rot" -> part.setRotation((float) args.arg(3).todouble(), (float) args.arg(4).todouble(), (float) args.arg(5).todouble());
                    case "pos" -> part.setPosition((float) args.arg(3).todouble(), (float) args.arg(4).todouble(), (float) args.arg(5).todouble());
                    case "scale" -> part.setScale((float) args.arg(3).todouble(), (float) args.arg(4).todouble(), (float) args.arg(5).todouble());
                    case "rot_add" -> part.setAdditiveRotation((float) args.arg(3).todouble(), (float) args.arg(4).todouble(), (float) args.arg(5).todouble());
                    case "vanilla_parent" -> part.setVanillaParent(args.arg(3).optjstring(""), args.arg(4).optjstring("full"));
                    case "vanilla_parent_clear" -> part.clearVanillaParent();
                    case "color" -> part.setColor((float) args.arg(3).todouble(), (float) args.arg(4).todouble(), (float) args.arg(5).todouble());
                    case "opacity" -> part.setOpacity((float) args.arg(3).todouble());
                    case "emissive" -> part.setEmissive(args.arg(3).toboolean());
                    default -> false;
                };
                if (changed) {
                    if ("rot".equals(op) || "pos".equals(op) || "scale".equals(op) || "rot_add".equals(op)) {
                        state.markPoseDirty();
                    } else {
                        state.markSnapshotDirty();
                    }
                }
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_part_read", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String requestedPath = args.arg(1).optjstring("");
                AvatarPartState part = state.parts().get(state.resolvePath(requestedPath));
                String key = args.arg(2).optjstring("");
                if (part == null) {
                    return switch (key) {
                        case "visible" -> LuaValue.valueOf(defaultPartVisibility(requestedPath));
                        case "scale" -> vec3(1, 1, 1);
                        default -> vec3(0, 0, 0);
                    };
                }
                return switch (key) {
                    case "visible" -> LuaValue.valueOf(part.visibilityControlled() ? part.visible() : defaultPartVisibility(requestedPath));
                    case "position" -> vec3(part.posX(), part.posY(), part.posZ());
                    case "rotation" -> vec3(part.rotX(), part.rotY(), part.rotZ());
                    case "scale" -> vec3(part.scaleX(), part.scaleY(), part.scaleZ());
                    case "rotation_add" -> vec3(part.additiveRotX(), part.additiveRotY(), part.additiveRotZ());
                    case "color" -> vec3(((part.colorArgb() >> 16) & 255) / 255.0, ((part.colorArgb() >> 8) & 255) / 255.0, (part.colorArgb() & 255) / 255.0);
                    case "opacity" -> LuaValue.valueOf(((part.colorArgb() >>> 24) & 255) / 255.0);
                    case "emissive" -> LuaValue.valueOf(part.emissive());
                    case "vanilla_parent" -> LuaValue.valueOf(part.vanillaParent());
                    case "vanilla_parent_mode" -> LuaValue.valueOf(part.vanillaAttachmentMode());
                    default -> LuaValue.NIL;
                };
            }
        });
        globals.set("_avatar_vanilla_visible", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String key = VanillaVisibilityKeys.normalize(args.arg(1).tojstring());
                boolean visible = args.arg(2).toboolean();
                Boolean previous = state.vanillaVisibility().put(key, visible);
                if (previous == null || previous != visible) state.markSnapshotDirty();
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_vanilla_transform", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String key = VanillaVisibilityKeys.normalize(arg.optjstring("PLAYER"));
                var transform = ClientAnimationState.getVanillaTransform(state.boundEntityId(), key);
                LuaTable value = new LuaTable();
                value.set("position", vec3(transform.x(), transform.y(), transform.z()));
                value.set("rotation", vec3(transform.rotationX(), transform.rotationY(), transform.rotationZ()));
                value.set("visible", LuaValue.valueOf(VanillaVisibilityKeys.effectiveVisible(
                    state.vanillaVisibility(), state.replaceVanilla(), key, transform.visible()
                )));
                return value;
            }
        });
        globals.set("_avatar_part_info", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) { return partInfo(arg.optjstring("model")); }
        });
        globals.set("_avatar_model_find", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String kind = args.arg(1).optjstring("");
                String query = args.arg(2).optjstring("");
                LuaTable result = new LuaTable();
                var model = ClientLuaAvatarRuntime.this.model;
                if (model == null || query.isBlank()) return result;
                int index = 1;
                for (var bone : model.bones()) {
                    boolean matches = "role".equalsIgnoreCase(kind)
                        ? bone.role().equalsIgnoreCase(query)
                        : "tag".equalsIgnoreCase(kind) && bone.tags().stream().anyMatch(tag -> tag.equalsIgnoreCase(query));
                    if (matches) result.set(index++, LuaValue.valueOf(model.bonePath(bone.uuid())));
                }
                return result;
            }
        });
        globals.set("_avatar_anim_play", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                playAnimation(arg.tojstring(), 1.0, 1.0, 0, null, 0, 0, List.of(), false, 0);
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_anim_play_ex", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Boolean loop = args.arg(5).isnil() ? null : args.arg(5).toboolean();
                playAnimation(
                    args.arg(1).optjstring(""), args.arg(2).optdouble(1), args.arg(3).optdouble(1), args.arg(4).optint(0), loop,
                    args.arg(6).optint(0), args.arg(7).optint(0), stringList(args.arg(8)), args.arg(9).optboolean(false), args.arg(10).optint(0)
                );
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_anim_parameter", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String operation = args.arg(1).optjstring("");
                String name = args.arg(2).optjstring("");
                return switch (operation) {
                    case "set" -> {
                        state.setAnimationParameter(name, args.arg(3).optdouble(0));
                        yield LuaValue.valueOf(state.animationParameter(name, 0));
                    }
                    case "clear" -> {
                        state.clearAnimationParameter(name);
                        yield LuaValue.NIL;
                    }
                    case "get" -> LuaValue.valueOf(state.animationParameter(name, 0));
                    default -> LuaValue.NIL;
                };
            }
        });
        globals.set("_avatar_anim_stop", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                stopAnimation(arg.optjstring(""));
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_anim_playing", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(isAnimationPlaying(arg.optjstring("")));
            }
        });
        globals.set("_avatar_anim_exists", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                var model = ClientLuaAvatarRuntime.this.model;
                return LuaValue.valueOf(model != null && model.hasAnimation(arg.optjstring("")));
            }
        });
        globals.set("_avatar_anim_info", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String name = arg.optjstring("");
                var layer = state.animationLayers().get(name.toLowerCase(Locale.ROOT));
                LuaTable value = new LuaTable();
                if (layer == null) return value;
                long now = System.currentTimeMillis();
                double elapsed = Math.max(0, now - layer.startedAtMillis()) * layer.speed() / 1000.0;
                double time = layer.looping() && layer.lengthSeconds() > 0 ? elapsed % layer.lengthSeconds() : Math.min(elapsed, layer.lengthSeconds());
                value.set("time", LuaValue.valueOf(time));
                value.set("length", LuaValue.valueOf(layer.lengthSeconds()));
                value.set("looping", LuaValue.valueOf(layer.looping()));
                value.set("weight", LuaValue.valueOf(layer.effectiveWeight(now)));
                value.set("priority", LuaValue.valueOf(layer.priority()));
                value.set("playing", LuaValue.valueOf(!layer.finished(now)));
                return value;
            }
        });
        globals.set("_avatar_anim_length", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                var model = ClientLuaAvatarRuntime.this.model;
                var animation = model == null ? null : model.findAnimation(arg.optjstring(""));
                return LuaValue.valueOf(animation == null ? 0 : animation.lengthSeconds());
            }
        });

globals.set("_avatar_camera_set", new VarArgFunction() {
    @Override public Varargs invoke(Varargs args) {
        requirePermission(AvatarPermission.CAMERA);
        String key = args.arg(1).optjstring("");
        boolean value = args.arg(2).optboolean(false);
        switch (key) {
            case "local_only" -> state.setLocalCameraOnly(value);
            case "first_person_masking" -> state.setFirstPersonMasking(value);
            case "hide_head_in_first_person" -> state.setHideHeadInFirstPerson(value);
            case "offset" -> state.setCameraOffset((float) args.arg(2).optdouble(0), (float) args.arg(3).optdouble(0), (float) args.arg(4).optdouble(0));
            case "rotation" -> state.setCameraRotation((float) args.arg(2).optdouble(0), (float) args.arg(3).optdouble(0), (float) args.arg(4).optdouble(0));
        }
        return LuaValue.NIL;
    }
});
globals.set("_avatar_nameplate_set", new VarArgFunction() {
    @Override public Varargs invoke(Varargs args) {
        state.setNameplate(args.arg(1).optjstring(""), args.arg(2).optboolean(true));
        return LuaValue.NIL;
    }
});
globals.set("_avatar_texture_sync", new OneArgFunction() {
    @Override public LuaValue call(LuaValue arg) {
        state.setTextureSyncMode(arg.optjstring("manifest"));
        return LuaValue.NIL;
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

        globals.set("_avatar_action_add", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String id = args.arg(1).optjstring("");
                String title = args.arg(2).optjstring(id);
                String description = args.arg(3).optjstring("");
                String page = args.arg(4).optjstring("main");
                boolean localOnly = args.arg(5).optboolean(false);
                boolean closeOnUse = args.arg(6).optboolean(true);
                LuaValue cb = args.arg(7);
                String icon = args.arg(8).optjstring("");
                LuaValue secondary = args.arg(9);
                state.registerAction(new AvatarAction(id, title, description, page, icon, localOnly, closeOnUse, () -> {
                    try {
                        instructionBudget.reset(EVENT_INSTRUCTION_LIMIT);
                        if (cb.isfunction()) cb.call();
                    } catch (Exception e) { ShyneCore.LOGGER.error("[AvatarLua] action failed: {}", e.getMessage(), e); }
                }, secondary.isfunction() ? () -> {
                    try {
                        instructionBudget.reset(EVENT_INSTRUCTION_LIMIT);
                        if (secondary.isfunction()) secondary.call();
                    } catch (Exception e) { ShyneCore.LOGGER.error("[AvatarLua] secondary action failed: {}", e.getMessage(), e); }
                } : null));
                return LuaValue.NIL;
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
        globals.set("_avatar_emote_register", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String id = args.arg(1).optjstring("");
                String animation = args.arg(2).optjstring("");
                String title = args.arg(3).optjstring(id);
                String description = args.arg(4).optjstring("");
                String page = args.arg(5).optjstring("emotes");
                boolean loop = args.arg(6).optboolean(false);
                boolean localOnly = args.arg(7).optboolean(false);
                boolean closeOnUse = args.arg(8).optboolean(true);
                state.registerEmote(new AvatarEmoteDefinition(id, animation, title, description, page, loop, localOnly, closeOnUse));
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_emote_play", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(playEmote(arg.optjstring("")));
            }
        });
        globals.set("_avatar_graph_bind", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                state.animationGraph().bind(args.arg(1).optjstring(""), args.arg(2).optjstring(""));
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_graph_trigger", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(triggerAnimationGraph(arg.optjstring("")));
            }
        });
        globals.set("_microphone_available", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(state.permissionAllowed(AvatarPermission.MICROPHONE) && ShyneMicrophoneState.snapshot().available()); }
        });
        globals.set("_microphone_level", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(state.permissionAllowed(AvatarPermission.MICROPHONE) ? ShyneMicrophoneState.snapshot().level() : 0.0D); }
        });
        globals.set("_microphone_speaking", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(state.permissionAllowed(AvatarPermission.MICROPHONE) && ShyneMicrophoneState.snapshot().speaking()); }
        });
        globals.set("_microphone_muted", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(!state.permissionAllowed(AvatarPermission.MICROPHONE) || ShyneMicrophoneState.snapshot().muted()); }
        });
        globals.set("_minecraft_shyne_command", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                requirePermission(AvatarPermission.COMMAND);
                String command = arg.checkjstring().trim();
                if (command.startsWith("/")) command = command.substring(1).trim();
                if (command.isBlank() || command.length() > 256) {
                    throw new org.luaj.vm2.LuaError("invalid Shyne command");
                }
                String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                if (!root.equals("shyne") && !root.equals("sjyne")) {
                    throw new org.luaj.vm2.LuaError("avatar scripts may only run /shyne or /sjyne commands");
                }
                net.minecraft.client.Minecraft client = net.minecraft.client.Minecraft.getInstance();
                if (client.player == null) return LuaValue.FALSE;
                long now = System.nanoTime();
                if (now - lastShyneCommandNanos < 250_000_000L) {
                    throw new org.luaj.vm2.LuaError("Shyne command rate limit: wait 250 ms");
                }
                lastShyneCommandNanos = now;
                client.player.connection.sendCommand(command);
                return LuaValue.TRUE;
            }
        });
        globals.set("_shyne_read", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Minecraft client = Minecraft.getInstance();
                var player = client.player;
                String key = args.arg(1).optjstring("");
                if ("client.singleplayer".equals(key)) return LuaValue.valueOf(client.hasSingleplayerServer());
                if ("world.loaded".equals(key)) return LuaValue.valueOf(client.level != null);
                if (player == null) return LuaValue.NIL;
                return switch (key) {
                    case "player.loaded" -> LuaValue.TRUE;
                    case "player.pos" -> vec3(player.getX(), player.getY(), player.getZ());
                    case "player.velocity" -> vec3(player.getDeltaMovement().x, player.getDeltaMovement().y, player.getDeltaMovement().z);
                    case "player.rot" -> vec3(player.getXRot(), player.getYRot(), 0);
                    case "player.look" -> vec3(player.getLookAngle().x, player.getLookAngle().y, player.getLookAngle().z);
                    case "player.body_yaw" -> LuaValue.valueOf(player.yBodyRot);
                    case "player.in_water" -> LuaValue.valueOf(player.isInWater());
                    case "player.underwater" -> LuaValue.valueOf(player.isUnderWater());
                    case "player.in_lava" -> LuaValue.valueOf(player.isInLava());
                    case "player.wet" -> LuaValue.valueOf(player.isInWaterOrRain());
                    case "player.on_ground" -> LuaValue.valueOf(player.onGround());
                    case "player.crouching" -> LuaValue.valueOf(player.isCrouching());
                    case "player.swimming" -> LuaValue.valueOf(player.isSwimming());
                    case "player.fall_flying" -> LuaValue.valueOf(player.isFallFlying());
                    case "player.sleeping" -> LuaValue.valueOf(player.isSleeping());
                    case "player.left_handed" -> LuaValue.valueOf(player.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT);
                    case "player.using_item" -> LuaValue.valueOf(player.isUsingItem());
                    case "player.active_item_time" -> LuaValue.valueOf(player.getTicksUsingItem());
                    case "player.pose" -> LuaValue.valueOf(player.getPose().name());
                    case "player.vehicle" -> vehicleInfo(player.getVehicle());
                    case "player.target" -> targetInfo(player, args.arg(2).optdouble(6));
                    case "player.effects" -> activeEffects(player);
                    case "player.swing" -> LuaValue.valueOf(1.0f - player.getAttackStrengthScale(0));
                    case "player.name" -> LuaValue.valueOf(player.getName().getString());
                    case "player.uuid" -> LuaValue.valueOf(player.getStringUUID());
                    case "player.health" -> LuaValue.valueOf(player.getHealth());
                    case "player.max_health" -> LuaValue.valueOf(player.getMaxHealth());
                    case "player.sprinting" -> LuaValue.valueOf(player.isSprinting());
                    case "player.main_hand" -> itemStack(player.getMainHandItem());
                    case "player.off_hand" -> itemStack(player.getOffhandItem());
                    case "player.armor_head" -> itemStack(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.HEAD));
                    case "player.armor_chest" -> itemStack(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.CHEST));
                    case "player.armor_legs" -> itemStack(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.LEGS));
                    case "player.armor_feet" -> itemStack(player.getItemBySlot(net.minecraft.world.entity.EquipmentSlot.FEET));
                    case "player.active_hand" -> LuaValue.valueOf(player.isUsingItem() ? player.getUsedItemHand().name() : "NONE");
                    case "world.time" -> LuaValue.valueOf(player.level().getGameTime());
                    case "world.day_time" -> LuaValue.valueOf(player.level().getGameTime() % 24_000L);
                    case "world.raining" -> LuaValue.valueOf(player.level().isRaining());
                    case "world.light" -> LuaValue.valueOf(player.level().getMaxLocalRawBrightness(net.minecraft.core.BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()))));
                    case "world.block" -> LuaValue.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(net.minecraft.core.BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()))).getBlock()).toString());
                    case "world.block_info" -> blockInfo(player, args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ()));
                    case "world.probe" -> physicsProbe(player, args);
                    case "world.biome" -> LuaValue.valueOf(player.level().getBiome(net.minecraft.core.BlockPos.containing(args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ())))
                        .unwrapKey().map(entryKey -> entryKey.identifier().toString()).orElse(""));
                    case "client.paused" -> LuaValue.valueOf(client.isPaused());
                    case "client.first_person" -> LuaValue.valueOf(client.options.getCameraType().isFirstPerson());
                    default -> LuaValue.NIL;
                };
            }
        });
        globals.set("_shyne_sound_play", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!state.permissionAllowed(AvatarPermission.SOUND)) return LuaValue.FALSE;
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return LuaValue.FALSE;
                var id = net.minecraft.resources.Identifier.tryParse(args.arg(1).optjstring(""));
                if (id == null) return LuaValue.FALSE;
                var sound = net.minecraft.core.registries.BuiltInRegistries.SOUND_EVENT.get(id).map(ref -> ref.value()).orElse(null);
                if (sound == null) return LuaValue.FALSE;
                float volume = (float) Math.max(0, Math.min(4, args.arg(2).optdouble(1)));
                float pitch = (float) Math.max(0.05, Math.min(4, args.arg(3).optdouble(1)));
                client.player.playSound(sound, volume, pitch);
                return LuaValue.TRUE;
            }
        });
        globals.set("_shyne_particle_spawn", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!state.permissionAllowed(AvatarPermission.PARTICLE)) return LuaValue.FALSE;
                Minecraft client = Minecraft.getInstance();
                if (client.level == null || particlesThisTick >= 256) return LuaValue.FALSE;
                var id = net.minecraft.resources.Identifier.tryParse(args.arg(1).optjstring(""));
                if (id == null) return LuaValue.FALSE;
                var type = net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.get(id).map(ref -> ref.value()).orElse(null);
                if (!(type instanceof net.minecraft.core.particles.SimpleParticleType particle)) return LuaValue.FALSE;
                client.level.addParticle(particle,
                    args.arg(2).optdouble(0), args.arg(3).optdouble(0), args.arg(4).optdouble(0),
                    args.arg(5).optdouble(0), args.arg(6).optdouble(0), args.arg(7).optdouble(0));
                particlesThisTick++;
                return LuaValue.TRUE;
            }
        });
        globals.set("_shyne_input_bind", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (inputBindings.size() >= DynamicAvatarInputRegistry.MAX_BINDINGS_PER_AVATAR) {
                    ShyneCore.LOGGER.warn("[AvatarInput] {} reached the {} binding limit", state.avatarId(), DynamicAvatarInputRegistry.MAX_BINDINGS_PER_AVATAR);
                    return LuaValue.FALSE;
                }
                String id = DynamicAvatarInputRegistry.sanitize(args.arg(1).optjstring(""));
                if (id.isBlank()) return LuaValue.FALSE;
                try {
                    InputBinding previous = inputBindings.remove(id);
                    if (previous != null) previous.handle().close();
                    var handle = DynamicAvatarInputRegistry.register(
                        inputOwner, state.avatarId(), id, args.arg(2).optjstring(id),
                        DynamicAvatarInputRegistry.inputType(args.arg(4).optjstring("keyboard")),
                        args.arg(3).optint(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN), args.arg(5).optint(0)
                    );
                    inputBindings.put(id, new InputBinding(handle, args.arg(6), args.arg(7), args.arg(8),
                        args.arg(9).optboolean(false), Math.max(1, args.arg(10).optint(10)),
                        Math.max(1, args.arg(11).optint(2)), handle.isDown(), 0));
                    return LuaValue.valueOf(id);
                } catch (Exception error) {
                    // A bad or late binding must not reject the complete Avatar activation.
                    ShyneCore.LOGGER.warn("[AvatarInput] Could not bind {}.{}: {}", state.avatarId(), id, error.getMessage());
                    return LuaValue.FALSE;
                }
            }
        });
        globals.set("_shyne_input_unbind", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.remove(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                if (binding == null) return LuaValue.FALSE;
                binding.handle().close();
                return LuaValue.TRUE;
            }
        });
        globals.set("_shyne_input_is_down", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                return LuaValue.valueOf(binding != null && binding.handle().isDown());
            }
        });
        globals.set("_shyne_input_get_key", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                if (binding == null) return LuaValue.NIL;
                return DynamicAvatarInputRegistry.snapshots().stream()
                    .filter(value -> value.stableId().equals(binding.handle().stableId())).findFirst()
                    .<LuaValue>map(value -> LuaValue.valueOf(value.keyName())).orElse(LuaValue.NIL);
            }
        });
        globals.set("_shyne_input_set_key", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(args.arg(1).optjstring("")));
                if (binding == null) return LuaValue.FALSE;
                try {
                    return LuaValue.valueOf(DynamicAvatarInputRegistry.setKey(binding.handle().stableId(),
                        com.mojang.blaze3d.platform.InputConstants.getKey(args.arg(2).checkjstring())));
                } catch (Exception error) {
                    return LuaValue.FALSE;
                }
            }
        });
        globals.set("_shyne_input_conflicts", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                InputBinding binding = inputBindings.get(DynamicAvatarInputRegistry.sanitize(arg.optjstring("")));
                LuaTable result = new LuaTable();
                if (binding == null) return result;
                List<String> conflicts = binding.handle().conflicts();
                for (int i = 0; i < conflicts.size(); i++) result.set(i + 1, LuaValue.valueOf(conflicts.get(i)));
                return result;
            }
        });
        globals.set("_shyne_diagnostics", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable result = new LuaTable();
                var model = ClientLuaAvatarRuntime.this.model;
                result.set("api_version", LuaValue.valueOf(AvatarLoader.AVATAR_API_VERSION));
                result.set("api_standard", LuaValue.valueOf(state.apiStandard()));
                result.set("api_automatic", LuaValue.valueOf(state.automaticApi()));
                result.set("custom_render_api_version", LuaValue.valueOf("1.3"));
                result.set("parts_controlled", LuaValue.valueOf(state.parts().size()));
                result.set("animation_layers", LuaValue.valueOf(state.animationLayers().size()));
                result.set("input_bindings", LuaValue.valueOf(inputBindings.size()));
                result.set("input_conflicts", LuaValue.valueOf(inputBindings.values().stream().mapToInt(value -> value.handle().conflicts().size()).sum()));
                result.set("input_errors", LuaValue.valueOf(DynamicAvatarInputRegistry.diagnostics().size()));
                result.set("modules_loaded", LuaValue.valueOf(modules.size()));
                result.set("particles_this_tick", LuaValue.valueOf(particlesThisTick));
                result.set("render_tasks", LuaValue.valueOf(renderTaskIds.size()));
                LuaTable apiModules = new LuaTable();
                ShyneApiStandard.modulesFor(state.apiStandard()).forEach((name, version) ->
                    apiModules.set(name, LuaValue.valueOf(version)));
                result.set("api_modules", apiModules);
                LuaTable errors = new LuaTable();
                int errorIndex = 1;
                for (String error : runtimeErrors) errors.set(errorIndex++, LuaValue.valueOf(error));
                result.set("runtime_errors", errors);
                if (model != null) {
                    result.set("bones", LuaValue.valueOf(model.bones().size()));
                    result.set("cubes", LuaValue.valueOf(model.cubes().size()));
                    result.set("animations", LuaValue.valueOf(model.animations().size()));
                    result.set("textures", LuaValue.valueOf(model.textures().size()));
                }
                LuaTable features = new LuaTable();
                for (String feature : List.of("lua_api_1_1", "api_auto_latest", "api_requirements", "event_isolation", "event_delta", "scheduler", "vector_math", "permission_query", "multi_animation", "animation_fade", "animation_mask", "animation_transition", "animation_expression", "animation_parameter", "blockbench_5_1", "additive_animation", "shortest_rotation", "part_color", "part_opacity", "part_translucency", "part_emissive", "camera_transform", "nameplate", "sound", "particle", "input", "input_mouse", "input_modifiers", "input_repeat", "render_text", "render_item", "render_block", "render_sprite", "render_line", "render_world", "render_world_3d", "render_world_light", "render_bone_binding", "render_rect", "render_outline", "render_polyline", "render_groups", "render_task_update", "render_screen", "profiler", "online_sync")) features.set(feature, LuaValue.TRUE);
                result.set("features", features);
                return result;
            }
        });
        globals.set("_shyne_api_modules", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable modules = new LuaTable();
                ShyneApiStandard.modulesFor(state.apiStandard()).forEach((name, version) ->
                    modules.set(name, LuaValue.valueOf(version)));
                return modules;
            }
        });
        globals.set("_shyne_api_supports", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                return LuaValue.valueOf(ShyneApiStandard.supports(
                    state.apiStandard(), args.arg(1).optjstring(""), args.arg(2).optjstring("*")
                ));
            }
        });
        globals.set("_shyne_permission_allowed", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(AvatarPermission.fromId(arg.optjstring(""))
                    .map(state::permissionAllowed).orElse(false));
            }
        });
        globals.set("_shyne_permission_requested", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(AvatarPermission.fromId(arg.optjstring(""))
                    .map(state.requestedPermissions()::contains).orElse(false));
            }
        });
        globals.set("_shyne_permissions", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable permissions = new LuaTable();
                for (AvatarPermission permission : AvatarPermission.values()) {
                    LuaTable value = new LuaTable();
                    value.set("requested", LuaValue.valueOf(state.requestedPermissions().contains(permission)));
                    value.set("allowed", LuaValue.valueOf(state.permissionAllowed(permission)));
                    value.set("dangerous", LuaValue.valueOf(permission.dangerous()));
                    permissions.set(permission.id(), value);
                }
                return permissions;
            }
        });
        globals.set("_shyne_report_error", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                reportRuntimeError(args.arg(1).optjstring("runtime"), args.arg(2).optjstring("unknown"),
                    args.arg(3).optjstring("unknown error"));
                return LuaValue.NIL;
            }
        });
        globals.set("_shyne_render_task", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                boolean world = args.arg(3).optboolean(false);
                AvatarPermission renderPermission = world ? AvatarPermission.WORLD_RENDER : AvatarPermission.HUD_RENDER;
                if (!state.permissionAllowed(renderPermission)) return LuaValue.FALSE;
                String id = DynamicAvatarInputRegistry.sanitize(args.arg(1).optjstring(""));
                if (id.isBlank()) return LuaValue.FALSE;
                if (!renderTaskIds.contains(id) && renderTaskIds.size() >= AvatarRenderTaskRegistry.MAX_TASKS_PER_AVATAR) return LuaValue.FALSE;
                String attachmentPath = state.resolvePath(args.arg(21).optjstring(""));
                if (attachmentPath == null) attachmentPath = "";
                var spec = new AvatarRenderTaskRegistry.TaskSpec(
                    args.arg(2).optjstring("text"), world,
                    args.arg(4).optjstring(""), args.arg(5).optjstring(""),
                    args.arg(6).optdouble(0), args.arg(7).optdouble(0), args.arg(8).optdouble(0),
                    args.arg(9).optdouble(0), args.arg(10).optdouble(0), args.arg(11).optdouble(0),
                    args.arg(12).optdouble(1), args.arg(13).optdouble(16), args.arg(14).optdouble(1),
                    (int) args.arg(15).optlong(0xFFFFFFFFL), args.arg(16).optboolean(false), args.arg(17).optboolean(true),
                    args.arg(18).optdouble(128), args.arg(19).optint(0), args.arg(20).optdouble(1),
                    attachmentPath.isBlank() ? null : state.boundEntityId(), state.modelId(), attachmentPath,
                    args.arg(22).optdouble(0), args.arg(23).optdouble(0), args.arg(24).optdouble(0),
                    args.arg(25).optdouble(0), args.arg(26).optdouble(0), args.arg(27).optdouble(0),
                    args.arg(28).optboolean(false), args.arg(29).optboolean(true), args.arg(30).optboolean(false)
                );
                boolean added = AvatarRenderTaskRegistry.upsert(renderTaskOwner, state.avatarId(), id, spec);
                if (added) renderTaskIds.add(id);
                return LuaValue.valueOf(added);
            }
        });
        globals.set("_shyne_render_remove", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String id = DynamicAvatarInputRegistry.sanitize(arg.optjstring(""));
                renderTaskIds.remove(id);
                return LuaValue.valueOf(AvatarRenderTaskRegistry.remove(renderTaskOwner, state.avatarId(), id));
            }
        });
        globals.set("_shyne_render_clear", new ZeroArgFunction() {
            @Override public LuaValue call() {
                AvatarRenderTaskRegistry.clearOwner(renderTaskOwner);
                renderTaskIds.clear();
                return LuaValue.NIL;
            }
        });
        globals.set("_shyne_render_screen", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable result = new LuaTable();
                result.set("width", LuaValue.valueOf(AvatarRenderTaskRegistry.lastScreenWidth()));
                result.set("height", LuaValue.valueOf(AvatarRenderTaskRegistry.lastScreenHeight()));
                result.set("ready", LuaValue.valueOf(AvatarRenderTaskRegistry.lastScreenWidth() > 0));
                return result;
            }
        });
        globals.set("_shyne_render_stats", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable result = new LuaTable();
                result.set("tasks", LuaValue.valueOf(renderTaskIds.size()));
                result.set("rendered", LuaValue.valueOf(AvatarRenderTaskRegistry.lastRendered()));
                result.set("culled", LuaValue.valueOf(AvatarRenderTaskRegistry.lastCulled()));
                result.set("task_limit", LuaValue.valueOf(AvatarRenderTaskRegistry.MAX_TASKS_PER_AVATAR));
                result.set("frame_limit", LuaValue.valueOf(AvatarRenderTaskRegistry.MAX_RENDERED_TASKS_PER_FRAME));
                result.set("line_point_limit", LuaValue.valueOf(AvatarRenderTaskRegistry.MAX_LINE_POINTS_PER_FRAME));
                result.set("glyph_limit", LuaValue.valueOf(AvatarRenderTaskRegistry.MAX_TEXT_GLYPHS_PER_FRAME));
                return result;
            }
        });
        globals.set("_shyne_profiler_snapshot", new ZeroArgFunction() {
            @Override public LuaValue call() {
                var profile = AvatarProfiler.snapshot(AvatarRenderTaskRegistry.snapshots().size(), AvatarRenderTaskRegistry.estimatedBytes());
                LuaTable result = new LuaTable();
                result.set("fps", LuaValue.valueOf(profile.fps()));
                result.set("frame_ms", LuaValue.valueOf(profile.frameMs()));
                result.set("avatar_frame_ms", LuaValue.valueOf(profile.avatarFrameMs()));
                result.set("estimated_fps_loss", LuaValue.valueOf(profile.estimatedFpsLoss()));
                result.set("heap_bytes", LuaValue.valueOf(profile.heapBytes()));
                result.set("avatar_bytes", LuaValue.valueOf(profile.avatarBytes()));
                result.set("task_count", LuaValue.valueOf(profile.taskCount()));
                result.set("rendered_tasks", LuaValue.valueOf(AvatarRenderTaskRegistry.lastRendered()));
                result.set("culled_tasks", LuaValue.valueOf(AvatarRenderTaskRegistry.lastCulled()));
                LuaTable metrics = new LuaTable();
                profile.metrics().forEach((category, metric) -> {
                    LuaTable value = new LuaTable();
                    value.set("average_ms", LuaValue.valueOf(metric.averageMs()));
                    value.set("maximum_ms", LuaValue.valueOf(metric.maximumMs()));
                    value.set("last_ms", LuaValue.valueOf(metric.lastMs()));
                    metrics.set(category.name().toLowerCase(Locale.ROOT), value);
                });
                result.set("metrics", metrics);
                return result;
            }
        });
    }

    private static LuaTable vec3(double x, double y, double z) {
        LuaTable value = new LuaTable();
        value.set("x", LuaValue.valueOf(x));
        value.set("y", LuaValue.valueOf(y));
        value.set("z", LuaValue.valueOf(z));
        value.set(1, LuaValue.valueOf(x));
        value.set(2, LuaValue.valueOf(y));
        value.set(3, LuaValue.valueOf(z));
        return value;
    }

    private void playAnimation(String animationName, double speed, double weight, int priority, Boolean loopOverride,
                               int fadeInTicks, int fadeOutTicks, List<String> mask, boolean additive, int transitionTicks) {
        if (animationName == null || animationName.isBlank() || state.boundEntityId() == null) return;
        var definition = model.findAnimation(animationName);
        double length = definition == null || definition.lengthSeconds() <= 0 ? 2.0 : definition.lengthSeconds();
        boolean looping = loopOverride != null ? loopOverride : definition == null || definition.looping();
        int transition = Math.max(0, Math.min(1200, transitionTicks));
        long now = System.currentTimeMillis();
        if (transition > 0 && !additive) {
            state.animationLayers().replaceAll((key, layer) ->
                !layer.additive() && layer.priority() == priority && !key.equals(animationName.toLowerCase(Locale.ROOT))
                    ? layer.requestStop(now, transition) : layer);
        }
        state.animationLayers().put(animationName.toLowerCase(Locale.ROOT), new AvatarAnimationLayer(
            animationName, now, length, looping,
            Math.max(0.01, Math.min(8.0, speed)), Math.max(0.0, Math.min(1.0, weight)),
            Math.max(-1000, Math.min(1000, priority)),
            Math.max(transition, Math.max(0, Math.min(1200, fadeInTicks))),
            Math.max(transition, Math.max(0, Math.min(1200, fadeOutTicks))),
            mask == null ? List.of() : List.copyOf(mask), additive, 0L
        ));
        state.markAnimationLayersDirty();
        state.markSnapshotDirty();
        state.refreshCurrentAnimationFromLayers(now);
        AvatarRuntime.publishAnimationPlayback(state, now);
    }

    private void stopAnimation(String animationName) {
        if (animationName == null) return;
        String key = animationName.toLowerCase(Locale.ROOT);
        AvatarAnimationLayer layer = state.animationLayers().get(key);
        long now = System.currentTimeMillis();
        if (layer != null && layer.fadeOutTicks() > 0) state.animationLayers().put(key, layer.requestStop(now));
        else state.animationLayers().remove(key);
        state.markAnimationLayersDirty();
        state.markSnapshotDirty();
        state.refreshCurrentAnimationFromLayers(now);
        AvatarRuntime.publishAnimationPlayback(state, now);
    }

    private boolean isAnimationPlaying(String animationName) {
        return animationName != null && state.animationLayers().containsKey(animationName.toLowerCase(Locale.ROOT));
    }

    private boolean playEmote(String emoteId) {
        AvatarEmoteDefinition emote = state.findEmote(emoteId);
        if (emote == null) return false;
        playAnimation(emote.animation(), 1.0, 1.0, 0, emote.loop(), 0, 0, List.of(), false, 0);
        return true;
    }

    private boolean triggerAnimationGraph(String trigger) {
        if (trigger == null || trigger.isBlank()) return false;
        String emoteId = state.animationGraph().resolve(trigger);
        return emoteId != null && !emoteId.isBlank() && playEmote(emoteId);
    }

    private static LuaTable matrix(float[] values) {
        LuaTable result = new LuaTable();
        float[] safe = values == null || values.length != 16
            ? new float[] {1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1}
            : values;
        for (int index = 0; index < 16; index++) result.set(index + 1, LuaValue.valueOf(safe[index]));
        return result;
    }

    private LuaTable partInfo(String requestedPath) {
        String path = state.resolvePath(requestedPath);
        String name = path == null || path.isBlank() ? "model" : path.substring(path.lastIndexOf('.') + 1);
        String parent = path != null && path.lastIndexOf('.') > 0 ? path.substring(0, path.lastIndexOf('.')) : "";
        LuaTable result = new LuaTable();
        result.set("path", LuaValue.valueOf(path == null ? "model" : path));
        result.set("name", LuaValue.valueOf(name));
        result.set("parent", LuaValue.valueOf(parent));
        result.set("role", LuaValue.valueOf(""));
        LuaTable children = new LuaTable();
        var model = this.model;
        seashyne.shynecore.model.BbBoneDefinition matched = null;
        if (model != null) {
            for (var bone : model.bones()) {
                if (model.bonePath(bone.uuid()).equalsIgnoreCase(path)) { matched = bone; break; }
            }
        }
        if (matched != null && model != null) {
            result.set("role", LuaValue.valueOf(matched.role()));
            LuaTable tags = new LuaTable();
            int tagIndex = 1;
            for (String tag : matched.tags()) tags.set(tagIndex++, LuaValue.valueOf(tag));
            result.set("tags", tags);
            int index = 1;
            for (String childUuid : matched.childBoneUuids()) {
                var child = model.findBoneByUuid(childUuid);
                if (child != null) children.set(index++, LuaValue.valueOf(model.bonePath(child.uuid())));
            }
            var captured = AvatarBoneTransformRegistry.findWorld(state.boundEntityId(), model.modelId(), path);
            if (captured != null) {
                result.set("world_position", vec3(captured.x(), captured.y(), captured.z()));
                result.set("world_rotation", vec3(captured.rotationX(), captured.rotationY(), captured.rotationZ()));
                result.set("world_scale", vec3(captured.scaleX(), captured.scaleY(), captured.scaleZ()));
                result.set("world_matrix", matrix(captured.matrix()));
                result.set("render_context", LuaValue.valueOf(captured.context()));
                result.set("transform_exact", LuaValue.TRUE);
            } else {
                var transform = AvatarBoneTransforms.resolve(model, state, matched);
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) result.set("world_position", vec3(client.player.getX() + transform.x() / 16.0, client.player.getY() + transform.y() / 16.0, client.player.getZ() + transform.z() / 16.0));
                else result.set("world_position", vec3(transform.x() / 16.0, transform.y() / 16.0, transform.z() / 16.0));
                result.set("world_rotation", vec3(transform.rotationX(), transform.rotationY(), transform.rotationZ()));
                result.set("world_scale", vec3(1, 1, 1));
                result.set("world_matrix", matrix(null));
                result.set("render_context", LuaValue.valueOf(AvatarRenderContext.current(client)));
                result.set("transform_exact", LuaValue.FALSE);
            }
        } else {
            result.set("world_position", vec3(0, 0, 0));
            result.set("world_rotation", vec3(0, 0, 0));
            result.set("world_scale", vec3(1, 1, 1));
            result.set("world_matrix", matrix(null));
            result.set("render_context", LuaValue.valueOf(AvatarRenderContext.OTHER));
            result.set("transform_exact", LuaValue.FALSE);
        }
        if (result.get("tags").isnil()) result.set("tags", new LuaTable());
        result.set("children", children);
        return result;
    }

    private boolean defaultPartVisibility(String requestedPath) {
        String path = state.resolvePath(requestedPath);
        var model = this.model;
        if (model == null || path == null) return true;
        for (var bone : model.bones()) {
            if (model.bonePath(bone.uuid()).equalsIgnoreCase(path)) return bone.visible();
        }
        for (var cube : model.cubes()) {
            if (model.cubePath(cube).equalsIgnoreCase(path)) return cube.visible();
        }
        for (var mesh : model.meshes()) {
            if (model.meshPath(mesh).equalsIgnoreCase(path)) return mesh.visible();
        }
        return true;
    }

    private static List<String> stringList(LuaValue value) {
        if (!value.istable()) return List.of();
        List<String> result = new ArrayList<>();
        LuaTable table = value.checktable();
        for (int i = 1; i <= table.length() && result.size() < 256; i++) {
            String item = table.get(i).optjstring("").trim();
            if (!item.isBlank()) result.add(item);
        }
        return List.copyOf(result);
    }

    private static LuaTable itemStack(net.minecraft.world.item.ItemStack stack) {
        LuaTable value = new LuaTable();
        String itemId = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        value.set("id", LuaValue.valueOf(itemId));
        value.set("material", LuaValue.valueOf(armorMaterial(itemId)));
        value.set("count", LuaValue.valueOf(stack.getCount()));
        value.set("empty", LuaValue.valueOf(stack.isEmpty()));
        value.set("damage", LuaValue.valueOf(stack.isDamageableItem() ? stack.getDamageValue() : 0));
        value.set("max_damage", LuaValue.valueOf(stack.isDamageableItem() ? stack.getMaxDamage() : 0));
        var trim = stack.get(net.minecraft.core.component.DataComponents.TRIM);
        if (trim != null) {
            value.set("trim_material", LuaValue.valueOf(trim.material().unwrapKey().map(key -> key.identifier().toString()).orElse("")));
            value.set("trim_pattern", LuaValue.valueOf(trim.pattern().unwrapKey().map(key -> key.identifier().toString()).orElse("")));
        } else {
            value.set("trim_material", LuaValue.NIL);
            value.set("trim_pattern", LuaValue.NIL);
        }
        return value;
    }

    private static String armorMaterial(String itemId) {
        for (String material : List.of("leather", "chainmail", "iron", "golden", "diamond", "netherite", "turtle", "copper")) {
            if (itemId.contains(material)) return "minecraft:" + material;
        }
        return "";
    }

    private static LuaTable activeEffects(net.minecraft.world.entity.player.Player player) {
        LuaTable result = new LuaTable();
        int index = 1;
        for (var effect : player.getActiveEffects()) {
            LuaTable value = new LuaTable();
            value.set("id", LuaValue.valueOf(effect.getEffect().unwrapKey()
                .map(key -> key.identifier().toString())
                .orElse(effect.getEffect().value().getDescriptionId())));
            value.set("ambient", LuaValue.valueOf(effect.isAmbient()));
            value.set("visible", LuaValue.valueOf(effect.isVisible()));
            value.set("amplifier", LuaValue.valueOf(effect.getAmplifier()));
            value.set("duration", LuaValue.valueOf(effect.getDuration()));
            result.set(index++, value);
        }
        return result;
    }

    private static LuaValue vehicleInfo(net.minecraft.world.entity.Entity vehicle) {
        if (vehicle == null) return LuaValue.NIL;
        LuaTable value = new LuaTable();
        value.set("type", LuaValue.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()).toString()));
        value.set("name", LuaValue.valueOf(vehicle.getName().getString()));
        value.set("position", vec3(vehicle.getX(), vehicle.getY(), vehicle.getZ()));
        value.set("velocity", vec3(vehicle.getDeltaMovement().x, vehicle.getDeltaMovement().y, vehicle.getDeltaMovement().z));
        value.set("rotation", vec3(vehicle.getXRot(), vehicle.getYRot(), 0));
        value.set("on_ground", LuaValue.valueOf(vehicle.onGround()));
        value.set("passenger_count", LuaValue.valueOf(vehicle.getPassengers().size()));
        value.set("uuid", LuaValue.valueOf(vehicle.getStringUUID()));
        return value;
    }

    private static LuaValue targetInfo(net.minecraft.world.entity.player.Player player, double range) {
        double boundedRange = Math.max(1.0, Math.min(128.0, range));
        var hit = player.pick(boundedRange, 0f, false);
        var start = player.getEyePosition();
        var end = start.add(player.getLookAngle().scale(boundedRange));
        double nearestDistance = hit.getType() == net.minecraft.world.phys.HitResult.Type.MISS ? boundedRange : start.distanceTo(hit.getLocation());
        net.minecraft.world.entity.Entity nearestEntity = null;
        var search = player.getBoundingBox().expandTowards(player.getLookAngle().scale(boundedRange)).inflate(1.0);
        for (var entity : player.level().getEntities(player, search, entity -> entity.isPickable() && !entity.isSpectator())) {
            var intersection = entity.getBoundingBox().inflate(0.3).clip(start, end);
            if (intersection.isEmpty()) continue;
            double distance = start.distanceTo(intersection.get());
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestEntity = entity;
            }
        }
        LuaTable value = new LuaTable();
        value.set("distance", LuaValue.valueOf(nearestDistance));
        if (nearestEntity != null) {
            value.set("type", LuaValue.valueOf("ENTITY"));
            value.set("position", vec3(nearestEntity.getX(), nearestEntity.getY(), nearestEntity.getZ()));
            value.set("entity_id", LuaValue.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(nearestEntity.getType()).toString()));
            value.set("uuid", LuaValue.valueOf(nearestEntity.getStringUUID()));
            value.set("name", LuaValue.valueOf(nearestEntity.getName().getString()));
            return value;
        }
        value.set("type", LuaValue.valueOf(hit.getType().name()));
        value.set("position", vec3(hit.getLocation().x, hit.getLocation().y, hit.getLocation().z));
        if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit) {
            var blockPos = blockHit.getBlockPos();
            var block = player.level().getBlockState(blockPos).getBlock();
            value.set("block", LuaValue.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString()));
            value.set("block_position", vec3(blockPos.getX(), blockPos.getY(), blockPos.getZ()));
            value.set("face", LuaValue.valueOf(blockHit.getDirection().getName()));
        }
        return value;
    }

    /** Client-side probe used by visual physics; it never changes world state. */
    private static LuaTable blockInfo(net.minecraft.world.entity.player.Player player, double x, double y, double z) {
        var position = net.minecraft.core.BlockPos.containing(x, y, z);
        var state = player.level().getBlockState(position);
        LuaTable result = new LuaTable();
        result.set("id", LuaValue.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString()));
        result.set("solid", LuaValue.valueOf(!state.getCollisionShape(player.level(), position).isEmpty()));
        result.set("fluid", LuaValue.valueOf(!state.getFluidState().isEmpty()));
        result.set("position", vec3(position.getX(), position.getY(), position.getZ()));
        return result;
    }

    /** Swept block/entity query for avatar-only collision response. */
    private static LuaTable physicsProbe(net.minecraft.world.entity.player.Player player, Varargs args) {
        var start = new net.minecraft.world.phys.Vec3(
            args.arg(2).optdouble(player.getX()), args.arg(3).optdouble(player.getY()), args.arg(4).optdouble(player.getZ())
        );
        var rawDirection = new net.minecraft.world.phys.Vec3(args.arg(5).optdouble(0), args.arg(6).optdouble(0), args.arg(7).optdouble(0));
        var direction = rawDirection.lengthSqr() < 0.000001 ? player.getLookAngle() : rawDirection.normalize();
        double distance = Math.max(0.01, Math.min(16.0, args.arg(8).optdouble(1.0)));
        double radius = Math.max(0.0, Math.min(2.0, args.arg(9).optdouble(0.0)));
        var end = start.add(direction.scale(distance));
        // A plain level.clip is only a zero-radius ray.  Sample the perimeter
        // of the requested visual collision sphere so `radius` affects blocks
        // as well as entities.
        var sweptBlock = sweptBlockHit(player, start, end, radius);
        var blockHit = sweptBlock.hit();
        double nearestDistance = sweptBlock.distance();
        net.minecraft.world.entity.Entity nearestEntity = null;
        var search = new net.minecraft.world.phys.AABB(start, end).inflate(radius);
        for (var entity : player.level().getEntities(player, search, entity -> entity.isPickable() && !entity.isSpectator())) {
            var intersection = entity.getBoundingBox().inflate(radius).clip(start, end);
            if (intersection.isEmpty()) continue;
            double hitDistance = start.distanceTo(intersection.get());
            if (hitDistance < nearestDistance) { nearestDistance = hitDistance; nearestEntity = entity; }
        }
        LuaTable result = new LuaTable();
        result.set("distance", LuaValue.valueOf(nearestDistance));
        if (nearestEntity != null) {
            result.set("hit", LuaValue.TRUE); result.set("type", LuaValue.valueOf("ENTITY"));
            result.set("position", vec3(nearestEntity.getX(), nearestEntity.getY(), nearestEntity.getZ()));
            result.set("entity_id", LuaValue.valueOf(net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(nearestEntity.getType()).toString()));
            result.set("normal", vec3(-direction.x, -direction.y, -direction.z));
            return result;
        }
        result.set("hit", LuaValue.valueOf(blockHit.getType() != net.minecraft.world.phys.HitResult.Type.MISS));
        result.set("type", LuaValue.valueOf(blockHit.getType().name()));
        result.set("position", vec3(blockHit.getLocation().x, blockHit.getLocation().y, blockHit.getLocation().z));
        if (blockHit instanceof net.minecraft.world.phys.BlockHitResult hit) {
            var face = hit.getDirection();
            result.set("block", LuaValue.valueOf(net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(hit.getBlockPos()).getBlock()).toString()));
            result.set("normal", vec3(face.getStepX(), face.getStepY(), face.getStepZ()));
        } else result.set("normal", vec3(0, 0, 0));
        return result;
    }

    /**
     * Approximates a swept sphere with a center ray plus eight perimeter rays.
     * This is intentionally visual-only and bounded: a probe never allocates
     * collision shapes or modifies the world, but narrow block edges still
     * respond when the caller supplies a non-zero radius.
     */
    private static BlockProbeHit sweptBlockHit(net.minecraft.world.entity.player.Player player,
                                                net.minecraft.world.phys.Vec3 start,
                                                net.minecraft.world.phys.Vec3 end,
                                                double radius) {
        var center = clipBlock(player, start, end);
        double closestDistance = center.getType() == net.minecraft.world.phys.HitResult.Type.MISS
            ? start.distanceTo(end) : start.distanceTo(center.getLocation());
        if (radius <= 0.0001) return new BlockProbeHit(center, closestDistance);

        var delta = end.subtract(start);
        if (delta.lengthSqr() < 0.000001) return new BlockProbeHit(center, closestDistance);
        var direction = delta.normalize();
        var reference = Math.abs(direction.y) < 0.95
            ? new net.minecraft.world.phys.Vec3(0, 1, 0)
            : new net.minecraft.world.phys.Vec3(1, 0, 0);
        var right = direction.cross(reference).normalize().scale(radius);
        var up = right.cross(direction).normalize().scale(radius);
        var diagonalA = right.add(up).normalize().scale(radius);
        var diagonalB = right.subtract(up).normalize().scale(radius);
        net.minecraft.world.phys.Vec3[] offsets = {
            right, right.scale(-1), up, up.scale(-1),
            diagonalA, diagonalA.scale(-1), diagonalB, diagonalB.scale(-1)
        };
        for (var offset : offsets) {
            var sampleStart = start.add(offset);
            var sample = clipBlock(player, sampleStart, end.add(offset));
            if (sample.getType() == net.minecraft.world.phys.HitResult.Type.MISS) continue;
            double sampledDistance = sampleStart.distanceTo(sample.getLocation());
            if (center.getType() == net.minecraft.world.phys.HitResult.Type.MISS || sampledDistance < closestDistance) {
                center = sample;
                closestDistance = sampledDistance;
            }
        }
        return new BlockProbeHit(center, closestDistance);
    }

    private static net.minecraft.world.phys.BlockHitResult clipBlock(net.minecraft.world.entity.player.Player player,
                                                                       net.minecraft.world.phys.Vec3 start,
                                                                       net.minecraft.world.phys.Vec3 end) {
        return player.level().clip(new net.minecraft.world.level.ClipContext(
            start, end, net.minecraft.world.level.ClipContext.Block.COLLIDER,
            net.minecraft.world.level.ClipContext.Fluid.NONE, player
        ));
    }

    private record BlockProbeHit(net.minecraft.world.phys.BlockHitResult hit, double distance) {}

    private void loadBootstrap() throws IOException {
        // These files define the mandatory Standard API. A partial bootstrap is
        // not a valid runtime, so activation must fail atomically.
        StringBuilder avatarApi = new StringBuilder(48 * 1024);
        for (String resource : AVATAR_BOOTSTRAP_MODULES) {
            avatarApi.append(readBundledLua(resource)).append('\n');
        }
        // Compile the concatenated modules as one chunk so private Lua `local`
        // helpers retain exactly the same scope as the former monolithic file.
        globals.load(avatarApi.toString(), "shyne_avatar_bundle.lua").call();
        loadBundledLua("/shyne_runtime/lua/shyne_rig.lua", "shyne_rig.lua");
    }

    private String readBundledLua(String resource) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(resource)) {
            if (in == null) throw new IOException("bundled Lua resource is missing: " + resource);
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private void loadBundledLua(String resource, String chunkName) throws IOException {
        globals.load(readBundledLua(resource), chunkName).call();
    }

    private void installModuleLoader() {
        globals.set("require", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String module = arg.checkjstring();
                // Converted Figura packs conventionally use `require("lib.SquAPI")`.
                // Route any final SquAPI module segment to Shyne's native table
                // instead of executing the bundled Figura implementation.
                String normalizedModule = module.trim().toLowerCase(Locale.ROOT);
                if (normalizedModule.equals("squapi") || normalizedModule.endsWith(".squapi")) {
                    return globals.get("squapi");
                }
                LuaValue cached = modules.get(module);
                if (cached != null) return cached;
                if (!module.matches("[A-Za-z0-9_.-]+")) throw new org.luaj.vm2.LuaError("invalid module name: " + module);
                Path root = state.rootDir().toAbsolutePath().normalize();
                Path source = root.resolve(module.replace('.', java.io.File.separatorChar) + ".lua").normalize();
                if (!source.startsWith(root)) throw new org.luaj.vm2.LuaError("module escapes avatar folder: " + module);
                if (!Files.isRegularFile(source)) throw new org.luaj.vm2.LuaError("module not found: " + module);
                try {
                    modules.put(module, LuaValue.TRUE); // break recursive require cycles
                    instructionBudget.reset(LOAD_INSTRUCTION_LIMIT);
                    LuaValue result = globals.load(Files.readString(source), module).call();
                    if (result.isnil()) result = LuaValue.TRUE;
                    modules.put(module, result);
                    return result;
                } catch (Exception error) {
                    modules.remove(module);
                    throw new org.luaj.vm2.LuaError("could not load module " + module + ": " + error.getMessage());
                }
            }
        });
    }

    public void entityInit(net.minecraft.client.Minecraft client) { callEvent("ENTITY_INIT", eventPayload("entity_init")); }
    public void tick(net.minecraft.client.Minecraft client) {
        particlesThisTick = 0;
        pollInputBindings();
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

    private void requirePermission(AvatarPermission permission) {
        if (!state.permissionAllowed(permission)) {
            throw new org.luaj.vm2.LuaError("Public Avatar permission not granted: " + permission.id());
        }
    }

    private LuaTable eventPayload(String type) {
        Minecraft client = Minecraft.getInstance();
        long now = System.nanoTime();
        Long previous = eventTimes.put(type, now);
        LuaTable event = new LuaTable();
        event.set("type", LuaValue.valueOf(type));
        event.set("time", LuaValue.valueOf(now / 1_000_000_000.0));
        event.set("tick", LuaValue.valueOf(client.level == null ? 0 : client.level.getGameTime()));
        event.set("context", LuaValue.valueOf(type.equals("render") ? "player" : "client"));
        event.set("delta", LuaValue.valueOf(previous == null ? 0 : Math.min(1.0, (now - previous) / 1_000_000_000.0)));
        event.set("sequence", LuaValue.valueOf(++eventSequence));
        event.set("api", LuaValue.valueOf(state.apiStandard()));
        return event;
    }

    private LuaTable renderEventPayload(String type, float partialTick, String context) {
        Minecraft client = Minecraft.getInstance();
        LuaTable event = eventPayload(type);
        event.set("context", LuaValue.valueOf(AvatarRenderContext.normalize(context)));
        event.set("delta", LuaValue.valueOf(Math.max(0f, Math.min(1f, partialTick))));
        event.set("partial_tick", event.get("delta"));
        event.set("frame_delta", LuaValue.valueOf(client.getDeltaTracker().getRealtimeDeltaTicks()));
        event.set("first_person", LuaValue.valueOf(AvatarRenderContext.FIRST_PERSON.equals(AvatarRenderContext.normalize(context))));
        var screen = client.gui.screen();
        event.set("screen", LuaValue.valueOf(screen == null ? "" : screen.getClass().getSimpleName()));
        var camera = client.gameRenderer.mainCamera();
        event.set("camera_position", vec3(camera.position().x, camera.position().y, camera.position().z));
        event.set("camera_rotation", vec3(camera.xRot(), camera.yRot(), 0));
        return event;
    }

    private void reportRuntimeError(String category, String source, String message) {
        String entry = category + ":" + source + ": " + message;
        if (runtimeErrors.size() >= 32) runtimeErrors.removeFirst();
        runtimeErrors.addLast(entry);
        ShyneCore.LOGGER.error("[AvatarLua] {}", entry);
    }
    public void dispose() {
        callEvent("AVATAR_UNLOAD", eventPayload("avatar_unload"));
        for (InputBinding binding : inputBindings.values()) binding.handle().close();
        AvatarRenderTaskRegistry.clearOwner(renderTaskOwner);
        renderTaskIds.clear();
        globals = null;
        instructionBudget = null;
        modules.clear();
        inputBindings.clear();
    }

    private void pollInputBindings() {
        for (String id : List.copyOf(inputBindings.keySet())) {
            InputBinding binding = inputBindings.get(id);
            if (binding == null) continue;
            boolean down = binding.handle().isDown();
            int heldTicks = down ? binding.heldTicks() + 1 : 0;
            if (down != binding.wasDown()) {
                LuaValue callback = down ? binding.onPress() : binding.onRelease();
                if (callback.isfunction()) {
                    try {
                        instructionBudget.reset(EVENT_INSTRUCTION_LIMIT);
                        callback.call(LuaValue.valueOf(id));
                    } catch (Exception error) {
                        ShyneCore.LOGGER.error("[AvatarLua] input {} failed: {}", id, error.getMessage(), error);
                    }
                }
            }
            if (inputBindings.get(id) != binding) continue;
            if (down && binding.onHold().isfunction()) invokeInputCallback(id, binding.onHold());
            if (inputBindings.get(id) != binding) continue;
            if (down && binding.repeat() && heldTicks >= binding.repeatDelay()
                && (heldTicks - binding.repeatDelay()) % binding.repeatInterval() == 0) {
                invokeInputCallback(id, binding.onPress());
            }
            if (inputBindings.get(id) != binding) continue;
            inputBindings.put(id, new InputBinding(binding.handle(), binding.onPress(), binding.onRelease(), binding.onHold(),
                binding.repeat(), binding.repeatDelay(), binding.repeatInterval(), down, heldTicks));
        }
    }

    private void invokeInputCallback(String id, LuaValue callback) {
        if (!callback.isfunction()) return;
        try {
            instructionBudget.reset(EVENT_INSTRUCTION_LIMIT);
            callback.call(LuaValue.valueOf(id));
        } catch (Exception error) {
            ShyneCore.LOGGER.error("[AvatarLua] input {} failed: {}", id, error.getMessage(), error);
        }
    }

    private record InputBinding(DynamicAvatarInputRegistry.Handle handle, LuaValue onPress, LuaValue onRelease,
                                LuaValue onHold, boolean repeat, int repeatDelay, int repeatInterval,
                                boolean wasDown, int heldTicks) {}

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
        } finally {
            AvatarProfiler.recordLuaEvent(key, System.nanoTime() - started);
        }
    }

}
