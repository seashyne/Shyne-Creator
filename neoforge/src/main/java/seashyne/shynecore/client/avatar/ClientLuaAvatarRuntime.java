package seashyne.shynecore.client.avatar;

import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.script.LuaSandbox;
import seashyne.shynecore.voice.ShyneMicrophoneState;
import net.minecraft.client.Minecraft;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

public final class ClientLuaAvatarRuntime {
    private static final int LOAD_INSTRUCTION_LIMIT = 1_000_000;
    private static final int EVENT_INSTRUCTION_LIMIT = 200_000;
    private final AvatarState state;
    private final Path scriptPath;
    private Globals globals;
    private LuaSandbox.Budget instructionBudget;
    private final Map<String, LuaValue> modules = new HashMap<>();
    private static final Map<String, net.minecraft.client.KeyMapping> REGISTERED_INPUTS = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, InputBinding> inputBindings = new LinkedHashMap<>();
    private long lastShyneCommandNanos;
    private int particlesThisTick;

    public ClientLuaAvatarRuntime(AvatarState state, Path scriptPath) {
        this.state = state;
        this.scriptPath = scriptPath;
    }

    public boolean load() {
        try {
            LuaSandbox.Environment environment = LuaSandbox.create();
            globals = environment.globals();
            instructionBudget = environment.budget();
            globals.set("SHYNE_AVATAR_ID", LuaValue.valueOf(state.avatarId()));
            globals.set("SHYNE_AVATAR_PATH", LuaValue.valueOf(state.rootDir().toString().replace('\\', '/')));
            globals.set("AVATAR_ID", LuaValue.valueOf(state.avatarId()));
            globals.set("AVATAR_PATH", LuaValue.valueOf(state.rootDir().toString().replace('\\', '/')));
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
        }
    }

