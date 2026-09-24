package seashyne.shynecore.network;

import seashyne.shynecore.avatar.AvatarAnimationClock;
import seashyne.shynecore.avatar.AvatarValueValidator;
import seashyne.shynecore.avatar.PngTextureValidator;
import seashyne.shynecore.model.*;
import seashyne.shynecore.network.ShyneNetwork.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Validates and sanitizes model geometry, texture bounds, bone hierarchies,
 * animation channels, and synchronized variables received over the network.
 */
public final class ShyneNetworkValidator {
    public static final int MAX_TEXTURE_BYTES = 8 * 1024 * 1024;
    public static final int MAX_AVATAR_TEXTURE_BYTES = 64 * 1024 * 1024;
    public static final int MAX_AVATAR_JSON_CHARS = 2 * 1024 * 1024;
    public static final int MAX_AVATAR_SUBSCRIPTION_COMMAND_BYTES = 16 * 1024;
    public static final int MAX_AVATAR_PARTS = 1024;
    public static final int MAX_SYNCED_VARS = 64;
    public static final int MAX_MODEL_CUBES = 16_384;
    public static final int MAX_MODEL_MESHES = 4_096;
    public static final int MAX_MODEL_MESH_VERTICES = 65_536;
    public static final int MAX_MODEL_MESH_FACES = 65_536;
    public static final int MAX_MODEL_BONES = 4_096;
    public static final int MAX_MODEL_ANIMATIONS = 512;
    public static final int MAX_MODEL_TEXTURES = 256;

    private ShyneNetworkValidator() {}

