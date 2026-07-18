package seashyne.shynecore.model;

import java.util.Map;

public record BbCubeDefinition(
    String name,
    String parentBoneUuid,
    float fromX,
    float fromY,
    float fromZ,
    float toX,
    float toY,
    float toZ,
    float originX,
    float originY,
    float originZ,
    float rotationX,
    float rotationY,
    float rotationZ,
    float inflate,
    Map<String, BbFaceUvDefinition> faces,
    int textureIndex,
    boolean mirror
) {}
