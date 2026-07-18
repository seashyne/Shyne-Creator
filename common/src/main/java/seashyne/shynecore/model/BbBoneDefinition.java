package seashyne.shynecore.model;

import java.util.List;

public record BbBoneDefinition(
    String uuid,
    String name,
    String parentName,
    String parentUuid,
    int cubeCount,
    float pivotX,
    float pivotY,
    float pivotZ,
    float rotationX,
    float rotationY,
    float rotationZ,
    List<String> childBoneUuids
) {}
