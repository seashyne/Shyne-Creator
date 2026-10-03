package seashyne.shynecore.client.avatar.bridge;

import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.UUID;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarLoader;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarQuotaManager;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.ShyneApiStandard;
import seashyne.shynecore.client.input.DynamicAvatarInputRegistry;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.voice.ShyneMicrophoneState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntSupplier;

/**
 * Handles system operations: chat print, commands, sound, particles, microphone, permissions, and diagnostics.
 */
public final class AvatarSystemBridge {
    private static final int MAX_PRINTS_PER_SECOND = 60;

    private final AvatarState state;
    private final BbModelDefinition model;
    private final IntSupplier inputBindingCountSupplier;
    private final IntSupplier inputConflictCountSupplier;
    private final IntSupplier renderTaskCountSupplier;
    private final IntSupplier loadedModuleCountSupplier;

    private long lastPrintResetNanos;
    private int printCountThisSecond;
    private long lastShyneCommandNanos;
    private int particlesThisTick;
    private final Deque<String> runtimeErrors = new ArrayDeque<>();

    public AvatarSystemBridge(AvatarState state, BbModelDefinition model,
                              IntSupplier inputBindingCountSupplier,
                              IntSupplier inputConflictCountSupplier,
                              IntSupplier renderTaskCountSupplier,
                              IntSupplier loadedModuleCountSupplier) {
        this.state = state;
        this.model = model;
        this.inputBindingCountSupplier = inputBindingCountSupplier;
        this.inputConflictCountSupplier = inputConflictCountSupplier;
        this.renderTaskCountSupplier = renderTaskCountSupplier;
        this.loadedModuleCountSupplier = loadedModuleCountSupplier;
    }

