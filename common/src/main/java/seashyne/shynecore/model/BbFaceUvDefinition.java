package seashyne.shynecore.model;

public record BbFaceUvDefinition(
    float u1,
    float v1,
    float u2,
    float v2,
    int rotation,
    int textureIndex,
    boolean enabled
) {
    public static final BbFaceUvDefinition DISABLED = new BbFaceUvDefinition(0, 0, 0, 0, 0, -1, false);
}
