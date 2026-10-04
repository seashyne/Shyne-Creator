package seashyne.shynecore.model;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static seashyne.shynecore.model.BbModelJsonHelper.*;

/**
 * Converts Blockbench {@code .bbmodel} JSON into Shyne's immutable runtime model.
 *
 * <p>Coordinates texture resolution, bone hierarchies, cube/mesh geometries,
 * and keyframe animations into one canonical {@link BbModelDefinition}.</p>
 */
public final class BbModelParser {
    private static final Gson GSON = new Gson();

    private BbModelParser() {}

    public static BbModelDefinition parse(Path path, String sourceModId) throws IOException {
        try (Reader reader = Files.newBufferedReader(path)) {
            JsonObject root = GSON.fromJson(reader, JsonObject.class);
            if (root == null) throw new IOException("Invalid .bbmodel JSON: " + path.getFileName());

            String displayName = root.has("name") ? root.get("name").getAsString() : stripExtension(path.getFileName().toString());
            int formatVersion = parseFormatVersion(root);
            int textureWidth = root.has("resolution") && root.get("resolution").isJsonObject()
                ? safeInt(root.getAsJsonObject("resolution").get("width"), 16) : 16;
            int textureHeight = root.has("resolution") && root.get("resolution").isJsonObject()
                ? safeInt(root.getAsJsonObject("resolution").get("height"), 16) : 16;

            List<BbTextureDefinition> textures = BbTextureResolver.parseTextures(root, path, textureWidth, textureHeight);
            String primaryTexture = textures.isEmpty() ? null : textures.get(0).relativePath();

            Map<String, RawBone> rawBones = new LinkedHashMap<>();
            Map<String, JsonObject> groupDefinitions = parseGroupDefinitions(root);
            if (root.has("outliner") && root.get("outliner").isJsonArray()) {
                for (JsonElement entry : root.getAsJsonArray("outliner")) {
                    collectRawBones(entry, null, null, rawBones, groupDefinitions);
                }
            }

            Map<String, String> elementParents = new HashMap<>();
            for (RawBone bone : rawBones.values()) {
                for (String elementUuid : bone.childElementUuids) elementParents.put(elementUuid, bone.uuid);
            }
            List<BbCubeDefinition> cubes = BbGeometryParser.parseCubes(root, elementParents);
            List<BbMeshDefinition> meshes = BbGeometryParser.parseMeshes(root, elementParents);
            Map<String, Integer> cubeCountByParentUuid = new HashMap<>();
            for (BbCubeDefinition cube : cubes) {
                if (cube.parentBoneUuid() != null) {
                    cubeCountByParentUuid.merge(cube.parentBoneUuid(), 1, Integer::sum);
                }
            }

            List<BbBoneDefinition> bones = new ArrayList<>();
            for (RawBone raw : rawBones.values()) {
                bones.add(new BbBoneDefinition(
                    raw.uuid,
                    raw.name,
                    raw.parentName,
                    raw.parentUuid,
                    raw.parentType,
                    raw.role,
                    raw.tags,
                    raw.physicsPreset,
                    cubeCountByParentUuid.getOrDefault(raw.uuid, 0),
                    raw.pivotX,
                    raw.pivotY,
                    raw.pivotZ,
                    raw.rotationX,
                    raw.rotationY,
                    raw.rotationZ,
                    raw.visible,
                    List.copyOf(raw.childBoneUuids)
                ));
            }

            List<BbAnimationDefinition> animations = BbAnimationParser.parseAnimations(root, rawBones, usesV5AnimationCoordinates(root));
            String modelId = sourceModId + ":" + stripExtension(path.getFileName().toString()).toLowerCase(Locale.ROOT);

            return new BbModelDefinition(
                modelId,
                sourceModId,
                displayName,
                path,
                formatVersion,
                textureWidth,
                textureHeight,
                primaryTexture,
                List.copyOf(textures),
                List.copyOf(bones),
                List.copyOf(cubes),
                List.copyOf(meshes),
                List.copyOf(animations)
            );
        }
    }

    private static Map<String, JsonObject> parseGroupDefinitions(JsonObject root) {
        Map<String, JsonObject> definitions = new LinkedHashMap<>();
        if (!root.has("groups") || !root.get("groups").isJsonArray()) return definitions;
        for (JsonElement entry : root.getAsJsonArray("groups")) {
            if (!entry.isJsonObject()) continue;
            JsonObject group = entry.getAsJsonObject();
            String uuid = raw(group.get("uuid"), "");
            if (!uuid.isBlank()) definitions.put(uuid, group);
        }
        return definitions;
    }

