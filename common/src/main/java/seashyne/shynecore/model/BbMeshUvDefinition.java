package seashyne.shynecore.model;

/** Per-face UV coordinate for one mesh vertex, in Blockbench texture pixels. */
public record BbMeshUvDefinition(float u, float v) {
    public static final BbMeshUvDefinition ZERO = new BbMeshUvDefinition(0f, 0f);
}