    private void installApi() {
        globals.set("_avatar_state_get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                Object value = state.vars().get(arg.tojstring());
                return value == null ? LuaValue.NIL : Coerce.toLua(value);
            }
        });
        globals.set("_avatar_state_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                state.vars().put(args.arg(1).tojstring(), Coerce.fromLua(args.arg(2)));
                state.markSnapshotDirty();
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_synced_get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                Object value = state.syncedVars().get(arg.tojstring());
                return value == null ? LuaValue.NIL : Coerce.toLua(value);
            }
        });
        globals.set("_avatar_synced_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                state.syncedVars().put(args.arg(1).tojstring(), Coerce.fromLua(args.arg(2)));
                state.markSyncedDirty();
                state.markSnapshotDirty();
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_remote_synced_get", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String playerId = args.arg(1).optjstring("");
                String key = args.arg(2).optjstring("");
                Object value = ClientAnimationState.getAvatarSyncedVar(playerId, key);
                return value == null ? LuaValue.NIL : Coerce.toLua(value);
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
                    case "color" -> part.setColor((float) args.arg(3).todouble(), (float) args.arg(4).todouble(), (float) args.arg(5).todouble());
                    case "opacity" -> part.setOpacity((float) args.arg(3).todouble());
                    case "emissive" -> part.setEmissive(args.arg(3).toboolean());
                    default -> false;
                };
                if (changed) state.markSnapshotDirty();
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_part_read", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                AvatarPartState part = state.parts().get(state.resolvePath(args.arg(1).optjstring("")));
                String key = args.arg(2).optjstring("");
                if (part == null) {
                    return switch (key) {
                        case "visible" -> LuaValue.TRUE;
                        case "scale" -> vec3(1, 1, 1);
                        default -> vec3(0, 0, 0);
                    };
                }
                return switch (key) {
                    case "visible" -> LuaValue.valueOf(part.visible());
                    case "position" -> vec3(part.posX(), part.posY(), part.posZ());
                    case "rotation" -> vec3(part.rotX(), part.rotY(), part.rotZ());
                    case "scale" -> vec3(part.scaleX(), part.scaleY(), part.scaleZ());
                    case "color" -> vec3(((part.colorArgb() >> 16) & 255) / 255.0, ((part.colorArgb() >> 8) & 255) / 255.0, (part.colorArgb() & 255) / 255.0);
                    case "opacity" -> LuaValue.valueOf(((part.colorArgb() >>> 24) & 255) / 255.0);
                    case "emissive" -> LuaValue.valueOf(part.emissive());
                    default -> LuaValue.NIL;
                };
            }
        });
        globals.set("_avatar_vanilla_visible", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String key = args.arg(1).tojstring();
                boolean visible = args.arg(2).toboolean();
                Boolean previous = state.vanillaVisibility().put(key, visible);
                if (previous == null || previous != visible) state.markSnapshotDirty();
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_anim_play", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarRuntime.playAnimation(arg.tojstring());
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_anim_play_ex", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                Boolean loop = args.arg(5).isnil() ? null : args.arg(5).toboolean();
                AvatarRuntime.playAnimation(
                    args.arg(1).optjstring(""), args.arg(2).optdouble(1), args.arg(3).optdouble(1), args.arg(4).optint(0), loop,
                    args.arg(6).optint(0), args.arg(7).optint(0), stringList(args.arg(8)), args.arg(9).optboolean(false)
                );
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_anim_stop", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                AvatarRuntime.stopAnimation(arg.optjstring(""));
                return LuaValue.NIL;
            }
        });
        globals.set("_avatar_anim_playing", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                return LuaValue.valueOf(AvatarRuntime.isAnimationPlaying(arg.optjstring("")));
            }
        });
        globals.set("_avatar_anim_exists", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                var model = AvatarRuntime.activeModel();
                return LuaValue.valueOf(model != null && model.hasAnimation(arg.optjstring("")));
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
        state.setSyncedSchemaPath(arg.optjstring(""));
        return LuaValue.NIL;
    }
});
globals.set("_avatar_schema_validate", new VarArgFunction() {
    @Override public Varargs invoke(Varargs args) {
        String key = args.arg(1).optjstring("");
        LuaValue value = args.arg(2);
        Object decoded = Coerce.fromLua(value);
        boolean valid = key != null && !key.isBlank() && decoded != null;
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
                state.registerAction(new AvatarAction(id, title, description, page, icon, localOnly, closeOnUse, () -> {
                    try {
                        instructionBudget.reset(EVENT_INSTRUCTION_LIMIT);
                        if (cb.isfunction()) cb.call();
                    } catch (Exception e) { ShyneCore.LOGGER.error("[AvatarLua] action failed: {}", e.getMessage(), e); }
                }));
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
                return LuaValue.valueOf(AvatarRuntime.playEmote(arg.optjstring("")));
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
                return LuaValue.valueOf(AvatarRuntime.triggerAnimationGraph(arg.optjstring("")));
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
                    case "player.vehicle" -> LuaValue.valueOf(player.isPassenger());
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
                    case "client.paused" -> LuaValue.valueOf(client.isPaused());
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
                if (inputBindings.size() >= 32) throw new org.luaj.vm2.LuaError("avatar input limit is 32 bindings");
                String id = args.arg(1).optjstring("").toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_.-]", "_");
                if (id.isBlank()) throw new org.luaj.vm2.LuaError("input id is required");
                String label = args.arg(2).optjstring(id);
                int defaultKey = args.arg(3).optint(org.lwjgl.glfw.GLFW.GLFW_KEY_UNKNOWN);
                String registryKey = state.avatarId() + "." + id;
                net.minecraft.client.KeyMapping mapping = REGISTERED_INPUTS.computeIfAbsent(registryKey, ignored ->
                    seashyne.shynecore.client.ui.ShyneKeybinds.registerDynamic(
                        new net.minecraft.client.KeyMapping("key.shyne_avatar." + registryKey, defaultKey, net.minecraft.client.KeyMapping.Category.MISC)
                    )
                );
                inputBindings.put(id, new InputBinding(mapping, args.arg(4), args.arg(5), mapping.isDown()));
                return LuaValue.valueOf(id);
            }
        });
        globals.set("_shyne_diagnostics", new ZeroArgFunction() {
            @Override public LuaValue call() {
                LuaTable result = new LuaTable();
                var model = AvatarRuntime.activeModel();
                result.set("api_version", LuaValue.valueOf(AvatarLoader.AVATAR_API_VERSION));
                result.set("parts_controlled", LuaValue.valueOf(state.parts().size()));
                result.set("animation_layers", LuaValue.valueOf(state.animationLayers().size()));
                result.set("input_bindings", LuaValue.valueOf(inputBindings.size()));
                result.set("modules_loaded", LuaValue.valueOf(modules.size()));
                result.set("particles_this_tick", LuaValue.valueOf(particlesThisTick));
                if (model != null) {
                    result.set("bones", LuaValue.valueOf(model.bones().size()));
                    result.set("cubes", LuaValue.valueOf(model.cubes().size()));
                    result.set("animations", LuaValue.valueOf(model.animations().size()));
                    result.set("textures", LuaValue.valueOf(model.textures().size()));
                }
                LuaTable features = new LuaTable();
                for (String feature : List.of("multi_animation", "animation_fade", "animation_mask", "additive_animation", "shortest_rotation", "part_color", "part_opacity", "part_translucency", "part_emissive", "camera_transform", "nameplate", "sound", "particle", "input", "online_sync")) features.set(feature, LuaValue.TRUE);
                result.set("features", features);
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
        value.set("id", LuaValue.valueOf(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString()));
        value.set("count", LuaValue.valueOf(stack.getCount()));
        value.set("empty", LuaValue.valueOf(stack.isEmpty()));
        return value;
    }

    private void loadBootstrap() {
        try (InputStream in = getClass().getResourceAsStream("/shyne_runtime/lua/shyne_avatar.lua")) {
            if (in == null) return;
            String source = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            globals.load(source, "shyne_avatar.lua").call();
        } catch (Exception e) {
            ShyneCore.LOGGER.error("[AvatarLua] bootstrap failed: {}", e.getMessage(), e);
        }
    }

    private void installModuleLoader() {
        globals.set("require", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String module = arg.checkjstring();
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
    public void render() { callEvent("RENDER", eventPayload("render")); }
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

    private static LuaTable eventPayload(String type) {
        Minecraft client = Minecraft.getInstance();
        LuaTable event = new LuaTable();
        event.set("type", LuaValue.valueOf(type));
        event.set("time", LuaValue.valueOf(System.nanoTime() / 1_000_000_000.0));
        event.set("tick", LuaValue.valueOf(client.level == null ? 0 : client.level.getGameTime()));
        event.set("context", LuaValue.valueOf(type.equals("render") ? "player" : "client"));
        event.set("delta", LuaValue.ZERO);
        return event;
    }
    public void dispose() {
        callEvent("AVATAR_UNLOAD", eventPayload("avatar_unload"));
        globals = null;
        instructionBudget = null;
        modules.clear();
        inputBindings.clear();
    }

    private void pollInputBindings() {
        for (var entry : inputBindings.entrySet()) {
            InputBinding binding = entry.getValue();
            boolean down = binding.mapping().isDown();
            if (down != binding.wasDown()) {
                LuaValue callback = down ? binding.onPress() : binding.onRelease();
                if (callback.isfunction()) {
                    try {
                        instructionBudget.reset(EVENT_INSTRUCTION_LIMIT);
                        callback.call(LuaValue.valueOf(entry.getKey()));
                    } catch (Exception error) {
                        ShyneCore.LOGGER.error("[AvatarLua] input {} failed: {}", entry.getKey(), error.getMessage(), error);
                    }
                }
                entry.setValue(new InputBinding(binding.mapping(), binding.onPress(), binding.onRelease(), down));
            }
        }
    }

    private record InputBinding(net.minecraft.client.KeyMapping mapping, LuaValue onPress, LuaValue onRelease, boolean wasDown) {}

    private void callEvent(String key, LuaValue... args) {
        if (globals == null) return;
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
        }
    }

    private static final class Coerce {
        static LuaValue toLua(Object value) {
            if (value == null) return LuaValue.NIL;
            if (value instanceof Boolean b) return LuaValue.valueOf(b);
            if (value instanceof Number n) return LuaValue.valueOf(n.doubleValue());
            if (value instanceof String s) return LuaValue.valueOf(s);
            if (value instanceof Map<?, ?> map) {
                LuaTable table = new LuaTable();
                for (Map.Entry<?, ?> entry : map.entrySet()) table.set(String.valueOf(entry.getKey()), toLua(entry.getValue()));
                return table;
            }
            if (value instanceof List<?> list) {
                LuaTable table = new LuaTable();
                for (int i = 0; i < list.size(); i++) table.set(i + 1, toLua(list.get(i)));
                return table;
            }
            return LuaValue.valueOf(String.valueOf(value));
        }

        static Object fromLua(LuaValue value) {
            if (value.isboolean()) return value.toboolean();
            if (value.isnumber()) return value.todouble();
            if (value.isstring()) return value.tojstring();
            if (value.isnil()) return null;
            if (value.istable()) {
                LuaTable table = (LuaTable) value;
                List<Object> array = new ArrayList<>();
                Map<String, Object> map = new LinkedHashMap<>();
                boolean arrayLike = true;
                Varargs next = LuaValue.NIL;
                while (true) {
                    next = table.next(next.arg1());
                    LuaValue key = next.arg1();
                    if (key.isnil()) break;
                    LuaValue val = next.arg(2);
                    if (arrayLike && key.isinttype()) {
                        int idx = key.toint();
                        while (array.size() < idx) array.add(null);
                        array.set(idx - 1, fromLua(val));
                    } else {
                        arrayLike = false;
                        map.put(key.tojstring(), fromLua(val));
                    }
                }
                if (arrayLike && !array.isEmpty()) return array;
                if (!array.isEmpty()) {
                    for (int i = 0; i < array.size(); i++) map.put(String.valueOf(i + 1), array.get(i));
                }
                return map;
            }
            return value.tojstring();
        }
    }
}
