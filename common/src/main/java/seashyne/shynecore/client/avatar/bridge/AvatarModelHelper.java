package seashyne.shynecore.client.avatar.bridge;

import net.minecraft.client.Minecraft;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import seashyne.shynecore.client.avatar.AvatarBoneTransforms;
import seashyne.shynecore.client.avatar.AvatarPhysicsController;
import seashyne.shynecore.client.avatar.AvatarState;
import seashyne.shynecore.client.render.AvatarBoneTransformRegistry;
import seashyne.shynecore.client.render.AvatarRenderContext;
import seashyne.shynecore.model.BbBoneDefinition;
import seashyne.shynecore.model.BbModelDefinition;

import java.util.Locale;

import static seashyne.shynecore.client.avatar.bridge.AvatarBridgeHelper.matrix;
import static seashyne.shynecore.client.avatar.bridge.AvatarBridgeHelper.vec3;

/**
 * Pure helper routines for querying bone information, visibility, and physics configs.
 */
public final class AvatarModelHelper {
    private AvatarModelHelper() {}

    public static LuaTable partInfo(AvatarState state, BbModelDefinition model, String requestedPath) {
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

    public static boolean defaultPartVisibility(AvatarState state, BbModelDefinition model, String requestedPath) {
        String path = state.resolvePath(requestedPath);
        if (model == null || path == null) return true;
        for (var bone : model.bones()) {
            if (model.bonePath(bone.uuid()).equalsIgnoreCase(path)) {
                if (isSpecialFiguraHiddenByDefault(bone.name())) return false;
                return bone.visible();
            }
        }
        for (var cube : model.cubes()) {
            if (model.cubePath(cube).equalsIgnoreCase(path)) return cube.visible();
        }
        for (var mesh : model.meshes()) {
            if (model.meshPath(mesh).equalsIgnoreCase(path)) return mesh.visible();
        }
        return true;
    }

    public static boolean isSpecialFiguraHiddenByDefault(String boneName) {
        if (boneName == null) return false;
        String normalized = boneName.trim().toLowerCase(Locale.ROOT).replace("_", "");
        return "skull".equals(normalized) || "portrait".equals(normalized);
    }

    public static AvatarPhysicsController.PhysicsConfig parsePhysicsConfig(String path, LuaValue val) {
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
            return new AvatarPhysicsController.PhysicsConfig(defaultSpring, defaultDamping, 0.0, defaultMaxAngle, 0.5, 0.5, defaultPreset);
        }

        if (val.isstring()) {
            String preset = val.tojstring().trim().toLowerCase(Locale.ROOT);
            return switch (preset) {
                case "tail" -> new AvatarPhysicsController.PhysicsConfig(0.14, 0.80, 0.0, 44.0, 0.6, 0.6, "tail");
                case "bunny_ears", "ears", "ear" -> new AvatarPhysicsController.PhysicsConfig(0.23, 0.70, 0.0, 28.0, 0.5, 0.4, "bunny_ears");
                case "hair" -> new AvatarPhysicsController.PhysicsConfig(0.18, 0.76, 0.0, 26.0, 0.5, 0.5, "hair");
                case "cloth", "cape", "skirt" -> new AvatarPhysicsController.PhysicsConfig(0.11, 0.84, 0.0, 38.0, 0.6, 0.6, "cloth");
                case "wings", "wing" -> new AvatarPhysicsController.PhysicsConfig(0.24, 0.70, 0.0, 30.0, 0.5, 0.4, "wings");
                default -> new AvatarPhysicsController.PhysicsConfig(defaultSpring, defaultDamping, 0.0, defaultMaxAngle, 0.5, 0.5, preset);
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
            return new AvatarPhysicsController.PhysicsConfig(spring, damping, gravity, maxAngle, wind, inherit, preset);
        }

        return AvatarPhysicsController.PhysicsConfig.DEFAULT;
    }
}
