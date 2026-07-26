package seashyne.shynecore.model;

/** One vertex in a Blockbench free-form mesh, in mesh-local model units. */
public record BbMeshVertexDefinition(
    String id,
    float x,
    float y,
    float z
) {}