    public static byte[] readTextureBytes(BbModelDefinition model, BbTextureDefinition texture) {
        if (model == null || model.sourceFile() == null || texture == null || texture.relativePath() == null) return null;
        Path modelRoot = model.sourceFile().toAbsolutePath().normalize().getParent();
        if (modelRoot == null) return null;
        Path allowedRoot = modelRoot.getParent() == null ? modelRoot : modelRoot.getParent();
        Path candidate = modelRoot.resolve(texture.relativePath().replace('/', java.io.File.separatorChar)).normalize();
        Path fallback = modelRoot.resolve("textures").resolve(Path.of(texture.relativePath()).getFileName()).normalize();
        try {
            Path selected = Files.isRegularFile(candidate) && candidate.startsWith(allowedRoot) ? candidate : fallback;
            if (!selected.startsWith(allowedRoot) || !Files.isRegularFile(selected)) return null;
            long size = Files.size(selected);
            if (size <= 0 || size > MAX_TEXTURE_BYTES) return null;
            byte[] bytes = Files.readAllBytes(selected);
            return PngTextureValidator.matches(bytes, texture.width(), texture.height()) ? bytes : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isSafeAnimationParameters(Map<String, Double> parameters) {
        if (parameters == null) return true;
        if (parameters.size() > 64) return false;
        return parameters.entrySet().stream().allMatch(entry -> entry.getKey() != null
            && entry.getKey().matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}")
            && entry.getValue() != null && Double.isFinite(entry.getValue()) && Math.abs(entry.getValue()) <= 1_000_000.0);
    }

    public static boolean isSafeSyncedVars(Map<String, Object> values) {
        if (values == null) return true;
        if (values.size() > MAX_SYNCED_VARS) return false;
        return values.entrySet().stream().allMatch(entry -> isSafeId(entry.getKey()) && AvatarValueValidator.isSafe(entry.getValue()));
    }

    public static boolean isSafeVanillaVisibility(Map<String, Boolean> values) {
        if (values == null) return true;
        if (values.size() > 64) return false;
        return values.entrySet().stream().allMatch(entry -> isSafeId(entry.getKey()) && entry.getValue() != null);
    }

    public static boolean isSafeModel(NetModelDefinition model) {
        if (model.bones() == null || model.cubes() == null || model.meshes() == null || model.animations() == null || model.textures() == null) return false;
        if (model.bones().size() > MAX_MODEL_BONES || model.cubes().size() > MAX_MODEL_CUBES || model.meshes().size() > MAX_MODEL_MESHES || model.animations().size() > MAX_MODEL_ANIMATIONS || model.textures().size() > MAX_MODEL_TEXTURES) return false;
        if (model.textureWidth() <= 0 || model.textureWidth() > 8192 || model.textureHeight() <= 0 || model.textureHeight() > 8192) return false;
        if (!isBoundedText(model.sourceModId(), 128, false) || !isBoundedText(model.displayName(), 256, true)
            || !isBoundedText(model.primaryTextureRelativePath(), 1_024, true)) return false;
        for (NetBoneDefinition bone : model.bones()) if (!isSafeBone(bone)) return false;
        for (NetCubeDefinition cube : model.cubes()) if (!isSafeCube(cube, model.textures().size())) return false;
        long meshVertices = 0;
        long meshFaces = 0;
        for (NetMeshDefinition mesh : model.meshes()) {
            if (mesh == null || mesh.vertices() == null || mesh.faces() == null
                || !isBoundedText(mesh.uuid(), 512, false) || !isBoundedText(mesh.name(), 512, true)
                || !isBoundedText(mesh.parentBoneUuid(), 512, true)
                || !finiteBounded(mesh.originX()) || !finiteBounded(mesh.originY()) || !finiteBounded(mesh.originZ())
                || !finiteBounded(mesh.rotationX()) || !finiteBounded(mesh.rotationY()) || !finiteBounded(mesh.rotationZ())) return false;
            meshVertices += mesh.vertices().size();
            meshFaces += mesh.faces().size();
            if (meshVertices > MAX_MODEL_MESH_VERTICES || meshFaces > MAX_MODEL_MESH_FACES) return false;
            for (Map.Entry<String, NetMeshVertexDefinition> entry : mesh.vertices().entrySet()) {
                NetMeshVertexDefinition vertex = entry.getValue();
                if (!isBoundedText(entry.getKey(), 512, false) || vertex == null || !isBoundedText(vertex.id(), 512, false)
                    || !finiteBounded(vertex.x()) || !finiteBounded(vertex.y()) || !finiteBounded(vertex.z())) return false;
            }
            for (NetMeshFaceDefinition face : mesh.faces()) {
                if (face == null || !isBoundedText(face.id(), 512, false) || face.vertexIds() == null || face.uvByVertex() == null
                    || face.vertexIds().size() > 256 || face.uvByVertex().size() > 256 || face.textureIndex() < -1 || face.textureIndex() >= model.textures().size()) return false;
                if (face.vertexIds().stream().anyMatch(id -> id == null || !mesh.vertices().containsKey(id))) return false;
                if (face.uvByVertex().entrySet().stream().anyMatch(entry -> entry.getKey() == null || !mesh.vertices().containsKey(entry.getKey())
                    || entry.getValue() == null || !finiteBounded(entry.getValue().u()) || !finiteBounded(entry.getValue().v()))) return false;
            }
        }
        long keyframeCount = 0;
        for (NetAnimationDefinition animation : model.animations()) {
            if (animation == null || !isBoundedText(animation.name(), 128, false) || !Double.isFinite(animation.lengthSeconds())
                || animation.lengthSeconds() < 0 || animation.lengthSeconds() > 86_400 || animation.animatorCount() < 0
                || animation.animatorCount() > MAX_MODEL_BONES || animation.boneAnimations() == null
                || animation.boneAnimations().size() > MAX_MODEL_BONES || animation.affectedBones() == null
                || animation.affectedBones().size() > MAX_MODEL_BONES
                || animation.affectedBones().stream().anyMatch(value -> !isBoundedText(value, 512, false))) return false;
            for (Map.Entry<String, NetBoneAnimation> entry : animation.boneAnimations().entrySet()) {
                NetBoneAnimation bone = entry.getValue();
                if (!isBoundedText(entry.getKey(), 512, false) || bone == null || !isBoundedText(bone.boneUuid(), 512, false)) return false;
                for (List<NetKeyframe> channel : List.of(bone.rotation(), bone.position(), bone.scale())) {
                    if (channel == null) return false;
                    keyframeCount += channel.size();
                    if (keyframeCount > 20_000) return false;
                    if (channel.stream().anyMatch(key -> !isSafeKeyframe(key))) return false;
                }
            }
        }
        long totalTextureBytes = 0;
        long totalTexturePixels = 0;
        for (NetTextureDefinition texture : model.textures()) {
            if (texture == null || !isBoundedText(texture.id(), 256, false) || !isBoundedText(texture.name(), 256, true)
                || !isBoundedText(texture.relativePath(), 1_024, true) || texture.width() <= 0 || texture.height() <= 0
                || texture.width() > PngTextureValidator.MAX_DIMENSION || texture.height() > PngTextureValidator.MAX_DIMENSION
                || texture.contentHash() == null || texture.contentBase64() == null || texture.contentBase64().length() > (MAX_TEXTURE_BYTES * 4 / 3) + 8) return false;
            if (!texture.contentHash().matches("[0-9a-fA-F]{64}") || texture.contentBase64().isBlank()) return false;
            try {
                byte[] bytes = Base64.getDecoder().decode(texture.contentBase64());
                PngTextureValidator.Dimensions dimensions = PngTextureValidator.dimensions(bytes);
                if (bytes.length > MAX_TEXTURE_BYTES || dimensions == null || dimensions.width() != texture.width() || dimensions.height() != texture.height()) return false;
                totalTextureBytes += bytes.length;
                totalTexturePixels += dimensions.pixels();
                if (totalTextureBytes > MAX_AVATAR_TEXTURE_BYTES) return false;
                if (totalTexturePixels > PngTextureValidator.MAX_AVATAR_PIXELS) return false;
                if (!texture.contentHash().isBlank() && !texture.contentHash().equalsIgnoreCase(HexFormat.of().formatHex(sha256(bytes)))) return false;
            } catch (IllegalArgumentException error) {
                return false;
            }
        }
        return true;
    }

    public static boolean isSafeKeyframe(NetKeyframe keyframe) {
        if (keyframe == null || !Float.isFinite(keyframe.time()) || keyframe.time() < -1 || keyframe.time() > 86_400) return false;
        return isSafePoint(keyframe.pre()) && isSafePoint(keyframe.post()) && keyframe.easing() != null && keyframe.easing().length() <= 32
            && isSafeBezier(keyframe.bezier());
    }

    public static boolean isSafeBone(NetBoneDefinition bone) {
        return bone != null && isBoundedText(bone.uuid(), 512, false) && isBoundedText(bone.name(), 512, true)
            && isBoundedText(bone.parentName(), 512, true) && isBoundedText(bone.parentUuid(), 512, true)
            && isBoundedText(bone.parentType(), 64, true) && isBoundedText(bone.role(), 64, true)
            && isBoundedText(bone.physicsPreset(), 64, true) && bone.cubeCount() >= 0 && bone.cubeCount() <= MAX_MODEL_CUBES
            && finiteBounded(bone.pivotX()) && finiteBounded(bone.pivotY()) && finiteBounded(bone.pivotZ())
            && finiteBounded(bone.rotationX()) && finiteBounded(bone.rotationY()) && finiteBounded(bone.rotationZ())
            && bone.tags() != null && bone.tags().size() <= 64 && bone.tags().stream().allMatch(value -> isBoundedText(value, 128, false))
            && bone.childBoneUuids() != null && bone.childBoneUuids().size() <= MAX_MODEL_BONES
            && bone.childBoneUuids().stream().allMatch(value -> isBoundedText(value, 512, false));
    }

    public static boolean isSafeCube(NetCubeDefinition cube, int textureCount) {
        if (cube == null || !isBoundedText(cube.name(), 512, true) || !isBoundedText(cube.parentBoneUuid(), 512, true)
            || cube.faces() == null || cube.faces().size() > 6 || cube.textureIndex() < -1 || cube.textureIndex() >= textureCount) return false;
        if (!finiteBounded(cube.fromX()) || !finiteBounded(cube.fromY()) || !finiteBounded(cube.fromZ())
            || !finiteBounded(cube.toX()) || !finiteBounded(cube.toY()) || !finiteBounded(cube.toZ())
            || !finiteBounded(cube.originX()) || !finiteBounded(cube.originY()) || !finiteBounded(cube.originZ())
            || !finiteBounded(cube.rotationX()) || !finiteBounded(cube.rotationY()) || !finiteBounded(cube.rotationZ())
            || !finiteBounded(cube.inflate())) return false;
        return cube.faces().entrySet().stream().allMatch(entry -> isBoundedText(entry.getKey(), 16, false)
            && entry.getValue() != null && isSafeFace(entry.getValue(), textureCount));
    }

    public static boolean isSafeFace(NetFaceUvDefinition face, int textureCount) {
        return finiteBounded(face.u1()) && finiteBounded(face.v1()) && finiteBounded(face.u2()) && finiteBounded(face.v2())
            && face.rotation() >= 0 && face.rotation() <= 270 && face.rotation() % 90 == 0
            && face.textureIndex() >= -1 && face.textureIndex() < textureCount;
    }

    public static boolean isSafeBezier(BbBezierData bezier) {
        if (bezier == null) return false;
        for (int axis = 0; axis < 3; axis++) {
            if (!finiteBounded(bezier.leftTime(axis)) || !finiteBounded(bezier.leftValue(axis))
                || !finiteBounded(bezier.rightTime(axis)) || !finiteBounded(bezier.rightValue(axis))) return false;
        }
        return true;
    }

    public static boolean isBoundedText(String value, int maxLength, boolean nullable) {
        if (value == null) return nullable;
        if (value.length() > maxLength || (!nullable && value.isBlank())) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }

    public static boolean isSafePoint(BbKeyframePoint point) {
        return point != null && isSafeExpression(point.x()) && isSafeExpression(point.y()) && isSafeExpression(point.z());
    }

    public static boolean isSafeExpression(String expression) {
        return expression != null && !expression.isBlank() && expression.length() <= 2_048;
    }

    public static boolean isSafeId(String value) {
        return value != null && value.length() >= 1 && value.length() <= 64 && value.matches("[A-Za-z0-9_.:-]+");
    }

    public static boolean isSafePart(NetAvatarPart part) {
        if (part == null || !isSafePartPath(part.path())) return false;
        return finiteBounded(part.posX()) && finiteBounded(part.posY()) && finiteBounded(part.posZ())
            && finiteBounded(part.rotX()) && finiteBounded(part.rotY()) && finiteBounded(part.rotZ())
            && finiteBounded(part.scaleX()) && finiteBounded(part.scaleY()) && finiteBounded(part.scaleZ())
            && finiteBounded(part.additiveRotX()) && finiteBounded(part.additiveRotY()) && finiteBounded(part.additiveRotZ());
    }

    public static boolean isSafePartPath(String value) {
        if (value == null || value.isBlank() || value.length() > 512) return false;
        for (int i = 0; i < value.length(); i++) if (Character.isISOControl(value.charAt(i))) return false;
        return true;
    }

    public static boolean isSafeAnimationLayer(NetAvatarAnimation layer) {
        return layer != null && layer.name() != null && layer.name().length() <= 128
            && AvatarAnimationClock.isSafeAge(layer.startedAtMillis())
            && AvatarAnimationClock.isSafeOptionalAge(layer.stoppingAtMillis())
            && Double.isFinite(layer.lengthSeconds()) && layer.lengthSeconds() >= 0 && layer.lengthSeconds() <= 86_400
            && Double.isFinite(layer.speed()) && layer.speed() >= 0.01 && layer.speed() <= 8
            && Double.isFinite(layer.weight()) && layer.weight() >= 0 && layer.weight() <= 1
            && layer.priority() >= -1000 && layer.priority() <= 1000
            && layer.fadeInTicks() >= 0 && layer.fadeInTicks() <= 1200
            && layer.fadeOutTicks() >= 0 && layer.fadeOutTicks() <= 1200
            && (layer.mask() == null || (layer.mask().size() <= 256 && layer.mask().stream().allMatch(value -> value != null && value.length() <= 256)));
    }

    public static boolean finiteBounded(float value) {
        return Float.isFinite(value) && Math.abs(value) <= 10_000f;
    }

    public static byte[] sha256(byte[] bytes) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
