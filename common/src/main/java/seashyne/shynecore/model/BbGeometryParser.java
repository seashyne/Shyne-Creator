package seashyne.shynecore.model;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.*;

import static seashyne.shynecore.model.BbModelJsonHelper.*;

/**
 * Parses cube and mesh geometry from Blockbench {@code .bbmodel} JSON trees.
 */
final class BbGeometryParser {
    static final List<String> FACE_KEYS = List.of("north", "south", "east", "west", "up", "down");

    private BbGeometryParser() {}

    static List<BbCubeDefinition> parseCubes(JsonObject root, Map<String, String> cubeParents) {
        List<BbCubeDefinition> cubes = new ArrayList<>();
        if (!root.has("elements") || !root.get("elements").isJsonArray()) return cubes;
        for (JsonElement entry : root.getAsJsonArray("elements")) {
            if (!entry.isJsonObject()) continue;
            JsonObject obj = entry.getAsJsonObject();
            if (obj.has("type") && !"cube".equalsIgnoreCase(obj.get("type").getAsString())) continue;
            float[] from = readVec3(obj.get("from"), 0, 0, 0);
            float[] to = readVec3(obj.get("to"), 1, 1, 1);
            float[] origin = readVec3(obj.get("origin"), 0, 0, 0);
            float[] rotation = readVec3(obj.get("rotation"), 0, 0, 0);
            float inflate = obj.has("inflate") ? safeFloat(obj.get("inflate"), 0f) : 0f;
            int textureIndex = obj.has("texture") ? safeInt(obj.get("texture"), 0) : 0;
            boolean mirror = obj.has("mirror_uv") && obj.get("mirror_uv").getAsBoolean();
            boolean visible = safeBoolean(obj.get("visibility"), true) && safeBoolean(obj.get("export"), true);
            String cubeUuid = obj.has("uuid") ? obj.get("uuid").getAsString() : null;
            String parentUuid = obj.has("parent") ? obj.get("parent").getAsString() : cubeParents.get(cubeUuid);
            cubes.add(new BbCubeDefinition(
                obj.has("name") ? obj.get("name").getAsString() : "cube_" + cubes.size(),
                parentUuid,
                from[0], from[1], from[2],
                to[0], to[1], to[2],
                origin[0], origin[1], origin[2],
                rotation[0], rotation[1], rotation[2],
                inflate,
                parseFaces(obj.get("faces")),
                textureIndex,
                mirror,
                visible
            ));
        }
        return cubes;
    }

    static Map<String, BbFaceUvDefinition> parseFaces(JsonElement faceElement) {
        Map<String, BbFaceUvDefinition> faces = new LinkedHashMap<>();
        for (String key : FACE_KEYS) faces.put(key, BbFaceUvDefinition.DISABLED);
        if (faceElement == null || !faceElement.isJsonObject()) return faces;
        JsonObject obj = faceElement.getAsJsonObject();
        for (String key : FACE_KEYS) {
            if (!obj.has(key) || !obj.get(key).isJsonObject()) continue;
            JsonObject face = obj.getAsJsonObject(key);
            float[] uv = readVec4(face.get("uv"), 0, 0, 0, 0);
            int rotation = face.has("rotation") ? safeInt(face.get("rotation"), 0) : 0;
            int textureIndex = face.has("texture") ? safeInt(face.get("texture"), -1) : -1;
            faces.put(key, new BbFaceUvDefinition(uv[0], uv[1], uv[2], uv[3], rotation, textureIndex, true));
        }
        return faces;
    }

    static List<BbMeshDefinition> parseMeshes(JsonObject root, Map<String, String> elementParents) {
        List<BbMeshDefinition> meshes = new ArrayList<>();
        if (!root.has("elements") || !root.get("elements").isJsonArray()) return meshes;
        for (JsonElement entry : root.getAsJsonArray("elements")) {
            if (!entry.isJsonObject()) continue;
            JsonObject obj = entry.getAsJsonObject();
            if (!obj.has("type") || !"mesh".equalsIgnoreCase(raw(obj.get("type"), ""))) continue;

            String uuid = raw(obj.get("uuid"), "mesh_" + meshes.size());
            String parentUuid = obj.has("parent") ? raw(obj.get("parent"), null) : elementParents.get(uuid);
            float[] origin = readVec3(obj.get("origin"), 0, 0, 0);
            float[] rotation = readVec3(obj.get("rotation"), 0, 0, 0);
            boolean visible = safeBoolean(obj.get("visibility"), true) && safeBoolean(obj.get("export"), true);
            Map<String, BbMeshVertexDefinition> vertices = parseMeshVertices(obj.get("vertices"));
            List<BbMeshFaceDefinition> faces = parseMeshFaces(obj.get("faces"), vertices);

            meshes.add(new BbMeshDefinition(
                uuid,
                raw(obj.get("name"), "mesh_" + meshes.size()),
                parentUuid,
                origin[0], origin[1], origin[2],
                rotation[0], rotation[1], rotation[2],
                vertices,
                faces,
                visible
            ));
        }
        return meshes;
    }

    static Map<String, BbMeshVertexDefinition> parseMeshVertices(JsonElement vertexElement) {
        Map<String, BbMeshVertexDefinition> vertices = new LinkedHashMap<>();
        if (vertexElement == null || !vertexElement.isJsonObject()) return vertices;
        for (Map.Entry<String, JsonElement> entry : vertexElement.getAsJsonObject().entrySet()) {
            float[] position = readVec3(entry.getValue(), 0, 0, 0);
            vertices.put(entry.getKey(), new BbMeshVertexDefinition(
                entry.getKey(), position[0], position[1], position[2]
            ));
        }
        return vertices;
    }

    static List<BbMeshFaceDefinition> parseMeshFaces(
        JsonElement faceElement,
        Map<String, BbMeshVertexDefinition> vertices
    ) {
        List<BbMeshFaceDefinition> faces = new ArrayList<>();
        if (faceElement == null || !faceElement.isJsonObject()) return faces;
        for (Map.Entry<String, JsonElement> entry : faceElement.getAsJsonObject().entrySet()) {
            if (!entry.getValue().isJsonObject()) continue;
            JsonObject face = entry.getValue().getAsJsonObject();
            List<String> vertexIds = readStringList(face.get("vertices"));
            Map<String, BbMeshUvDefinition> uvByVertex = new LinkedHashMap<>();
            JsonObject uv = face.has("uv") && face.get("uv").isJsonObject()
                ? face.getAsJsonObject("uv") : null;
            for (String vertexId : vertexIds) {
                float[] value = uv == null ? new float[] {0f, 0f} : readVec2(uv.get(vertexId), 0f, 0f);
                uvByVertex.put(vertexId, new BbMeshUvDefinition(value[0], value[1]));
            }
            int textureIndex = face.has("texture") ? safeInt(face.get("texture"), -1) : 0;
            boolean validVertices = vertexIds.size() >= 3 && vertexIds.stream().allMatch(vertices::containsKey);
            faces.add(new BbMeshFaceDefinition(
                entry.getKey(), vertexIds, uvByVertex, textureIndex,
                validVertices && textureIndex >= 0
            ));
        }
        return faces;
    }
}
