package seashyne.shynecore.network;

import org.junit.jupiter.api.Test;
import seashyne.shynecore.model.BbMeshDefinition;
import seashyne.shynecore.model.BbMeshFaceDefinition;
import seashyne.shynecore.model.BbMeshUvDefinition;
import seashyne.shynecore.model.BbMeshVertexDefinition;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ShyneMeshNetworkTest {
    @Test
    void roundTripsMeshGeometryAndKeepsLegacyModelConstructor() {
        Map<String, BbMeshVertexDefinition> vertices = new LinkedHashMap<>();
        vertices.put("a", new BbMeshVertexDefinition("a", 0, 1, 2));
        vertices.put("b", new BbMeshVertexDefinition("b", 3, 4, 5));
        vertices.put("c", new BbMeshVertexDefinition("c", 6, 7, 8));
        BbMeshDefinition source = new BbMeshDefinition(
            "mesh", "Ear", "head", 1, 2, 3, 10, 20, 30, vertices,
            List.of(new BbMeshFaceDefinition("face", List.of("a", "b", "c"), Map.of(
                "a", new BbMeshUvDefinition(1, 2),
                "b", new BbMeshUvDefinition(3, 4),
                "c", new BbMeshUvDefinition(5, 6)
            ), 2, true)), false
        );

        assertEquals(source, ShyneNetwork.NetMeshDefinition.from(source).toRuntime());

        ShyneNetwork.NetModelDefinition legacy = new ShyneNetwork.NetModelDefinition(
            "test:model", "test", "Test", 1, 16, 16, "", List.of(), List.of(), List.of(), List.of()
        );
        assertTrue(legacy.toRuntime().meshes().isEmpty());
    }
}
