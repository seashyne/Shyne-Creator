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
import seashyne.shynecore.client.avatar.AvatarPhysicsController;
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
                    case "light" -> {
                        if (args.arg(3).isnil()) yield part.clearLight();
                        int b = args.arg(3).optint(15);
                        int s = args.arg(4).optint(b);
                        yield part.setLight(b, s);
                    }
                    case "render_type" -> part.setRenderType(args.arg(3).optjstring("DEFAULT"));
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
                    case "light" -> {
                        if (!part.hasOverrideLight()) yield LuaValue.NIL;
                        LuaTable lt = new LuaTable();
                        lt.set("block", LuaValue.valueOf((part.overrideLight() >> 4) & 15));
                        lt.set("sky", LuaValue.valueOf((part.overrideLight() >> 20) & 15));
                        yield lt;
                    }
                    case "render_type" -> LuaValue.valueOf(part.renderType());
                    case "vanilla_parent" -> LuaValue.valueOf(part.vanillaParent());
                    case "vanilla_parent_mode" -> LuaValue.valueOf(part.vanillaAttachmentMode());
                    default -> LuaValue.NIL;
                };
            }
        });

        globals.set("_avatar_vanilla_visible", new VarArgFunction() {
            @Override public Varargs invoke(Varargs args) {
                String rawKey = args.arg(1).tojstring();
                boolean visible = args.arg(2).toboolean();
                var keys = VanillaVisibilityKeys.expand(rawKey);
                boolean changed = false;
                for (String key : keys) {
                    Boolean previous = state.vanillaVisibility().put(key, visible);
                    if (previous == null || previous != visible) changed = true;
                }
                if (changed) state.markSnapshotDirty();
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
                    case "shadow_radius" -> state.setShadowRadius((float) args.arg(2).optdouble(-1.0));
                    case "fov" -> {
                        Minecraft mc = Minecraft.getInstance();
                        if (mc.options != null) {
                            mc.options.fov().set((int) Math.round(args.arg(2).optdouble(mc.options.fov().get())));
                        }
                    }
                }
                return LuaValue.NIL;
            }
        });

        globals.set("_avatar_camera_read", new OneArgFunction() {
            @Override public LuaValue call(LuaValue arg) {
                String key = arg.optjstring("");
                return switch (key) {
                    case "shadow_radius" -> LuaValue.valueOf(state.shadowRadius());
                    case "offset" -> vec3(state.cameraOffsetX(), state.cameraOffsetY(), state.cameraOffsetZ());
                    case "rotation" -> vec3(state.cameraRotationX(), state.cameraRotationY(), state.cameraRotationZ());
                    case "first_person_arm" -> LuaValue.valueOf(state.firstPersonArm());
                    case "first_person_masking" -> LuaValue.valueOf(state.firstPersonMasking());
                    case "hide_head" -> LuaValue.valueOf(state.hideHeadInFirstPerson());
                    default -> LuaValue.NIL;
                };
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

                AvatarPhysicsController.PhysicsConfig config = AvatarModelHelper.parsePhysicsConfig(canonicalPath, configVal);
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
                state.setNameplateStyle(args.arg(3).optjstring(""), (int) args.arg(4).optlong(0xFFFFFFFFL), args.arg(5).optboolean(false), args.arg(6).optboolean(false));
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
        return AvatarModelHelper.partInfo(state, model, requestedPath);
    }

    public boolean defaultPartVisibility(String requestedPath) {
        return AvatarModelHelper.defaultPartVisibility(state, model, requestedPath);
    }
}
