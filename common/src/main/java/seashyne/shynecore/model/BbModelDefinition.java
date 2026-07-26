package seashyne.shynecore.model;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public record BbModelDefinition(
    String modelId,
    String sourceModId,
    String displayName,
    Path sourceFile,
    int formatVersion,
    int textureWidth,
    int textureHeight,
    String primaryTextureRelativePath,
    List<BbTextureDefinition> textures,
    List<BbBoneDefinition> bones,
    List<BbCubeDefinition> cubes,
    List<BbMeshDefinition> meshes,
    List<BbAnimationDefinition> animations
) {
    /** Source-compatible constructor for models created before mesh support. */
    public BbModelDefinition(
        String modelId, String sourceModId, String displayName, Path sourceFile,
        int formatVersion, int textureWidth, int textureHeight, String primaryTextureRelativePath,
        List<BbTextureDefinition> textures, List<BbBoneDefinition> bones,
        List<BbCubeDefinition> cubes, List<BbAnimationDefinition> animations
    ) {
        this(modelId, sourceModId, displayName, sourceFile, formatVersion, textureWidth, textureHeight,
            primaryTextureRelativePath, textures, bones, cubes, List.of(), animations);
    }

    public BbModelDefinition {
        meshes = meshes == null ? List.of() : List.copyOf(meshes);
    }

    public BbModelDefinition withModelId(String value) {
        return new BbModelDefinition(
            value, sourceModId, displayName, sourceFile, formatVersion, textureWidth, textureHeight,
            primaryTextureRelativePath, textures, bones, cubes, meshes, animations
        );
    }

    public boolean hasGeometry() {
        return (cubes != null && !cubes.isEmpty()) || !meshes.isEmpty();
    }

    public boolean hasAnimation(String animationName) {
        return animations.stream().anyMatch(a -> a.name().equalsIgnoreCase(animationName));
    }

    public BbAnimationDefinition findAnimation(String animationName) {
        return animations.stream()
            .filter(a -> a.name().equalsIgnoreCase(animationName))
            .findFirst()
            .orElse(null);
    }

    public BbBoneDefinition findBoneByUuid(String uuid) {
        return bones.stream().filter(b -> uuid != null && uuid.equals(b.uuid())).findFirst().orElse(null);
    }

    public BbTextureDefinition primaryTexture() {
        if (textures == null || textures.isEmpty()) return null;
        if (primaryTextureRelativePath != null) {
            for (BbTextureDefinition texture : textures) {
                if (primaryTextureRelativePath.equals(texture.relativePath())) return texture;
            }
        }
        return textures.get(0);
    }

    public BbTextureDefinition texture(int index) {
        if (textures == null || textures.isEmpty()) return null;
        return textures.get(index >= 0 && index < textures.size() ? index : 0);
    }

    public String bonePath(String boneUuid) {
        BbBoneDefinition bone = findBoneByUuid(boneUuid);
        if (bone == null) return "model";
        List<String> names = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        while (bone != null && visited.add(bone.uuid())) {
            names.add(0, bone.name());
            bone = findBoneByUuid(bone.parentUuid());
        }
        return "model." + String.join(".", names);
    }

    public String cubePath(BbCubeDefinition cube) {
        if (cube == null) return "model";
        return (cube.parentBoneUuid() == null ? "model" : bonePath(cube.parentBoneUuid())) + "." + cube.name();
    }

    public String meshPath(BbMeshDefinition mesh) {
        if (mesh == null) return "model";
        return (mesh.parentBoneUuid() == null ? "model" : bonePath(mesh.parentBoneUuid())) + "." + mesh.name();
    }
}