    private static JsonElement groupProperty(JsonObject outline, JsonObject definition, String name) {
        if (outline != null && outline.has(name)) return outline.get(name);
        return definition == null ? null : definition.get(name);
    }

    private static void collectRawBones(
        JsonElement entry,
        String parentUuid,
        String parentName,
        Map<String, RawBone> rawBones,
        Map<String, JsonObject> groupDefinitions
    ) {
        if (!entry.isJsonObject()) return;
        JsonObject obj = entry.getAsJsonObject();
        String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : UUID.randomUUID().toString();
        JsonObject definition = groupDefinitions.get(uuid);
        String name = raw(groupProperty(obj, definition, "name"), "bone_" + rawBones.size());
        float[] origin = readVec3(groupProperty(obj, definition, "origin"), 0, 0, 0);
        float[] rotation = readVec3(groupProperty(obj, definition, "rotation"), 0, 0, 0);
        String parentType = raw(groupProperty(obj, definition, "parent_type"), "");
        JsonElement roleElement = groupProperty(obj, definition, "shyne_role");
        if (roleElement == null) roleElement = groupProperty(obj, definition, "role");
        String role = raw(roleElement, "");
        JsonElement tagsElement = groupProperty(obj, definition, "shyne_tags");
        if (tagsElement == null) tagsElement = groupProperty(obj, definition, "tags");
        List<String> tags = readStringList(tagsElement);
        String physicsPreset = raw(groupProperty(obj, definition, "shyne_physics"), "none");
        boolean visible = safeBoolean(groupProperty(obj, definition, "visibility"), true)
            && safeBoolean(groupProperty(obj, definition, "export"), true);
        RawBone bone = new RawBone(uuid, name, parentUuid, parentName, parentType, role, tags, physicsPreset, origin[0], origin[1], origin[2], rotation[0], rotation[1], rotation[2], visible);
        rawBones.put(uuid, bone);
        if (parentUuid != null && rawBones.containsKey(parentUuid)) {
            rawBones.get(parentUuid).childBoneUuids.add(uuid);
        }
        JsonElement children = groupProperty(obj, definition, "children");
        if (children != null && children.isJsonArray()) {
            for (JsonElement child : children.getAsJsonArray()) {
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    bone.childElementUuids.add(child.getAsString());
                } else {
                    collectRawBones(child, uuid, name, rawBones, groupDefinitions);
                }
            }
        }
    }

    static RawBone findBoneByName(String name, Map<String, RawBone> bones) {
        for (RawBone bone : bones.values()) {
            if (bone.name.equalsIgnoreCase(name)) return bone;
        }
        return null;
    }

    private static int parseFormatVersion(JsonObject root) {
        if (!root.has("meta") || !root.get("meta").isJsonObject()) return 0;
        JsonObject meta = root.getAsJsonObject("meta");
        if (!meta.has("format_version")) return 0;
        try { return (int) Math.round(Double.parseDouble(meta.get("format_version").getAsString()) * 100.0); }
        catch (RuntimeException ignored) { return 0; }
    }

    private static boolean usesV5AnimationCoordinates(JsonObject root) {
        if (!root.has("meta") || !root.get("meta").isJsonObject()) return false;
        JsonObject meta = root.getAsJsonObject("meta");
        if (!meta.has("format_version")) return false;
        try { return Double.parseDouble(meta.get("format_version").getAsString()) >= 5.0; }
        catch (RuntimeException ignored) { return false; }
    }

    static final class RawBone {
        final String uuid;
        final String name;
        final String parentUuid;
        final String parentName;
        final String parentType;
        final String role;
        final List<String> tags;
        final String physicsPreset;
        final float pivotX;
        final float pivotY;
        final float pivotZ;
        final float rotationX;
        final float rotationY;
        final float rotationZ;
        final boolean visible;
        final List<String> childBoneUuids = new ArrayList<>();
        final List<String> childElementUuids = new ArrayList<>();

        RawBone(String uuid, String name, String parentUuid, String parentName, String parentType, String role, List<String> tags, String physicsPreset, float pivotX, float pivotY, float pivotZ, float rotationX, float rotationY, float rotationZ, boolean visible) {
            this.uuid = uuid;
            this.name = name;
            this.parentUuid = parentUuid;
            this.parentName = parentName;
            this.parentType = parentType == null ? "" : parentType;
            this.role = role == null ? "" : role;
            this.tags = tags == null ? List.of() : List.copyOf(tags);
            this.physicsPreset = physicsPreset == null ? "none" : physicsPreset;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
            this.visible = visible;
        }
    }
}
