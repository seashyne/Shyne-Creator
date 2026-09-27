package seashyne.shynecore.client.avatar.bridge;

import net.minecraft.client.Minecraft;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.lib.OneArgFunction;
import org.luaj.vm2.lib.VarArgFunction;
import seashyne.shynecore.ShyneCore;
import seashyne.shynecore.client.avatar.AvatarAction;
import seashyne.shynecore.client.avatar.AvatarBoneTransforms;
import seashyne.shynecore.client.avatar.AvatarPartState;
import seashyne.shynecore.client.avatar.AvatarPermission;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.avatar.VanillaVisibilityKeys;
import seashyne.shynecore.client.render.AvatarBoneTransformRegistry;
import seashyne.shynecore.client.render.AvatarRenderContext;
import seashyne.shynecore.client.state.ClientAnimationState;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;
import seashyne.shynecore.script.LuaSandbox;

import java.util.Locale;

import static seashyne.shynecore.client.avatar.bridge.AvatarBridgeHelper.matrix;
import static seashyne.shynecore.client.avatar.bridge.AvatarBridgeHelper.vec3;

/**
 * Handles avatar bones, parts, meshes, vanilla model transforms, and UI actions.
 */
public final class AvatarModelBridge {
    private final AvatarState state;
    private final BbModelDefinition model;
    private final java.util.function.Supplier<LuaSandbox.Budget> budgetSupplier;
    private final int eventInstructionLimit;

    public AvatarModelBridge(AvatarState state, BbModelDefinition model,
                             java.util.function.Supplier<LuaSandbox.Budget> budgetSupplier, int eventInstructionLimit) {
        this.state = state;
        this.model = model;
        this.budgetSupplier = budgetSupplier;
        this.eventInstructionLimit = eventInstructionLimit;
    }

