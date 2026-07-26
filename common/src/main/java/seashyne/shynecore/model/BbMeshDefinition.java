package seashyne.shynecore.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Immutable Blockbench free-form mesh element. Vertices are local to {@code origin}. */
public record BbMeshDefinition(
    String uuid,
    String name,
    String parentBoneUuid,
    float originX,
    float originY,
    float originZ,
    float rotationX,
    float rotationY,
    float rotationZ,
    Map<String, BbMeshVertexDefinition> vertices,
    List<BbMeshFaceDefinition> faces,
    boolean visible
) {
    public BbMeshDefinition {
        uuid = uuid == null ? "" : uuid;
        name = name == null ? "mesh" : name;
        vertices = vertices == null
            ? Map.of()
            : Collections.unmodifiableMap(new LinkedHashMap<>(vertices));
        faces = faces == null ? List.of() : List.copyOf(faces);
    }

    public BbMeshVertexDefinition vertex(String id) {
        return vertices.get(id);
    }
}
