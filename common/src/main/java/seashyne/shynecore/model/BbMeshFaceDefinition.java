package seashyne.shynecore.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** A textured polygon in a Blockbench free-form mesh. */
public record BbMeshFaceDefinition(
    String id,
    List<String> vertexIds,
    Map<String, BbMeshUvDefinition> uvByVertex,
    int textureIndex,
    boolean enabled
) {
    public BbMeshFaceDefinition {
        id = id == null ? "" : id;
        vertexIds = vertexIds == null ? List.of() : List.copyOf(vertexIds);
        uvByVertex = uvByVertex == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(uvByVertex));
    }

    public BbMeshUvDefinition uv(String vertexId) {
        return uvByVertex.getOrDefault(vertexId, BbMeshUvDefinition.ZERO);
    }
}