    public void register(Globals globals) {
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

        globals.set("_avatar_camera_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                requirePermission(AvatarPermission.CAMERA);
                String key = args.arg(1).optjstring("");
                boolean value = args.arg(2).optboolean(false);
                switch (key) {
                    case "local_only" -> state.setLocalCameraOnly(value);
                    case "first_person_masking" -> state.setFirstPersonMasking(value);
                    case "first_person_arm" -> state.setFirstPersonArm(value);
                    case "hide_head_in_first_person" -> state.setHideHeadInFirstPerson(value);
                    case "offset" -> state.setCameraOffset((float) args.arg(2).optdouble(0), (float) args.arg(3).optdouble(0), (float) args.arg(4).optdouble(0));
                    case "rotation" -> state.setCameraRotation((float) args.arg(2).optdouble(0), (float) args.arg(3).optdouble(0), (float) args.arg(4).optdouble(0));
                }
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_bone_physics_set", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String requestedPath = args.arg(1).optjstring("");
                if (requestedPath.isBlank()) return LuaValue.FALSE;
                String canonicalPath = state.resolvePath(requestedPath);
                LuaValue configVal = args.arg(2);

                if (configVal.isboolean() && !configVal.toboolean()) {
                    seashyne.shynecore.client.avatar.AvatarRuntime.removeBonePhysics(canonicalPath);
                    return LuaValue.TRUE;
                }

                seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig config = parsePhysicsConfig(canonicalPath, configVal);
                seashyne.shynecore.client.avatar.AvatarRuntime.setBonePhysics(canonicalPath, config);
                return LuaValue.TRUE;
            }
        });

        globals.set("_avatar_bone_physics_get", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String requestedPath = arg.optjstring("");
                if (requestedPath.isBlank()) return LuaValue.NIL;
                String canonicalPath = state.resolvePath(requestedPath);
                seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig config = seashyne.shynecore.client.avatar.AvatarRuntime.getBonePhysics(canonicalPath);
                if (config == null) return LuaValue.NIL;
                LuaTable table = new LuaTable();
                table.set("enabled", LuaValue.TRUE);
                table.set("spring", LuaValue.valueOf(config.spring()));
                table.set("damping", LuaValue.valueOf(config.damping()));
                table.set("gravity", LuaValue.valueOf(config.gravity()));
                table.set("max_angle", LuaValue.valueOf(config.maxAngle()));
                table.set("wind", LuaValue.valueOf(config.wind()));
                table.set("inherit", LuaValue.valueOf(config.inherit()));
                table.set("preset", LuaValue.valueOf(config.preset()));
                return table;
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
                        var budget = budgetSupplier.get();
                        if (budget != null) budget.reset(eventInstructionLimit);
                        if (cb.isfunction()) cb.call();
                    } catch (Exception e) { ShyneCore.LOGGER.error("[AvatarLua] action failed: {}", e.getMessage(), e); }
                }, secondary.isfunction() ? () -> {
                    try {
                        var budget = budgetSupplier.get();
                        if (budget != null) budget.reset(eventInstructionLimit);
                        if (secondary.isfunction()) secondary.call();
                    } catch (Exception e) { ShyneCore.LOGGER.error("[AvatarLua] secondary action failed: {}", e.getMessage(), e); }
                } : null));
                return LuaValue.NIL;
            }
        });
    }

    private void requirePermission(AvatarPermission permission) {
        if (!state.permissionAllowed(permission)) {
            throw new org.luaj.vm2.LuaError("Public Avatar permission not granted: " + permission.id());
        }
    }

    public LuaTable partInfo(String requestedPath) {
        String path = state.resolvePath(requestedPath);
        String name = path == null || path.isBlank() ? "model" : path.substring(path.lastIndexOf('.') + 1);
        String parent = path != null && path.lastIndexOf('.') > 0 ? path.substring(0, path.lastIndexOf('.')) : "";
        LuaTable result = new LuaTable();
        result.set("path", LuaValue.valueOf(path == null ? "model" : path));
        result.set("name", LuaValue.valueOf(name));
        result.set("parent", LuaValue.valueOf(parent));
        result.set("role", LuaValue.valueOf(""));
        LuaTable children = new LuaTable();
        BbBoneDefinition matched = null;
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

    public boolean defaultPartVisibility(String requestedPath) {
        String path = state.resolvePath(requestedPath);
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

    private static seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig parsePhysicsConfig(String path, LuaValue val) {
        String lower = path.toLowerCase(Locale.ROOT);
        String defaultPreset = "custom";
        double defaultSpring = 0.18;
        double defaultDamping = 0.76;
        double defaultMaxAngle = 35.0;

        if (lower.contains("tail")) {
            defaultPreset = "tail";
            defaultSpring = 0.14;
            defaultDamping = 0.80;
            defaultMaxAngle = 44.0;
        } else if (lower.contains("ear")) {
            defaultPreset = "bunny_ears";
            defaultSpring = 0.23;
            defaultDamping = 0.70;
            defaultMaxAngle = 28.0;
        } else if (lower.contains("hair")) {
            defaultPreset = "hair";
            defaultSpring = 0.18;
            defaultDamping = 0.76;
            defaultMaxAngle = 26.0;
        } else if (lower.contains("wing")) {
            defaultPreset = "wings";
            defaultSpring = 0.24;
            defaultDamping = 0.70;
            defaultMaxAngle = 30.0;
        } else if (lower.contains("cloth") || lower.contains("cape") || lower.contains("skirt")) {
            defaultPreset = "cloth";
            defaultSpring = 0.11;
            defaultDamping = 0.84;
            defaultMaxAngle = 38.0;
        }

        if (val == null || val.isnil() || (val.isboolean() && val.toboolean())) {
            return new seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig(defaultSpring, defaultDamping, 0.0, defaultMaxAngle, 0.5, 0.5, defaultPreset);
        }

        if (val.isstring()) {
            String preset = val.tojstring().trim().toLowerCase(Locale.ROOT);
            return switch (preset) {
                case "tail" -> new seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig(0.14, 0.80, 0.0, 44.0, 0.6, 0.6, "tail");
                case "bunny_ears", "ears", "ear" -> new seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig(0.23, 0.70, 0.0, 28.0, 0.5, 0.4, "bunny_ears");
                case "hair" -> new seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig(0.18, 0.76, 0.0, 26.0, 0.5, 0.5, "hair");
                case "cloth", "cape", "skirt" -> new seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig(0.11, 0.84, 0.0, 38.0, 0.6, 0.6, "cloth");
                case "wings", "wing" -> new seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig(0.24, 0.70, 0.0, 30.0, 0.5, 0.4, "wings");
                default -> new seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig(defaultSpring, defaultDamping, 0.0, defaultMaxAngle, 0.5, 0.5, preset);
            };
        }

        if (val.istable()) {
            double spring = val.get("spring").optdouble(val.get("stiffness").optdouble(defaultSpring));
            double damping = val.get("damping").optdouble(defaultDamping);
            double gravity = val.get("gravity").optdouble(0.0);
            double maxAngle = val.get("max_angle").optdouble(val.get("maxAngle").optdouble(defaultMaxAngle));
            double wind = val.get("wind").optdouble(val.get("wind_strength").optdouble(0.5));
            double inherit = val.get("inherit").optdouble(0.5);
            String preset = val.get("preset").optjstring(defaultPreset);
            return new seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig(spring, damping, gravity, maxAngle, wind, inherit, preset);
        }

        return seashyne.shynecore.client.avatar.AvatarPhysicsController.PhysicsConfig.DEFAULT;
    }
}
