package seashyne.shynecore.model;

import com.google.gson.*;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Converts Blockbench {@code .bbmodel} JSON into Shyne's immutable runtime model.
 *
 * <p>File resolution stays here instead of in the renderer, giving validation,
 * networking and both loaders one canonical view of model content.</p>
 */
public final class BbModelParser {
    private static final Gson GSON = new Gson();
    private static final List<String> FACE_KEYS = List.of("north", "south", "east", "west", "up", "down");

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

            List<BbTextureDefinition> textures = parseTextures(root, path, textureWidth, textureHeight);
            String primaryTexture = textures.isEmpty() ? null : textures.get(0).relativePath();

            Map<String, RawBone> rawBones = new LinkedHashMap<>();
            if (root.has("outliner") && root.get("outliner").isJsonArray()) {
                for (JsonElement entry : root.getAsJsonArray("outliner")) {
                    collectRawBones(entry, null, null, rawBones);
                }
            }

            Map<String, String> cubeParents = new HashMap<>();
            for (RawBone bone : rawBones.values()) {
                for (String cubeUuid : bone.childCubeUuids) cubeParents.put(cubeUuid, bone.uuid);
            }
            List<BbCubeDefinition> cubes = parseCubes(root, cubeParents);
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
                    cubeCountByParentUuid.getOrDefault(raw.uuid, 0),
                    raw.pivotX,
                    raw.pivotY,
                    raw.pivotZ,
                    raw.rotationX,
                    raw.rotationY,
                    raw.rotationZ,
                    List.copyOf(raw.childBoneUuids)
                ));
            }

            List<BbAnimationDefinition> animations = parseAnimations(root, rawBones);
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
                List.copyOf(animations)
            );
        }
    }

    private static List<BbTextureDefinition> parseTextures(JsonObject root, Path modelPath, int fallbackWidth, int fallbackHeight) throws IOException {
        List<BbTextureDefinition> textures = new ArrayList<>();
        if (!root.has("textures") || !root.get("textures").isJsonArray()) {
            List<Path> discovered = discoverPngFiles(modelPath);
            for (int i = 0; i < discovered.size(); i++) {
                Path png = discovered.get(i);
                textures.add(new BbTextureDefinition(String.valueOf(i), png.getFileName().toString(), relativeToModel(modelPath, png), fallbackWidth, fallbackHeight));
            }
            if (textures.isEmpty()) {
                String pngName = stripExtension(modelPath.getFileName().toString()) + ".png";
                textures.add(new BbTextureDefinition("0", pngName, pngName, fallbackWidth, fallbackHeight));
            }
            return textures;
        }
        JsonArray array = root.getAsJsonArray("textures");
        for (int i = 0; i < array.size(); i++) {
            JsonElement entry = array.get(i);
            if (!entry.isJsonObject()) continue;
            JsonObject obj = entry.getAsJsonObject();
            String id = obj.has("id") ? obj.get("id").getAsString() : String.valueOf(i);
            String name = obj.has("name") ? obj.get("name").getAsString() : (stripExtension(modelPath.getFileName().toString()) + "_" + i);
            String declaredPath = obj.has("relative_path") ? obj.get("relative_path").getAsString() : name;
            String relativePath = resolveTextureReference(modelPath, declaredPath, name);
            int width = obj.has("uv_width") ? safeInt(obj.get("uv_width"), fallbackWidth)
                : obj.has("width") ? safeInt(obj.get("width"), fallbackWidth) : fallbackWidth;
            int height = obj.has("uv_height") ? safeInt(obj.get("uv_height"), fallbackHeight)
                : obj.has("height") ? safeInt(obj.get("height"), fallbackHeight) : fallbackHeight;
            textures.add(new BbTextureDefinition(id, name, relativePath, width, height));
        }
        if (textures.isEmpty()) {
            List<Path> discovered = discoverPngFiles(modelPath);
            for (int i = 0; i < discovered.size(); i++) {
                Path png = discovered.get(i);
                textures.add(new BbTextureDefinition(String.valueOf(i), png.getFileName().toString(), relativeToModel(modelPath, png), fallbackWidth, fallbackHeight));
            }
        }
        return textures;
    }

    private static String resolveTextureReference(Path modelPath, String declaredPath, String name) throws IOException {
        Path root = modelPath.toAbsolutePath().normalize().getParent();
        if (root == null) return declaredPath;
        if (declaredPath != null && !declaredPath.isBlank()) {
            try {
                Path direct = root.resolve(declaredPath.replace('/', java.io.File.separatorChar)).normalize();
                if (direct.startsWith(root) && Files.isRegularFile(direct)) return relativeToModel(modelPath, direct);
            } catch (RuntimeException ignored) {
                // Figura files often retain an absolute editor path from another computer.
            }
        }
        String wanted = Path.of(name == null || name.isBlank() ? declaredPath : name).getFileName().toString();
        List<Path> matches;
        try (var stream = Files.walk(root)) {
            matches = stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().equalsIgnoreCase(wanted))
                .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
        if (matches.size() == 1) return relativeToModel(modelPath, matches.get(0));
        if (matches.size() > 1) throw new IOException("Ambiguous texture '" + wanted + "': found " + matches.size() + " files inside avatar");
        return declaredPath == null || declaredPath.isBlank() ? wanted : declaredPath;
    }

    private static List<Path> discoverPngFiles(Path modelPath) throws IOException {
        Path root = modelPath.toAbsolutePath().normalize().getParent();
        if (root == null) return List.of();
        try (var stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile)
                .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".png"))
                .sorted(Comparator.comparing(Path::toString, String.CASE_INSENSITIVE_ORDER))
                .toList();
        }
    }

    private static String relativeToModel(Path modelPath, Path texturePath) {
        Path root = modelPath.toAbsolutePath().normalize().getParent();
        return root.relativize(texturePath.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static List<BbCubeDefinition> parseCubes(JsonObject root, Map<String, String> cubeParents) {
        List<BbCubeDefinition> cubes = new ArrayList<>();
        if (!root.has("elements") || !root.get("elements").isJsonArray()) return cubes;
        for (JsonElement entry : root.getAsJsonArray("elements")) {
            if (!entry.isJsonObject()) continue;
            JsonObject obj = entry.getAsJsonObject();
            if (obj.has("type") && !"cube".equalsIgnoreCase(obj.get("type").getAsString())) continue;
            float[] from = readVec3(obj.get("from"), 0, 0, 0);
            float[] to = readVec3(obj.get("to"), 1, 1, 1);
            float[] origin = readVec3(obj.get("origin"), 0, 0, 0);
            float[] rotation = readVec3(obj.get("rotation"), 0, 0, 0);
            float inflate = obj.has("inflate") ? safeFloat(obj.get("inflate"), 0f) : 0f;
            int textureIndex = obj.has("texture") ? safeInt(obj.get("texture"), 0) : 0;
            boolean mirror = obj.has("mirror_uv") && obj.get("mirror_uv").getAsBoolean();
            String cubeUuid = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
            String parentUuid = obj.has("parent") ? obj.get("parent").getAsString() : cubeParents.get(cubeUuid);
            cubes.add(new BbCubeDefinition(
                obj.has("name") ? obj.get("name").getAsString() : "cube_" + cubes.size(),
                parentUuid,
                from[0], from[1], from[2],
                to[0], to[1], to[2],
                origin[0], origin[1], origin[2],
                rotation[0], rotation[1], rotation[2],
                inflate,
                parseFaces(obj.get("faces")),
                textureIndex,
                mirror
            ));
        }
        return cubes;
    }

    private static Map<String, BbFaceUvDefinition> parseFaces(JsonElement faceElement) {
        Map<String, BbFaceUvDefinition> faces = new LinkedHashMap<>();
        for (String key : FACE_KEYS) faces.put(key, BbFaceUvDefinition.DISABLED);
        if (faceElement == null || !faceElement.isJsonObject()) return faces;
        JsonObject obj = faceElement.getAsJsonObject();
        for (String key : FACE_KEYS) {
            if (!obj.has(key) || !obj.get(key).isJsonObject()) continue;
            JsonObject face = obj.getAsJsonObject(key);
            float[] uv = readVec4(face.get("uv"), 0, 0, 0, 0);
            int rotation = face.has("rotation") ? safeInt(face.get("rotation"), 0) : 0;
            int textureIndex = face.has("texture") ? safeInt(face.get("texture"), -1) : -1;
            faces.put(key, new BbFaceUvDefinition(uv[0], uv[1], uv[2], uv[3], rotation, textureIndex, true));
        }
        return faces;
    }

    private static List<BbAnimationDefinition> parseAnimations(JsonObject root, Map<String, RawBone> bones) {
        List<BbAnimationDefinition> animations = new ArrayList<>();
        if (!root.has("animations") || !root.get("animations").isJsonArray()) return animations;
        for (JsonElement entry : root.getAsJsonArray("animations")) {
            if (!entry.isJsonObject()) continue;
            JsonObject anim = entry.getAsJsonObject();
            String name = anim.has("name") ? anim.get("name").getAsString() : "animation_" + animations.size();
            double length = anim.has("length") ? safeDouble(anim.get("length"), 0.0) : 0.0;
            boolean looping = anim.has("loop") && !anim.get("loop").isJsonNull() && !"once".equalsIgnoreCase(anim.get("loop").getAsString());
            Map<String, BbBoneAnimation> boneAnimations = new LinkedHashMap<>();
            if (anim.has("animators") && anim.get("animators").isJsonObject()) {
                JsonObject animators = anim.getAsJsonObject("animators");
                for (Map.Entry<String, JsonElement> animatorEntry : animators.entrySet()) {
                    if (!animatorEntry.getValue().isJsonObject()) continue;
                    JsonObject animator = animatorEntry.getValue().getAsJsonObject();
                    String boneUuid = animatorEntry.getKey();
                    RawBone rawBone = bones.get(boneUuid);
                    if (rawBone == null && animator.has("name")) {
                        rawBone = findBoneByName(animator.get("name").getAsString(), bones);
                        if (rawBone != null) boneUuid = rawBone.uuid;
                    }
                    boneAnimations.put(boneUuid, new BbBoneAnimation(
                        boneUuid,
                        parseKeyframes(animator.get("rotation")),
                        parseKeyframes(animator.get("position")),
                        parseKeyframes(animator.get("scale"))
                    ));
                }
            }
            animations.add(new BbAnimationDefinition(name, length, looping, boneAnimations.size(), Map.copyOf(boneAnimations), List.copyOf(boneAnimations.keySet())));
        }
        return animations;
    }

    private static List<BbKeyframe> parseKeyframes(JsonElement channelElement) {
        List<BbKeyframe> frames = new ArrayList<>();
        if (channelElement == null || channelElement.isJsonNull()) return frames;
        if (channelElement.isJsonObject()) {
            for (Map.Entry<String, JsonElement> entry : channelElement.getAsJsonObject().entrySet()) {
                float time = safeFloat(entry.getKey(), 0f);
                if (!entry.getValue().isJsonObject()) continue;
                JsonObject frame = entry.getValue().getAsJsonObject();
                float[] vec = readKeyframeVector(frame);
                String easing = frame.has("easing") ? frame.get("easing").getAsString() : "linear";
                frames.add(new BbKeyframe(time, vec[0], vec[1], vec[2], easing));
            }
        } else if (channelElement.isJsonArray()) {
            for (JsonElement item : channelElement.getAsJsonArray()) {
                if (!item.isJsonObject()) continue;
                JsonObject frame = item.getAsJsonObject();
                float time = frame.has("time") ? safeFloat(frame.get("time"), 0f) : 0f;
                float[] vec = readKeyframeVector(frame);
                String easing = frame.has("easing") ? frame.get("easing").getAsString() : "linear";
                frames.add(new BbKeyframe(time, vec[0], vec[1], vec[2], easing));
            }
        }
        frames.sort(Comparator.comparing(BbKeyframe::time));
        return frames;
    }

    private static float[] readKeyframeVector(JsonObject frame) {
        if (frame.has("vector")) return readVec3(frame.get("vector"), 0, 0, 0);
        if (frame.has("data_points") && frame.get("data_points").isJsonArray()) {
            JsonArray points = frame.getAsJsonArray("data_points");
            if (!points.isEmpty() && points.get(0).isJsonObject()) {
                JsonObject point = points.get(0).getAsJsonObject();
                return new float[] {
                    safeFloat(point.get("x"), 0),
                    safeFloat(point.get("y"), 0),
                    safeFloat(point.get("z"), 0)
                };
            }
        }
        return new float[] { 0, 0, 0 };
    }

    private static void collectRawBones(JsonElement entry, String parentUuid, String parentName, Map<String, RawBone> rawBones) {
        if (!entry.isJsonObject()) return;
        JsonObject obj = entry.getAsJsonObject();
        String uuid = obj.has("uuid") ? obj.get("uuid").getAsString() : UUID.randomUUID().toString();
        String name = obj.has("name") ? obj.get("name").getAsString() : "bone_" + rawBones.size();
        float[] origin = readVec3(obj.get("origin"), 0, 0, 0);
        float[] rotation = readVec3(obj.get("rotation"), 0, 0, 0);
        RawBone bone = new RawBone(uuid, name, parentUuid, parentName, origin[0], origin[1], origin[2], rotation[0], rotation[1], rotation[2]);
        rawBones.put(uuid, bone);
        if (parentUuid != null && rawBones.containsKey(parentUuid)) {
            rawBones.get(parentUuid).childBoneUuids.add(uuid);
        }
        if (obj.has("children") && obj.get("children").isJsonArray()) {
            for (JsonElement child : obj.getAsJsonArray("children")) {
                if (child.isJsonPrimitive() && child.getAsJsonPrimitive().isString()) {
                    bone.childCubeUuids.add(child.getAsString());
                } else {
                    collectRawBones(child, uuid, name, rawBones);
                }
            }
        }
    }

    private static RawBone findBoneByName(String name, Map<String, RawBone> bones) {
        for (RawBone bone : bones.values()) {
            if (bone.name.equalsIgnoreCase(name)) return bone;
        }
        return null;
    }

    private static int parseFormatVersion(JsonObject root) {
        if (!root.has("meta") || !root.get("meta").isJsonObject()) return 0;
        JsonObject meta = root.getAsJsonObject("meta");
        return meta.has("format_version") ? safeInt(meta.get("format_version"), 0) : 0;
    }

    private static float[] readVec3(JsonElement el, float dx, float dy, float dz) {
        float[] out = new float[] { dx, dy, dz };
        if (el == null || el.isJsonNull()) return out;
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (arr.size() > 0) out[0] = safeFloat(arr.get(0), dx);
            if (arr.size() > 1) out[1] = safeFloat(arr.get(1), dy);
            if (arr.size() > 2) out[2] = safeFloat(arr.get(2), dz);
        }
        return out;
    }

    private static float[] readVec4(JsonElement el, float a, float b, float c, float d) {
        float[] out = new float[] { a, b, c, d };
        if (el == null || el.isJsonNull()) return out;
        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            for (int i = 0; i < Math.min(4, arr.size()); i++) out[i] = safeFloat(arr.get(i), out[i]);
        }
        return out;
    }

    private static String stripExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        return idx == -1 ? fileName : fileName.substring(0, idx);
    }

    private static int safeInt(JsonElement el, int def) {
        try { return el == null || el.isJsonNull() ? def : el.getAsInt(); }
        catch (Exception ignored) { return def; }
    }

    private static int safeInt(String raw, int def) {
        try { return Integer.parseInt(raw); }
        catch (Exception ignored) { return def; }
    }

    private static float safeFloat(JsonElement el, float def) {
        try { return el == null || el.isJsonNull() ? def : el.getAsFloat(); }
        catch (Exception ignored) { return def; }
    }

    private static float safeFloat(String raw, float def) {
        try { return Float.parseFloat(raw); }
        catch (Exception ignored) { return def; }
    }

    private static double safeDouble(JsonElement el, double def) {
        try { return el == null || el.isJsonNull() ? def : el.getAsDouble(); }
        catch (Exception ignored) { return def; }
    }

    private static final class RawBone {
        final String uuid;
        final String name;
        final String parentUuid;
        final String parentName;
        final float pivotX;
        final float pivotY;
        final float pivotZ;
        final float rotationX;
        final float rotationY;
        final float rotationZ;
        final List<String> childBoneUuids = new ArrayList<>();
        final List<String> childCubeUuids = new ArrayList<>();

        RawBone(String uuid, String name, String parentUuid, String parentName, float pivotX, float pivotY, float pivotZ, float rotationX, float rotationY, float rotationZ) {
            this.uuid = uuid;
            this.name = name;
            this.parentUuid = parentUuid;
            this.parentName = parentName;
            this.pivotX = pivotX;
            this.pivotY = pivotY;
            this.pivotZ = pivotZ;
            this.rotationX = rotationX;
            this.rotationY = rotationY;
            this.rotationZ = rotationZ;
        }
    }
}
