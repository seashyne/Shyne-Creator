package seashyne.shynecore.model;

import java.util.List;

public record BbBoneDefinition(
    String uuid,
    String name,
    String parentName,
    String parentUuid,
    String parentType,
    String role,
    List<String> tags,
    String physicsPreset,
    int cubeCount,
    float pivotX,
    float pivotY,
    float pivotZ,
    float rotationX,
    float rotationY,
    float rotationZ,
    boolean visible,
    List<String> childBoneUuids
) {
    /** Source-compatible constructor for models created before native physics metadata. */
    public BbBoneDefinition(
        String uuid, String name, String parentName, String parentUuid, String parentType,
        String role, List<String> tags, int cubeCount,
        float pivotX, float pivotY, float pivotZ,
        float rotationX, float rotationY, float rotationZ,
        boolean visible, List<String> childBoneUuids
    ) {
        this(uuid, name, parentName, parentUuid, parentType, role, tags, "none", cubeCount,
            pivotX, pivotY, pivotZ, rotationX, rotationY, rotationZ, visible, childBoneUuids);
    }

    public BbBoneDefinition(
        String uuid, String name, String parentName, String parentUuid, String parentType,
        String role, List<String> tags, int cubeCount,
        float pivotX, float pivotY, float pivotZ,
        float rotationX, float rotationY, float rotationZ,
        List<String> childBoneUuids
    ) {
        this(uuid, name, parentName, parentUuid, parentType, role, tags, "none", cubeCount,
            pivotX, pivotY, pivotZ, rotationX, rotationY, rotationZ, true, childBoneUuids);
    }

    public BbBoneDefinition {
        parentType = parentType == null ? "" : parentType;
        role = role == null ? "" : role;
        tags = tags == null ? List.of() : List.copyOf(tags);
        physicsPreset = normalizePhysicsPreset(physicsPreset);
        childBoneUuids = childBoneUuids == null ? List.of() : List.copyOf(childBoneUuids);
    }

    private static String normalizePhysicsPreset(String value) {
        String normalized = value == null ? "none" : value.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "bunny_ears", "tail", "hair", "cloth", "wings" -> normalized;
            default -> "none";
        };
    }
}