    public void register(Globals globals) {
        globals.set("print", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                StringBuilder sb = new StringBuilder();
                for (int i = 1; i <= args.narg(); i++) {
                    if (i > 1) sb.append(" ");
                    sb.append(args.arg(i).tojstring());
                }
                String message = sb.toString();

                Minecraft client = Minecraft.getInstance();
                long now = System.nanoTime();
                if (now - lastPrintResetNanos > 1_000_000_000L) {
                    lastPrintResetNanos = now;
                    printCountThisSecond = 0;
                }
                if (client != null) {
                    if (printCountThisSecond <= MAX_PRINTS_PER_SECOND) {
                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.sendSystemMessage(Component.literal(message));
                            }
                        });
                    } else if (printCountThisSecond == MAX_PRINTS_PER_SECOND + 1) {
                        client.execute(() -> {
                            if (client.player != null) {
                                client.player.sendSystemMessage(
                                    Component.literal("§c[Avatar:" + state.avatarId() + "] Chat print rate limit exceeded (max " + MAX_PRINTS_PER_SECOND + "/sec)§r")
                                );
                            }
                        });
                    }
                }

                ShyneCore.LOGGER.info("[Avatar:{}] {}", state.avatarId(), message);
                return LuaValue.NIL;
            }
        });

        globals.set("printJson", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String json;
                if (arg.isstring()) {
                    json = arg.tojstring();
                } else if (arg.istable()) {
                    Object javaObj = seashyne.shynecore.script.LuaValueCodec.toJava(arg);
                    json = new com.google.gson.Gson().toJson(javaObj);
                } else {
                    json = arg.tojstring();
                }

                Minecraft client = Minecraft.getInstance();
                long now = System.nanoTime();
                if (now - lastPrintResetNanos > 1_000_000_000L) {
                    lastPrintResetNanos = now;
                    printCountThisSecond = 0;
                }
                printCountThisSecond++;
                if (printCountThisSecond <= MAX_PRINTS_PER_SECOND) {
                    client.execute(() -> {
                        if (client.player != null) {
                            client.player.sendSystemMessage(parseJsonComponent(json));
                        }
                    });
                }
                ShyneCore.LOGGER.info("[Avatar:{}:json] {}", state.avatarId(), json);
                return LuaValue.NIL;
            }
        });

        globals.set("_minecraft_shyne_command", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                requirePermission(AvatarPermission.COMMAND);
                String command = arg.checkjstring().trim();
                if (command.startsWith("/")) command = command.substring(1).trim();
                if (command.isBlank() || command.length() > 256) {
                    throw new LuaError("invalid Shyne command");
                }
                String root = command.split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
                if (!root.equals("shyne") && !root.equals("sjyne")) {
                    throw new LuaError("avatar scripts may only run /shyne or /sjyne commands");
                }
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return LuaValue.FALSE;
                long now = System.nanoTime();
                if (now - lastShyneCommandNanos < 250_000_000L) {
                    throw new LuaError("Shyne command rate limit: wait 250 ms");
                }
                lastShyneCommandNanos = now;
                client.player.connection.sendCommand(command);
                return LuaValue.TRUE;
            }
        });

        globals.set("_shyne_sound_play", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!state.permissionAllowed(AvatarPermission.SOUND)) return LuaValue.FALSE;
                if (!AvatarQuotaManager.get(state.avatarId()).tryPlaySound()) return LuaValue.FALSE;
                Minecraft client = Minecraft.getInstance();
                if (client.player == null) return LuaValue.FALSE;
                String soundName = args.arg(1).optjstring("");
                if (soundName.isBlank()) return LuaValue.FALSE;

                float volume = (float) Math.max(0, Math.min(4, args.arg(2).optdouble(1)));
                float pitch = (float) Math.max(0.05, Math.min(4, args.arg(3).optdouble(1)));
                Double posX = args.arg(4).isnil() ? null : args.arg(4).todouble();
                Double posY = args.arg(5).isnil() ? null : args.arg(5).todouble();
                Double posZ = args.arg(6).isnil() ? null : args.arg(6).todouble();

                // 1. Try playing custom .ogg from avatar directory
                if (seashyne.shynecore.client.avatar.sound.AvatarCustomSoundManager.play(state, soundName, volume, pitch, posX, posY, posZ)) {
                    return LuaValue.TRUE;
                }

                // 2. Fallback to vanilla Minecraft sound event
                var id = Identifier.tryParse(soundName.contains(":") ? soundName : "minecraft:" + soundName);
                if (id == null) return LuaValue.FALSE;
                var sound = BuiltInRegistries.SOUND_EVENT.get(id).map(ref -> ref.value()).orElse(null);
                if (sound == null) return LuaValue.FALSE;
                if (posX != null && posY != null && posZ != null && client.level != null) {
                    client.level.playLocalSound(posX, posY, posZ, sound, net.minecraft.sounds.SoundSource.PLAYERS, volume, pitch, false);
                } else {
                    client.player.playSound(sound, volume, pitch);
                }
                return LuaValue.TRUE;
            }
        });

        globals.set("_shyne_particle_spawn", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!state.permissionAllowed(AvatarPermission.PARTICLE)) return LuaValue.FALSE;
                if (!AvatarQuotaManager.get(state.avatarId()).trySpawnParticle()) return LuaValue.FALSE;
                Minecraft client = Minecraft.getInstance();
                if (client.level == null || particlesThisTick >= 256) return LuaValue.FALSE;
                String rawId = args.arg(1).optjstring("");
                if (rawId.isBlank()) return LuaValue.FALSE;
                if (!rawId.contains(":")) rawId = "minecraft:" + rawId;
                var id = Identifier.tryParse(rawId);
                if (id == null) return LuaValue.FALSE;

                double posX = args.arg(2).optdouble(0);
                double posY = args.arg(3).optdouble(0);
                double posZ = args.arg(4).optdouble(0);
                double velX = args.arg(5).optdouble(0);
                double velY = args.arg(6).optdouble(0);
                double velZ = args.arg(7).optdouble(0);

                if ("minecraft:dust".equals(rawId)) {
                    float r = (float) Math.max(0.0, Math.min(1.0, args.arg(8).optdouble(1.0)));
                    float g = (float) Math.max(0.0, Math.min(1.0, args.arg(9).optdouble(1.0)));
                    float b = (float) Math.max(0.0, Math.min(1.0, args.arg(10).optdouble(1.0)));
                    float scale = (float) Math.max(0.1, Math.min(4.0, args.arg(11).optdouble(1.0)));
                    int rInt = Math.max(0, Math.min(255, (int) (r * 255)));
                    int gInt = Math.max(0, Math.min(255, (int) (g * 255)));
                    int bInt = Math.max(0, Math.min(255, (int) (b * 255)));
                    int packedColor = (rInt << 16) | (gInt << 8) | bInt;
                    DustParticleOptions dust = new DustParticleOptions(packedColor, scale);
                    client.level.addParticle(dust, posX, posY, posZ, velX, velY, velZ);
                    particlesThisTick++;
                    return LuaValue.TRUE;
                }

                var type = BuiltInRegistries.PARTICLE_TYPE.get(id).map(ref -> ref.value()).orElse(null);
                if (!(type instanceof SimpleParticleType particle)) return LuaValue.FALSE;
                client.level.addParticle(particle, posX, posY, posZ, velX, velY, velZ);
                particlesThisTick++;
                return LuaValue.TRUE;
            }
        });

        globals.set("_microphone_available", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(state.permissionAllowed(AvatarPermission.MICROPHONE) && ShyneMicrophoneState.snapshot().available()); }
        });
        globals.set("_microphone_level", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!state.permissionAllowed(AvatarPermission.MICROPHONE)) return LuaValue.ZERO;
                String idStr = args.arg(1).optjstring(null);
                UUID target = idStr != null && !idStr.isBlank() ? parseUuidSafe(idStr) : state.boundEntityId();
                return LuaValue.valueOf(ShyneMicrophoneState.getSpeakerSnapshot(target).level());
            }
        });
        globals.set("_microphone_speaking", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                if (!state.permissionAllowed(AvatarPermission.MICROPHONE)) return LuaValue.FALSE;
                String idStr = args.arg(1).optjstring(null);
                UUID target = idStr != null && !idStr.isBlank() ? parseUuidSafe(idStr) : state.boundEntityId();
                return LuaValue.valueOf(ShyneMicrophoneState.getSpeakerSnapshot(target).speaking());
            }
        });
        globals.set("_microphone_muted", new ZeroArgFunction() {
            @Override public LuaValue call() { return LuaValue.valueOf(!state.permissionAllowed(AvatarPermission.MICROPHONE) || ShyneMicrophoneState.snapshot().muted()); }
        });

        globals.set("_shyne_diagnostics", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable result = new LuaTable();
                result.set("api_version", LuaValue.valueOf(AvatarLoader.AVATAR_API_VERSION));
                result.set("api_standard", LuaValue.valueOf(state.apiStandard()));
                result.set("api_automatic", LuaValue.valueOf(state.automaticApi()));
                result.set("custom_render_api_version", LuaValue.valueOf("1.4"));
                result.set("parts_controlled", LuaValue.valueOf(state.parts().size()));
                result.set("animation_layers", LuaValue.valueOf(state.animationLayers().size()));
                result.set("input_bindings", LuaValue.valueOf(inputBindingCountSupplier.getAsInt()));
                result.set("input_conflicts", LuaValue.valueOf(inputConflictCountSupplier.getAsInt()));
                result.set("input_errors", LuaValue.valueOf(DynamicAvatarInputRegistry.diagnostics().size()));
                result.set("modules_loaded", LuaValue.valueOf(loadedModuleCountSupplier.getAsInt()));
                result.set("particles_this_tick", LuaValue.valueOf(particlesThisTick));
                result.set("render_tasks", LuaValue.valueOf(renderTaskCountSupplier.getAsInt()));
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
                for (String feature : List.of("lua_api_1_1", "api_auto_latest", "api_requirements", "event_isolation", "event_delta", "scheduler", "vector_math", "permission_query", "multi_animation", "animation_fade", "animation_mask", "animation_transition", "animation_expression", "animation_parameter", "blockbench_5_1", "additive_animation", "part_color", "part_opacity", "part_translucency", "part_emissive", "camera_transform", "nameplate", "sound", "particle", "input", "input_mouse", "input_modifiers", "input_repeat", "render_text", "render_item", "render_block", "render_sprite", "render_line", "render_world", "render_world_3d", "render_world_light", "render_bone_binding", "render_rect", "render_outline", "render_polyline", "render_groups", "render_task_update", "render_screen", "script_canvas_ui", "script_canvas_click", "profiler", "online_sync")) {
                    features.set(feature, LuaValue.TRUE);
                }
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

        globals.set("_avatar_quota_stats", new ZeroArgFunction() {
            @Override public LuaValue call() {
                var snap = AvatarQuotaManager.get(state.avatarId()).snapshot();
                LuaTable t = new LuaTable();
                t.set("dynamic_textures", LuaValue.valueOf(snap.dynamicTextures()));
                t.set("max_dynamic_textures", LuaValue.valueOf(snap.maxDynamicTextures()));
                t.set("dynamic_texture_bytes", LuaValue.valueOf(snap.dynamicTextureBytes()));
                t.set("max_dynamic_texture_bytes", LuaValue.valueOf(snap.maxDynamicTextureBytes()));
                t.set("active_render_tasks", LuaValue.valueOf(snap.activeRenderTasks()));
                t.set("max_render_tasks", LuaValue.valueOf(snap.maxRenderTasks()));
                t.set("particles_per_sec", LuaValue.valueOf(snap.particlesPerSecond()));
                t.set("particles_dropped", LuaValue.valueOf(snap.particlesDroppedPerSecond()));
                t.set("max_particles_per_sec", LuaValue.valueOf(snap.maxParticlesPerSecond()));
                t.set("sounds_per_sec", LuaValue.valueOf(snap.soundsPerSecond()));
                t.set("sounds_dropped", LuaValue.valueOf(snap.soundsDroppedPerSecond()));
                t.set("max_sounds_per_sec", LuaValue.valueOf(snap.maxSoundsPerSecond()));
                return t;
            }
        });
    }

    public void tickReset() {
        particlesThisTick = 0;
    }

    public int particlesThisTick() {
        return particlesThisTick;
    }

    public void requirePermission(AvatarPermission permission) {
        if (!state.permissionAllowed(permission)) {
            throw new LuaError("Public Avatar permission not granted: " + permission.id());
        }
    }

    private static long lastErrorChatNanos = 0;
    private static String lastErrorMessage = "";

    public void reportRuntimeError(String category, String source, String message) {
        String entry = "[" + category + ":" + source + "] " + message;
        if (runtimeErrors.size() >= 32) runtimeErrors.pollFirst();
        runtimeErrors.addLast(entry);
        ShyneCore.LOGGER.error("[AvatarLua] {}", entry);

        Minecraft client = Minecraft.getInstance();
        if (client != null) {
            long now = System.nanoTime();
            // Rate limit error messages in chat to prevent spam on render loops (max 1 per second if same, or 500ms if different)
            if (!entry.equals(lastErrorMessage) || now - lastErrorChatNanos > 1_000_000_000L) {
                lastErrorChatNanos = now;
                lastErrorMessage = entry;
                client.execute(() -> {
                    if (client.player != null) {
                        client.player.sendSystemMessage(Component.literal("§c[Avatar Error:" + state.avatarId() + "] §e" + entry));
                    }
                });
            }
        }
    }

    public Deque<String> runtimeErrors() {
        return runtimeErrors;
    }

    private static Component parseJsonComponent(String json) {
        if (json == null || json.isBlank()) {
            return Component.empty();
        }
        try {
            var elem = com.google.gson.JsonParser.parseString(json);
            var result = net.minecraft.network.chat.ComponentSerialization.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, elem);
            var opt = result.result();
            if (opt.isPresent()) {
                return opt.get();
            }
        } catch (Throwable ignored) {}
        try {
            var elem = com.google.gson.JsonParser.parseString(json);
            if (elem.isJsonObject() && elem.getAsJsonObject().has("text")) {
                return Component.literal(elem.getAsJsonObject().get("text").getAsString());
            }
        } catch (Throwable ignored) {}
        return Component.literal(json);
    }

    private static UUID parseUuidSafe(String str) {
        if (str == null || str.isBlank()) return null;
        try {
            return UUID.fromString(str);
        } catch (Exception e) {
            return null;
        }
    }
}
